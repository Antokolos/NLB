if vn.on then
local tr = nlb:theme_root();
theme.win.geom(68, 670, 1704, 301);
theme.inv.geom(68, 982, 1704, 90);
theme.win.color('black', 'brown', 'orange');

theme.set('scr.gfx.bg', tr .. 'gfx/bg.jpg');

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', tr .. 'fonts/{sans,sans-b,sans-i,sans-bi}.ttf');
theme.set('inv.fnt.name', tr .. 'fonts/{sans,sans-b,sans-i,sans-bi}.ttf');
theme.set('win.fnt.size', 24);

paginator:turnoff();
vn:turnoff();
end