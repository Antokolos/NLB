-- paginator module
require "kbd"
require "click"
require "theme"
require "modules/vn"
require 'modules/log'

click.bg = true

local clickInInvArea = function(x, y)
    local invx, invy, invw, invh =
    tonumber(theme.get("inv.x")),
    tonumber(theme.get("inv.y")),
    tonumber(theme.get("inv.w")),
    tonumber(theme.get("inv.h"));
    if ((x >= invx) and (x <= invx + invw) and (y >= invy) and (y <= invy + invh)) then
        -- Click in inventory area
        return true;
    end
    return false;
end

local paginatorKbd = function(down, key)
    if here().debug then
        return
    end

    if key:find("ctrl") then
        vn.skip_mode = down
    end

    if vn:actonkey(down, key) then
        if here().autos then
            here():autos();
        end
        return
    end

    if down and key == 'space' then
        if vn:finish() then
            return
        end
        if paginator.onproceed then
            paginator.onproceed();
        end
        if paginator._last then
            if here().walk_to then
                vn:request_full_clear();
                return walk(here().walk_to)
            end
            return
        end
        paginator.process = true
        RAW_TEXT = true
        return game._realdisp
    end
end

local paginatorClick = function(x, y, a, b, c, d)
    if here().debug then
        return
    end

    if clickInInvArea(x, y) then
        -- Click in inventory area
        return
    end

    local v, g = vn:test_click(x, y);
    if v then
        -- Click inside some gobj in vn
        vn:click_sprite(v, g);
        if here().autos then
            here():autos();
        end
        return true;
    end

    if (paginator._last and here().walk_to) or not paginator._last then
        return paginatorKbd(true, 'space')
    else
        vn:finish();
    end
end

local text_page = function(txt)
    local s, e
    local pg = paginator
    local ss = pg._page
    local res = ''
    if not txt then
        return nil;
    end
    txt = txt:gsub("[ \t\n]+$", ""):gsub("^[ \t\n]+", "");

    s, e = txt:find(pg.delim, ss)
    if s then
        res = txt:sub(ss, s)
        pg._page = e + 1
    else
        pg._last = true
        res = txt:sub(ss)
    end
    res = res:gsub("%$[^%$]+%$", function(s)
        s = s:gsub("^%$", ""):gsub("%$$", "");
        local f = stead.eval(s)
        if not f then
            print("Error in expression: ", s)
            error("Bug in expression:" .. s);
        end
        f();
        return ''
    end)
    res = res:gsub("[ \t\n]+$", ""):gsub("^[ \t\n]+", "");

    local loc = here();
    if loc.nextsnd and res ~= '' then
        loc.nextsnd(loc);
    end

    return res .. '\n'
end

paginator = obj {
    nam = 'paginator';
    system_type = true;
    w = 0;
    h = 0;
    _page = 1;
    var { process = false; on = true; onproceed = false; },
    delim = '\n\n';
    turnon = function(s)
        s.on = true;
    end,
    turnoff = function(s)
        s.on = false;
    end,
    set_onproceed = function(s, callback)
        s.onproceed = callback;
    end;
}

local paginatorIfaceCmd = function(f, s, cmd, ...)
    local r, v = f(s, cmd, ...)
    if here().debug or not paginator.on then
        return r, v
    end
    if type(r) == 'string' and (stead.state or RAW_TEXT) then
        if not RAW_TEXT then -- and player_moved() then
        if player_moved() then
            paginator._page = 1
            paginator._last = false
        end
        game._realdisp = game._lastdisp
        end

        if RAW_TEXT and not paginator.process and
                --- timer:get() == 0 and r:find("^[ \t\n]*$") and not paginator._last then
                r:find("^[ \t\n]*$") and not paginator._last then
            paginator.process = true
            r = game._realdisp
        end

        if paginator.process or not RAW_TEXT then
            while true do
                r = text_page(r)
                if not r then
                    paginator._last = true;
                    game._lastdisp = "\n";
                    return "\n", v;
                end
                --- if timer:get() ~= 0 or not r:find("^[ \t\n]*$") or paginator._last then
                if not r:find("^[ \t\n]*$") or paginator._last then
                    break
                end
                r = game._realdisp
            end
        end
        paginator.process = false
        game._lastdisp = r
    end
    return r, v
end

local paginatorIfaceFmt = function(f, self, cmd, st, moved, r, av, objs, pv)
    if paginator.on then
        -- st -- changed state (main win), move -- loc changed
        -- maybe we should print action reactions and life texts somewhere, but we shouldn't print it to the main window
        local l, vv
        if st then
            if isForcedsc(stead.here()) or NEED_SCENE then
                l = stead.here():scene(); -- статическая часть сцены
            end
        end
        vv = stead.fmt(stead.cat(stead.par(stead.scene_delim, l, nil, nil, objs, nil), '^'));
        return vv
    else
        return f(self, cmd, st, moved, r, av, objs, pv);
    end
end

local paginatorGetTitle = function(f, s, cmd, ...)
    if not paginator.on then
        return f(s, cmd, ...)
    end
    -- else no title
end
stead.phrase_prefix = ''

stead.module_init(function()
    hook_keys('space', 'right ctrl', 'left ctrl');
    game.click = function(s, x, y, a, b, c, d)
        local result;
        if paginator.on then
            result = paginatorClick(x, y, a, b, c, d);
        else
            return vn:click(x, y, a, b, c, d);
        end
        return result;
    end
    game.kbd = function(s, down, key)
        local result;
        if paginator.on then
            result = paginatorKbd(down, key);
        else
            return;
        end
        return result;
    end
    iface.cmd = stead.hook(iface.cmd, function(f, s, cmd, ...)
        vn:need_renew();
        return paginatorIfaceCmd(f, s, cmd, ...);
    end)

    iface.fmt = stead.hook(iface.fmt, function(f, self, cmd, st, moved, r, av, objs, pv)
        return paginatorIfaceFmt(f, self, cmd, st, moved, r, av, objs, pv);
    end)

    instead.get_title = stead.hook(instead.get_title, function(f, s, cmd, ...)
        return paginatorGetTitle(f, s, cmd, ...);
    end)
end)