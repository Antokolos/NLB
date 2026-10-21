/**
 * @(#)BookPack.java
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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Пакет книги NLB (.nlbpack) — дерево каталога книги в одном файле, формат NLBNavigator
 * (Source/NLBCore/.../util/BookPack.cpp, docs/pack.md): каталоги, файлы с содержимым, а у
 * медиафайлов пакета без медиа — только имя, размер и время изменения.
 * <p>
 * Формат (числа — little-endian): «NLBPACK\n», версия u32 (2), исходный каталог (строка),
 * число записей u64, затем записи: тип u8 (0 — каталог, 1 — файл, 2 — медиафайл без
 * содержимого), путь (строка, через «/», корень — пустая строка), размер u64, время изменения
 * u64; у файла — длина содержимого u32 и само содержимое. Строка — длина u32 и байты UTF-8.
 * <p>
 * Книгу NLBL читает из каталога, поэтому пакет распаковывается ({@link #unpack}) — без крупных
 * медиафайлов: вместо них пустые файлы (загрузчику нужен только их список), а содержимое
 * читается прямо из пакета ({@link #open}). Имя картинки в пакете — прежнее (x.png), а данные
 * могут быть JPG (упаковка с конвертацией): формат определяется по содержимому.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class BookPack {
    public static final String EXTENSION = ".nlbpack";
    /**
     * Медиафайлы крупнее этого при распаковке не пишутся на диск — отдаются из пакета
     */
    public static final long LAZY_THRESHOLD = 64 * 1024;
    private static final byte[] MAGIC = "NLBPACK\n".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 2;
    private static final int MAX_PATH_BYTES = 64 * 1024;
    private static final String UNPACKED_MARKER = ".unpacked";

    public enum Type {
        DIRECTORY, FILE, MEDIA_FILE
    }

    public static final class Entry {
        private final Type m_type;
        private final String m_path;
        private final long m_size;
        private final long m_mtime;
        private final long m_offset;
        private final long m_length;

        Entry(Type type, String path, long size, long mtime, long offset, long length) {
            m_type = type;
            m_path = path;
            m_size = size;
            m_mtime = mtime;
            m_offset = offset;
            m_length = length;
        }

        public Type getType() {
            return m_type;
        }

        /**
         * Путь относительно корня книги, через «/»
         */
        public String getPath() {
            return m_path;
        }

        /**
         * Размер исходного файла
         */
        public long getSize() {
            return m_size;
        }

        public long getMtime() {
            return m_mtime;
        }

        /**
         * Длина содержимого в пакете (у картинки, перекодированной в JPG, — не размер исходного файла)
         */
        public long getLength() {
            return m_length;
        }
    }

    private final File m_file;
    private final String m_sourceDir;
    private final Map<String, Entry> m_entries;

    private BookPack(File file, String sourceDir, Map<String, Entry> entries) {
        m_file = file;
        m_sourceDir = sourceDir;
        m_entries = entries;
    }

    public static boolean isPackFile(File file) {
        return file.isFile() && file.getName().toLowerCase().endsWith(EXTENSION);
    }

    /**
     * Каталог для распаковки пакетов по умолчанию (во временном каталоге системы)
     */
    public static File defaultCacheDir() {
        return new File(System.getProperty("java.io.tmpdir"), "nlb-packs");
    }

    /**
     * Прочитать оглавление пакета (содержимое файлов не читается — запоминается его место)
     */
    public static BookPack read(File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            Reader reader = new Reader(channel);
            byte[] magic = reader.bytes(MAGIC.length);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException("это не пакет книги NLB: " + file);
            }
            if (reader.u32() != VERSION) {
                throw new IOException("неподдерживаемая версия пакета: " + file);
            }
            String sourceDir = reader.str();
            long count = reader.u64();
            if (count < 0 || count > channel.size()) {
                throw new IOException("пакет повреждён: " + file);
            }
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (long i = 0; i < count; i++) {
                int typeCode = reader.u8();
                if (typeCode > 2) {
                    throw new IOException("пакет повреждён: " + file);
                }
                Type type = Type.values()[typeCode];
                String path = reader.str();
                if (!isSafePath(path)) {
                    throw new IOException("недопустимый путь в пакете: " + path);
                }
                long size = reader.u64();
                long mtime = reader.u64();
                long offset = 0;
                long length = 0;
                if (type == Type.FILE) {
                    length = reader.u32();
                    offset = reader.position();
                    if (offset + length > channel.size()) {
                        throw new IOException("пакет повреждён: " + file);
                    }
                    reader.skip(length);
                }
                entries.put(path, new Entry(type, path, size, mtime, offset, length));
            }
            if (!entries.containsKey("")) {
                throw new IOException("пакет повреждён: " + file);
            }
            return new BookPack(file, sourceDir, Collections.unmodifiableMap(entries));
        }
    }

    /**
     * Путь записи — внутри корня: без «..», «.», пустых частей, «\» и абсолютных путей
     */
    private static boolean isSafePath(String path) {
        if (path.isEmpty()) {
            return true;
        }
        if (path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0 || path.indexOf(':') >= 0) {
            return false;
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    public File getFile() {
        return m_file;
    }

    /**
     * Каталог, из которого собран пакет (для сведения)
     */
    public String getSourceDir() {
        return m_sourceDir;
    }

    public Collection<Entry> entries() {
        return m_entries.values();
    }

    /**
     * Запись по пути относительно корня книги; null — нет такой
     */
    public Entry find(String path) {
        return m_entries.get(path);
    }

    /**
     * Медиафайл книги или модуля (каталоги images/ и sound/, как в NLBNavigator)
     */
    public static boolean isMediaPath(String path) {
        for (String dir : new String[]{"images", "sound"}) {
            if (path.startsWith(dir + "/") || path.contains("/" + dir + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Содержимое файла пакета; IOException — не файл с содержимым
     */
    public InputStream open(Entry entry) throws IOException {
        if (entry.getType() != Type.FILE) {
            throw new IOException("в пакете нет содержимого " + entry.getPath());
        }
        if (entry.getLength() == 0) {
            return new ByteArrayInputStream(new byte[0]);
        }
        FileChannel channel = FileChannel.open(m_file.toPath(), StandardOpenOption.READ);
        channel.position(entry.m_offset);
        InputStream in = Channels.newInputStream(channel);
        return new InputStream() {
            private long m_left = entry.getLength();

            @Override
            public int read() throws IOException {
                if (m_left <= 0) {
                    return -1;
                }
                int b = in.read();
                if (b >= 0) {
                    m_left--;
                }
                return b;
            }

            @Override
            public int read(byte[] buffer, int off, int len) throws IOException {
                if (m_left <= 0) {
                    return -1;
                }
                int n = in.read(buffer, off, (int) Math.min(len, m_left));
                if (n > 0) {
                    m_left -= n;
                }
                return n;
            }

            @Override
            public void close() throws IOException {
                in.close();
            }
        };
    }

    /**
     * Будет ли содержимое записи распаковано на диск (иначе на диске — пустой файл, а
     * содержимое берётся из пакета)
     */
    public static boolean isUnpacked(Entry entry) {
        return entry.getType() == Type.FILE && !(isMediaPath(entry.getPath()) && entry.getLength() > LAZY_THRESHOLD);
    }

    /**
     * Распаковать пакет в cacheDir (каталог «имя-длина-время» — пакет того же размера и времени
     * изменения распаковывается один раз) и вернуть каталог книги. Распаковка — во временный
     * каталог с переименованием в конце: незаконченная распаковка не используется
     */
    public File unpack(File cacheDir) throws IOException {
        String name = m_file.getName();
        if (name.toLowerCase().endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }
        File target = new File(
                cacheDir,
                name + "-" + Long.toHexString(m_file.length()) + "-" + Long.toHexString(m_file.lastModified())
        );
        if (new File(target, UNPACKED_MARKER).isFile()) {
            return target;
        }
        Files.createDirectories(cacheDir.toPath());
        File temp = Files.createTempDirectory(cacheDir.toPath(), name + "-tmp").toFile();
        try {
            try (RandomAccessFile file = new RandomAccessFile(m_file, "r")) {
                byte[] buffer = new byte[64 * 1024];
                for (Entry entry : m_entries.values()) {
                    File out = entry.getPath().isEmpty() ? temp : new File(temp, entry.getPath());
                    if (entry.getType() == Type.DIRECTORY) {
                        Files.createDirectories(out.toPath());
                        continue;
                    }
                    Files.createDirectories(out.getParentFile().toPath());
                    try (OutputStream os = Files.newOutputStream(out.toPath())) {
                        if (isUnpacked(entry)) {
                            file.seek(entry.m_offset);
                            long left = entry.getLength();
                            while (left > 0) {
                                int n = file.read(buffer, 0, (int) Math.min(buffer.length, left));
                                if (n < 0) {
                                    throw new EOFException("пакет повреждён: " + m_file);
                                }
                                os.write(buffer, 0, n);
                                left -= n;
                            }
                        }
                    }
                }
            }
            Files.createFile(new File(temp, UNPACKED_MARKER).toPath());
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath());
            } catch (IOException e) {
                // другой процесс успел распаковать тот же пакет
                if (!new File(target, UNPACKED_MARKER).isFile()) {
                    throw e;
                }
            }
        } finally {
            if (temp.exists()) {
                deleteTree(temp);
            }
        }
        return target;
    }

    private static void deleteTree(File dir) {
        try (Stream<java.nio.file.Path> paths = Files.walk(dir.toPath())) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // не удалось убрать временный каталог — не страшно
        }
    }

    /**
     * Чтение полей little-endian с учётом позиции в файле
     */
    private static final class Reader {
        private final FileChannel m_channel;
        private final java.nio.ByteBuffer m_buffer = java.nio.ByteBuffer.allocate(1 << 16);
        private long m_bufferStart = 0;

        Reader(FileChannel channel) {
            m_channel = channel;
            m_buffer.limit(0);
        }

        long position() {
            return m_bufferStart + m_buffer.position();
        }

        private void need(int count) throws IOException {
            if (m_buffer.remaining() >= count) {
                return;
            }
            m_bufferStart += m_buffer.position();
            m_buffer.compact();
            while (m_buffer.position() < count) {
                if (m_channel.read(m_buffer, m_bufferStart + m_buffer.position()) < 0) {
                    throw new EOFException("пакет повреждён");
                }
            }
            m_buffer.flip();
        }

        void skip(long count) throws IOException {
            long inBuffer = Math.min(count, m_buffer.remaining());
            m_buffer.position(m_buffer.position() + (int) inBuffer);
            long rest = count - inBuffer;
            if (rest > 0) {
                m_bufferStart = position() + rest;
                m_buffer.clear();
                m_buffer.limit(0);
            }
        }

        byte[] bytes(int count) throws IOException {
            byte[] result = new byte[count];
            int done = 0;
            while (done < count) {
                int chunk = Math.min(count - done, m_buffer.capacity());
                need(chunk);
                m_buffer.get(result, done, chunk);
                done += chunk;
            }
            return result;
        }

        int u8() throws IOException {
            need(1);
            return m_buffer.get() & 0xFF;
        }

        long u32() throws IOException {
            need(4);
            long v = 0;
            for (int i = 0; i < 4; i++) {
                v |= (long) (m_buffer.get() & 0xFF) << (8 * i);
            }
            return v;
        }

        long u64() throws IOException {
            need(8);
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v |= (long) (m_buffer.get() & 0xFF) << (8 * i);
            }
            return v;
        }

        String str() throws IOException {
            long length = u32();
            if (length > MAX_PATH_BYTES) {
                throw new IOException("пакет повреждён");
            }
            return new String(bytes((int) length), StandardCharsets.UTF_8);
        }
    }
}
