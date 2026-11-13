/**
 * @(#)Tar.java
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
package com.nlbhub.nlb.web.preview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Распаковка tar (пакеты npm — ustar, иногда с заголовками pax): только обычные файлы из каталога
 * prefix, без выхода за target ({@code ../} и абсолютные пути отбрасываются).
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
final class Tar {
    private static final int BLOCK = 512;

    private Tar() {
    }

    static void extract(InputStream in, String prefix, Path target) throws IOException {
        Path base = target.toAbsolutePath().normalize();
        Files.createDirectories(base);
        byte[] header = new byte[BLOCK];
        String longName = null;
        try (InputStream tar = in) {
            while (readFully(tar, header)) {
                if (isZero(header)) {
                    break;
                }
                String name = string(header, 0, 100);
                String ustarPrefix = string(header, 345, 155);
                if (!ustarPrefix.isEmpty() && string(header, 257, 5).equals("ustar")) {
                    name = ustarPrefix + "/" + name;
                }
                long size = Long.parseLong(string(header, 124, 12).trim().isEmpty() ? "0" : string(header, 124, 12).trim(), 8);
                char type = (char) header[156];
                byte[] data = readData(tar, size);
                if (type == 'L') {  // длинное имя GNU — для следующего заголовка
                    longName = new String(data, StandardCharsets.UTF_8).replace("\0", "");
                    continue;
                }
                if (type == 'x') {  // pax: path=… для следующего заголовка
                    longName = paxPath(new String(data, StandardCharsets.UTF_8));
                    continue;
                }
                if (longName != null) {
                    name = longName;
                    longName = null;
                }
                if ((type != '0' && type != '\0') || !name.startsWith(prefix)) {
                    continue;
                }
                Path file = base.resolve(name.substring(prefix.length())).normalize();
                if (!file.startsWith(base) || file.equals(base)) {
                    continue;
                }
                Files.createDirectories(file.getParent());
                Files.copy(new java.io.ByteArrayInputStream(data), file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String paxPath(String records) {
        for (String line : records.split("\n")) {
            int eq = line.indexOf(" path=");
            if (eq >= 0) {
                return line.substring(eq + " path=".length());
            }
        }
        return null;
    }

    private static byte[] readData(InputStream in, long size) throws IOException {
        if (size > Integer.MAX_VALUE - BLOCK) {
            throw new IOException("Слишком большой файл в архиве");
        }
        int padded = (int) ((size + BLOCK - 1) / BLOCK * BLOCK);
        byte[] buf = new byte[padded];
        if (!readFully(in, buf)) {
            throw new IOException("Архив оборван");
        }
        return java.util.Arrays.copyOf(buf, (int) size);
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                if (off == 0) {
                    return false;
                }
                throw new IOException("Архив оборван");
            }
            off += n;
        }
        return true;
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String string(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }
}
