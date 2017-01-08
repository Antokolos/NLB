-- vn module
require 'timer'
require 'theme'
require 'sprites'
require 'modules/gobj'
require 'modules/log'

local win_reset = function()
    if not vn._win_get then
        return
    end
    theme.set('win.x', tonumber(vn.win_x));
    theme.set('win.y', tonumber(vn.win_y));
    theme.set('win.w', tonumber(vn.win_w));
    theme.set('win.h', tonumber(vn.win_h));
    theme.set('up.x', tonumber(vn.up_x));
    theme.set('down.x', tonumber(vn.down_x));
    vn._win_get = false
end
local win_get = function()
    local s = vn
    if s._win_get then
        return
    end
    s.win_x, s.win_y, s.win_w, s.win_h, s.up_x, s.down_x =
    theme.get 'win.x',
    theme.get 'win.y',
    theme.get 'win.w',
    theme.get 'win.h',
    theme.get 'up.x',
    theme.get 'down.x';
    s._win_get = true
end

game.timer = stead.hook(game.timer, function(f, s, cmd, ...)
    return vntimer(f, s, cmd, ...);
end)

game.fading = stead.hook(game.fading, function(f, s, cmd, ...)
    return vnfading(f, s, cmd, ...);
end)

vntimer = function(f, s, cmd, ...)
    if not vn.on then
        return f(s, cmd, ...)
    end
    local update_cursor_result = vn:update_cursor();

    vnticks_diff = get_ticks() - vnticks;
    if (vnticks_diff <= vn.hz) then
        if vn:preload() then
            return update_cursor_result;
        end
    end
    vn.slowcpu = (vnticks_diff > vn.ticks_threshold);
    log:trace("vnticks_diff = " .. vnticks_diff);
    vnticks = get_ticks();

    if vn.stopped then
        -- NB: do not put heavy code in onover/onout
        local x, y = stead.mouse_pos();
        vn:over(x, y);
        vn:out(x, y);
    end

    if vn.pause_frames > 0 then
        vn.pause_frames = vn.pause_frames - 1;
        return update_cursor_result;
    else
        local pausecb = vn.pause_callback;
        vn.pause_callback = false;
        if (pausecb) then
            pausecb();
            return update_cursor_result;
        end
    end
    if vn.bg_changing then
        if vn.bg_changing == 1 then
            theme.gfx.bg(vn.offscreen)
            vn.bg_changing = 2
        elseif vn.bg_changing == 2 then
            vn.bg_changing = false
            vn._scene_effect = false
            win_reset();
            vn:textbg(vn.offscreen)
            --- timer:stop()
            theme.gfx.bg(vn.offscreen)
        end
        RAW_TEXT = true;
        return game._lastdisp or "";
    end
    if not vn.stopped then
        if not vn:process() then
            RAW_TEXT = true;
            return game._lastdisp or "";
        end
    end
    if vn._need_update then
        vn._need_update = false;
        return true;
    end
    return update_cursor_result;
end

--if not game.old_fading then
--    game.old_fading = game.fading
--end

vnfading = function(f, s, cmd, ...)
    if not vn.on then
        return f(s, cmd, ...)
    end
    local b = vn.bg_changing
    if vn.skip_mode then return end
    --if game.old_fading(s) or b then
    if b then
        return vn.fading
    end
    return 2
end

image = function(name, pic)
    if not pic then
        return { nam = name, pic = name };
    end
    return { nam = name, pic = pic };
end

vn = obj {
    nam = 'vn';
    system_type = true;
    sprite_cache = {};
    sprite_cache_data = {};
    _effects = {};
    _pending_effects = {};
    _dirty_rects = {};
    _bg = false;
    _need_update = false;
    _wf = 0;
    _fln = nil;
    _frn = nil;
    cache_effects = true;
    tmr = 5;
    hz = 18;
    ticks_threshold = 36;
    slowcpu = false;
    var {
        on = true,
        stopped = true,
        partial_clear = true,
        speed = 500,
        fading = 8,
        bgalpha = 127,
        stp = 1,
        win_x = 0,
        win_y = 0,
        win_w = 0,
        win_h = 0,
        up_x = 0,
        down_x = 0,
        callback = false,
        extent = 5,
        default_label_extent = 4,
        default_tooltip_offset = 5,
        hud_color = '#000000',
        pause_frames = 0,
        pause_callback = false,
        direct_lock = false,
        use_src = false,
        cursor_need_update = false;
    };
    turnon = function(s)
        s.on = true;
    end,
    turnoff = function(s)
        s.on = false;
    end,
    screen = function(s)
        if theme.get('scr.gfx.mode') == 'direct' then
            return sprite.screen()
        else
            return s.offscreen
        end
    end;
    ini = function(s, load)
        s:request_full_clear();
        if not load or not s.on then
            return
        end
        local i, v, n
        for i, v in ipairs(s._effects) do
            v.hasmore = true;
            v.step = s:get_init_step(v);
            s:load_effect(v)
            if s:get_step(v) < s:get_max_step(v) - s:get_from_stop(v) then
                n = true
            end
        end
        s:set_bg(s._bg)
        s:add_all_missing_children();
        s:start()
    end;
    init = function(s)
        s.scr_w = theme.get 'scr.w'
        s.scr_h = theme.get 'scr.h'
        s.offscreen = sprite.blank(s.scr_w, s.scr_h)
        s.blackscreen = sprite.box(s.scr_w, s.scr_h, 'black')
        s:request_full_clear();
        timer:set(s.tmr);
    end;
    get_spr_rct = function(s, v)
        if v.last_rct then
            return v.last_rct;
        else
            return s:do_effect(v, true);
        end
    end;
    collision_base = function(s, v, dirty_rect)
        if not v or not dirty_rect then
            return false;
        end
        local vv = dirty_rect.v;
        --local morph_over = s:get_morph(v, true);
        --local morph_out = s:get_morph(v, false);
        --local info_over = s:get_base_info(morph_over);
        --local info_out = s:get_base_info(morph_out);
        if v.nam == vv.nam then --or info_over.nam == vv.nam or info_out.nam == vv.nam then
            -- there is no collision if it is the same object
            return false;
        end
        return true;
    end;
    --Input dirty_rect: dirty_rect to check for collision detection
    --Return: return true if the rectangles overlap otherwise return false
    collision = function(s, v, dirty_rect)
        if not s:collision_base(v, dirty_rect) then
            return false;
        end

        local rct = s:get_spr_rct(v);
        return s:collisionr(rct, dirty_rect);
    end;
    collisionrt = function(s, v, dirty_rect)
        if not v.last_rct or not dirty_rect then
            return false;
        end
        local rct = s:draw_hud(v.last_rct, true);
        return s:collisionr(rct, dirty_rect);
    end;
    collisionr = function(s, rct, dirty_rect)
        if not rct or not dirty_rect then
            return false;
        end
        local x, y, w, h = dirty_rect.x, dirty_rect.y, dirty_rect.w, dirty_rect.h;
        -- self bottom < other sprite top
        if rct.y + rct.h < y then
            return false
        end
        -- self top > other sprite bottom
        if rct.y > y + h then
            return false
        end
        -- self left > other sprite right
        if rct.x > x + w then
            return false
        end
        -- self right < other sprite left
        if rct.x + rct.w < x then
            return false
        end

        return true
    end;
    inside_spr = function(s, v, x, y)
        -- Click falls inside this picture
        local rct = s:get_spr_rct(v);
        return x >= rct.x and x <= rct.x + rct.w and y >= rct.y and y <= rct.y + rct.h;
    end;
    test_click = function(s, x, y)
        for i, v in ipairs(s._effects) do
            if not s:is_inactive_due_to_anim_state(v) and s:inside_spr(v, x, y) then
                return true;
            end
        end
        return false;
    end;
    click = function(s, x, y, a, b, c, d)
        if not s.on then
            return;
        end
        for i, v in ipairs(s._effects) do
            local active = not s:is_inactive_due_to_anim_state(v);
            if active and s:gobf(v).onclick and s:enabled(v) and s:inside_spr(v, x, y) then
                s:gobf(v):onclick(s);
            end
        end
    end;
    get_morph = function(s, v, is_over)
        local gob = s:gobf(v);
        local morph;
        if is_over then
            local morphover = gob.morphover;
            if type(morphover) == 'function' then
                morphover = morphover();
            end
            morph = stead.ref(morphover);
        else
            local morphout = gob.morphout;
            if type(morphout) == 'function' then
                morphout = morphout();
            end
            morph = stead.ref(morphout);
        end
        return morph;
    end;
    shapechange = function(s, v, is_over)
        local morph = s:get_morph(v, is_over);
        if not morph then
            return false;
        end
        s:hide(v);
        s:effect_int(nil, morph, is_over);
        s:start();
        return true;
    end;
    over = function(s, x, y, a, b, c, d)
        if not s.on then
            return;
        end
        for i, v in ipairs(s._effects) do
            local active = not s:is_inactive_due_to_anim_state(v);
            if active and s:gobf(v).onover and s:enabled(v) and not v.mouse_over and s:inside_spr(v, x, y) then
                s:gobf(v):onover();
                s:update_tooltip(v);
                if not s:shapechange(v, true) then
                    v.mouse_over = true;
                    if s.stopped then
                        s.stopped = false;
                    end
                end
            end
        end
    end;
    out = function(s, x, y, a, b, c, d)
        if not s.on then
            return;
        end
        for i, v in ipairs(s._effects) do
            -- Don't doing not s:is_inactive_due_to_anim_state(v); check, because I want to always hide tooltip
            if s:gobf(v).onout and s:enabled(v) and v.mouse_over and not s:inside_spr(v, x, y) then
                s:outf(v);
            end
        end
    end;
    outf = function(s, v)
        s:gobf(v):onout();
        s:update_tooltip(v, true, true);
        if not s:shapechange(v, false) then
            v.mouse_over = false;
            if s.stopped then
                s.stopped = false;
            end
        end
    end;
    request_full_clear = function(s)
        s.partial_clear = false;
    end;
    need_update = function(s)
        s._need_update = true;
    end;
    is_sprite = function(s, obj)
        return obj.pic:match("^spr:");
    end;
    file_exists = function(s, name)
        local f = io.open(name, "r")
        if f ~= nil then io.close(f) return true else return false end
    end;
    read_meta = function(s, name)
        if not name then
            return {};
        end
        local n = name .. ".meta";
        local f = io.open(n, "r");
        local content = nil;
        if f then
            content = f:read("*all");
            f:close();
        end
        if content then
            local meta = loadstring(content);
            return meta();
        else
            return {};
        end
    end;
    split_url = function(s, url)
        -- Splits the url into main part and extension part
        return url:match("^(.+)(%..+)$")
    end;
    busy = function(s, busy, job, callback_to_run_after, do_not_show_label)
        if busy then
            if not do_not_show_label then
                vn:show(busy_spr, 'middle');
            end
            if job then
                local cb = function()
                    job();
                    if not do_not_show_label then
                        vn:hide(busy_spr);
                    end
                    if callback_to_run_after then
                        return callback_to_run_after;
                    else
                        return true;
                    end
                end
                vn:startcb(cb);
            else
                vn:start();
            end
        else
            if not do_not_show_label then
                vn:hide(busy_spr);
            end
            vn:start();
        end
    end;
    preload_effect = function(s, image, startFrame, maxStep, fromStop, maxPreload, callback_to_run_after, do_not_show_label)
        local job = function()
            if not fromStop then
                fromStop = 0;
            end
            if not maxPreload then
                maxPreload = startFrame + 2;
            end
            local v = { pic = image, nam = image, start = startFrame, max_step = maxStep, from_stop = fromStop };
            s:load_effect(v);
            for i = startFrame,maxPreload do
                v.spr[i]:val();
            end
        end;
        s:busy(true, job, callback_to_run_after, do_not_show_label);
    end;
    find_interval = function(s, milestones, sprStep, max_step)
        local mstl = 0;
        local msth = max_step + 1;
        local mstIdx = 1;
        if milestones then
            for i=1,#milestones do
                mst = milestones[i];
                if sprStep >= mst then
                    mstl = mst;
                    mstIdx = i;
                end
                if sprStep < mst then
                    msth = mst;
                    mstIdx = i - 1;
                    break;
                end
            end
        end
        return string.format(".%04d-%04d", mstl, msth - 1), mstIdx;
    end;
    load_effect = function(s, v)
        local ss = s;
        if v.spr == nil then v.spr = {}; end;
        -- Will return nils if v is sprite
        local prefix, extension = s:split_url(v.pic);
        local meta, milestones, bgcolors = s:read_meta(prefix);
        local start = s:get_start(v);
        local maxstep = s:get_max_step(v);
        log:dbg("Loading effect " .. v.nam .. "; start = " .. start .. "; maxstep = " .. maxstep);
        for sprStep = start, maxstep do
            v.spr[sprStep] = {
                was_loaded = false,
                preloaded_effect = false,
                alpha = 255,
                scale = 1.0,
                origin = false,
                cache = false,
                val = function(s)
                    if not s.cache then
                        s.origin = s:get_origin();
                        s.cache = s:prepare_effect(sprStep, s.origin);
                    end
                    return s.cache;
                end,
                get_origin = function(s)
                    if not s.origin then
                        s.origin = s:load();
                    end
                    return s.origin;
                end,
                load = function(s)
                    if ss:is_sprite(v) then
                        return v.pic;
                    end
                    if ss:file_exists(v.pic) then
                        if sprStep == ss:get_start(v) then
                            return s:load_file(v.pic);
                        elseif sprStep > ss:get_start(v) then
                            v.spr[ss:get_start(v)]:val();
                            return v.spr[ss:get_start(v)].origin;
                        end
                    else
                        return s:load_frame();
                    end
                    return nil;
                end,
                load_file = function(s, sprfile, key, idx, united, milestoneIdx)
                    if not key then
                        key = sprfile;
                    end
                    if not idx then
                        idx = ss:get_start(v);
                    end
                    local startIdx = 0;
                    if milestones then
                        startIdx = milestones[milestoneIdx];
                    end
                    if (ss.sprite_cache[key] and ss.sprite_cache[key][idx]) then
                        return ss.sprite_cache[key][idx];
                    end
                    local loaded;
                    if united and ss.sprite_cache[key] and ss.sprite_cache[key][-milestoneIdx] then
                        loaded = ss.sprite_cache[key][-milestoneIdx];
                    else
                        local tmp = sprite.load(sprfile);
                        if bgcolors then
                            if bgcolors[milestoneIdx] then
                                local ws, hs = sprite.size(tmp);
                                loaded = sprite.box(ws, hs, bgcolors[milestoneIdx]);
                                sprite.draw(tmp, loaded, 0, 0);
                                sprite.free(tmp);
                            else
                                loaded = tmp;
                            end
                        else
                            loaded = tmp;
                        end
                    end
                    if loaded then
                        s.was_loaded = true;
                        if not ss.sprite_cache[key] then
                            ss.sprite_cache[key] = {};
                        end
                        if united then
                            if not ss.sprite_cache[key][-milestoneIdx] then
                                ss.sprite_cache[key][-milestoneIdx] = loaded;
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
                                        sw, sh = cursize:match("^(.*)x(.*)$");
                                        w, h = tonumber(sw), tonumber(sh);
                                    else
                                        error("Metafile for " .. prefix .. " is not valid");
                                    end
                                end
                            end
                            if not w or not h then
                                w, h = sprite.size(loaded);
                                ystart = w * idx;
                                h = w;
                            end
                            ss.sprite_cache[key][idx] = {["loaded"] = loaded, ["x"] = 0, ["y"] = ystart, ["w"] = w, ["h"] = h};
                        else
                            ss.sprite_cache[key][idx] = loaded;
                        end
                    else
                        error("Can not load sprite: " .. tostring(sprfile));
                    end
                    if idx > ss:get_start(v) then
                        if not ss.sprite_cache_data[key] then
                            local scd = {};
                            scd[0] = v;
                            scd[1] = idx;
                            ss.sprite_cache_data[key] = scd;
                        end
                        ss.sprite_cache_data[key][1] = idx;
                    end
                    return ss.sprite_cache[key][idx];
                end,
                load_frame = function(s)
                    local sprfile = prefix .. '.' .. string.format("%04d", sprStep) .. extension;
                    if ss:file_exists(sprfile) then
                        return s:load_file(sprfile, prefix, sprStep);
                    end
                    local interval, milestoneIdx = ss:find_interval(milestones, sprStep, ss:get_max_step(v));
                    local united_sprfile = prefix .. interval .. extension;
                    if ss:file_exists(united_sprfile) then
                        return s:load_file(united_sprfile, prefix, sprStep, true, milestoneIdx);
                    elseif sprStep == start then
                        error("Can not load key sprite (" .. sprfile .. " or " .. united_sprfile .. ")");
                    elseif sprStep > ss:get_start(v) then
                        return v.spr[sprStep - 1]:val();
                    end
                end,
                prepare_params = function(s, spr_step)                    
                    local eff = ss:get_eff(v);
                    if not eff then
                        return;
                    end
                    local mxs, zstep = ss:steps(v, spr_step);
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
                end,
                prepare_effect = function(s, spr_step, base_spr)
                    s:prepare_params(spr_step);
                    if not ss.cache_effects then
                        log:dbg("Do not preparing effects, because vn.cache_effects = " .. tostring(ss.cache_effects));
                        return base_spr;
                    end
                    if base_spr.loaded then
                        log:dbg("Do not preparing effects, because base_spr is composite image");
                        return base_spr;
                    end
                    local eff = ss:get_eff(v);
                    if eff == 'fadein' then
                        s.preloaded_effect = true;                        
                        return sprite.alpha(base_spr, s.alpha); 
                    elseif eff == 'fadeout' then
                        s.preloaded_effect = true;
                        return sprite.alpha(base_spr, s.alpha);
                    elseif eff == 'zoomin' or eff == 'zoomout' then
                        s.preloaded_effect = true;
                        if s.scale > 0.0 then
                            return sprite.scale(base_spr, s.scale, s.scale, false);
                        else
                            return sprite.blank(1, 1);
                        end
                    end
                    log:dbg("Falling back to base_spr");
                    return base_spr;
                end,
                free = function(s)
                    if s.was_loaded and s.origin then
                        if s.preloaded_effect and s.cache then
                            -- If effect was preloaded, then some additional sprite was created (via sprite.scale, sprite.alpha etc).
                            -- It should be freed. If this sprite will be shown again, then origin can be restored from the sprite_cache,
                            -- but this local cache will be recreated.
                            sprite.free(s.cache);
                        end
                        if ss.nocache then
                            -- Currently this code will not be executed, because cache is always used
                            sprite.free(s.origin);
                        end
                        s.cache = nil;
                        s.origin = nil;
                    end
                end
            };
        end
    end;
    free_effect = function(s, v)
        for i, vv in ipairs(v.spr) do
            vv:free();
        end
        local gob = s:gobf(v);
        if gob and gob.is_dynamic then
            delete(gob);
        end
        v.spr = nil;
    end;
    preload = function(s)
        for k, w in pairs(s.sprite_cache_data) do
            local v = w[0];
            local lastIdx = w[1];
            -- v.spr can be nil if free_effect() was already called
            if (v.spr and (lastIdx > s:get_start(v)) and (lastIdx < (s:get_max_step(v) - s:get_from_stop(v)))) then
                for i = lastIdx + 1, (s:get_max_step(v) - s:get_from_stop(v)) do
                    if v.spr[i] then
                        v.spr[i]:val();
                    else
                        -- This error should actually never appear
                        log:err("Error preloading sprite " .. v.pic .. "@" .. i);
                    end
                    log:dbg("preloaded " .. v.nam .. "@" .. i);
                    if (get_ticks() - vnticks > vn.hz) then
                        return false;
                    end
                end
            else
                s.sprite_cache_data[k] = nil;
            end
            if (get_ticks() - vnticks > vn.hz) then
                return false;
            end
        end
        return true;
    end;
    -- Call clear_cache periodically to free memory from possible garbage...
    clear_cache = function(s)
        for k, w in pairs(s.sprite_cache) do
            for i, ss in ipairs(w) do
                if not ss.loaded then
                    sprite.free(ss);
                end
            end
        end
        s.sprite_cache = {};
        s.sprite_cache_data = {};
    end;
    hide = function(s, image, eff, ...)
        local v
        local nam
        if type(image) == 'string' then
            nam = image
        else
            nam = image.nam
        end
        if not nam then return end

        if s.skip_mode then eff = false end

        if eff then
            return s:effect(image, eff, ...)
        end

        local i, k = s:lookup(nam)
        if not i then return end
        if s:gobf(i).onhide then
            s:gobf(i):onhide();
        end
        local parent = s:parentf(i);
        -- NB: parent should fully contain its child
        if parent then
            s:remove_child(parent, i);
            parent.hasmore = true;
            s.stopped = false;
        else
            s:clear_bg_under_sprite(i, true);
        end
        stead.table.remove(s._effects, k);
        for ii, vv in ipairs(i.children) do
            s:hide(s:childf(vv), eff, ...);
        end
        s:free_effect(i);
        return
    end;

    show = function(s, ...)
        return s:effect(...)
    end;

    gshow = function(s, gob)
        return s:effect_int(nil, gob);
    end;

    lookup = function(s, n)
        if not n then return end
        local i, k
        for i, k in ipairs(s._effects) do
            if k.nam == n then
                return k, i
            end
        end
    end;

    lookup_full = function(s, n)
        if not n then return end
        local kk, ii = s:lookup(n);
        if kk then
            return kk, ii;
        end
        local i, k
        for i, k in ipairs(s._pending_effects) do
            if k.nam == n then
                return k, i
            end
        end
    end;

    glookup_full = function(s, n)
        if not n then return end
        local kk, ii = s:glookup(n);
        if kk then
            return kk, ii;
        end
        local i, k
        for i, k in ipairs(s._pending_effects) do
            if k.gob == n then
                return k, i
            end
        end
    end;

    glookup = function(s, n)
        if not n then return end
        local i, k
        for i, k in ipairs(s._effects) do
            if k.gob == n then
                return k, i
            end
        end
    end;

    real_size = function(s, v, idx)
        local xarm, yarm = s:abs_arm(v, idx);
        local sp = s:frame(v, idx);
        if sp.tmp then
            sprite.free(sp.spr);
        end
        return sp.w + xarm, sp.h + yarm;
    end;

    -- return max size through hierarchy
    max_size = function(s, v, idx)
        local resx, resy = s:real_size(v, idx);
        for i, cnam in ipairs(v.children) do
            local vvx, vvy = s:max_size(s:childf(cnam), idx);
            if vvx > resx then
                resx = vvx;
            end
            if vvy > resy then
                resy = vvy;
            end
        end
        return resx, resy;
    end;

    size = function(s, v, idx)
        local root = s:rootf(v);
        return s:max_size(root, idx);
    end;

    rel_arm = function(s, v, idx)
        return s:arm_by_idx(v, idx, 1), s:arm_by_idx(v, idx, 2);
    end;

    abs_arm = function(s, v, idx)
        local parent = s:parentf(v);
        local px, py = 0, 0;
        if parent then
            px, py = s:abs_arm(parent, idx);
        end
        local rx, ry = s:rel_arm(v, idx);
        return rx + px, ry + py;
    end;

    total_rel_arm = function(s, v, idx)
        local parent = s:parentf(v);
        local px, py = 0, 0;
        if parent then
            px, py = s:total_rel_arm(parent, idx);
        else
            return 0, 0;
        end
        local rx, ry = s:rel_arm(v, idx);
        return rx + px, ry + py;
    end;

    arm = function(s, v, idx)
        return s:abs_arm(v, idx);
    end;

    arm_by_idx = function(s, v, idx, subidx)
        local varm = s:gobf(v).arm;
        if not varm then
            return 0;
        end
        if type(varm) == 'function' then
            varm = s:gobf(v):arm();
        end
        local arm = varm[idx];
        if not arm then
            arm = varm[0];
        end
        if arm[subidx] then
            return arm[subidx];
        else
            return 0;
        end
    end;

    steps = function(s, v, cur_stp)
        if not cur_stp then
            cur_stp = s:get_step(v);
        end
        local mxs = s:get_max_step(v) - s:get_from_stop(v) - s:get_start(v);
        log:trace("vstep="..tostring(s:get_step(v)).."; vstart="..tostring(s:get_start(v)));
        local zstep = cur_stp - s:get_start(v);
        return mxs, zstep;
    end;

    update_cursor = function(s)
        if s.cursor_need_update then
            if s.use_src then
                local use_cursor = theme.get("scr.gfx.cursor.use");
                theme.set("scr.gfx.cursor.normal", use_cursor);
            else
                theme.reset("scr.gfx.cursor.normal");
            end
            s.cursor_need_update = false;
            RAW_TEXT = true;
            return game._lastdisp or "";
        end
    end;

    set_use_src = function(s)
        return function(gobl)
            if s.stopped then
                s.use_src = stead.deref(gobl);
                s.cursor_need_update = true;
            end;
        end
    end;

    effect = function(s, image, eff, speed, startFrame, curStep, framesFromStop, arm, hot_step, acceleration, is_preserved, dirty_draw)
        local maxStep = math.floor((speed or s.speed) / s.hz);
        local gg = init_gobj(image, eff, maxStep, startFrame, curStep, framesFromStop, arm, hot_step, acceleration, is_preserved, dirty_draw);
        return s:effect_int(nil, gg);
    end;

    gobf = function(s, v) return stead.ref(v.gob); end;

    parentf = function(s, v) return s:lookup_full(v.parent); end;

    rootf = function(s, v)
        local vv = s:parentf(v);
        if vv then
            return s:rootf(vv);
        else
            return v;
        end
    end;

    childf = function(s, v_nam)
        local ch = s:lookup_full(v_nam);
        return ch;
    end;

    get_start = function(s, v) return s:rootf(v).start; end;

    get_init_step = function(s, v) return s:rootf(v).init_step; end;

    get_step = function(s, v) return s:rootf(v).step; end;

    get_max_step = function(s, v) return s:rootf(v).max_step; end;

    get_from_stop = function(s, v) return s:rootf(v).from_stop; end;

    get_hotstep = function(s, v) return s:rootf(v).hotstep; end;

    get_accel = function(s, v) return s:rootf(v).accel; end;

    get_eff = function(s, v) return s:rootf(v).eff; end;

    get_from = function(s, v) return s:rootf(v).from; end;

    get_pos = function(s, v) return s:rootf(v).pos; end;

    get_forward = function(s, v) return s:rootf(v).forward; end;

    set_hasmore_all = function(s, v, hasmore)
        v.hasmore = hasmore;
        for ii, vv in ipairs(v.children) do
            s:set_hasmore_all(s:childf(vv), hasmore);
        end
    end;

    invalidate = function(s, gobj)
        local v = s:glookup_full(stead.deref(gobj));
        if v then
            s:set_hasmore_all(v, true);
            s.stopped = false;
        end
    end;

    invalidate_all = function(s)
        for i, v in ipairs(s._effects) do
            v.hasmore = true;
        end
        for i, v in ipairs(s._pending_effects) do
            v.hasmore = true;
        end

    end;

    get_base_info = function(s, g)
        if not g then
            return {["picture"] = false, ["name"] = false};
        end
        local image;
        if type(g.pic) == 'function' then
            image = g:pic();
        else
            image = g.pic;
        end
        local v;
        if type(image) == 'string' then
            v = { pic = image, nam = image };
            image = v
        end
        return {["picture"] = image.pic, ["name"] = image.nam};
    end;

    effect_int = function(s, parent_eff, g, is_over)
        local info = s:get_base_info(g);
        local eff = g.eff;
        local maxStep = g.maxStep;
        local t = eff;

        if not is_over then
            is_over = false;
        end

        local is_preserved = g.preserved;
        if not is_preserved then
            is_preserved = false;
        end

        local dirty_draw = g.dirty_draw;
        if not dirty_draw then
            dirty_draw = false;
        end

        local parent_nam = false;
        if parent_eff then
            parent_nam = parent_eff.nam;
        end
        local v = {
            parent = parent_nam,
            newborn = true,
            was_disabled = false,
            hasmore = true,
            pic = info.picture,
            nam = info.name,
            eff = t,
            forward = true,
            init_step = g.curStep,
            step = g.curStep,
            start = g.startFrame,
            from_stop = g.framesFromStop,
            hotstep = g.hot_step,
            accel = g.acceleration,
            mouse_over = is_over,
            gob = stead.deref(g),
            preserved = is_preserved,
            dirty_draw = dirty_draw,
            last_rct = false
            --children = {} - actually can be set here, but I'll set it later, after possible hide() call
        }

        if eff == 'hide' then
            s:hide(v)
            return
        end

        v.children = {}

        local i, k
        local old_pos

        local oe = s:lookup(v.nam)

        if not eff then
            eff = ''
        end
        v.from = ''
        if eff:find("moveinleft") or eff:find("moveoutleft") then
            v.from = 'left'
        elseif eff:find("moveinright") or eff:find("moveoutright") then
            v.from = 'right'
        elseif eff:find("moveintop") or eff:find("moveouttop") then
            v.from = 'top'
        elseif eff:find("moveinbottom") or eff:find("moveoutbottom") then
            v.from = 'bottom'
        end

        if eff:find("movein") then
            v.eff = 'movein'
        elseif eff:find("moveout") then
            v.eff = 'moveout'
        elseif eff:find("fadein") then
            v.eff = 'fadein'
        elseif eff:find("fadeout") then
            v.eff = 'fadeout'
        elseif eff:find("zoomin") then
            v.eff = 'zoomin'
        elseif eff:find("zoomout") then
            v.eff = 'zoomout'
        elseif eff:find("reverse") then
            v.eff = 'reverse';
        elseif eff:find("overlap") then
            v.eff = 'overlap';
        else
            v.eff = 'none'
        end

        v.step = g.curStep;
        v.start = g.startFrame;
        v.max_step = maxStep;
        v.from_stop = g.framesFromStop
        if oe then
            if oe.pic ~= v.pic then -- new pic
                s:free_effect(oe);
                oe.pic = v.pic
                s:load_effect(oe)
            end
            old_pos = s:get_pos(oe);
            v = oe
        else
            s:load_effect(v)
        end

        if s.skip_mode then v.eff = 'none' end

        if s:get_eff(v) == 'none' then
            v.pos = eff
        else
            if eff:find("%-") then
                v.pos = eff:gsub("^[^%-]*%-", "");
            else
                v.pos = ''
            end
        end

        if v.pos == '' and old_pos then
            v.pos = old_pos
        end

        if not oe then
            stead.table.insert(s._pending_effects, v)
        end

        s:add_missing_children(v);

        return v
    end;

    add_all_missing_children = function(s)
        local added = false;
        local effects_to_remove = {}
        local added_effects = {};
        for i, v in ipairs(s._effects) do
            added = added or s:add_missing_children(v, added_effects, effects_to_remove);
        end
        for added_effect_nam, added_effect in pairs(added_effects) do
            log:info("Effect " .. added_effect_nam .. " was re-added and should not be removed");
            effects_to_remove[added_effect_nam] = nil;
        end
        for effect_to_remove_nam, effect_to_remove in pairs(effects_to_remove) do
            log:info("Effect " .. effect_to_remove_nam .. " should be removed");
            s:hide(effect_to_remove);
        end
        return added;
    end;

    add_missing_children = function(s, v, added_effects, effects_to_remove)
        local added = false;
        local g = s:gobf(v);
        local objects = objs(g);
        if objects then
            if not added_effects then
                added_effects = {};
            end
            if not effects_to_remove then
                effects_to_remove = {};
            end
            local children = {};
            for ii, vv in ipairs(v.children) do
                children[vv] = s:childf(vv);
            end
            local yarmc = 0;
            for i, gch in ipairs(objects) do
                local info = s:get_base_info(gch);
                children[info.name] = nil;
                if gch.iarm then
                    gch.arm = gch.iarm;
                else
                    gch.arm = { [0] = { 0, yarmc } };
                end
                local ch = s:glookup_full(stead.deref(gch));
                if not ch then
                    added = true;
                    ch = s:add_child(v, gch);
                    if not ch then
                        return false;
                    end
                    added_effects[ch.nam] = ch;
                end
                if not gch.iarm then
                    local xarm, yarm = s:real_size(ch, 0);
                    local px, py = s:abs_arm(ch);
                    yarmc = yarmc + yarm - py;
                end
            end
            for child_nam, child in pairs(children) do
                effects_to_remove[child_nam] = child;
            end
        end
        return added;
    end;

    add_child = function(s, parent, gob)
        local info = s:get_base_info(gob);
        local ch = s:lookup_full(info.name);
        if ch then
            log:warn("Prevented repeated addition of child " .. ch.nam .. " to parent " .. parent.nam .. ", please fix your code!");
            return false;
        end
        gob.startFrame = s:get_start(parent);
        gob.curStep = s:get_step(parent);
        gob.maxStep = s:get_max_step(parent);
        gob.framesFromStop = s:get_from_stop(parent);
        gob.hot_step = s:get_hotstep(parent);
        gob.acceleration = s:get_accel(parent);
        gob.is_paused = s:gobf(parent).is_paused;
        local child = s:effect_int(parent, gob);
        log:info("Added child = " .. child.nam .. " to " .. parent.nam);
        child.init_step = s:get_init_step(parent);
        child.eff = s:get_eff(parent);
        child.from = s:get_from(parent);
        child.pos = s:get_pos(parent);
        stead.table.insert(parent.children, child.nam);
        return child;
    end;

    remove_child = function(s, parent, child)
        for i, v in ipairs(parent.children) do
            if v == child.nam then
                stead.table.remove(parent.children, i);
                return;
            end
        end
    end;

    vpause = function(s, v, is_paused)
        local gob = stead.ref(v.gob);
        gob.is_paused = is_paused;
        for i, cnam in ipairs(v.children) do
            local vv = s:lookup(cnam);
            s:vpause(vv, is_paused);
        end
    end;

    pause = function(s, frames, callback)
        local existing_callback = s.pause_callback;
        local new_callback = callback;
        if type(callback) == "table" then
            new_callback = function()
                local i, v = next(callback, nil) -- i is an index of t, v = t[i]
                while i do
                    if v then
                        v();
                    end
                    i, v = next(callback, i) -- get next index
                end
            end
        end
        if existing_callback and new_callback then
            s.pause_callback = function()
                existing_callback();
                s.pause_callback = new_callback;
                s.pause_frames = frames;
            end;
        elseif new_callback then
            s.pause_callback = new_callback;
            s.pause_frames = frames;
        elseif existing_callback then
            s.pause_frames = s.pause_frames + frames;
        end
    end;

    postoxy = function(s, v, idx)
        if s:parentf(v) then
            local x, y = s:postoxy(s:parentf(v), idx);
            local xarm, yarm = s:rel_arm(v, idx);
            return x + xarm, y + yarm;
        else
            if not idx then
                idx = 0;
            end
            local vw, vh = s:size(v, idx)
            local xarm, yarm = s:abs_arm(v, idx)
            local x, y = xarm, yarm
            if s:get_pos(v):find 'left' then
                x = xarm
            elseif s:get_pos(v):find 'right' then
                x = s.scr_w - vw
            else
                x = math.floor((s.scr_w - vw) / 2);
            end
            if s:get_pos(v):find 'top' then
                y = yarm
            elseif s:get_pos(v):find 'bottom' then
                y = s.scr_h - vh
            elseif s:get_pos(v):find 'middle' then
                y = math.floor((s.scr_h - vh) / 2)
            else
                y = s.scr_h - vh
            end
            if s:get_pos(v):find('@[ \t]*[0-9+%-]+[ \t]*,[ \t]*[0-9+%-]+') then
                local dx, dy
                local p = s:get_pos(v):gsub("^[^@]*@", "")
                dx = p:gsub("^([0-9+%-]+)[ \t]*,[ \t]*([0-9+%-]+)", "%1")
                dy = p:gsub("^([0-9+%-]+)[ \t]*,[ \t]*([0-9+%-]+)", "%2")
                x = x + dx
                y = y + dy
            end
            return x, y
        end
    end;

    set_bg = function(s, picture)
        if not picture then
            s.bg_spr = sprite.box(s.scr_w, s.scr_h, theme.get 'scr.col.bg');
            s._bg = false;
            return
        end
        if s.bg_spr then
            local bg_spr = sprite.load(picture);
            sprite.copy(bg_spr, s.bg_spr)
        else
            s.bg_spr = sprite.load(picture)
        end
        if not s.bg_spr then
            error("Can not load bg sprite:" .. tostring(picture))
        end
        s._bg = picture;
    end;

    frame = function(s, v, idx, target, x, y, only_compute, free_immediately)
        if not v.spr or not v.spr[idx] then
            log:warn("nonexistent sprite when trying to get frame " .. tostring(idx) .. " of " .. v.nam);
            return empty_frame;
        end
        local sp = v.spr[idx];
        local ospr = sp:val();
        if not ospr then -- Strange error when using resources in idf...
            log:err("filesystem access problem when trying to get frame " .. tostring(idx) .. " of " .. v.nam);
            return empty_frame;
        end
        if not x then
            x = 0;
        end
        if not y then
            y = 0;
        end
        if ospr.loaded then
            local res = nil;
            if not target then
                res = sprite.blank(ospr.w, ospr.h);
                target = res;
            end
            if not only_compute then
                sprite.draw(ospr.loaded, ospr.x, ospr.y, ospr.w, ospr.h, target, x, y);
            end
            if free_immediately and res then
                sprite.free(res);
                res = nil;
            end
            return {["spr"] = res, ["wc"] = ospr.w, ["hc"] = ospr.h, ["w"] = ospr.w, ["h"] = ospr.h, ["tmp"] = (res ~= nil), ["preloaded_effect"] = sp.preloaded_effect, ["alpha"] = sp.alpha, ["scale"] = sp.scale};
        else
            local w, h = sprite.size(sp:get_origin());
            local wc, hc = sprite.size(ospr);
            if not only_compute and target then
                sprite.draw(ospr, target, x, y);
            end
            return {["spr"] = ospr, ["wc"] = wc, ["hc"] = hc, ["w"] = w, ["h"] = h, ["tmp"] = false, ["preloaded_effect"] = sp.preloaded_effect, ["alpha"] = sp.alpha, ["scale"] = sp.scale};
        end
    end;

    fade = function(s, v, only_compute)        
        local x, y, idx, sp, alpha;
        local fadein = (s:get_eff(v) == 'fadein');
        if fadein then
            idx = s:get_step(v);
        else
            idx = s:get_max_step(v) - s:get_step(v);
        end
        x, y = s:postoxy(v, idx);

        if v.spr[idx].preloaded_effect then
            sp = s:frame(v, s:get_step(v), s:screen(), x, y, only_compute, true);
            alpha = sp.alpha;
            log:dbg("Using preloaded effect in fade(), alpha = " .. alpha);
        else
            sp = s:frame(v, idx);
            local mxs, zstep = s:steps(v);        
            if fadein then
                alpha = math.floor(255 * zstep / mxs);
            else
                alpha = math.floor(255 * (1 - zstep / mxs));
            end
            log:dbg("Calculating effect on the fly in fade(), alpha = " .. alpha);
            local spr = sprite.alpha(sp.spr, alpha);
            if sp.tmp then
                sprite.free(sp.spr);
            end
            if not only_compute then
                sprite.draw(spr, s:screen(), x, y);
            end
            sprite.free(spr);
        end
        return idx, x, y, sp.w, sp.h, alpha;
    end;
    overlap = function(s, v, only_compute)
        return s:none(v, only_compute);
    end;
    none = function(s, v, only_compute)
        local x, y
        x, y = s:postoxy(v, s:get_step(v))
        local sp = s:frame(v, s:get_step(v), s:screen(), x, y, only_compute, true);
        return s:get_step(v), x, y, sp.w, sp.h;
    end;
    reverse = function(s, v, only_compute)
        local x, y
        local idx = s:get_max_step(v) - s:get_step(v);
        x, y = s:postoxy(v, idx)
        local sp = s:frame(v, idx, s:screen(), x, y, only_compute, true);
        return idx, x, y, sp.w, sp.h;
    end;
    clear = function(s, x, y, w, h, target)
        local pad = s.spritepad;
        if not target then
            target = s:screen();
        end
        sprite.copy(s.bg_spr, x - pad, y - pad, w + 2 * pad, h + 2 * pad, target, x - pad, y - pad);
    end;
    moveout = function(s, v, only_compute)
        local mxs, zstep = s:steps(v);
        local x_start, x_end
        local y_start, y_end
        local x, y
        local idx = s:get_max_step(v) - s:get_step(v);
        local vw, vh = s:size(v, idx)
        local xarm, yarm = s:arm(v, idx)
        local ws, hs = vw - xarm, vh - yarm
        x_start, y = s:postoxy(v, idx)
        if s:get_from(v) == 'left' or s:get_from(v) == 'right' then
            x_start, y = s:postoxy(v, idx)
        elseif s:get_from(v) == 'top' or s:get_from(v) == 'bottom' then
            x, y_start = s:postoxy(v, idx)
        end
        if s:get_from(v) == 'left' then
            x_end = -ws
            x = math.floor(x_start - zstep * (x_start - x_end) / mxs)
        elseif s:get_from(v) == 'right' then
            x_end = s.scr_w;
            local xarmr, yarmr = s:total_rel_arm(v, idx);
            if s:parentf(v) then
                x_end = x_end + xarmr;
            end
            x = math.floor(x_start + zstep * (x_end - x_start) / mxs)
        elseif s:get_from(v) == 'top' then
            y_end = -hs
            y = math.floor(y_start - zstep * (y_start - y_end) / mxs)
        elseif s:get_from(v) == 'bottom' then
            y_end = s.scr_h;
            local xarmr, yarmr = s:total_rel_arm(v, idx);
            if s:parentf(v) then
                y_end = y_end + yarmr;
            end
            y = math.floor(y_start + zstep * (y_end - y_start) / mxs)
        end
        local sp = s:frame(v, idx, s:screen(), x, y, only_compute, true);
        return idx, x, y, sp.w, sp.h;
    end;

    zoom = function(s, v, only_compute)
        local spr, sprpos;
        if s:get_eff(v) == 'zoomin' then
            sprpos = s:get_step(v);
        else
            sprpos = s:get_max_step(v) - s:get_step(v);
        end

        local vw, vh = s:size(v, sprpos)
        local xarm, yarm = s:arm(v, sprpos)
        local ws, hs = vw - xarm, vh - yarm        

        local sp = s:frame(v, sprpos);
        local scale = sp.scale;
        if scale == 0.0 then
            return s:get_start(v), 0, 0, 0, 0, 0.0;
        end        
        local w, h = sp.w, sp.h;
        if scale ~= 1.0 then
            if sp.preloaded_effect then
                spr = sp.spr;
            else
                spr = sprite.scale(sp.spr, scale, scale, false);
            end
            w, h = sprite.size(spr);
        else
            spr = sp.spr;
        end

        local x, y = s:postoxy(v, sprpos)

        local xdiff, xextent, ydiff, yextent, xarmr, yarmr;
        xdiff = ws * (1 - scale);
        ydiff = hs * (1 - scale);
        xarmr, yarmr = s:total_rel_arm(v, sprpos);
        xextent = xarmr * scale;
        yextent = yarmr * scale;
        if s:get_pos(v):find 'left' then
            x = x - xarmr + math.floor(xextent);
        elseif s:get_pos(v):find 'right' then
            x = x + math.floor(xdiff);
        else
            x = x + math.floor((xdiff - xarmr + xextent) / 2);
        end
        if s:get_pos(v):find 'top' then
            y = y - yarmr + math.floor(yextent);
        elseif s:get_pos(v):find 'bottom' then
            y = y + math.floor(ydiff);
        else
            y = y + math.floor((ydiff - yarmr + yextent) / 2);
        end

        if not only_compute then
            sprite.draw(spr, s:screen(), x, y)
        end
        if not sp.preloaded_effect and sp.spr ~= spr then
            sprite.free(spr)
        end
        if sp.tmp then
            sprite.free(sp.spr);
        end
        return sprpos, x, y, w, h, scale;
    end;

    movein = function(s, v, only_compute)
        local mxs, zstep = s:steps(v);
        local x_start, y_start
        local x_end, y_end
        local x, y
        local idx = s:get_step(v);
        if s:get_from(v) == 'left' or s:get_from(v) == 'right' then
            x_end, y = s:postoxy(v, idx)
        elseif s:get_from(v) == 'top' or s:get_from(v) == 'bottom' then
            x, y_end = s:postoxy(v, idx)
        end
        local vw, vh = s:size(v, idx)
        local xarm, yarm = s:arm(v, idx)
        local ws, hs = vw - xarm, vh - yarm
        if s:get_from(v) == 'left' then
            x_start = -ws
            x = math.floor(x_start + zstep * (xarm - x_start) / mxs)
        elseif s:get_from(v) == 'right' then
            x_start = s.scr_w
            local xarmr, yarmr = s:total_rel_arm(v, idx);
            if s:parentf(v) then
                x_start = x_start + xarmr;
            end
            x = math.floor(x_start - zstep * (x_start - x_end) / mxs)
        elseif s:get_from(v) == 'top' then
            y_start = -hs
            y = math.floor(y_start + zstep * (yarm - y_start) / mxs)
        elseif s:get_from(v) == 'bottom' then
            y_start = s.scr_h
            local xarmr, yarmr = s:total_rel_arm(v, idx);
            if s:parentf(v) then
                y_start = y_start + yarmr;
            end
            y = math.floor(y_start - zstep * (y_start - y_end) / mxs)
        end
        local sp = s:frame(v, idx, s:screen(), x, y, only_compute, true);
        return idx, x, y, sp.w, sp.h;
    end;
    do_effect = function(s, v, only_compute, clear, hide)
        local idx, x, y, w, h;
        local scale = 1.0;
        local alpha = 255;
        if s:get_eff(v) == 'movein' then
            idx, x, y, w, h = s:movein(v, only_compute);
        elseif s:get_eff(v) == 'moveout' then
            idx, x, y, w, h = s:moveout(v, only_compute)
        elseif s:get_eff(v) == 'fadein' or s:get_eff(v) == 'fadeout' then
            idx, x, y, w, h, alpha = s:fade(v, only_compute)
        elseif s:get_eff(v) == 'zoomin' or s:get_eff(v) == 'zoomout' then
            idx, x, y, w, h, scale = s:zoom(v, only_compute)
        elseif s:get_eff(v) == 'reverse' then
            idx, x, y, w, h = s:reverse(v, only_compute)
        elseif s:get_eff(v) == 'overlap' then
            idx, x, y, w, h = s:overlap(v, only_compute)
        else
            idx, x, y, w, h = s:none(v, only_compute)
        end
        if clear then
            s:clear(x, y, w, h);
        end
        v.last_rct = {["v"] = v, ["idx"] = idx, ["x"] = x, ["y"] = y, ["w"] = w, ["h"] = h, ["scale"] = scale, ["alpha"] = alpha};
        if hide or v.dirty_draw or v.newborn then
            log:trace(v.nam .. " is dirty");
            s._dirty_rects[v.nam] = v.last_rct;
        end
        if not only_compute and not clear then
            v.newborn = false;
        end
        return v.last_rct;
    end;
    startcb = function(s, callback, effect)
        s.callback = callback;
        s:start(effect);
    end;
    enable_pending_effects = function(s)
        for i, v in ipairs(s._pending_effects) do
            stead.table.insert(s._effects, v);
        end
        s._pending_effects = {};
    end;
    start = function(s, effect)
        -- do first step
        if not s.bg_spr then
            error "No scene background specified!"
        end
        s:enable_pending_effects();
        if effect then
            s._scene_effect = effect
        end
        win_get();
        -- s.scr_w + s.textpad + s._wf, s.scr_h + s.textpad -- because otherwise screen is corrupted for some reason
        theme.win.geom(s.scr_w + s.textpad + s._wf, s.scr_h + s.textpad, 0, 0)
        theme.set("win.up.x", -s.scr_w);
        theme.set("win.down.x", -s.scr_w);
        if s.skip_mode then
            s._scene_effect = false
        end
        s.stopped = false;
        s:process(true) -- draw frame to offscreen
        if s._scene_effect == 'fading' or s._scene_effect == 'fade' then
            theme.gfx.bg(s.blackscreen)
            vn.bg_changing = 1
        elseif s._scene_effect == 'dissolve' then
            vn.bg_changing = 2
            theme.gfx.bg(s.offscreen)
        else
            vn.bg_changing = false
            win_reset()
            if not s.direct_lock then
                s:textbg(s.offscreen)
                theme.gfx.bg(s.offscreen)
            end
            return
        end        
        --- s:tmr_rst();
        return
        -- just transpose
    end;
    tmr_rst = function(s)
        timer:stop();
        timer:set(s.tmr);
    end;
    -- effect is effect name, like 'dissolve'
    -- wf is the fancy border width, in pixels
    -- fln and frn are paths to the borders' images
    geom = function(s, x, y, w, h, effect, wf, fln, frn, callback)
        -- wf can be zero, this means do not use borders
        if wf then
            s._wf = wf;
        else
            s._wf = 0;
        end
        s._fln = fln;
        s._frn = frn;
        s.win_x, s.win_y, s.win_w, s.win_h = x + s._wf, y, w - 2 * s._wf, h;
        theme.win.geom(s.win_x, s.win_y, s.win_w, s.win_h);
        s:request_full_clear();
        if callback then
            s:startcb(callback, effect);
        else
            s:start(effect);
        end;
        s:commit();
    end;
    scene = function(s, bg, eff, preserve_cache)
        local i, v
        local preserved_effects = {};
        local preserved_pending_effects = {};
        for i, v in ipairs(s._effects) do
            if v.preserved then
                stead.table.insert(preserved_effects, v);
            else
                s:free_effect(v)
            end
        end
        for i, v in ipairs(s._pending_effects) do
            if v.preserved then
                stead.table.insert(preserved_pending_effects, v);
            else
                s:free_effect(v)
            end
        end
        s._effects = preserved_effects;
        s._pending_effects = preserved_pending_effects;
        s._dirty_rects = {};
        if not preserve_cache then
            s:clear_cache();
        end
        s._scene_effect = eff
        -- if bg is nil, simple box sprite will be set
        s:set_bg(bg)
        s:clear_bg();
    end;
    in_vnr = function(s)
        return here()._is_vnr;
    end;
    force_textbg = function(s)
        return here().textbg;
    end;
    textpad = 8;
    spritepad = 8;
    textbg = function(s, to)
        if (s.direct_lock or not s:in_vnr()) and not s:force_textbg() then
            return;
        end
        local pad = vn.textpad;
        local wf = vn._wf;
        local fln = vn._fln;
        local frn = vn._frn;
        local w, h = theme.get 'win.w', theme.get 'win.h'
        local x, y = theme.get 'win.x', theme.get 'win.y'
        local invw, invh = theme.get 'inv.w', theme.get 'inv.h'
        local invx, invy = theme.get 'inv.x', theme.get 'inv.y'
        local sb = sprite.box(w + pad * 2, h + pad * 2, 'black', s.bgalpha)
        local si = sprite.box(invw, invh, 'black')
        sprite.copy(s.bg_spr, x - pad - wf, y - pad, w + pad * 2 + wf * 2, h + pad * 2, to, x - pad - wf, y - pad);
        sprite.copy(s.bg_spr, invx, invy, invw, invh, to, invx, invy);
        if (wf > 0) then
            local fl;
            if fln then
                fl = sprite.load(fln);
            else
                fl = sprite.box(wf, h + pad * 2, 'black', s.bgalpha);
            end
            local fr;
            if frn then
                fr = sprite.load(frn);
            else
                fr = sprite.box(wf, h + pad * 2, 'black', s.bgalpha);
            end
            sprite.draw(fl, to, x - pad - wf, y - pad);
            sprite.draw(fr, to, x + w + pad, y - pad);
            sprite.free(fl)
            sprite.free(fr)
        end
        sprite.draw(sb, to, x - pad, y - pad)
        sprite.draw(si, to, invx, invy)
        sprite.free(sb)
        sprite.free(si)
    end;
    commit = function(s, from)
        if not from then
            from = s:screen();
        end
        if s.direct_lock then
            return;
        end
        sprite.copy(from, s.offscreen);
        s:textbg(s.offscreen);
        theme.gfx.bg(s.offscreen);
    end;
    finish = function(s)
        local k, v
        local r
        for k, v in ipairs(s._effects) do
            if s:get_step(v) < s:get_max_step(v) - s:get_from_stop(v) and s:get_forward(v) then
                r = true
                s:enter_direct();
                s:clear_bg_under_sprite(v);
                v.step = s:get_max_step(v) - s:get_from_stop(v)
            elseif s:get_step(v) > s:get_init_step(v) and not s:get_forward(v) then
                r = true
                s:enter_direct();
                s:clear_bg_under_sprite(v);
                v.step = s:get_init_step(v)
            end
        end
        return r
    end;
    stop = function(s)
        --- timer:stop()
        local e2 = {}
        local i, v

        for i, v in ipairs(s._effects) do
            if not s:get_eff(v):find("out") then
                stead.table.insert(e2, v)
            else
                s:free_effect(v)
            end
        end
        s._effects = e2

        s:commit(s:screen())
        s:leave_direct();

        s.stopped = true;
        --- RAW_TEXT = true
        --- return game._lastdisp or "";
    end;
    lock_direct = function(s)
        s.direct_lock = true;
        theme.set('scr.gfx.mode', 'direct');
    end;
    enter_direct = function(s)
        if not s.direct_lock then
            theme.set('scr.gfx.mode', 'direct');
        end
    end;
    unlock_direct = function(s)
        s.direct_lock = false;
        theme.reset('scr.gfx.mode');
    end;
    leave_direct = function(s)
        if not s.direct_lock then
            theme.reset('scr.gfx.mode');
        end
    end;
    set_step = function(s, v, from_step, forward)
        s:enter_direct();
        s:clear_bg_under_sprite(v);
        if from_step then
            v.step = from_step;
        end
        if forward ~= nil then
            v.forward = forward;
        end
        local r = s:draw_step(v);
        local data = r.data;
        if data then
            s:draw_hud(data, false);
        end
        for k, vv in ipairs(v.children) do
            log:trace("nam = " .. v.nam .. "; child = " .. vv);
            s:set_step(s:childf(vv), from_step, forward);
        end
    end;
    -- we use v.step instead of s:get_step(v) in this method, because we need to know when exactly child sprite will reach the end of its animation
    do_step = function(s, v)
        local hotstep = s:get_hotstep(v);
        local accel = s:get_accel(v);
        if hotstep and s.slowcpu then
            hotstep = math.floor(hotstep / 3);
            if hotstep == 0 then
                hotstep = 1;
            end
        end
        if accel and s.slowcpu then
            accel = accel * 4;
        end
        if v.newborn then
            return true;
        elseif not s:gobf(v).is_paused then
            if s:get_forward(v) then
                if (v.step < s:get_max_step(v) - s:get_from_stop(v)) then
                    v.step = v.step + s.stp;
                    if hotstep and v.step % hotstep == 0 then
                        v.step = v.step + accel * s.stp;
                    end
                    if v.step > s:get_max_step(v) - s:get_from_stop(v) then
                        v.step = s:get_max_step(v) - s:get_from_stop(v);
                    end
                    return true;
                end
            else
                if (v.step > s:get_init_step(v)) then
                    v.step = v.step - s.stp;
                    if hotstep and v.step % hotstep == 0 then
                        v.step = v.step - accel * s.stp;
                    end
                    if v.step < s:get_init_step(v) then
                        v.step = s:get_init_step(v);
                    end
                    return true;
                end
            end
        end
        return false;
    end;
    draw_step = function(s, v, clear)
        local hasmore = true;
        if not clear then
            -- If we should clear under the sprite, then don't do next step
            -- So, then we'll clear the background under the previous position of the sprite
            hasmore = s:do_step(v);
        end
        local hadmore = v.hasmore;
        v.hasmore = hasmore;
        local result = {["data"] = nil, ["hasmore"] = hasmore};
        local e = true;
        if s:gobf(v).enablefn then
            e = s:gobf(v):enablefn();
        end
        if e then
            if hadmore or s:check_dirty(v) then
                result.data = s:do_effect(v, false, clear);
            end
        else
            if s:gobf(v).onhide then
                s:gobf(v):onhide();
            end
        end
        return result;
    end;
    do_effects = function(s, clear)
        local result = {["datas"] = {}, ["hasmore"] = false};
        for i, v in ipairs(s._effects) do
            if s:enabled(v) then
                local r = s:draw_step(v, clear);
                if r.data then
                    stead.table.insert(result.datas, r.data);
                    result.hasmore = (result.hasmore or r.hasmore);
                end
            end
        end
        return result;
    end;
    clear_bg = function(s, partial_clear)
        if partial_clear then
            for i, v in ipairs(s._effects) do
                if s:need_to_clear(v) then
                    s:clear_bg_under_sprite(v);
                end
            end
        else
            sprite.copy(s.bg_spr, s:screen());
            s:invalidate_all();
        end
    end;
    check_dirty = function(s, v)
        for k, r in pairs(s._dirty_rects) do
            if s:collision(v, r) or s:collisionrt(v, r) then
                return true;
            end
        end
        return false;
    end;
    check_dirty_and_force_redraw = function(s, v)
        if v.hasmore then
            -- redraw already forced
            return true;
        end
        if s:check_dirty(v) then
            s:set_hasmore_all(v, true);
            return true;
        end
        return false;
    end;
    need_to_clear = function(s, v)
        return s:enabled(v) and (s:get_eff(v) ~= 'overlap' and (v.hasmore or s:check_dirty_and_force_redraw(v)));
    end;
    clear_bg_under_sprite = function(s, v, hide)
        local data = s:do_effect(v, true, true, hide);
        s:draw_hud(data, false, nil, true, hide);
        --s:update_tooltip(v, true, hide); -- done in onout
    end;
    has_any_animation_in_progress = function(s)
        for i, v in ipairs(s._effects) do
            if s:has_animation_in_progress(v) then
                return true;
            end
        end
        return false;
    end;
    is_inactive_due_to_anim_state = function(s, v)
        local init_frame = s:get_init_step(v);
        if not init_frame then
            return false;
        end
        local last_frame = s:get_max_step(v) - s:get_from_stop(v);
        if s:get_eff(v) == 'none' or (init_frame > 0 and init_frame < last_frame) then
            return false;
        end
        local fadein = s:get_eff(v) == 'fadein';
        local fadeout = s:get_eff(v) == 'fadeout';
        local last = (s:get_step(v) == last_frame) and s:get_forward(v);
        local first = (s:get_step(v) == init_frame) and not s:get_forward(v);
        return (last and fadeout) or (first and fadein);
    end;
    has_animation_in_progress = function(s, v)
        if s:gobf(v).is_paused then
            return false;
        end
        local f = (s:get_step(v) < s:get_max_step(v) - s:get_from_stop(v)) and s:get_forward(v);
        local b = (s:get_step(v) > s:get_init_step(v)) and not s:get_forward(v);
        return f or b;
    end;
    process = function(s, initpass)
        local i, v
        local first
        local cbresult = false;
        timer:stop();
        if s:has_any_animation_in_progress() then
            s:enter_direct();
        end
        s:clear_bg(s.partial_clear);
        s.partial_clear = true;
        local res = s:do_effects();
        local n = res.hasmore;
        local x, y = stead.mouse_pos();
        s:draw_huds(res.datas);
        s:tooltips(x, y);
        if not n then
            s:stop();
            s._dirty_rects = {};
            if (vn.callback) then
                local callback = vn.callback;
                vn.callback = false;
                cbresult = callback();
            end
            if cbresult then
                if type(cbresult) == 'function' then
                    s:tmr_rst();
                    return cbresult();
                else
                    s:start();
                    s:tmr_rst();
                    return true;
                end
            else
                s:tmr_rst();
                return false;
            end
        elseif initpass then
            s:commit(s:screen())
            s:leave_direct();
        end
        s:tmr_rst();
        return n
    end;
    draw_huds = function(s, datas, clear, hide)
        for k, vv in ipairs(datas) do
            s:draw_hud(vv, false, nil, clear, hide)
        end
    end;
    draw_hud = function(s, data, only_compute, target, clear, hide)
        local v, idx, x, y, scale, alpha = data.v, data.idx, data.x, data.y, data.scale, data.alpha;
        if scale == 0 or alpha == 0 then
            return false;
        end
        if not idx then
            idx = 0;
        end
        if not target then
            target = s:screen();
        end
        if (s:gobf(v) and s:gobf(v).txtfn) then
            local texts = s:gobf(v):txtfn();
            local xpos, ypos = nil, nil;
            if not x or not y then
                xpos, ypos = s:postoxy(v, idx);
            end
            if x then
                xpos = x;
            end
            if y then
                ypos = y;
            end
            xpos = xpos + data.w * scale;
            ypos = ypos + data.h * scale / 2.0;
            xpos = xpos + s.default_label_extent + s.default_tooltip_offset;
            local sprites = {};
            local htotal = 0;
            local wmax = 0;
            for k, vv in pairs(texts) do
                if vv.text then
                    local color = vv.color;
                    if not color then
                        color = s.hud_color;
                    end
                    local textSpriteInit = sprite.text(hudFont, vv.text, color);
                    local textSpriteScaled;
                    if scale ~= 1.0 then
                        textSpriteScaled = sprite.scale(textSpriteInit, scale, scale, false);
                        sprite.free(textSpriteInit);
                    else
                        textSpriteScaled = textSpriteInit;
                    end
                    local textSprite;
                    if alpha ~= 255 then
                        textSprite = sprite.alpha(textSpriteScaled, alpha);
                        sprite.free(textSpriteScaled);
                    else
                        textSprite = textSpriteScaled;
                    end
                    local w, h = sprite.size(textSprite);
                    if clear or only_compute then
                        sprite.free(textSprite);
                    end
                    w = w + s.extent;
                    if w > wmax then
                        wmax = w;
                    end
                    htotal = htotal + h;
                    stead.table.insert(sprites, {["spr"] = textSprite, ["xpos"] = xpos, ["w"] = w, ["h"] = h});
                end
            end
            local ycur = ypos - htotal / 2.0;
            local rct = {["v"] = v, ["x"] = xpos, ["y"] = ycur, ["w"] = wmax, ["h"] = htotal};
            if only_compute then
                return rct;
            end
            if hide or v.dirty_draw then
                log:trace(v.nam .. " hud is dirty");
                s._dirty_rects[v.nam .. '_hud'] = rct;
            end
            if clear then
                s:clear(xpos, ycur, wmax, htotal, target);
                return rct;
            end
            for i, ss in ipairs(sprites) do
                local hudSprite = sprite.blank(ss.w, ss.h);
                sprite.draw(target, ss.xpos, ycur, ss.w, ss.h, hudSprite, 0, 0);
                sprite.draw(ss.spr, hudSprite, 0, 0);
                sprite.draw(hudSprite, target, ss.xpos, ycur);
                ycur = ycur + ss.h;
                sprite.free(hudSprite);
                sprite.free(ss.spr);
            end
            return rct;
        end
    end;
    tooltips = function(s, x, y)
        if not s.on then
            return;
        end
        for i, v in ipairs(s._effects) do
            if s:enabled(v) and s:gobf(v).tooltipfn and s:inside_spr(v, x, y) then
                s:update_tooltip(v);
            end
        end
    end;
    should_ignore = function(s, v)
        return here().ignore_preserved_gobjs and v.preserved;
    end;
    enabled = function(s, v)
        local gob = s:gobf(v);
        local result = not disabled(gob) and (not gob.enablefn or gob:enablefn()) and not s:should_ignore(v);
        if result then
            v.was_disabled = false;
        elseif not v.was_disabled then
            -- disabled => clear this sprite
            s:clear_bg_under_sprite(v, true);
            -- simulate onout, if it is defined
            if gob.onout then
                s:outf(v);
            end
            -- to redraw it when it will become enabled again
            v.hasmore = true;
            v.was_disabled = true;
        end
        return result;
    end;
    update_tooltip = function(s, v, erase, hide)
        local xx, yy = s:postoxy(v);
        local sp = s:frame(v, 0);
        local text, pos, clear_under_tooltip = s:gobf(v):tooltipfn();
        if text then
            s:tooltip(v, text, pos, xx, yy, sp.w, sp.h, clear_under_tooltip, erase, hide);
        end
    end;
    tooltip = function(s, v, text, pos, x, y, vw, vh, clear_under_tooltip, erase, hide)
        local target = s:screen();
        local label, w, h = s:label(text);
        local xmax = theme.get("scr.w");
        local xt, yt;
        if pos == "n" then
            local yy = y - h - s.default_tooltip_offset;
            local txt_offset = (vw - w) / 2;
            xt = x + txt_offset;
            yt = yy;
        elseif pos == "s" then
            local yy = y + vh + s.default_tooltip_offset;
            local txt_offset = (vw - w) / 2;
            xt = x + txt_offset;
            yt = yy;
        else
            local yy = y + vh / 2 - h / 2;
            local xx = x + vw + s.default_tooltip_offset;
            if ((xmax - xx > w) or (pos == "e")) and not (pos == "w") then
                xt = xx;
                yt = yy;
            else
                xt = x - w - s.default_tooltip_offset;
                yt = yy;
            end
        end
        if clear_under_tooltip or erase then
            sprite.copy(s.bg_spr, xt, yt, w, h, target, xt, yt);
        end
        if hide or v.dirty_draw then
            log:trace(v.nam .. " tooltip is dirty");
            s._dirty_rects[v.nam .. '_tooltip'] = {["v"] = v, ["x"] = xt, ["y"] = yt, ["w"] = w, ["h"] = h};
        end
        if not erase then
            sprite.draw(label, target, xt, yt);
        end
        sprite.free(label);
    end;
    -- Sprite with label text. You should call sprite.free(), when you no longer need this.
    label = function(s, text, extent, color, bgcolor, bgalpha, font)
        if not color then
            color = '#000000';
        end
        if not bgcolor then
            bgcolor = 'white';
        end
        if not bgalpha then
            bgalpha = 127;
        end
        if not extent then
            extent = s.default_label_extent;
        end
        if not font then
            font = hudFont;
        end
        local texts = {};
        local src = {};
        local w, h = 0, 0;
        if type(text) == 'table' then
            src = text;
        else
            stead.table.insert(src, text);
        end
        for i, t in ipairs(src) do
            if t and t ~= "" then
                local spr = sprite.text(font, t, color);
                local ww, hh = sprite.size(spr);
                if ww > w then
                    w = ww;
                end
                stead.table.insert(texts, {["spr"] = spr, ["x"] = extent, ["y"] = h + extent});
                h = h + extent + hh;
            end
        end
        w = w + 2 * extent;
        h = h + extent;
        local label = sprite.box(w, h, bgcolor, bgalpha);
        for ii, tt in ipairs(texts) do
            sprite.draw(tt.spr, label, tt.x, tt.y);
            sprite.free(tt.spr);
        end
        return label, w, h;
    end;
    txt_line_fn = function(s, text, color)
        local txt = text;
        local clr = color;
        if not clr then
            clr = 'black';
        end
        return function()
            local result = {};
            stead.table.insert(result, { ["text"] = txt, ["color"] = clr });
            return result;
        end
    end;
}

stead.module_init(function()
    vn:init()
    vnticks = stead.ticks();
    vnticks_diff = vn.ticks_threshold;
    hudFont = sprite.font('fonts/Medieval_English.ttf', 30);
    empty_s = sprite.load('gfx/empty.png');
    empty_frame = {["spr"] = empty_s, ["w"] = 0, ["h"] = 0, ["tmp"] = false, ["preloaded_effect"] = false, ["alpha"] = 255, ["scale"] = 1.0};
    if LANG == "ru" then
        busy_spr = vn:label("Загрузка...", 40, "#ffffff", "black");
    else
        busy_spr = vn:label("Loading...", 40, "#ffffff", "black");
    end
end)

function vnr(v)
    if not v.nam then v.nam = 'vn'; v.disp = false; end
    v._is_vnr = true;
    return room(v)
end
