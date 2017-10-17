if not vn.on then
local tr = nlb:theme_root();
theme.win.geom(0, 380, 640, 100);
theme.inv.geom(0, 450, 640, 30);

theme.set('inv.mode', 'horizontal-left');

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('inv.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('win.fnt.size', 16);

vn:turnon();
paginator:turnon();
end