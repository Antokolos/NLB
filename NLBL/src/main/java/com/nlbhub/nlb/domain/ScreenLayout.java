/**
 * @(#)ScreenLayout.java
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

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.MediaFile;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.export.ExportManager;
import com.nlbhub.nlb.util.ImageDownscaler;
import com.nlbhub.nlb.util.StringHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Раскладка экрана VN 1920×1080 для предпросмотра «как на экране» в редакторе: фон страницы и
 * спрайты её графических объектов — прямоугольники в координатах экрана (могут выходить за него:
 * так видно, что обрежется).
 * <p>
 * Правила — как в игре: положение объекта — {@link ScreenPlacement} (экспорт, веб-плеер), из
 * него прямоугольник — как GameScreen::objectRect NLBNavigator и objectRect в screen.js (vn.lua
 * postoxy): по горизонтали left — arm, middle — по центру, right — к правому краю; по вертикали
 * так же; плюс offset; вложенный объект — от точки родителя на iarm. Спрайт — в своём размере.
 * Фон — как фон INSTEAD (vn.lua): в своём размере по центру экрана.
 * <p>
 * Показывается начальное состояние страницы: объекты, которые появятся по ходу игры
 * (модификации), не учитываются; у анимации — первый кадр; из нескольких картинок через «;» —
 * первая.
 */
public final class ScreenLayout {
    public static final int SCREEN_WIDTH = ScreenPlacement.SCREEN_WIDTH;
    public static final int SCREEN_HEIGHT = ScreenPlacement.SCREEN_HEIGHT;

    private ScreenLayout() {
    }

    public enum Kind {BACKGROUND, SPRITE}

    /** Фон или спрайт: прямоугольник на экране и картинка */
    public static final class Element {
        private final Kind m_kind;
        private final String m_objId;
        private final String m_parentId;
        private final String m_label;
        private final File m_file;
        private final int m_x;
        private final int m_y;
        private final int m_width;
        private final int m_height;

        Element(Kind kind, String objId, String parentId, String label, File file, int x, int y, int width, int height) {
            m_kind = kind;
            m_objId = objId;
            m_parentId = parentId;
            m_label = label;
            m_file = file;
            m_x = x;
            m_y = y;
            m_width = width;
            m_height = height;
        }

        public Kind getKind() {
            return m_kind;
        }

        /** Объект спрайта (null у фона) */
        public String getObjId() {
            return m_objId;
        }

        /** Родительский объект вложенного спрайта; null — корневой */
        public String getParentId() {
            return m_parentId;
        }

        /** Как назвать автору: имя объекта, отображаемое имя или имя файла */
        public String getLabel() {
            return m_label;
        }

        /** Файл картинки (уже с учётом переадресации и кадра); null — картинки нет */
        public File getFile() {
            return m_file;
        }

        public int getX() {
            return m_x;
        }

        public int getY() {
            return m_y;
        }

        public int getWidth() {
            return m_width;
        }

        public int getHeight() {
            return m_height;
        }

        public boolean hasImage() {
            return m_file != null && m_width > 0 && m_height > 0;
        }

        public int overflowTop() {
            return Math.max(0, -m_y);
        }

        public int overflowBottom() {
            return Math.max(0, m_y + m_height - SCREEN_HEIGHT);
        }

        public int overflowLeft() {
            return Math.max(0, -m_x);
        }

        public int overflowRight() {
            return Math.max(0, m_x + m_width - SCREEN_WIDTH);
        }

        /** Часть картинки за краем экрана — в игре не видна */
        public boolean isCut() {
            return hasImage() && (overflowTop() + overflowBottom() + overflowLeft() + overflowRight() > 0);
        }
    }

    /** Экран страницы: фон (может не быть) и спрайты в порядке рисования */
    public static final class Screen {
        private final boolean m_standard;
        private final Element m_background;
        private final List<Element> m_sprites;

        Screen(boolean standard, Element background, List<Element> sprites) {
            m_standard = standard;
            m_background = background;
            m_sprites = Collections.unmodifiableList(sprites);
        }

        /** Тема страницы — STANDARD: экран задаёт её theme.ini, раскладка VN к ней не относится */
        public boolean isStandardTheme() {
            return m_standard;
        }

        public Element getBackground() {
            return m_background;
        }

        public List<Element> getSprites() {
            return m_sprites;
        }

        /**
         * Фон не того размера: игра (INSTEAD, NLBNavigator, веб-плеер) рисует его в своём размере по
         * центру — лишнее обрезано, вокруг — цвет рамки
         */
        public boolean isBackgroundMismatched() {
            return m_background != null && m_background.hasImage()
                    && (m_background.getWidth() != SCREEN_WIDTH || m_background.getHeight() != SCREEN_HEIGHT);
        }
    }

    /**
     * Что раскладывать: страница как в книге и несохранённые правки диалога свойств (картинка и
     * тема страницы, картинка и флажок «графический» объекта)
     */
    public static final class Input {
        private final Page m_page;
        /** Объект не на странице: экран с ним одним */
        private final Obj m_aloneObj;
        private String m_pageImage;
        private Theme m_theme;
        private final Map<String, String> m_objImages = new HashMap<>();
        private final Map<String, Boolean> m_objGraphical = new HashMap<>();
        private final Map<String, Obj.CoordsOrigin> m_objOrigins = new HashMap<>();
        private final Map<String, String> m_objOffsets = new HashMap<>();

        private Input(Page page, Obj aloneObj) {
            m_page = page;
            m_aloneObj = aloneObj;
            m_pageImage = page == null ? null : page.getImageFileName();
            m_theme = page == null ? Theme.DEFAULT : page.getTheme();
        }

        public static Input of(Page page) {
            return new Input(page, null);
        }

        /**
         * Экран страницы, на которой лежит объект (через объекты-контейнеры); объект ни на какой
         * странице — экран с ним одним
         */
        public static Input ofObj(Obj obj) {
            Page page = pageOf(obj);
            return page != null ? new Input(page, null) : new Input(null, obj);
        }

        /** Страница объекта: контейнер или контейнер контейнера; null — нет */
        public static Page pageOf(Obj obj) {
            final NonLinearBook book = obj.getCurrentNLB();
            final Set<String> seen = new HashSet<>();
            String containerId = obj.getContainerId();
            while (StringHelper.notEmpty(containerId) && seen.add(containerId)) {
                Page page = book.getPageById(containerId);
                if (page != null) {
                    return page.isDeleted() ? null : page;
                }
                Obj parent = book.getObjById(containerId);
                if (parent == null) {
                    return null;
                }
                containerId = parent.getContainerId();
            }
            return null;
        }

        /** Несохранённые точка отсчёта и offset объекта */
        public Input objPlacement(String objId, Obj.CoordsOrigin origin, String offset) {
            m_objOrigins.put(objId, origin);
            m_objOffsets.put(objId, offset);
            return this;
        }

        public Input pageImage(String imageFileNames) {
            m_pageImage = imageFileNames;
            return this;
        }

        /** Тема страницы (DEFAULT — тема книги) */
        public Input theme(Theme theme) {
            m_theme = theme;
            return this;
        }

        public Input obj(String objId, String imageFileNames, boolean graphical) {
            m_objImages.put(objId, imageFileNames);
            m_objGraphical.put(objId, graphical);
            return this;
        }
    }

    /**
     * @param imagesDir  каталог картинок книги
     * @param imageFiles библиотека картинок (переадресации .redirect)
     */
    public static Screen layout(Input input, File imagesDir, List<MediaFile> imageFiles) {
        final Page page = input.m_page;
        final NonLinearBook book = page != null ? page.getCurrentNLB() : input.m_aloneObj.getCurrentNLB();
        Theme theme = input.m_theme;
        if (theme == null || theme == Theme.DEFAULT) {
            theme = book.getTheme();
        }
        // DEFAULT экспорт в VN играет как VN (VNSTEADExportManager: тема не STANDARD)
        final boolean standard = theme == Theme.STANDARD;
        final Map<String, String> redirects = new HashMap<>();
        for (MediaFile file : imageFiles) {
            if (StringHelper.notEmpty(file.getRedirect())) {
                redirects.put(file.getFileName(), file.getRedirect());
            }
        }

        Element background = null;
        File bgFile = page == null ? null : imageFile(imagesDir, input.m_pageImage, page.isImageAnimated(), 1, redirects);
        if (bgFile != null) {
            int[] size = ImageDownscaler.imageSize(bgFile);
            int w = size == null ? 0 : size[0];
            int h = size == null ? 0 : size[1];
            // vn.lua: фон не во весь экран — по центру, math.floor((sw - w) / 2)
            background = new Element(
                    Kind.BACKGROUND, null, null, bgFile.getName(), bgFile,
                    Math.floorDiv(SCREEN_WIDTH - w, 2), Math.floorDiv(SCREEN_HEIGHT - h, 2), w, h
            );
        }

        final List<Element> sprites = new ArrayList<>();
        final List<String> roots = page != null ? page.getContainedObjIds() : List.of(input.m_aloneObj.getId());
        addObjects(input, book, roots, null, imagesDir, redirects, sprites, new HashSet<>());
        return new Screen(standard, background, sprites);
    }

    /** Обход «родитель раньше детей», в порядке файла content страницы и объектов */
    private static void addObjects(
            Input input, NonLinearBook book, List<String> ids, Element parent, File imagesDir,
            Map<String, String> redirects, List<Element> sprites, Set<String> visited
    ) {
        for (String id : ids) {
            Obj obj = book.getObjById(id);
            if (obj == null || obj.isDeleted() || !visited.add(id)) {
                continue;
            }
            Boolean graphicalOverride = input.m_objGraphical.get(id);
            boolean graphical = graphicalOverride != null ? graphicalOverride : obj.isGraphical();
            if (!graphical) {
                continue;  // не спрайт: в VN — в тексте, а не на экране; его содержимое — тоже
            }
            String names = input.m_objImages.containsKey(id) ? input.m_objImages.get(id) : obj.getImageFileName();
            int frame = obj.getStartFrame() > 0 ? obj.getStartFrame() : 1;
            File file = obj.isImageInScene() ? imageFile(imagesDir, names, obj.isAnimatedImage(), frame, redirects) : null;
            int[] size = file == null ? null : ImageDownscaler.imageSize(file);
            int w = size == null ? 0 : size[0];
            int h = size == null ? 0 : size[1];
            int x;
            int y;
            String offsetString = input.m_objOffsets.containsKey(id) ? input.m_objOffsets.get(id) : obj.getOffset();
            Obj.CoordsOrigin coordsOrigin = input.m_objOrigins.containsKey(id) ? input.m_objOrigins.get(id) : obj.getCoordsOrigin();
            if (parent != null) {
                // вложенный в объект: точка родителя + iarm (offset или координаты внутри родителя)
                int[] inner = ScreenPlacement.innerArm(obj, offsetString);
                x = parent.getX() + inner[0];
                y = parent.getY() + inner[1];
            } else {
                int origin = coordsOrigin == null ? 0 : coordsOrigin.ordinal();
                int col = origin % 3;
                int row = origin / 3;
                // с offset arm = 0,0 (decorateObjEffect: arm только без offset)
                int[] offset = StringHelper.isEmpty(offsetString) ? null : ScreenPlacement.parsePair(offsetString);
                int[] arm = StringHelper.isEmpty(offsetString) ? ScreenPlacement.arm(obj, coordsOrigin) : new int[]{0, 0};
                x = col == 0 ? arm[0] : col == 2 ? SCREEN_WIDTH - w : Math.floorDiv(SCREEN_WIDTH - w, 2);
                y = row == 0 ? arm[1] : row == 2 ? SCREEN_HEIGHT - h : Math.floorDiv(SCREEN_HEIGHT - h, 2);
                if (offset != null) {
                    x += offset[0];
                    y += offset[1];
                }
            }
            Element element = new Element(
                    Kind.SPRITE, id, parent == null ? null : parent.getObjId(), label(obj, file), file, x, y, w, h
            );
            sprites.add(element);
            addObjects(input, book, obj.getContainedObjIds(), element, imagesDir, redirects, sprites, visited);
        }
    }

    private static String label(Obj obj, File file) {
        if (StringHelper.notEmpty(obj.getName())) {
            return obj.getName();
        }
        String disp = obj.getDisp();
        if (StringHelper.notEmpty(disp) && !disp.trim().isEmpty()) {
            return disp.trim();
        }
        return file != null ? file.getName() : obj.getId();
    }

    /**
     * Файл картинки поля: первое имя (VOID и пустое — нет картинки), цель переадресации, у
     * анимации — кадр frame (имя в книге — последний кадр, ExportManager.FILE_NAME_PATTERN; нет
     * такого кадра — само имя)
     */
    static File imageFile(File imagesDir, String names, boolean animated, int frame, Map<String, String> redirects) {
        if (StringHelper.isEmpty(names)) {
            return null;
        }
        String name = names.split(Constants.MEDIA_FILE_NAME_SEP)[0].trim();
        if (name.isEmpty() || Constants.VOID.equals(name)) {
            return null;
        }
        String redirect = redirects.get(name);
        if (StringHelper.notEmpty(redirect)) {
            name = redirect;
        }
        if (animated) {
            Matcher matcher = ExportManager.FILE_NAME_PATTERN.matcher(name);
            if (matcher.find() && !matcher.group(2).isEmpty()) {
                File frameFile = new File(imagesDir, matcher.group(1) + frame + matcher.group(3));
                if (frameFile.isFile()) {
                    return frameFile;
                }
            }
        }
        return new File(imagesDir, name);
    }
}
