/**
 * @(#)BookMedia.java
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

import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.util.BookPack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Медиафайлы книги для веб-клиента: картинки и звуки книги и всех её внешних модулей по имени
 * файла, которое отдаёт модель экрана (уже выбранному по тегу и переадресациям). Порт
 * BookMediaSource NLBNavigator: файлы книги важнее файлов модулей, имя ищется и с каталогом
 * ("images/x.png"), и без него. У книги из пакета (.nlbpack) содержимое крупных файлов
 * читается из пакета: на диске при распаковке остаются пустые файлы
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class BookMedia {
    /**
     * Найденный медиафайл
     */
    public interface Media {
        String getName();

        long getLength();

        InputStream open() throws IOException;
    }

    private static final int MAX_MODULE_DEPTH = 8;
    private final File m_root;
    private final BookPack m_pack;
    /**
     * Имя -> путь относительно корня книги, через «/»
     */
    private Map<String, String> m_index = null;

    public BookMedia(File bookRoot) {
        this(bookRoot, null);
    }

    /**
     * pack — пакет, распакованный в bookRoot (null — обычный каталог книги)
     */
    public BookMedia(File bookRoot, BookPack pack) {
        m_root = bookRoot;
        m_pack = pack;
    }

    private void buildIndex(Map<String, String> index, File dir, String prefix, int depth) {
        for (String media : new String[]{NonLinearBook.IMAGES_DIR_NAME, NonLinearBook.SOUND_DIR_NAME}) {
            File mediaDir = new File(dir, media);
            File[] files = mediaDir.listFiles(File::isFile);
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String path = prefix + media + "/" + file.getName();
                index.putIfAbsent(file.getName(), path);  // книга раньше модулей
                index.putIfAbsent(media + "/" + file.getName(), path);
            }
        }
        File[] modules = new File(dir, "modules").listFiles(File::isDirectory);
        if (modules != null && depth < MAX_MODULE_DEPTH) {
            java.util.Arrays.sort(modules);
            for (File module : modules) {
                buildIndex(index, module, prefix + "modules/" + module.getName() + "/", depth + 1);
            }
        }
    }

    /**
     * Файл по имени из модели экрана; null — нет такого (или в пакете без медиа есть только его имя)
     */
    public synchronized Media resolve(String name) {
        if (name == null || name.isEmpty() || m_root == null) {
            return null;
        }
        if (m_index == null) {
            Map<String, String> index = new HashMap<>();
            buildIndex(index, m_root, "", 0);
            m_index = index;
        }
        String path = m_index.get(name);
        if (path == null && name.lastIndexOf('/') >= 0) {
            path = m_index.get(name.substring(name.lastIndexOf('/') + 1));
        }
        if (path == null) {
            return null;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        BookPack.Entry entry = m_pack == null ? null : m_pack.find(path);
        if (entry != null) {
            if (entry.getType() != BookPack.Type.FILE) {
                return null;
            }
            return new Media() {
                public String getName() {
                    return fileName;
                }

                public long getLength() {
                    return entry.getLength();
                }

                public InputStream open() throws IOException {
                    return m_pack.open(entry);
                }
            };
        }
        File file = new File(m_root, path);
        if (!file.isFile()) {
            return null;
        }
        return new Media() {
            public String getName() {
                return fileName;
            }

            public long getLength() {
                return file.length();
            }

            public InputStream open() throws IOException {
                return Files.newInputStream(file.toPath());
            }
        };
    }
}
