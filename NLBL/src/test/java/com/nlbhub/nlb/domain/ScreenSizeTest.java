/**
 * @(#)ScreenSizeTest.java
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

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.ScreenSize;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Размер экрана книги: файл screen, значение по умолчанию у старых книг, размер модулей, смена
 * размера с пересчётом смещений объектов и её отмена
 */
public class ScreenSizeTest {
    @Rule
    public TemporaryFolder m_tmp = new TemporaryFolder();

    @BeforeClass
    public static void config() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    @Test
    public void parseAndFormat() {
        assertEquals(new ScreenSize(1280, 720), ScreenSize.parse("1280x720"));
        assertEquals(new ScreenSize(2560, 1440), ScreenSize.parse(" 2560 × 1440\n"));
        assertEquals("1280x720", new ScreenSize(1280, 720).toString());
        assertEquals("испорченный файл — по умолчанию", ScreenSize.DEFAULT, ScreenSize.parse("wide"));
        assertEquals(ScreenSize.DEFAULT, ScreenSize.parse("0x720"));
        assertEquals(ScreenSize.DEFAULT, ScreenSize.parse(null));
        assertEquals(2.0, new ScreenSize(3840, 2160).scaleFrom(ScreenSize.DEFAULT), 0);
        assertEquals(4, ScreenSize.PRESETS.size());
        for (ScreenSize size : ScreenSize.PRESETS) {
            assertEquals("все варианты 16:9: " + size, size.getWidth() * 9, size.getHeight() * 16);
        }
    }

    private static NonLinearBookFacade newFacade() {
        NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        return facade;
    }

    @Test
    public void fileOnlyForNonDefaultSizeAndModulesUseMainBook() throws Exception {
        File book = new File(m_tmp.getRoot(), "book");
        NonLinearBookFacade facade = newFacade();
        facade.addPage(facade.createPage(10, 10));
        facade.saveAs(book, new DummyProgressData());
        assertFalse("размер по умолчанию — без файла (старые книги не меняются)", new File(book, "screen").exists());

        NonLinearBookFacade opened = newFacade();
        opened.load(book.getPath(), new DummyProgressData());
        assertEquals("книга без файла — 1920×1080", ScreenSize.DEFAULT, opened.getNlb().getScreenSize());

        opened.changeScreenSize(new ScreenSize(1280, 720), false);
        opened.save(false, new DummyProgressData());
        assertEquals("1280x720", Files.readString(new File(book, "screen").toPath(), StandardCharsets.UTF_8));

        NonLinearBookFacade again = newFacade();
        again.load(book.getPath(), new DummyProgressData());
        assertEquals(new ScreenSize(1280, 720), again.getNlb().getScreenSize());
        // внутренний модуль страницы — размер главной книги
        PageImpl page = (PageImpl) again.getNlb().getPages().values().iterator().next();
        assertEquals(new ScreenSize(1280, 720), page.getModuleImpl().getScreenSize());

        again.changeScreenSize(ScreenSize.DEFAULT, false);
        again.save(false, new DummyProgressData());
        assertFalse("вернули 1920×1080 — файл убран", new File(book, "screen").exists());
    }

    /** Страница; корневой объект с offset; вложенный без offset; вложенный в него с offset */
    private static final class Scene {
        final NonLinearBookImpl book = new NonLinearBookImpl();
        final PageImpl page = new PageImpl(book);
        final ObjImpl root = new ObjImpl(book);
        final ObjImpl scroll = new ObjImpl(book);
        final ObjImpl icon = new ObjImpl(book);
        final ObjImpl badge = new ObjImpl(book);

        Scene() {
            book.addPage(page);
            root.setOffset("100,-30");
            put(root, page.getId(), 0, 0);
            page.addContainedObjId(root.getId());
            // свиток без offset — корневой: его место на холсте не пересчитывается
            put(scroll, page.getId(), 100, 100);
            page.addContainedObjId(scroll.getId());
            // иконка внутри свитка без offset: iarm = (30, 40)
            put(icon, scroll.getId(), 130, 140);
            scroll.addContainedObjId(icon.getId());
            // значок внутри иконки с offset
            badge.setOffset("5,7");
            put(badge, icon.getId(), 131, 141);
            icon.addContainedObjId(badge.getId());
        }

        private void put(ObjImpl obj, String containerId, float left, float top) {
            obj.setContainerId(containerId);
            obj.getCoords().setLeft(left);
            obj.getCoords().setTop(top);
            book.addObj(obj);
        }
    }

    @Test
    public void rescaleOffsetsAndUndo() {
        Scene s = new Scene();
        assertEquals("root, icon (iarm), badge — три объекта", 3, s.book.countOffsetChanges(new ScreenSize(3840, 2160)));
        NonLinearBookImpl.ChangeScreenSizeCommand command = s.book.createChangeScreenSizeCommand(new ScreenSize(3840, 2160), true);
        command.execute();
        assertEquals(new ScreenSize(3840, 2160), s.book.getScreenSize());
        assertEquals("200,-60", s.root.getOffset());
        assertEquals("корневой без offset — место на холсте прежнее", 100, s.scroll.getCoords().getLeft(), 0);
        assertEquals("iarm × 2", 160, s.icon.getCoords().getLeft(), 0);
        assertEquals(180, s.icon.getCoords().getTop(), 0);
        assertEquals("10,14", s.badge.getOffset());
        assertEquals("с offset — едет вместе с родителем на холсте", 161, s.badge.getCoords().getLeft(), 0);
        assertEquals(181, s.badge.getCoords().getTop(), 0);

        command.revert();
        assertEquals(ScreenSize.DEFAULT, s.book.getScreenSize());
        assertEquals("100,-30", s.root.getOffset());
        assertEquals(130, s.icon.getCoords().getLeft(), 0);
        assertEquals(140, s.icon.getCoords().getTop(), 0);
        assertEquals("5,7", s.badge.getOffset());
        assertEquals(131, s.badge.getCoords().getLeft(), 0);
    }

    @Test
    public void roundTripThroughOtherSizes() {
        Scene s = new Scene();
        s.book.createChangeScreenSizeCommand(new ScreenSize(3840, 2160), true).execute();
        s.book.createChangeScreenSizeCommand(ScreenSize.DEFAULT, true).execute();
        assertEquals("×2 и обратно — без потерь", "100,-30", s.root.getOffset());
        assertEquals(130, s.icon.getCoords().getLeft(), 0);

        s.book.createChangeScreenSizeCommand(new ScreenSize(1280, 720), true).execute();
        assertEquals("×2/3 с округлением", "67,-20", s.root.getOffset());
        s.book.createChangeScreenSizeCommand(ScreenSize.DEFAULT, true).execute();
        String[] back = s.root.getOffset().split(",");
        assertTrue("через 1280 — не больше 1 px погрешности: " + s.root.getOffset(),
                Math.abs(Integer.parseInt(back[0]) - 100) <= 1 && Math.abs(Integer.parseInt(back[1]) + 30) <= 1);
    }

    @Test
    public void withoutRescaleOnlySizeChanges() {
        Scene s = new Scene();
        NonLinearBookImpl.ChangeScreenSizeCommand command = s.book.createChangeScreenSizeCommand(new ScreenSize(2560, 1440), false);
        command.execute();
        assertEquals(new ScreenSize(2560, 1440), s.book.getScreenSize());
        assertEquals("100,-30", s.root.getOffset());
        assertEquals(130, s.icon.getCoords().getLeft(), 0);
    }

    @Test
    public void internalModuleObjectsAreRescaled() {
        Scene s = new Scene();
        NonLinearBookImpl module = s.page.getModuleImpl();
        PageImpl modulePage = new PageImpl(module);
        module.addPage(modulePage);
        ObjImpl inModule = new ObjImpl(module);
        inModule.setOffset("40,10");
        inModule.setContainerId(modulePage.getId());
        modulePage.addContainedObjId(inModule.getId());
        module.addObj(inModule);
        assertEquals(new ScreenSize(1920, 1080), module.getScreenSize());
        s.book.createChangeScreenSizeCommand(new ScreenSize(3840, 2160), true).execute();
        assertEquals("80,20", inModule.getOffset());
        assertEquals("модуль — размер главной книги", new ScreenSize(3840, 2160), module.getScreenSize());
    }
}
