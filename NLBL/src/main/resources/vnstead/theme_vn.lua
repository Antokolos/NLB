if not vn.on then
theme.set('scr.w', 1920);
theme.set('scr.h', 1080);
theme.win.geom(248, 888, 1424, 184);
theme.inv.geom(0, 1050, 1920, 30);
theme.win.color('white', '#00FF00', 'gold')

--theme.set('scr.col.bg', '#011322');
--theme.set('scr.gfx.bg', 'box:1920x1080,#011322');

theme.set('win.gfx.up', 'gfx/aup.png');
theme.set('win.gfx.down', 'gfx/adown.png');
theme.set('inv.gfx.up', 'gfx/aup.png');
theme.set('inv.gfx.down', 'gfx/adown.png');

--theme.menu.gfx.button('gfx/menubtn.png', 1827, 0);

theme.set('inv.mode', 'horizontal-left');
--theme.set('scr.gfx.scalable', 5);

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', 'fonts/STEINEMU.ttf');
theme.set('inv.fnt.name', 'fonts/STEINEMU.ttf');
theme.set('menu.fnt.name', 'fonts/STEINEMU.ttf');
theme.set('win.fnt.size', 32);
theme.set('inv.fnt.size', 24);
theme.set('menu.fnt.size', 32);
theme.set('win.fnt.height', 1.3);
theme.set('inv.fnt.height', 1.3);
theme.set('menu.fnt.height', 1.3);
vn:turnon();
paginator:turnon();
end