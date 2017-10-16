if vn.on then
local tr = nlb:theme_root();
theme.set('scr.w', 1920);
theme.set('scr.h', 1080);
theme.win.geom(68, 670, 1704, 301);
theme.inv.geom(68, 982, 1704, 90);

theme.win.color('black', 'brown', 'orange')
theme.inv.color('black', 'brown', 'orange')

--theme.set('scr.col.bg', '#011322');
theme.set('scr.gfx.bg', tr .. 'gfx/bg.jpg');

theme.set('win.gfx.up', tr .. 'gfx/aup.png');
theme.set('win.gfx.down', tr .. 'gfx/adown.png');
theme.set('inv.gfx.up', tr .. 'gfx/aup.png');
theme.set('inv.gfx.down', tr .. 'gfx/adown.png');

--theme.menu.gfx.button('gfx/menubtn.png', 1827, 0);

--theme.set('inv.mode', 'horizontal-center');
--theme.set('scr.gfx.scalable', 5); -- was 1

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', tr .. 'fonts/{sans,sans-b,sans-i,sans-bi}.ttf');
theme.set('inv.fnt.name', tr .. 'fonts/{sans,sans-b,sans-i,sans-bi}.ttf');
theme.set('menu.fnt.name', tr .. 'fonts/STEINEMU.ttf');
theme.set('win.fnt.size', 24);
theme.set('inv.fnt.size', 24);
theme.set('menu.fnt.size', 32);
theme.set('win.fnt.height', 1.3);
theme.set('inv.fnt.height', 1.3);
theme.set('menu.fnt.height', 1.3);

paginator:turnoff();
vn:turnoff();
end