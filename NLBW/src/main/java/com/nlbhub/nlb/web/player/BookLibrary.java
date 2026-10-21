/**
 * @(#)BookLibrary.java
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

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.util.BookPack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Книги веб-плеера: каталоги книг и пакеты книг (&lt;книга&gt;.nlbpack, формат NLBNavigator) в корне
 * библиотеки и книги, переданные из редактора (в памяти, со всеми несохранёнными правками).
 * Пакет распаковывается в кэш при первом обращении (см. {@link BookPack#unpack}), каталог с тем
 * же именем важнее пакета. Загруженная книга не меняется во время игры (состояние
 * партии — в сессии игрока), поэтому один экземпляр делят все игроки. Экземпляров — по одному
 * на язык: язык текстов задаёт сама книга ({@link NonLinearBookImpl#setPlaybackLanguage}), и
 * игроки на разных языках играют разные экземпляры. Экземпляр на языке загружается при первом
 * обращении к нему.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class BookLibrary {
    /**
     * Сведения о книге для списка книг
     */
    public static final class BookInfo {
        private final String m_id;
        private final String m_title;
        private final String m_author;
        private final String m_language;
        private final List<String> m_languages;
        private final String m_mode;

        BookInfo(String id, String title, String author, String language, List<String> languages, String mode) {
            m_id = id;
            m_title = title;
            m_author = author;
            m_language = language;
            m_languages = languages;
            m_mode = mode;
        }

        public String getId() {
            return m_id;
        }

        public String getTitle() {
            return m_title;
        }

        public String getAuthor() {
            return m_author;
        }

        /**
         * Язык книги по умолчанию (свойства книги)
         */
        public String getLanguage() {
            return m_language;
        }

        /**
         * Языки, на которых есть тексты книги (со всеми модулями)
         */
        public List<String> getLanguages() {
            return m_languages;
        }

        /**
         * Режим по умолчанию: «standard» для книги с темой STANDARD, иначе «vn»
         */
        public String getMode() {
            return m_mode;
        }
    }

    private static final String STARTPOINT_FILE = "startpoint";

    private volatile File m_root;
    /**
     * Книги из редактора: id -> книга (её язык — язык редактора)
     */
    private final Map<String, NonLinearBook> m_editorBooks = new ConcurrentHashMap<>();
    /**
     * id + '\n' + язык -> экземпляр книги
     */
    private final Map<String, NonLinearBook> m_instances = new ConcurrentHashMap<>();
    private final Map<String, BookMedia> m_media = new ConcurrentHashMap<>();
    private final Map<String, List<String>> m_languages = new ConcurrentHashMap<>();
    /**
     * Пакеты книг: id -> пакет и каталог, куда он распакован
     */
    private final Map<String, Unpacked> m_packs = new ConcurrentHashMap<>();
    private final File m_packCache;

    private static final class Unpacked {
        private final BookPack m_pack;
        private final File m_dir;
        private final long m_length;
        private final long m_mtime;

        Unpacked(BookPack pack, File dir, File file) {
            m_pack = pack;
            m_dir = dir;
            m_length = file.length();
            m_mtime = file.lastModified();
        }

        boolean isFresh(File file) {
            return file.length() == m_length && file.lastModified() == m_mtime;
        }
    }

    /**
     * root — каталог, в котором каталоги книг; null — только книги из редактора
     */
    public BookLibrary(File root) {
        this(root, BookPack.defaultCacheDir());
    }

    /**
     * packCache — куда распаковывать пакеты книг
     */
    public BookLibrary(File root, File packCache) {
        m_root = root;
        m_packCache = packCache;
    }

    public void setRoot(File root) {
        m_root = root;
        clear();
    }

    public File getRoot() {
        return m_root;
    }

    /**
     * Книга из редактора (в памяти, с несохранёнными правками) — важнее одноимённого каталога.
     * Запоминается её копия на момент передачи
     */
    public void putEditorBook(String id, NonLinearBook book) {
        // копия: правки в редакторе во время игры не меняют книгу под игроками
        m_editorBooks.put(id, book instanceof NonLinearBookImpl ? ((NonLinearBookImpl) book).copyForPlayback() : book);
        forget(id);
    }

    /**
     * Забыть загруженные экземпляры (книги в каталоге изменились, редактор передал новые)
     */
    public void clear() {
        m_editorBooks.clear();
        m_instances.clear();
        m_media.clear();
        m_languages.clear();
        m_packs.clear();
    }

    private void forget(String id) {
        m_instances.keySet().removeIf(key -> key.startsWith(id + "\n"));
        m_media.remove(id);
        m_languages.remove(id);
        m_packs.remove(id);
    }

    /**
     * Имя каталога книги — без путей: «..», разделители и пустое имя не допускаются
     */
    static boolean isValidId(String id) {
        return id != null && !id.isEmpty() && !id.equals(".") && !id.equals("..")
                && id.indexOf('/') < 0 && id.indexOf('\\') < 0 && id.indexOf('\0') < 0;
    }

    /**
     * Каталог книги; null — нет такой книги на диске
     */
    File bookDir(String id) {
        if (!isValidId(id)) {
            return null;
        }
        NonLinearBook editorBook = m_editorBooks.get(id);
        if (editorBook != null) {
            return editorBook.getRootDir();
        }
        File root = m_root;
        if (root == null) {
            return null;
        }
        File dir = new File(root, id);
        if (new File(dir, STARTPOINT_FILE).isFile()) {
            return dir;
        }
        Unpacked unpacked = unpacked(id);
        return unpacked == null ? null : unpacked.m_dir;
    }

    private File packFile(String id) {
        File root = m_root;
        if (root == null || !isValidId(id)) {
            return null;
        }
        File file = new File(root, id + BookPack.EXTENSION);
        return file.isFile() ? file : null;
    }

    /**
     * Пакет книги, распакованный в кэш; null — пакета нет или он не читается. Пакет, изменённый
     * на диске, распаковывается заново
     */
    private Unpacked unpacked(String id) {
        File file = packFile(id);
        if (file == null) {
            return null;
        }
        Unpacked cached = m_packs.get(id);
        if (cached != null && cached.isFresh(file)) {
            return cached;
        }
        synchronized (m_packs) {
            cached = m_packs.get(id);
            if (cached != null && cached.isFresh(file)) {
                return cached;
            }
            try {
                BookPack pack = BookPack.read(file);
                Unpacked unpacked = new Unpacked(pack, pack.unpack(m_packCache), file);
                if (cached != null) {
                    forget(id);
                }
                m_packs.put(id, unpacked);
                return unpacked;
            } catch (IOException e) {
                return null;
            }
        }
    }

    public boolean exists(String id) {
        return isValidId(id) && (m_editorBooks.containsKey(id) || bookDir(id) != null);
    }

    /**
     * Книги библиотеки: из редактора, затем каталоги по имени
     */
    public List<BookInfo> books() {
        Set<String> ids = new TreeSet<>(m_editorBooks.keySet());
        File root = m_root;
        File[] dirs = root == null ? null : root.listFiles(File::isDirectory);
        if (dirs != null) {
            Arrays.sort(dirs);
            for (File dir : dirs) {
                if (new File(dir, STARTPOINT_FILE).isFile() && isValidId(dir.getName())) {
                    ids.add(dir.getName());
                }
            }
        }
        File[] packs = root == null ? null : root.listFiles(BookPack::isPackFile);
        if (packs != null) {
            for (File pack : packs) {
                String name = pack.getName();
                String id = name.substring(0, name.length() - BookPack.EXTENSION.length());
                if (isValidId(id) && name.endsWith(BookPack.EXTENSION)) {
                    ids.add(id);
                }
            }
        }
        List<BookInfo> result = new ArrayList<>();
        for (String id : ids) {
            NonLinearBook book = book(id, null);
            if (book != null) {
                result.add(new BookInfo(id, book.getTitle(), book.getAuthor(), book.getLanguage(), languages(id),
                        defaultMode(book)));
            }
        }
        return result;
    }

    /**
     * Режим игры по умолчанию для книги: «standard» для темы STANDARD, иначе «vn»
     */
    public static String defaultMode(NonLinearBook book) {
        return book.getTheme() == Theme.STANDARD ? "standard" : "vn";
    }

    /**
     * Экземпляр книги на языке (null — язык книги по умолчанию); null — книги нет или она не
     * загрузилась. Язык, которого в книге нет, — язык по умолчанию
     */
    public NonLinearBook book(String id, String language) {
        if (!exists(id)) {
            return null;
        }
        NonLinearBook base = baseBook(id);
        if (base == null) {
            return null;
        }
        if (language == null || language.equals(base.getLanguage()) || !languages(id).contains(language)) {
            return base;
        }
        File dir = bookDir(id);
        return m_instances.computeIfAbsent(id + "\n" + language, key -> {
            // книга редактора — копией (несохранённые правки), каталог — загрузкой с диска
            NonLinearBookImpl book = base instanceof NonLinearBookImpl && m_editorBooks.containsKey(id)
                    ? ((NonLinearBookImpl) base).copyForPlayback()
                    : load(dir);
            if (book != null) {
                book.setPlaybackLanguage(language);
            }
            return book;
        });
    }

    /**
     * Экземпляр на языке книги по умолчанию: книга редактора или загруженная с диска
     */
    private NonLinearBook baseBook(String id) {
        NonLinearBook editorBook = m_editorBooks.get(id);
        if (editorBook != null) {
            return editorBook;
        }
        // каталог — до computeIfAbsent: распаковка обновлённого пакета меняет m_instances
        File dir = bookDir(id);
        return m_instances.computeIfAbsent(id + "\n", key -> load(dir));
    }

    private static NonLinearBookImpl load(File dir) {
        if (dir == null) {
            return null;
        }
        try {
            NonLinearBookImpl book = new NonLinearBookImpl();
            return book.load(dir.getPath(), new DummyProgressData()) ? book : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Языки, на которых есть тексты книги (страниц, со всеми модулями), и язык книги
     */
    public List<String> languages(String id) {
        return m_languages.computeIfAbsent(id, key -> {
            NonLinearBook book = baseBook(id);
            Set<String> languages = new TreeSet<>();
            collectLanguages(book, languages, 0);
            return new ArrayList<>(languages);
        });
    }

    private static void collectLanguages(NonLinearBook book, Set<String> out, int depth) {
        if (book == null || depth > 32) {
            return;
        }
        if (book.getLanguage() != null && !book.getLanguage().isEmpty()) {
            out.add(book.getLanguage());
        }
        for (Page page : book.getPages().values()) {
            for (String language : page.getTexts().keySet()) {
                if (!page.getTexts().get(language).isEmpty()) {
                    out.add(language);
                }
            }
            NonLinearBook module = page.getModule();
            if (module != null && !module.isEmpty()) {
                collectLanguages(module, out, depth + 1);
            }
        }
    }

    /**
     * Медиафайлы книги (null — книги нет)
     */
    public BookMedia media(String id) {
        File dir = bookDir(id);
        if (dir == null) {
            return null;
        }
        Unpacked unpacked = m_packs.get(id);
        BookPack pack = unpacked != null && unpacked.m_dir.equals(dir) ? unpacked.m_pack : null;
        return m_media.computeIfAbsent(id, key -> new BookMedia(dir, pack));
    }
}
