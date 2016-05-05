function splitIntoLines(str)
    local t = {}
    local function helper(line) table.insert(t, line) return "" end
    helper((str:gsub("(.-)^", helper)))
    return t
end

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
            if txt then
                local txts = splitIntoLines(txt);
                for i, t in ipairs(txts) do
                    stead.table.insert(result, { ["text"] = t, ["color"] = clr });
                end
            end
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
                    res = vno:gobf(use_src_obj):usefn(s);
                    vno.use_src = false;
                    vno.cursor_need_update = true;
                end
            end
            if res and type(res) == 'string' then
                p(res);
            end
        end;
    end
    if v.is_menu then
        v.usefn = nil;  -- ignore existing uses, if any
    elseif not v.usefn and v.use then
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
    if not v.clear_under_tooltip then
        v.clear_under_tooltip = false;
    end
    if not v.tooltipfn then
        v.tooltipfn = function(s)
            if not s.dsc then
                return nil;
            end
            local txt = s:dsc();
            if txt then
                local result = {};
                local txts = splitIntoLines(txt);
                for i, t in ipairs(txts) do
                    stead.table.insert(result, t);
                end
                return result, s.ttpos, s.clear_under_tooltip;
            end
            return nil, s.ttpos;
        end;
    end
    if not v.enablefn then
        v.enablefn = function(s)
            return true;
        end;
    end
    if not v.is_dynamic then
        v.is_dynamic = false;
    end
    if not v.is_paused then
        v.is_paused = false;
    end
    return obj(v);
end

gmenu = function(v)
    v.is_menu = true;
    return gobj(v);
end

arm_tostring = function(arm)
    if not arm then
        return "nil";
    end
    local res = "{";
    for k, v in pairs(arm) do
        res = res .. string.format("[%d] = {%d, %d}, ", k, v[1], v[2]);
    end
    res = res .. "}";
    return res;
end

init_gobj = function(image, eff, maxStep, startFrame, curStep, framesFromStop, arm, hot_step, acceleration)
    local constr_string = string.format(
        "gobj({['nam'] = '%s', ['pic'] = '%s', ['eff'] = '%s', ['maxStep'] = %s, ['startFrame'] = %s, ['curStep'] = %s, ['framesFromStop'] = %s, ['arm'] = %s, ['hot_step'] = %s, ['acceleration'] = %s, ['is_dynamic'] = true})",
        tostring(image), tostring(image), tostring(eff), tostring(maxStep), tostring(startFrame), tostring(curStep), tostring(framesFromStop), arm_tostring(arm), tostring(hot_step), tostring(acceleration)
    );
    return new(constr_string);
end

stead.module_init(function()
end)