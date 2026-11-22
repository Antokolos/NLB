/**
 * @(#)ThemeScalerTest.java
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Тема экспорта INSTEAD (NLBW/res/vnstead/themes/default) под экран книги другого размера */
public class ThemeScalerTest {
    private static final File THEME = new File("../NLBW/res/vnstead/themes/default");
    @Rule
    public TemporaryFolder m_tmp = new TemporaryFolder();
    private File m_theme;

    @Before
    public void copyTheme() throws IOException {
        m_theme = m_tmp.newFolder("default");
        Path source = THEME.toPath();
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path target = m_theme.toPath().resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Текст файла с переводами строк «\n»: на Windows git выгружает тему с CRLF, ThemeScaler их сохраняет */
    private String text(String name) throws IOException {
        return new String(Files.readAllBytes(new File(m_theme, name).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private int[] size(String name) throws IOException {
        BufferedImage image = ImageIO.read(new File(m_theme, "gfx/" + name));
        return new int[]{image.getWidth(), image.getHeight()};
    }

    @Test
    public void defaultSizeChangesNothing() throws IOException {
        byte[] ini = Files.readAllBytes(new File(m_theme, "theme.ini").toPath());
        byte[] bg = Files.readAllBytes(new File(m_theme, "gfx/bg.jpg").toPath());
        ThemeScaler.scale(m_theme, ScreenSize.DEFAULT);
        assertArrayEquals(ini, Files.readAllBytes(new File(m_theme, "theme.ini").toPath()));
        assertArrayEquals(bg, Files.readAllBytes(new File(m_theme, "gfx/bg.jpg").toPath()));
    }

    @Test
    public void qhdTheme() throws IOException {
        ThemeScaler.scale(m_theme, new ScreenSize(2560, 1440));
        String ini = text("theme.ini");
        assertTrue(ini, ini.contains("scr.w = 2560\n"));
        assertTrue(ini.contains("scr.h = 1440\n"));
        assertTrue("фон-заливка", ini.contains("scr.gfx.bg=box:2560x1440,#011322"));
        assertTrue("68 × 4/3 = 90,67", ini.contains("win.x = 91\n"));
        assertTrue("область картинки STANDARD: 1787 × 4/3", ini.contains("scr.gfx.w = 2383\n"));
        assertTrue("кнопка меню", ini.contains("menu.button.x = 2436\n"));
        assertTrue("шрифт 24 × 4/3", ini.contains("win.fnt.size = 32\n"));
        assertTrue("межстрочный множитель — как есть", ini.contains("win.fnt.height = 1.3\n"));
        assertTrue("scalable — как есть", ini.contains("scr.gfx.scalable = 5\n"));

        String vn = text("theme_vn.lua");
        assertTrue(vn, vn.contains("theme.win.geom(331, 1117, 1899, 272);"));
        assertTrue(vn.contains("theme.inv.geom(0, 1400, 2560, 40);"));
        assertTrue("32 × 4/3 = 42,67", vn.contains("theme.set('win.fnt.size', 43);"));
        String standard = text("theme_standard.lua");
        assertTrue(standard, standard.contains("theme.win.geom(91, 893, 2272, 401);"));
        assertTrue(standard.contains("theme.set('win.fnt.size', 32);"));

        assertArrayEquals(new int[]{2560, 1440}, size("bg.jpg"));
        assertArrayEquals("240×220 × 4/3", new int[]{320, 293}, size("fl.png"));
        assertArrayEquals(new int[]{109, 109}, size("menubtn.png"));
        assertTrue("прозрачность у PNG сохраняется", ImageIO.read(new File(m_theme, "gfx/fl.png")).getColorModel().hasAlpha());
    }

    @Test
    public void readyPictureForTheSizeWins() throws IOException {
        File ready = new File(m_theme, "gfx/menubtn_3840x2160.png");
        ImageIO.write(new BufferedImage(170, 170, BufferedImage.TYPE_INT_ARGB), "png", ready);
        File other = new File(m_theme, "gfx/menubtn_1280x720.png");
        ImageIO.write(new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB), "png", other);
        ThemeScaler.scale(m_theme, new ScreenSize(3840, 2160));
        assertArrayEquals("готовая картинка автора", new int[]{170, 170}, size("menubtn.png"));
        assertArrayEquals("остальные — × 2", new int[]{164, 164}, size("menubtn_ru.png"));
        assertArrayEquals(new int[]{3840, 2160}, size("bg.jpg"));
        assertFalse("готовые под размеры в игру не идут", ready.exists());
        assertFalse(other.exists());
    }

    @Test
    public void smallerScreenAndBackgroundCrop() throws IOException {
        // фон другой пропорции закрывает экран и обрезается по центру
        BufferedImage wide = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(wide, "jpg", new File(m_theme, "gfx/bg.jpg"));
        ThemeScaler.scale(m_theme, new ScreenSize(1280, 720));
        assertArrayEquals(new int[]{1280, 720}, size("bg.jpg"));
        assertArrayEquals("32×96 × 2/3", new int[]{21, 64}, size("aup.png"));
        assertEquals("theme.ini", true, text("theme.ini").contains("scr.w = 1280\n"));
    }
    @Test
    public void crlfThemeKeepsItsLineEndings() throws IOException {
        // как тема на Windows (git autocrlf): числа пересчитываются, CRLF остаётся
        File ini = new File(m_theme, "theme.ini");
        String lf = new String(Files.readAllBytes(ini.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        Files.write(ini.toPath(), lf.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
        ThemeScaler.scale(m_theme, new ScreenSize(3840, 2160));
        String raw = new String(Files.readAllBytes(ini.toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains("scr.w = 3840\r\n"));
        assertTrue(raw.contains("win.fnt.size = 48\r\n"));
        assertFalse("нет голых \\n", raw.replace("\r\n", "").contains("\n"));
    }
}
