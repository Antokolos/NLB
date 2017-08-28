require "modules/vn"
require "modules/gobj"
require "modules/nlb"
require "dice/modules/dice"
require "dice/modules/dicegames"
require "dice/modules/rulesdlg"

next_turn_obj = menu {
    nam = "next_turn_obj",
    system_type = true,
    dsc = function(s)
        return '{' .. s:disp() .. '}' .. img 'blank:64x64';
    end,
    disp = function(s)
        if _export_lang == 'ru' then
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
    system_type = true,
    dsc = function(s)
        local result = "{" .. s:txt() .. "}";
        return result .. img 'blank:64x64';
    end,
    txt = function(s)
        if _export_lang == 'ru' then
            return "Удвоить ставку";
        else
            return "Double the stake";
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
        end
    end
}

_play_game_obj = menu {
    nam = "play_game_obj",
    system_type = true,
    dsc = function(s)
        return "{" .. s:txt() .. "}";
    end,
    txt = function(s)
        if _export_lang == 'ru' then
            return "Закончить игру";
        else
            return "Leave the game";
        end
    end,
    disp = function(s)
        if _export_lang == 'ru' then
            return 'Сыграть в кости';
        else
            return 'Play the dice';
        end
    end,
    act = function(s)
        take(_play_game_obj);
        walk(stead.ref(game_room._returnto));
    end,
    inv = function(s)
        walk(game_room);
        drop(_play_game_obj, game_room);
    end
}

you_lose = gobj {
    nam = "you_lose",
    system_type = true,
    pic = function(s) return you_lose_label end,
    eff = "fadein-middle-middle@0,0",
    maxStep = 100
}

you_win = gobj {
    nam = "you_win",
    system_type = true,
    pic = function(s) return you_win_label end,
    eff = "fadein-middle-middle@0,0",
    maxStep = 100
}

reward = gobj {
    nam = "reward",
    system_type = true,
    pic = "dice/gfx/reward.png",
    eff = "overlap-middle-middle@0,0",
    dirty_draw = true,
    maxStep = 270,
    startFrame = 0,
    curStep = 39,
    hot_step = 2,
    acceleration = 1
}

round_finished = gobj {
    nam = "round_finished",
    system_type = true,
    pic = "dice/gfx/round_finished.png",
    eff = "middle-middle@0,-18"
}

txt1 = gobj {
    nam = "txt1",
    system_type = true,
    pic = "gfx/empty.png",
    iarm = { [0] = { 90, 170 } },
    txtfn = function(s) return rollStat:info(); end
}

txt2 = gobj {
    nam = "txt2",
    system_type = true,
    pic = "gfx/empty.png",
    iarm = { [0] = { 60, 230 } },
    txtfn = function(s) return rollStat:info(); end
}

paper = gobj {
    nam = "paper",
    system_type = true,
    pic = function(s) return game.table.paper; end,
    eff = "moveinright-right-top@-20,40",
    maxStep = 10,
    startFrame = 0,
    curStep = 2,
    act = function(s)
        local v = vn:glookup(stead.deref(s));
        if s.is_paused then
            vn:vpause(v, false);
        else
            vn:set_step(v, nil, not v.forward);
        end
        vn:start();
    end,
    --is_paused = true;
}

plate = gobj {
    nam = "plate",
    system_type = true,
    pic = function(s) return game.table.plate; end,
    eff = "right-bottom@110,75"
}

btn_dice = gobj {
    nam = "btn_dice",
    system_type = true,
    pic = "dice/gfx/btn_dice.png",
    eff = "right-top@-40,500",
    onover = function(s) set_sound('dice/sfx/shake.ogg', 1, 0); return true; end,
    morphover = "alt_btn_dice",
    enablefn = function(s) return enablefn(s); end
}

alt_btn_dice = gobj {
    nam = "alt_btn_dice",
    system_type = true,
    pic = "dice/gfx/alt_btn_dice.png",
    eff = "right-top@-40,500",
    morphout = "btn_dice",
    dsc = function(s) return dice:rollTxt(); end,
    ttpos = "n",
    act = function(s) if vn.stopped then s:onout(); dice:act(); end; end,
    onout = function(s) stop_sound(1); return true; end,
    enablefn = function(s) return enablefn(s); end
}

btn_next = gobj {
    nam = "btn_next",
    system_type = true,
    pic = "dice/gfx/btn_next.png",
    eff = "right-top@-40,625",
    morphover = "alt_btn_next",
    enablefn = function(s) return enablefn(s); end
}

alt_btn_next = gobj {
    nam = "alt_btn_next",
    system_type = true,
    pic = "dice/gfx/alt_btn_next.png",
    eff = "right-top@-40,625",
    morphout = "btn_next",
    dsc = function(s) return next_turn_obj:disp(); end,
    act = function(s) if vn.stopped then next_turn_obj:act(); end; end,
    enablefn = function(s) return enablefn(s); end
}

btn_inc = gobj {
    nam = "btn_inc",
    system_type = true,
    pic = "dice/gfx/btn_inc.png",
    eff = "right-top@-40,750",
    morphover = "alt_btn_inc",
    enablefn = function(s) return enablefn(s); end
}

alt_btn_inc = gobj {
    nam = "alt_btn_inc",
    system_type = true,
    pic = "dice/gfx/alt_btn_inc.png",
    eff = "right-top@-40,750",
    morphout = "btn_inc",
    dsc = function(s) return increase_bet_obj:txt(); end,
    act = function(s) if vn.stopped then increase_bet_obj:act(); end; end,
    enablefn = function(s) return enablefn(s); end
}

btn_exit = gobj {
    nam = "btn_exit",
    system_type = true,
    pic = "dice/gfx/btn_exit.png",
    eff = "right-top@-40,875",
    morphover = "alt_btn_exit",
    enablefn = function(s) return enablefn2(s); end
}

alt_btn_exit = gobj {
    nam = "alt_btn_exit",
    system_type = true,
    pic = "dice/gfx/alt_btn_exit.png",
    eff = "right-top@-40,875",
    morphout = "btn_exit",
    dsc = function(s) return _play_game_obj:txt(); end,
    ttpos = "s",
    act = function(s) if vn.stopped then stop_sound(1); _play_game_obj:act(); end; end,
    enablefn = function(s) return enablefn2(s); end
}

btn_dice2 = gobj {
    nam = "btn_dice2",
    system_type = true,
    pic = "dice/gfx/btn_dice2.png",
    eff = "right-bottom@-195,-440",
    onover = function(s) set_sound('dice/sfx/shake.ogg', 1, 0); return true; end,
    morphover = "alt_btn_dice2",
    enablefn = function(s) return enablefn(s); end
}

alt_btn_dice2 = gobj {
    nam = "alt_btn_dice2",
    system_type = true,
    pic = "dice/gfx/alt_btn_dice2.png",
    eff = "right-bottom@-195,-440",
    morphout = "btn_dice2",
    dsc = function(s) return dice:rollTxt(); end,
    act = function(s) if vn.stopped then s:onout(); dice:act(); end; end,
    onout = function(s) stop_sound(1); return true; end,
    enablefn = function(s) return enablefn(s); end
}

btn_next2 = gobj {
    nam = "btn_next2",
    system_type = true,
    pic = "dice/gfx/btn_next2.png",
    eff = "right-bottom@-195,-280",
    morphover = "alt_btn_next2",
    enablefn = function(s) return enablefn(s); end
}

alt_btn_next2 = gobj {
    nam = "alt_btn_next2",
    system_type = true,
    pic = "dice/gfx/alt_btn_next2.png",
    eff = "right-bottom@-195,-280",
    morphout = "btn_next2",
    dsc = function(s) return next_turn_obj:disp(); end,
    act = function(s) if vn.stopped then next_turn_obj:act(); end; end,
    enablefn = function(s) return enablefn(s); end
}

btn_inc2 = gobj {
    nam = "btn_inc2",
    system_type = true,
    pic = "dice/gfx/btn_inc2.png",
    eff = "right-bottom@-195,-180",
    morphover = "alt_btn_inc2",
    enablefn = function(s) return enablefn(s); end
}

alt_btn_inc2 = gobj {
    nam = "alt_btn_inc2",
    system_type = true,
    pic = "dice/gfx/alt_btn_inc2.png",
    eff = "right-bottom@-195,-180",
    morphout = "btn_inc2",
    dsc = function(s) return increase_bet_obj:txt(); end,
    act = function(s) if vn.stopped then increase_bet_obj:act(); end; end,
    enablefn = function(s) return enablefn(s); end
}

btn_exit2 = gobj {
    nam = "btn_exit2",
    system_type = true,
    pic = "dice/gfx/btn_exit2.png",
    eff = "right-bottom@-195,-10",
    morphover = "alt_btn_exit2",
    enablefn = function(s) return enablefn2(s); end
}

alt_btn_exit2 = gobj {
    nam = "alt_btn_exit2",
    system_type = true,
    pic = "dice/gfx/alt_btn_exit2.png",
    eff = "right-bottom@-195,-10",
    morphout = "btn_exit2",
    dsc = function(s) return _play_game_obj:txt(); end,
    act = function(s) if vn.stopped then stop_sound(1); _play_game_obj:act(); end; end,
    enablefn = function(s) return enablefn2(s); end
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
        if _export_lang == 'ru' then
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
            rollStat._rolls = nlb:shallowcopy(rolls);
            for k, v in pairs(s._rolls) do
                good_luck = good_luck and (v == 1);
                should_pass_turn = should_pass_turn or (v == 1);
                is_double = is_double and ((prev == 0) or (prev == v));
                s._curRollsByPlayer[pos] = s._curRollsByPlayer[pos] + v;
                prev = v;
            end
            vn:invalidate(paper);
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
                    vn:gshow(round_finished);
                    vn:gshow(reward);
                    set_sound('dice/sfx/money.ogg', nil, 1);
                    if s.data and not s:is_defined(rollStat.data.mainplr) then
                        return function()
                            vn:gshow(you_lose);
                            vn:startcb(function()
                                _play_game_obj:act();
                                return true;
                            end);
                            return true;
                        end
                    elseif s:only_one_player() then
                        return function()
                            vn:gshow(you_win);
                            vn:startcb(function()
                                _play_game_obj:act();
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
        if _export_lang == 'ru' then
            return 'Результат: 1 + 1, большая удача! Вы получаете +25 очков!';
        else
            return "Result: 1 + 1, you are very lucky! You've got +25!";
        end
    end,
    is_double_message = function(s, value)
        if _export_lang == 'ru' then
            return "Дубль, вам повезло! Вы получаете +" .. tostring(value) .. " очков!";
        else
            return "Double, you are lucky! You've got +" .. tostring(value);
        end
    end,
    should_pass_turn_message = function(s)
        if _export_lang == 'ru' then
            return "Результат: выпало 1, вам не повезло! Ход переходит к противнику.";
        else
            return "Result: 1 rolled, you are unlucky! Miss the turn.";
        end
    end,
    win_message = function(s, name)
        if _export_lang == 'ru' then
            return "Игрок " .. name .. " побеждает в этом раунде!";
        else
            return "Player " .. name .. " wins this round!";
        end
    end,
    get_player_string = function(s)
        if _export_lang == 'ru' then
            return "Игрок: ";
        else
            return "Player: ";
        end
    end,
    get_pts_string = function(s)
        if _export_lang == 'ru' then
            return "Очки: ";
        else
            return "Points: ";
        end
    end,
    get_money_string = function(s)
        if _export_lang == 'ru' then
            return "Деньги: ";
        else
            return "Money: ";
        end
    end,
    get_bet_string = function(s)
        if _export_lang == 'ru' then
            return "Ставка: ";
        else
            return "Stake: ";
        end
    end,
}

game_room = vnr {
    nam = true,
    nosave = true,
    system_type = true,
    ignore_preserved_gobjs = true,
    bgimg = function(s, bg)
        vn:scene(bg);
        vn:geom(8, 864, 1904, 184, 'dissolve', 240);
    end,
    dsc = function(s)
        return "";
    end,
    enter = function(s, f)
        _dlg_visible = true;
        rollStat.data = nlb:shallowcopy(game.data);
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
        if f ~= nil then
            s._returnto = stead.deref(f);
        end
        vn:turnon();
        paginator:turnoff();
        vn:lock_direct();
        rollStat:init();
        s:bgimg(game.table.bg);

        vn:preload_effect('dice/gfx/reward.png', 0, 270, 0, 270);

        vn:gshow(v_c7c2ac92_ac1b_4557_8265_8adddb054136);
        vn:gshow(_dices_help);

        local paper_eff = vn:gshow(paper);

        if (game.table.plate) then
            --set_music('dice/sfx/abu_ali.ogg');
            objs(paper):add(txt1);
            --vn:add_child(paper_eff, txt1); -- will be done automatically via vn:add_all_missing_children()
            vn:gshow(plate);
            vn:gshow(btn_dice2);
            vn:gshow(btn_next2);
            vn:gshow(btn_inc2);
            vn:gshow(btn_exit2);
        else
            --set_music('dice/sfx/tavernm.ogg');
            objs(paper):add(txt2);
            --vn:add_child(paper_eff, txt2); -- will be done automatically via vn:add_all_missing_children()
            vn:gshow(btn_dice);
            vn:gshow(btn_next);
            vn:gshow(btn_inc);
            vn:gshow(btn_exit);
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
    exit = function(s, t)
        stop_music();
        remove(rollStat, me());
        if rollStat.data then
            game.data = nlb:shallowcopy(rollStat.data);
            game.money = game.data.money[game.data.mainplr];
        end
        remove(_play_game_obj, me());
        vn:unlock_direct();
        _money = game.money;
        paginator:turnon();
    end,
    obj = { 'dice', 'next_turn_obj', 'increase_bet_obj' }
};

game.money = 150
game.defaultbet = 50
game.data = {
    ["mainplr"] = 2,
    ["names"] = {nil, "Ragnar", "Citizen", "Trader"},
    ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
    ["ai"] = {nil, nil, "aggressive", "cautious"},
    ["money"] = {0, game.money, 350, 500},
    ["bet"] = 50,
    ["threshold"] = 100
};
game.table = { ["bg"] = 'dice/gfx/table2.png', ["paper"] = 'dice/gfx/paper2.png' };

_play_bp_two_ais = menu {
    nam = "play_bp_two_ais",
    system_type = true,
    dsc = function(s) return "{Сыграть с Горожанином и Торговцем}^" end, 
    act = function(s)
        if _money then
            game.money = _money;
        end
        game.data = {
            ["mainplr"] = 2,
            ["names"] = {nil, "Ragnar", "Citizen", "Trader"},
            ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
            ["ai"] = {nil, nil, "aggressive", "cautious"},
            ["money"] = {0, game.money, 350, 500},
            ["bet"] = 50,
            ["threshold"] = 100
        };
        game.table.bg = 'dice/gfx/table2.png';
        game.table.paper = 'dice/gfx/paper2.png';
        game.table.plate = false;
        game.table.surftype = false;

        _play_game_obj:inv(); 
    end,
    actf = function(s) return s:act(); end
}

_play_bp_one_ai = menu {
    nam = "play_bp_one_ai",
    system_type = true,
    dsc = function(s) return "{Сыграть с Абу-Али}^" end, 
    act = function(s)
        if _money then
            game.money = _money;
        end
        game.data = {
            ["mainplr"] = 2,
            ["names"] = {nil, "Рагнар", nil, "Абу-Али"},
            ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
            ["ai"] = {nil, nil, nil, "optimum"},
            ["money"] = {0, game.money, 0, 1000},
            ["bet"] = 50,
            ["threshold"] = 100
        };
        game.table.bg = 'dice/gfx/table1.png';
        game.table.paper = 'dice/gfx/paper1.png';
        game.table.plate = 'dice/gfx/plate.png';
        game.table.surftype = 'stone';

        _play_game_obj:inv(); 
    end,
    actf = function(s) return s:act(); end
}

_play_bp_one_hotseat = menu {
    nam = "play_bp_one_hotseat",
    system_type = true,
    dsc = function(s) return "{Сыграть с другом}^" end, 
    act = function(s)
        if _money then
            game.money = _money;
        end
        game.data = {
            ["mainplr"] = 2,
            ["names"] = {nil, "Рагнар", nil, "Варвар"},
            ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
            ["ai"] = {nil, nil, nil, nil},
            ["money"] = {0, game.money, 0, game.money},
            ["bet"] = 50,
            ["threshold"] = 100
        };
        game.table.bg = 'dice/gfx/table1.png';
        game.table.paper = 'dice/gfx/paper1.png';
        game.table.plate = 'dice/gfx/plate.png';
        game.table.surftype = false;

        _play_game_obj:inv(); 

    end,
    actf = function(s) return s:act(); end
}

_play_bp_two_hotseats = menu {
    nam = "play_bp_two_hotseats",
    system_type = true,
    dsc = function(s) return "{Сыграть с двумя друзьями}^" end, 
    act = function(s)
        if _money then
            game.money = _money;
        end
        game.data = {
            ["mainplr"] = 2,
            ["names"] = {nil, "Рагнар", "Варвар", "Воин"},
            ["colors"] = {{nil, nil}, {"#ff0000", nil}, {"#ff0000", nil}, {"#ff0000", nil}},
            ["ai"] = {nil, nil, nil, nil},
            ["money"] = {0, game.money, game.money, game.money},
            ["bet"] = 50,
            ["threshold"] = 100
        };
        game.table.bg = 'dice/gfx/table2.png';
        game.table.paper = 'dice/gfx/paper2.png';
        game.table.plate = false;
        game.table.surftype = false;

        _play_game_obj:inv(); 
    end,
    actf = function(s) return s:act(); end
}

stead.module_init(function()
    rollStat:init();
    dicegames['setrolls'] = function() return rollStat:setrolls(); end;
    local tr = nlb:theme_root();
    local labelFont = sprite.font(tr .. 'fonts/STEINEMU.ttf', 48);
    if _export_lang == 'ru' then
        you_win_label = vn:label("Вы победили!", 40, "#ffffff", "black", 127, labelFont);
        you_lose_label = vn:label("Вы проиграли...", 40, "#ffffff", "black", 127, labelFont);
    else
        you_win_label = vn:label("You win!", 40, "#ffffff", "black", 127, labelFont);
        you_lose_label = vn:label("You lose...", 40, "#ffffff", "black", 127, labelFont);
    end
    sprite.free_font(labelFont);
    enablefn = function(v) return (rollStat.data and not rollStat.data.ai[dice.pos] and rollStat:is_defined(rollStat.data.mainplr)); end;
    enablefn2 = function(v) return (rollStat.data and not rollStat.data.ai[dice.pos]); end;
end)
