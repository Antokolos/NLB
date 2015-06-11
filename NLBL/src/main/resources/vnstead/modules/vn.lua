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
	s.win_x, s.win_y, s.win_w, s.win_h, s.up_x, s.down_x = theme.get 'win.x',
		theme.get 'win.y',
		theme.get 'win.w',
		theme.get 'win.h',
		theme.get 'up.x',
		theme.get 'down.x';
	    s._win_get = true
end
game.timer = function(s)
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
			timer:stop()
			theme.gfx.bg(vn.offscreen)
		end
		RAW_TEXT = true
		return game._lastdisp
	end
	if not vn:process() then
		vn.tostop = true
	end
end

if not game.old_fading then
	game.old_fading = game.fading
end

game.fading = function(s)
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
	_wf = 0;
	_fln = nil;
    _frn = nil;
    hz = 50;
	var { speed = 500, fading = 8, bgalpha = 127 };
	var { win_x = 0, win_y = 0, win_w = 0, win_h = 0, up_x = 0, down_x = 0; };
	screen = function(s)
		if s._need_effect then
			return sprite.screen()
		else
			return s.offscreen
		end
	end;
	ini = function(s, load)
		if not load then
			return
		end
		local i,v, n
		for i,v in ipairs(s._effects) do
			s:load_effect(v)
			if v.step < v.max_step then
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
	end;
	load_effect = function(s, v)
		v.spr = sprite.load(v.pic)
		if not v.spr then
			error ("Can not load sprite:"..tostring(v.pic))
		end
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

		local i,k = s:lookup(nam)
		if not i then return end
		sprite.free(i.spr);
		stead.table.remove(s._effects, k)
		return
	end;

	show = function(s, ...)
		return s:effect(...)
	end;

	lookup = function(s, n)
		if not n then return end
		local i,k
		for i,k in ipairs(s._effects) do
			if k.nam == n then
				return k, i
			end
		end
	end;

	effect = function(s, image, eff, speed)
		local v

		if type(image) == 'string' then
			v = { pic = image, nam = image };
			image = v
		end

		local picture = image.pic
		local name = image.nam
		local v = { pic = picture, nam = name, eff = t, step = 0; }

		if eff == 'hide' then
			s:hide(v)
			return
		end

		local i,k
		local old_pos

		local oe = s:lookup(v.nam)

		if oe then
			if oe.pic ~= v.pic then -- new pic
				sprite.free(oe.spr);
				oe.pic = v.pic
				s:load_effect(oe)
			end
			old_pos = oe.pos
			v = oe
		else
			s:load_effect(v)
		end
		v.step = 0
		v.max_step = math.floor((speed or s.speed) / s.hz)
		v.w, v.h = sprite.size(v.spr)
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

		if v.eff ~= 'none' then
			s._need_effect = true
		end

		if not oe then
			stead.table.insert(s._effects, v)
		end

		return v
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
			error ("Can not load ng sprite:"..tostring(picture))
		end
		s._bg = picture
	end;
	fade = function(s, v)
		local x, y
		x, y = s:postoxy(v)
		if v.eff == 'fadein' then
			spr = sprite.alpha(v.spr, math.floor(255 * v.step / v.max_step))
		else
			spr = sprite.alpha(v.spr, math.floor(255 * (v.max_step - v.step) / v.max_step))
		end
		sprite.draw(spr, s:screen(), x, y);
		sprite.free(spr)
	end;

	none = function(s, v)
		local x, y
		x, y = s:postoxy(v)
		sprite.draw(v.spr, s:screen(), x, y);
	end;
	postoxy = function(s, v)
		local x = 0
		local  y = 0
		if v.pos:find 'left' then
			x = 0
		elseif v.pos:find 'right' then
			x = s.scr_w - v.w
		else
			x = math.floor((s.scr_w - v.w) / 2);
		end
		if v.pos:find 'top' then
			y = 0
		elseif v.pos:find 'bottom' then
			y = s.scr_h - v.h
		elseif v.pos:find 'middle' then
			y = math.floor((s.scr_h - v.h) / 2)
		else
			y = s.scr_h - v.h
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
	moveout = function(s, v)
		local x_start, x_end
		local y_start, y_end
		local x, y
		x_start, y = s:postoxy(v)
		if v.from == 'left' or v.from == 'right' then
			x_start, y = s:postoxy(v)
		elseif v.from == 'top' or v.from == 'bottom' then
			x, y_start = s:postoxy(v)
		end
		if v.from == 'left' then
			x = math.floor(x_start - v.step * ( x_start + v.w) / v.max_step)
		elseif v.from == 'right' then
			x = math.floor(x_start + v.step * ( s.scr_w - x_start) / v.max_step)
		elseif v.from == 'top' then
			y_end = - v.h
			y = math.floor(y_start - v.step * (y_start - y_end) / v.max_step)
		elseif v.from == 'bottom' then
			y_end = s.scr_h
			y = math.floor(y_start + v.step * (s.scr_h - y_start + v.h) / v.max_step)
		end
		sprite.draw(v.spr, s:screen(), x, y);
	end;

	zoom = function(s, v)
		local x
		local y
		local spr
		local scale
		if v.eff == 'zoomin' then
			scale = v.step / v.max_step;
		else
			scale = (v.max_step - v.step) / v.max_step
		end

		if scale == 0 then
			return
		end

		if scale ~= 1.0 then
			spr = sprite.scale(v.spr, scale, scale, false);
		else
			spr = v.spr
		end

		local w, h = sprite.size(spr)

		x, y = s:postoxy(v)

		if v.pos:find 'left' then
			x = x - math.floor((v.w - w))
		elseif v.pos:find 'right' then
			x = x + math.floor((v.w - w))
		else
			x = x + math.floor((v.w - w) / 2)
		end
		if v.pos:find 'top' then
			y = y - math.floor((v.h - h))
		elseif v.pos:find 'bottom' then
			y = y + math.floor((v.h - h))
		elseif v.pos:find 'middle' then
			y = y + math.floor((v.h - h) / 2)
		else
			y = y + math.floor((v.h - h) / 2)
		end
		sprite.draw(spr, s:screen(), x, y)
		if v.spr ~= spr then
			sprite.free(spr)
		end
	end;

	movein = function(s, v)
		local x_start, y_start
		local x_end, y_end
		local x, y
		if v.from == 'left' or v.from == 'right' then
			x_end, y = s:postoxy(v)
		elseif v.from == 'top' or v.from == 'bottom' then
			x, y_end = s:postoxy(v)
		end

		if v.from == 'left' then
			x_start = - v.w
			x = math.floor(x_start + v.step * (x_end + v.w) / v.max_step)
		elseif v.from == 'right' then
			x_start = s.scr_w
			x = math.floor(x_start - v.step * (s.scr_w - x_end) / v.max_step)
		elseif v.from == 'top' then
			y_start = - v.h
			y = math.floor(y_start + v.step * (y_end - y_start) / v.max_step)
		elseif v.from == 'bottom' then
			y_start = s.scr_h
			y = math.floor(y_start - v.step * (s.scr_h - y_end) / v.max_step)
		end
		sprite.draw(v.spr, s:screen(), x, y);
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
		else
			return s:none(v)
		end
	end;
	start = function(s, effect)
		-- do first step
		if not s.bg_spr then
			error "No scene background specified!"
		end
		if s._need_effect then -- doing some effect(s) 
		-- enter direct mode
			theme.set('scr.gfx.mode', 'direct');
			s:process()
			timer:set(s.hz)
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
			s:textbg(s.offscreen)
			theme.gfx.bg(s.offscreen)
			return
		end
		timer:set(s.hz)
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
		s.win_x, s.win_y, s.win_w, s.win_h = x + s._wf, y, w - 2*s._wf, h;
		theme.win.geom(s.win_x, s.win_y, s.win_w, s.win_h);
		if effect then
		    s:start(effect);
		else
		    s:start();
		end;
		s:commit();
	end;
	scene = function(s, bg, eff)
		local i,v
		for i,v in ipairs(s._effects) do
			sprite.free(v.spr)
		end
		s._effects = {}
		s._need_effect = false
		s._scene_effect = eff
		-- if bg is nil, simple box sprite will be set
		s:set_bg(bg)
	end;
	textpad = 8;
	textbg = function(s, to)
		local pad = vn.textpad;
		local wf = vn._wf;
		local fln = vn._fln;
		local frn = vn._frn;
		local w, h = theme.get 'win.w', theme.get 'win.h'
		local x, y = theme.get 'win.x', theme.get 'win.y'
		local sb = sprite.box(w + pad*2, h + pad * 2, 'black', s.bgalpha)
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
		sprite.free(sb)
	end;
	commit = function(s, from)
		sprite.copy(from, s.offscreen);
		s:textbg(s.offscreen)
		theme.gfx.bg(s.offscreen);
	end;
	finish = function(s)
		local k,v
		local r
		for k,v in ipairs(s._effects) do
			if v.step < v.max_step and v.eff ~= 'none' then
				r = true
				v.step  = v.max_step
			end
		end
		return r
	end;
	stop  = function(s)
		timer:stop()

		local e2 = {}
		local i,v

		for i,v in ipairs(s._effects) do
			if not v.eff:find("out") then
				stead.table.insert(e2, v)
			else
				sprite.free(v.spr)
			end
		end
		s._effects = e2

		s:commit(sprite.screen())
		theme.reset('scr.gfx.mode')
		RAW_TEXT = true
		return game._lastdisp
	end;
	process = function(s)
		local i,v
		local n = false
		local first
		-- clear bg
		sprite.copy(s.bg_spr, s:screen())
		for i,v in ipairs(s._effects) do
			s:do_effect(v)
			if v.step < v.max_step then
				v.step = v.step + 1
				n = true
			end
		end
		return n
	end;
}

stead.module_init(function()
	vn:init()
end)

function vnr(v)
	if not v.nam then v.nam = 'vn'; v.disp = false; end
	return room(v)
end
