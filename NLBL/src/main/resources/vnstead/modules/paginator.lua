require "kbd"
require "click"

if not iface.cmd_orig then
    iface.cmd_orig = iface.cmd
end
hook_keys('space', 'right ctrl', 'left ctrl')

click.bg = true

game.click = function(s, x, y, a, b, c, d)

	if here().debug then
		return
	end

	if (paginator._last and here().walk_to) or not paginator._last then
		return game:kbd(true, 'space')
	end
end

game.kbd = function(s, down, key)
	if here().debug then
		return
	end

	if key:find("ctrl") then
		vn.skip_mode = down
	end
	if down and key == 'space' then
		if vn:finish() then
			return
		end
		if paginator._last then
			if here().walk_to then
				return walk(here().walk_to)
			end
			return
		end
		paginator.process = true
		RAW_TEXT = true
		return game._realdisp
	end
end

local text_page = function(txt)
	local s, e
	local pg = paginator
	local ss = pg._page 
	local res = ''
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
			error("Bug in expression:"..s);
		end
		f();
		return ''
	end)
	res = res:gsub("[ \t\n]+$", ""):gsub("^[ \t\n]+", "");

    local loc = here();
    if loc.nextsnd ~= nil and res ~= '' then
        loc.nextsnd(loc);
    end

	return res..'\n'
end

paginator = obj {
	nam = 'paginator';
	system_type = true;
	w = 0;
	h = 0;
	_page = 1;
	delim = '\n\n';
}

iface.cmd = function(s, cmd, ...)
	local r,v = iface.cmd_orig(s, cmd, ...)
	if here().debug then
		return r,v
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
			timer:get() == 0 and r:find("^[ \t\n]*$") and not paginator._last then
			paginator.process = true
			r = game._realdisp
		end

		if paginator.process or not RAW_TEXT then
			while true do
				r = text_page(r)
				if timer:get() ~= 0 or not r:find("^[ \t\n]*$") or paginator._last then
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

stead.module_init(function()
end)

instead.get_title = function(s)
-- no title
end
stead.phrase_prefix = ''
