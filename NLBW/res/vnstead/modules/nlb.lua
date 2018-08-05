require 'prefs'
require 'sprites'

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
        if not list then
            return nil;
        else
            s._lists[listname] = list.next;
            return list.value;
        end;
    end;
    inject = function(s, listname, v)
        local list = s._lists[listname];
        if not list then
            s._lists[listname] = {next = nil, value = v};
        else
            while list.next do
                list = list.next
            end;
            list.next = {next = nil, value = v};
        end;
    end;
    eject = function(s, listname)
        local list = s._lists[listname];
        local prevlist = list;
        local islast = true;
        if not list then
            return nil;
        else
            while list.next do
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
        while list do
            val = list.value;
            if (val.nlbid == v.nlbid) then
                if not prevlist then
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
        if not list then
            return 0;
        else
            repeat
                list = list.next;
                result = result + 1;
            until not list;
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
        if not loclist then
            return;
        else
            repeat
                if destination then
                    s:addf(destination, loclist.value, unique);
                elseif destinationList then
                    s:push(destinationList, loclist.value);
                else
                    s:addf(obj, loclist.value, unique);
                end;
                loclist = loclist.next;
            until not loclist;
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
        if not loclist then
            return nil;
        else
            repeat
                res[i] = loclist.value;
                loclist = loclist.next;
                i = i + 1;
            until not loclist;
        end;
        return res;
    end;
    addArr = function(s, listname, arr)
        local n = #arr
        for i=1,n do s:push(listname, arr[i]) end;
    end;
    usea = function(s, actionObject, targetObject)
        if actionObject and targetObject then
            if actionObject.usea then
                actionObject:usea(targetObject);
            end;
        end;
    end;
    acta = function(s, object)
        if object and object.acta then
            object:acta();
        end;
    end;
    actf = function(s, object)
        if object and object.actf then
            object:actf();
        end;
    end;
    clear = function(s, object)
        if object and object.clear then
            object:clear();
        end;
    end;
    addf = function(s, target, object, unique)
        if not target then
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
            if ores then
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
    ways_chk = function(s, r)
        if not r.wcns then return; end;
        for k, v in pairs(r.wcns) do
            if v() then
                enable(k);
            else
                disable(k);
            end
        end
    end;
    alts_txt = function(s, r)
        if not r.wcns then return ""; end;
        local result = "";
        for k, v in pairs(r.wcns) do
            if not v() then
                local t = r.alts[k];
                if t then
                    result = result.."^"..t();
                end
            end
        end
        return result;
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
        if not r then
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
        if not s._curloc then
            return here();
        else
            return s._curloc;
        end;
    end;
    lasttext = function(s)
        local lt = s:curloc().lasttext;
        if lt then
            return lt;
        else
            return '';
        end
    end;
    nlbwalk = function(s, src, tgt)
        vn:request_full_clear();
        if not src then
            src = s:curloc();
        end
        if (src ~= tgt) then
            lifeoff(src);
            lifeon(tgt);
            s._curloc = tgt;
            walk(tgt);
            if s._curloc.wastext then tgt.wastext = true; end;
        else
            local lasttext = src.lasttext;
            local wastext = src.wastext;
            if src.exit then
                src:exit(src);
            end
            if src.enter then
                src:enter(src);
            end
            src.wastext = wastext;
            src.lasttext = lasttext;
        end
    end;
    setAchievementMax = function(s, statsAPI, achievementName, max)
        prefs.achievements_max[achievementName] = max;
        prefs:store();
    end;
    setAchievement = function(s, statsAPI, achievementName, modificationId)
        -- There is no harm to call statsAPI.init() one more time,
        -- we are just trying to init Stats one more time in case of failure to init on start
        statsAPI.init();
        if not prefs.achievements_ids[achievementName][modificationId] then
            prefs.achievements_ids[achievementName][modificationId] = 1;
        else
            prefs.achievements_ids[achievementName][modificationId] = prefs.achievements_ids[achievementName][modificationId] + 1;
        end
        s:storeAchievement(statsAPI, achievementName);
        for k, v in pairs(prefs.achievements_ids) do
            local max = prefs.achievements_max[k] or 1;
            if k ~= prefs.achievementNamePerfectGame and tablelength(v) < max then
                prefs:store();
                return false;
            end
        end
        if prefs.achievementNamePerfectGame then
            prefs.achievements_ids[prefs.achievementNamePerfectGame][modificationId] = 1;
            statsAPI.setAchievement(prefs.achievementNamePerfectGame, true);
        end
        prefs:store();
        return true;
    end;
    storeAchievement = function(s, statsAPI, achievementName)
        if not prefs.achievements_max[achievementName] then
            statsAPI.setAchievement(achievementName, true);
        else
            local len = tablelength(prefs.achievements_ids[achievementName]);
            statsAPI.setAchievementProgress(achievementName, len, prefs.achievements_max[achievementName], true);
            statsAPI.setStat('STAT_' .. achievementName, len, true);
            if len >= prefs.achievements_max[achievementName] then
                statsAPI.setAchievement(achievementName, true);
            end
        end
    end;
    getAchievement = function(s, statsAPI, achievementName)
        return tablelength(prefs.achievements_ids[achievementName]);
    end;
    resendAchievements = function(s, statsAPI)
        for k, v in pairs(prefs.achievements_ids) do
            if tablelength(v) > 0 then
                s:storeAchievement(statsAPI, k);
            end
        end
        -- This code is for compatibility with previous achievements
        if prefs.achievements then
            for k, v in pairs(prefs.achievements) do
                if v > 0 then
                    s:setAchievement(statsAPI, k, "compat_id");
                end
            end
        end
    end;
    pdscf = function(s, ov)
        if ov and not ov:disabled() then
            local dscft = ov:dscf();
            if not dscft then return; end;
            s:curloc().lasttext = s:lasttext().." ".. dscft; p(dscft); s:curloc().wastext = true;
        end
    end;
    pdscs = function(s, ob)
        local objects = objs(ob);
        for i, o in ipairs(objects) do
            if o.suppress_dsc then
                s:pdscs(o);
                s:pdscf(o);
            end
        end
    end;
    theme_switch = function(s, theme_file, do_not_cleanup)
        if not theme_file then return; end;
        local theme_root = s:theme_root();
        dofile(theme_root .. theme_file);
        if not do_not_cleanup then
            vn:cleanup_scene();
        end
    end;
    theme_root = function(s)
        local theme_name = theme.name();
        return 'themes' .. theme_name:gsub('[\\.]', '/') .. '/';
    end;
    std_bg = function(s)
        local tr = s:theme_root();
        return tr .. 'gfx/bg.jpg';
    end;
    file_exists = function(s, name)
        local f = s:file_open(name);
        if f then
            io.close(f);
            return true;
        else
            return false;
        end
    end;
    file_open = function(s, name, mode)
        if not name then return nil; end;
        if not mode then
            mode = 'r';
        end
        return io.open(name, mode) or io.open(instead_gamepath() .. "/" .. name, mode);
    end;
    snapshot = function(s)
        if isSnapshot(0) then
            delete_snapshot(0);
        end
        make_snapshot(0);
    end;
};

function tablelength(t)
    if not t then
        return 0;
    end
    local count = 0
    for _ in pairs(t) do count = count + 1 end
    return count
end;

_try_again = menu {
    nam = "try_again",
    system_type = true,
    dsc = function(s)
        local tr = nlb:theme_root();
        if _export_lang == 'ru' then
            return img(tr .. 'gfx/theendru.png') .. txtc("^{Попробовать ещё раз}^");
        else
            return img(tr .. 'gfx/theenden.png') .. txtc("^{Try again}^");
        end
    end,
    act = function(s)
        if isSnapshot(0) then
            restore_snapshot(0);
        else
            stead.restart();
        end
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

nlbcmn = function(v)
    v.cont_alive = function(s)
        if not s.container then
            return true;
        end
        local cont = s.container();
        return not cont or not cont.alive or cont:alive();
    end;
    if not v.alive then
        v.alive = function(s)
            return s:cont_alive();
        end;
    end;
    v.life = function(s)
        local alive = s:alive() and s:cont_alive();
        if not alive then
            nlb._filter[stead.deref(s)] = true;
            s:disable();
        end;
    end;
    v.revive = function(s)
        local alive = s:alive() and s:cont_alive();
        if alive then
            nlb._filter[stead.deref(s)] = false;
            s:enable();
        end;
    end;
    return v;
end;

nlbobj = function(v)
    return obj(nlbcmn(v));
end;

nlbstat = function(v)
    return stat(nlbcmn(v));
end;

nlbmenu = function(v)
    return menu(nlbcmn(v));
end;

_syscall_hud_dark = menu {
    nam = "syscall_hud_dark",
    system_type = true,
    act = function(s)
        vn:set_hud_theme('dark');
        vn:set_hud_spacing();
    end,
    actf = function(s) return s:act(); end
}

_syscall_hud_dark_sparse = menu {
    nam = "syscall_hud_dark_sparse",
    system_type = true,
    act = function(s)
        vn:set_hud_theme('dark');
        vn:set_hud_spacing(25);
    end,
    actf = function(s) return s:act(); end
}

_syscall_hud_default = menu {
    nam = "syscall_hud_default",
    system_type = true,
    act = function(s)
        vn:set_hud_theme();
        vn:set_hud_spacing();
    end,
    actf = function(s) return s:act(); end
}

_syscall_showmenubtn  = menu {
    nam = "syscall_showmenubtn",
    system_type = true,
    act = function(s)
        local tr = nlb:theme_root();
        if _export_lang == 'ru' then
            local btn = sprite.load(tr .. 'gfx/menubtn_ru.png');
            local w, h = sprite.size(btn);
            sprite.free(btn);
            theme.menu.gfx.button(tr .. 'gfx/menubtn_ru.png', theme.get('scr.w') - w, 0);
        else
            local btn = sprite.load(tr .. 'gfx/menubtn.png');
            local w, h = sprite.size(btn);
            sprite.free(btn);
            theme.menu.gfx.button(tr .. 'gfx/menubtn.png', theme.get('scr.w') - w, 0);
        end
    end,
    actf = function(s) return s:act(); end
}

_syscall_starten = menu {
    nam = "syscall_starten",
    system_type = true,
    act = function(s)
        vn:show(vn.busy_spr, 'middle');
        vn:startcb(function()
            vn:unlock_direct();
            gamefile('module_en.lua', true);
        end);
    end,
    actf = function(s) return s:act(); end
}

_syscall_startru = menu {
    nam = "syscall_startru",
    system_type = true,
    act = function(s)
        vn:show(vn.busy_spr, 'middle');
        vn:startcb(function()
            vn:unlock_direct();
            gamefile('module_ru.lua', true);
        end);
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
        while r do
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
        if list then
            repeat
                list.value:acta();
                list = list.next;
            until not list;
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
        if list then
            repeat
                list.value:usea(w, ww);
                list = list.next;
            until not list;
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
        if list then
            repeat
                w:usea(list.value, list.value);
                list = list.next;
            until not list;
        end;
    end;
}

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

stead.module_init(function()
    nlb:rst();
end)