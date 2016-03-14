
dicegames = {
    ['setluck'] = function() luckStat:setluck(); end;
    ['getlucky'] = function() luckStat:getlucky(luckStat._threshold, luckStat._luckyObj, luckStat._unluckyObj); end;
}

-- If you want to implement your own dice game, you should tell the dice, how to set your roll callback properly
-- To do this, add the following to your dice game's module init:
-- dicegames['<FUNC_NAME>'] = function() <INITIALIZER_CODE> end;
-- You can find the example in big_pig.lua module

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