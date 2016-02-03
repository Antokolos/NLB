theme.set('scr.w', 1920);
theme.set('scr.h', 1080);
theme.win.geom(200, 45, 1220, 1025);
theme.inv.geom(1520, 45, 400, 1025);
theme.win.color('white', '#00FF00', 'gold')

theme.set('scr.col.bg', 'gray');
theme.set('scr.gfx.bg', 'box:1920x1080,black');

theme.set('win.gfx.up', 'gfx/aup.png');
theme.set('win.gfx.down', 'gfx/adown.png');
theme.set('inv.gfx.up', 'gfx/aup.png');
theme.set('inv.gfx.down', 'gfx/adown.png');

theme.menu.gfx.button('gfx/menubtn.png', 1827, 0);

--theme.set('inv.mode', 'disabled'); ???
theme.set('scr.gfx.scalable', 1);

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', 'fonts/sans.ttf');
theme.set('inv.fnt.name', 'fonts/sans.ttf');
theme.set('menu.fnt.name', 'fonts/STEINEMU.ttf');
theme.set('win.fnt.size', 24);
theme.set('inv.fnt.size', 24);
theme.set('menu.fnt.size', 32);
theme.set('win.fnt.height', 1.3);
theme.set('inv.fnt.height', 1.3);
theme.set('menu.fnt.height', 1.3);

paginator:turnoff();
vn:turnoff();