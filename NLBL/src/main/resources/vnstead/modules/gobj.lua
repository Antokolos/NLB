gobj = function(v)
    if not v.startFrame then
        v.startFrame = 0;
    end
    if not v.curStep then
        v.curStep = v.startFrame;
    end
    if not v.maxStep then
        v.maxStep = 0;
    end
    if not v.framesFromStop then
        v.framesFromStop = 0;
    end
    if not v.hot_step then
        v.hot_step = nil; -- maybe some default value here
    end
    if not v.acceleration then
        v.acceleration = 1;
    end
    if not v.arm then
        v.arm = { [0] = { 0, 0 } };
    end
    if not v.act then
        v.act = function(s) end;
    end
    if not v.txtfn then
        v.txtfn = function(s)
            if not s.disp then
                return {};
            end
            local txt = s:disp();
            local clr = 'black';
            local result = {};
            stead.table.insert(result, { ["text"] = txt, ["color"] = clr });
            return result;
        end;
    end
    if not v.onclick and v.act then
        v.onclick = function(s, vno)
            local res = nil;
            if s.act and not vno.use_src then
                res = s:act();
            end
            if s.usefn and not vno.use_src then
                vno:set_use_src()(s);
            else
                local use_src_obj = vno:glookup(vno.use_src);
                if use_src_obj then
                    res = use_src_obj.gob:usefn(s);
                    vno.use_src = false;
                    vno.cursor_need_update = true;
                end
            end
            if res and type(res) == 'string' then
                p(res);
            end
        end;
    end
    if not v.usefn and v.use then
        v.usefn = function(s, t)
            local res = s:use(t);
            if res then
                return res;
            elseif t.used then
                return t:used(s);
            end
            -- Maybe add nouse
        end
    end
    if not v.onover then
        v.onover = function(s) end;
    end
    if not v.onout then
        v.onout = function(s) end;
    end
    if not v.onhide then
        v.onhide = function(s) end;
    end
    if not v.morphover then
        v.morphover = false;
    end
    if not v.morphout then
        v.morphout = false;
    end
    if not v.ttpos then
        v.ttpos = "h";
    end
    if not v.tooltipfn then
        v.tooltipfn = function(s)
            if not s.dsc then
                return nil;
            end
            local txt = s:dsc();
            return txt, s.ttpos;
        end;
    end
    if not v.enablefn then
        v.enablefn = function(s)
            return true;
        end;
    end
    return obj(v);
end

init_gobj = function(image, eff, maxStep, startFrame, curStep, framesFromStop, arm, hot_step, acceleration)
    return gobj({
        ["nam"] = image,
        ["pic"] = image,
        ["eff"] = eff,
        ["maxStep"] = maxStep,
        ["startFrame"] = startFrame,
        ["curStep"] = curStep,
        ["framesFromStop"] = framesFromStop,
        ["arm"] = arm,
        ["hot_step"] = hot_step,
        ["acceleration"] = acceleration
    });
end

stead.module_init(function()
end)