/**
 * @(#)ScreenPlacement.java
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

import com.nlbhub.nlb.api.Coords;
import com.nlbhub.nlb.api.Obj;

/**
 * Положение графического объекта на экране VN 1920×1080 — общие правила экспорта в INSTEAD
 * (ExportManager.getRelativeCoords: arm), ядра веб-плеера (PlayerEngine.objPlacement) и
 * предпросмотра «как на экране» в редакторе (ScreenLayout). Порт PlayerEngine::objPlacement
 * NLBNavigator.
 */
public final class ScreenPlacement {
    public static final int SCREEN_WIDTH = 1920;
    public static final int SCREEN_HEIGHT = 1080;

    private ScreenPlacement() {
    }

    /**
     * arm корневого объекта: координаты относительно страницы, пересчитанные на экран 1920×1080,
     * и отсчёт от выбранного угла (ExportManager.getTransformedCoords). Ширина или высота
     * страницы 0 — координата 0.
     */
    public static int[] arm(Obj obj) {
        return arm(obj, obj.getCoordsOrigin());
    }

    /** То же с другой точкой отсчёта (несохранённая правка в свойствах объекта) */
    public static int[] arm(Obj obj, Obj.CoordsOrigin coordsOrigin) {
        final double screenW = SCREEN_WIDTH;
        final double screenH = SCREEN_HEIGHT;
        Coords rel = obj.getRelativeCoords(true);
        double w = rel.getWidth();
        double h = rel.getHeight();
        double left = w != 0 ? Math.floor(rel.getLeft() * screenW / w) : 0.0;
        double top = h != 0 ? Math.floor(rel.getTop() * screenH / h) : 0.0;
        double x = left;
        double y = top;
        int origin = coordsOrigin == null ? 0 : coordsOrigin.ordinal();
        switch (origin) {
            case 1:  // MiddleTop
                x = left - screenW / 2;
                break;
            case 2:  // RightTop (как в экспорте: y = 0)
                x = screenW - left;
                y = 0;
                break;
            case 3:  // LeftMiddle
                y = screenH / 2 - top;
                break;
            case 4:  // MiddleMiddle
                x = left - screenW / 2;
                y = screenH / 2 - top;
                break;
            case 5:  // RightMiddle
                x = screenW - left;
                y = screenH / 2 - top;
                break;
            case 6:  // LeftBottom
                y = screenH - top;
                break;
            case 7:  // MiddleBottom
                x = left - screenW / 2;
                y = screenH - top;
                break;
            case 8:  // RightBottom
                x = screenW - left;
                y = screenH - top;
                break;
            default:  // LeftTop
                break;
        }
        return new int[]{(int) x, (int) y};
    }

    /**
     * iarm объекта, вложенного в объект в самой книге (decorateObjArm): его offset или
     * координаты относительно родителя (без пересчёта на экран)
     */
    public static int[] innerArm(Obj obj) {
        return innerArm(obj, obj.getOffset());
    }

    /** То же с другим offset (несохранённая правка в свойствах объекта) */
    public static int[] innerArm(Obj obj, String offset) {
        int[] inner = parsePair(offset);
        if (inner != null) {
            return inner;
        }
        Coords rel = obj.getRelativeCoords(true);
        return new int[]{(int) rel.getLeft(), (int) rel.getTop()};
    }

    /** «x,y» -> {x, y}; числа — как std::stoi; null — не пара чисел */
    public static int[] parsePair(String s) {
        if (s == null) {
            return null;
        }
        int comma = s.indexOf(',');
        if (comma < 0) {
            return null;
        }
        Long x = parseCInt(s.substring(0, comma));
        Long y = parseCInt(s.substring(comma + 1));
        return (x == null || y == null) ? null : new int[]{x.intValue(), y.intValue()};
    }

    /**
     * Как std::stoi: пробелы, знак, цифры; остаток строки не важен. null — числа нет
     */
    static Long parseCInt(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        int start = i;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        int digits = i;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == digits) {
            return null;
        }
        try {
            long value = Long.parseLong(s.substring(start, i));
            return (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
