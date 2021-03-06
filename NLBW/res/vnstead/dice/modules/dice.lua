require "modules/vn"
require "dice/modules/dicegames"

dice = menu {
    nam = "dice",
    system_type = true,
    dsc = function(s)
        return '{' .. s:disp() .. '}' .. img 'blank:64x64';
    end,
    disp = function(s)
        if s.diceOnScreen then
            return img('dice/gfx/dice.png') .. ' ' .. s:hideTxt();
        else
            return img('dice/gfx/dice.png') .. ' ' .. s:rollTxt();
        end
    end,
    var {
        diceOnScreen = false,
        rewardOnScreen = false,
        surftype = 'wood',
        lastRolls = {},
        lastRollFiles = {},
        callback = false,
        callbackName = '',
        pos = 2,
        die_count = 2,
        die_frames_from_stop = 3,
        die_frames_max = 100,
        hotstep = 2,
        acceleration = 1
    },
    set_orientation = function(s, o)
        if (o == "east") then
            s.pos = 1;
        elseif (o == "south") then
            s.pos = 2;
        elseif (o == "west") then
            s.pos = 3;
            --elseif (o == "north") then
        else
            s.pos = 4;
        end
    end,
    set_die_count = function(s, count)
        s.die_count = count;
    end,
    rollTxt = function(s)
        if (LANG == "ru") then
            return 'Бросить кубики';
        else
            return 'Roll the dice';
        end
    end,
    hideTxt = function(s)
        if true then
            return s:rollTxt();
        end
        -- Or, alternatively...
        if (LANG == "ru") then
            return 'Убрать кубики';
        else
            return 'Hide the dice';
        end
    end,
    act = function(s)
        --vn:pause(10, function() set_sound(s:get_roll_sound(), 2, 1); end);
        return s:inv();
    end,
    inv = function(s)
        if s.rewardOnScreen then
            vn:hide('dice/gfx/round_finished.png');
            vn:hide('dice/gfx/reward.png');
            stop_sound();
            s.rewardOnScreen = false;
        end
        vn:pause(20, function()
            set_sound(s:get_roll_sound(), 2, 1);
            if not s:show() then
                s:hide(true);
                s:show();
            end
        end);
    end,
    next_player = function(s, act_immediately)
        if s.diceOnScreen then
            s:hide();
        end
        s:update_next_player_pos();
        if act_immediately then
            s:act();
        end
    end,
    get_next_player_pos = function(s)
        local pos = s.pos;
        repeat
            pos = pos % 4 + 1
        until rollStat:is_defined(pos);
        return pos;
    end,
    update_next_player_pos = function(s)
        s.pos = s:get_next_player_pos();
    end,
    set_callback = function(s, f, name)
        s.callback = f;
        s.callbackName = name;
    end,
    -- Please always invoke the callback with the following function, never use s.callback directly!
    invoke_callback = function(s, ...)
        if (not s.callback) then
            for key, initializer in pairs(dicegames) do
                if (key == s.callbackName) then
                    s:set_callback(initializer());
                end
            end
        end
        if (s.callback) then
            return s.callback(...);
        else
            return nil;
        end
    end,
    reset_callback = function(s)
        s.callback = false;
        s.callbackName = '';
    end,
    dismiss = function(s)
        s.diceOnScreen = false;
    end,
    getPosStr = function(s, idx)
        local hpos = -576 + 356 * idx;
        local vpos = -506 + 356 * idx;
        if (s.pos == 1) then
            return { die = 'dice/gfx/e.' .. idx .. '.die', show = 'moveinright-right-middle@0,' .. hpos, hide = 'hide', hidefast = 'hide' };
        elseif (s.pos == 2) then
            return { die = 'dice/gfx/s.' .. idx .. '.die', show = 'moveinbottom-bottom-middle@' .. vpos .. ',0', hide = 'hide', hidefast = 'hide' };
        elseif (s.pos == 3) then
            return { die = 'dice/gfx/w.' .. idx .. '.die', show = 'moveinleft-left-middle@0,' .. hpos, hide = 'hide', hidefast = 'hide' };
            --elseif (s.pos == 4) then
        else
            return { die = 'dice/gfx/n.' .. idx .. '.die', show = 'moveintop-top-middle@' .. vpos .. ',0', hide = 'hide', hidefast = 'hide' };
        end
    end,
    hide = function(s, fast)
        local result = false;
        local pausecbs = {};
        for i = s.die_count, 1, -1 do
            if s.diceOnScreen then
                local lastRoll = s.lastRolls[i];
                if (s.lastRollFiles[i] and (s.lastRollFiles[i] ~= '')) then
                    local st = s:getStartFrame(lastRoll);
                    if fast then
                        vn:hide(s.lastRollFiles[i], s:getPosStr(i).hidefast, vn.hz, s.die_frames_from_stop, nil, st);
                    else
                        local img = s.lastRollFiles[i];
                        local hidestr = s:getPosStr(i).hide;
                        local spd = s.die_frames_max * vn.hz;
                        local stt = st;
                        local pausecb = function() vn:hide(img, hidestr, spd, s.die_frames_from_stop, nil, stt); end;
                        if rollStat.should_pass or (rollStat.data and s.pos ~= rollStat.data.mainplr) then
                            stead.table.insert(pausecbs, pausecb);
                        else
                            pausecb();
                        end
                    end
                end
                table.remove(s.lastRollFiles, i);
                table.remove(s.lastRolls, i);
                result = true;
            end
        end
        local needStart = true;
        local startfn = function() vn:start(); end;
        if next(pausecbs) ~= nil then
            -- pausecbs is NOT empty
            --needStart = false;
            --stead.table.insert(pausecbs, startfn);
            vn:pause(50, pausecbs);
        end
        if s.diceOnScreen then
            s.diceOnScreen = false;
            if (needStart) then
                startfn();
            end
        end
        return result;
    end,
    show = function(s)
        local result = false;
        for i = 1, s.die_count do
            if not s.diceOnScreen then
                local lastRoll = rnd(6);
                local lastRollFile = string.format(s:getPosStr(i).die .. '%d.png', lastRoll);
                local st = s:getStartFrame(lastRoll);
                s.lastRolls[i] = lastRoll;
                s.lastRollFiles[i] = lastRollFile;
                vn:preload_effect(lastRollFile, st, s.die_frames_max, s.die_frames_from_stop, s.die_frames_max, nil, true);
                vn:show(lastRollFile, s:getPosStr(i).show, s.die_frames_max * vn.hz, st, nil, s.die_frames_from_stop, s:getArm(), s.hotstep, s.acceleration);
                result = true;
            end
        end
        if not s.diceOnScreen then
            s.diceOnScreen = true;
            vn:startcb(function() return s:invoke_callback(s.lastRolls, s.pos); end);
        end
        return result;
    end,
    getArm = function(s)
        local r = rnd();
        if s.pos == 1 or s.pos == 3 then
            local result = {};
            for i = 0, s.die_frames_max do
                result[i] = { 250 + r * 220, math.abs((i + 20) * (i - (40 * r + 70 * (1 - r))) * (i - 130) / 1000) };
            end
            return result;
        elseif s.pos == 2 or s.pos == 4 then
            return { [0] = { 0, 150 + r * 85 } };
        else
            return { [0] = { 0, 0 } };
        end
    end,
    getStartFrame = function(s, rollValue)
        --if true then
        --return 0;
        --end
        if rollValue == 1 then
            return 33;
        elseif rollValue == 2 then
            return 39;
        elseif rollValue == 3 then
            return 27;
        elseif rollValue == 4 then
            return 39;
        elseif rollValue == 5 then
            return 37;
        elseif rollValue == 6 then
            return 40;
        else
            return 0;
        end
    end,
    get_roll_sound = function(s)
        if s.surftype == 'stone' then
            return 'dice/sfx/kosti.ogg';
        else
            return 'dice/sfx/dice_roll.ogg';
        end
    end
}

stead.module_init(function()
end)