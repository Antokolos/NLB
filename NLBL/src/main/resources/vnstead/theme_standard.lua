theme.set('scr.w', 1920);
theme.set('scr.h', 1080);
theme.win.geom(200, 200, 1220, 680);
theme.inv.geom(1520, 200, 400, 680);
theme.win.color('white', '#00FF00', 'gold')

theme.set('scr.col.bg', 'gray');
theme.set('scr.gfx.bg', 'box:1920x1080,black');

theme.set('menu.button.x', 1890);
theme.set('menu.button.y', 1050);

--theme.set('inv.mode', 'disabled'); ???
theme.set('scr.gfx.scalable', 1);

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', 'fonts/STEINEMU.ttf');
theme.set('inv.fnt.name', 'fonts/STEINEMU.ttf');
theme.set('menu.fnt.name', 'fonts/STEINEMU.ttf');
theme.set('win.fnt.size', 16);
theme.set('inv.fnt.size', 16);
theme.set('menu.fnt.size', 16);
theme.set('win.fnt.height', 1.0);
theme.set('inv.fnt.height', 1.0);
theme.set('menu.fnt.height', 1.0);

paginator:turnoff();
vn:turnoff();