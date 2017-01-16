-- dlgButtonOK_Out
v_53b088b4_4b84_4939_89a7_191607467229 = gmenu {
    system_type = true,
    var { tag = ''; container = function() return v_c7c2ac92_ac1b_4557_8265_8adddb054136; end; topmost = true; },
    nlbid = '53b088b4-4b84-4939-89a7-191607467229',
    deref = function(s) return stead.deref(v_53b088b4_4b84_4939_89a7_191607467229); end,
    nam = "dlgButtonOK_Out",
    eff = "left-top@0,0",
    iarm = { [0] = { 900.0, 0.0 } };
    snd = function(s)
    end,
    disp = function(s) end,
    dscf = function(s) end,
    dsc = function(s) return s.dscf(s); end,
    act = function(s)
        s:acta();
        return true;
    end,
    actt = function(s)
        return "";
    end,
    acta = function(s)
        s:actf();
        s:actcmn();
    end,
    actf = function(s)
    end,
    pic = function(s)
        if (true) then
            return 'gfx/dlgbuttonok_out.png';

        end
    end,
    imgv = function(s) return img(s.pic(s)); end,
    used = function(s, w)
    end,
    actcmn = function(s)
    end,
};

_dlgButtonOK_Out = v_53b088b4_4b84_4939_89a7_191607467229
-- dialogObj
v_c7c2ac92_ac1b_4557_8265_8adddb054136 = gmenu {
    system_type = true,
    var { tag = ''; container = function() return v_0e49cfc2_eb54_4db1_926d_79ce4d67836c; end; topmost = true; },
    nlbid = 'c7c2ac92-ac1b-4557-8265-8adddb054136',
    deref = function(s) return stead.deref(v_c7c2ac92_ac1b_4557_8265_8adddb054136); end,
    nam = "dialogObj",
    eff = "zoomin-left-bottom@0,0",
    arm = {[0] = {460, 140}},
    maxStep = 8,
    startFrame = 0,
    curStep = 0,
    snd = function(s)
    end,
    disp = function(s) end,
    dscf = function(s) end,
    dsc = function(s) return s.dscf(s); end,
    act = function(s)
        s:acta();
        return true;
    end,
    actt = function(s)
        return "";
    end,
    acta = function(s)
        s:actf();
        s:actcmn();
    end,
    actf = function(s)
        _dlg_visible =  not _dlg_visible;
        local v = vn:glookup(stead.deref(s));
        if s.is_paused then
            vn:vpause(v, false);
        else
            vn:set_step(v, nil, not v.forward);
        end
        vn:start();
    end,
    pic = function(s)
        if (true) then
            return 'gfx/dialog_bg.png';

        end
    end,
    imgv = function(s) return img(s.pic(s)); end,
    used = function(s, w)
    end,
    actcmn = function(s)
    end,
    obj = {
        'v_c757b8a3_210e_4966_8fb5_a853c8f0512c',
        'v_53b088b4_4b84_4939_89a7_191607467229',
    },
};

_dialogObj = v_c7c2ac92_ac1b_4557_8265_8adddb054136

_dices_help = gobj {
    nam = "dices_help",
    system_type = true,
    pic = "gfx/dices_help.png",
    eff = "left-top@40,875",
    morphover = "_alt_dices_help"
}

_alt_dices_help = gobj {
    nam = "alt_dices_help",
    system_type = true,
    pic = "gfx/dices_help_alt.png",
    eff = "left-top@40,875",
    morphout = "_dices_help",
    dsc = function(s) return "Help"; end,
    act = function(s) if vn.stopped then nlb:actf(v_c7c2ac92_ac1b_4557_8265_8adddb054136); end; end
}

-- dialogTextObj
v_c757b8a3_210e_4966_8fb5_a853c8f0512c = gmenu {
    system_type = true,
    var { tag = ''; container = function() return v_c7c2ac92_ac1b_4557_8265_8adddb054136; end; topmost = true; },
    nlbid = 'c757b8a3-210e-4966-8fb5-a853c8f0512c',
    cache_text = true,
    deref = function(s) return stead.deref(v_c757b8a3_210e_4966_8fb5_a853c8f0512c); end,
    nam = "dialogTextObj",
    eff = "left-top@0,0",
    arm = { [0] = { 0,0 } },
    iarm = { [0] = { 3.0, 404.0 } };
    snd = function(s)
    end,
    disp = function(s) if _export_lang == 'ru' then return s:disp_ru(); else return s:disp_en(); end end,
    disp_ru = function(s) return "Правила игры в кости «Большая свинья».^"..
            "Цель игры первым набрать 100 или больше очков.^"..
            "Вы бросаете две кости, полученное число складываете с уже имеющимся.^"..
            "Вы можете бросать кости до тех пор, пока сами не решите передать ход или^"..
            "пока вам не выпадет 1. В этом случае, набранные в текущей серии бросков очки^"..
            "аннулируются, а право хода автоматически передаётся  противнику. Но если единицы^"..
            "выпали на обоих кубиках, вы получаете сразу 25 очков и право хода по-прежнему^"..
            "остаётся за вами. Если выпал дубль из любых других чисел, то количество очков просто^"..
            "удваивается (например: выпало 3 и 3, значит вы получаете 12 очков).^"..
            "В каждом следующем раунде игры право первого броска получает победитель^"..
            "предыдущего раунда (то есть игрок, набравший 100 очков).^"..
            "Далее ход передаётся по часовой стрелке.^"..
            "У каждого игрока имеется некоторая сумма денег. Перед началом каждого раунда^"..
            "игроки должны сделать ставку. Минимальная ставка составляет 50 монет, но можно^"..
            "её удвоить. При этом максимальная ставка не может превышать количество монет,^"..
            "имеющееся у любого из игроков. (например: у одного игрока имеется 500 монет, у^"..
            "другого – 400 и у третьего – 300, следовательно, максимальная ставка будет 300).^"..
            "Помните, что удвоив ставку, вы уже не сможете поменять своё решение.^"..
            "В конце раунда победивший игрок забирает весь банк.^"..
            "Игра продолжается до тех пор, пока один игрок не завладеет всеми^"..
            "монетами своих соперников.^"..
            "Независимо от количества выигранных (проигранных) монет, вы можете^"..
            "выйти из игры в любой момент, когда получите право хода."; end,
    disp_en = function(s) return "\"Big Pig\" game rules:^"..
            "The objective is to get 100 points or more.^"..
            "You keep rolling two dice and points you receive are summed up. You can continue^"..
            "rolling until you decide to give up your turn to another player or until you get a die^"..
            "with 1 point. In that case, all points for your current turn fall to zero, and the turn^"..
            "automatically goes to another player.^"..
            "But if you get double 1’s, it gives you 25 points and the turn remains yours. If you get^"..
            "double of any other number, it gives you twice as many points as their combined total.^"..
            "(For example, if you roll two 3’s, the 6 points are doubled, thus giving you 12 points).^"..
            "In every new round, the winner of the previous, the player to have first gotten 100^"..
            "points or more, goes first.^"..
            "They pass the turn clockwise. Every player begins with some amount of money.^"..
            "Before every round all players shall stake a chosen amount of it. The minimum stake is^"..
            "50 coins, but it can be doubled. The maximum stake can\'t be more than the lowest^"..
            "amount of money that any of your opponents possess. (For example, if player A has^"..
            "500 coins, B has 400 coins and C has 300 coins, the maximum stake will be 300^"..
            "coins). Keep in mind, that once you\'ve doubled your stake, you won\'t be able to^"..
            "change your decision. In the end of every round the winner takes the bank.^"..
            "The game continues until one player wins all their opponents’ money. When the^"..
            "turn is yours, you can leave the game at any moment, no matter how successful^"..
            "it was."; end,
    dscf = function(s) end,
    dsc = function(s) return s.dscf(s); end,
    act = function(s)
        s:acta();
        return true;
    end,
    actt = function(s)
        return "";
    end,
    acta = function(s)
        s:actf();
        s:actcmn();
    end,
    actf = function(s)
    end,
    pic = function(s)
        if (true) then
            return 'gfx/dlg_text_bg.png';

        end
    end,
    imgv = function(s) return img(s.pic(s)); end,
    used = function(s, w)
    end,
    actcmn = function(s)
    end,
};

_dialogTextObj = v_c757b8a3_210e_4966_8fb5_a853c8f0512c

stead.module_init(function()
end)