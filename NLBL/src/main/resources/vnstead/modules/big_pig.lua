require "modules/vn"
require "modules/dice"
require "modules/dicegames"

rollStat = stat {
    nam = "rollStat",
    system_type = true,
    _rolls = {},
    _allRollsByPlayer = {},
    _curRollsByPlayer = {},
    var { message = "", data = false, should_pass = false, hotstep = 6, acceleration = 1 },
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
    only_one_player = function(s)
        local result = false;
        for i = 1, 4 do
            local defined = s:is_defined(i);
            if result and defined then
                return false;
            end
            result = result or defined;
        end
        return result;
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
                stead.table.insert(result, { ["text"] = name, ["color"] = color1 });
                stead.table.insert(result, { ["text"] = s:get_pts_string() .. cursum .. "; " .. s:get_money_string() .. money, ["color"] = color2 });
            end
        end
        if s.data then
            stead.table.insert(result, { ["text"] = s:get_bet_string() .. s.data.bet, ["color"] = nil });
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
                    vn:show('gfx/reward.png', 'middle-middle@0,0', 270 * vn.hz, 0, 39, nil, nil, s.hotstep, s.acceleration);
                    set_sound('sfx/money.ogg', nil, 1);
                    if s.data and not s:is_defined(rollStat.data.mainplr) then
                        return function()
                            vn:show(you_lose_label, "fadein-middle-middle@0,0", 100 * vn.hz);
                            vn:startcb(function()
                                play_game_obj:act();
                                return true;
                            end);
                            return true;
                        end
                    elseif s:only_one_player() then
                        return function()
                            vn:show(you_win_label, "fadein-middle-middle@0,0", 100 * vn.hz);
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
                        return function()
                            if result then
                                vn:startcb(function()
                                    dice:act();
                                    return true;
                                end);
                            else
                                dice:act();
                            end;
                            return true;
                        end
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
        rollStat:init();
        s:bgimg(game.table.bg);

        vn:preload_effect('gfx/reward.png', 0, 270, 0, 270);
        local txtfn = function() return rollStat:info(); end;
        local paper = vn:show(game.table.paper, 'moveinright-right-top@-20,40', 10 * vn.hz, 0, 2, nil, nil, nil, nil, nil, function(v) vn:set_step(v, nil, not v.forward); vn:start(); end);

        local overfn = function(v) set_sound('sfx/shake.ogg', 1, 0); end;
        local outfn = function(v) stop_sound(1); end;
        local enablefn = function(v) return (rollStat.data and not rollStat.data.ai[dice.pos] and rollStat:is_defined(rollStat.data.mainplr)); end;
        local enablefn2 = function(v) return (rollStat.data and not rollStat.data.ai[dice.pos]); end;
        if (game.table.plate) then
            set_music('sfx/abu_ali.ogg');
            vn:add_child(paper, 'gfx/empty.png', 90, 100, txtfn);
            vn:show(game.table.plate, 'right-bottom@110,75', 0);
            vn:show_btn(
                "gfx/btn_dice2.png",
                'right-bottom@-195,-440',
                nil, function(v) if vn.stopped then outfn(v); dice:act(); end; end,
                "gfx/alt_btn_dice2.png",
                'right-bottom@-195,-440',
                overfn, outfn,
                function(v) return dice:rollTxt() end,
                enablefn
            );
            vn:show_btn(
                "gfx/btn_next2.png",
                'right-bottom@-195,-280',
                nil, function(v) if vn.stopped then next_turn_obj:act(); end; end,
                "gfx/alt_btn_next2.png",
                'right-bottom@-195,-280',
                nil, nil,
                function(v) return next_turn_obj:disp() end,
                enablefn
            );
            vn:show_btn(
                "gfx/btn_inc2.png",
                'right-bottom@-195,-180',
                nil, function(v) if vn.stopped then increase_bet_obj:act(); end; end,
                "gfx/alt_btn_inc2.png",
                'right-bottom@-195,-180',
                nil, nil,
                function(v) return increase_bet_obj:txt() end,
                enablefn
            );
            vn:show_btn(
                "gfx/btn_exit2.png",
                'right-bottom@-195,-10',
                nil, function(v) if vn.stopped then play_game_obj:act(); end; end,
                "gfx/alt_btn_exit2.png",
                'right-bottom@-195,-10',
                nil, nil,
                function(v) return play_game_obj:txt() end,
                enablefn2
            );
        else
            set_music('sfx/tavernm.ogg');
            vn:add_child(paper, 'gfx/empty.png', 60, 100, txtfn);
            vn:show_btn(
                "gfx/btn_dice.png",
                'right-top@-40,500',
                nil, function(v) if vn.stopped then outfn(v); dice:act(); end; end,
                "gfx/alt_btn_dice.png",
                'right-top@-40,500',
                overfn, outfn,
                function(v) return dice:rollTxt(), "n" end,
                enablefn
            );
            vn:show_btn(
                "gfx/btn_next.png",
                'right-top@-40,625',
                nil, function(v) if vn.stopped then next_turn_obj:act(); end; end,
                "gfx/alt_btn_next.png",
                'right-top@-40,625',
                nil, nil,
                function(v) return next_turn_obj:disp(), "h" end,
                enablefn
            );
            vn:show_btn(
                "gfx/btn_inc.png",
                'right-top@-40,750',
                nil, function(v) if vn.stopped then increase_bet_obj:act(); end; end,
                "gfx/alt_btn_inc.png",
                'right-top@-40,750',
                nil, nil,
                function(v) return increase_bet_obj:txt(), "h" end,
                enablefn
            );
            vn:show_btn(
                "gfx/btn_exit.png",
                'right-top@-40,875',
                nil, function(v) if vn.stopped then play_game_obj:act(); end; end,
                "gfx/alt_btn_exit.png",
                'right-top@-40,875',
                nil, nil,
                function(v) return play_game_obj:txt(), "s" end,
                enablefn2
            );
        end
        vn:start();
        take(rollStat);
        if game.table and (game.table.surftype == 'stone') then
            dice.surftype = 'stone';
        else
            dice.surftype = 'wood';
        end
        dice:set_callback(rollStat:setrolls());
        dice.pos = 2;
    end,
    left = function(s, t)
        stop_music();
        remove(rollStat, me());
        if rollStat.data then
            game.data = shallowcopy(rollStat.data);
            game.money = game.data.money[game.data.mainplr];
        end
        vn:unlock_direct();
        paginator:turnon();
    end,
    obj = { 'dice', 'next_turn_obj', 'increase_bet_obj' }
};

game.enable_save = function()
        if here().nosave then
                return false
        end
        return true
end

game.enable_autosave = function()
        if here().nosave then
                return false
        end
        return true
end

game.money = 200
game.defaultbet = 100
game.data = {
    ["mainplr"] = 2,
    ["names"] = {nil, "Рагнар", "Горожанин", "Торговец"},
    ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
    ["ai"] = {nil, nil, "aggressive", "cautious"},
    ["money"] = {0, game.money, 300, 400},
    ["bet"] = 100,
    ["threshold"] = 100
};
game.table = { ["bg"] = 'images/table2.png', ["paper"] = 'images/paper2.png' };

play_bp_two_ais = menu {
    nam = "play_bp_two_ais",
    dsc = function(s) return "{Сыграть с Горожанином и Торговцем}^" end, 
    act = function(s) 
        game.data = {
            ["mainplr"] = 2,
            ["names"] = {nil, "Рагнар", "Горожанин", "Торговец"},
            ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
            ["ai"] = {nil, nil, "aggressive", "cautious"},
            ["money"] = {0, game.money, 300, 400},
            ["bet"] = 100,
            ["threshold"] = 10
        };
        game.table.bg = 'images/table2.png';
        game.table.paper = 'images/paper2.png';
        game.table.plate = false;
        game.table.surftype = false;

        play_game_obj:inv(); 
    end
}

play_bp_one_ai = menu {
    nam = "play_bp_one_ai",
    dsc = function(s) return "{Сыграть с Абу-Али}^" end, 
    act = function(s) 
        game.data = {
            ["mainplr"] = 2,
            ["names"] = {nil, "Рагнар", nil, "Абу-Али"},
            ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
            ["ai"] = {nil, nil, nil, "optimum"},
            ["money"] = {0, game.money, 0, 1000},
            ["bet"] = 100,
            ["threshold"] = 100,
        };
        game.table.bg = 'images/table1.png';
        game.table.paper = 'images/paper1.png';
        game.table.plate = 'images/plate.png';
        game.table.surftype = 'stone';

        play_game_obj:inv(); 
    end
}

play_bp_one_hotseat = menu {
    nam = "play_bp_one_hotseat",
    dsc = function(s) return "{Сыграть с другом}^" end, 
    act = function(s) 
        game.data = {
            ["mainplr"] = 2,
            ["names"] = {nil, "Рагнар", nil, "Варвар"},
            ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
            ["ai"] = {nil, nil, nil, nil},
            ["money"] = {0, game.money, 0, game.money},
            ["bet"] = 100,
            ["threshold"] = 100
        };
        game.table.bg = 'images/table1.png';
        game.table.paper = 'images/paper1.png';
        game.table.plate = 'images/plate.png';
        game.table.surftype = false;

        play_game_obj:inv(); 

    end
}

play_bp_two_hotseats = menu {
    nam = "play_bp_two_hotseats",
    dsc = function(s) return "{Сыграть с двумя друзьями}^" end, 
    act = function(s) 
        game.data = {
            ["mainplr"] = 2,
            ["names"] = {nil, "Рагнар", "Варвар", "Воин"},
            ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
            ["ai"] = {nil, nil, nil, nil},
            ["money"] = {0, game.money, game.money, game.money},
            ["bet"] = 100,
            ["threshold"] = 100
        };
        game.table.bg = 'images/table2.png';
        game.table.paper = 'images/paper2.png';
        game.table.plate = false;
        game.table.surftype = false;

        play_game_obj:inv(); 
    end
}

stead.module_init(function()
    rollStat:init();
    dicegames['setrolls'] = function() rollStat:setrolls(); end;
    local labelFont = sprite.font('fonts/STEINEMU.ttf', 48);
    if LANG == "ru" then
        you_win_label = vn:label("Вы победили!", 40, "#ffffff", "black", 127, labelFont);
        you_lose_label = vn:label("Вы проиграли...", 40, "#ffffff", "black", 127, labelFont);
    else
        you_win_label = vn:label("You win!", 40, "#ffffff", "black", 127, labelFont);
        you_lose_label = vn:label("You lose...", 40, "#ffffff", "black", 127, labelFont);
    end
    sprite.free_font(labelFont);
end)
