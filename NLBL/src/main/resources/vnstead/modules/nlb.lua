require 'prefs'

nlb = obj {
    nam = 'nlb';
    system_type = true;
    _fps = 25;
    _lists = {};
    _clones = {};
    _filter = {};
    _curloc = nil;
    rst = function(s)
        s._lists = {};
        s._clones = {};
        s._filter = {};
        s._curloc = nil;
    end;
    push = function(s, listname, v)
        local list = s._lists[listname];
        s._lists[listname] = {next = list, value = v};
    end;
    pushObjs = function(s, listname, v)
        for k,vv in stead.opairs(objs(v)) do
            local o = stead.ref(vv);
            if (isObject(o)) then
                s:push(listname, o);
            end;
        end;
    end;
    pop = function(s, listname)
        local list = s._lists[listname];
        if list == nil then
            return nil;
        else
            s._lists[listname] = list.next;
            return list.value;
        end;
    end;
    inject = function(s, listname, v)
        local list = s._lists[listname];
        if list == nil then
            s._lists[listname] = {next = nil, value = v};
        else
            while list.next ~= nil do
                list = list.next
            end;
            list.next = {next = nil, value = v};
        end;
    end;
    eject = function(s, listname)
        local list = s._lists[listname];
        local prevlist = list;
        local islast = true;
        if list == nil then
            return nil;
        else
            while list.next ~= nil do
                prevlist = list
                list = list.next
                islast = false
            end;
            prevlist.next = nil;
            if islast then
                s._lists[listname] = nil;
            end;
            return list.value;
        end;
    end;
    rmv = function(s, listname, v)
        local list = s._lists[listname];
        local val;
        local prevlist = nil;
        while list ~= nil do
            val = list.value;
            if (val.nlbid == v.nlbid) then
                if (prevlist == nil) then
                    s._lists[listname] = list.next;
                    return;
                else
                    prevlist.next = list.next
                    return;
                end;
            end;
            prevlist = list;
            list = list.next;
        end;
    end;
    size = function(s, listname)
        local list = s._lists[listname];
        local result = 0;
        if list == nil then
            return 0;
        else
            repeat
                list = list.next;
                result = result + 1;
            until list == nil;
        end;
        return result;
    end;
    shuffle = function(s, listname)
        local arr = s:toArray(s._lists[listname]);
        s._lists[listname] = nil;
        s:addArr(listname, s:shuffled(arr));
    end;
    addAll = function(s, obj, destination, destinationList, listName, unique)
        local loclist = s._lists[listName];
        if loclist == nil then
            return;
        else
            repeat
                if destination ~= nil then
                    s:addf(destination, loclist.value, unique);
                elseif destinationList ~= nil then
                    s:push(destinationList, loclist.value);
                else
                    s:addf(obj, loclist.value, unique);
                end;
                loclist = loclist.next;
            until loclist == nil;
        end;
    end;
    shuffled = function(s, tab)
        local n, order, res = #tab, {}, {};
        for i=1,n do order[i] = { rand = rnd(n), idx = i } end;
        table.sort(order, function(a,b) return a.rand < b.rand end);
        for i=1,n do res[i] = tab[order[i].idx] end;
        return res;
    end;
    toArray = function(s, list)
        local res = {}
        local loclist = list;
        local i = 1;
        if loclist == nil then
            return nil;
        else
            repeat
                res[i] = loclist.value;
                loclist = loclist.next;
                i = i + 1;
            until loclist == nil;
        end;
        return res;
    end;
    addArr = function(s, listname, arr)
        local n = #arr
        for i=1,n do s:push(listname, arr[i]) end;
    end;
    usea = function(s, actionObject, targetObject)
        if actionObject ~= nil and targetObject ~= nil then
            if actionObject.usea ~= nil then
                actionObject:usea(targetObject);
            end;
        end;
    end;
    acta = function(s, object)
        if object ~= nil then
            if object.acta ~= nil then
                object:acta();
            end;
        end;
    end;
    actf = function(s, object)
        if object ~= nil then
            if object.actf ~= nil then
                object:actf();
            end;
        end;
    end;
    clear = function(s, object)
        if object ~= nil then
            if object.clear ~= nil then
                object:clear();
            end;
        end;
    end;
    addf = function(s, target, object, unique)
        if target == nil then
            if not have(object) then
                take(object);
            elseif not unique then
                take(s:clone(object));
            end;
        else
            local ores = nil;
            if not exist(object, target) then
                ores = object;
            elseif not unique then
                ores = s:clone(object);
            end;
            if ores ~= nil then
                ores.container = function() return target; end;
                objs(target):add(ores);
            end;
        end;
    end;
    clrcntnr = function(s, coll)
        -- TODO: add implementation (set container to nil func for every obj from coll)
    end;
    revive = function(s)
        for k,v in pairs(s._filter) do
            if v then
                local o = stead.ref(k);
                o:revive();
            end;
        end;
    end;
    shallowcopy = function(s, orig)
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
    end;
    deepcopy = function(s, t)
        local k; local v;
        if type(t) ~= "table" then return t end;
        local mt = getmetatable(t);
        local res = {};
        for k,v in pairs(t) do
            if type(v) == "table" and k ~= "container" then
                v = s:deepcopy(v)
            end;
            res[k] = v;
        end;
        setmetatable(res, mt);
        return res;
    end;
    clone = function(s, obj)
        if obj.nlbobj == "listobj" then
            return new('nlb:clonefdl(listobj, \''.. obj.listnam..'\')');
        else
            return new('nlb:clonef(\''.. obj:deref()..'\')');
        end;
    end;
    clonelst = function(s, obj, listnam)
        if obj.nlbobj == "listobj" then
            return new('nlb:clonefdl(listobj, \''..listnam..'\')');
        else
            return nil;
        end;
    end;
    clonef = function(s, sref)
        local obj = stead.ref(sref);
        return s:clonefd(obj);
    end;
    clonefd = function(s, obj)
        local ret = s:deepcopy(obj);
        local r = s._clones[obj.nlbid];
        if r == nil then
            r = 1;
        else
            r = r + 1;
        end;
        s._clones[obj.nlbid] = r;
        ret.nam = obj.nam..r;
        return ret;
    end;
    clonefdl = function(s, obj, listnam)
        local ret = s:clonefd(obj)
        ret.listnam = listnam;
        return ret;
    end;
    curloc = function(s)
        if s._curloc == nil then
            return here();
        else
            return s._curloc;
        end;
    end;
    nlbwalk = function(s, src, tgt)
        vn:request_full_clear();
        if not src then
            src = s:curloc();
        end
        if (src ~= tgt) then
            s._curloc = tgt;
            walk(tgt);
            if s._curloc.wastext then tgt.wastext = true; end;
        else
            local lasttext = src.lasttext;
            local wastext = src.wastext;
            if src.exit ~= nil then
                src:exit(src);
            end
            if src.enter ~= nil then
                src:enter(src);
            end
            src.wastext = wastext;
            src.lasttext = lasttext;
        end
    end;
    setAchievement = function(s, statsAPI, achievementName)
        -- There is no harm to call statsAPI.init() one more time,
        -- we are just trying to init Stats one more time in case of failure to init on start
        statsAPI.init();
        local achievementNamePerfectGame = prefs.achievementNamePerfectGame;
        if not prefs.achievements[achievementName] then
            prefs.achievements[achievementName] = 1;
        else
            prefs.achievements[achievementName] = prefs.achievements[achievementName] + 1;
        end
        statsAPI.setAchievement(achievementName, true);
        if achievementNamePerfectGame then
            prefs.achievements[achievementNamePerfectGame] = 1; -- let's assume we already got all achievements
        end
        for k, v in pairs(prefs.achievements) do
            if not v or (v <= 0) then
                if achievementNamePerfectGame then
                    prefs.achievements[achievementNamePerfectGame] = 0;
                end
                prefs:store();
                return false;
            end
        end
        if achievementNamePerfectGame then
            statsAPI.setAchievement(achievementNamePerfectGame, true);
        end
        prefs:store();
        return true;
    end;
    resendAchievements = function(s, statsAPI)
        for k, v in pairs(prefs.achievements) do
            if v and v > 0 then
                statsAPI.setAchievement(k, true);
            end
        end
    end;
};

_try_again = menu {
    nam = "try_again",
    system_type = true,
    dsc = function(s) return img 'blank:132x23'.."{Try again}^" end,
    act = function(s)
        stead.restart();
    end,
    actf = function(s) return s:act(); end
}

_syscall_hidemenubtn  = menu {
    nam = "syscall_hidemenubtn",
    system_type = true,
    act = function(s)
        theme.menu.gfx.button('gfx/empty.png', 0, 0);
    end,
    actf = function(s) return s:act(); end
}

_syscall_showmenubtn  = menu {
    nam = "syscall_showmenubtn",
    system_type = true,
    act = function(s)
        theme.menu.gfx.button('gfx/menubtn.png', 1827, 0);
    end,
    actf = function(s) return s:act(); end
}

_syscall_starten = menu {
    nam = "syscall_starten",
    system_type = true,
    act = function(s)
        gamefile('module_en.lua', true);
    end,
    actf = function(s) return s:act(); end
}

_syscall_startru = menu {
    nam = "syscall_startru",
    system_type = true,
    act = function(s)
        gamefile('module_ru.lua', true);
    end,
    actf = function(s) return s:act(); end
}

_syscall_menu = menu {
    nam = "syscall_menu",
    system_type = true,
    act = function(s)
        stead.menu_toggle();
    end,
    actf = function(s) return s:act(); end
}

_syscall_menu_save = menu {
    nam = "syscall_menu_save",
    system_type = true,
    act = function(s)
        stead.menu_toggle('save');
    end,
    actf = function(s) return s:act(); end
}

_syscall_menu_load = menu {
    nam = "syscall_menu_load",
    system_type = true,
    act = function(s)
        stead.menu_toggle('load');
    end,
    actf = function(s) return s:act(); end
}

_syscall_menu_exit = menu {
    nam = "syscall_menu_exit",
    system_type = true,
    act = function(s)
        stead.menu_toggle('exit');
    end,
    actf = function(s) return s:act(); end
}

listobj = {
    nam = "listobj",
    nlbobj = "listobj",
    nlbid = "listobj",
    deref = function(s) return stead.deref(listobj); end,
    listnam = "",
    clear = function(s)
        local r = nlb:eject(s.listnam);
        while r ~= nil do
            r = nlb:eject(s.listnam);
        end;
    end,
    act = function(s)
        s:acta()
    end,
    acta = function(s)
        s:actf()
    end,
    actf = function(s)
        local list = nlb._lists[s.listnam];
        if list ~= nil then
            repeat
                list.value:acta();
                list = list.next;
            until list == nil;
        end;
    end,
    use = function(s, w)
        s:usea(w, w);
    end,
    usea = function(s, w, ww)
        s:usef(w, ww);
    end,
    usef = function(s, w, ww)
        local list = nlb._lists[s.listnam];
        if list ~= nil then
            repeat
                list.value:usea(w, ww);
                list = list.next;
            until list == nil;
        end;
    end,
    used = function(s, w)
        s:useda(w);
    end,
    useda = function(s, w)
        s:usedf(w);
    end,
    usedf = function(s, w)
        local list = nlb._lists[s.listnam];
        if list ~= nil then
            repeat
                w:usea(list.value, list.value);
                list = list.next;
            until list == nil;
        end;
    end;
}

stead.module_init(function()
    nlb:rst();
end)