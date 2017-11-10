local tr = nlb:theme_root();
theme.win.geom(40, 328, 560, 114);
theme.inv.geom(0, 450, 640, 30);
theme.win.color('white', '#00FF00', 'gold');

theme.set('inv.mode', 'horizontal-left');

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('inv.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('win.fnt.size', 16);

vn:turnon();
paginator:turnon();