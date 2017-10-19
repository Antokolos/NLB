if vn.on then
local tr = nlb:theme_root();
theme.win.geom(20, 20, 450, 440);
theme.inv.geom(500, 20, 120, 440);
theme.win.color('white', '#00FF00', 'gold');

theme.set('inv.mode', 'vertical');

-- http://www.fonts2u.com/steinem-unicode.font
theme.set('win.fnt.name', tr .. 'fonts/{sans,sans-b,sans-i,sans-bi}.ttf');
theme.set('inv.fnt.name', tr .. 'fonts/{sans,sans-b,sans-i,sans-bi}.ttf');
theme.set('win.fnt.size', 12);

paginator:turnoff();
vn:turnoff();
end