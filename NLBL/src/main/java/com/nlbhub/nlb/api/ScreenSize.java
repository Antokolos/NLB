/**
 * @(#)ScreenSize.java
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
package com.nlbhub.nlb.api;

import java.util.List;

/**
 * Размер экрана игры — свойство книги (файл {@code screen} в корне книги, строка вида
 * {@code 1280x720}; нет файла — 1920×1080, как у всех прежних книг). Экран один на всю игру:
 * страницы VN и STANDARD. Пропорция у всех вариантов одна — 16:9, поэтому раскладка темы и
 * картинки пересчитываются одним множителем ({@link #scaleFrom}).
 */
public final class ScreenSize {
    public static final ScreenSize DEFAULT = new ScreenSize(1920, 1080);
    /** Варианты в свойствах книги (все 16:9): меньше, по умолчанию, QHD, 4K */
    public static final List<ScreenSize> PRESETS = List.of(
            new ScreenSize(1280, 720), DEFAULT, new ScreenSize(2560, 1440), new ScreenSize(3840, 2160)
    );

    private final int m_width;
    private final int m_height;

    public ScreenSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Screen size must be positive: " + width + "x" + height);
        }
        m_width = width;
        m_height = height;
    }

    public int getWidth() {
        return m_width;
    }

    public int getHeight() {
        return m_height;
    }

    /** Множитель от экрана other к этому (по ширине; у 16:9 по высоте тот же) */
    public double scaleFrom(ScreenSize other) {
        return (double) m_width / other.m_width;
    }

    /**
     * Из строки файла {@code screen}: «1280x720» (допускаются пробелы и «×»). Не разобрать —
     * {@link #DEFAULT}: книга должна открываться, даже если файл испорчен вручную.
     */
    public static ScreenSize parse(String text) {
        if (text == null) {
            return DEFAULT;
        }
        String[] parts = text.trim().toLowerCase().split("\\s*[x×]\\s*");
        if (parts.length != 2) {
            return DEFAULT;
        }
        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            return (width > 0 && height > 0) ? new ScreenSize(width, height) : DEFAULT;
        } catch (NumberFormatException e) {
            return DEFAULT;
        }
    }

    /** Как в файле {@code screen}: «1280x720» */
    @Override
    public String toString() {
        return m_width + "x" + m_height;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScreenSize && ((ScreenSize) o).m_width == m_width && ((ScreenSize) o).m_height == m_height;
    }

    @Override
    public int hashCode() {
        return 31 * m_width + m_height;
    }
}
