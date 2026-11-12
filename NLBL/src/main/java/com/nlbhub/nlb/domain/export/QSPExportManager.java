/**
 * @(#)QSPExportManager.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2014 Anton P. Kolosov
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
 * Copyright (c) 2013 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.List;

/**
 * The QSPExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/4/13
 */
public class QSPExportManager extends TextExportManager {
    // Windows line separator should be used for QSP text format,
    // not System.getProperty("line.separator"), otherwise txt2gam utility processing fails
    private static final String LINE_SEPARATOR = "\r\n";
    /**
     * Конец локации. txt2gam (QSPFoundation) и редакторы QSP ищут конец по префиксу «--» (вне
     * кавычек) — стандартная запись «--- имя ---------». Раньше экспорт писал «-», и txt2gam
     * не находил ни одного конца: вся игра становилась одной локацией. @qsp/converters понимает
     * оба варианта.
     */
    private static final String LOC_END = "---------------------------------";

    /**
     * Комментарий QSP. Кавычки и фигурные скобки внутри «!» QSP (и txt2gam) читает как начало строки
     * или блока, и они тянутся через переводы строк: апостроф в названии страницы или ссылки
     * («Beobras' room») проглатывал следующие локации (в «Острове» txt2gam терял 10 из 422). В
     * комментарии они заменяются похожими знаками, перевод строки — пробелом.
     */
    private static String comment(String text) {
        String safe = text == null ? EMPTY_STRING : text.replace("'", "’").replace("\"", "”")
                .replace("{", "(").replace("}", ")").replace("\r", " ").replace("\n", " ");
        return "! " + safe + LINE_SEPARATOR;
    }

    public QSPExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    /** Виды действий игрока (биты 1, 2, 4, 8 — как PlayerContext.COUNT_*) */
    private static final String[] COUNT_TYPES = {"inv", "act", "use", "walk"};
    /** Метка PDSC до сборки текста: объект задан именем, его id известен в generateNLBText */
    private static final String PDSC_MARK = "\u0001nlb_pdsc:";

    /** Место «инвентарь» в $nlb_in */
    private static final String INVENTORY = "nlb_inv";

    /**
     * Начало игры и служебные локации объектов. Объекты QSP не знает (у него есть только
     * инвентарь — список подписей), поэтому место каждого объекта книги хранится в строковом
     * массиве $nlb_in[id] (id страницы или объекта-контейнера, 'nlb_inv' — инвентарь, '' — нигде),
     * а объекты сцены выводятся в тексте страницы ссылками EXEC (USEHTML).
     */
    @Override
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        m_gameInvText = qspString(nlbBuildingBlocks.getGameInvText());
        m_gameNouseText = qspString(nlbBuildingBlocks.getGameNouseText());
        final List<PageBuildingBlocks> pages = nlbBuildingBlocks.getPagesBuildingBlocks();
        final List<ObjBuildingBlocks> objs = orderedObjects(nlbBuildingBlocks);
        StringBuilder sb = new StringBuilder();
        sb.append("! Generated with Non-Linear Book Builder, http://nlbhub.com").append(LINE_SEPARATOR);
        sb.append("! Начало игры: служебные переменные и места объектов, затем стартовая страница").append(LINE_SEPARATOR);
        sb.append("#nlb_start").append(LINE_SEPARATOR);
        sb.append("USEHTML = 1").append(LINE_SEPARATOR);
        sb.append("$ONOBJSEL = 'nlb_onobjsel'").append(LINE_SEPARATOR);
        sb.append("$nlb_sel = ''").append(LINE_SEPARATOR);
        sb.append("nlb_objs_n = ").append(objs.size()).append(LINE_SEPARATOR);
        for (int i = 0; i < objs.size(); i++) {
            ObjBuildingBlocks obj = objs.get(i);
            sb.append("$nlb_objs[").append(i).append("] = '").append(obj.getObjId()).append("'").append(LINE_SEPARATOR);
            sb.append("$nlb_in['").append(obj.getObjId()).append("'] = '").append(obj.getContainerId()).append("'").append(LINE_SEPARATOR);
            sb.append("nlb_isobj['").append(obj.getObjId()).append("'] = 1").append(LINE_SEPARATOR);
            if (StringHelper.notEmpty(obj.getObjDefaultTagVariable())) {
                sb.append("$nlb_tag['").append(obj.getObjId()).append("'] = '").append(qspString(obj.getObjDefaultTagVariable()))
                        .append("'").append(LINE_SEPARATOR);
            }
            if (!obj.getObjSounds().isEmpty()) {
                sb.append("nlb_hassnd['").append(obj.getObjId()).append("'] = 1").append(LINE_SEPARATOR);
            }
            if (!obj.isVisibleInInventory()) {
                // предмет без подписи и картинки лежит в инвентаре невидимым (как в плеере и INSTEAD)
                sb.append("nlb_invhide['").append(obj.getObjId()).append("'] = 1").append(LINE_SEPARATOR);
            }
            if (hasInventoryImage(obj)) {
                sb.append("nlb_invimg['").append(obj.getObjId()).append("'] = 1").append(LINE_SEPARATOR);
            }
        }
        // теги по умолчанию (var { tag = … } в INSTEAD): GETTAG без явного TAG возвращает их
        for (PageBuildingBlocks page : pages) {
            if (StringHelper.notEmpty(page.getPageDefaultTag())) {
                sb.append("$nlb_tag['").append(page.getPageId()).append("'] = '").append(qspString(page.getPageDefaultTag()))
                        .append("'").append(LINE_SEPARATOR);
            }
            if (!page.getPageSounds().isEmpty()) {
                sb.append("nlb_hassnd['").append(page.getPageId()).append("'] = 1").append(LINE_SEPARATOR);
            }
        }
        if (!pages.isEmpty()) {
            sb.append("GOTO '").append(pages.get(0).getPageId()).append("'").append(LINE_SEPARATOR);
        }
        sb.append(LOC_END).append(LINE_SEPARATOR);
        sb.append(SERVICE_LOCATIONS.replace("\n", LINE_SEPARATOR)
                .replace("%GAME_INV%", m_gameInvText).replace("%NLB_INV%", INVENTORY)
                .replace("%COUNT%", ACTION_COUNT_VAR));
        // Конец хода (из #id_turn): callback-объекты, если игрок с прошлого хода что-то сделал
        // (update_action_count в INSTEAD, PlayerEngine; как в jsIQ2 и AXMA) — их действие без
        // «взять». nlb_cnt_prev — сумма счётчиков на прошлом ходу (до callback-ов: их GOTO
        // уводит со страницы, и следующий ход не должен повторить их ещё раз)
        sb.append("#nlb_turn").append(LINE_SEPARATOR);
        sb.append("nlb_diff = ").append(countSum(0)).append(LINE_SEPARATOR);
        sb.append("IF nlb_diff > nlb_cnt_prev:").append(LINE_SEPARATOR);
        sb.append("    nlb_cnt_prev = nlb_diff").append(LINE_SEPARATOR);
        for (ObjBuildingBlocks obj : objs) {
            if (obj.isCallback()) {
                boolean constrained = StringHelper.notEmpty(obj.getObjConstraint());
                sb.append("    ").append(constrained ? "IF " + obj.getObjConstraint() + ": " : "")
                        .append("GOSUB 'nlb_o_").append(obj.getObjId()).append("_acta'").append(LINE_SEPARATOR);
            }
        }
        sb.append("END").append(LINE_SEPARATOR);
        sb.append("nlb_cnt_prev = nlb_diff").append(LINE_SEPARATOR);
        sb.append(LOC_END).append(LINE_SEPARATOR);
        sb.append(achievementLocation(nlbBuildingBlocks));
        return sb.toString();
    }

    /**
     * Достижения (PlayerContext.achieve, nlb.lua setAchievement): ACHIEVE — GOSUB 'nlb_achieve',
     * имя, id модификации. Каждое место ACHIEVE засчитывается один раз (nlb_achid['имя|id']),
     * число мест — nlb_ach['имя'] (его возвращает ACHIEVED), порог — nlb_achmax['имя'] (ACHMAX;
     * без него — первое же место). Набран порог — объявление строкой (один раз, nlb_achgot);
     * затем perfect game: все прочие достижения книги набрали свой порог (без ACHMAX — 1). Между
     * прохождениями QSP ничего не сохраняет — достижения живут, пока идёт игра
     */
    private String achievementLocation(NLBBuildingBlocks nlbBuildingBlocks) {
        final String ls = LINE_SEPARATOR;
        final String announce = qspString(achievementAnnouncement(nlbBuildingBlocks.getLang()));
        StringBuilder sb = new StringBuilder();
        sb.append("#nlb_achieve").append(ls);
        sb.append("IF nlb_achid[$ARGS[0] + '|' + $ARGS[1]] = 0:").append(ls);
        sb.append("    nlb_achid[$ARGS[0] + '|' + $ARGS[1]] = 1").append(ls);
        sb.append("    nlb_ach[$ARGS[0]] = nlb_ach[$ARGS[0]] + 1").append(ls);
        sb.append("END").append(ls);
        sb.append("IF nlb_achgot[$ARGS[0]] = 0 AND (nlb_achmax[$ARGS[0]] = 0 OR nlb_ach[$ARGS[0]] >= nlb_achmax[$ARGS[0]]):").append(ls);
        sb.append("    nlb_achgot[$ARGS[0]] = 1").append(ls);
        sb.append("    '").append(announce).append("' + $ARGS[0]").append(ls);
        sb.append("END").append(ls);
        String perfect = nlbBuildingBlocks.getAchievementNamePerfectGame();
        if (StringHelper.notEmpty(perfect)) {
            sb.append("nlb_perf = 1").append(ls);
            for (String name : nlbBuildingBlocks.getAchievements()) {
                if (!name.equals(perfect)) {
                    sb.append("IF nlb_ach['").append(name).append("'] < MAX(nlb_achmax['").append(name).append("'], 1): nlb_perf = 0").append(ls);
                }
            }
            sb.append("IF nlb_perf = 1:").append(ls);
            sb.append("    IF nlb_achid['").append(perfect).append("|' + $ARGS[1]] = 0:").append(ls);
            sb.append("        nlb_achid['").append(perfect).append("|' + $ARGS[1]] = 1").append(ls);
            sb.append("        nlb_ach['").append(perfect).append("'] = nlb_ach['").append(perfect).append("'] + 1").append(ls);
            sb.append("    END").append(ls);
            sb.append("    IF nlb_achgot['").append(perfect).append("'] = 0:").append(ls);
            sb.append("        nlb_achgot['").append(perfect).append("'] = 1").append(ls);
            sb.append("        '").append(announce).append(perfect).append("'").append(ls);
            sb.append("    END").append(ls);
            sb.append("END").append(ls);
        }
        sb.append(LOC_END).append(ls);
        return sb.toString();
    }

    /**
     * Служебные локации (QSP 5.8+: LOOP, LOCAL). nlb_show_in — объекты в контейнере (порядок —
     * порядок объектов в книге); nlb_act — щелчок по объекту в тексте: действие (act) или, если
     * выбран предмет инвентаря, применение; nlb_refresh — снова показать страницу после действия:
     * ответ ($MAINTXT) — после текста страницы, действия-ссылки пересобираются; nlb_onobjsel —
     * выбор предмета в инвентаре: первый щелчок — inv (применение к себе или game.inv) и выбор
     * для применения, щелчок по другому предмету — применение, по тому же — отмена выбора;
     * nlb_use — применение ARGS[0] к ARGS[1], без ответа — nouse; nlb_place — перенести объект
     * ARGS[0] в ARGS[1] (с инвентарём QSP); nlb_inv_sync — подписи предметов с переменными
     * («Жизнь: 100»): в QSP подпись статична, поэтому в начале каждого хода изменившаяся подпись
     * заменяется на том же месте списка.
     */
    private static final String SERVICE_LOCATIONS = String.join("\n",
            "#nlb_show_in",
            "LOCAL nlb_i",
            "nlb_i = 0",
            "LOOP WHILE nlb_i < nlb_objs_n:",
            "    IF $nlb_in[$nlb_objs[nlb_i]] = $ARGS[0]: GOSUB 'nlb_o_' + $nlb_objs[nlb_i] + '_dsc'",
            "    nlb_i = nlb_i + 1",
            "END",
            LOC_END,
            "#nlb_act",
            "*CLR",
            "IF $nlb_sel <> '':",
            "    GOSUB 'nlb_use', $nlb_sel, $ARGS[0]",
            "ELSE",
            "    %COUNT% = %COUNT% + 1",
            "    nlb_cnt_act = nlb_cnt_act + 1",
            "    GOSUB 'nlb_o_' + $ARGS[0] + '_act'",
            "END",
            "GOSUB 'nlb_refresh'",
            LOC_END,
            "#nlb_refresh",
            "nlb_tick = 1",
            "$nlb_reply = $MAINTXT",
            "*CLR",
            "GOSUB $CURLOC + '_show'",
            "IF $nlb_reply <> '': *P $nlb_reply",
            "GOSUB $CURLOC + '_turn'",
            LOC_END,
            "#nlb_onobjsel",
            "$nlb_o = $nlb_byname[$SELOBJ]",
            "IF $nlb_o = '': EXIT",
            "*CLR",
            "IF $nlb_sel = '':",
            "    %COUNT% = %COUNT% + 1",
            "    nlb_cnt_inv = nlb_cnt_inv + 1",
            "    nlb_printed = 0",
            "    GOSUB 'nlb_o_' + $nlb_o + '_use', $nlb_o",
            "    IF nlb_printed = 0: '%GAME_INV%'",
            "    $nlb_sel = $nlb_o",
            "ELSEIF $nlb_sel = $nlb_o:",
            "    $nlb_sel = ''",
            "    UNSEL",
            "ELSE",
            "    GOSUB 'nlb_use', $nlb_sel, $nlb_o",
            "END",
            "GOSUB 'nlb_refresh'",
            LOC_END,
            "#nlb_use",
            "%COUNT% = %COUNT% + 1",
            "nlb_cnt_use = nlb_cnt_use + 1",
            "nlb_printed = 0",
            "GOSUB 'nlb_o_' + $ARGS[0] + '_use', $ARGS[1]",
            "IF nlb_printed = 0: GOSUB 'nlb_o_' + $ARGS[0] + '_nouse'",
            "$nlb_sel = ''",
            "UNSEL",
            LOC_END,
            "#nlb_place",
            "IF $nlb_in[$ARGS[0]] = '%NLB_INV%' AND $ARGS[1] <> '%NLB_INV%' AND nlb_invhide[$ARGS[0]] = 0: DELOBJ $nlb_shown[$ARGS[0]]",
            "IF $nlb_in[$ARGS[0]] <> '%NLB_INV%' AND $ARGS[1] = '%NLB_INV%' AND nlb_invhide[$ARGS[0]] = 0:",
            "    $nlb_shown[$ARGS[0]] = $FUNC('nlb_o_' + $ARGS[0] + '_disp')",
            "    $nlb_byname[$nlb_shown[$ARGS[0]]] = $ARGS[0]",
            "    IF nlb_invimg[$ARGS[0]] = 1:",
            "        ADDOBJ $nlb_shown[$ARGS[0]], $FUNC('nlb_img_' + $ARGS[0])",
            "    ELSE",
            "        ADDOBJ $nlb_shown[$ARGS[0]]",
            "    END",
            "END",
            "$nlb_in[$ARGS[0]] = $ARGS[1]",
            LOC_END,
            // Списки (nlb.lua: push/pop — начало, inject/eject — конец): элементы — строки
            // $nlb_li['имя,i'] (0 — начало), длина — nlb_ln['имя']; объект или страница — id,
            // число — строкой ($STR), при снятии в числовую переменную — VAL
            "#nlb_lpush",
            "LOCAL nlb_k",
            "nlb_k = nlb_ln[$ARGS[0]]",
            "LOOP WHILE nlb_k > 0:",
            "    $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)] = $nlb_li[$ARGS[0] + ',' + $STR(nlb_k - 1)]",
            "    nlb_k = nlb_k - 1",
            "END",
            "$nlb_li[$ARGS[0] + ',0'] = $ARGS[1]",
            "nlb_ln[$ARGS[0]] = nlb_ln[$ARGS[0]] + 1",
            LOC_END,
            "#nlb_linject",
            "$nlb_li[$ARGS[0] + ',' + $STR(nlb_ln[$ARGS[0]])] = $ARGS[1]",
            "nlb_ln[$ARGS[0]] = nlb_ln[$ARGS[0]] + 1",
            LOC_END,
            "#nlb_lpop",
            "LOCAL nlb_k",
            "$RESULT = ''",
            "IF nlb_ln[$ARGS[0]] > 0:",
            "    $RESULT = $nlb_li[$ARGS[0] + ',0']",
            "    nlb_k = 1",
            "    LOOP WHILE nlb_k < nlb_ln[$ARGS[0]]:",
            "        $nlb_li[$ARGS[0] + ',' + $STR(nlb_k - 1)] = $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)]",
            "        nlb_k = nlb_k + 1",
            "    END",
            "    nlb_ln[$ARGS[0]] = nlb_ln[$ARGS[0]] - 1",
            "END",
            LOC_END,
            "#nlb_leject",
            "$RESULT = ''",
            "IF nlb_ln[$ARGS[0]] > 0:",
            "    nlb_ln[$ARGS[0]] = nlb_ln[$ARGS[0]] - 1",
            "    $RESULT = $nlb_li[$ARGS[0] + ',' + $STR(nlb_ln[$ARGS[0]])]",
            "END",
            LOC_END,
            // nlb:rmv — первое вхождение
            "#nlb_lrmv",
            "LOCAL nlb_k, nlb_f",
            "nlb_k = 0",
            "nlb_f = -1",
            "LOOP WHILE nlb_k < nlb_ln[$ARGS[0]] AND nlb_f < 0:",
            "    IF $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)] = $ARGS[1]: nlb_f = nlb_k",
            "    nlb_k = nlb_k + 1",
            "END",
            "IF nlb_f >= 0:",
            "    nlb_k = nlb_f + 1",
            "    LOOP WHILE nlb_k < nlb_ln[$ARGS[0]]:",
            "        $nlb_li[$ARGS[0] + ',' + $STR(nlb_k - 1)] = $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)]",
            "        nlb_k = nlb_k + 1",
            "    END",
            "    nlb_ln[$ARGS[0]] = nlb_ln[$ARGS[0]] - 1",
            "END",
            LOC_END,
            // Фишер — Йейтс
            "#nlb_lshuffle",
            "LOCAL nlb_k, nlb_j, $nlb_v",
            "nlb_k = nlb_ln[$ARGS[0]] - 1",
            "LOOP WHILE nlb_k > 0:",
            "    nlb_j = RAND(0, nlb_k)",
            "    $nlb_v = $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)]",
            "    $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)] = $nlb_li[$ARGS[0] + ',' + $STR(nlb_j)]",
            "    $nlb_li[$ARGS[0] + ',' + $STR(nlb_j)] = $nlb_v",
            "    nlb_k = nlb_k - 1",
            "END",
            LOC_END,
            // OBJS: содержимое контейнера ARGS[1] — в начало списка ARGS[0] (push каждого — обратный порядок)
            "#nlb_lobjs",
            "LOCAL nlb_i",
            "nlb_i = 0",
            "LOOP WHILE nlb_i < nlb_objs_n:",
            "    IF $nlb_in[$nlb_objs[nlb_i]] = $ARGS[1]: GOSUB 'nlb_lpush', $ARGS[0], $nlb_objs[nlb_i]",
            "    nlb_i = nlb_i + 1",
            "END",
            LOC_END,
            // ADDALL: элементы списка ARGS[0] — в контейнер ARGS[1] или в начало списка ARGS[2]
            "#nlb_laddall",
            "LOCAL nlb_k, $nlb_v",
            "nlb_k = 0",
            "LOOP WHILE nlb_k < nlb_ln[$ARGS[0]]:",
            "    $nlb_v = $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)]",
            "    IF $ARGS[2] <> '':",
            "        GOSUB 'nlb_lpush', $ARGS[2], $nlb_v",
            "    ELSEIF nlb_isobj[$nlb_v] = 1:",
            "        GOSUB 'nlb_place', $nlb_v, $ARGS[1]",
            "    END",
            "    nlb_k = nlb_k + 1",
            "END",
            LOC_END,
            // CLEAR, CLRINV: всё содержимое контейнера (страницы, инвентаря) — никуда
            "#nlb_clear",
            "LOCAL nlb_i",
            "nlb_i = 0",
            "LOOP WHILE nlb_i < nlb_objs_n:",
            "    IF $nlb_in[$nlb_objs[nlb_i]] = $ARGS[0]: GOSUB 'nlb_place', $nlb_objs[nlb_i], ''",
            "    nlb_i = nlb_i + 1",
            "END",
            LOC_END,
            // Музыка (set_music в INSTEAD): тот же файл не перезапускается, '' — остановить (void)
            "#nlb_music",
            "IF $ARGS[0] <> $nlb_music:",
            "    IF $nlb_music <> '': CLOSE $nlb_music",
            "    $nlb_music = $ARGS[0]",
            "    IF $nlb_music <> '': PLAY $nlb_music",
            "END",
            LOC_END,
            // картинка в тексте: путь -> <img> (пустой путь — ничего)
            // (служебная страница, которую сейчас покинут, — nlb_leave — картинок не показывает)
            "#nlb_imgtag",
            "IF $ARGS[0] <> '' AND nlb_leave = 0: $RESULT = '<img src=\"' + $ARGS[0] + '\">'",
            LOC_END,
            "#nlb_inv_sync",
            "LOCAL nlb_i, nlb_j, $nlb_n, $nlb_id",
            "nlb_i = 0",
            "LOOP WHILE nlb_i < nlb_objs_n:",
            "    $nlb_id = $nlb_objs[nlb_i]",
            "    IF $nlb_in[$nlb_id] = '%NLB_INV%' AND nlb_invhide[$nlb_id] = 0:",
            "        $nlb_n = $FUNC('nlb_o_' + $nlb_id + '_disp')",
            "        IF $nlb_n <> $nlb_shown[$nlb_id]:",
            "            nlb_j = 1",
            "            LOOP WHILE nlb_j <= COUNTOBJ AND $GETOBJ(nlb_j) <> $nlb_shown[$nlb_id]: nlb_j = nlb_j + 1",
            "            IF nlb_j <= COUNTOBJ: KILLOBJ nlb_j",
            "            IF nlb_invimg[$nlb_id] = 1:",
            "                ADDOBJ $nlb_n, $FUNC('nlb_img_' + $nlb_id), nlb_j",
            "            ELSE",
            "                ADDOBJ $nlb_n, '', nlb_j",
            "            END",
            "            $nlb_byname[$nlb_n] = $nlb_id",
            "            $nlb_shown[$nlb_id] = $nlb_n",
            "        END",
            "    END",
            "    nlb_i = nlb_i + 1",
            "END",
            LOC_END,
            "");

    private String m_gameInvText = EMPTY_STRING;
    private String m_gameNouseText = EMPTY_STRING;

    /**
     * Объект операции: известный — 'id', из переменной (ссылка на объект или страницу; строковая,
     * см. inferObjectReferences) — $имя, без них — сам («s» в INSTEAD)
     */
    private String objRef(String objId, String objVar) {
        if (objId != null) {
            return "'" + objId + "'";
        }
        if (StringHelper.notEmpty(objVar)) {
            return objVar.startsWith("$") ? objVar : "$" + objVar;
        }
        return "'" + selfId() + "'";
    }

    /** Элемент списка — строка: объект — id, переменная — значение (число — $STR), выражение — $STR(…) */
    private String listValue(String objectId, String objectVar) {
        if (objectId != null) {
            return "'" + objectId + "'";
        }
        String value = objectVar.trim();
        if (isPlainName(value)) {
            return variableDataType(value) == Variable.DataType.STRING ? "$" + value : "$STR(" + value + ")";
        }
        if (value.startsWith("'") || value.startsWith("\"") || value.startsWith("$")) {
            return value;
        }
        return "$STR(" + value + ")";
    }

    /** POP/EJECT: в строковую переменную — как есть, в числовую — VAL (из пустого списка — '' и 0) */
    private String listResult(String variableName, String location, String listVariableName) {
        String call = "$FUNC('" + location + "', '" + listVariableName + "')";
        if (variableDataType(variableName) == Variable.DataType.STRING) {
            return "$" + variableName + " = " + call + LINE_SEPARATOR;
        }
        return variableName + " = VAL(" + call + ")" + LINE_SEPARATOR;
    }

    /** Текст внутри строки QSP в апострофах (HTML-экранирование — как у текста книги) */
    private static String qspString(String text) {
        if (StringHelper.isEmpty(text)) {
            return EMPTY_STRING;
        }
        return escapeHtml(text).replace("'", "''");
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Объект — служебные локации: _dsc (описание в сцене со ссылкой EXEC и содержимое
     * объекта-контейнера; условие объекта скрывает и его содержимое), _act (действие: текст
     * действия, модификации, общий объект; переносимый — в инвентарь), _acttext, _mods,
     * _use (применения к ARGS[0]: тексты успеха/неудачи, модификации), _nouse.
     */
    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        final String id = objBlocks.getObjId();
        final String prefix = "#nlb_o_" + id;
        final String common = objBlocks.getCommonObjId();
        StringBuilder sb = new StringBuilder();
        sb.append(objBlocks.getObjComment());
        sb.append(prefix).append("_dsc").append(LINE_SEPARATOR);
        final boolean constrained = StringHelper.notEmpty(objBlocks.getObjConstraint());
        if (constrained) {
            sb.append("IF ").append(objBlocks.getObjConstraint()).append(":").append(LINE_SEPARATOR);
        }
        sb.append("GOSUB 'nlb_o_").append(id).append("_text'").append(LINE_SEPARATOR);
        sb.append("GOSUB 'nlb_show_in', '").append(id).append("'").append(LINE_SEPARATOR);
        if (constrained) {
            sb.append("END").append(LINE_SEPARATOR);
        }
        sb.append(LOC_END).append(LINE_SEPARATOR);

        // описание объекта: _text — вывести (PDSC), _textv — значение (DSC). Графический объект
        // (VN) щёлкают по картинке, а картинок здесь нет: если у него есть действие, ссылкой
        // становится его текст (без ссылки EXEC в нём), а без текста — подпись (выбор навыков в
        // «Варваре»); подпись без действия — просто текст (надпись-картинка)
        String text = objBlocks.getObjText();
        if (text.contains(OBJ_LABEL_MARK)) {
            // подпись для пустой метки {}: disp, иначе первая строка текста действия (карта во
            // «Фронтире»), иначе имя — как PlayerEngine.sceneLabel
            String objLabel = StringHelper.notEmpty(objBlocks.getObjDisp()) ? objBlocks.getObjDisp() : firstLine(objBlocks.getObjActStart());
            if (objLabel.isEmpty()) {
                objLabel = StringHelper.notEmpty(objBlocks.getObjName()) ? qspString(objBlocks.getObjName()) : id;
            }
            text = text.replace(OBJ_LABEL_MARK, objLabel);
        }
        if (objBlocks.isGraphical()) {
            String label = text.isEmpty() ? objBlocks.getObjDisp()
                    : text.substring(1, text.length() - LINE_SEPARATOR.length() - 1);  // без '…'
            if (objBlocks.hasAction() && !text.contains("EXEC:") && StringHelper.notEmpty(label)) {
                text = "'" + objectLink(id, label) + "'" + LINE_SEPARATOR;
            } else if (text.isEmpty() && StringHelper.notEmpty(label)) {
                text = "'" + label + "'" + LINE_SEPARATOR;
            }
        }
        if (text.isEmpty() && objBlocks.isImageInScene() && !objBlocks.isGraphical()) {
            // объект без описания, но с картинкой (фон боя, рамка карты во «Фронтире») — одна
            // картинка, как в плеере; с действием — ссылкой
            String image = "<<$FUNC(''nlb_imgtag'', $FUNC(''nlb_img_" + id + "''))>>";
            text = "'" + (objBlocks.hasAction() ? objectLink(id, image) : image) + "'" + LINE_SEPARATOR;
        }
        sb.append(prefix).append("_text").append(LINE_SEPARATOR);
        sb.append(text);
        sb.append(LOC_END).append(LINE_SEPARATOR);
        sb.append(prefix).append("_textv").append(LINE_SEPARATOR);
        sb.append("$RESULT = ").append(text.isEmpty() ? "''" + LINE_SEPARATOR : text);
        sb.append(LOC_END).append(LINE_SEPARATOR);

        sb.append(prefix).append("_disp").append(LINE_SEPARATOR);
        // подпись в инвентаре; без неё — имя объекта (как nam в INSTEAD), без имени — id
        String disp = StringHelper.notEmpty(objBlocks.getObjDisp()) ? objBlocks.getObjDisp()
                : StringHelper.notEmpty(objBlocks.getObjName()) ? qspString(objBlocks.getObjName()) : id;
        sb.append("$RESULT = '").append(disp).append("'").append(LINE_SEPARATOR);
        sb.append(LOC_END).append(LINE_SEPARATOR);

        sb.append(prefix).append("_acttext").append(LINE_SEPARATOR);
        sb.append(objBlocks.getObjActStart());
        sb.append(LOC_END).append(LINE_SEPARATOR);

        sb.append(prefix).append("_mods").append(LINE_SEPARATOR);
        sb.append(objBlocks.getObjModifications());
        sb.append(objBlocks.getObjVariable());
        sb.append(LOC_END).append(LINE_SEPARATOR);

        // _acta — действие без «взять» (так его вызывают и callback-объекты в конце хода)
        sb.append(prefix).append("_acta").append(LINE_SEPARATOR);
        sb.append("GOSUB 'nlb_o_").append(id).append("_acttext'").append(LINE_SEPARATOR);
        sb.append("GOSUB 'nlb_o_").append(id).append("_mods'").append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(common)) {
            // общий объект (objActA): его текст — если у объекта своего нет, затем его модификации
            if (StringHelper.isEmpty(objBlocks.getObjActStart())) {
                sb.append("GOSUB 'nlb_o_").append(common).append("_acttext'").append(LINE_SEPARATOR);
            }
            sb.append("GOSUB 'nlb_o_").append(common).append("_mods'").append(LINE_SEPARATOR);
        }
        sb.append(LOC_END).append(LINE_SEPARATOR);
        sb.append(prefix).append("_act").append(LINE_SEPARATOR);
        sb.append("GOSUB 'nlb_o_").append(id).append("_acta'").append(LINE_SEPARATOR);
        if (objBlocks.isTakable()) {
            // decorateObjTak: взять общий объект, если он переносимый, иначе этот
            String toTake = StringHelper.notEmpty(common) && isTakable(common) ? common : id;
            sb.append("GOSUB 'nlb_place', '").append(toTake).append("', '").append(INVENTORY).append("'").append(LINE_SEPARATOR);
        }
        sb.append(LOC_END).append(LINE_SEPARATOR);

        sb.append(prefix).append("_use").append(LINE_SEPARATOR);
        for (UseBuildingBlocks use : objBlocks.getUseBuildingBlocks()) {
            final boolean hasSuccess = StringHelper.notEmpty(use.getUseSuccessText());
            final boolean useConstrained = StringHelper.notEmpty(use.getUseConstraint());
            sb.append("IF $ARGS[0] = '").append(use.getUseTarget()).append("':").append(LINE_SEPARATOR);
            if (useConstrained) {
                sb.append("IF ").append(use.getUseConstraint()).append(":").append(LINE_SEPARATOR);
            }
            if (hasSuccess) {
                sb.append("'").append(use.getUseSuccessText()).append("'").append(LINE_SEPARATOR);
                sb.append("nlb_printed = 1").append(LINE_SEPARATOR);
            }
            sb.append(use.getUseModifications());
            sb.append(use.getUseVariable());
            if (useConstrained) {
                if (hasSuccess && StringHelper.notEmpty(use.getUseFailureText())) {
                    sb.append("ELSE").append(LINE_SEPARATOR);
                    sb.append("'").append(use.getUseFailureText()).append("'").append(LINE_SEPARATOR);
                    sb.append("nlb_printed = 1").append(LINE_SEPARATOR);
                }
                sb.append("END").append(LINE_SEPARATOR);
            }
            sb.append("END").append(LINE_SEPARATOR);
        }
        if (StringHelper.notEmpty(common)) {
            sb.append("IF nlb_printed = 0: GOSUB 'nlb_o_").append(common).append("_use', $ARGS[0]").append(LINE_SEPARATOR);
        }
        sb.append(LOC_END).append(LINE_SEPARATOR);

        sb.append(prefix).append("_nouse").append(LINE_SEPARATOR);
        String nouse = objBlocks.getObjNouse();
        sb.append("'").append(StringHelper.notEmpty(nouse) ? nouse : m_gameNouseText).append("'").append(LINE_SEPARATOR);
        sb.append(LOC_END).append(LINE_SEPARATOR);
        if (!objBlocks.isGraphical()) {
            sb.append(imageLocation(id, objBlocks.getObjImages()));
        }
        sb.append(soundLocation(id, objBlocks.getObjSounds(), false));
        return sb.toString();
    }

    /** Картинка предмета в инвентаре (ADDOBJ с картинкой) */
    private static boolean hasInventoryImage(ObjBuildingBlocks obj) {
        return obj.isImageInInventory() && !obj.isGraphical() && !obj.getObjImages().isEmpty();
    }

    private static String qspLiteral(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    /**
     * Картинка страницы или объекта (ModificationInterpreter.selectImage, pic в INSTEAD):
     * #nlb_img_id возвращает путь первой картинки, чьё условие совпало с тегом (без условия —
     * подходит всегда), или ''
     */
    private String imageLocation(String id, List<MediaChoice> images) {
        if (images.isEmpty()) {
            return EMPTY_STRING;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("#nlb_img_").append(id).append(LINE_SEPARATOR);
        boolean first = true;
        boolean chain = false;
        for (MediaChoice image : images) {
            if (image.hasConstraint()) {
                sb.append(first ? "IF " : "ELSEIF ").append("$nlb_tag['").append(id).append("'] = ")
                        .append(qspLiteral(image.getConstraint())).append(":").append(LINE_SEPARATOR);
                sb.append("    $RESULT = ").append(qspLiteral(image.getPath())).append(LINE_SEPARATOR);
                chain = true;
            } else if (chain) {
                sb.append("ELSE").append(LINE_SEPARATOR);
                sb.append("    $RESULT = ").append(qspLiteral(image.getPath())).append(LINE_SEPARATOR);
            } else {
                sb.append("$RESULT = ").append(qspLiteral(image.getPath())).append(LINE_SEPARATOR);
            }
            first = false;
        }
        if (chain) {
            sb.append("END").append(LINE_SEPARATOR);
        }
        sb.append(LOC_END).append(LINE_SEPARATOR);
        return sb.toString();
    }

    /**
     * Звук страницы или объекта (ModificationInterpreter.playSound, snd в INSTEAD): #nlb_snd_id.
     * Без условия — всегда, подряд идущие с условием — цепочка по тегу. Музыка — nlb_music,
     * эффект — PLAY; у страницы эффект — фраза озвучки: в STANDARD при входе звучит одна, первая
     * подходящая (nlb_voice). Вызов — при входе на страницу (после её модификаций) и из SND/SSND/WSND
     */
    private String soundLocation(String id, List<MediaChoice> sounds, boolean page) {
        if (sounds.isEmpty()) {
            return EMPTY_STRING;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("#nlb_snd_").append(id).append(LINE_SEPARATOR);
        if (page && sounds.stream().anyMatch(MediaChoice::isSfx)) {
            sb.append("nlb_voice = 0").append(LINE_SEPARATOR);
        }
        for (List<MediaChoice> step : MediaChoice.soundSteps(sounds)) {
            if (step.size() == 1 && !step.get(0).hasConstraint()) {
                sb.append(soundStatement(step.get(0), page));
                continue;
            }
            boolean first = true;
            for (MediaChoice sound : step) {
                sb.append(first ? "IF " : "ELSEIF ").append("$nlb_tag['").append(id).append("'] = ")
                        .append(qspLiteral(sound.getConstraint())).append(":").append(LINE_SEPARATOR);
                sb.append("    ").append(soundStatement(sound, page));
                first = false;
            }
            sb.append("END").append(LINE_SEPARATOR);
        }
        sb.append(LOC_END).append(LINE_SEPARATOR);
        return sb.toString();
    }

    private static String soundStatement(MediaChoice sound, boolean page) {
        if (sound.isStop()) {
            return "GOSUB 'nlb_music', ''" + LINE_SEPARATOR;
        }
        if (!sound.isSfx()) {
            return "GOSUB 'nlb_music', " + qspLiteral(sound.getPath()) + LINE_SEPARATOR;
        }
        if (page) {
            return "IF nlb_voice = 0: PLAY " + qspLiteral(sound.getPath()) + " & nlb_voice = 1" + LINE_SEPARATOR;
        }
        return "PLAY " + qspLiteral(sound.getPath()) + LINE_SEPARATOR;
    }

    /** SND/SSND/WSND: звук объекта (страницы), если он у неё есть */
    private static String playSoundOf(String idExpression) {
        return "IF nlb_hassnd[" + idExpression + "] = 1: GOSUB 'nlb_snd_' + " + idExpression + LINE_SEPARATOR;
    }

    private boolean isTakable(String objId) {
        Boolean takable = m_takable.get(objId);
        return takable != null && takable;
    }

    /** Переносимость объектов по id (для общих объектов): заполняется до вывода объектов */
    private final java.util.Map<String, Boolean> m_takable = new java.util.HashMap<>();

    @Override
    protected String generateNLBText(final NLBBuildingBlocks nlbBlocks) throws com.nlbhub.nlb.exception.NLBConsistencyException {
        java.util.Map<String, String> idByName = new java.util.HashMap<>();
        for (ObjBuildingBlocks obj : nlbBlocks.getObjsBuildingBlocks()) {
            m_takable.put(obj.getObjId(), obj.isTakable());
            if (StringHelper.notEmpty(obj.getObjName())) {
                idByName.put(obj.getObjName(), obj.getObjId());
            }
        }
        String text = super.generateNLBText(nlbBlocks);
        // PDSC: имя объекта -> вывод его описания
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote(PDSC_MARK) + "(.*?)" + java.util.regex.Pattern.quote(PDSC_MARK)).matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String id = idByName.get(matcher.group(1));
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    id == null ? comment("PDSC: объект " + matcher.group(1) + " не найден").trim() : "GOSUB 'nlb_o_" + id + "_text'"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @Override
    protected String decorateObjName(String name, String id) {
        return name == null ? EMPTY_STRING : name;
    }

    // --- части объекта (ExportManager.createObjBuildingBlocks)

    @Override
    protected String decorateObjLabel(String id) {
        return id;
    }

    @Override
    protected String decorateObjComment(String name) {
        return comment("Object -- " + (name == null ? EMPTY_STRING : name));
    }

    @Override
    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        return dispText == null ? EMPTY_STRING : dispText.replace(LINE_SEPARATOR, " ");
    }

    /**
     * Описание объекта в сцене: часть в {фигурных скобках} — ссылка EXEC на nlb_act (как ссылка
     * объекта в тексте INSTEAD). Текст уже экранирован expandVariables (апострофы, HTML)
     */
    @Override
    protected String decorateObjText(String objId, String objName, boolean suppressDsc, String objText, boolean imageEnabled, boolean isGraphicalObj) {
        if (suppressDsc || StringHelper.isEmpty(objText)) {
            return EMPTY_STRING;
        }
        // картинка объекта в сцене — в начале ссылки, как imgv в INSTEAD ({картинка метка})
        String image = imageEnabled && !isGraphicalObj ? "<<$FUNC(''nlb_imgtag'', $FUNC(''nlb_img_" + objId + "''))>>" : EMPTY_STRING;
        if (EMPTY_LINK.matcher(objText).find()) {
            return "'" + expandEmptyLink(objId, objText, image) + "'" + LINE_SEPARATOR;
        }
        String text = objText.replaceAll("\\{([^}]*)\\}",
                java.util.regex.Matcher.quoteReplacement(objectLink(objId, image + "\u0002")).replace("\u0002", "$1"));
        return "'" + text + "'" + LINE_SEPARATOR;
    }

    /** Перевод строки в тексте QSP (USEHTML) — '^' INSTEAD */
    private static final String BR = "<br>";
    /** Пустая метка {} в описании объекта */
    private static final java.util.regex.Pattern EMPTY_LINK = java.util.regex.Pattern.compile("\\{\\s*\\}");
    /** Подпись ссылки, которую заменит подпись объекта (generateObjText) */
    private static final String OBJ_LABEL_MARK = "\u0004";

    private static String trimTrailingBreaks(String text) {
        String result = text;
        while (true) {
            String stripped = result.stripTrailing();
            if (stripped.endsWith(BR)) {
                result = stripped.substring(0, stripped.length() - BR.length());
            } else {
                return result;
            }
        }
    }

    /** Видимый текст строки QSP: без разметки HTML; подстановка <<…>> видима */
    private static boolean hasVisibleText(String line) {
        return !line.replaceAll("<<[^>]*>>", "x").replaceAll("<[^>]*>", "").replace("&nbsp;", " ").isBlank();
    }

    /**
     * Пустая метка {} (в INSTEAD — картинка, например флажки настроек «Фронтира»): ссылкой
     * становится первая непустая строка рядом, а без неё — подпись объекта (OBJ_LABEL_MARK), как
     * PlayerEngine.sceneLabel и экспорт jsIQ2
     */
    private static String expandEmptyLink(String objId, String objText, String image) {
        String rest = EMPTY_LINK.matcher(objText).replaceAll("");
        // строки — через перевод строки или <br> ('^'); разделители сохраняются
        String[] parts = rest.split("(?<=\r\n|<br>)", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String body = part.endsWith(BR) ? part.substring(0, part.length() - BR.length())
                    : part.endsWith(LINE_SEPARATOR) ? part.substring(0, part.length() - LINE_SEPARATOR.length()) : part;
            if (hasVisibleText(body)) {
                String label = body.strip();
                int start = body.indexOf(label);
                parts[i] = body.substring(0, start) + objectLink(objId, image + label) + body.substring(start + label.length())
                        + part.substring(body.length());
                return String.join("", parts);
            }
        }
        return rest + objectLink(objId, image + OBJ_LABEL_MARK);
    }

    /** Первая непустая строка текста действия ('…' — оператор вывода) */
    private static String firstLine(String actStart) {
        String text = actStart.strip();
        if (text.startsWith("'") && text.endsWith("'") && text.length() >= 2) {
            text = text.substring(1, text.length() - 1);
        }
        for (String line : text.split("\r\n|<br>")) {
            if (hasVisibleText(line)) {
                return line.strip();
            }
        }
        return EMPTY_STRING;
    }

    /** Ссылка на объект в строке QSP: щелчок — nlb_act */
    private static String objectLink(String objId, String label) {
        return "<a href=\"EXEC:GOSUB ''nlb_act'', ''" + objId + "''\">" + label + "</a>";
    }

    @Override
    protected String decorateObjActStart(String actTextExpanded, String commonObjId) {
        return StringHelper.isEmpty(actTextExpanded) ? EMPTY_STRING : "'" + actTextExpanded + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjNouse(String nouseTextExpanded) {
        return StringHelper.isEmpty(nouseTextExpanded) ? EMPTY_STRING : nouseTextExpanded;
    }

    @Override
    protected String decorateObjConstraint(String constraintValue) {
        return constraintValue;
    }

    @Override
    protected String decorateObjModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateObjVariable(String variableName) {
        return variableName + " = 1" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateContainedObjId(String containedObjId) {
        return containedObjId;
    }

    @Override
    protected String decorateUseTarget(String targetId) {
        return targetId;
    }

    @Override
    protected String decorateUseModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateUseVariable(String variableName) {
        return variableName + " = 1" + LINE_SEPARATOR;
    }

    /**
     * Текст книги в строке QSP: апострофы удваиваются, &amp; &lt; &gt; — сущностями (USEHTML),
     * переменная — подвыражением &lt;&lt;имя&gt;&gt; (строковая — &lt;&lt;$имя&gt;&gt;)
     */
    @Override
    protected String expandVariables(List<TextChunk> textChunks) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    // '^' — перевод строки INSTEAD (так пишут тексты книг, которые играют в
                    // INSTEAD; так его понимает и Java-плеер): <br> — USEHTML
                    result.append(qspString(textChunk.getText()).replace("^", BR));
                    break;
                case VARIABLE:
                    Variable.DataType type = getVariableDataType(textChunk.getText());
                    result.append("<<").append(type == Variable.DataType.STRING ? "$" : EMPTY_STRING)
                            .append(textChunk.getText()).append(">>");
                    break;
                case ACTION_TEXT:
                    break;
                case NEWLINE:
                    result.append(getLineSeparator());
                    break;
            }
        }
        // хвостовые '^' не дают пустой строки: плеер обрезает текст справа
        return trimTrailingBreaks(result.toString());
    }

    @Override
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        // подпись действия и текст применения — одна строка ('^' — пробел, по краям — ничего,
        // как подпись ссылки в плеере)
        return expandVariables(textChunks).replace(LINE_SEPARATOR, " ").replace(BR, " ").strip();
    }

    /**
     * Страница — три локации. Вход «#id»: модификации страницы (до текста, как enter в INSTEAD),
     * затем показ и ход. Показ «#id_show»: заголовок, картинка, текст и объекты страницы — его
     * повторяет и действие с объектом (nlb_refresh), уже без модификаций входа. Ход «#id_turn»:
     * автоссылки и действия-ссылки (CLA — их пересобирает и nlb_refresh: условия могли измениться).
     */
    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        final String pageId = pageBlocks.getPageId();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(pageBlocks.getPageComment());
        stringBuilder.append(pageBlocks.getPageLabel());
        stringBuilder.append(pageBlocks.getPageModifications());
        stringBuilder.append(pageBlocks.getPageVariable());
        final boolean hasTimer = pageBlocks.isHasPageTimer();
        if (hasTimer) {
            // initf(): таймер страницы обнуляется после её модификаций
            stringBuilder.append(pageBlocks.getPageTimerVariableInit());
            stringBuilder.append("nlb_tick = 0").append(LINE_SEPARATOR);
        }
        if (!pageBlocks.getPageSounds().isEmpty()) {
            // initf(): звук страницы — при входе, после модификаций (повторный показ его не повторяет)
            stringBuilder.append("GOSUB 'nlb_snd_").append(pageId).append("'").append(LINE_SEPARATOR);
        }
        stringBuilder.append("GOSUB '").append(pageId).append("_show'").append(LINE_SEPARATOR);
        stringBuilder.append("GOSUB '").append(pageId).append("_turn'").append(LINE_SEPARATOR);
        stringBuilder.append(LOC_END).append(LINE_SEPARATOR);
        stringBuilder.append("#").append(pageId).append("_show").append(LINE_SEPARATOR);
        if (hasTimer) {
            // тик таймера — ход игрока на странице (действие, nlb_refresh), до показа: текст
            // страницы видит новое значение; переход на страницу — не тик
            stringBuilder.append("IF nlb_tick = 1:").append(LINE_SEPARATOR);
            stringBuilder.append("    nlb_tick = 0").append(LINE_SEPARATOR);
            stringBuilder.append("    ").append(pageBlocks.getPageTimerVariable());
            stringBuilder.append("END").append(LINE_SEPARATOR);
        }
        stringBuilder.append(pageBlocks.getPageCaption());
        // Картинка — при каждом показе (тег мог измениться). Служебная страница — без текста,
        // её сразу покидают по автоссылке — картинок (своей и объектов) не показывает, как в
        // плеере (PlayerEngine.followLink): условие автоссылки проверяется до показа (nlb_leave)
        List<MediaChoice> images = pageBlocks.getPageImages();
        stringBuilder.append("nlb_leave = 0").append(LINE_SEPARATOR);
        if (!pageBlocks.isHasPageText()) {
            stringBuilder.append(leaveCheck(pageBlocks.getLinksBuildingBlocks()));
        }
        if (images.size() == 1 && !images.get(0).hasConstraint()) {
            stringBuilder.append("IF nlb_leave = 0: '<img src=\"").append(images.get(0).getPath().replace("'", "''")).append("\">'").append(LINE_SEPARATOR);
        } else if (!images.isEmpty()) {
            stringBuilder.append("$nlb_img = $FUNC('nlb_imgtag', $FUNC('nlb_img_").append(pageId).append("'))").append(LINE_SEPARATOR);
            stringBuilder.append("IF $nlb_img <> '': $nlb_img").append(LINE_SEPARATOR);
        }
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append("GOSUB 'nlb_show_in', '").append(pageId).append("'").append(LINE_SEPARATOR);
        stringBuilder.append(LOC_END).append(LINE_SEPARATOR);
        stringBuilder.append("#").append(pageId).append("_turn").append(LINE_SEPARATOR);
        stringBuilder.append("CLA").append(LINE_SEPARATOR);
        stringBuilder.append("GOSUB 'nlb_turn'").append(LINE_SEPARATOR);
        stringBuilder.append("GOSUB 'nlb_inv_sync'").append(LINE_SEPARATOR);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            appendLink(stringBuilder, linkBlocks);
        }
        String noChoices = noChoicesCondition(pageBlocks);
        if (noChoices != null) {
            // Выбирать нечего, а автоссылки ждут таймера — время проматывается (как в плеере):
            // тик за тиком, пока не сработает автоссылка (XGOTO прерывает цикл)
            if (!noChoices.isEmpty()) {
                stringBuilder.append("IF ").append(noChoices).append(":").append(LINE_SEPARATOR);
            }
            stringBuilder.append("nlb_wait = 0").append(LINE_SEPARATOR);
            stringBuilder.append("LOOP WHILE nlb_wait < ").append(pageBlocks.getTimerWaitTicks()).append(":").append(LINE_SEPARATOR);
            stringBuilder.append("    nlb_wait = nlb_wait + 1").append(LINE_SEPARATOR);
            stringBuilder.append("    ").append(pageBlocks.getPageTimerVariable());
            for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
                if (linkBlocks.isAuto()) {
                    appendLink(stringBuilder, linkBlocks);
                }
            }
            stringBuilder.append("END").append(LINE_SEPARATOR);
            if (!noChoices.isEmpty()) {
                stringBuilder.append("END").append(LINE_SEPARATOR);
            }
        }
        stringBuilder.append(pageBlocks.getPageEnd());
        if (images.size() > 1 || (images.size() == 1 && images.get(0).hasConstraint())) {
            stringBuilder.append(imageLocation(pageId, images));
        }
        stringBuilder.append(soundLocation(pageId, pageBlocks.getPageSounds(), true));
        return stringBuilder.toString();
    }

    /**
     * Условие «выбирать нечего» для проматывания таймера (PlayerEngine.autoWaitTicks): ни одна
     * обычная ссылка не доступна. null — не проматывать (нет таймера или автоссылок, либо есть
     * ссылка без условия), пустая строка — проматывать всегда (обычных ссылок нет)
     */
    private static String noChoicesCondition(PageBuildingBlocks pageBlocks) {
        if (!pageBlocks.isHasPageTimer() || pageBlocks.getTimerWaitTicks() <= 0) {
            return null;
        }
        boolean hasAuto = false;
        StringBuilder any = new StringBuilder();
        for (LinkBuildingBlocks linkBlocks : pageBlocks.getLinksBuildingBlocks()) {
            if (linkBlocks.isAuto()) {
                hasAuto = true;
            } else if (StringHelper.isEmpty(linkBlocks.getLinkConstraint())) {
                return null;
            } else {
                any.append(any.length() > 0 ? " OR " : "").append("(").append(linkBlocks.getLinkConstraint()).append(")");
            }
        }
        if (!hasAuto) {
            return null;
        }
        return any.length() == 0 ? "" : "NO (" + any + ")";
    }

    /** Ссылка страницы в ходе «#id_turn»: автоссылка — переход при условии, обычная — действие ACT */
    /** nlb_leave = 1, если сработает автоссылка (условия — как в appendLink) */
    private String leaveCheck(List<LinkBuildingBlocks> linksBlocks) {
        StringBuilder sb = new StringBuilder();
        for (LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (!linkBlocks.isAuto()) {
                continue;
            }
            String constraint = linkBlocks.getLinkConstraint();
            if (linkBlocks.isNeedsAction()) {
                String hasAction = "(" + ACTION_COUNT_VAR + " >= " + getNeedsActionCount() + ")";
                constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") AND " + hasAction;
            }
            if (StringHelper.isEmpty(constraint)) {
                return sb.append("nlb_leave = 1").append(LINE_SEPARATOR).toString();
            }
            sb.append("IF ").append(constraint).append(": nlb_leave = 1").append(LINE_SEPARATOR);
        }
        return sb.toString();
    }

    private void appendLink(StringBuilder stringBuilder, LinkBuildingBlocks linkBlocks) {
        {
            final boolean waitsAction = linkBlocks.isAuto() && linkBlocks.isNeedsAction();
            String constraint = linkBlocks.getLinkConstraint();
            if (waitsAction) {
                // has_action(): автоссылка ждёт, пока игрок сделает нужное число действий
                String hasAction = "(" + ACTION_COUNT_VAR + " >= " + getNeedsActionCount() + ")";
                constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") AND " + hasAction;
            }
            final boolean hasConstraint = !StringHelper.isEmpty(constraint);
            stringBuilder.append(linkBlocks.getLinkComment());
            if (hasConstraint) {
                stringBuilder.append("IF ").append(constraint).append(":");
                stringBuilder.append(LINE_SEPARATOR);
            }
            if (!linkBlocks.isAuto()) {
                stringBuilder.append(linkBlocks.getLinkStart());
                // переход по ссылке отменяет выбранный для применения предмет
                stringBuilder.append("    $nlb_sel = ''").append(LINE_SEPARATOR);
                stringBuilder.append("    UNSEL").append(LINE_SEPARATOR);
            }
            if (!linkBlocks.isTechnical()) {
                // переход по обычной ссылке — действие игрока (для автоссылок «needs action»)
                stringBuilder.append("    ").append(ACTION_COUNT_VAR).append(" = ").append(ACTION_COUNT_VAR).append(" + 1").append(LINE_SEPARATOR);
                stringBuilder.append("    nlb_cnt_walk = nlb_cnt_walk + 1").append(LINE_SEPARATOR);
            } else if (waitsAction) {
                // has_action() сбрасывает счётчики действий — те же, что COUNTRST (как в плеере)
                stringBuilder.append(decorateCountResetOperation());
            }
            stringBuilder.append(linkBlocks.getLinkModifications());
            stringBuilder.append(linkBlocks.getLinkVariable());
            stringBuilder.append(linkBlocks.getLinkVisitStateVariable());
            if (linkBlocks.isAuto()) {
                // XGOTO не очищает основное окно: текст страницы, которую сразу покидают по
                // автоссылке, остаётся виден перед текстом следующей (как в плеере)
                stringBuilder.append(linkBlocks.getLinkGoTo().replaceFirst("GOTO '", "XGOTO '"));
            } else {
                stringBuilder.append(linkBlocks.getLinkGoTo());
                stringBuilder.append(linkBlocks.getLinkEnd());
            }
            if (hasConstraint) {
                if (linkBlocks.hasShownAltText()) {
                    // альтернативный текст недоступной ссылки (#135) — строкой в основном окне
                    // (текст уже экранирован expandVariables)
                    stringBuilder.append("ELSE").append(LINE_SEPARATOR);
                    stringBuilder.append("  '").append(linkBlocks.getLinkAltText()).append("'");
                    stringBuilder.append(LINE_SEPARATOR);
                }
                stringBuilder.append("END").append(LINE_SEPARATOR);
            }
        }
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected boolean shouldInferStringTypes() {
        // в QSP строковая и числовая переменные — разные ($имя и имя)
        return true;
    }

    @Override
    protected String decorateMod() {
        return "MOD";
    }

    @Override
    protected String decorateTrue() {
        return "1";
    }

    @Override
    protected String decorateFalse() {
        return "0";
    }

    @Override
    protected String decorateEq() {
        return "=";
    }

    @Override
    protected String decorateNEq() {
        return "<>";
    }

    @Override
    protected String decorateGt() {
        return ">";
    }

    @Override
    protected String decorateGte() {
        return ">=";
    }

    @Override
    protected String decorateLt() {
        return "<";
    }

    @Override
    protected String decorateLte() {
        return "<=";
    }

    @Override
    protected String decorateNot() {
        return "NO ";
    }

    @Override
    protected String decorateOr() {
        return "OR";
    }

    @Override
    protected String decorateAnd() {
        return "AND";
    }

    @Override
    protected String decorateExistence(final String decoratedVariable) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return "(" + constraintVar + " = 1)";
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        // строковая переменная QSP — с $; выражения подставляются через Matcher.quoteReplacement,
        // поэтому экранировать $ не нужно (раньше в код попадало «\\$имя» — Type mismatch)
        return "$" + constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return constraintVar;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return "#" + linkId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return comment("Link -- " + comment);
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        return "  ACT '" + linkText + "':" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkSource,
            int sourcePageNumber,
            String linkTarget,
            int targetPageNumber,
            boolean isTechnical,
            Theme theme) {
        return "    GOTO '" + linkTarget + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkEnd(Theme theme) {
        return "  END" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return LOC_END + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return "    " + variableName + " = 1" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return "    " + linkVisitStateVariable + " = 1" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageVariable(final String variableName) {
        return variableName + " = 1" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTimerVariableInit(final String variableName) {
        // таймер страницы (tvarid): обнуляется при входе, +1 за ход игрока на странице
        return StringHelper.isEmpty(variableName) ? Constants.EMPTY_STRING : variableName + " = 0" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTimerVariable(final String variableName) {
        return StringHelper.isEmpty(variableName) ? Constants.EMPTY_STRING
                : variableName + " = " + variableName + " + 1" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageThemeModifications(String modificationsText) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return modificationsText;
    }

    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return (
                    "'" + qspString(caption.toUpperCase()) + "'" + LINE_SEPARATOR
                            + "'" + StringHelper.createRepeatedString(caption.length(), "-") + "'" + LINE_SEPARATOR
                            + "''" + LINE_SEPARATOR
            );
        } else {
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePageNotes(String notes) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme) {
        // картинка выбирается при показе страницы (generatePageText, nlb_img_id)
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        // звук — в #nlb_snd_id (soundLocation)
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageTextStart(String labelText, int pageNumber, List<TextChunk> pageTextChunks, Theme theme) {
        return "'" + super.decoratePageTextStart(labelText, pageNumber, pageTextChunks, theme);
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        return "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        return "#" + labelText + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return comment("Page -- " + comment);
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + LINE_SEPARATOR;
    }

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        // тег — $nlb_tag[id] (объекта, страницы или ссылки из переменной; без них — свой)
        return "$nlb_tag[" + objRef(objId, variable) + "] = '" + qspString(tag) + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        return resultingVariable + " = $nlb_tag[" + objRef(objId, objVariableName) + "]" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateWhile(final String constraint) {
        // QSP 5.8+: многострочный цикл LOOP WHILE …: … END
        return "LOOP WHILE " + constraint + ":" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIf(final String constraint) {
        return "IF " + constraint + ":" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        // место объекта — $nlb_in (объект из переменной экспорт не отслеживает: его нет)
        return "IF " + (objId != null ? "$nlb_in['" + objId + "'] = '" + INVENTORY + "'" : "0") + ":" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElse() {
        return "ELSE" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        return "ELSEIF " + constraint + ":" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateEnd() {
        // END закрывает и IF, и LOOP
        return "END" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateReturn(String returnValue) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateHaveOperation(String variableName, String objId, String objVar) {
        // variableName — «(имя = 1)» (decorateBooleanVar)
        String name = variableName.replaceAll("^\\((.*) = 1\\)$", "$1");
        String obj = objRef(objId, objVar);
        return name + " = 0" + LINE_SEPARATOR + "IF $nlb_in[" + obj + "] = '" + INVENTORY + "': " + name + " = 1" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateCloneOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        // контейнер объекта (страница или объект); инвентарь и «нигде» — пусто
        String result = "$" + variableName;
        return result + " = $nlb_in[" + objRef(objId, objVar) + "]" + LINE_SEPARATOR
                + "IF " + result + " = '" + INVENTORY + "': " + result + " = ''" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateGetIdOperation(final String variableName, final String objId, final String objVar) {
        // прототип объекта: клонов в экспорте нет — сам объект
        String result = variableName.startsWith("$") ? variableName : "$" + variableName;
        return result + " = " + objRef(objId, objVar) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelObj(String destinationId, final String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        String obj = objRef(objectId, objectVar);
        if (destinationName != null) {
            // место — список: nlb:rmv(список, obj), и объект — никуда
            return "GOSUB 'nlb_lrmv', '" + destinationName + "', " + obj + LINE_SEPARATOR
                    + "GOSUB 'nlb_place', " + obj + ", ''" + LINE_SEPARATOR;
        }
        String destination = destinationId != null ? "'" + destinationId + "'" : "$CURLOC";
        return "IF $nlb_in[" + obj + "] = " + destination + ": GOSUB 'nlb_place', " + obj + ", ''" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        String obj = objRef(objectId, objectVar);
        return "IF $nlb_in[" + obj + "] = '" + INVENTORY + "': GOSUB 'nlb_place', " + obj + ", ''" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        // без получателя — в себя (страницу или объект-контейнер; s в INSTEAD)
        String destination = destinationId != null ? "'" + destinationId + "'" : "'" + selfId() + "'";
        return "GOSUB 'nlb_place', " + objRef(objectId, objectVar) + ", " + destination + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return "GOSUB 'nlb_place', " + objRef(objectId, objectVar) + ", '" + INVENTORY + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        // в контейнер, в список или (без получателя) — в себя
        String container = destinationId != null ? destinationId : (destinationListVariableName == null ? selfId() : "");
        return "GOSUB 'nlb_laddall', '" + sourceListVariableName + "', '" + container + "', '"
                + (destinationListVariableName != null ? destinationListVariableName : "") + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        return "GOSUB 'nlb_lobjs', '" + listVariableName + "', " + objRef(srcObjId, objectVar) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSSndOperation() {
        return playSoundOf("'" + selfId() + "'");
    }

    @Override
    protected String decorateWSndOperation() {
        // ww — объект, к которому применяют: в nlb_o_id_use это $ARGS[0]; вне применения — ничего
        return inUseLink() ? playSoundOf("$ARGS[0]") : EMPTY_STRING;
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        return playSoundOf(objRef(objectId, objectVar));
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        return "GOSUB 'nlb_lpush', '" + listVariableName + "', '" + selfId() + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        // объект, с которым применяют (ww), в экспорте QSP неизвестен — не поддерживается
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        return "GOSUB 'nlb_lpush', '" + listVariableName + "', " + listValue(objectId, objectVar) + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        return listResult(variableName, "nlb_lpop", listVariableName);
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        return "GOSUB 'nlb_linject', '" + listVariableName + "', '" + selfId() + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        return "GOSUB 'nlb_linject', '" + listVariableName + "', " + listValue(objectId, objectVar) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        return listResult(variableName, "nlb_leject", listVariableName);
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        if (destinationId != null) {
            return "GOSUB 'nlb_clear', '" + destinationId + "'" + LINE_SEPARATOR;
        } else if (destinationVar != null) {
            return "nlb_ln['" + destinationVar + "'] = 0" + LINE_SEPARATOR;  // nlb:clear(список)
        }
        return "GOSUB 'nlb_clear', $CURLOC" + LINE_SEPARATOR;  // текущая комната
    }

    @Override
    protected String decorateClearInvOperation() {
        return "GOSUB 'nlb_clear', '" + INVENTORY + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        return variableName + " = nlb_ln['" + listVariableName + "']" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        // rnd(n) в NLB — целое от 1 до n
        return variableName + " = RAND(1, " + maxValue + ")" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        return "nlb_achmax['" + achievementName + "'] = " + max + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        return "GOSUB 'nlb_achieve', '" + achievementName + "', '" + modificationId + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        return variableName + " = nlb_ach['" + achievementName + "']" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateGoToOperation(String locationId) {
        // GOTO прерывает выполнение текущего кода, поэтому переход из модификаций ссылки важнее
        // цели ссылки (как в INSTEAD)
        return "GOTO '" + locationId + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        return variableName + " = " + countSum(statType) + LINE_SEPARATOR;
    }

    /** Сумма счётчиков действий игрока с последнего сброса (как PlayerContext.countGet): inv 1, act 2, use 4, walk 8 */
    private static String countSum(int statType) {
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < COUNT_TYPES.length; i++) {
            if (statType == 0 || (statType & (1 << i)) != 0) {
                if (sum.length() > 0) {
                    sum.append(" + ");
                }
                sum.append("(nlb_cnt_").append(COUNT_TYPES[i]).append(" - nlb_cntr_").append(COUNT_TYPES[i]).append(")");
            }
        }
        return sum.length() > 0 ? sum.toString() : "0";
    }

    @Override
    protected String decorateCountResetOperation() {
        // сброс счётчиков действий — и для автоссылок «needs action»: они ждут действий игрока
        // с последнего сброса (как в плеере, PlayerEngine.hasAction)
        StringBuilder result = new StringBuilder();
        result.append(ACTION_COUNT_VAR).append(" = 0").append(LINE_SEPARATOR);
        for (String type : COUNT_TYPES) {
            result.append("nlb_cntr_").append(type).append(" = nlb_cnt_").append(type).append(LINE_SEPARATOR);
        }
        return result.toString();
    }

    @Override
    protected String decorateOpenURLOperation(String url) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWinGeomOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInvGeomOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWinColorOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInvColorOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateShuffleOperation(String listVariableName) {
        return "GOSUB 'nlb_lshuffle', '" + listVariableName + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        // вывод значения выражения без перевода строки
        return "*P '<<" + variableName.replace("'", "''") + ">>'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        if (dscObjId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return resultVariableName + " = $FUNC('nlb_o_" + dscObjId + "_textv')" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        // объект по имени: id известен только при сборке текста (generateNLBText)
        return PDSC_MARK + objVariableName + PDSC_MARK + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        if (objId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        // описания объектов внутри объекта
        return "GOSUB 'nlb_show_in', '" + objId + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        if (actingObjId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "GOSUB 'nlb_o_" + actingObjId + "_act'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActtOperation(String resultVariableName, String actObjVariable, String actObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateActfOperation(String actingObjVariable, String actingObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        // TODO: implement
        return EMPTY_STRING;
    }
}
