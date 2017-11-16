require 'modules/log'
require 'modules/gr'

vnspr = function(w)
    local sprStep = w.sprStep;
    local v = w.v;
    local prefix = w.prefix;
    local extension = w.extension;
    local read_meta = function(name)
        if not name then
            return {};
        end
        local n = name .. ".meta";
        local f = nlb.file_open(n);
        local content = nil;
        if f then
            content = f:read("*all");
            f:close();
        end
        if content then
            local meta = stead.eval(content);
            return meta();
        else
            return {};
        end
    end;
    local is_sprite = function(obj)
        return obj.pic:match("^spr:");
    end;
    local is_virtual_empty = function(obj)
        return obj.pic:match("^virtual") and obj.pic:match("empty$");
    end;
    local meta, milestones, bgcolors = read_meta(prefix);

    w.val = function(s)
        if not s.cache then
            s.origin = s:get_origin();
            s.cache = s:prepare_effect(sprStep, s.origin);
        end
        return s.cache;
    end
    w.get_origin = function(s)
        if not s.origin then
            s.origin = s:load();
        end
        return s.origin;
    end
    w.visible = function(s)
        return s.alpha > 0 and s.scale > 0.0;
    end
    w.invisible = function(s)
        return not s:visible();
    end
    w.load = function(s)
        if is_sprite(v) then
            return v.pic;
        end
        if is_virtual_empty(v) then
            return vn.empty_s;
        end
        if vn:file_exists(v.pic) then
            if sprStep == vn:get_start(v) then
                return s:load_file(v.pic, prefix);
            elseif sprStep > vn:get_start(v) then
                v.spr[vn:get_start(v)]:val();
                return v.spr[vn:get_start(v)].origin;
            end
        else
            return s:load_frame();
        end
        return nil;
    end
    w.load_file = function(s, sprfile, key, idx, united, milestoneIdx)
        if not key then
            key = sprfile;
        end
        if not idx then
            idx = vn:get_start(v);
        end
        local startIdx = 0;
        if milestones then
            startIdx = milestones[milestoneIdx];
        end
        if (vn.sprite_cache[key] and vn.sprite_cache[key][idx]) then
            return vn.sprite_cache[key][idx];
        end
        local loaded;
        if united and vn.sprite_cache[key] and vn.sprite_cache[key][-milestoneIdx] then
            loaded = vn.sprite_cache[key][-milestoneIdx];
        else
            local tmp = gr:load(sprfile);
            if bgcolors then
                if bgcolors[milestoneIdx] then
                    local ws, hs = gr:size(tmp);
                    loaded = gr:box(ws, hs, bgcolors[milestoneIdx]);
                    gr:draw(tmp, loaded, 0, 0);
                    gr:free(tmp);
                else
                    loaded = tmp;
                end
            else
                loaded = tmp;
            end
        end
        if loaded then
            if not vn.sprite_cache[key] then
                vn.sprite_cache[key] = {};
            end
            if united then
                if not vn.sprite_cache[key][-milestoneIdx] then
                    vn.sprite_cache[key][-milestoneIdx] = loaded;
                end
                local ystart = 0;
                local w, h;
                if meta then
                    local cursize;
                    for i = startIdx,idx do
                        cursize = meta[i + 1];
                        if not cursize then
                            cursize = meta[1];
                        end
                        if h then
                            ystart = ystart + h;
                        end
                        if cursize then
                            local sw, sh = cursize:match("^(.*)x(.*)$");
                            w, h = tonumber(sw), tonumber(sh);
                        else
                            error("Metafile for " .. prefix .. " is not valid");
                        end
                    end
                end
                if not w or not h then
                    w, h = gr:size(loaded);
                    ystart = w * idx;
                    h = w;
                end
                vn.sprite_cache[key][idx] = {["loaded"] = loaded, ["x"] = 0, ["y"] = ystart, ["w"] = w, ["h"] = h};
            else
                vn.sprite_cache[key][idx] = loaded;
            end
        else
            error("Can not load sprite: " .. tostring(sprfile));
        end
        if idx > vn:get_start(v) then
            if not vn.sprite_cache_data[key] then
                local scd = {};
                scd[0] = v;
                scd[1] = idx;
                vn.sprite_cache_data[key] = scd;
            end
            vn.sprite_cache_data[key][1] = idx;
        end
        return vn.sprite_cache[key][idx];
    end
    w.load_frame = function(s)
        local sprfile = prefix .. '.' .. string.format("%04d", sprStep) .. extension;
        if vn:file_exists(sprfile) then
            return s:load_file(sprfile, prefix, sprStep);
        end
        local interval, milestoneIdx = vn:find_interval(milestones, sprStep, vn:get_max_step(v));
        local united_sprfile = prefix .. interval .. extension;
        if vn:file_exists(united_sprfile) then
            return s:load_file(united_sprfile, prefix, sprStep, true, milestoneIdx);
        elseif sprStep == start then
            error("Can not load key sprite (" .. sprfile .. " or " .. united_sprfile .. ")");
        elseif sprStep > vn:get_start(v) then
            return v.spr[sprStep - 1]:val();
        end
    end
    w.prepare_params = function(s, spr_step)
        local eff = vn:get_eff(v);
        if not eff then
            return;
        end
        local mxs, zstep = vn:steps(v, spr_step);
        log:dbg("Preparing parameters for " .. v.nam .. "; spr_step = " .. spr_step .. "; eff = " .. eff);
        if eff == 'fadein' then
            s.alpha = math.floor(255 * zstep / mxs);
        elseif eff == 'fadeout' then
            s.alpha = math.floor(255 * (1 - zstep / mxs));
        elseif eff == 'zoomin' or eff == 'zoomout' then
            s.scale = zstep / mxs;
            if eff == 'zoomout' then
                s.scale = 1.0 - s.scale;
            end
        end
    end
    w.prepare_effect = function(s, spr_step, base_spr)
        s:prepare_params(spr_step);
        if not vn.cache_effects then
            log:dbg("Do not preparing effects, because vn.cache_effects = " .. tostring(vn.cache_effects));
            return base_spr;
        end
        if base_spr.loaded then
            log:dbg("Do not preparing effects, because base_spr is composite image");
            return base_spr;
        end
        local eff = vn:get_eff(v);
        if eff == 'fadein' then
            s.preloaded_effect = true;
            return gr:alpha(base_spr, s.alpha);
        elseif eff == 'fadeout' then
            s.preloaded_effect = true;
            return gr:alpha(base_spr, s.alpha);
        elseif eff == 'zoomin' or eff == 'zoomout' then
            s.preloaded_effect = true;
            if s.scale > 0.0 then
                return gr:scale(base_spr, s.scale, s.scale, false);
            else
                return gr:blank(1, 1);
            end
        end
        log:dbg("Falling back to base_spr");
        return base_spr;
    end
    w.free = function(s)
        if s.preloaded_effect and s.cache then
            -- If effect was preloaded, then some additional sprite was created (via gr:scale, gr:alpha etc).
            -- It should be freed. If this sprite will be shown again, then origin can be restored from the sprite_cache,
            -- but this local cache will be recreated.
            gr:free(s.cache);
        end
        if vn.nocache and s.origin then
            -- Currently this code will not be executed, because cache is always used
            gr:free(s.origin);
        end
        s.cache = nil;
        s.origin = nil;
    end
    return obj(w);
end

stead.module_init(function()
end)