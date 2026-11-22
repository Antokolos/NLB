/**
 * @(#)ThemeScaler.java
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
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.ScreenSize;
import com.nlbhub.nlb.util.ImageDownscaler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Тема экспорта INSTEAD (res/vnstead/themes/default, нарисована под экран 1920×1080) — под экран
 * книги другого размера. Все размеры 16:9, поэтому всё пересчитывается одним множителем
 * k = ширина / 1920:
 * <ul>
 * <li>theme.ini — scr.w/h, «box:WxH» фона, область картинки scr.gfx, окна win и inv, кнопка
 * меню, размеры шрифтов *.fnt.size (межстрочный множитель fnt.height и прочее — как есть);</li>
 * <li>theme_vn.lua и theme_standard.lua — theme.win.geom / theme.inv.geom и
 * theme.set('….fnt.size', N);</li>
 * <li>картинки gfx/: готовый файл {@code <имя>_<W>x<H>.<расширение>} — вместо автоматического;
 * иначе bg.jpg (фон страниц STANDARD) — закрыть экран целиком и обрезать по центру, остальные
 * (боковины окна текста, кнопка меню, стрелки, «Конец») — × k с сохранением пропорций.
 * Готовые файлы под размеры в игру не копируются.</li>
 * </ul>
 * Пиксели модулей (vn.lua, nlb.lua) пересчитывает сама игра множителем nlb._screen_k из
 * main.lua (nlb:px). Ручную тему 640x480 и темы книг со своим theme.ini не трогаем.
 */
public final class ThemeScaler {
    /** Фон страниц STANDARD: закрывает экран, обрезается по центру */
    private static final String BACKGROUND = "bg.jpg";
    private static final int JPEG_QUALITY = 95;
    private static final Pattern INI_NUMBER = Pattern.compile(
            "^(\\s*(?:scr\\.[wh]|scr\\.gfx\\.[xywh]|win\\.[xywh]|inv\\.[xywh]|menu\\.button\\.[xy]|[a-z]+\\.fnt\\.size)\\s*=\\s*)(-?\\d+)(.*)$",
            Pattern.DOTALL  // строка с \r (CRLF) тоже
    );
    private static final Pattern INI_BOX = Pattern.compile("(box:)(\\d+)x(\\d+)");
    private static final Pattern LUA_GEOM = Pattern.compile("(theme\\.(?:win|inv)\\.geom\\()\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*(\\))");
    private static final Pattern LUA_FONT_SIZE = Pattern.compile("(theme\\.set\\('[a-z]+\\.fnt\\.size',\\s*)(\\d+)(\\))");
    private static final Pattern SIZED_NAME = Pattern.compile("^(.+)_(\\d+)x(\\d+)(\\.[^.]+)$");

    private ThemeScaler() {
    }

    /** Подогнать тему themeDir под экран size; при 1920×1080 ничего не делает */
    public static void scale(File themeDir, ScreenSize size) throws IOException {
        if (ScreenSize.DEFAULT.equals(size) || !themeDir.isDirectory()) {
            return;
        }
        final double k = size.scaleFrom(ScreenSize.DEFAULT);
        final File ini = new File(themeDir, "theme.ini");
        if (ini.isFile()) {
            write(ini, scaleIni(read(ini), k, size));
        }
        for (String lua : new String[]{"theme_vn.lua", "theme_standard.lua"}) {
            final File file = new File(themeDir, lua);
            if (file.isFile()) {
                write(file, scaleLua(read(file), k));
            }
        }
        scaleImages(new File(themeDir, "gfx"), k, size);
    }

    static int px(int value, double k) {
        return (int) Math.round(value * k);
    }

    static String scaleIni(String text, double k, ScreenSize size) {
        final StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String result = line;
            final Matcher number = INI_NUMBER.matcher(line);
            if (number.matches()) {
                result = number.group(1) + px(Integer.parseInt(number.group(2)), k) + number.group(3);
            }
            final Matcher box = INI_BOX.matcher(result);
            if (box.find()) {
                result = box.replaceFirst("$1" + size.getWidth() + "x" + size.getHeight());
            }
            out.append(result).append('\n');
        }
        out.setLength(out.length() - 1);
        return out.toString();
    }

    static String scaleLua(String text, double k) {
        Matcher geom = LUA_GEOM.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (geom.find()) {
            geom.appendReplacement(out, Matcher.quoteReplacement(geom.group(1)
                    + px(Integer.parseInt(geom.group(2)), k) + ", " + px(Integer.parseInt(geom.group(3)), k) + ", "
                    + px(Integer.parseInt(geom.group(4)), k) + ", " + px(Integer.parseInt(geom.group(5)), k) + geom.group(6)));
        }
        geom.appendTail(out);
        final Matcher font = LUA_FONT_SIZE.matcher(out.toString());
        final StringBuilder result = new StringBuilder();
        while (font.find()) {
            font.appendReplacement(result, Matcher.quoteReplacement(font.group(1) + px(Integer.parseInt(font.group(2)), k) + font.group(3)));
        }
        font.appendTail(result);
        return result.toString();
    }

    private static void scaleImages(File gfx, double k, ScreenSize size) throws IOException {
        final File[] files = gfx.listFiles();
        if (files == null) {
            return;
        }
        final String suffix = "_" + size.getWidth() + "x" + size.getHeight();
        final List<File> sized = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && SIZED_NAME.matcher(file.getName()).matches()) {
                sized.add(file);
            }
        }
        for (File file : files) {
            if (!file.isFile() || sized.contains(file)) {
                continue;
            }
            final String name = file.getName();
            final int dot = name.lastIndexOf('.');
            if (dot < 0) {
                continue;
            }
            final File ready = new File(gfx, name.substring(0, dot) + suffix + name.substring(dot));
            if (ready.isFile()) {
                // готовая картинка автора под этот размер
                Files.copy(ready.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else if (BACKGROUND.equals(name.toLowerCase(Locale.ROOT))) {
                ImageDownscaler.cover(file, size.getWidth(), size.getHeight(), JPEG_QUALITY);
            } else {
                ImageDownscaler.rescale(file, k, JPEG_QUALITY);
            }
        }
        // готовые картинки под размеры в игру не нужны
        for (File file : sized) {
            Files.deleteIfExists(file.toPath());
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
