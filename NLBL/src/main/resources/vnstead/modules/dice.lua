require "modules/vn"

dice = menu {
    nam = "dice",
    system_type = true,
    dsc = function(s)
        return '{' .. s:disp() .. '}' .. img 'blank:64x64';
    end,
    disp = function(s)
        if s.diceOnScreen then
            return img('gfx/dice.png') .. ' ' .. s:hideTxt();
        else
            return img('gfx/dice.png') .. ' ' .. s:rollTxt();
        end
    end,
    var {
        diceOnScreen = false,
        rewardOnScreen = false,
        lastRolls = {},
        lastRollFiles = {},
        callback = false,
        callbackName = '',
        pos = 2,
        die_count = 2
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
            return 'Бросить кубик';
        else
            return 'Roll the die';
        end
    end,
    hideTxt = function(s)
        if true then
            return s:rollTxt();
        end
        -- Or, alternatively...
        if (LANG == "ru") then
            return 'Убрать кубик';
        else
            return 'Hide the die';
        end
    end,
    act = function(s)
        return s:inv();
    end,
    inv = function(s)
        if s.rewardOnScreen then
            vn:hide('gfx/round_finished.png', 'fadeout-middle-middle@0,-18', vn.hz);
            vn:hide('gfx/reward.png', 'fadeout-middle-middle@0,0', vn.hz);
            s.rewardOnScreen = false;
        end
        if not s:show() then
            s:hide(true);
            s:show();
        end
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
            if (s.callbackName == 'setluck') then
                s:set_callback(luckStat:setluck());
            elseif (s.callbackName == 'getlucky') then
                s:set_callback(luckStat:getlucky(luckStat._threshold, luckStat._luckyObj, luckStat._unluckyObj));
            elseif (s.callbackName == 'setrolls') then
                s:set_callback(rollStat:setrolls());
            else
                s:reset_callback();
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
            return { die = 'gfx/e.' .. idx .. '.die', show = 'moveinright-right-middle@0,' .. hpos, hide = 'hide', hidefast = 'hide' };
        elseif (s.pos == 2) then
            return { die = 'gfx/s.' .. idx .. '.die', show = 'moveinbottom-bottom-middle@' .. vpos .. ',0', hide = 'hide', hidefast = 'hide' };
        elseif (s.pos == 3) then
            return { die = 'gfx/w.' .. idx .. '.die', show = 'moveinleft-left-middle@0,' .. hpos, hide = 'hide', hidefast = 'hide' };
            --elseif (s.pos == 4) then
        else
            return { die = 'gfx/n.' .. idx .. '.die', show = 'moveintop-top-middle@' .. vpos .. ',0', hide = 'hide', hidefast = 'hide' };
        end
    end,
    hide = function(s, fast)
        local result = false;
        local pausecbs = {};
        for i = s.die_count, 1, -1 do
            if s.diceOnScreen then
                local lastRoll = s.lastRolls[i];
                if ((s.lastRollFiles[i] ~= nil) and (s.lastRollFiles[i] ~= '')) then
                    local st = s:getStartFrame(lastRoll);
                    if fast then
                        vn:hide(s.lastRollFiles[i], s:getPosStr(i).hidefast, vn.hz, 0, st, s:getArm());
                    else
                        local img = s.lastRollFiles[i];
                        local hidestr = s:getPosStr(i).hide;
                        local spd = 100 * vn.hz;
                        local stt = st;
                        local arm = s:getArm();
                        local pausecb = function() vn:hide(img, hidestr, spd, 0, stt, arm); end;
                        if rollStat.should_pass or (rollStat.data and s.pos ~= rollStat.data.mainplr) then
                            table.insert(pausecbs, i, pausecb);
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
            --table.insert(pausecbs, s.die_count + 1, startfn);
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
                table.insert(s.lastRolls, i, lastRoll);
                table.insert(s.lastRollFiles, i, lastRollFile);
                vn:show(lastRollFile, s:getPosStr(i).show, 100 * vn.hz, st, nil, s:getArm());
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
            for i = 0, 100 do
                table.insert(result, i, { 250 + r * 220, math.abs((i + 20) * (i - (40 * r + 70 * (1 - r))) * (i - 130) / 1000) });
            end
            return result;
        elseif s.pos == 2 or s.pos == 4 then
            return { [0] = { 0, 235 } };
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
    end
}

next_turn_obj = menu {
    nam = "next_turn_obj",
    dsc = function(s)
        return '{' .. s:disp() .. '}' .. img 'blank:64x64';
    end,
    disp = function(s)
        if (LANG == "ru") then
            return 'Передать ход';
        else
            return 'Next player';
        end
    end,
    act = function(s)
        return s:inv();
    end,
    inv = function(s)
        rollStat:add_result();
        rollStat:reset_cur();
        dice:next_player();
        dice:act();
    end
}

luckStat = stat {
    nam = "luckStat",
    system_type = true,
    _luck = 0,
    _threshold = 0,
    _luckyObj = false,
    _unluckyObj = false,
    disp = function(s)
        if (LANG == "ru") then
            return "Удача: " .. s._luck;
        else
            return "Luck: " .. s._luck;
        end
    end,
    setluck = function(s)
        return function(rolls, pos)
            luckStat._luck = rolls[1];
        end, 'setluck'
    end,
    getlucky = function(s, threshold, luckyObj, unluckyObj)
        s._threshold = threshold;
        s._luckyObj = luckyObj;
        s._unluckyObj = unluckyObj;
        return function(rolls, pos)
            local roll = rolls[1];
            if (luckStat._luck + roll >= luckStat._threshold) then
                luckStat._luckyObj:enable();
                luckStat._unluckyObj:disable();
            else
                luckStat._luckyObj:disable();
                luckStat._unluckyObj:enable();
            end
            vn:need_update();
        end, 'getlucky'
    end
}

rollStat = stat {
    nam = "rollStat",
    system_type = true,
    _rolls = {},
    _allRollsByPlayer = {},
    _curRollsByPlayer = {},
    var { message = "", data = false, should_pass = false },
    init = function(s)
        s._allRollsByPlayer[1] = 0;
        s._allRollsByPlayer[2] = 0;
        s._allRollsByPlayer[3] = 0;
        s._allRollsByPlayer[4] = 0;
        s:reset_cur();
    end,
    add_result = function(s)
        s._allRollsByPlayer[1] = s._allRollsByPlayer[1] + s._curRollsByPlayer[1];
        s._allRollsByPlayer[2] = s._allRollsByPlayer[2] + s._curRollsByPlayer[2];
        s._allRollsByPlayer[3] = s._allRollsByPlayer[3] + s._curRollsByPlayer[3];
        s._allRollsByPlayer[4] = s._allRollsByPlayer[4] + s._curRollsByPlayer[4];
    end,
    reset_cur = function(s)
        s._curRollsByPlayer[1] = 0;
        s._curRollsByPlayer[2] = 0;
        s._curRollsByPlayer[3] = 0;
        s._curRollsByPlayer[4] = 0;
    end,
    dsc = function(s)
        return s:disp();
    end,
    disp = function(s)
        local result = "";
        local prefix;
        if (LANG == "ru") then
            prefix = "Броски: ";
        else
            prefix = "Rolls: ";
        end
        for k, v in pairs(s._rolls) do
            result = result .. tostring(v) .. "; ";
        end
        result = result .. s.message;
        if result ~= "" then
            return prefix .. result;
        else
            return "";
        end
    end,
    is_defined = function(s, pos)
        -- The idea is: ignore players whose name is not specified, they will immediately pass the turn
        if s.data then
            local name = s.data.names[pos];
            local money = s.data.money[pos];
            return name ~= nil and money > 0;
        end
        return false;
    end,
    info = function(s)
        local result = {};
        for i = 1, 4 do
            local cursum = s._allRollsByPlayer[i] + s._curRollsByPlayer[i];
            local name = nil
            local money = 0;
            local colors = nil;
            if s.data then
                name = s.data.names[i];
                money = s.data.money[i];
                colors = s.data.colors[i];
            end
            if name and money > 0 then
                local color1 = nil;
                local color2 = nil;
                if colors then
                    color1 = colors[1];
                    color2 = colors[2];
                end
                table.insert(result, { ["text"] = name, ["color"] = color1 });
                table.insert(result, { ["text"] = s:get_pts_string() .. cursum .. "; " .. s:get_money_string() .. money, ["color"] = color2 });
            end
        end
        if s.data then
            table.insert(result, { ["text"] = s:get_bet_string() .. s.data.bet, ["color"] = nil });
        end
        return result;
    end,
    setrolls = function(s)
        return function(rolls, pos)
            local curpos = dice.pos;
            local nextpos = dice:get_next_player_pos();
            local result = false;
            local should_pass_turn = false;
            local good_luck = true;
            local is_double = true;
            local prev = 0;
            rollStat._rolls = shallowcopy(rolls);
            for k, v in pairs(s._rolls) do
                good_luck = good_luck and (v == 1);
                should_pass_turn = should_pass_turn or (v == 1);
                is_double = is_double and ((prev == 0) or (prev == v));
                s._curRollsByPlayer[pos] = s._curRollsByPlayer[pos] + v;
                prev = v;
            end
            s.message = "";
            s.should_pass = false;
            if good_luck then
                s.message = s:good_luck_message();
                s._curRollsByPlayer[pos] = s._curRollsByPlayer[pos] + 23;
                should_pass_turn = false;
            elseif is_double then
                s.message = s:is_double_message(4 * prev);
                s._curRollsByPlayer[pos] = s._curRollsByPlayer[pos] + 2 * prev;
                should_pass_turn = false;
            elseif should_pass_turn then
                s.should_pass = true;
                s.message = s:should_pass_turn_message();
                s:reset_cur();
                dice:next_player();
            end
            if s.data then
                local score = s._allRollsByPlayer[pos] + s._curRollsByPlayer[pos];
                if score >= s.data.threshold then
                    s.message = s:win_message(s.data.names[pos]);
                    local prize = 0;
                    for i = 1, 4 do
                        if s:is_defined(i) then
                            local new_money = s.data.money[i] - s.data.bet;
                            if new_money > 0 then
                                s.data.money[i] = new_money;
                                prize = prize + s.data.bet;
                            else
                                prize = prize + s.data.money[i];
                                s.data.money[i] = 0;
                            end
                        end
                    end
                    s.data.money[pos] = s.data.money[pos] + prize;
                    s:init();
                    vn:show('gfx/round_finished.png', 'middle-middle@0,-18', vn.hz);
                    vn:show('gfx/reward.png', 'middle-middle@0,0', 270 * vn.hz);
                    if rollStat.data and not rollStat:is_defined(rollStat.data.mainplr) then
                        return function()
                            vn:show("gfx/loss.png", "fadein-middle-middle@0,0", 100 * vn.hz);
                            vn:startcb(function()
                                play_game_obj:act();
                                return true;
                            end);
                            return true;
                        end
                    end
                    s.data.bet = s:get_next_round_bet();
                    dice.rewardOnScreen = true;
                    result = true;
                end
                local under_threshold = s._curRollsByPlayer[pos] <= s:get_threshold(s.data.ai[curpos]);
                local action_required = should_pass_turn or not under_threshold;
                if s.data.ai[curpos] or action_required then
                    if under_threshold or should_pass_turn then
                        return function() if result then vn:startcb(function() dice:act(); return true; end); else dice:act(); end; return true; end
                    else
                        return function()
                            if result then
                                vn:startcb(function()
                                    rollStat:add_result();
                                    rollStat:reset_cur();
                                    dice:next_player(true);
                                    return true;
                                end);
                            else
                                rollStat:add_result();
                                rollStat:reset_cur();
                                dice:next_player(true);
                            end;
                            return true;
                        end
                    end
                end
            end
            return result;
        end, 'setrolls'
    end,
    get_next_round_bet = function(s)
        local next_round_bet = game.defaultbet;
        if s.data then
            for i = 1, 4 do
                if s:is_defined(i) and s.data.money[i] < next_round_bet then
                    next_round_bet = s.data.money[i];
                end
            end
        end
        return next_round_bet;
    end,
    get_threshold = function(s, ainame)
        if ainame == 'aggressive' then
            return 36;
        elseif ainame == 'cautious' then
            return 12;
        elseif ainame == 'optimum' then
            return 24;
        else
            -- human player also has threshold value :)
            if (s.data) then
                return s.data.threshold;
            else
                return 1000000;
            end
        end
    end,
    good_luck_message = function(s)
        if (LANG == "ru") then
            return 'Результат: 1 + 1, большая удача! Вы получаете +25 очков!';
        else
            return "Result: 1 + 1, you are very lucky! You've got +25!";
        end
    end,
    is_double_message = function(s, value)
        if (LANG == "ru") then
            return "Дубль, вам повезло! Вы получаете +" .. tostring(value) .. " очков!";
        else
            return "Double, you are lucky! You've got +" .. tostring(value);
        end
    end,
    should_pass_turn_message = function(s)
        if (LANG == "ru") then
            return "Результат: выпало 1, вам не повезло! Ход переходит к противнику.";
        else
            return "Result: 1 rolled, you are unlucky! Miss the turn.";
        end
    end,
    win_message = function(s, name)
        if (LANG == "ru") then
            return "Игрок " .. name .. " побеждает в этом раунде!";
        else
            return "Player " .. name .. " wins this round!";
        end
    end,
    get_player_string = function(s)
        if (LANG == "ru") then
            return "Игрок: ";
        else
            return "Player: ";
        end
    end,
    get_pts_string = function(s)
        if (LANG == "ru") then
            return "Очки: ";
        else
            return "Points: ";
        end
    end,
    get_money_string = function(s)
        if (LANG == "ru") then
            return "Деньги: ";
        else
            return "Money: ";
        end
    end,
    get_bet_string = function(s)
        if (LANG == "ru") then
            return "Ставка: ";
        else
            return "Bet: ";
        end
    end,
}

function shallowcopy(orig)
    if not orig then
        return nil;
    end
    local orig_type = type(orig)
    local copy
    if orig_type == 'table' then
        copy = {}
        for orig_key, orig_value in pairs(orig) do
            copy[orig_key] = orig_value
        end
    else -- number, string, boolean, etc
    copy = orig
    end
    return copy
end

increase_bet_obj = menu {
    nam = "increase_bet_obj",
    dsc = function(s)
        local result = "{" .. s:txt() .. "}";
        return result .. img 'blank:64x64';
    end,
    txt = function(s)
        if (LANG == "ru") then
            return "Удвоить ставку";
        else
            return "Double the bet";
        end
    end,
    act = function(s)
        if rollStat.data then
            rollStat.data.bet = rollStat.data.bet * 2;
            for i = 1, 4 do
                if rollStat:is_defined(i) and rollStat.data.bet > rollStat.data.money[i] then
                    rollStat.data.bet = rollStat.data.money[i];
                end
            end
            vn:start();
            vn:commit();
        end
    end
}

play_game_obj = menu {
    nam = "play_game_obj",
    dsc = function(s)
        return "{" .. s:txt() .. "}";
    end,
    txt = function(s)
        if (LANG == "ru") then
            return "Закончить игру";
        else
            return "Leave the game";
        end
    end,
    disp = function(s)
        if (LANG == "ru") then
            return 'Сыграть в кости';
        else
            return 'Play the dice';
        end
    end,
    act = function(s)
        take(play_game_obj);
        walk(game_room.returnto);
    end,
    inv = function(s)
        walk(game_room);
        drop(play_game_obj, game_room);
    end
}

game_room = vnr {
    nam = true,
    nosave = true,
    var { returnto = game_room; },
    bgimg = function(s, bg)
        vn:scene(bg);
        vn:geom(8, 864, 1904, 184, 'dissolve', 240, 'gfx/fl.png', 'gfx/fr.png');
    end,
    dsc = function(s)
        return "";
    end,
    enter = function(s, f)
        rollStat.data = shallowcopy(game.data);
        if rollStat.data then
            rollStat._rolls = {};
            rollStat.message = "";
            if not rollStat:is_defined(rollStat.data.mainplr) then
                dice:disable();
                next_turn_obj:disable();
                increase_bet_obj:disable();
            else
                rollStat.data.bet = rollStat:get_next_round_bet();
                dice:enable();
                next_turn_obj:enable();
                increase_bet_obj:enable();
            end
        end
    end,
    entered = function(s, f)
        if f ~= nil then
            s.returnto = f;
        end
        paginator:turnoff();
        vn:lock_direct();
        s:bgimg(game.table.bg);
        vn:show(game.table.paper, 'right-top@-20,40', 0);
        local enablefn = function(v) return (rollStat.data and not rollStat.data.ai[dice.pos] and rollStat:is_defined(rollStat.data.mainplr)); end;
        local enablefn2 = function(v) return (rollStat.data and not rollStat.data.ai[dice.pos]); end;
        if (game.table.plate) then
            vn.xhud = 1540;
            vn.yhud = 100;
            vn:show(game.table.plate, 'right-bottom@110,95', 0);
            vn:show_btn(
                function(v) if vn.stopped then dice:act(); end; end,
                "gfx/btn_dice.png",
                'right-bottom@-195,-430',
                "gfx/alt_btn_dice.png",
                'right-bottom@-195,-430',
                function(v) return dice:rollTxt() end,
                enablefn
            );
            vn:show_btn(
                function(v) if vn.stopped then next_turn_obj:act(); end; end,
                "gfx/btn_next.png",
                'right-bottom@-195,-270',
                "gfx/alt_btn_next.png",
                'right-bottom@-195,-270',
                function(v) return next_turn_obj:disp() end,
                enablefn
            );
            vn:show_btn(
                function(v) if vn.stopped then increase_bet_obj:act(); end; end,
                "gfx/btn_inc.png",
                'right-bottom@-195,-150',
                "gfx/alt_btn_inc.png",
                'right-bottom@-195,-150',
                function(v) return increase_bet_obj:txt() end,
                enablefn
            );
            vn:show_btn(
                function(v) if vn.stopped then play_game_obj:act(); end; end,
                "gfx/btn_exit.png",
                'right-bottom@-195,-20',
                "gfx/alt_btn_exit.png",
                'right-bottom@-195,-20',
                function(v) return play_game_obj:txt() end,
                enablefn2
            );
        else
            vn.xhud = 1560;
            vn.yhud = 120;
            vn:show_btn(
                function(v) if vn.stopped then dice:act(); end; end,
                "gfx/btn_dice.png",
                'right-top@-40,500',
                "gfx/alt_btn_dice.png",
                'right-top@-40,500',
                function(v) return dice:rollTxt(), "n" end,
                enablefn
            );
            vn:show_btn(
                function(v) if vn.stopped then next_turn_obj:act(); end; end,
                "gfx/btn_next.png",
                'right-top@-40,625',
                "gfx/alt_btn_next.png",
                'right-top@-40,625',
                function(v) return next_turn_obj:disp(), "h" end,
                enablefn
            );
            vn:show_btn(
                function(v) if vn.stopped then increase_bet_obj:act(); end; end,
                "gfx/btn_inc.png",
                'right-top@-40,750',
                "gfx/alt_btn_inc.png",
                'right-top@-40,750',
                function(v) return increase_bet_obj:txt(), "h" end,
                enablefn
            );
            vn:show_btn(
                function(v) if vn.stopped then play_game_obj:act(); end; end,
                "gfx/btn_exit.png",
                'right-top@-40,875',
                "gfx/alt_btn_exit.png",
                'right-top@-40,875',
                function(v) return play_game_obj:txt(), "s" end,
                enablefn2
            );
        end
        vn:start();
        vn.txtfun = function() return rollStat:info(); end;
        take(rollStat);
        dice:set_callback(rollStat:setrolls());
        dice.pos = 2;
    end,
    left = function(s, t)
        remove(rollStat, me());
        vn.txtfun = function() return {}; end;
        if rollStat.data then
            game.data = shallowcopy(rollStat.data);
            game.money = game.data.money[game.data.mainplr];
        end
        vn:unlock_direct();
        paginator:turnon();
    end,
    obj = { 'dice', 'next_turn_obj', 'increase_bet_obj' }
};

stead.module_init(function()
    rollStat:init();
end)