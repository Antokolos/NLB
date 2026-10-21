/**
 * @(#)TextPortions.java
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
package com.nlbhub.nlb.player;

import java.util.ArrayList;
import java.util.List;

/**
 * Порции текста VN, как экспорт в VN-INSTEAD (VNSTEADExportManager.getPreprocessedChunks и
 * paginator): каждая строка текста — порция; длинная строка делится по предложениям — порция
 * набирается, пока она не длиннее {@link #THRESHOLD} символов, а хвост, с которым последняя
 * порция не длиннее {@link #THRESHOLD_WARN}, присоединяется к ней. Пустые строки только
 * разделяют. Порт GameScreen::portionsOf из NLBNavigator. Листание по порциям (интерфейс)
 * никогда не повторяет прочитанное.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class TextPortions {
    /**
     * PARAGRAPH_THRESHOLD экспортёра
     */
    public static final int THRESHOLD = 100;
    /**
     * PARAGRAPH_THRESHOLD_WARN экспортёра
     */
    public static final int THRESHOLD_WARN = 330;
    /**
     * Закрывающие кавычки и скобки — к предложению: «…!» », «…?”», «….)»
     */
    private static final String[] CLOSING = {"\"", "'", ")", "»", "”", "“"};

    private TextPortions() {
    }

    /**
     * Абзацы -> порции (каждая порция — список абзацев; сейчас — ровно один)
     */
    public static List<List<ScreenModel.Paragraph>> of(List<ScreenModel.Paragraph> text) {
        List<List<ScreenModel.Paragraph>> result = new ArrayList<>();
        for (ScreenModel.Paragraph paragraph : text) {
            if (isBlank(paragraph)) {
                continue;
            }
            for (ScreenModel.Paragraph part : split(paragraph)) {
                List<ScreenModel.Paragraph> portion = new ArrayList<>();
                portion.add(part);
                result.add(portion);
            }
        }
        return result;
    }

    private static boolean isBlank(ScreenModel.Paragraph paragraph) {
        for (ScreenModel.TextRun run : paragraph.m_runs) {
            for (int i = 0; i < run.m_text.length(); i++) {
                char c = run.m_text.charAt(i);
                if (c != ' ' && c != '\t') {
                    return false;
                }
            }
        }
        return true;
    }

    private static int codePoints(String s, int from, int to) {
        return s.codePointCount(from, to);
    }

    /**
     * Абзац -> порции по предложениям (SENTENCE_PATTERN NLB — [^.?!]+ [.?!]+ (\")?); ссылки
     * абзаца сохраняются
     */
    static List<ScreenModel.Paragraph> split(ScreenModel.Paragraph paragraph) {
        String all = paragraph.getText();
        // концы предложений
        List<Integer> ends = new ArrayList<>();
        int i = 0;
        while (i < all.length()) {
            char c = all.charAt(i);
            if (c == '.' || c == '?' || c == '!') {
                while (i < all.length() && (all.charAt(i) == '.' || all.charAt(i) == '?' || all.charAt(i) == '!')) {
                    i++;
                }
                boolean more = true;
                while (more && i < all.length()) {
                    more = false;
                    for (String q : CLOSING) {
                        if (all.startsWith(q, i)) {
                            i += q.length();
                            more = true;
                            break;
                        }
                    }
                }
                ends.add(i);
            } else {
                i++;
            }
        }
        if (ends.isEmpty() || ends.get(ends.size() - 1) != all.length()) {
            ends.add(all.length());
        }
        // порции по алгоритму экспортёра: [начало, конец)
        List<int[]> chunks = new ArrayList<>();
        int start = 0;
        int prevStart = 0;
        int prevEnd = 0;
        boolean hasPrev = false;
        for (int e : ends) {
            if (codePoints(all, start, e) > THRESHOLD) {
                if (hasPrev) {
                    chunks.add(new int[]{prevStart, prevEnd});
                }
                prevStart = start;
                prevEnd = e;
                hasPrev = true;
                start = e;
            }
        }
        if (hasPrev && codePoints(all, prevStart, all.length()) <= THRESHOLD_WARN) {
            chunks.add(new int[]{prevStart, all.length()});
        } else {
            if (hasPrev) {
                chunks.add(new int[]{prevStart, prevEnd});
            }
            if (start < all.length()) {
                chunks.add(new int[]{start, all.length()});
            }
        }
        List<ScreenModel.Paragraph> result = new ArrayList<>();
        if (chunks.size() <= 1) {
            result.add(paragraph);
            return result;
        }
        // разрезать фрагменты абзаца по границам порций
        for (int[] chunk : chunks) {
            ScreenModel.Paragraph part = paragraph.copyWithoutRuns();
            int pos = 0;
            for (ScreenModel.TextRun run : paragraph.m_runs) {
                int rb = pos;
                int re = pos + run.m_text.length();
                pos = re;
                int from = Math.max(rb, chunk[0]);
                int to = Math.min(re, chunk[1]);
                if (from < to) {
                    part.m_runs.add(run.withText(run.m_text.substring(from - rb, to - rb)));
                }
            }
            // пробелы на стыке порций — прочь (paginator обрезает их у порции)
            while (!part.m_runs.isEmpty()) {
                String text = part.m_runs.get(0).m_text;
                int b = 0;
                while (b < text.length() && (text.charAt(b) == ' ' || text.charAt(b) == '\t')) {
                    b++;
                }
                if (b < text.length()) {
                    part.m_runs.set(0, part.m_runs.get(0).withText(text.substring(b)));
                    break;
                }
                part.m_runs.remove(0);
            }
            if (!part.m_runs.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }
}
