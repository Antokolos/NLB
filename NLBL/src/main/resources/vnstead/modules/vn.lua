-- vn module
require 'timer'
require 'theme'
require 'sprites'

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
    -- NB: do not put heavy code in onover/onout
    local x, y = stead.mouse_pos();
    vn:over(x, y);
    vn:out(x, y);

    if (get_ticks() - vnticks <= vn.hz) then
        return;
    end
    vnticks = get_ticks();
    if vn.pause_frames > 0 then
        vn.pause_frames = vn.pause_frames - 1;
        return;
    else
        local pausecb = vn.pause_callback;
        vn.pause_callback = false;
        if (pausecb) then
            pausecb();
            return;
        end
    end
    if vn.tostop then
        vn.tostop = false
        return vn:stop()
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
        RAW_TEXT = true
        return game._lastdisp
    end
    if not vn.stopped and not vn:process() then
        vn.tostop = true
    end
    if vn._need_update then
        vn._need_update = false;
        return true;
    end
end

--if not game.old_fading then
--	game.old_fading = game.fading
--end

vnfading = function(f, s, cmd, ...)
    if not vn.on then
        return f(s, cmd, ...)
    end
    local b = vn.bg_changing
    if vn.skip_mode then return end
    --	if game.old_fading(s) or b then
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
    _effects = {};
    _bg = false;
    _need_effect = false;
    _need_update = false;
    _wf = 0;
    _fln = nil;
    _frn = nil;
    hz = 18;
    var { speed = 500, fading = 8, bgalpha = 127 };
    var {
        on = true,
        stopped = true,
        uiupdate = false,
        win_x = 0,
        win_y = 0,
        win_w = 0,
        win_h = 0,
        up_x = 0,
        down_x = 0,
        callback = false,
        txtfun = false,
        xhud = 1540,
        yhud = 100,
        extent = 100,
        hud_color = '#000000',
        pause_frames = 0,
        pause_callback = false,
        direct_lock = false;
    };
    turnon = function(s)
        s.on = true;
    end,
    turnoff = function(s)
        s.on = false;
    end,
    screen = function(s)
        if s._need_effect then
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
            v.step = 0;
            s:load_effect(v)
            if v.step < v.max_step - v.from_stop then
                n = true
            end
        end
        s:set_bg(s._bg)
        s:start()
    end;
    init = function(s)
        s.scr_w = theme.get 'scr.w'
        s.scr_h = theme.get 'scr.h'
        s.offscreen = sprite.blank(s.scr_w, s.scr_h)
        s.blackscreen = sprite.box(s.scr_w, s.scr_h, 'black')
        timer:set(1); --(s.hz)
    end;
    get_spr_rct = function(s, v)
        local xs, ys = s:postoxy(v, v.step)
        local ws, hs = sprite.size(v.spr[v.step]:val());
        return { x = xs, y = ys, w = ws, h = hs };
    end;
    inside_spr = function(s, v, x, y)
        -- Click falls inside this picture
        local rct = s:get_spr_rct(v);
        return x >= rct.x and x <= rct.x + rct.w and y >= rct.y and y <= rct.y + rct.h;
    end;
    click = function(s, x, y, a, b, c, d)
        if not s.on then
            return;
        end
        for i, v in ipairs(s._effects) do
            if v.onclick and s:inside_spr(v, x, y) then
                v:onclick();
            end
        end
    end;
    over = function(s, x, y, a, b, c, d)
        if not s.on then
            return;
        end
        for i, v in ipairs(s._effects) do
            if v.onover and s:inside_spr(v, x, y) then
                v:onover();
            end
        end
    end;
    out = function(s, x, y, a, b, c, d)
        if not s.on then
            return;
        end
        for i, v in ipairs(s._effects) do
            if v.onout and not s:inside_spr(v, x, y) then
                v:onout();
            end
        end
    end;
    need_update = function(s)
        s._need_update = true;
    end;
    file_exists = function(s, name)
        local f = io.open(name, "r")
        if f ~= nil then io.close(f) return true else return false end
    end;
    split_url = function(s, url)
        -- Splits the url into main part and extension part
        return url:match("^(.+)(%..+)$")
    end;
    load_effect = function(s, v)
        local ss = s;
        if v.spr == nil then v.spr = {}; end;
        for sprStep = 0, v.max_step do
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
                    if ss:file_exists(v.pic) then
                        if sprStep == v.start then
                            return sprite.load(v.pic);
                        elseif sprStep > v.start then
                            return v.spr[v.start]:val();
                        end
                    else
                        return s:load_frame();
                    end
                    return nil;
                end,
                load_frame = function(s)
                    local prefix, extension = ss:split_url(v.pic);
                    local sprfile = prefix .. '.' .. string.format("%04d", sprStep) .. extension;
                    local loaded = nil;
                    if ss:file_exists(sprfile) then
                        loaded = sprite.load(sprfile);
                        if loaded then
                            s.was_loaded = true;
                        else
                            error("Can not load sprite: " .. tostring(sprfile));
                        end
                    elseif sprStep > v.start then
                        loaded = v.spr[sprStep - 1]:val();
                    end

                    return loaded;
                end,
                free = function(s)
                    if s.was_loaded and s.cache then
                        sprite.free(s.cache);
                        s.cache = nil;
                    end
                end
            };
        end
    end;
    free_effect = function(s, v)
        for sprStep = 0, v.max_step do
            v.spr[sprStep]:free();
            v.spr[sprStep] = nil;
        end
        v.spr = nil;
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
        s:free_effect(i);
        stead.table.remove(s._effects, k)
        return
    end;

    show = function(s, ...)
        return s:effect(...)
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

    size = function(s, v, idx)
        local xarm, yarm = s:arm(v, idx);
        local vw, vh = sprite.size(v.spr[idx]:val());
        return vw + xarm, vh + yarm;
    end;

    arm = function(s, v, idx)
        return s:arm_by_idx(v, idx, 1), s:arm_by_idx(v, idx, 2);
    end;

    arm_by_idx = function(s, v, idx, subidx)
        if not v.arm then
            return 0;
        end
        local arm = v.arm[idx];
        if not arm then
            arm = v.arm[0];
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

    effect = function(s, image, eff, speed, startFrame, framesFromStop, armarr, on_clk, on_over, on_out, tooltip_fn, enable_fn)
        local t = eff;
        local v

        if type(image) == 'string' then
            v = { pic = image, nam = image };
            image = v
        end

        if not startFrame then
            startFrame = 0;
        end

        if not framesFromStop then
            framesFromStop = 0;
        end

        local picture = image.pic
        local name = image.nam
        local v = {
            pic = picture,
            nam = name,
            eff = t,
            step = startFrame,
            start = startFrame,
            from_stop = framesFromStop,
            arm = armarr,
            onclick = on_clk,
            onover = on_over,
            onout = on_out,
            tooltipfn = tooltip_fn,
            enablefn = enable_fn;
        }

        if eff == 'hide' then
            s:hide(v)
            return
        end

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
            v.step = startFrame
            v.max_step = math.floor((speed or s.speed) / s.hz)
            v.from_stop = framesFromStop
            s:load_effect(v)
        end
        v.step = startFrame
        v.start = startFrame
        v.max_step = math.floor((speed or s.speed) / s.hz)
        v.from_stop = framesFromStop
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

        --- because none can be animated now...
        --- if v.eff ~= 'none' then
        s._need_effect = true
        --- end

        if not oe then
            stead.table.insert(s._effects, v)
        end

        return v
    end;

    pause = function(s, frames, callback)
        if type(callback) == "table" then
            local callbacks = function()
                local i, v = next(callback, nil) -- i is an index of t, v = t[i]
                while i do
                    if v then
                        v();
                    end
                    i, v = next(callback, i) -- get next index
                end
            end
            s.pause_callback = callbacks;
        else
            s.pause_callback = callback;
        end
        s.pause_frames = frames;
    end;

    postoxy = function(s, v, idx)
        if not idx then
            idx = 0;
        end
        local vw, vh = s:size(v, idx)
        local xarm, yarm = s:arm(v, idx)
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
    end;

    set_bg = function(s, picture)
        if not picture then
            s.bg_spr = sprite.box(s.scr_w, s.scr_h, theme.get 'scr.col.bg')
            s._bg = picture;
            return
        end
        if s.bg_spr then
            local bg_spr = sprite.load(picture);
            sprite.copy(bg_spr, s.bg_spr)
        else
            s.bg_spr = sprite.load(picture)
        end
        if not s.bg_spr then
            error("Can not load ng sprite:" .. tostring(picture))
        end
        s._bg = picture
    end;

    fade = function(s, v)
        local mxs, zstep = s:steps(v);
        local x, y, idx
        local fadein = (v.eff == 'fadein');
        if fadein then
            idx = v.step;
        else
            idx = v.max_step - v.step;
        end
        x, y = s:postoxy(v, idx)

        if fadein then
            spr = sprite.alpha(v.spr[idx]:val(), math.floor(255 * zstep / mxs))
        else
            spr = sprite.alpha(v.spr[idx]:val(), math.floor(255 * (1 - zstep / mxs)))
        end
        sprite.draw(spr, s:screen(), x, y);
        sprite.free(spr)
    end;
    none = function(s, v)
        local x, y
        x, y = s:postoxy(v, v.step)
        sprite.draw(v.spr[v.step]:val(), s:screen(), x, y);
    end;
    reverse = function(s, v)
        local x, y
        local idx = v.max_step - v.step;
        x, y = s:postoxy(v, idx)
        sprite.draw(v.spr[idx]:val(), s:screen(), x, y);
    end;
    moveout = function(s, v)
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
            x = math.floor(x_start + zstep * (s.scr_w - x_start) / mxs)
        elseif v.from == 'top' then
            y_end = -hs
            y = math.floor(y_start - zstep * (y_start - y_end) / mxs)
        elseif v.from == 'bottom' then
            y_end = s.scr_h
            --y = math.floor(y_start + zstep * (s.scr_h - y_start + vh) / mxs)
            y = math.floor(y_start + zstep * (s.scr_h - y_start) / mxs)
        end
        sprite.draw(v.spr[idx]:val(), s:screen(), x, y);
    end;

    zoom = function(s, v)
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
            return
        end

        if scale ~= 1.0 then
            spr = sprite.scale(v.spr[sprpos]:val(), scale, scale, false);
        else
            spr = v.spr[sprpos]:val()
        end

        local w, h = sprite.size(spr)

        x, y = s:postoxy(v, sprpos)

        if v.pos:find 'left' then
            --x = x - math.floor((ws - w))
            -- re-use x from postoxy()
        elseif v.pos:find 'right' then
            x = x + math.floor((ws - w))
        else
            x = x + math.floor((ws - w) / 2)
        end
        if v.pos:find 'top' then
            --y = y - math.floor((hs - h))
            -- re-use y from postoxy()
        elseif v.pos:find 'bottom' then
            y = y + math.floor((hs - h))
        elseif v.pos:find 'middle' then
            y = y + math.floor((hs - h) / 2)
        else
            y = y + math.floor((hs - h) / 2)
        end
        sprite.draw(spr, s:screen(), x, y)
        if v.spr[sprpos]:val() ~= spr then
            sprite.free(spr)
        end
    end;

    movein = function(s, v)
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
            x = math.floor(x_start - zstep * (s.scr_w - x_end) / mxs)
        elseif v.from == 'top' then
            y_start = -hs
            y = math.floor(y_start + zstep * (yarm - y_start) / mxs)
        elseif v.from == 'bottom' then
            y_start = s.scr_h
            y = math.floor(y_start - zstep * (s.scr_h - y_end) / mxs)
        end
        sprite.draw(v.spr[v.step]:val(), s:screen(), x, y);
    end;
    do_effect = function(s, v)
        if v.eff == 'movein' then
            return s:movein(v)
        elseif v.eff == 'moveout' then
            return s:moveout(v)
        elseif v.eff == 'fadein' or v.eff == 'fadeout' then
            return s:fade(v)
        elseif v.eff == 'zoomin' or v.eff == 'zoomout' then
            return s:zoom(v)
        elseif v.eff == 'reverse' then
            return s:reverse(v)
        else
            return s:none(v)
        end
    end;
    startcb = function(s, callback, effect)
        s.callback = callback;
        s:start(effect);
    end;
    start = function(s, effect, uiupdate)
        -- do first step
        if not s.bg_spr then
            error "No scene background specified!"
        end
        if s._need_effect then -- doing some effect(s)
        s:enter_direct();
        s.stopped = false;
        if uiupdate then
            s.uiupdate = true;
        else
            -- NB: uiupdate can be nil, but here I want it to be true or false, not nil
            s.uiupdate = false;
        end
        s:process()
        --- timer:set(s.hz)
        -- s.stopped = false;
        return
        end
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
            s:start(effect);
        else
            s:start();
        end;
        s:commit();
    end;
    scene = function(s, bg, eff)
        local i, v
        for i, v in ipairs(s._effects) do
            s:free_effect(v)
        end
        s._effects = {}
        s._need_effect = false
        s._scene_effect = eff
        -- if bg is nil, simple box sprite will be set
        s:set_bg(bg)
    end;
    textpad = 8;
    textbg = function(s, to)
        if s.direct_lock then
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
            if v.step < v.max_step - v.from_stop and v.eff ~= 'none' then
                r = true
                v.step = v.max_step - v.from_stop
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

        --s:draw_hud()
        s:commit(s:screen())
        s:leave_direct();

        RAW_TEXT = true
        s.stopped = true;
        s.uiupdate = false;
        return game._lastdisp
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
    process = function(s)
        local i, v
        local n = false
        local first
        local cbresult = false;
        -- clear bg
        sprite.copy(s.bg_spr, s:screen())
        for i, v in ipairs(s._effects) do
            local e = true;
            if v.enablefn then
                e = v:enablefn();
            end
            if e then
                s:do_effect(v);
            end
            if v.step < v.max_step - v.from_stop then
                v.step = v.step + 1
                n = true
            end
        end
        local x, y = stead.mouse_pos();
        if n then
            s:draw_hud();
            s:tooltips(x, y);
        else
            if (vn.callback) then
                local callback = vn.callback;
                vn.callback = false;
                cbresult = callback();
            end
            s:draw_hud();
            s:tooltips(x, y);
            if cbresult then
                if type(cbresult) == 'function' then
                    return cbresult();
                else
                    s:start();
                    return true;
                end
            end
        end
        return n
    end;
    draw_hud = function(s, target)
        if not target then
            if s.direct_lock then
                target = sprite.screen();
            else
                target = s:screen();
            end
        end
        if (s.txtfun) then
            local texts = s.txtfun();
            local ycur = s.yhud;
            for k, v in pairs(texts) do
                local color = v.color;
                if not color then
                    color = s.hud_color;
                end
                local textSprite = sprite.text(hudFont, v.text, color);
                local w, h = sprite.size(textSprite);
                w = w + s.extent;
                local hudSprite = sprite.blank(w, h);
                sprite.draw(target, s.xhud, ycur, w, h, hudSprite, 0, 0);
                sprite.draw(textSprite, hudSprite, 0, 0);
                sprite.draw(hudSprite, target, s.xhud, ycur);
                ycur = ycur + h;
                sprite.free(hudSprite);
                sprite.free(textSprite);
            end
        end
    end;
    tooltips = function(s, x, y)
        if not s.on then
            return;
        end
        for i, v in ipairs(s._effects) do
            local enabled = not v.enablefn or v:enablefn();
            if enabled and v.tooltipfn and s:inside_spr(v, x, y) then
                local xx, yy = s:postoxy(v);
                local vw, vh = sprite.size(v.spr[0]:val());
                local text, pos = v:tooltipfn();
                s:tooltip(text, pos, xx, yy, vw, vh);
            end
        end
    end;
    tooltip = function(s, text, pos, x, y, vw, vh)
        if not pos then
            pos = "h";
        end
        local spr = sprite.text(hudFont, text, '#000000');
        local w, h = sprite.size(spr);
        local tt_bg = sprite.box(w, h, 'white', 127);
        local target;
        if s.direct_lock then
            target = sprite.screen();
        else
            target = s:screen();
        end
        local xmax = theme.get("scr.w");
        if pos == "h" then
            local yy = y + vh / 2 - h / 2;
            if (xmax - x > w) then
                sprite.draw(tt_bg, target, x + vw + 5, yy);
                sprite.draw(spr, target, x + vw + 5, yy);
            else
                sprite.draw(tt_bg, target, x - w - 5, yy);
                sprite.draw(spr, target, x - w - 5, yy);
            end
        elseif pos == "n" then
            local yy = y - h - 5;
            local txt_offset = (vw - w) / 2;
            sprite.draw(tt_bg, target, x + txt_offset, yy);
            sprite.draw(spr, target, x + txt_offset, yy);
        elseif pos == "s" then
            local yy = y + vh + 5;
            local txt_offset = (vw - w) / 2;
            sprite.draw(tt_bg, target, x + txt_offset, yy);
            sprite.draw(spr, target, x + txt_offset, yy);
        end
        sprite.free(spr);
        sprite.free(tt_bg);
    end;
    show_btn = function(s, actfn, btnimg, btneff, ovrimg, ovreff, tooltipfn, enablefn, btnframes, ovrframes)
        if not btnframes then
            btnframes = 0;
        end
        if not ovrframes then
            ovrframes = 0;
        end
        s:show(btnimg,
            btneff,
            btnframes * s.hz, nil, nil, nil,
            nil,
            function(v)
                if s.uiupdate then
                    return;
                end
                s:hide(v);
                s:show(ovrimg,
                    ovreff,
                    ovrframes * s.hz, nil, nil, nil,
                    actfn,
                    nil,
                    function(v)
                        if s.uiupdate then
                            return;
                        end
                        s:hide(v);
                        s:show_btn(actfn, btnimg, btneff, ovrimg, ovreff, tooltipfn, enablefn, btnframes, ovrframes);
                        s:start(nil, true);
                    end,
                    tooltipfn,
                    enablefn,
                    btnframes,
                    ovrframes
                );
                s:start(nil, true);
            end,
            nil,
            tooltipfn,
            enablefn,
            btnframes,
            ovrframes
        );
    end;
}

stead.module_init(function()
    vn:init()
    vnticks = stead.ticks();
    hudFont = sprite.font('fonts/Medieval_English.ttf', 32);
end)

function vnr(v)
    if not v.nam then v.nam = 'vn'; v.disp = false; end
    return room(v)
end
