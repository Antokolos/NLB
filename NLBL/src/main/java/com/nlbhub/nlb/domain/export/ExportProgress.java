/**
 * @(#)ExportProgress.java
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
package com.nlbhub.nlb.domain.export;

/**
 * Прогресс экспорта: слушатель на поток (экспорт идёт в одном потоке), которому экспорт сообщает,
 * что делает — формат («QSP»), этап (страницы и объекты, запись файла, картинки, звук) и
 * сколько сделано из скольких. Слушатель может отменить экспорт: следующий шаг бросит
 * {@link Cancelled}. Без слушателя шаги ничего не делают.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class ExportProgress {
    /** Экспорт отменён слушателем */
    public static final class Cancelled extends RuntimeException {
        public Cancelled() {
            super("Export cancelled");
        }
    }

    public interface Listener {
        /**
         * @param format формат («QSP»; пусто — без формата)
         * @param stage  этап
         * @param done   сделано
         * @param total  всего; 0 и меньше — неизвестно
         */
        void progress(String format, String stage, int done, int total);

        boolean isCancelled();
    }

    // названия этапов показывает редактор, а его интерфейс — на английском
    public static final String STAGE_BLOCKS = "pages and objects";
    public static final String STAGE_WRITE = "writing the file";
    public static final String STAGE_IMAGES = "images";
    public static final String STAGE_SOUND = "sound";

    private static final ThreadLocal<Listener> LISTENER = new ThreadLocal<>();
    private static final ThreadLocal<String> FORMAT = ThreadLocal.withInitial(() -> "");
    private static final ThreadLocal<int[]> COUNTER = ThreadLocal.withInitial(() -> new int[2]);

    private ExportProgress() {
    }

    /** Слушатель для экспортов этого потока (null — убрать) */
    public static void setListener(Listener listener) {
        if (listener == null) {
            LISTENER.remove();
            FORMAT.remove();
            COUNTER.remove();
        } else {
            LISTENER.set(listener);
        }
    }

    /** Формат, который сейчас экспортируется */
    public static void format(String format) {
        FORMAT.set(format == null ? "" : format);
    }

    /** Начать этап со счётчиком (total ≤ 0 — неизвестно сколько) */
    public static void stage(String stage, int total) {
        int[] counter = COUNTER.get();
        counter[0] = 0;
        counter[1] = total;
        report(stage, 0, total);
    }

    /** Шаг этапа: счётчик +1 */
    public static void step(String stage) {
        int[] counter = COUNTER.get();
        counter[0]++;
        report(stage, counter[0], counter[1]);
    }

    private static void report(String stage, int done, int total) {
        Listener listener = LISTENER.get();
        if (listener == null) {
            return;
        }
        if (listener.isCancelled()) {
            throw new Cancelled();
        }
        listener.progress(FORMAT.get(), stage, done, total);
    }
}
