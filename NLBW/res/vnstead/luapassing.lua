local initFunc = function()
end

local setAchievementFunc = function(achievementName)
end

local storeFunc = function()
end

local clearAchievementFunc = function(achievementName)
end

function init()
    _statsInitDone = false;
end

statsAPI = {
    init = function()
        print("Initializing API...\n");
        if (_statsInitDone) then
            print("Already initialized!\n");
            return 0.0;
        else
            initFunc();
            _statsInitDone = true;
            print("API initialized.\n");
            return 1.0;
        end
    end,
    setAchievement = function(achievementName, storeImmediately)
        print("Setting achievement '"..achievementName.."'...\n");
        setAchievementFunc(achievementName);
        if (storeImmediately) then
            storeFunc();
        end
        print("Achievement set.\n");
        return 0.0;
    end,
    store = function()
        print("Storing...\n");
        storeFunc();
        print("Done.\n");
        return 0.0;
    end,
    clearAchievement = function(achievementName, storeImmediately)
        print("Clearing achievement '"..achievementName.."'...\n");
        clearAchievementFunc(achievementName);
        if (storeImmediately) then
            storeFunc();
        end
        print("Achievement cleared.\n");
        return 0.0;
    end,
    resetAll = function()
        print("Resetting all achievements...\n");
        resetAllFunc();
        print("Done.\n");
        return 0.0;
    end,
    openURL = function(url)
        p(url);
    end
}