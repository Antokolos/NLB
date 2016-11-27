nlb = obj {
    nam = 'nlb';
    system_type = true;
    _fps = 25;
    _lists = {};
    _clones = {};
    _filter = {};
    _curloc = nil;
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
        setmetatable(res,mt);
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
};

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
end)