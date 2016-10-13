-- vn module
require 'timer'
require 'theme'
require 'sprites'
require 'modules/gobj'

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
    vnticks = get_ticks();

    -- NB: do not put heavy code in onover/onout
    local x, y = stead.mouse_pos();
    vn:over(x, y);
    vn:out(x, y);

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
        return game._lastdisp;
    end
    if not vn.stopped then
        if not vn:process() then
            RAW_TEXT = true;
            return game._lastdisp;
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
    _bg = false;
    _need_update = false;
    _wf = 0;
    _fln = nil;
    _frn = nil;
    hz = 18;
    ticks_threshold = 36;
    slowcpu = false;
    var {
        on = true,
        stopped = true,
        uiupdate = false,
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
        extent = 100,
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
        if not load or not s.on then
            return
        end
        local i, v, n
        for i, v in ipairs(s._effects) do
            v.step = v.init_step;
            s:load_effect(v)
            if v.step < v.max_step - v.from_stop then
                n = true
            end
        end
        s:set_bg(s._bg)
        vn:add_all_missing_children();
        s:start(nil, true)
    end;
    init = function(s)
        s.scr_w = theme.get 'scr.w'
        s.scr_h = theme.get 'scr.h'
        s.offscreen = sprite.blank(s.scr_w, s.scr_h)
        s.blackscreen = sprite.box(s.scr_w, s.scr_h, 'black')
        s.uiupdate = true;
        timer:set(1); --(s.hz)
    end;
    get_spr_rct = function(s, v)
        local res = s:do_effect(v, true);
        return { x = res.x, y = res.y, w = res.w, h = res.h };
    end;
    inside_spr = function(s, v, x, y)
        -- Click falls inside this picture
        local rct = s:get_spr_rct(v);
        return x >= rct.x and x <= rct.x + rct.w and y >= rct.y and y <= rct.y + rct.h;
    end;
    test_click = function(s, x, y)
        for i, v in ipairs(s._effects) do
            if s:inside_spr(v, x, y) then
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
            if s:gobf(v).onclick and s:inside_spr(v, x, y) then
                s:gobf(v):onclick(s);
            end
        end
    end;
    shapechange = function(s, v, is_over)
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
        if not morph then
            return false;
        end
        s:hide(v);
        s:effect_int(nil, morph, is_over);
        s:start(nil, true);
        return true;
    end;
    over = function(s, x, y, a, b, c, d)
        if not s.on then -- or s.uiupdate then
            return;
        end
        for i, v in ipairs(s._effects) do
            if s:gobf(v).onover and s:enabled(v) and not v.mouse_over and s:inside_spr(v, x, y) then
                s:gobf(v):onover();
                if not s:shapechange(v, true) then
                    v.mouse_over = true;
                    s:update_tooltip(v);
                    if s.stopped then
                        s.uiupdate = true;
                        s.stopped = false;
                    end
                end
            end
        end
    end;
    out = function(s, x, y, a, b, c, d)
        if not s.on then -- or s.uiupdate then
            return;
        end
        for i, v in ipairs(s._effects) do
            if s:gobf(v).onout and s:enabled(v) and v.mouse_over and not s:inside_spr(v, x, y) then
                s:gobf(v):onout();
                if not s:shapechange(v, false) then
                    v.mouse_over = false;
                    if s.stopped then
                        s.uiupdate = true;
                        s.stopped = false;
                    end
                end
            end
        end
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
        local meta, milestones = s:read_meta(prefix);
        for sprStep = v.start, v.max_step do
            v.spr[sprStep] = {
                was_loaded = false,
                cache = nil,
                val = function(s)
                    if not s.cache then
                        s.cache = s:load();
                    end
                    return s.cache;
                end,
                load = function(s)
                    if ss:is_sprite(v) then
                        return v.pic;
                    end
                    if ss:file_exists(v.pic) then
                        if sprStep == v.start then
                            return s:load_file(v.pic);
                        elseif sprStep > v.start then
                            return v.spr[v.start]:val();
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
                        idx = v.start;
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
                        loaded = sprite.load(sprfile);
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
                    if idx > v.start then
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
                    local interval, milestoneIdx = ss:find_interval(milestones, sprStep, v.max_step);
                    local united_sprfile = prefix .. interval .. extension;
                    if ss:file_exists(united_sprfile) then
                        return s:load_file(united_sprfile, prefix, sprStep, true, milestoneIdx);
                    elseif sprStep == start then
                        error("Can not load key sprite (" .. sprfile .. " or " .. united_sprfile .. ")");
                    elseif sprStep > v.start then
                        return v.spr[sprStep - 1]:val();
                    end
                end,
                free = function(s)
                    if s.was_loaded and s.cache then
                        if ss.nocache then
                            -- Currently this code will not be executed, because cache is always used
                            sprite.free(s.cache);
                        end
                        s.cache = nil;
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
        if gob.is_dynamic then
            delete(gob);
        end
        v.spr = nil;
    end;
    preload = function(s)
        for k, w in pairs(s.sprite_cache_data) do
            local v = w[0];
            local lastIdx = w[1];
            -- v.spr can be nil if free_effect() was already called
            if (v.spr and (lastIdx > v.start) and (lastIdx < (v.max_step - v.from_stop))) then
                for i = lastIdx + 1, (v.max_step - v.from_stop) do
                    if v.spr[i] then
                        v.spr[i]:val();
                    else
                        -- This error should actually never appear
                        print("Error preloading sprite " .. v.pic .. "@" .. i);
                    end
                    --print("preloaded " .. v.nam .. "@" .. i);
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
        s:clear_bg_under_sprite(i);
        stead.table.remove(s._effects, k)
        for ii, vv in ipairs(i.children) do
            s:hide(s:childf(vv), eff, ...);
        end
        if s:gobf(i).onhide then
            s:gobf(i):onhide();
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

    steps = function(s, v)
        local mxs = v.max_step - v.from_stop - v.start;
        --print("vstep="..tostring(v.step).."vstart="..tostring(v.start));
        local zstep = v.step - v.start;
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
            return game._lastdisp;
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

    effect = function(s, image, eff, speed, startFrame, curStep, framesFromStop, arm, hot_step, acceleration, is_preserved)
        local maxStep = math.floor((speed or s.speed) / s.hz);
        local gg = init_gobj(image, eff, maxStep, startFrame, curStep, framesFromStop, arm, hot_step, acceleration, is_preserved);
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

    effect_int = function(s, parent_eff, g, is_over)
        local image;
        if type(g.pic) == 'function' then
            image = g:pic();
        else
            image = g.pic;
        end
        local eff = g.eff;
        local maxStep = g.maxStep;
        local t = eff;
        local v;

        if not is_over then
            is_over = false;
        end

        local is_preserved = g.preserved;
        if not is_preserved then
            is_preserved = false;
        end

        if type(image) == 'string' then
            v = { pic = image, nam = image };
            image = v
        end

        local picture = image.pic
        local name = image.nam
        local parent_nam = false;
        if parent_eff then
            parent_nam = parent_eff.nam;
        end
        local v = {
            parent = parent_nam,
            newborn = true,
            pic = picture,
            nam = name,
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
            preserved = is_preserved
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

        if oe then
            if oe.pic ~= v.pic then -- new pic
                s:free_effect(oe);
                oe.pic = v.pic
                s:load_effect(oe)
            end
            old_pos = oe.pos
            v = oe
        else
            v.step = g.curStep;
            v.max_step = maxStep;
            v.from_stop = g.framesFromStop
            s:load_effect(v)
        end
        v.step = g.curStep;
        v.start = g.startFrame;
        v.max_step = maxStep;
        v.from_stop = g.framesFromStop
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
        else
            v.eff = 'none'
        end

        if s.skip_mode then v.eff = 'none' end

        if v.eff == 'none' then
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
        for i, v in ipairs(s._effects) do
            added = added or s:add_missing_children(v);
        end
        return added;
    end;

    add_missing_children = function(s, v)
        local added = false;
        local g = s:gobf(v);
        if objs(g) then
            local yarmc = 0;
            for i, gch in ipairs(objs(g)) do
                if gch.iarm then
                    gch.arm = gch.iarm;
                else
                    gch.arm = { [0] = { 0, yarmc } };
                end
                local ch = s:glookup_full(stead.deref(gch));
                if not ch then
                    added = true;
                    ch = s:add_child(v, gch);
                else
                    s:reconfigure_children(ch);
                end
                if not gch.iarm then
                    local xarm, yarm = s:real_size(ch, 0);
                    local px, py = s:abs_arm(ch);
                    yarmc = yarmc + yarm - py;
                end
            end
        end
        return added;
    end;

    add_child = function(s, parent, gob)
        gob.startFrame = parent.start;
        gob.curStep = parent.step;
        gob.maxStep = parent.max_step;
        gob.framesFromStop = parent.from_stop;
        gob.hot_step = parent.hotstep;
        gob.acceleration = parent.accel;
        gob.is_paused = s:gobf(parent).is_paused;
        local child = s:effect_int(parent, gob);
        --print("Added child = " .. child.nam .. " to " .. parent.nam);
        child.eff = parent.eff;
        child.from = parent.from;
        child.pos = parent.pos;
        stead.table.insert(parent.children, child.nam);
        s:reconfigure_children(parent);
        return child;
    end;

    -- Reconfigures properties of existing children
    reconfigure_children = function(s, parent)
        --print("reconfiguring children of " .. parent.nam);
        for i, cnam in ipairs(parent.children) do
            --print("reconfiguring " .. cnam);
            local vv = s:childf(cnam);
            vv.start = parent.start;
            vv.step = parent.step;
            vv.max_step = parent.max_step;
            vv.from_stop = parent.from_stop;
            vv.hotstep = parent.hotstep;
            vv.accel = parent.accel;
            vv.eff = parent.eff;
            vv.from = parent.from;
            vv.pos = parent.pos;
            for ii, ccnam in ipairs(vv.children) do
                local vvv = s:childf(ccnam);
                s:reconfigure_children(vvv);
            end
        end
    end;

    remove_child = function(s, parent, child)
        stead.table.remove(parent.children, child.nam);
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
            if v.pos:find 'left' then
                x = xarm
            elseif v.pos:find 'right' then
                x = s.scr_w - vw
            else
                x = math.floor((s.scr_w - vw) / 2);
            end
            if v.pos:find 'top' then
                y = yarm
            elseif v.pos:find 'bottom' then
                y = s.scr_h - vh
            elseif v.pos:find 'middle' then
                y = math.floor((s.scr_h - vh) / 2)
            else
                y = s.scr_h - vh
            end
            if v.pos:find('@[ \t]*[0-9+%-]+[ \t]*,[ \t]*[0-9+%-]+') then
                local dx, dy
                local p = v.pos:gsub("^[^@]*@", "")
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
            print("WARN: nonexistent sprite when trying to get frame " .. tostring(idx) .. " of " .. v.nam);
            return empty_frame;
        end
        local ospr = v.spr[idx]:val();
        if not ospr then -- Strange error when using resources in idf...
            print("ERROR: filesystem access problem when trying to get frame " .. tostring(idx) .. " of " .. v.nam);
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
            return {["spr"] = res, ["w"] = ospr.w, ["h"] = ospr.h, ["tmp"] = (res ~= nil)};
        else
            local w, h = sprite.size(ospr);
            if not only_compute and target then
                sprite.draw(ospr, target, x, y);
            end
            return {["spr"] = ospr, ["w"] = w, ["h"] = h, ["tmp"] = false};
        end
    end;

    fade = function(s, v, only_compute)
        local mxs, zstep = s:steps(v);
        local x, y, idx
        local fadein = (v.eff == 'fadein');
        if fadein then
            idx = v.step;
        else
            idx = v.max_step - v.step;
        end
        x, y = s:postoxy(v, idx)

        local alpha;
        if fadein then
            alpha = math.floor(255 * zstep / mxs);
        else
            alpha = math.floor(255 * (1 - zstep / mxs));
        end
        local sp = s:frame(v, idx);
        local spr = sprite.alpha(sp.spr, alpha);
        if sp.tmp then
            sprite.free(sp.spr);
        end
        if not only_compute then
            sprite.draw(spr, s:screen(), x, y);
        end
        sprite.free(spr)
        return idx, x, y, sp.w, sp.h, alpha;
    end;
    none = function(s, v, only_compute)
        local x, y
        x, y = s:postoxy(v, v.step)
        local sp = s:frame(v, v.step, s:screen(), x, y, only_compute, true);
        return v.step, x, y, sp.w, sp.h;
    end;
    reverse = function(s, v, only_compute)
        local x, y
        local idx = v.max_step - v.step;
        x, y = s:postoxy(v, idx)
        local sp = s:frame(v, idx, s:screen(), x, y, only_compute, true);
        return idx, x, y, sp.w, sp.h;
    end;
    clear = function(s, x, y, w, h)
        sprite.copy(s.bg_spr, x, y, w, h, s:screen(), x, y);
    end;
    moveout = function(s, v, only_compute)
        local mxs, zstep = s:steps(v);
        local x_start, x_end
        local y_start, y_end
        local x, y
        local idx = v.max_step - v.step;
        local vw, vh = s:size(v, idx)
        local xarm, yarm = s:arm(v, idx)
        local ws, hs = vw - xarm, vh - yarm
        x_start, y = s:postoxy(v, idx)
        if v.from == 'left' or v.from == 'right' then
            x_start, y = s:postoxy(v, idx)
        elseif v.from == 'top' or v.from == 'bottom' then
            x, y_start = s:postoxy(v, idx)
        end
        if v.from == 'left' then
            x_end = -ws
            x = math.floor(x_start - zstep * (x_start - x_end) / mxs)
        elseif v.from == 'right' then
            x_end = s.scr_w;
            local xarmr, yarmr = s:total_rel_arm(v, idx);
            if s:parentf(v) then
                x_end = x_end + xarmr;
            end
            x = math.floor(x_start + zstep * (x_end - x_start) / mxs)
        elseif v.from == 'top' then
            y_end = -hs
            y = math.floor(y_start - zstep * (y_start - y_end) / mxs)
        elseif v.from == 'bottom' then
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
        local mxs, zstep = s:steps(v);
        local x
        local y
        local spr
        local scale
        local sprpos;
        if v.eff == 'zoomin' then
            scale = zstep / mxs;
            sprpos = v.step;
        else
            scale = 1 - zstep / mxs
            sprpos = v.max_step - v.step;
        end

        local vw, vh = s:size(v, sprpos)
        local xarm, yarm = s:arm(v, sprpos)
        local ws, hs = vw - xarm, vh - yarm
        if scale == 0 then
            return v.start, 0, 0, 0;
        end

        local sp = s:frame(v, sprpos);
        local w, h = sp.w, sp.h;
        if scale ~= 1.0 then
            spr = sprite.scale(sp.spr, scale, scale, false);
            w, h = sprite.size(spr);
        else
            spr = sp.spr;
        end

        x, y = s:postoxy(v, sprpos)

        local xdiff, xextent, ydiff, yextent, xarmr, yarmr;
        xdiff = ws * (1 - scale);
        ydiff = hs * (1 - scale);
        xarmr, yarmr = s:total_rel_arm(v, sprpos);
        xextent = xarmr * scale;
        yextent = yarmr * scale;
        if v.pos:find 'left' then
            x = x - xarmr + math.floor(xextent);
        elseif v.pos:find 'right' then
            x = x + math.floor(xdiff);
        else
            x = x + math.floor((xdiff - xarmr + xextent) / 2);
        end
        if v.pos:find 'top' then
            y = y - yarmr + math.floor(yextent);
        elseif v.pos:find 'bottom' then
            y = y + math.floor(ydiff);
        else
            y = y + math.floor((ydiff - yarmr + yextent) / 2);
        end

        if not only_compute then
            sprite.draw(spr, s:screen(), x, y)
        end
        if sp.spr ~= spr then
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
        local idx = v.step;
        if v.from == 'left' or v.from == 'right' then
            x_end, y = s:postoxy(v, idx)
        elseif v.from == 'top' or v.from == 'bottom' then
            x, y_end = s:postoxy(v, idx)
        end
        local vw, vh = s:size(v, idx)
        local xarm, yarm = s:arm(v, idx)
        local ws, hs = vw - xarm, vh - yarm
        if v.from == 'left' then
            x_start = -ws
            x = math.floor(x_start + zstep * (xarm - x_start) / mxs)
        elseif v.from == 'right' then
            x_start = s.scr_w
            local xarmr, yarmr = s:total_rel_arm(v, idx);
            if s:parentf(v) then
                x_start = x_start + xarmr;
            end
            x = math.floor(x_start - zstep * (x_start - x_end) / mxs)
        elseif v.from == 'top' then
            y_start = -hs
            y = math.floor(y_start + zstep * (yarm - y_start) / mxs)
        elseif v.from == 'bottom' then
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
    do_effect = function(s, v, only_compute, clear)
        local idx, x, y, w, h;
        local scale = 1.0;
        local alpha = 255;
        if v.eff == 'movein' then
            idx, x, y, w, h = s:movein(v, only_compute);
        elseif v.eff == 'moveout' then
            idx, x, y, w, h = s:moveout(v, only_compute)
        elseif v.eff == 'fadein' or v.eff == 'fadeout' then
            idx, x, y, w, h, alpha = s:fade(v, only_compute)
        elseif v.eff == 'zoomin' or v.eff == 'zoomout' then
            idx, x, y, w, h, scale = s:zoom(v, only_compute)
        elseif v.eff == 'reverse' then
            idx, x, y, w, h = s:reverse(v, only_compute)
        else
            idx, x, y, w, h = s:none(v, only_compute)
        end
        if clear then
            s:clear(x, y, w, h);
        end
        if not only_compute and not clear then
            v.newborn = false;
        end
        return {["v"] = v, ["idx"] = idx, ["x"] = x, ["y"] = y, ["w"] = w, ["h"] = h, ["scale"] = scale, ["alpha"] = alpha};
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
    start = function(s, effect, uiupdate)
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
        if uiupdate then
            s.uiupdate = true;
        else
            -- NB: uiupdate can be nil, but here I want it to be true or false, not nil
            s.uiupdate = false;
        end
        s:process() -- draw frame to offscreen
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
            --s.stopped = false;
            return
        end
        --- timer:set(s.hz)
        -- s.stopped = false;
        return
        -- just transpose
    end;
    -- effect is effect name, like 'dissolve'
    -- wf is the fancy border width, in pixels
    -- fln and frn are paths to the borders' images
    geom = function(s, x, y, w, h, effect, wf, fln, frn)
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
        if effect then
            s:start(effect, true);
        else
            s:start(nil, true);
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
            if v.step < v.max_step - v.from_stop and v.forward then
                r = true
                v.step = v.max_step - v.from_stop
            elseif v.step > v.init_step and not v.forward then
                r = true
                v.step = v.init_step
            end
        end
        return r
    end;
    stop = function(s)
        --- timer:stop()
        local e2 = {}
        local i, v

        for i, v in ipairs(s._effects) do
            if not v.eff:find("out") then
                stead.table.insert(e2, v)
            else
                s:free_effect(v)
            end
        end
        s._effects = e2

        s:commit(s:screen())
        s:leave_direct();

        s.stopped = true;
        s.uiupdate = false;
        --- RAW_TEXT = true
        --- return game._lastdisp;
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
            s:draw_hud(data.v, data.idx, data.x, data.y, data.scale, data.alpha);
        end
        for k, vv in ipairs(v.children) do
            --print("nam = " .. v.nam .. "; child = " .. vv);
            s:set_step(s:childf(vv), from_step, forward);
        end
    end;
    do_step = function(s, v)
        local hotstep = v.hotstep;
        local accel = v.accel;
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
            if v.forward then
                if (v.step < v.max_step - v.from_stop) then
                    v.step = v.step + s.stp;
                    if hotstep and v.step % hotstep == 0 then
                        v.step = v.step + accel * s.stp;
                    end
                    if v.step > v.max_step - v.from_stop then
                        v.step = v.max_step - v.from_stop;
                    end
                    return true;
                end
            else
                if (v.step > v.init_step) then
                    v.step = v.step - s.stp;
                    if hotstep and v.step % hotstep == 0 then
                        v.step = v.step - accel * s.stp;
                    end
                    if v.step < v.init_step then
                        v.step = v.init_step;
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
        local result = {["data"] = nil, ["hasmore"] = hasmore};
        local e = true;
        if s:gobf(v).enablefn then
            e = s:gobf(v):enablefn();
        end
        if e then
            result.data = s:do_effect(v, false, clear);
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
            local r = s:draw_step(v, clear);
            if r.data then
                stead.table.insert(result.datas, r.data);
                result.hasmore = (result.hasmore or r.hasmore);
            end
        end
        return result;
    end;
    clear_bg = function(s, partial_clear)
        if partial_clear then
            for i, v in ipairs(s._effects) do
                s:clear_bg_under_sprite(v);
            end
        else
            sprite.copy(s.bg_spr, s:screen());
        end
    end;
    clear_bg_under_sprite = function(s, v)
        local data = s:do_effect(v, true, true);
        s:draw_hud(data.v, data.idx, data.x, data.y, data.scale, data.alpha, nil, true);
    end;
    has_any_animation_in_progress = function(s)
        for i, v in ipairs(s._effects) do
            if s:has_animation_in_progress(v) then
                return true;
            end
        end
        return false;
    end;
    has_animation_in_progress = function(s, v)
        if s:gobf(v).is_paused then
            return false;
        end
        local f = (v.step < v.max_step - v.from_stop) and v.forward;
        local b = (v.step > v.init_step) and not v.forward;
        return f or b;
    end;
    process = function(s)
        local i, v
        local first
        local cbresult = false;
        s:clear_bg(not s.uiupdate);
        s.uiupdate = false;
        if s:has_any_animation_in_progress() then
            s:enter_direct();
        end
        local res = s:do_effects();
        local n = res.hasmore;
        local x, y = stead.mouse_pos();
        if n then
            s:draw_huds(res.datas);
            s:tooltips(x, y);
        else
            s:stop();
            if (vn.callback) then
                local callback = vn.callback;
                vn.callback = false;
                cbresult = callback();
            end
            s:draw_huds(res.datas);
            s:tooltips(x, y);
            if cbresult then
                if type(cbresult) == 'function' then
                    return cbresult();
                else
                    s:start();
                    return true;
                end
            else
                return false;
            end
        end
        return n
    end;
    draw_huds = function(s, datas, clear)
        for k, vv in ipairs(datas) do
            s:draw_hud(vv.v, vv.idx, vv.x, vv.y, vv.scale, vv.alpha, nil, clear)
        end
    end;
    draw_hud = function(s, v, idx, x, y, scale, alpha, target, clear)
        if scale == 0 or alpha == 0 then
            return;
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
            local f = s:frame(v, idx, nil, nil, nil, true, true);
            if f then
                xpos = xpos + f.w * scale;
                ypos = ypos + f.h * scale / 2.0;
            end
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
                    if clear then
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
            if clear then
                sprite.draw(s.bg_spr, xpos, ycur, wmax, htotal, target, xpos, ycur);
                return
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
    enabled = function(s, v)
        return not s:gobf(v).enablefn or s:gobf(v):enablefn();
    end;
    update_tooltip = function(s, v)
        local xx, yy = s:postoxy(v);
        local sp = s:frame(v, 0);
        local text, pos, clear_under_tooltip = s:gobf(v):tooltipfn();
        if text then
            s:tooltip(text, pos, xx, yy, sp.w, sp.h, clear_under_tooltip);
        end
    end;
    tooltip = function(s, text, pos, x, y, vw, vh, clear_under_tooltip)
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
        if clear_under_tooltip then
            sprite.copy(s.bg_spr, xt, yt, w, h, target, xt, yt);
        end
        sprite.draw(label, target, xt, yt);
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
    empty_frame = {["spr"] = empty_s, ["w"] = 0, ["h"] = 0, ["tmp"] = false};
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
