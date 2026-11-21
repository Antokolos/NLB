/**
 * @(#)ScreenLayoutTest.java
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
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.MediaFile;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Theme;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Раскладка экрана VN для предпросмотра. Ожидаемые числа — по формуле GameScreen::objectRect
 * NLBNavigator (и objectRect в screen.js): left/top — arm, middle — по центру, right/bottom — к
 * краю; arm — ExportManager.getRelativeCoords
 */
public class ScreenLayoutTest {
    @Rule
    public TemporaryFolder m_tmp = new TemporaryFolder();
    private File m_images;
    private NonLinearBookImpl m_book;
    private PageImpl m_page;
    private final List<MediaFile> m_library = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        m_images = m_tmp.newFolder("images");
        m_book = new NonLinearBookImpl();
        m_page = new PageImpl(m_book);
        // страница на холсте редактора: 200×100 с левым верхним углом в (100, 100)
        m_page.getCoords().setLeft(100);
        m_page.getCoords().setTop(100);
        m_page.getCoords().setWidth(200);
        m_page.getCoords().setHeight(100);
        m_book.addPage(m_page);
    }

    private void png(String name, int w, int h) throws IOException {
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", new File(m_images, name));
    }

    /** Графический объект на странице; на холсте — в (150, 150): относительно страницы (50, 50) */
    private ObjImpl sprite(String image, Obj.CoordsOrigin origin) {
        ObjImpl obj = new ObjImpl(m_book);
        obj.setImageFileName(image);
        obj.setGraphical(true);
        obj.setImageInScene(true);
        obj.setCoordsOrigin(origin);
        obj.getCoords().setLeft(150);
        obj.getCoords().setTop(150);
        obj.setContainerId(m_page.getId());
        m_page.addContainedObjId(obj.getId());
        m_book.addObj(obj);
        return obj;
    }

    private ScreenLayout.Screen layout() {
        return ScreenLayout.layout(ScreenLayout.Input.of(m_page), m_images, m_library);
    }

    @Test
    public void allNineOrigins() throws IOException {
        png("s.png", 100, 50);
        // arm: left = floor(50 * 1920 / 200) = 480, top = floor(50 * 1080 / 100) = 540
        int[][] expected = {
                {480, 540},   // LeftTop
                {910, 540},   // MiddleTop: (1920 - 100) / 2
                {1820, 0},    // RightTop: 1920 - 100; y = arm 0 (как в экспорте)
                {480, 515},   // LeftMiddle: (1080 - 50) / 2
                {910, 515},   // MiddleMiddle
                {1820, 515},  // RightMiddle
                {480, 1030},  // LeftBottom: 1080 - 50
                {910, 1030},  // MiddleBottom
                {1820, 1030}, // RightBottom
        };
        for (Obj.CoordsOrigin origin : Obj.CoordsOrigin.values()) {
            sprite("s.png", origin);
        }
        List<ScreenLayout.Element> sprites = layout().getSprites();
        assertEquals(9, sprites.size());
        for (int i = 0; i < 9; i++) {
            String what = Obj.CoordsOrigin.values()[i].name();
            assertEquals(what, expected[i][0], sprites.get(i).getX());
            assertEquals(what, expected[i][1], sprites.get(i).getY());
            assertEquals(100, sprites.get(i).getWidth());
            assertEquals(50, sprites.get(i).getHeight());
            assertFalse(what, sprites.get(i).isCut());
        }
    }

    @Test
    public void coolAirDoctorIsCutAtTheTop() throws IOException {
        // CoolAir-Images: страница 215×138 в (96, 204), доктор в (113, 257), левый нижний угол,
        // спрайт 1086×1448: x = floor(17 * 1920 / 215) = 151, y = 1080 - 1448 = -368
        m_page.getCoords().setLeft(96);
        m_page.getCoords().setTop(204);
        m_page.getCoords().setWidth(215);
        m_page.getCoords().setHeight(138);
        png("drmunoz_neutral.png", 1086, 1448);
        ObjImpl doctor = sprite("drmunoz_neutral.png", Obj.CoordsOrigin.LeftBottom);
        doctor.getCoords().setLeft(113);
        doctor.getCoords().setTop(257);
        doctor.setOffset("0,0");
        ScreenLayout.Element e = layout().getSprites().get(0);
        // offset 0,0 задан: arm = 0, x = 0
        assertEquals(0, e.getX());
        assertEquals(-368, e.getY());
        doctor.setOffset("");
        e = layout().getSprites().get(0);
        assertEquals(151, e.getX());
        assertEquals(-368, e.getY());
        assertTrue(e.isCut());
        assertEquals(368, e.overflowTop());
        assertEquals(0, e.overflowBottom());
        assertEquals("drmunoz_neutral.png", e.getLabel());
    }

    @Test
    public void offsetReplacesArm() throws IOException {
        png("s.png", 100, 50);
        ObjImpl obj = sprite("s.png", Obj.CoordsOrigin.LeftBottom);
        obj.setOffset("100,-20");
        ScreenLayout.Element e = layout().getSprites().get(0);
        assertEquals(100, e.getX());
        assertEquals(1080 - 50 - 20, e.getY());
    }

    @Test
    public void nestedObjectFromParentPoint() throws IOException {
        png("scroll.png", 400, 600);
        png("icon.png", 64, 64);
        ObjImpl scroll = sprite("scroll.png", Obj.CoordsOrigin.LeftTop);
        ObjImpl icon = new ObjImpl(m_book);
        icon.setImageFileName("icon.png");
        icon.setGraphical(true);
        icon.setImageInScene(true);
        icon.setOffset("10,20");
        icon.setContainerId(scroll.getId());
        scroll.addContainedObjId(icon.getId());
        m_book.addObj(icon);
        ObjImpl second = new ObjImpl(m_book);
        second.setImageFileName("icon.png");
        second.setGraphical(true);
        second.setImageInScene(true);
        // без offset: координаты внутри родителя на холсте, без пересчёта на экран
        scroll.getCoords().setLeft(150);
        scroll.getCoords().setTop(150);
        second.getCoords().setLeft(155);
        second.getCoords().setTop(180);
        second.setContainerId(scroll.getId());
        scroll.addContainedObjId(second.getId());
        m_book.addObj(second);

        List<ScreenLayout.Element> sprites = layout().getSprites();
        assertEquals(3, sprites.size());
        assertEquals(scroll.getId(), sprites.get(1).getParentId());
        assertEquals(480 + 10, sprites.get(1).getX());
        assertEquals(540 + 20, sprites.get(1).getY());
        assertEquals(480 + 5, sprites.get(2).getX());
        assertEquals(540 + 30, sprites.get(2).getY());
    }

    @Test
    public void nonGraphicalObjectsAreNotSprites() throws IOException {
        png("s.png", 100, 50);
        ObjImpl obj = sprite("s.png", Obj.CoordsOrigin.LeftTop);
        obj.setGraphical(false);
        assertTrue(layout().getSprites().isEmpty());
        // правка из диалога: флажок «Graphical» ещё не сохранён
        assertEquals(1, ScreenLayout.layout(
                ScreenLayout.Input.of(m_page).obj(obj.getId(), "s.png", true), m_images, m_library
        ).getSprites().size());
    }

    @Test
    public void backgroundCenteredInItsSize() throws IOException {
        png("bg.png", 1920, 1080);
        png("tall.png", 1920, 1448);
        m_page.setImageFileName("bg.png");
        ScreenLayout.Screen screen = layout();
        assertEquals(0, screen.getBackground().getX());
        assertEquals(0, screen.getBackground().getY());
        assertFalse(screen.isBackgroundMismatched());
        assertFalse(screen.isStandardTheme());

        screen = ScreenLayout.layout(ScreenLayout.Input.of(m_page).pageImage("tall.png"), m_images, m_library);
        assertEquals(-184, screen.getBackground().getY());
        assertTrue(screen.isBackgroundMismatched());
        assertEquals(184, screen.getBackground().overflowTop());
        assertEquals(184, screen.getBackground().overflowBottom());

        m_page.setTheme(Theme.STANDARD);
        assertTrue(layout().isStandardTheme());
        assertTrue(ScreenLayout.layout(ScreenLayout.Input.of(m_page).theme(Theme.VN), m_images, m_library).isStandardTheme() == false);
    }

    @Test
    public void redirectFirstNameAndAnimationFrame() throws IOException {
        png("walk1.png", 10, 10);
        png("walk2.png", 20, 20);
        png("walk3.png", 30, 30);
        png("big.png", 300, 300);
        MediaFileImpl redirected = new MediaFileImpl("small.png");
        redirected.setRedirect("big.png");
        m_library.add(redirected);

        ObjImpl walk = sprite("walk3.png", Obj.CoordsOrigin.LeftTop);
        walk.setAnimatedImage(true);
        walk.setStartFrame(2);
        ObjImpl red = sprite("small.png;walk1.png", Obj.CoordsOrigin.LeftTop);
        ObjImpl none = sprite("VOID", Obj.CoordsOrigin.LeftTop);
        ObjImpl notInScene = sprite("walk1.png", Obj.CoordsOrigin.LeftTop);
        notInScene.setImageInScene(false);

        List<ScreenLayout.Element> sprites = layout().getSprites();
        assertEquals("walk2.png", sprites.get(0).getFile().getName());
        assertEquals(20, sprites.get(0).getWidth());
        assertEquals("big.png", sprites.get(1).getFile().getName());
        assertEquals(300, sprites.get(1).getWidth());
        assertNull(sprites.get(2).getFile());
        assertFalse(sprites.get(2).hasImage());
        assertNull(sprites.get(3).getFile());
        assertNotNull(none);
    }
}
