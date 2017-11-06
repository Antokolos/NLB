require 'sprites'

gr = obj {
    nam = 'gr';
    system_type = true;
    --var { diff = 0, sprs = {} };
    screen = function(s)
        --log:dbg("gr:screen()");
        return sprite.screen();
    end;
    alpha = function(s, spr, alpha)
        local res = sprite.alpha(spr, alpha);
        --local idx = tostring(res);
        --local ss = ">>>gr:alpha(" .. tostring(spr) .. ", " .. tostring(alpha) .. ") = " .. tostring(res);
        --log:dbg(ss);
        --s.diff = s.diff + 1;
        --s.sprs[idx] = ss;
        return res;
    end;
    blank = function(s, w, h)
        local res = sprite.blank(w, h);
        --local idx = tostring(res);
        --local ss = ">>>gr:blank(" .. tostring(w) .. ", " .. tostring(h) .. ") = " .. tostring(res);
        --log:dbg(ss);
        --s.diff = s.diff + 1;
        --s.sprs[idx] = ss;
        return res;
    end;
    box = function(s, w, h, color, alpha)
        local res = sprite.box(w, h, color, alpha);
        --local idx = tostring(res);
        --local ss = ">>>gr:box(" .. tostring(w) .. ", " .. tostring(h) .. ", " .. tostring(color) .. ", " .. tostring(alpha) .. ") = " .. tostring(res)
        --log:dbg(ss);
        --s.diff = s.diff + 1;
        --s.sprs[idx] = ss;
        return res;
    end;
    load = function(s, file_name)
        local res = sprite.load(file_name);
        --local idx = tostring(res);
        --local ss = ">>>gr:load(" .. file_name .. ") = " .. tostring(res);
        --log:dbg(ss);
        --s.diff = s.diff + 1;
        --s.sprs[idx] = ss;
        return res;
    end;
    size = function(s, spr)
        local w, h = sprite.size(spr);
        --log:dbg("gr:size(" .. tostring(spr) .. ") = " .. tostring(w) .. ", " .. tostring(h));
        return w, h;
    end;
    free = function(s, spr)
        --log:dbg("gr:free(" .. tostring(spr) .. ");");
        --s.diff = s.diff - 1;
        --log:dbg("diff = " .. tostring(s.diff));
        --s.sprs[tostring(spr)] = nil;
        --for k, v in pairs(s.sprs) do
        --    if v then
        --        log:err(v);
        --    end
        --end
        sprite.free(spr);
    end;
    draw = function(s, src_spr, dst_spr, x, y)
        --log:dbg("gr:draw()");
        sprite.draw(src_spr, dst_spr, x, y);
    end;
    draw_ext = function(s, src_spr, fx, fy, fw, fh, dst_spr, x, y, alpha)
        --log:dbg("gr:draw_ext()");
        sprite.draw(src_spr, fx, fy, fw, fh, dst_spr, x, y, alpha);
    end;
    copy = function(s, src_spr, dst_spr, x, y)
        --log:dbg("gr:copy()");
        sprite.copy(src_spr, dst_spr, x, y);
    end;
    copy_ext = function(s, src_spr, fx, fy, fw, fh, dst_spr, x, y, alpha)
        --log:dbg("gr:copy_ext()");
        sprite.copy(src_spr, fx, fy, fw, fh, dst_spr, x, y, alpha);
    end;
    compose = function(s, src_spr, dst_spr, x, y)
        --log:dbg("gr:compose()");
        sprite.compose(src_spr, dst_spr, x, y);
    end;
    compose_ext = function(s, src_spr, fx, fy, fw, fh, dst_spr, x, y, alpha)
        --log:dbg("gr:compose_ext()");
        sprite.compose(src_spr, fx, fy, fw, fh, dst_spr, x, y, alpha);
    end;
    scale = function(s, spr, xs, ys, smooth)
        local res = sprite.scale(spr, xs, ys, smooth);
        --local idx = tostring(res);
        --local ss = "gr:scale(" .. tostring(spr) .. ", " .. tostring(xs) .. ", " .. tostring(ys) .. ", " .. tostring(smooth) .. ") = " .. tostring(res);
        --log:dbg("gr:scale(" .. tostring(spr) .. ", " .. tostring(xs) .. ", " .. tostring(ys) .. ", " .. tostring(smooth) .. ") = " .. tostring(res));
        --s.diff = s.diff + 1;
        --s.sprs[idx] = ss;
        return res;
    end;
    text = function(s, font, text, col, style)
        local res = sprite.text(font, text, col, style);
        --local idx = tostring(res);
        --local ss = "gr:text(" .. text .. ") = " .. tostring(res);
        --log:dbg(ss);
        --s.diff = s.diff + 1;
        --s.sprs[idx] = ss;
        return res;
    end;
    font = function(s, font_path, size)
        local res = sprite.font(font_path, size);
        --log:dbg("gr:font(" .. font_path .. ", " .. tostring(size) .. ") = " .. tostring(res));
        return res;
    end;
}

stead.module_init(function()
end)