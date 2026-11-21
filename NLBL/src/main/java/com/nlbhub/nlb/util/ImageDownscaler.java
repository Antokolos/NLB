/**
 * @(#)ImageDownscaler.java
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
package com.nlbhub.nlb.util;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.MediaFile;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.domain.export.ExportManager;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Уменьшение картинок библиотеки книги («Уменьшить…» в Media Library редактора).
 * <p>
 * Группа картинок уменьшается с одним общим коэффициентом — по самой большой из них, чтобы
 * ракурсы одного персонажа остались одного масштаба; увеличения нет (коэффициент не больше 1).
 * Формат и имя файла сохраняются (PNG — PNG с прозрачностью, JPG — JPG), файл перезаписывается
 * через временный рядом с ним. Кадры анимированной картинки (имя в книге — последний кадр, см.
 * {@link ExportManager#FILE_NAME_PATTERN}) входят в группу все вместе, переадресованный файл
 * (.redirect) заменяется целью переадресации — её и показывает игра.
 */
public final class ImageDownscaler {
    /** Форматы, которые умеем записать без потерь смысла (GIF с анимацией — нет) */
    private static final Set<String> SUPPORTED = new HashSet<>(List.of("png", "jpg", "jpeg", "bmp"));
    private static final String TEMP_SUFFIX = ".downscale-tmp";

    private ImageDownscaler() {
    }

    /** Строка плана: файл, размер до и после */
    public static final class Item {
        private final File m_file;
        private final int m_width;
        private final int m_height;
        private int m_newWidth;
        private int m_newHeight;
        private final String m_problem;

        Item(File file, int width, int height, String problem) {
            m_file = file;
            m_width = width;
            m_height = height;
            m_newWidth = width;
            m_newHeight = height;
            m_problem = problem;
        }

        public File getFile() {
            return m_file;
        }

        public int getWidth() {
            return m_width;
        }

        public int getHeight() {
            return m_height;
        }

        public int getNewWidth() {
            return m_newWidth;
        }

        public int getNewHeight() {
            return m_newHeight;
        }

        /** Почему файл не уменьшить (не читается, формат не поддерживается; текст для редактора); null — можно */
        public String getProblem() {
            return m_problem;
        }

        public boolean isChanged() {
            return m_problem == null && (m_newWidth != m_width || m_newHeight != m_height);
        }
    }

    /** План уменьшения группы: общий коэффициент и строки по файлам */
    public static final class Plan {
        private final double m_scale;
        private final List<Item> m_items;

        Plan(double scale, List<Item> items) {
            m_scale = scale;
            m_items = Collections.unmodifiableList(items);
        }

        /** Общий коэффициент группы, 0 < scale <= 1 */
        public double getScale() {
            return m_scale;
        }

        public List<Item> getItems() {
            return m_items;
        }

        public boolean hasChanges() {
            for (Item item : m_items) {
                if (item.isChanged()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * План: размеры читаются из заголовков файлов (картинки не декодируются целиком).
     *
     * @param maxWidth  наибольшая ширина; 0 или меньше — без ограничения
     * @param maxHeight наибольшая высота; 0 или меньше — без ограничения
     */
    public static Plan plan(Collection<File> files, int maxWidth, int maxHeight) {
        List<Item> items = new ArrayList<>();
        for (File file : files) {
            items.add(readItem(file));
        }
        return compute(items, maxWidth, maxHeight);
    }

    /**
     * Тот же набор файлов с другими ограничениями — без чтения диска (размеры уже известны):
     * диалог пересчитывает план на каждое изменение поля, а кадров анимации бывают тысячи
     * (заставка «Фронтира» — 1478, чтение их заголовков — больше секунды)
     */
    public static Plan replan(Plan plan, int maxWidth, int maxHeight) {
        List<Item> items = new ArrayList<>();
        for (Item item : plan.getItems()) {
            items.add(new Item(item.getFile(), item.getWidth(), item.getHeight(), item.getProblem()));
        }
        return compute(items, maxWidth, maxHeight);
    }

    private static Plan compute(List<Item> items, int maxWidth, int maxHeight) {
        int groupWidth = 0;
        int groupHeight = 0;
        for (Item item : items) {
            if (item.getProblem() == null) {
                groupWidth = Math.max(groupWidth, item.getWidth());
                groupHeight = Math.max(groupHeight, item.getHeight());
            }
        }
        double scale = 1.0;
        if (maxWidth > 0 && groupWidth > maxWidth) {
            scale = Math.min(scale, (double) maxWidth / groupWidth);
        }
        if (maxHeight > 0 && groupHeight > maxHeight) {
            scale = Math.min(scale, (double) maxHeight / groupHeight);
        }
        if (scale < 1.0) {
            for (Item item : items) {
                if (item.getProblem() == null) {
                    item.m_newWidth = scaled(item.getWidth(), scale, maxWidth);
                    item.m_newHeight = scaled(item.getHeight(), scale, maxHeight);
                }
            }
        }
        return new Plan(scale, items);
    }

    private static int scaled(int size, double scale, int limit) {
        int result = Math.max(1, (int) Math.round(size * scale));
        return limit > 0 ? Math.min(result, limit) : result;
    }

    /**
     * Размер картинки из заголовка файла (без декодирования); null — нет файла, формат не
     * поддерживается или файл не читается
     */
    public static int[] imageSize(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        return readSize(file);
    }

    private static Item readItem(File file) {
        String format = formatOf(file);
        if (!SUPPORTED.contains(format)) {
            return new Item(file, 0, 0, "format is not supported");
        }
        int[] size = readSize(file);
        return size == null ? new Item(file, 0, 0, "cannot be read") : new Item(file, size[0], size[1], null);
    }

    /** Размер из заголовка файла любым читателем ImageIO; null — не читается */
    private static int[] readSize(File file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in != null) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(in, true, true);
                        return new int[]{reader.getWidth(0), reader.getHeight(0)};
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IOException ignored) {
            // ниже — «не читается»
        }
        return null;
    }

    private static String formatOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Уменьшить файлы плана, у которых меняется размер. JPG пишется с качеством jpegQuality
     * (1–100), PNG — без потерь с прозрачностью. Каждый файл — через временный рядом и замену,
     * чтобы при сбое не остался недописанный файл.
     */
    public static void apply(Plan plan, int jpegQuality) throws IOException {
        for (Item item : plan.getItems()) {
            if (!item.isChanged()) {
                continue;
            }
            File file = item.getFile();
            BufferedImage source = ImageIO.read(file);
            if (source == null) {
                throw new IOException("Не удалось прочитать " + file);
            }
            String format = formatOf(file);
            boolean opaque = format.equals("jpg") || format.equals("jpeg") || format.equals("bmp")
                    || !source.getColorModel().hasAlpha();
            BufferedImage result = resize(source, item.getNewWidth(), item.getNewHeight(), opaque);
            File temp = new File(file.getParentFile(), file.getName() + TEMP_SUFFIX);
            try {
                write(result, format, temp, jpegQuality);
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        }
    }

    private static void write(BufferedImage image, String format, File target, int jpegQuality) throws IOException {
        String writerFormat = format.equals("jpeg") ? "jpg" : format;
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(writerFormat);
        if (!writers.hasNext()) {
            throw new IOException("Нет записи в формате " + format);
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (writerFormat.equals("jpg")) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(1, Math.min(100, jpegQuality)) / 100.0f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /**
     * Качественное уменьшение: шагами не больше чем вдвое (билинейно), последний шаг —
     * бикубически. Одним шагом при сильном уменьшении билинейная интерполяция берёт лишь часть
     * пикселей — получаются «лесенки» и мерцание мелких деталей. Прозрачность — в
     * премультиплицированном виде, иначе по краям спрайта появляется тёмная кайма.
     */
    static BufferedImage resize(BufferedImage source, int width, int height, boolean opaque) {
        int workType = opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB_PRE;
        BufferedImage current = convert(source, workType);
        int w = current.getWidth();
        int h = current.getHeight();
        while (w / 2 >= width && h / 2 >= height && (w > width || h > height)) {
            w = Math.max(width, w / 2);
            h = Math.max(height, h / 2);
            current = draw(current, w, h, workType, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (w != width || h != height) {
            current = draw(current, width, height, workType, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        return opaque ? current : convert(current, BufferedImage.TYPE_INT_ARGB);
    }

    private static BufferedImage convert(BufferedImage source, int type) {
        if (source.getType() == type) {
            return source;
        }
        return draw(source, source.getWidth(), source.getHeight(), type, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, int type, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /**
     * Имена анимированных картинок книги (страницы и объекты, включая внутренние модули) — как
     * записаны в книге, то есть имена последних кадров.
     */
    public static Set<String> animatedImageNames(NonLinearBook book) {
        Set<String> result = new LinkedHashSet<>();
        collectAnimated(book, result, Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        return result;
    }

    private static void collectAnimated(NonLinearBook book, Set<String> result, Set<NonLinearBook> visited) {
        if (book == null || !visited.add(book)) {
            return;
        }
        for (Page page : book.getPages().values()) {
            if (page.isDeleted()) {
                continue;
            }
            if (page.isImageAnimated()) {
                addNames(page.getImageFileName(), result);
            }
            if (!page.isModuleExternal()) {
                collectAnimated(page.getModule(), result, visited);
            }
        }
        for (Obj obj : book.getObjs().values()) {
            if (!obj.isDeleted() && obj.isAnimatedImage()) {
                addNames(obj.getImageFileName(), result);
            }
        }
    }

    private static void addNames(String names, Set<String> result) {
        if (StringHelper.isEmpty(names)) {
            return;
        }
        for (String name : names.split(Constants.MEDIA_FILE_NAME_SEP)) {
            if (!name.isEmpty() && !Constants.VOID.equals(name)) {
                result.add(name);
            }
        }
    }

    /**
     * Кадры анимации по имени последнего кадра: основа + 1…N + расширение, только имеющиеся в
     * библиотеке. Имя без номера кадра — пустой список.
     */
    public static List<String> animationFrames(String animatedName, Collection<String> libraryNames) {
        List<String> result = new ArrayList<>();
        Matcher matcher = ExportManager.FILE_NAME_PATTERN.matcher(animatedName);
        if (!matcher.find() || matcher.group(2).isEmpty()) {
            return result;
        }
        int last;
        try {
            last = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return result;
        }
        Set<String> names = new HashSet<>(libraryNames);
        for (int i = 1; i <= last; i++) {
            String frame = matcher.group(1) + i + matcher.group(3);
            if (names.contains(frame)) {
                result.add(frame);
            }
        }
        return result;
    }

    /**
     * Группа для уменьшения по выделению в библиотеке: переадресованные файлы заменяются целью
     * переадресации (если она есть в библиотеке), а выделенный кадр анимации добавляет все кадры
     * своей серии. Порядок — порядок выделения, без повторов.
     *
     * @param animatedNames имена анимированных картинок книги ({@link #animatedImageNames})
     */
    public static List<String> group(Collection<String> selected, List<MediaFile> imageFiles, Set<String> animatedNames) {
        Map<String, String> redirects = new HashMap<>();
        List<String> libraryNames = new ArrayList<>();
        for (MediaFile file : imageFiles) {
            libraryNames.add(file.getFileName());
            if (!StringHelper.isEmpty(file.getRedirect())) {
                redirects.put(file.getFileName(), file.getRedirect());
            }
        }
        Set<String> library = new HashSet<>(libraryNames);
        List<List<String>> series = new ArrayList<>();
        for (String animated : animatedNames) {
            List<String> frames = animationFrames(resolve(animated, redirects, library), libraryNames);
            if (!frames.isEmpty()) {
                series.add(frames);
            }
        }
        Set<String> result = new LinkedHashSet<>();
        for (String name : selected) {
            String target = resolve(name, redirects, library);
            result.add(target);
            for (List<String> frames : series) {
                if (frames.contains(target)) {
                    result.addAll(frames);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * Предупреждения для диалога редактора (по-английски, как его интерфейс): объекты с
     * картинками группы, положение которых задано в пикселях, — смещение (offset) и вложенные
     * объекты (их координаты — в пикселях картинки родителя). После уменьшения пиксели не
     * пересчитываются, и такие объекты сдвинутся относительно картинки.
     *
     * @param group имена файлов группы ({@link #group}); переадресованные на них имена тоже учитываются
     */
    public static List<String> offsetWarnings(NonLinearBook book, Collection<String> group, List<MediaFile> imageFiles) {
        Set<String> names = new HashSet<>(group);
        for (MediaFile file : imageFiles) {
            if (!StringHelper.isEmpty(file.getRedirect()) && group.contains(file.getRedirect())) {
                names.add(file.getFileName());
            }
        }
        List<String> result = new ArrayList<>();
        collectWarnings(book, names, result, Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        return result;
    }

    private static void collectWarnings(NonLinearBook book, Set<String> names, List<String> result, Set<NonLinearBook> visited) {
        if (book == null || !visited.add(book)) {
            return;
        }
        Map<String, Integer> nested = new HashMap<>();
        for (Obj obj : book.getObjs().values()) {
            if (!obj.isDeleted() && obj.getContainerType() == Obj.ContainerType.Obj) {
                nested.merge(obj.getContainerId(), 1, Integer::sum);
            }
        }
        for (Obj obj : book.getObjs().values()) {
            if (obj.isDeleted() || !usesAny(obj.getImageFileName(), names)) {
                continue;
            }
            String label = label(obj) + "' (" + obj.getImageFileName().replace(Constants.MEDIA_FILE_NAME_SEP, ", ") + ")";
            String offset = obj.getOffset();
            if (!StringHelper.isEmpty(offset) && !offset.replace(" ", "").equals("0,0")) {
                result.add("Object '" + label + " is placed with offset " + offset
                        + " (pixels): check its position after downscaling.");
            }
            Integer count = nested.get(obj.getId());
            if (count != null) {
                result.add("Object '" + label + " contains " + count + " nested object(s) positioned in pixels"
                        + " of its image: they will not move with the downscaled image.");
            }
        }
        for (Page page : book.getPages().values()) {
            if (!page.isDeleted() && !page.isModuleExternal()) {
                collectWarnings(page.getModule(), names, result, visited);
            }
        }
    }

    /** Как назвать объект автору: имя, иначе отображаемое имя, иначе идентификатор */
    private static String label(Obj obj) {
        if (!StringHelper.isEmpty(obj.getName())) {
            return obj.getName();
        }
        String disp = obj.getDisp();
        return StringHelper.isEmpty(disp) ? obj.getId() : disp.trim();
    }

    private static boolean usesAny(String imageFileNames, Set<String> names) {
        if (StringHelper.isEmpty(imageFileNames)) {
            return false;
        }
        for (String name : imageFileNames.split(Constants.MEDIA_FILE_NAME_SEP)) {
            if (names.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static String resolve(String name, Map<String, String> redirects, Set<String> library) {
        String target = redirects.get(name);
        return target != null && library.contains(target) ? target : name;
    }
}
