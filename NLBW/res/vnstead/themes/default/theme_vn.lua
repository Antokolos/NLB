local tr = nlb:theme_root();
theme.win.geom(248, 838, 1424, 204);
theme.inv.geom(0, 1050, 1920, 30);
theme.win.color('white', '#00FF00', 'gold');

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('inv.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('win.fnt.size', 32);

vn:turnon();
paginator:turnon();