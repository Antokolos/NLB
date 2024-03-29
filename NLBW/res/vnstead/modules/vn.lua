-- vn module
require 'timer'
require 'theme'
require 'sprites'
require 'modules/fonts'
require 'modules/gobj'
require 'modules/gr'
require 'modules/vnspr'
require 'modules/log'
require 'modules/nlb'

game.timer = stead.hook(game.timer, function(f, s, cmd, ...)
    return vntimer(f, s, cmd, ...);
end)

game.fading = stead.hook(game.fading, function(f, s, cmd, ...)
    return vnfading(f, s, cmd, ...);
end)

vntimer = function(f, s, cmd, ...)
--    if not vn.on then
--        return f(s, cmd, ...)
--    end
    local update_cursor_result = vn:update_cursor();

    vn.vnticks_diff = get_ticks() - vn.vnticks;
    vn.renewticks_diff = get_ticks() - vn.renewticks;

    if (vn.vnticks_diff <= vn.hz) then
        if vn:preload() then
            return update_cursor_result;
        end
    end
    vn.slowcpu = (vn.vnticks_diff > vn:ticks_threshold());
    log:trace("vnticks_diff = " .. vn.vnticks_diff);
    vn.vnticks = get_ticks();
    local x, y = stead.mouse_pos();
    vn:enable_by_cursor(x, y);
    if vn.stopped then
        -- NB: do not put heavy code in onover/onout
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
            vn:textbg(vn.offscreen)
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
    if vn._need_renew and (vn.renewticks_diff > vn:renew_threshold()) then
        vn._need_renew = false;
        vn.renewticks = get_ticks();
        vn:renew();
    end
    if vn._need_update then
        vn._need_update = false;
        RAW_TEXT = true;
        return game._lastdisp or "";
    end
    return update_cursor_result;
end

--if not game.old_fading then
--    game.old_fading = game.fading
--end

vnfading = function(f, s, cmd, ...)
--    if not vn.on then
--        return f(s, cmd, ...)
--    end
    local b = vn.bg_changing
    if vn.skip_mode then return end
    --if game.old_fading(s) or b then
    if b then
        return vn.fading
    end
    if vn.stopped then return end
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
    text_sprites_cache = {};
    undertextb_cache = {};
    undertexti_cache = {};
    scraps_cache = {};
    load_once_sprite_keys = {};
    _effects = {};
    _pending_effects = {};
    _dirty_rects = {};
    _bg = false;
    _need_update = false;
    _need_renew = false;
    _wf = 0;
    cache_effects = true;
    tmr = 20;
    hz_onthefly = 20;
    hz_preloaded = 40;
    hz = 20;
    slowcpu = false;
    vnticks = 0;
    renewticks = 0;
    vnticks_diff = 0;
    renewticks_diff = 0;
    hudFont = false;
    hudFont_H1 = false;
    empty_s = false;
    fln_s = false;
    frn_s = false;
    busy_spr = false;
    var {
        on = true,
        dbg = false,
        stopped = true,
        finishing = false,
        partial_clear = true,
        speed = 500,
        fading = 8,
        bgalpha = 127,
        stp = 1,
        callback = false,
        extent = 5,
        default_label_extent = 4,
        default_tooltip_offset = 5,
        hud_color = 'black',
        hud_bgcolor = 'white',
        hud_bgalpha = 127,
        hud_line_spacing = 0,
        hud_indent_spacing = 25,
        pause_frames = 0,
        pause_callback = false,
        direct_lock = false,
        use_src = false,
        cursor_need_update = false;
    };
    ticks_threshold = function(s)
        return s.hz * 2;
    end,
    turnon = function(s)
        -- s._bg = false; -- possible bugfix???
        s.on = true;
    end,
    turnoff = function(s)
        -- s._bg = false; -- possible bugfix???
        s.on = false;
    end,
    screen = function(s)
        if theme.get('scr.gfx.mode') == 'direct' then
            return gr:screen();
        else
            return s.offscreen
        end
    end;
    standard_renew = function(s)
        s:clear_bg();
        s:invalidate_all();
        s:startcb(function() s:commit(); end);
    end;
    renew = function(s)
        if s:add_all_missing_children() then
            s:invalidate_all();
            s:start();
        else
            s:invalidate_all();
        end;
    end;
    renew_threshold = function(s)
        return 350;
    end;
    rst = function(s)
        s:clear_cache();
        s.sprite_cache = {};
        s.sprite_cache_data = {};
        s.text_sprites_cache = {};
        s.undertextb_cache = {};
        s.undertexti_cache = {};
        s.scraps_cache = {};
        s.load_once_sprite_keys = {};
        s._effects = {};
        s._pending_effects = {};
        s._dirty_rects = {};
        s._bg = false;
        s._need_update = false;
        s._need_renew = false;
        s.finishing = false;
        s._wf = 0;
    end;
    ini = function(s, load)
        s.fading = 8;
        local here = here();
        if here and here.initf and here.theme_file then
            nlb:theme_switch(here:theme_file(), true);
            local x, y, w, h = here:initf();
            if s:in_choices() then
                local is_end = true;
                for i, v in ipairs(objs()) do
                    if (stead.nameof(v) ~= stead.nameof(_try_again)) then
                        is_end = false;
                    end
                end
                if not is_end then
                    s._wf = 0;
                end
            end
            theme.win.geom(x, y, w, h);
        end;
        s.vnticks = stead.ticks();
        s.renewticks = s.vnticks;
        s.vnticks_diff = s:ticks_threshold();
        s.renewticks_diff = s:renew_threshold();
        local tr = nlb:theme_root();
        s.hudFont = gr:font(tr .. 'fonts/STEINEMU.ttf', 29);  -- Medieval_English.ttf
        s.hudFont_H1 = gr:font(tr .. 'fonts/STEINEMU.ttf', 45);  -- Medieval_English.ttf
        s.empty_s = gr:load('gfx/empty.png');
        if nlb:file_exists(tr .. 'gfx/fl.png') then
            s.fln_s = gr:load(tr .. 'gfx/fl.png');
        else
            s.fln_s = gr:blank(1, 1);
        end
        if nlb:file_exists(tr .. 'gfx/fr.png') then
            s.frn_s = gr:load(tr .. 'gfx/fr.png');
        else
            s.frn_s = gr:blank(1, 1);
        end
        if LANG == "ru" then
            s.busy_spr = vn:label("Загрузка...", 40, "#ffffff", "black");
        else
            s.busy_spr = vn:label("Loading...", 40, "#ffffff", "black");
        end
        s:request_full_clear();
        --if not load or not s.on then -- causes bugs???
        --    return
        --end
        local i, v;
        for i, v in ipairs(s._effects) do
            v.hasmore = true;
            -- v.step = s:get_init_step(v); -- This causes bug with text when traversing through the auto links, text line is not shown after game load. It is better to just remove that :)
            s:load_effect(v);
        end
        s:set_bg(s._bg, true);
        s:draw_notes();
        s:add_all_missing_children();
        if s.direct_lock then
            s:lock_direct();
        end
        s:start();
    end;
    init = function(s)
        s.scr_w = theme.get 'scr.w'
        s.scr_h = theme.get 'scr.h'
        s.offscreen = gr:blank(s.scr_w, s.scr_h)
        s.blackscreen = gr:box(s.scr_w, s.scr_h, 'black')
        s:request_full_clear();
        s.hz = s.hz_onthefly;
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
        if not vv or v.nam == vv.nam then --or info_over.nam == vv.nam or info_out.nam == vv.nam then
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
            local vv, gob = s:test_clickc(v, x, y);
            if vv then
                return vv, gob;
            end
        end
        return false;
    end;
    test_clickc = function(s, v, x, y)
        -- here we are trying to traverse down through the hierarchy in order to determine, whether we clicked some child or not
        -- the point of this method is to try to click the child which is closest to the leaf child in the hierarchy
        for ii, vv in ipairs(v.children) do
            local vvv, gob = s:test_clickc(s:childf(vv), x, y);
            if vvv then
                return vvv, gob;
            end
        end
        return s:test_clickv(v, x, y);
    end;
    test_clickv = function(s, v, x, y)
        local active = not s:is_inactive_due_to_anim_state(v);
        local gob = s:gobf(v);
        local morphover = s:get_morph(v, true);
        local has_gob_click_handler = (gob and gob.onclick);
        local has_morphover_click_handler = (morphover and morphover.onclick);
        local has_gob_or_morph = has_gob_click_handler or has_morphover_click_handler;
        if active and has_gob_or_morph and s:enabled(v) and s:inside_spr(v, x, y) then
            if has_morphover_click_handler then
                return v, morphover;
            else -- has_gob_click_handler, or else we won't get there at all
                return v, gob;
            end
        end
        return false;
    end;
    click = function(s, x, y, a, b, c, d)
--        if not s.on then
--            return;
--        end
        local v, g = s:test_click(x, y);
        if v then
            return s:click_sprite(v, g);
        end
    end;
    click_sprite = function(s, v, g)
        if not v.noactredraw then
            s:overf(v, false);
        end
        s:invalidate_all();
        local callback = function()
            g:onclick(s);
            s:pause(1, function()
                if here().autos then
                    here():autos();
                end
            end);
        end;
        if not v.noactredraw then
            s:startcb(callback);
        else
            callback();
            s.stopped = false;
        end
        return true;
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
    shapechange = function(s, v, is_over, start_immediately)
        local morph = s:get_morph(v, is_over);
        if not morph then
            return false;
        end
        s:hide(v);
        s:effect_int(nil, morph, is_over);
        if start_immediately then
            s:start();
        end
        return morph;
    end;
    over = function(s, x, y, a, b, c, d)
--        if not s.on then
--            return;
--        end
        for i, v in ipairs(s._effects) do
            local active = not s:is_inactive_due_to_anim_state(v);
            if active and s:gobf(v).onover and s:enabled(v) and not v.mouse_over and s:inside_spr(v, x, y) then
                s:overf(v, true);
            end
        end
    end;
    overf = function(s, v, start_immediately)
        if v.mouse_over or not s:gobf(v).onover then
            -- Double-check for safety, now you can call it anywhere
            return;
        end
        s:gobf(v):onover();
        s:update_tooltip(v);
        local morph = s:shapechange(v, true, start_immediately);
        if not morph then
            v.mouse_over = true;
            if s.stopped then
                s.stopped = false;
            end
        end
        return morph;
    end;
    out = function(s, x, y, a, b, c, d)
--        if not s.on then
--            return;
--        end
        for i, v in ipairs(s._effects) do
            -- Don't doing not s:is_inactive_due_to_anim_state(v); check, because I want to always hide tooltip
            if s:gobf(v).onout and s:enabled(v) and v.mouse_over and not s:inside_spr(v, x, y) then
                s:outf(v, true);
            end
        end
    end;
    outf = function(s, v, start_immediately)
        if not v.mouse_over or not s:gobf(v).onout then
            -- Double-check for safety, now you can call it anywhere
            return;
        end
        s:gobf(v):onout();
        s:update_tooltip(v, true, true);
        local morph = s:shapechange(v, false, start_immediately);
        if not morph then
            v.mouse_over = false;
            if s.stopped then
                s.stopped = false;
            end
        end
        return morph;
    end;
    request_full_clear = function(s)
        s.partial_clear = false;
    end;
    need_update = function(s)
        s._need_update = true;
    end;
    need_renew = function(s)
        s._need_renew = true;
    end;
    split_url = function(s, url)
        -- Splits the url into main part and extension part
        return url:match("^(.+)(%..+)$")
    end;
    busy = function(s, busy, job, callback_to_run_after, do_not_show_label)
        if busy then
            if not do_not_show_label then
                s:show(s.busy_spr, 'middle');
            end
            if job then
                local cb = function()
                    job();
                    if not do_not_show_label then
                        vn:hide(vn.busy_spr);
                    end
                    if callback_to_run_after then
                        return callback_to_run_after;
                    else
                        return true;
                    end
                end
                s:startcb(cb);
            else
                s:start();
            end
        else
            if not do_not_show_label then
                s:hide(s.busy_spr);
            end
            s:start();
        end
    end;
    preload_effect = function(s, image, startFrame, maxStep, fromStop, maxPreload, callback_to_run_after, do_not_show_label, loadOnce)
        local prefix, extension = s:split_url(image);
        if (s.load_once_sprite_keys[prefix]) then
            if callback_to_run_after then
                vn:startcb(callback_to_run_after);
            end
            return;
        end
        local job = function()
            if not fromStop then
                fromStop = 0;
            end
            if not maxPreload then
                maxPreload = startFrame + 2;
            end
            local v = { pic = image, nam = image, start = startFrame, max_step = maxStep, from_stop = fromStop, load_once = loadOnce };
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
                local mst = milestones[i];
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
        if v.preserved or v.load_once then
            log:dbg(prefix .. " should be preserved");
            s.load_once_sprite_keys[prefix] = true;
        end
        local start = s:get_start(v);
        local maxstep = s:get_max_step(v);
        log:dbg("Loading effect " .. v.nam .. "; start = " .. start .. "; maxstep = " .. maxstep);
        for sprStep = start, maxstep do
            v.spr[sprStep] = vnspr {
                nam = v.nam .. tostring(sprStep),
                preloaded_effect = false,
                alpha = 255,
                scale = 1.0,
                origin = false,
                cache = false,
                sprStep = sprStep,
                v = v,
                prefix = prefix,
                extension = extension
            };
        end
    end;
    free_effect = function(s, v)
        for i, vv in pairs(v.spr) do
            if vv.free then
                vv:free();
            else
                log:err("Error freeing sprite " .. v.pic .. "@" .. i);
            end
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
                    if (get_ticks() - s.vnticks > s.hz) then
                        return false;
                    end
                end
            else
                s.sprite_cache_data[k] = nil;
            end
            if (get_ticks() - s.vnticks > s.hz) then
                return false;
            end
        end
        return true;
    end;
    -- Call clear_cache periodically to free memory from possible garbage...
    clear_cache = function(s)
        local load_once_cache = {};
        local load_once_cache_data = {};
        for k, w in pairs(s.sprite_cache) do
            if s.load_once_sprite_keys[k] then
                log:dbg(k .. " is loaded once or preserved");
                load_once_cache[k] = w;
            else
                for i, ss in pairs(w) do
                    if not ss.loaded then
                        gr:free(ss);
                    end
                end
            end
        end
        for k, w in pairs(s.sprite_cache_data) do
            if s.load_once_sprite_keys[k] then
                load_once_cache_data[k] = w;
            end
        end
        for kk, ww in pairs(s.text_sprites_cache) do
            for ii, sss in ipairs(ww.sprites) do
                gr:free(sss.spr);
            end
        end
        s.sprite_cache = {};
        for k, w in pairs(load_once_cache) do
            s.sprite_cache[k] = w;
        end
        s.sprite_cache_data = {};
        for k, w in pairs(load_once_cache_data) do
            s.sprite_cache_data[k] = w;
        end
        s.text_sprites_cache = {};
        for k, w in pairs(s.undertextb_cache) do
            gr:free(w);
        end
        s.undertextb_cache = {};
        for k, w in pairs(s.undertexti_cache) do
            gr:free(w);
        end
        s.undertexti_cache = {};
        for k, w in pairs(s.scraps_cache) do
            gr:free(w);
        end
        s.scraps_cache = {};
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
        local g1 = stead.ref(n);
        if not g1 then
            return
        end
        local nam_g1 = stead.nameof(g1);
        for i, k in ipairs(s._pending_effects) do
            local g2 = stead.ref(k.gob);
            if nam_g1 == stead.nameof(g2) then
                return k, i
            end
        end
    end;

    glookup = function(s, n)
        if not n then return end
        local i, k
        local g1 = stead.ref(n);
        if not g1 then
            return
        end
        local nam_g1 = stead.nameof(g1);
        for i, k in ipairs(s._effects) do
            local g2 = stead.ref(k.gob);
            if nam_g1 == stead.nameof(g2) then
                return k, i
            end
        end
    end;

    real_size = function(s, v, idx)
        local xarm, yarm = s:abs_arm(v, idx);
        local sp = s:frame(v, idx);
        if sp.tmp then
            gr:free(sp.spr);
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

    -- todo: if _effects will be map instead of array, this method can be much faster...
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
        s:invalidatev(v);
    end;

    invalidatev = function(s, v)
        if v then
            s:set_hasmore_all(v, true);
            s.stopped = false;
        end
    end;

    invalidatebyv = function(s, v)
        for i, vv in ipairs(s._effects) do
            if s:collision(vv, s:get_spr_rct(v)) then
                s:invalidatev(vv);
            end
        end
        for i, vv in ipairs(s._pending_effects) do
            if s:collision(vv, s:get_spr_rct(v)) then
                s:invalidatev(vv);
            end
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

        local is_load_once = g.load_once;
        if not is_load_once then
            is_load_once = false;
        end

        local dirty_draw = g.dirty_draw;
        if not dirty_draw then
            dirty_draw = false;
        end

        local parent_nam = false;
        if parent_eff then
            parent_nam = parent_eff.nam;
        end
        local topmost = false;
        if g.topmost then
            topmost = true;
        end
        local cache_text = false;
        if g.cache_text then
            cache_text = true;
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
            load_once = is_load_once,
            topmost = topmost,
            cache_text = cache_text,
            looped = g.looped,
            noactredraw = g.noactredraw,
            showoncur = g.showoncur,
            pause = g.pause,
            collapsable = g.collapsable,
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

        if g.showoncur then
            -- Should be disabled by default, enabled only when cursor is over it
            disable(g);
        end
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
                idx = s:get_init_step(v);
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

    set_bg = function(s, picture, force)
        if not picture then
            s.bg_spr = gr:box(s.scr_w, s.scr_h, theme.get 'scr.col.bg');
            s._bg = false;
            return
        end
        if s.bg_spr then
            if force or s._bg ~= picture then
                local bg_spr = gr:load(picture);
                gr:copy(bg_spr, s.bg_spr);
                gr:free(bg_spr);
            else
                log:dbg("Background " .. picture .. " is already set, doing nothing");
            end
        else
            s.bg_spr = gr:load(picture)
        end
        if not s.bg_spr then
            error("Can not load bg sprite:" .. tostring(picture))
        end
        s._bg = picture;
    end;

    frame = function(s, v, idx, target, x, y, only_compute, free_immediately)
        if not v.spr or not v.spr[idx] then
            log:warn("nonexistent sprite when trying to get frame " .. tostring(idx) .. " of " .. v.nam);
            if debug then log:warn(debug.traceback()); end;
            return s:empty_frame(0, 0);
        end
        local sp = v.spr[idx];
        local ospr = sp:val();
        if not ospr then -- Strange error when using resources in idf...
            log:err("filesystem access problem when trying to get frame " .. tostring(idx) .. " of " .. v.nam);
            log:err(debug.traceback());
        return s:empty_frame(0, 0);
        end
        if not x then
            x = 0;
        end
        if not y then
            y = 0;
        end
        if ospr.loaded then
            if sp:invisible() then
                log:dbg("Frame " .. tostring(idx) .. " of " .. v.nam .. " is invisible");
                return s:empty_frame(ospr.w, ospr.h);
            end
            local res = nil;
            if not target then
                res = gr:blank(ospr.w, ospr.h);
                target = res;
            end
            if not only_compute then
                gr:draw_ext(ospr.loaded, ospr.x, ospr.y, ospr.w, ospr.h, target, x, y);
            end
            if free_immediately and res then
                gr:free(res);
                res = nil;
            end
            return {["spr"] = res, ["w"] = ospr.w, ["h"] = ospr.h, ["tmp"] = (res ~= nil), ["preloaded_effect"] = sp.preloaded_effect, ["alpha"] = sp.alpha, ["scale"] = sp.scale};
        else
            local w, h = gr:size(sp:get_origin());
            if sp:invisible() then
                log:dbg("Frame " .. tostring(idx) .. " of " .. v.nam .. " is invisible");
                return s:empty_frame(w, h);
            end
            if not only_compute and target then
                gr:draw(ospr, target, x, y);
            end
            return {["spr"] = ospr, ["w"] = w, ["h"] = h, ["tmp"] = false, ["preloaded_effect"] = sp.preloaded_effect, ["alpha"] = sp.alpha, ["scale"] = sp.scale};
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

        if v.spr[idx] and v.spr[idx].preloaded_effect then
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
            local spr = gr:alpha(sp.spr, alpha);
            if sp.tmp then
                gr:free(sp.spr);
            end
            if not only_compute then
                gr:draw(spr, s:screen(), x, y);
            end
            gr:free(spr);
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
    spritepad = 8;
    clear = function(s, x, y, w, h, target)
        local pad = s.spritepad;
        if not target then
            target = s:screen();
        end
        gr:copy_ext(s.bg_spr, x - pad, y - pad, w + 2 * pad, h + 2 * pad, target, x - pad, y - pad);
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
                spr = gr:scale(sp.spr, scale, scale, false);
            end
            w, h = gr:size(spr);
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
            gr:draw(spr, s:screen(), x, y)
        end
        if not sp.preloaded_effect and sp.spr ~= spr then
            gr:free(spr)
        end
        if sp.tmp then
            gr:free(sp.spr);
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
--        if not s.on then
--            callback();
--            return;
--        end
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
        if s.skip_mode then
            s._scene_effect = false
        end
        s.stopped = false;
        s.finishing = false;
        s:process(true) -- draw frame to offscreen
        if s._scene_effect == 'fading' or s._scene_effect == 'fade' then
            theme.gfx.bg(s.blackscreen)
            vn.bg_changing = 1
        elseif s._scene_effect == 'dissolve' then
            vn.bg_changing = 2
            theme.gfx.bg(s.offscreen)
        else
            vn.bg_changing = false
            if not s.direct_lock then
                s:textbg(s.offscreen)
                theme.gfx.bg(s.offscreen)
            end
            return
        end
        return
        -- just transpose
    end;
    get_win_coords = function(s)
        --local scr_width, scr_height = theme.get('scr.w'), theme.get('scr.h');
        --local win_height = (scr_height * 10) / 54;
        --return math.floor(s.textpad), math.floor(scr_height - 2 * s.textpad - win_height), math.floor(scr_width - 2 * s.textpad), math.floor(win_height - 2 * s.textpad);
        return theme.get('win.x'), theme.get('win.y'), theme.get('win.w'), theme.get('win.h');
    end;
    get_end_coords = function(s)
        local tr = nlb:theme_root();
        local scr_width, scr_height = theme.get('scr.w'), theme.get('scr.h');
        local sp_end;
        if _export_lang == 'ru' then
            sp_end = sprite.load(tr .. 'gfx/theendru.png');
        else
            sp_end = sprite.load(tr .. 'gfx/theenden.png');
        end
        local spw, sph = sprite.size(sp_end);
        sprite.free(sp_end);
        local wf, hf = 0, 200;
        if nlb:file_exists(tr .. 'gfx/fl.png') then
            wf, hf = sprite.size(s.fln_s);
        end
        local win_width = spw;
        local win_height = hf;
        local real_height = win_height - 2 * s.textpad;
        return math.floor((scr_width - win_width) / 2), math.floor((scr_height - real_height) / 2), math.floor(win_width), math.floor(real_height);
    end;
    get_choices_coords = function(s)
        local scr_width, scr_height = theme.get('scr.w'), theme.get('scr.h');
        local win_height = scr_height / 2;
        return math.floor(2 * scr_width / 10), math.floor((scr_height - win_height) / 2), math.floor(6 * scr_width / 10), math.floor(win_height);
    end;
    auto_geom = function(s, effect, callback)
        local x, y, w, h = s:get_win_coords();
        local wf, hf = 0, 0;
        local tr = nlb:theme_root();
        if nlb:file_exists(tr .. 'gfx/fl.png') then
            wf, hf = sprite.size(s.fln_s);
        end
        return s:geom(x, y, w, h, effect, wf, callback);
    end;
    auto_geom_end = function(s, effect, callback)
        local x, y, w, h = s:get_end_coords();
        local wf, hf = 0, 0;
        local tr = nlb:theme_root();
        if nlb:file_exists(tr .. 'gfx/fl.png') then
            wf, hf = sprite.size(s.fln_s);
        end
        return s:geom(x, y, w, h, effect, wf, callback);
    end;
    auto_geom_choices = function(s, effect, callback)
        local x, y, w, h = s:get_choices_coords();
        return s:geom(x, y, w, h, effect, nil, callback);
    end;
    -- effect is effect name, like 'dissolve'
    -- wf is the fancy border width, in pixels
    -- fln and frn are paths to the borders' images
    geom = function(s, x, y, w, h, effect, wf, callback)
        -- wf can be zero, this means do not use borders
        if wf then
            s._wf = wf;
        else
            s._wf = 0;
        end
        theme.win.geom(x, y, w, h);
        s:request_full_clear();
        if callback then
            s:startcb(callback, effect);
        else
            s:start(effect);
        end;
        s:commit();
        return x, y, w, h;
    end;
    scene = function(s, bg, eff, preserve_cache)
        s:cleanup_scene(preserve_cache);
        s._scene_effect = eff
        -- if bg is nil, simple box sprite will be set
        s:set_bg(bg)
        s:clear_bg();
        s:draw_notes();
    end;
    cleanup_scene = function(s, preserve_cache)
        local preserved_effects = {};
        local preserved_pending_effects = {};
        for i, v in ipairs(s._effects) do
            if v.preserved then
                stead.table.insert(preserved_effects, v);
            elseif not v.load_once then
                s:free_effect(v)
            end
        end
        for i, v in ipairs(s._pending_effects) do
            if v.preserved then
                stead.table.insert(preserved_pending_effects, v);
            elseif not v.load_once then
                s:free_effect(v)
            end
        end
        s._effects = preserved_effects;
        s._pending_effects = preserved_pending_effects;
        s._dirty_rects = {};
        if not preserve_cache then
            s:clear_cache();
        end
    end;
    draw_notes = function(s)
        if s.dbg then
            local label, w, h = s:label(nlb:curloc().notes);
            gr:draw(label, s.bg_spr, 0, 0);
            gr:free(label);
        end
    end;
    in_vnr = function(s)
        return here()._is_vnr;
    end;
    in_choices = function(s)
        return here()._is_vn_choices;
    end;
    force_textbg = function(s)
        return here().textbg;
    end;
    textpad = 8;
    textbg = function(s, to)
        if (s.direct_lock or not s:in_vnr()) and not s:force_textbg() then
            return;
        end
        local pad = s.textpad;
        local wf = s._wf;
        local w, h = theme.get 'win.w', theme.get 'win.h'
        local x, y = theme.get 'win.x', theme.get 'win.y'
        local invw, invh = theme.get 'inv.w', theme.get 'inv.h'
        local invx, invy = theme.get 'inv.x', theme.get 'inv.y'
        gr:copy_ext(s.bg_spr, x - pad - wf, y - pad, w + pad * 2 + wf * 2, h + pad * 2, to, x - pad - wf, y - pad);
        gr:copy_ext(s.bg_spr, invx, invy, invw, invh, to, invx, invy);
        if (wf > 0) then
            -- You can use this instead of fln and frn gr:box(wf, h + pad * 2, 'black', s.bgalpha);
            gr:draw(s.fln_s, to, x - pad - wf, y - pad);
            gr:draw(s.frn_s, to, x + w + pad, y - pad);
        end
        local sb, si = s:undertext(w, h, invw, invh);
        gr:draw(sb, to, x - pad, y - pad)
        gr:draw(si, to, invx, invy)
    end;
    commit = function(s, from)
        if not from then
            from = s:screen();
        end
        if s.direct_lock then
            return;
        end
        gr:copy(from, s.offscreen);
        s:textbg(s.offscreen);
        theme.gfx.bg(s.offscreen);
    end;
    actonkey = function(s, down, key)
        for k, v in ipairs(s._effects) do
            local gob = s:gobf(v);
            if gob and gob.actonkey and s:enabled(v) and gob:actonkey(down, key) then
                return true;
            end
        end
        return false;
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
        s.finishing = true;
        return r
    end;
    stop = function(s)
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
        if not s.direct_lock then
            s.direct_lock = true;
            theme.set('scr.gfx.mode', 'direct');
        end
    end;
    enter_direct = function(s)
        if not s.direct_lock then
            theme.set('scr.gfx.mode', 'direct');
        end
    end;
    unlock_direct = function(s)
        if s.direct_lock then
            s.direct_lock = false;
            theme.reset('scr.gfx.mode');
        end
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
                else
                    if v.looped and not s.finishing then
                        s:set_step(v, s:get_init_step(v));
                        return true;
                    end
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
                else
                    if v.looped and not s.finishing then
                        s:set_step(v, s:get_max_step(v) - s:get_from_stop(v));
                    end
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
        local topmost = {};
        for i, v in ipairs(s._effects) do
            local need_skip = v.pause and v.lastdraw and (s.vnticks - v.lastdraw >= 0) and (s.vnticks - v.lastdraw < v.pause);
            if not need_skip and s:enabled(v) then
                v.lastdraw = s.vnticks;
                if v.preserved or v.topmost then
                    stead.table.insert(topmost, i);
                else
                    local r = s:draw_step(v, clear);
                    if r.data then
                        stead.table.insert(result.datas, r.data);
                        result.hasmore = (result.hasmore or r.hasmore);
                    end
                end
            end
        end
        for ii, kk in ipairs(topmost) do
            local vv = s._effects[kk];
            local r = s:draw_step(vv, clear);
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
                if s:need_to_clear(v) then
                    s:clear_bg_under_sprite(v);
                end
            end
        else
            gr:copy(s.bg_spr, s:screen());
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
                    return cbresult();
                else
                    s:start();
                    return true;
                end
            else
                return false;
            end
        elseif initpass then
            s:commit(s:screen())
            s:leave_direct();
        end
        return n
    end;
    draw_huds = function(s, datas, clear, hide)
        for k, vv in ipairs(datas) do
            s:draw_hud(vv, false, nil, clear, hide)
        end
    end;
    draw_hud = function(s, data, only_compute, target, clear, hide)
        local v, idx, x, y, scale, alpha = data.v, data.idx, data.x, data.y, data.scale, data.alpha;
        if scale <= 0.0 or alpha <= 0 then
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
            ypos = ypos + data.h * scale * s:get_pos_coef(v);
            xpos = xpos + s.default_label_extent + s.default_tooltip_offset;
            local cached_sprites_idx = v.nam .. '@' .. tostring(scale) .. '_' .. tostring(alpha);
            local cached_sprites = s.text_sprites_cache[cached_sprites_idx];
            local sprites = {};
            local htotal = 0;
            local wmax = 0;
            local empty_text = true;
            if not cached_sprites then
                local tmp_sprite = clear or only_compute;
                for k, vv in pairs(texts) do
                    if vv.text and vv.text ~= '' then
                        empty_text = false;
                        local color = vv.color;
                        if not color then
                            color = s.hud_color;
                        end
                        local text = vv.text:match("^== (.*) ==$");
                        local font = text and s.hudFont_H1 or s.hudFont;
                        text = text or vv.text;
                        local textSpriteInit = gr:text(font, text, color);
                        local textSpriteScaled;
                        if scale ~= 1.0 then
                            textSpriteScaled = gr:scale(textSpriteInit, scale, scale, false);
                            gr:free(textSpriteInit);
                        else
                            textSpriteScaled = textSpriteInit;
                        end
                        local textSprite;
                        if alpha ~= 255 then
                            textSprite = gr:alpha(textSpriteScaled, alpha);
                            gr:free(textSpriteScaled);
                        else
                            textSprite = textSpriteScaled;
                        end
                        local w, h = gr:size(textSprite);
                        if tmp_sprite then
                            gr:free(textSprite);
                            textSprite = nil;
                        end
                        w = w + s.extent;
                        if w > wmax then
                            wmax = w;
                        end
                        htotal = htotal + h + s.hud_line_spacing;
                        stead.table.insert(sprites, {["spr"] = textSprite, ["w"] = w, ["h"] = h});
                    else
                        htotal = htotal + s.hud_indent_spacing;
                        stead.table.insert(sprites, {["spr"] = nil, ["w"] = 0, ["h"] = s.hud_indent_spacing});
                    end
                end
                if v.cache_text and not tmp_sprite then
                    -- You can combine multiple text lines into single sprite using the following code
                    -- I disabled this, because it is not much faster than multiple lines rendering
                    --local combined = gr:blank(wmax, htotal);
                    --s:combine_text_sprites(sprites, combined, 0, 0, true);
                    --sprites = {};
                    --stead.table.insert(sprites, {["spr"] = combined, ["w"] = wmax, ["h"] = htotal});

                    cached_sprites = {["sprites"] = sprites, ["htotal"] = htotal, ["wmax"] = wmax}
                    s.text_sprites_cache[cached_sprites_idx] = cached_sprites;
                end
            else
                sprites = cached_sprites.sprites;
                empty_text = not sprites or next(sprites) == nil;
                htotal = cached_sprites.htotal;
                wmax = cached_sprites.wmax;
            end
            htotal = htotal - s.hud_line_spacing; -- spacing for the last line should not be taken into account
            local ycur = ypos - htotal * s:get_pos_coef(v);
            if empty_text then
                return {["v"] = v, ["x"] = xpos, ["y"] = ycur, ["w"] = 0, ["h"] = 0};
            end
            local rct = {["v"] = v, ["x"] = xpos, ["y"] = ycur, ["w"] = wmax, ["h"] = htotal};
            if only_compute then
                return rct;
            end
            if hide or v.dirty_draw then
                log:trace(v.nam .. " hud is dirty");
                s._dirty_rects[v.nam .. '_hud'] = rct;
            end
            s:combine_text_sprites(sprites, target, xpos, ycur, not cached_sprites, clear);
            return rct;
        end
    end;
    -- This method returns the position of the text based on the coords origin on the sprite (see eff property).
    -- If the sprite has a parent, then the text should be positioned at the center, regardless of the coords origin.
    get_pos_coef = function(s, v)
        local parent = s:parentf(v);
        if parent then
            return 0.5;
        end
        if s:get_pos(v):find 'top' then
            -- should go first to prevent incorrect detection of top-middle
            return 0;
        elseif s:get_pos(v):find 'bottom' then
            -- should go second to prevent incorrect detection of bottom-middle
            return 1;
        elseif s:get_pos(v):find 'middle' then
            return 0.5;
        end
    end;
    combine_text_sprites = function(s, sprites, target, x, y, freesp, clear)
        local xpos = x;
        local ycur = y;
        for i, ss in ipairs(sprites) do
            if (ss.spr or clear) and ycur + ss.h > 0 and ycur < tonumber(s.scr_h) then
                if clear then
                    s:clear(xpos, ycur, ss.w, ss.h + s.hud_line_spacing, target);
                else
                    gr:draw(ss.spr, target, xpos, ycur);
                end
            end
            ycur = ycur + ss.h + s.hud_line_spacing;
            if freesp and ss.spr then
                gr:free(ss.spr);
            end
        end
    end;
    tooltips = function(s, x, y)
--        if not s.on then
--            return;
--        end
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
        local result = not disabled(gob) and (not gob.alive or gob:alive()) and (not gob.enablefn or gob:enablefn()) and not s:should_ignore(v);
        if result then
            v.was_disabled = false;
        elseif not v.was_disabled then
            -- disabled => clear this sprite
            s:clear_bg_under_sprite(v, true);
            -- simulate onout, if it is defined
            if gob.onout then
                s:outf(v, true);
            end
            -- to redraw it when it will become enabled again
            v.hasmore = true;
            v.was_disabled = true;
        end
        return result;
    end;
    enable_by_cursor = function(s, x, y)
        local callbacks = {};
        local existing_callback = s.callback;
        local has_changes = false;
        for i, v in ipairs(s._effects) do
            if v.showoncur then
                local gob = s:gobf(v);
                local inside = s:inside_spr(v, x, y);
                local vdis = disabled(gob);
                if inside and vdis then
                    s:set_step(v, s:get_init_step(v), true);
                    has_changes = true;
                    stead.table.insert(callbacks, function()
                        enable(gob);
                        return v;
                    end);
                elseif not inside and not vdis then
                    has_changes = true;
                    if v.collapsable then
                        s:set_step(v, s:get_step(v), false);
                        stead.table.insert(callbacks, function()
                            disable(gob);
                            return v;
                        end);
                    else
                        disable(gob);
                    end
                end
            end
        end
        if not has_changes then
            s:process(true);
            return false;
        end
        s:startcb(function()
            if existing_callback then
                existing_callback();
            end
            for i, cb in ipairs(callbacks) do
                local v = cb();
                s:invalidatev(v);
                s:invalidatebyv(v);
            end
            return true;
        end);
        return true;
    end;
    update_tooltip = function(s, v, erase, hide)
        local xx, yy = s:postoxy(v);
        local text, pos, clear_under_tooltip = s:gobf(v):tooltipfn();
        if text then
            local sp = s:frame(v, s:get_init_step(v));
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
            local ttpos = pos;
            if not ttpos then
                if xmax - x - vw > x then
                    ttpos = "e";
                else
                    ttpos = "w";
                end
            end
            if ((xmax - xx > w) or (ttpos == "e")) and not (ttpos == "w") then
                xt = xx;
                yt = yy;
            else
                xt = x - w - s.default_tooltip_offset;
                yt = yy;
            end
        end
        if clear_under_tooltip or erase then
            gr:copy_ext(s.bg_spr, xt, yt, w, h, target, xt, yt);
        end
        if hide or v.dirty_draw then
            log:trace(v.nam .. " tooltip is dirty");
            s._dirty_rects[v.nam .. '_tooltip'] = {["v"] = v, ["x"] = xt, ["y"] = yt, ["w"] = w, ["h"] = h};
        end
        if not erase then
            gr:draw(label, target, xt, yt);
        end
        gr:free(label);
    end;
    -- Sprite with label text. You should call gr:free(), when you no longer need this.
    label = function(s, text, extent, color, bgcolor, bgalpha, font)
        if not color then
            color = s.hud_color;
        end
        if not bgcolor then
            bgcolor = s.hud_bgcolor;
        end
        if not bgalpha then
            bgalpha = s.hud_bgalpha;
        end
        if not extent then
            extent = s.default_label_extent;
        end
        if not font then
            font = s.hudFont;
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
                local spr = gr:text(font, t, color);
                local ww, hh = gr:size(spr);
                if ww > w then
                    w = ww;
                end
                stead.table.insert(texts, {["spr"] = spr, ["x"] = extent, ["y"] = h + extent});
                h = h + extent + hh;
            end
        end
        w = w + 2 * extent;
        h = h + extent;
        local label = gr:box(w, h, bgcolor, bgalpha);
        for ii, tt in ipairs(texts) do
            gr:draw(tt.spr, label, tt.x, tt.y);
            gr:free(tt.spr);
        end
        return label, w, h;
    end;
    set_hud_theme = function(s, theme_name)
        if theme_name == 'dark' then
            s.hud_color = 'white';
            s.hud_bgcolor = 'black';
        else
            s.hud_color = 'black';
            s.hud_bgcolor = 'white';
        end
        s.hud_bgalpha = 127;
    end,
    set_hud_spacing = function(s, line_spacing, indent_spacing)
        s.hud_line_spacing = line_spacing or 0;
        s.hud_indent_spacing = indent_spacing or 25;
    end,
    txt_line_fn = function(s, text, color)
        local txt = text;
        local clr = color;
        if not clr then
            clr = s.hud_color;
        end
        return function()
            local result = {};
            stead.table.insert(result, { ["text"] = txt, ["color"] = clr });
            return result;
        end
    end;
    empty_frame = function(s, w, h)
        return {["spr"] = s.empty_s, ["w"] = w, ["h"] = h, ["tmp"] = false, ["preloaded_effect"] = false, ["alpha"] = 0, ["scale"] = 0.0};
    end;
    undertext = function(s, w, h, invw, invh)
        local pad = s.textpad;
        local sb_index = tostring(w) .. "x" .. tostring(h);
        local si_index = tostring(invw) .. "x" .. tostring(invh);
        if not s.undertextb_cache[sb_index] then
            s.undertextb_cache[sb_index] = gr:box(w + pad * 2, h + pad * 2, 'black', s.bgalpha);
        end
        if not s.undertexti_cache[si_index] then
            s.undertexti_cache[si_index] = gr:box(invw, invh, 'black');
        end
        return s.undertextb_cache[sb_index], s.undertexti_cache[si_index];
    end;
    scrap = function(s, w, h)
        local sb_index = tostring(w) .. "x" .. tostring(h);
        if not s.scraps_cache[sb_index] then
            s.scraps_cache[sb_index] = gr:blank(w, h);
        end
        return s.scraps_cache[sb_index];
    end;
}

stead.module_init(function()
    vn:rst();
    vn:init()
end)

function vnr(v)
    if not v.nam then v.nam = 'vn'; v.disp = false; end
    v._is_vnr = true;
    return room(v)
end

function vn_choices(v)
    if not v.nam then v.nam = 'vn_choices'; v.disp = false; end
    v._is_vn_choices = true;
    return room(v)
end
