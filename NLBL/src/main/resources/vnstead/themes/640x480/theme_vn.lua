if not vn.on then
local tr = nlb:theme_root();
theme.set('scr.w', 640);
theme.set('scr.h', 480);
theme.win.geom(0, 380, 640, 100);
theme.inv.geom(0, 450, 640, 30);
theme.win.color('white', '#00FF00', 'gold')

--theme.set('scr.col.bg', '#011322');
--theme.set('scr.gfx.bg', 'box:1920x1080,#011322');

theme.set('win.gfx.up', tr .. 'gfx/aup.png');
theme.set('win.gfx.down', tr .. 'gfx/adown.png');
theme.set('inv.gfx.up', tr .. 'gfx/aup.png');
theme.set('inv.gfx.down', tr .. 'gfx/adown.png');

--theme.menu.gfx.button('gfx/menubtn.png', 1827, 0);

theme.set('inv.mode', 'horizontal-left');
--theme.set('scr.gfx.scalable', 5);

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('inv.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('menu.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('win.fnt.size', 16);
theme.set('inv.fnt.size', 12);
theme.set('menu.fnt.size', 16);
theme.set('win.fnt.height', 1.3);
theme.set('inv.fnt.height', 1.3);
theme.set('menu.fnt.height', 1.3);
vn:turnon();
paginator:turnon();
end