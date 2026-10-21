/**
 * @(#)ScreenTheme.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2026 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 *
 * Copyright (c) 2026 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.web.player;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Оформление экрана как тема INSTEAD — порт ScreenTheme NLBNavigator: theme.ini базовой темы,
 * поверх него theme.ini темы игры, затем theme_vn.lua / theme_standard.lua обеих (вызовы
 * theme.win.geom, theme.win.color, theme.set). Координаты темы (scr.w×scr.h) пересчитываются в
 * экран 1920×1080 с полями. Пути к файлам (шрифты, картинки) — «base/…» или «game/…»: от корня
 * той темы, где задан параметр (клиент берёт их по адресу /play/theme/&lt;книга&gt;/&lt;путь&gt;).
 * <p>
 * Как в INSTEAD и NLBNavigator (--base-theme, --theme): базовая тема задаёт параметры по
 * умолчанию (звук щелчка, шрифты, цвета…), тема игры переопределяет часть из них. Базовая тема —
 * каталог __base_theme__ пакета книги, иначе стандартная тема INSTEAD (res/instead/themes/default);
 * тема игры — каталог __theme__ пакета (pack --theme), иначе тема экспорта NLB
 * (res/vnstead/themes/default). Обе встроенные темы зашиты в jar NLBW
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class ScreenTheme {
    public static final String BASE = "base";
    public static final String GAME = "game";
    private static final String BUILTIN = "/com/nlbhub/nlb/web/player/theme/";
    private static final String GAME_DIR = "__theme__";
    private static final String BASE_DIR = "__base_theme__";

    /**
     * Корень темы: каталог на диске или встроенная тема (dir == null: ресурсы NLBW theme/&lt;имя&gt;)
     */
    public static final class Root {
        private final String m_name;
        private final File m_dir;

        Root(String name, File dir) {
            m_name = name;
            m_dir = dir;
        }

        String name() {
            return m_name;
        }

        /**
         * Содержимое файла темы; null — нет такого (путь — без «..» и абсолютных частей)
         */
        InputStream open(String rel) throws IOException {
            if (!isSafe(rel)) {
                return null;
            }
            if (m_dir == null) {
                return ScreenTheme.class.getResourceAsStream(BUILTIN + m_name + "/" + rel);
            }
            File file = new File(m_dir, rel);
            return file.isFile() ? Files.newInputStream(file.toPath()) : null;
        }

        boolean exists(String rel) {
            try (InputStream in = open(rel)) {
                return in != null;
            } catch (IOException e) {
                return false;
            }
        }

        String read(String rel) {
            try (InputStream in = open(rel)) {
                return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        }
    }

    static boolean isSafe(String rel) {
        if (rel == null || rel.isEmpty() || rel.startsWith("/") || rel.indexOf('\\') >= 0 || rel.indexOf('\0') >= 0) {
            return false;
        }
        for (String part : rel.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Корни тем книги: [базовая, тема игры] — из пакета книги, иначе встроенные
     */
    public static Root[] roots(File bookDir) {
        File base = bookDir == null ? null : new File(bookDir, BASE_DIR);
        File game = bookDir == null ? null : new File(bookDir, GAME_DIR);
        return new Root[]{
                new Root(BASE, base != null && base.isDirectory() ? base : null),
                new Root(GAME, game != null && game.isDirectory() ? game : null)
        };
    }

    /**
     * Окно темы (win.* — текст, inv.* — инвентарь)
     */
    static final class Window {
        double x, y, w, h;
        int[] fg, link, alink;
        String font = "", fontBold = "", fontItalic = "";
        double fontSize;
        double lineHeight = 1.0;
        String upImage = "", downImage = "";

        Window(double x, double y, double w, double h, int[] fg, int[] link, int[] alink, double fontSize) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.fg = fg;
            this.link = link;
            this.alink = alink;
            this.fontSize = fontSize;
        }

        ObjectNode toJson() {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("x", x).put("y", y).put("w", w).put("h", h);
            node.put("fg", css(fg)).put("link", css(link)).put("alink", css(alink));
            node.put("font", font).put("fontBold", fontBold).put("fontItalic", fontItalic);
            node.put("fontSize", fontSize).put("lineHeight", lineHeight);
            node.put("upImage", upImage).put("downImage", downImage);
            return node;
        }
    }

    private static final int[] WHITE = {255, 255, 255};
    private static final int[] GREEN = {0, 255, 0};
    private static final int[] GOLD = {255, 215, 0};

    int[] m_background = {0x01, 0x13, 0x22};
    double m_scale = 1.0;
    /**
     * Где экран темы (scr.w×scr.h) на экране 1920×1080
     */
    double[] m_screenArea = {0, 0, 1920, 1080};
    /**
     * scr.gfx.bg, если это картинка (а не box:…)
     */
    String m_backgroundImage = "";
    /**
     * Фон страниц STANDARD: экспорт задаёт каждой такой странице bgimg = nlb:std_bg() — gfx/bg.jpg
     * активной темы (темы игры, иначе базовой); он заменяет scr.gfx.bg
     */
    String m_pageBackground = "";
    /**
     * scr.gfx.x/y/w/h — картинка страницы STANDARD
     */
    double[] m_pictureArea = {40, 30, 1787, 640};
    boolean m_waysBottom = false;
    String m_themeMenuButton = "";
    double m_menuButtonX = 0, m_menuButtonY = 0;
    String m_clickSound = "";
    /**
     * menu.fnt.name — шрифт меню
     */
    String m_menuFont = "";
    Window m_text;
    Window m_inventory;
    boolean m_inventoryHorizontal = true;
    String m_inventoryAlign = "center";
    String m_align = "justify";
    /**
     * Кнопка меню, которую ставит игра NLB (nlb.lua): gfx/menubtn.png и gfx/menubtn_ru.png
     * активной темы, в правом верхнем углу
     */
    String m_menuButton = "";
    String m_menuButtonRu = "";

    private ScreenTheme(boolean visualNovel) {
        // Тема по умолчанию экспорта NLB (ScreenTheme::defaults)
        if (visualNovel) {
            m_text = new Window(248, 838, 1424, 204, WHITE, GREEN, GOLD, 32);
            m_inventory = new Window(0, 1050, 1920, 30, WHITE, GREEN, GOLD, 24);
        } else {
            m_text = new Window(68, 670, 1704, 301, WHITE, GREEN, GOLD, 24);
            m_inventory = new Window(68, 982, 1704, 90, WHITE, GREEN, GOLD, 24);
        }
    }

    private static final Map<String, int[]> NAMES = new HashMap<>();

    static {
        Object[][] names = {
                {"black", 0, 0, 0}, {"white", 255, 255, 255}, {"brown", 165, 42, 42}, {"orange", 255, 165, 0},
                {"gold", 255, 215, 0}, {"red", 255, 0, 0}, {"green", 0, 128, 0}, {"blue", 0, 0, 255},
                {"yellow", 255, 255, 0}, {"gray", 128, 128, 128}, {"grey", 128, 128, 128}, {"silver", 192, 192, 192},
                {"navy", 0, 0, 128}, {"maroon", 128, 0, 0}, {"lightblue", 173, 216, 230}, {"goldenrod", 218, 165, 32},
                {"darkgoldenrod", 184, 134, 11}, {"lime", 0, 255, 0}, {"aqua", 0, 255, 255}, {"cyan", 0, 255, 255},
                {"fuchsia", 255, 0, 255}, {"magenta", 255, 0, 255}, {"purple", 128, 0, 128}, {"olive", 128, 128, 0},
                {"teal", 0, 128, 128}, {"darkgray", 169, 169, 169}, {"darkgrey", 169, 169, 169},
                {"lightgray", 211, 211, 211}, {"lightgrey", 211, 211, 211}, {"pink", 255, 192, 203},
                {"violet", 238, 130, 238}, {"darkblue", 0, 0, 139}, {"darkred", 139, 0, 0}, {"darkgreen", 0, 100, 0},
                {"lightgreen", 144, 238, 144}, {"skyblue", 135, 206, 235}, {"tan", 210, 180, 140},
                {"wheat", 245, 222, 179}, {"beige", 245, 245, 220}, {"ivory", 255, 255, 240}, {"khaki", 240, 230, 140},
                {"salmon", 250, 128, 114}, {"tomato", 255, 99, 71}, {"coral", 255, 127, 80},
                {"chocolate", 210, 105, 30}, {"sienna", 160, 82, 45}, {"crimson", 220, 20, 60}, {"indigo", 75, 0, 130}
        };
        for (Object[] n : names) {
            NAMES.put((String) n[0], new int[]{(Integer) n[1], (Integer) n[2], (Integer) n[3]});
        }
    }

    /**
     * Цвет INSTEAD: имя или #RRGGBB
     */
    static int[] color(String raw, int[] fallback) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.length() == 7 && v.charAt(0) == '#') {
            try {
                int rgb = Integer.parseInt(v.substring(1), 16);
                return new int[]{rgb >> 16, (rgb >> 8) & 0xFF, rgb & 0xFF};
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        int[] named = NAMES.get(v);
        return named == null ? fallback : named;
    }

    static String css(int[] c) {
        return String.format("#%02x%02x%02x", c[0], c[1], c[2]);
    }

    /**
     * "fonts/{sans,sans-b,sans-i,sans-bi}.ttf", 2 -> "fonts/sans-i.ttf" (0 — обычный, 1 — жирный,
     * 2 — курсив); пустой вариант — обычный
     */
    static String fontVariant(String name, int index) {
        int open = name.indexOf('{');
        int close = open < 0 ? -1 : name.indexOf('}', open);
        if (open < 0 || close < 0) {
            return name;
        }
        String[] parts = name.substring(open + 1, close).split(",", -1);
        String pick = index < parts.length ? parts[index].trim() : "";
        if (pick.isEmpty()) {
            pick = parts[0].trim();
        }
        return name.substring(0, open) + pick + name.substring(close + 1);
    }

    private static final class IniValue {
        final String value;
        final Root root;

        IniValue(String value, Root root) {
            this.value = value;
            this.root = root;
        }
    }

    private static void readIni(Root root, Map<String, IniValue> ini) {
        if (root == null) {
            return;
        }
        for (String line : root.read("theme.ini").split("\r?\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String value = line.substring(eq + 1);
            int comment = value.indexOf(';');
            if (comment >= 0) {
                value = value.substring(0, comment);
            }
            ini.put(line.substring(0, eq).trim(), new IniValue(value.trim(), root));
        }
    }

    private static String path(Root root, String rel) {
        return root.name() + "/" + rel;
    }

    private static final Pattern GEOM = Pattern.compile(
            "theme\\.(win|inv)\\.geom\\(\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*\\)");
    private static final Pattern COLORS = Pattern.compile(
            "theme\\.(win|inv)\\.color\\(\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*\\)");
    private static final Pattern SET_SIZE = Pattern.compile("theme\\.set\\('(win|inv)\\.fnt\\.size'\\s*,\\s*([0-9.]+)\\)");
    private static final Pattern SET_FONT = Pattern.compile(
            "theme\\.set\\('(win|inv)\\.fnt\\.name'\\s*,\\s*(?:tr\\s*\\.\\.\\s*)?'([^']*)'\\)");

    /**
     * Тема из корней [базовая, игры] (тема игры может быть null)
     */
    public static ScreenTheme load(Root[] roots, boolean visualNovel) {
        ScreenTheme t = new ScreenTheme(visualNovel);
        Map<String, IniValue> ini = new HashMap<>();
        for (Root root : roots) {
            readIni(root, ini);
        }
        double sw = number(ini, "scr.w", 1920);
        double sh = number(ini, "scr.h", 1080);
        if (sw <= 0 || sh <= 0) {
            sw = 1920;
            sh = 1080;
        }
        double k = Math.min(1920 / sw, 1080 / sh);
        double ox = (1920 - sw * k) / 2;
        double oy = (1080 - sh * k) / 2;
        t.m_scale = k;
        t.m_screenArea = new double[]{ox, oy, sw * k, sh * k};
        if (ini.containsKey("scr.col.bg")) {
            t.m_background = color(ini.get("scr.col.bg").value, t.m_background);
        }
        IniValue bg = ini.get("scr.gfx.bg");
        if (bg != null) {
            if (bg.value.startsWith("box:")) {
                int comma = bg.value.indexOf(',');
                if (comma >= 0) {
                    t.m_background = color(bg.value.substring(comma + 1), t.m_background);
                }
            } else if (!bg.value.isEmpty()) {
                t.m_backgroundImage = path(bg.root, bg.value);
            }
        }
        if (ini.containsKey("scr.gfx.x")) t.m_pictureArea[0] = ox + number(ini, "scr.gfx.x", 0) * k;
        if (ini.containsKey("scr.gfx.y")) t.m_pictureArea[1] = oy + number(ini, "scr.gfx.y", 0) * k;
        if (number(ini, "scr.gfx.w", -1) >= 0) t.m_pictureArea[2] = number(ini, "scr.gfx.w", 0) * k;
        if (number(ini, "scr.gfx.h", -1) >= 0) t.m_pictureArea[3] = number(ini, "scr.gfx.h", 0) * k;
        for (String prefix : new String[]{"win", "inv"}) {
            Window w = prefix.equals("win") ? t.m_text : t.m_inventory;
            if (ini.containsKey(prefix + ".x")) w.x = ox + number(ini, prefix + ".x", 0) * k;
            if (ini.containsKey(prefix + ".y")) w.y = oy + number(ini, prefix + ".y", 0) * k;
            if (ini.containsKey(prefix + ".w")) w.w = number(ini, prefix + ".w", w.w / k) * k;
            if (ini.containsKey(prefix + ".h")) w.h = number(ini, prefix + ".h", w.h / k) * k;
            if (ini.containsKey(prefix + ".col.fg")) w.fg = color(ini.get(prefix + ".col.fg").value, w.fg);
            if (ini.containsKey(prefix + ".col.link")) w.link = color(ini.get(prefix + ".col.link").value, w.link);
            if (ini.containsKey(prefix + ".col.alink")) w.alink = color(ini.get(prefix + ".col.alink").value, w.alink);
            if (ini.containsKey(prefix + ".fnt.size")) w.fontSize = number(ini, prefix + ".fnt.size", w.fontSize / k) * k;
            w.lineHeight = number(ini, prefix + ".fnt.height", w.lineHeight);
            IniValue up = ini.get(prefix + ".gfx.up");
            if (up != null) w.upImage = path(up.root, up.value);
            IniValue down = ini.get(prefix + ".gfx.down");
            if (down != null) w.downImage = path(down.root, down.value);
            IniValue font = ini.get(prefix + ".fnt.name");
            if (font != null) {
                w.font = path(font.root, fontVariant(font.value, 0));
                w.fontBold = path(font.root, fontVariant(font.value, 1));
                w.fontItalic = path(font.root, fontVariant(font.value, 2));
            }
        }
        if (ini.containsKey("inv.mode")) {
            String mode = ini.get("inv.mode").value;
            t.m_inventoryHorizontal = mode.startsWith("horizontal");
            int dash = mode.indexOf('-');
            t.m_inventoryAlign = dash < 0 ? "center" : mode.substring(dash + 1);
        }
        if (ini.containsKey("win.align")) {
            t.m_align = ini.get("win.align").value;
        }
        if (ini.containsKey("win.ways.mode")) {
            t.m_waysBottom = ini.get("win.ways.mode").value.equals("bottom");
        }
        IniValue menuButton = ini.get("menu.gfx.button");
        if (menuButton != null) t.m_themeMenuButton = path(menuButton.root, menuButton.value);
        if (ini.containsKey("menu.button.x")) t.m_menuButtonX = ox + number(ini, "menu.button.x", 0) * k;
        if (ini.containsKey("menu.button.y")) t.m_menuButtonY = oy + number(ini, "menu.button.y", 0) * k;
        IniValue menuFont = ini.get("menu.fnt.name");
        if (menuFont != null) t.m_menuFont = path(menuFont.root, fontVariant(menuFont.value, 0));
        IniValue click = ini.get("snd.click");
        if (click != null && !click.value.isEmpty()) t.m_clickSound = path(click.root, click.value);
        for (Root root : roots) {
            if (root == null) {
                continue;
            }
            if (root.exists("gfx/bg.jpg")) t.m_pageBackground = path(root, "gfx/bg.jpg");
            if (root.exists("gfx/menubtn.png")) t.m_menuButton = path(root, "gfx/menubtn.png");
            if (root.exists("gfx/menubtn_ru.png")) t.m_menuButtonRu = path(root, "gfx/menubtn_ru.png");
            String lua = root.read(visualNovel ? "theme_vn.lua" : "theme_standard.lua");
            Matcher m = GEOM.matcher(lua);
            while (m.find()) {
                Window w = m.group(1).equals("win") ? t.m_text : t.m_inventory;
                w.x = ox + Double.parseDouble(m.group(2)) * k;
                w.y = oy + Double.parseDouble(m.group(3)) * k;
                w.w = Double.parseDouble(m.group(4)) * k;
                w.h = Double.parseDouble(m.group(5)) * k;
            }
            m = COLORS.matcher(lua);
            while (m.find()) {
                Window w = m.group(1).equals("win") ? t.m_text : t.m_inventory;
                w.fg = color(m.group(2), w.fg);
                w.link = color(m.group(3), w.link);
                w.alink = color(m.group(4), w.alink);
            }
            m = SET_SIZE.matcher(lua);
            while (m.find()) {
                (m.group(1).equals("win") ? t.m_text : t.m_inventory).fontSize = Double.parseDouble(m.group(2)) * k;
            }
            m = SET_FONT.matcher(lua);
            while (m.find()) {
                Window w = m.group(1).equals("win") ? t.m_text : t.m_inventory;
                w.font = path(root, fontVariant(m.group(2), 0));
                w.fontBold = path(root, fontVariant(m.group(2), 1));
                w.fontItalic = path(root, fontVariant(m.group(2), 2));
            }
        }
        return t;
    }

    private static double number(Map<String, IniValue> ini, String key, double fallback) {
        IniValue v = ini.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public ObjectNode toJson() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("background", css(m_background));
        node.put("scale", m_scale);
        node.set("text", m_text.toJson());
        node.set("inventory", m_inventory.toJson());
        node.put("inventoryHorizontal", m_inventoryHorizontal);
        node.put("inventoryAlign", m_inventoryAlign);
        node.put("align", m_align);
        node.put("menuButton", m_menuButton);
        node.put("menuButtonRu", m_menuButtonRu);
        node.put("themeMenuButton", m_themeMenuButton);
        node.put("menuButtonX", m_menuButtonX);
        node.put("menuButtonY", m_menuButtonY);
        node.put("backgroundImage", m_backgroundImage);
        node.put("pageBackground", m_pageBackground);
        node.set("screenArea", area(m_screenArea));
        node.set("pictureArea", area(m_pictureArea));
        node.put("waysBottom", m_waysBottom);
        node.put("clickSound", m_clickSound);
        node.put("menuFont", m_menuFont);
        return node;
    }

    private static ObjectNode area(double[] r) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("x", r[0]).put("y", r[1]).put("w", r[2]).put("h", r[3]);
        return node;
    }
}
