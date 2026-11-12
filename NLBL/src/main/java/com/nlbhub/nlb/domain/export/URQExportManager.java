/**
 * @(#)URQExportManager.java
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
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The URQExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/4/13
 */
public class URQExportManager extends TextExportManager {
    // Windows line separator should be used for URQ format
    private static final String LINE_SEPARATOR = "\r\n";

    /**
     * Блок модификаций (IF … END или WHILE … END). В URQ условие пишется только в одну строку,
     * поэтому блоки собираются из меток и переходов: ложное условие ветки — переход к метке
     * следующей ветки, конец ветки — переход к метке конца блока, цикл — переход к его началу.
     * Метки внутри кода страницы UrqW при выполнении пропускает.
     */
    private final class Block {
        private final int m_id = ++m_blockCount;
        private final boolean m_loop;
        private int m_branch = 1;

        private Block(boolean loop) {
            m_loop = loop;
        }

        private String branchLabel() {
            return "nlb_if_" + m_id + "_" + m_branch;
        }

        private String endLabel() {
            return (m_loop ? "nlb_while_" : "nlb_if_") + m_id + "_end";
        }

        private String loopLabel() {
            return "nlb_while_" + m_id;
        }
    }

    private int m_blockCount = 0;

    private final Deque<Block> m_openBlocks = new ArrayDeque<>();

    public URQExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    /** Метка ключа объекта (номер) до сборки текста: id -> номер (generateNLBText) */
    private static final String KEY_MARK = "\u0001nlb_key:";
    /** Метка объекта по имени (PDSC) до сборки текста */
    private static final String NAME_MARK = "\u0001nlb_name:";
    /** Метка ключа контейнера до сборки текста: id объекта -> oN, страницы -> pN (ckey) */
    private static final String CKEY_MARK = "\u0001nlb_ckey:";
    /** Ключ контейнера «инвентарь» */
    private static final String INV_KEY = "inv";
    private static final String END_MARK = "\u0002";
    /** Остаток от деления до сборки текста (в UrqW оператора нет: a-b*int(a/b)) */
    private static final String MOD_MARK = "\u0003";
    private static final java.util.regex.Pattern MOD_OPERANDS = java.util.regex.Pattern.compile(
            "([\\w.]+|\\([^()]*\\))\\s*" + MOD_MARK + "\\s*([\\w.]+|\\([^()]*\\))");

    @Override
    protected String decorateMod() {
        return MOD_MARK;
    }
    private static final String[] COUNT_TYPES = {"inv", "act", "use", "walk"};

    private String m_gameInvText = EMPTY_STRING;
    private String m_gameNouseText = EMPTY_STRING;

    private static String key(String objId) {
        return KEY_MARK + objId + END_MARK;
    }

    /**
     * Ключ контейнера — часть имени переменной (в именах UrqW нельзя «-»): объект N — oN,
     * страница — pN (pageRef), инвентарь — inv. Объект это или страница, известно при сборке текста
     */
    private static String ckey(String containerId) {
        return CKEY_MARK + containerId + END_MARK;
    }

    /**
     * Начало игры и служебные метки объектов. Объектов в URQ нет, есть инвентарь (inv+/inv-,
     * действия предмета — метки use_…), поэтому объекты книги пронумерованы. Контейнер (страница
     * pN, объект oN, инвентарь inv — ckey) хранит содержимое списком c<ключ> (nlb_ln_c…,
     * nlb_li_c…_i: сначала порядок книги, затем — по мере добавления), принадлежность —
     * nlb_at_N_<ключ>. Объект может лежать в нескольких контейнерах сразу — ADD не убирает его
     * из прежнего места (objs():add в INSTEAD, PlayerContext.containerAdd); nlb_in_N — последний
     * владелец (CNTNR). В инвентаре объект — предмет nlbo_N с подписью display_use_nlbo_N, в
     * тексте страницы — ссылка [[текст|nlb_act_N]]. Ответ на действие копится в nlb_reply и выводится
     * после повторного показа страницы (как в INSTEAD: сцена, затем ответ).
     */
    @Override
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        m_gameInvText = urqString(nlbBuildingBlocks.getGameInvText());
        m_gameNouseText = urqString(nlbBuildingBlocks.getGameNouseText());
        final List<PageBuildingBlocks> pages = nlbBuildingBlocks.getPagesBuildingBlocks();
        final List<ObjBuildingBlocks> objs = m_orderedObjs;
        StringBuilder sb = new StringBuilder();
        sb.append("; Generated with Non-Linear Book Builder, http://nlbhub.com").append(LINE_SEPARATOR);
        sb.append(":nlb_start").append(LINE_SEPARATOR);
        sb.append("nlb_sel=0").append(LINE_SEPARATOR);
        sb.append("nlb_cnt_prev=0").append(LINE_SEPARATOR);
        sb.append("nlb_reply=\"\"").append(LINE_SEPARATOR);
        for (int i = 0; i < objs.size(); i++) {
            sb.append("nlb_in_").append(i + 1).append("=\"")
                    .append(StringHelper.notEmpty(objs.get(i).getContainerId()) ? containerKey(objs.get(i).getContainerId()) : "")
                    .append("\"").append(LINE_SEPARATOR);
            // номер -> id (контейнер из переменной: OBJS), признак объекта (ADDALL), тег по умолчанию
            sb.append("nlb_id_").append(i + 1).append("=\"").append(objs.get(i).getObjId()).append("\"").append(LINE_SEPARATOR);
            sb.append("nlb_isobj_").append(i + 1).append("=1").append(LINE_SEPARATOR);
            if (StringHelper.notEmpty(objs.get(i).getObjDefaultTagVariable())) {
                sb.append("nlb_tag_").append(i + 1).append("=\"").append(urqString(objs.get(i).getObjDefaultTagVariable()))
                        .append("\"").append(LINE_SEPARATOR);
            }
            if (!objs.get(i).isVisibleInInventory()) {
                // предмет без подписи и картинки лежит в инвентаре невидимым (как в плеере и INSTEAD)
                sb.append("nlb_invhide_").append(i + 1).append("=1").append(LINE_SEPARATOR);
            }
            if (!objs.get(i).getObjSounds().isEmpty()) {
                sb.append("nlb_hassnd_").append(i + 1).append("=1").append(LINE_SEPARATOR);
            }
            ObjType type = objs.get(i).getObjType();
            if (type == ObjType.MENU || type == ObjType.GMENU) {
                // щелчок в инвентаре — действие, без выбора для применения (decorateObjInv: menu)
                sb.append("nlb_menu_").append(i + 1).append("=1").append(LINE_SEPARATOR);
            } else if (type == ObjType.STAT) {
                sb.append("nlb_menu_").append(i + 1).append("=2").append(LINE_SEPARATOR);  // только подпись
            }
            if (objs.get(i).isSuppressDsc()) {
                sb.append("nlb_supp_").append(i + 1).append("=1").append(LINE_SEPARATOR);
            }
        }
        // содержимое контейнеров (порядок книги, content)
        for (java.util.Map.Entry<String, List<String>> entry : initialContents(nlbBuildingBlocks).entrySet()) {
            final String c = containerKey(entry.getKey());
            final List<String> items = entry.getValue();
            for (int i = 0; i < items.size(); i++) {
                final int n = m_keys.get(items.get(i));
                sb.append("nlb_at_").append(n).append("_").append(c).append("=1").append(LINE_SEPARATOR);
                sb.append("nlb_li_c").append(c).append("_").append(i).append("=").append(n).append(LINE_SEPARATOR);
            }
            sb.append("nlb_ln_c").append(c).append("=").append(items.size()).append(LINE_SEPARATOR);
        }
        // страницы: тег по умолчанию и признак звука — по ссылке «pN» (pageRef)
        for (PageBuildingBlocks page : pages) {
            String ref = pageRef(page.getPageId());
            if (StringHelper.notEmpty(page.getPageDefaultTag())) {
                sb.append("nlb_tag_").append(ref).append("=\"").append(urqString(page.getPageDefaultTag())).append("\"").append(LINE_SEPARATOR);
            }
            if (!page.getPageSounds().isEmpty()) {
                sb.append("nlb_hassnd_").append(ref).append("=1").append(LINE_SEPARATOR);
            }
        }
        if (!pages.isEmpty()) {
            sb.append("goto ").append(pages.get(0).getPageId()).append(LINE_SEPARATOR);
        }
        // объекты контейнера nlb_where (ключ) по порядку; вложенные — рекурсивно (proc), поэтому
        // счётчик и контейнер — по глубине nlb_sd
        sb.append(String.join(LINE_SEPARATOR,
                ":nlb_show_in",
                "nlb_sd=nlb_sd+1",
                "nlb_si_#nlb_sd$=0",
                "nlb_sw_#nlb_sd$=nlb_where",
                ":nlb_show_in_loop",
                "nlb_where=nlb_sw_#nlb_sd$",
                "nlb_k2=nlb_si_#nlb_sd$",
                "if nlb_k2>=nlb_ln_c#%nlb_where$ then goto nlb_show_in_end",
                "nlb_so=nlb_li_c#%nlb_where$_#nlb_k2$",
                "nlb_si_#nlb_sd$=nlb_k2+1",
                "proc nlb_o_#nlb_so$_dsc",
                "goto nlb_show_in_loop",
                ":nlb_show_in_end",
                "nlb_sd=nlb_sd-1",
                "end",
                // PDSCS: описания объектов с suppressDsc в контейнере nlb_where (вложенные — раньше
                // своего контейнера), как ModificationInterpreter.pdscs
                ":nlb_pdscs",
                "nlb_sd=nlb_sd+1",
                "nlb_si_#nlb_sd$=0",
                "nlb_sw_#nlb_sd$=nlb_where",
                ":nlb_pdscs_loop",
                "nlb_where=nlb_sw_#nlb_sd$",
                "nlb_k2=nlb_si_#nlb_sd$",
                "if nlb_k2>=nlb_ln_c#%nlb_where$ then goto nlb_pdscs_end",
                "nlb_so=nlb_li_c#%nlb_where$_#nlb_k2$",
                "nlb_si_#nlb_sd$=nlb_k2+1",
                "if nlb_supp_#nlb_so$<>1 then goto nlb_pdscs_loop",
                "nlb_sd_o_#nlb_sd$=nlb_so",
                "nlb_where=\"o\"+nlb_so",
                "proc nlb_pdscs",
                "nlb_so=nlb_sd_o_#nlb_sd$",
                "proc nlb_o_#nlb_so$_on",
                "if nlb_on=1 then proc nlb_o_#nlb_so$_text",
                "goto nlb_pdscs_loop",
                ":nlb_pdscs_end",
                "nlb_sd=nlb_sd-1",
                "end",
                "")).append(LINE_SEPARATOR);
        // Конец хода (из :id_turn): callback-объекты, если игрок с прошлого хода что-то сделал
        // (update_action_count в INSTEAD, PlayerEngine; как в QSP, jsIQ2 и AXMA) — их действие без
        // «взять»; nlb_cnt_prev ставится до них: их goto уводит со страницы
        sb.append(":nlb_turn").append(LINE_SEPARATOR);
        sb.append(assignment("nlb_diff", countSum(0)));
        sb.append("if nlb_diff<=nlb_cnt_prev then goto nlb_turn_end").append(LINE_SEPARATOR);
        sb.append("nlb_cnt_prev=nlb_diff").append(LINE_SEPARATOR);
        for (int i = 1; i <= objs.size(); i++) {
            ObjBuildingBlocks obj = objs.get(i - 1);
            if (obj.isCallback()) {
                boolean constrained = StringHelper.notEmpty(obj.getObjConstraint());
                sb.append(constrained ? "if " + obj.getObjConstraint() + " then " : "")
                        .append("proc nlb_o_").append(i).append("_acta").append(LINE_SEPARATOR);
            }
        }
        sb.append(":nlb_turn_end").append(LINE_SEPARATOR);
        sb.append("nlb_cnt_prev=nlb_diff").append(LINE_SEPARATOR);
        sb.append("end").append(LINE_SEPARATOR);
        // подписи предметов (с переменными — пересчитываются каждый ход)
        sb.append(":nlb_disp_all").append(LINE_SEPARATOR);
        for (int i = 1; i <= objs.size(); i++) {
            sb.append("display_use_nlbo_").append(i).append("=\"").append(objDisp(objs.get(i - 1))).append("\"").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);
        sb.append(String.join(LINE_SEPARATOR,
                // щелчок по объекту в тексте: действие или (выбран предмет) применение
                ":nlb_act",
                "if nlb_sel<>0 then goto nlb_act_use",
                ACTION_COUNT_VAR + "=" + ACTION_COUNT_VAR + "+1",
                "nlb_cnt_act=nlb_cnt_act+1",
                "proc nlb_o_#nlb_o$_act",
                "goto nlb_redraw",
                ":nlb_act_use",
                "nlb_t=nlb_o",
                "nlb_o=nlb_sel",
                "goto nlb_use",
                // применение nlb_o к nlb_t
                ":nlb_use",
                ACTION_COUNT_VAR + "=" + ACTION_COUNT_VAR + "+1",
                "nlb_cnt_use=nlb_cnt_use+1",
                "nlb_printed=0",
                "proc nlb_o_#nlb_o$_use",
                "if nlb_printed=0 then proc nlb_o_#nlb_o$_nouse",
                "nlb_sel=0",
                "goto nlb_redraw",
                // щелчок по предмету: первый — inv (применение к себе) и выбор для применения,
                // другой предмет — применение, тот же — отмена выбора
                ":nlb_invclick",
                "if nlb_sel=0 and nlb_menu_#nlb_o2$>0 then goto nlb_invmenu",
                "if nlb_sel=0 then goto nlb_invfirst",
                "if nlb_sel=nlb_o2 then goto nlb_invcancel",
                "nlb_o=nlb_sel",
                "nlb_t=nlb_o2",
                "goto nlb_use",
                ":nlb_invcancel",
                "nlb_sel=0",
                "goto nlb_redraw",
                // предмет-меню (карты в бою «Фронтира»): щелчок — действие, показатель — ничего; не
                // выбираются (PlayerEngine.clickInventory)
                ":nlb_invmenu",
                ACTION_COUNT_VAR + "=" + ACTION_COUNT_VAR + "+1",
                "nlb_cnt_inv=nlb_cnt_inv+1",
                "if nlb_menu_#nlb_o2$=1 then proc nlb_o_#nlb_o2$_acta",
                "goto nlb_redraw",
                ":nlb_invfirst",
                ACTION_COUNT_VAR + "=" + ACTION_COUNT_VAR + "+1",
                "nlb_cnt_inv=nlb_cnt_inv+1",
                "nlb_printed=0",
                "nlb_o=nlb_o2",
                "nlb_t=nlb_o2",
                "proc nlb_o_#nlb_o$_use",
                "if nlb_printed=0 then nlb_reply=nlb_reply+\"" + m_gameInvText + "<br>\"",
                "nlb_sel=nlb_o2",
                "goto nlb_redraw",
                // показать страницу заново: сцена, ответ, кнопки
                ":nlb_redraw",
                // ход игрока на странице — тик её таймера (в начале показа)
                "nlb_tick=1",
                "cls",
                "proc #%nlb_page$_show",
                "if nlb_reply<>\"\" then pln #%nlb_reply$",
                "nlb_reply=\"\"",
                "goto #%nlb_page$_turn",
                // Списки (nlb.lua: push/pop — начало, inject/eject — конец): имя — nlb_lname,
                // длина — nlb_ln_<имя>, элементы — nlb_li_<имя>_<i> (0 — начало); значение — nlb_lval,
                // снятое — nlb_lres (из пустого — 0). Счётчик nlb_k — у этих подпрограмм, nlb_m — у
                // тех, что их вызывают
                ":nlb_lpush",
                "nlb_k = nlb_ln_#%nlb_lname$",
                ":nlb_lpush_loop",
                "if nlb_k<=0 then goto nlb_lpush_set",
                "nlb_k1 = nlb_k-1",
                "nlb_li_#%nlb_lname$_#nlb_k$ = nlb_li_#%nlb_lname$_#nlb_k1$",
                "nlb_k = nlb_k-1",
                "goto nlb_lpush_loop",
                ":nlb_lpush_set",
                "nlb_li_#%nlb_lname$_0 = nlb_lval",
                "nlb_ln_#%nlb_lname$ = nlb_ln_#%nlb_lname$+1",
                "end",
                ":nlb_linject",
                "nlb_k = nlb_ln_#%nlb_lname$",
                "nlb_li_#%nlb_lname$_#nlb_k$ = nlb_lval",
                "nlb_ln_#%nlb_lname$ = nlb_k+1",
                "end",
                ":nlb_lpop",
                "nlb_lres=0",
                "if nlb_ln_#%nlb_lname$<=0 then goto nlb_lpop_end",
                "nlb_lres = nlb_li_#%nlb_lname$_0",
                "nlb_k=1",
                ":nlb_lpop_loop",
                "if nlb_k>=nlb_ln_#%nlb_lname$ then goto nlb_lpop_done",
                "nlb_k1 = nlb_k-1",
                "nlb_li_#%nlb_lname$_#nlb_k1$ = nlb_li_#%nlb_lname$_#nlb_k$",
                "nlb_k = nlb_k+1",
                "goto nlb_lpop_loop",
                ":nlb_lpop_done",
                "nlb_ln_#%nlb_lname$ = nlb_ln_#%nlb_lname$-1",
                ":nlb_lpop_end",
                "end",
                ":nlb_leject",
                "nlb_lres=0",
                "if nlb_ln_#%nlb_lname$<=0 then goto nlb_leject_end",
                "nlb_k = nlb_ln_#%nlb_lname$-1",
                "nlb_lres = nlb_li_#%nlb_lname$_#nlb_k$",
                "nlb_ln_#%nlb_lname$ = nlb_k",
                ":nlb_leject_end",
                "end",
                // nlb:rmv — первое вхождение nlb_lval
                ":nlb_lrmv",
                "nlb_k=0",
                ":nlb_lrmv_find",
                "if nlb_k>=nlb_ln_#%nlb_lname$ then goto nlb_lrmv_end",
                "if nlb_li_#%nlb_lname$_#nlb_k$=nlb_lval then goto nlb_lrmv_shift",
                "nlb_k = nlb_k+1",
                "goto nlb_lrmv_find",
                ":nlb_lrmv_shift",
                "nlb_k = nlb_k+1",
                "if nlb_k>=nlb_ln_#%nlb_lname$ then goto nlb_lrmv_cut",
                "nlb_k1 = nlb_k-1",
                "nlb_li_#%nlb_lname$_#nlb_k1$ = nlb_li_#%nlb_lname$_#nlb_k$",
                "goto nlb_lrmv_shift",
                ":nlb_lrmv_cut",
                "nlb_ln_#%nlb_lname$ = nlb_ln_#%nlb_lname$-1",
                ":nlb_lrmv_end",
                "end",
                // Фишер — Йейтс
                ":nlb_lshuffle",
                "nlb_k = nlb_ln_#%nlb_lname$-1",
                ":nlb_lshuffle_loop",
                "if nlb_k<=0 then goto nlb_lshuffle_end",
                "nlb_k1 = nlb_k+1",
                "nlb_j = rnd#nlb_k1$-1",
                "nlb_lres = nlb_li_#%nlb_lname$_#nlb_k$",
                "nlb_li_#%nlb_lname$_#nlb_k$ = nlb_li_#%nlb_lname$_#nlb_j$",
                "nlb_li_#%nlb_lname$_#nlb_j$ = nlb_lres",
                "nlb_k = nlb_k-1",
                "goto nlb_lshuffle_loop",
                ":nlb_lshuffle_end",
                "end",
                // OBJS: содержимое контейнера nlb_c — в начало списка nlb_lname (обратный порядок)
                ":nlb_lobjs",
                "nlb_m=0",
                "nlb_lc=\"c\"+nlb_c",
                ":nlb_lobjs_loop",
                "if nlb_m>=nlb_ln_#%nlb_lc$ then goto nlb_lobjs_end",
                "nlb_lval = nlb_li_#%nlb_lc$_#nlb_m$",
                "nlb_m = nlb_m+1",
                "proc nlb_lpush",
                "goto nlb_lobjs_loop",
                ":nlb_lobjs_end",
                "end",
                // ADDALL: элементы списка nlb_lsrc — в начало списка nlb_ldst или (объекты) в контейнер nlb_c
                ":nlb_laddall",
                "nlb_m=0",
                ":nlb_laddall_loop",
                "if nlb_m>=nlb_ln_#%nlb_lsrc$ then goto nlb_laddall_end",
                "nlb_lval = nlb_li_#%nlb_lsrc$_#nlb_m$",
                "nlb_m = nlb_m+1",
                "if nlb_ldst=\"\" then goto nlb_laddall_place",
                "nlb_lname = nlb_ldst",
                "proc nlb_lpush",
                "goto nlb_laddall_loop",
                ":nlb_laddall_place",
                "if nlb_isobj_#nlb_lval$<>1 then goto nlb_laddall_loop",
                "nlb_po = nlb_lval",
                "nlb_pd = nlb_c",
                "proc nlb_add",
                "goto nlb_laddall_loop",
                ":nlb_laddall_end",
                "end",
                // CLEAR, CLRINV: всё содержимое контейнера nlb_c (ключ) — вон из него; владелец
                // сбрасывается у тех, чей последний владелец — он (PlayerContext.containerClear)
                ":nlb_clear",
                "nlb_m=0",
                "nlb_lc=\"c\"+nlb_c",
                ":nlb_clear_loop",
                "if nlb_m>=nlb_ln_#%nlb_lc$ then goto nlb_clear_end",
                "nlb_po=nlb_li_#%nlb_lc$_#nlb_m$",
                "nlb_at_#nlb_po$_#%nlb_c$=0",
                "if nlb_in_#nlb_po$=nlb_c then nlb_in_#nlb_po$=\"\"",
                "nlb_m=nlb_m+1",
                "goto nlb_clear_loop",
                ":nlb_clear_end",
                "nlb_ln_#%nlb_lc$=0",
                "end",
                // nlb_add — объект nlb_po в контейнер nlb_pd (ключ), не убирая из прежних мест
                // (objs():add); nlb_del — убрать из nlb_pd (владелец сбрасывается, container = nil);
                // nlb_take — взять (take в INSTEAD): из текущей комнаты, при nlb_tk=1 (щелчок по
                // переносимому объекту) — и от последнего владельца, если в комнате его нет
                ":nlb_add",
                "if nlb_at_#nlb_po$_#%nlb_pd$=1 then goto nlb_add_owner",
                "nlb_at_#nlb_po$_#%nlb_pd$=1",
                "nlb_lname=\"c\"+nlb_pd",
                "nlb_lval=nlb_po",
                "proc nlb_linject",
                ":nlb_add_owner",
                "if nlb_pd<>\"" + INV_KEY + "\" then nlb_in_#nlb_po$=nlb_pd",
                "end",
                ":nlb_del",
                "if nlb_at_#nlb_po$_#%nlb_pd$<>1 then goto nlb_del_owner",
                "nlb_at_#nlb_po$_#%nlb_pd$=0",
                "nlb_lname=\"c\"+nlb_pd",
                "nlb_lval=nlb_po",
                "proc nlb_lrmv",
                ":nlb_del_owner",
                "if nlb_pd<>\"" + INV_KEY + "\" then nlb_in_#nlb_po$=\"\"",
                "end",
                ":nlb_take",
                "nlb_pd=nlb_pkey",
                "if nlb_at_#nlb_po$_#%nlb_pd$=1 then goto nlb_take_del",
                "if nlb_tk<>1 then goto nlb_take_inv",
                "nlb_pd=nlb_in_#nlb_po$",
                "if nlb_pd=\"\" then goto nlb_take_inv",
                "if nlb_at_#nlb_po$_#%nlb_pd$<>1 then goto nlb_take_inv",
                ":nlb_take_del",
                "proc nlb_del",
                ":nlb_take_inv",
                "nlb_pd=\"" + INV_KEY + "\"",
                "proc nlb_add",
                "end",
                // Инвентарь UrqW: предметы по взятию, за каждым — содержимое (вложенные объекты, как
                // PlayerEngine.inventoryObjects: панель боя «Фронтира» с картами); выключенный объект
                // скрывает и содержимое, невидимый (nlb_invhide) — только себя. Список показа cinvs
                // собирается каждый ход; отличается от показанного (cinvd) — инвентарь заново
                ":nlb_inv_sync",
                "nlb_ln_cinvs=0",
                "nlb_ig=nlb_ig+1",
                "nlb_iw=\"" + INV_KEY + "\"",
                "proc nlb_inv_in",
                "nlb_m=0",
                "if nlb_ln_cinvs<>nlb_ln_cinvd then goto nlb_inv_redo",
                ":nlb_inv_cmp",
                "if nlb_m>=nlb_ln_cinvs then end",
                "if nlb_li_cinvs_#nlb_m$<>nlb_li_cinvd_#nlb_m$ then goto nlb_inv_redo",
                "nlb_m=nlb_m+1",
                "goto nlb_inv_cmp",
                ":nlb_inv_redo",
                "invkill",
                "nlb_m=0",
                ":nlb_inv_redo_loop",
                "if nlb_m>=nlb_ln_cinvs then goto nlb_inv_redo_end",
                "nlb_po=nlb_li_cinvs_#nlb_m$",
                "nlb_li_cinvd_#nlb_m$=nlb_po",
                "inv+ nlbo_#nlb_po$",
                "nlb_m=nlb_m+1",
                "goto nlb_inv_redo_loop",
                ":nlb_inv_redo_end",
                "nlb_ln_cinvd=nlb_ln_cinvs",
                "end",
                ":nlb_inv_in",
                "nlb_id=nlb_id+1",
                "nlb_ix_#nlb_id$=0",
                "nlb_iws_#nlb_id$=nlb_iw",
                ":nlb_inv_in_loop",
                "nlb_iw=nlb_iws_#nlb_id$",
                "nlb_k2=nlb_ix_#nlb_id$",
                "if nlb_k2>=nlb_ln_c#%nlb_iw$ then goto nlb_inv_in_end",
                "nlb_iv=nlb_li_c#%nlb_iw$_#nlb_k2$",
                "nlb_ix_#nlb_id$=nlb_k2+1",
                "if nlb_iseen_#nlb_iv$=nlb_ig or nlb_id>32 then goto nlb_inv_in_loop",
                "proc nlb_o_#nlb_iv$_on",
                "if nlb_on=0 then goto nlb_inv_in_loop",
                "nlb_iseen_#nlb_iv$=nlb_ig",
                "if nlb_invhide_#nlb_iv$=0 then nlb_li_cinvs_#nlb_ln_cinvs$=nlb_iv & nlb_ln_cinvs=nlb_ln_cinvs+1",
                "nlb_iw=\"o\"+nlb_iv",
                "proc nlb_inv_in",
                "goto nlb_inv_in_loop",
                ":nlb_inv_in_end",
                "nlb_id=nlb_id-1",
                "end",
                ""));
        sb.append(achievementProc(nlbBuildingBlocks));
        return sb.toString();
    }

    private String objDisp(ObjBuildingBlocks obj) {
        if (StringHelper.notEmpty(obj.getObjDisp())) {
            // подпись предмета — одна строка: '^' (перевод строки INSTEAD) — пробел
            return obj.getObjDisp().replace(BR, " ").replaceAll(" {2,}", " ").strip();
        }
        return StringHelper.notEmpty(obj.getObjName()) ? urqString(obj.getObjName()) : obj.getObjId();
    }

    /** Текст для строки URQ в кавычках и для pln: & — разделитель команд, " — конец строки */
    /** Места ACHIEVE: номер места (в имени переменной nlb_achid_<имя>_<номер>) — по id модификации */
    private final java.util.Map<String, Integer> m_achieveSites = new java.util.HashMap<>();

    /**
     * Достижения (PlayerContext.achieve, nlb.lua setAchievement): ACHIEVE — nlb_an (имя), nlb_ak
     * (номер места) и proc nlb_achieve. Место засчитывается один раз (nlb_achid_<имя>_<место>),
     * число мест — nlb_ach_<имя> (ACHIEVED), порог — nlb_achmax_<имя> (ACHMAX, без него — первое
     * же место); набран порог — объявление в ответ (один раз, nlb_achgot_<имя>), затем perfect
     * game. UrqW между играми ничего не сохраняет — достижения живут, пока идёт игра
     */
    private String achievementProc(NLBBuildingBlocks nlbBuildingBlocks) {
        final String ls = LINE_SEPARATOR;
        StringBuilder sb = new StringBuilder();
        sb.append(":nlb_achieve").append(ls);
        sb.append("if nlb_achid_#%nlb_an$_#nlb_ak$=1 then goto nlb_achieve_got").append(ls);
        sb.append("nlb_achid_#%nlb_an$_#nlb_ak$=1").append(ls);
        sb.append("nlb_ach_#%nlb_an$ = nlb_ach_#%nlb_an$+1").append(ls);
        sb.append(":nlb_achieve_got").append(ls);
        sb.append("if nlb_achgot_#%nlb_an$=1 then goto nlb_achieve_perf").append(ls);
        sb.append("if nlb_achmax_#%nlb_an$>0 and nlb_ach_#%nlb_an$<nlb_achmax_#%nlb_an$ then goto nlb_achieve_perf").append(ls);
        sb.append("nlb_achgot_#%nlb_an$=1").append(ls);
        sb.append("nlb_reply=nlb_reply+\"").append(urqString(achievementAnnouncement(nlbBuildingBlocks.getLang())))
                .append("#%nlb_an$<br>\"").append(ls);
        sb.append(":nlb_achieve_perf").append(ls);
        String perfect = nlbBuildingBlocks.getAchievementNamePerfectGame();
        if (StringHelper.notEmpty(perfect)) {
            // все прочие достижения книги набрали свой порог (без ACHMAX — 1)
            for (String name : nlbBuildingBlocks.getAchievements()) {
                if (!name.equals(perfect)) {
                    sb.append("if nlb_ach_").append(name).append("<1 then goto nlb_achieve_end").append(ls);
                    sb.append("if nlb_ach_").append(name).append("<nlb_achmax_").append(name).append(" then goto nlb_achieve_end").append(ls);
                }
            }
            sb.append("if nlb_achid_").append(perfect).append("_#nlb_ak$=1 then goto nlb_achieve_pgot").append(ls);
            sb.append("nlb_achid_").append(perfect).append("_#nlb_ak$=1").append(ls);
            sb.append("nlb_ach_").append(perfect).append(" = nlb_ach_").append(perfect).append("+1").append(ls);
            sb.append(":nlb_achieve_pgot").append(ls);
            sb.append("if nlb_achgot_").append(perfect).append("=1 then goto nlb_achieve_end").append(ls);
            sb.append("nlb_achgot_").append(perfect).append("=1").append(ls);
            sb.append("nlb_reply=nlb_reply+\"").append(urqString(achievementAnnouncement(nlbBuildingBlocks.getLang())))
                    .append(urqString(perfect)).append("<br>\"").append(ls);
        }
        sb.append(":nlb_achieve_end").append(ls);
        sb.append("end").append(ls);
        return sb.toString();
    }

    /** Вызов подпрограммы списка: nlb_lname — имя, nlb_lval — значение */
    private static String listCall(String proc, String listName, String value) {
        return "nlb_lname=\"" + listName + "\"" + LINE_SEPARATOR + assignment("nlb_lval", value) + "proc " + proc + LINE_SEPARATOR;
    }

    /** «Сам» как элемент списка: объект — номер, страница — ссылка «pN» строкой (pageRef) */
    private String selfValue() {
        return selfIsObj() ? key(selfId()) : "\"" + pageRef(selfId()) + "\"";
    }

    /**
     * Ссылка на страницу (элемент списка SPUSH/SINJECT, «сам» в TAG/GETTAG): «p» и номер страницы.
     * Имя переменной из id страницы (nlb_tag_<id>) UrqW не читает: подстановка #%…$ разбирает
     * его как выражение, и дефисы UUID становятся вычитанием; nlb_tag_p17 читается, как
     * nlb_tag_N объекта — так выбирается картинка страницы по тегу (TAG $room …)
     */
    private String pageRef(String pageId) {
        return "p" + getExportPageNumber(pageId);
    }

    /** Объект в имени переменной (nlb_in_N, nlb_tag_N): известный — номер, из переменной — #имя$, без них — сам */
    private String nameRef(String objId, String objVar) {
        if (objId != null) {
            return key(objId);
        }
        if (StringHelper.notEmpty(objVar)) {
            return "#%" + objVar + "$";
        }
        return selfIsObj() ? key(selfId()) : pageRef(selfId());
    }

    private static String urqString(String text) {
        if (StringHelper.isEmpty(text)) {
            return EMPTY_STRING;
        }
        return text.replace("&", "##38$").replace("\"", "##34$");
    }

    /** Добавить к ответу (выводится после повторного показа страницы) */
    private static String reply(String text) {
        return "nlb_reply=nlb_reply+\"" + text + "<br>\"" + LINE_SEPARATOR;
    }

    /**
     * Объект N — служебные метки (подпрограммы): _dsc (описание со ссылкой и содержимое
     * контейнера; условие объекта скрывает и содержимое), _text (описание — в ответ, для PDSC),
     * _act (действие: текст действия, модификации, общий объект; переносимый — в инвентарь),
     * _acttext, _mods, _use (применения к nlb_t), _nouse; use_nlbo_N — щелчок по предмету.
     */
    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        final String id = objBlocks.getObjId();
        final int n = m_keys.get(id);
        final String prefix = ":nlb_o_" + n;
        final String common = objBlocks.getCommonObjId();
        StringBuilder sb = new StringBuilder();
        sb.append("; Object -- ").append(objBlocks.getObjName()).append(LINE_SEPARATOR);
        sb.append(prefix).append("_dsc").append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(objBlocks.getObjConstraint())) {
            sb.append("if not (").append(objBlocks.getObjConstraint()).append(") then end").append(LINE_SEPARATOR);
        }
        if (objBlocks.isGraphical() || objBlocks.isImageInScene()) {
            // объект с картинкой в нескольких контейнерах (держатели карт «Фронтира») — на экране
            // один раз, вместе с содержимым, как сцена плеера (PlayerEngine.sceneObjects)
            sb.append("if nlb_seen_").append(n).append("=nlb_gen then end").append(LINE_SEPARATOR);
            sb.append("nlb_seen_").append(n).append("=nlb_gen").append(LINE_SEPARATOR);
        }
        // ссылка без текста ({} у графического объекта) — подписью объекта: «[[|метка]]» UrqW
        // ссылкой не считает
        final String label = StringHelper.notEmpty(objBlocks.getObjDisp()) ? objBlocks.getObjDisp()
                : StringHelper.notEmpty(objBlocks.getObjName()) ? urqString(objBlocks.getObjName()) : "…";
        final String text = objBlocks.getObjText().replace("[[|nlb_act_", "[[" + label + "|nlb_act_");
        if (objBlocks.isImageInScene() && !objBlocks.isGraphical() && !text.isEmpty()) {
            // картинка объекта в сцене — перед его описанием (в INSTEAD — в начале ссылки; ссылка
            // UrqW — только текст)
            sb.append(showImage(String.valueOf(n), objBlocks.getObjImages()));
        }
        if (!text.isEmpty()) {
            sb.append("pln ").append(text).append(LINE_SEPARATOR);
        } else if (objBlocks.isImageInScene() && !objBlocks.isGraphical()) {
            // объект без описания, но с картинкой (фон боя во «Фронтире») — одна картинка, как в плеере
            sb.append(showImage(String.valueOf(n), objBlocks.getObjImages()));
        } else if (objBlocks.isGraphical() && StringHelper.notEmpty(objBlocks.getObjDisp())) {
            // графический объект (VN) без текста щёлкают по картинке, а картинок здесь нет
            sb.append("pln [[").append(objBlocks.getObjDisp()).append("|nlb_act_").append(n).append("]]").append(LINE_SEPARATOR);
        }
        sb.append("nlb_where=\"o").append(n).append("\"").append(LINE_SEPARATOR);
        sb.append("proc nlb_show_in").append(LINE_SEPARATOR);
        sb.append("end").append(LINE_SEPARATOR);

        // _text — описание в ответ (PDSC): и у объекта с suppressDsc (objDscF в плеере)
        final String textF = objBlocks.getObjTextF().replace("[[|nlb_act_", "[[" + label + "|nlb_act_");
        sb.append(prefix).append("_text").append(LINE_SEPARATOR);
        if (!textF.isEmpty()) {
            sb.append(reply(textF));
        }
        sb.append("end").append(LINE_SEPARATOR);
        // _on — объект включён (условие объекта): выключенный не виден и в инвентаре
        sb.append(prefix).append("_on").append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(objBlocks.getObjConstraint())) {
            sb.append("nlb_on=0").append(LINE_SEPARATOR);
            sb.append("if ").append(objBlocks.getObjConstraint()).append(" then nlb_on=1").append(LINE_SEPARATOR);
        } else {
            sb.append("nlb_on=1").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);
        if (!objBlocks.isGraphical()) {
            sb.append(imageProc(String.valueOf(n), objBlocks.getObjImages()));
        }
        sb.append(soundProc(String.valueOf(n), objBlocks.getObjSounds(), false));

        sb.append(":nlb_act_").append(n).append(LINE_SEPARATOR);
        sb.append("nlb_o=").append(n).append(LINE_SEPARATOR);
        sb.append("goto nlb_act").append(LINE_SEPARATOR);
        sb.append(":use_nlbo_").append(n).append(LINE_SEPARATOR);
        sb.append("nlb_o2=").append(n).append(LINE_SEPARATOR);
        sb.append("goto nlb_invclick").append(LINE_SEPARATOR);

        sb.append(prefix).append("_acttext").append(LINE_SEPARATOR);
        sb.append(objBlocks.getObjActStart());
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(prefix).append("_mods").append(LINE_SEPARATOR);
        sb.append(objBlocks.getObjModifications());
        sb.append(objBlocks.getObjVariable());
        sb.append("end").append(LINE_SEPARATOR);

        // _acta — действие без «взять» (так его вызывают и callback-объекты в конце хода)
        sb.append(prefix).append("_acta").append(LINE_SEPARATOR);
        sb.append("proc nlb_o_").append(n).append("_acttext").append(LINE_SEPARATOR);
        sb.append("proc nlb_o_").append(n).append("_mods").append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(common) && m_keys.containsKey(common)) {
            int c = m_keys.get(common);
            if (StringHelper.isEmpty(objBlocks.getObjActStart())) {
                sb.append("proc nlb_o_").append(c).append("_acttext").append(LINE_SEPARATOR);
            }
            sb.append("proc nlb_o_").append(c).append("_mods").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);
        sb.append(prefix).append("_act").append(LINE_SEPARATOR);
        sb.append("proc nlb_o_").append(n).append("_acta").append(LINE_SEPARATOR);
        if (objBlocks.isTakable()) {
            String toTake = StringHelper.notEmpty(common) && m_keys.containsKey(common) && Boolean.TRUE.equals(m_takable.get(common)) ? common : id;
            sb.append("nlb_po=").append(m_keys.get(toTake)).append(LINE_SEPARATOR);
            sb.append("nlb_tk=1").append(LINE_SEPARATOR);
            sb.append("proc nlb_take").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(prefix).append("_use").append(LINE_SEPARATOR);
        int u = 0;
        for (UseBuildingBlocks use : objBlocks.getUseBuildingBlocks()) {
            u++;
            final String next = "nlb_o_" + n + "_use_" + (u + 1);
            final boolean hasSuccess = StringHelper.notEmpty(use.getUseSuccessText());
            final boolean useConstrained = StringHelper.notEmpty(use.getUseConstraint());
            sb.append(":nlb_o_").append(n).append("_use_").append(u).append(LINE_SEPARATOR);
            sb.append("if nlb_t<>").append(use.getUseTarget()).append(" then goto ").append(next).append(LINE_SEPARATOR);
            if (useConstrained) {
                sb.append("if not (").append(use.getUseConstraint()).append(") then goto nlb_o_").append(n).append("_use_").append(u).append("_fail").append(LINE_SEPARATOR);
            }
            if (hasSuccess) {
                sb.append(reply(use.getUseSuccessText()));
                sb.append("nlb_printed=1").append(LINE_SEPARATOR);
            }
            sb.append(use.getUseModifications());
            sb.append(use.getUseVariable());
            sb.append("goto ").append(next).append(LINE_SEPARATOR);
            if (useConstrained) {
                sb.append(":nlb_o_").append(n).append("_use_").append(u).append("_fail").append(LINE_SEPARATOR);
                if (hasSuccess && StringHelper.notEmpty(use.getUseFailureText())) {
                    sb.append(reply(use.getUseFailureText()));
                    sb.append("nlb_printed=1").append(LINE_SEPARATOR);
                }
            }
        }
        sb.append(":nlb_o_").append(n).append("_use_").append(u + 1).append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(common) && m_keys.containsKey(common)) {
            sb.append("if nlb_printed=0 then proc nlb_o_").append(m_keys.get(common)).append("_use").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(prefix).append("_nouse").append(LINE_SEPARATOR);
        String nouse = objBlocks.getObjNouse();
        sb.append(reply(StringHelper.notEmpty(nouse) ? nouse : m_gameNouseText));
        sb.append("end").append(LINE_SEPARATOR);
        return sb.toString();
    }

    private final java.util.Map<String, Integer> m_keys = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> m_takable = new java.util.HashMap<>();
    private List<ObjBuildingBlocks> m_orderedObjs = new java.util.ArrayList<>();

    @Override
    protected String generateNLBText(final NLBBuildingBlocks nlbBlocks) throws com.nlbhub.nlb.exception.NLBConsistencyException {
        m_orderedObjs = orderedObjects(nlbBlocks);
        java.util.Map<String, String> idByName = new java.util.HashMap<>();
        for (int i = 0; i < m_orderedObjs.size(); i++) {
            ObjBuildingBlocks obj = m_orderedObjs.get(i);
            m_keys.put(obj.getObjId(), i + 1);
            m_takable.put(obj.getObjId(), obj.isTakable());
            if (StringHelper.notEmpty(obj.getObjName())) {
                idByName.put(obj.getObjName(), obj.getObjId());
            }
        }
        String text = super.generateNLBText(nlbBlocks);
        java.util.regex.Matcher ckeys = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote(CKEY_MARK) + "(.*?)" + java.util.regex.Pattern.quote(END_MARK)).matcher(text);
        StringBuilder withKeys = new StringBuilder();
        while (ckeys.find()) {
            ckeys.appendReplacement(withKeys, java.util.regex.Matcher.quoteReplacement(containerKey(ckeys.group(1))));
        }
        ckeys.appendTail(withKeys);
        text = withKeys.toString();
        // метки, известные только теперь: id объекта -> номер, имя объекта -> номер
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(" + java.util.regex.Pattern.quote(KEY_MARK) + "|" + java.util.regex.Pattern.quote(NAME_MARK) + ")(.*?)"
                        + java.util.regex.Pattern.quote(END_MARK)).matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String id = KEY_MARK.equals(matcher.group(1)) ? matcher.group(2) : idByName.get(matcher.group(2));
            Integer n = id == null ? null : m_keys.get(id);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(n == null ? "0" : String.valueOf(n)));
        }
        matcher.appendTail(result);
        // a % b -> (a-b*int(a/b))
        String withMod = result.toString();
        String previous;
        do {
            previous = withMod;
            withMod = MOD_OPERANDS.matcher(withMod).replaceAll("($1-$2*int($1/$2))");
        } while (!withMod.equals(previous));
        return withMod;
    }

    /** Ключ контейнера (см. ckey): объект N — oN, страница — pN */
    private String containerKey(String containerId) {
        Integer n = m_keys.get(containerId);
        return n != null ? "o" + n : pageRef(containerId);
    }

    // --- части объекта (ExportManager.createObjBuildingBlocks)

    @Override
    protected String decorateObjName(String name, String id) {
        return name == null ? EMPTY_STRING : name;
    }

    @Override
    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        return dispText == null ? EMPTY_STRING : dispText.replaceAll("(?m)^pln ?", EMPTY_STRING).replace(LINE_SEPARATOR, " ").strip();
    }

    /** Описание объекта: часть в {фигурных скобках} — ссылка на nlb_act_N */
    @Override
    protected String decorateObjText(String objId, String objName, boolean suppressDsc, String objText, boolean imageEnabled, boolean isGraphicalObj) {
        if (suppressDsc || StringHelper.isEmpty(objText)) {
            return EMPTY_STRING;
        }
        // строки текста — «pln …» (пустая — «pln»): в описании объекта — одной строкой
        String text = objText.replaceAll("(?m)^pln ?", EMPTY_STRING).replace(LINE_SEPARATOR, " ").strip();
        return text.replaceAll("\\{([^}]*)\\}",
                java.util.regex.Matcher.quoteReplacement("[[") + "$1" + java.util.regex.Matcher.quoteReplacement("|nlb_act_" + key(objId) + "]]"));
    }

    @Override
    protected String decorateObjActStart(String actTextExpanded, String commonObjId) {
        String text = actTextExpanded == null ? EMPTY_STRING : actTextExpanded.replace(LINE_SEPARATOR, " ").replace("pln ", EMPTY_STRING).trim();
        return text.isEmpty() ? EMPTY_STRING : reply(text);
    }

    @Override
    protected String decorateObjNouse(String nouseTextExpanded) {
        return nouseTextExpanded == null ? EMPTY_STRING : nouseTextExpanded.replace(LINE_SEPARATOR, " ").replace("pln ", EMPTY_STRING).trim();
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
        return variableName + "=1" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateContainedObjId(String containedObjId) {
        return containedObjId;
    }

    @Override
    protected String decorateUseTarget(String targetId) {
        return key(targetId);
    }

    @Override
    protected String decorateUseModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateUseVariable(String variableName) {
        return variableName + "=1" + LINE_SEPARATOR;
    }

    /**
     * Страница — три метки. Вход «:id»: модификации страницы (до текста, как enter в INSTEAD),
     * затем показ и ход. Показ «:id_show» (подпрограмма): заголовок, текст и объекты страницы — его
     * повторяет и действие с объектом (nlb_redraw), уже без модификаций входа. Ход «:id_turn»:
     * подписи предметов, автоссылки, кнопки.
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
            // initf(): таймер страницы обнуляется после её модификаций; переход — не тик
            stringBuilder.append(pageBlocks.getPageTimerVariableInit());
            stringBuilder.append("nlb_tick=0").append(LINE_SEPARATOR);
        }
        stringBuilder.append("nlb_page = \"").append(pageId).append("\"").append(LINE_SEPARATOR);
        final String ref = pageRef(pageId);
        stringBuilder.append("nlb_pkey = \"").append(ref).append("\"").append(LINE_SEPARATOR);
        if (!pageBlocks.getPageSounds().isEmpty()) {
            // initf(): звук страницы — при входе, после модификаций
            stringBuilder.append("proc nlb_snd_").append(ref).append(LINE_SEPARATOR);
        }
        stringBuilder.append("proc ").append(pageId).append("_show").append(LINE_SEPARATOR);
        stringBuilder.append("goto ").append(pageId).append("_turn").append(LINE_SEPARATOR);
        stringBuilder.append(":").append(pageId).append("_show").append(LINE_SEPARATOR);
        stringBuilder.append("nlb_gen=nlb_gen+1").append(LINE_SEPARATOR);  // объекты с картинкой — один раз за показ
        if (hasTimer) {
            // тик таймера — ход игрока на странице (nlb_redraw), до показа: текст видит новое значение
            stringBuilder.append("if nlb_tick=1 then ").append(pageBlocks.getPageTimerVariable());
            stringBuilder.append("nlb_tick=0").append(LINE_SEPARATOR);
        }
        stringBuilder.append(pageBlocks.getPageCaption());
        // Картинка — при каждом показе: тег мог измениться. Служебная страница — без текста, её
        // сразу покидают по автоссылке — картинок (своей и объектов) не показывает, как в плеере
        // (PlayerEngine.followLink); goto UrqW экран не очищает, и убрать картинку потом нельзя —
        // поэтому условие автоссылки проверяется до показа (nlb_leave). Объекты страницы, которую
        // сейчас покинут, не показываются и при тексте (nlb_gone): сцена плеера — только у
        // страницы, где остановились (ход боя «Фронтира» идёт через несколько страниц)
        stringBuilder.append("nlb_leave=0").append(LINE_SEPARATOR);
        final String leave = leaveCondition(linksBlocksOf(pageBlocks));
        if (leave != null) {
            stringBuilder.append(leave.isEmpty() ? "nlb_leave=1" + LINE_SEPARATOR : leave);
            stringBuilder.append("nlb_gone=nlb_leave").append(LINE_SEPARATOR);
            if (pageBlocks.isHasPageText()) {
                stringBuilder.append("nlb_leave=0").append(LINE_SEPARATOR);
            }
        }
        stringBuilder.append(showImage(ref, pageBlocks.getPageImages()));
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append("nlb_where = \"").append(ref).append("\"").append(LINE_SEPARATOR);
        stringBuilder.append(leave != null ? "if nlb_gone=0 then " : "").append("proc nlb_show_in").append(LINE_SEPARATOR);
        stringBuilder.append("end").append(LINE_SEPARATOR);
        stringBuilder.append(":").append(pageId).append("_turn").append(LINE_SEPARATOR);
        // конец хода (callback-объекты); их текст действия — в ответ, он выводится сразу
        stringBuilder.append("proc nlb_turn").append(LINE_SEPARATOR);
        stringBuilder.append("if nlb_reply<>\"\" then pln #%nlb_reply$").append(LINE_SEPARATOR);
        stringBuilder.append("nlb_reply=\"\"").append(LINE_SEPARATOR);
        stringBuilder.append("proc nlb_disp_all").append(LINE_SEPARATOR);
        stringBuilder.append("proc nlb_inv_sync").append(LINE_SEPARATOR);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        // Автоссылки — до кнопок: переход срабатывает сам, как в INSTEAD, и кнопки этой страницы
        // уже не выводятся. goto в UrqW не очищает экран, поэтому текст страницы остаётся виден
        // перед текстом следующей (как в плеере). Первая автоссылка с выполненным условием
        // уводит со страницы.
        boolean unconditionalAuto = appendAutoLinks(stringBuilder, linksBlocks);
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (linkBlocks.isAuto() || unconditionalAuto) {
                continue;
            }
            if (!StringHelper.isEmpty(linkBlocks.getLinkConstraint())) {
                stringBuilder.append("if ").append(linkBlocks.getLinkConstraint()).append(" then ");
            }
            if (linkBlocks.hasShownAltText()) {
                // альтернативный текст недоступной ссылки (#135): «if … then btn … else pln …» —
                // одной строкой, else в UrqW — до конца строки
                stringBuilder.append(linkBlocks.getLinkStart().stripTrailing());
                stringBuilder.append(" else pln ").append(linkBlocks.getLinkAltText()).append(LINE_SEPARATOR);
            } else {
                stringBuilder.append(linkBlocks.getLinkStart());
            }
        }
        String noChoices = unconditionalAuto ? null : noChoicesCondition(pageBlocks);
        if (noChoices != null) {
            // Выбирать нечего, а автоссылки ждут таймера — время проматывается (как в плеере):
            // тик за тиком, пока не сработает автоссылка
            String wait = pageId + "_wait";
            if (!noChoices.isEmpty()) {
                stringBuilder.append("if not ").append(noChoices).append(" then goto ").append(wait).append(LINE_SEPARATOR);
                stringBuilder.append("goto ").append(wait).append("_end").append(LINE_SEPARATOR);
            }
            stringBuilder.append(":").append(wait).append(LINE_SEPARATOR);
            stringBuilder.append("nlb_wait=0").append(LINE_SEPARATOR);
            stringBuilder.append(":").append(wait).append("_loop").append(LINE_SEPARATOR);
            stringBuilder.append("if nlb_wait>=").append(pageBlocks.getTimerWaitTicks()).append(" then goto ")
                    .append(wait).append("_end").append(LINE_SEPARATOR);
            stringBuilder.append("nlb_wait=nlb_wait+1").append(LINE_SEPARATOR);
            stringBuilder.append(pageBlocks.getPageTimerVariable());
            appendAutoLinks(stringBuilder, linksBlocks);
            stringBuilder.append("goto ").append(wait).append("_loop").append(LINE_SEPARATOR);
            stringBuilder.append(":").append(wait).append("_end").append(LINE_SEPARATOR);
        }
        stringBuilder.append(pageBlocks.getPageEnd());
        if (needsImageProc(pageBlocks.getPageImages())) {
            stringBuilder.append(imageProc(ref, pageBlocks.getPageImages()));
        }
        stringBuilder.append(soundProc(ref, pageBlocks.getPageSounds(), true));
        return stringBuilder.toString();
    }

    /**
     * Автоссылки страницы: первая с выполненным условием уводит со страницы. true — есть
     * безусловная (после неё ничего не выполнится)
     */
    private static List<LinkBuildingBlocks> linksBlocksOf(PageBuildingBlocks pageBlocks) {
        return pageBlocks.getLinksBuildingBlocks();
    }

    /**
     * Строки, которые ставят nlb_leave=1, если сработает автоссылка (условия — как в
     * appendAutoLinks); null — автоссылок нет, "" — есть автоссылка без условия
     */
    private String leaveCondition(List<LinkBuildingBlocks> linksBlocks) {
        StringBuilder sb = new StringBuilder();
        boolean hasAuto = false;
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (linkBlocks.isAuto()) {
                hasAuto = true;
                String constraint = linkBlocks.getLinkConstraint();
                if (linkBlocks.isNeedsAction()) {
                    String hasAction = "(" + ACTION_COUNT_VAR + ">=" + getNeedsActionCount() + ")";
                    constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") and " + hasAction;
                }
                if (StringHelper.isEmpty(constraint)) {
                    return EMPTY_STRING;
                }
                sb.append("if ").append(constraint).append(" then nlb_leave=1").append(LINE_SEPARATOR);
            }
        }
        return hasAuto ? sb.toString() : null;
    }

    private boolean appendAutoLinks(StringBuilder stringBuilder, List<LinkBuildingBlocks> linksBlocks) {
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (linkBlocks.isAuto()) {
                String constraint = linkBlocks.getLinkConstraint();
                if (linkBlocks.isNeedsAction()) {
                    // has_action(): автоссылка ждёт, пока игрок сделает нужное число действий
                    String hasAction = "(" + ACTION_COUNT_VAR + ">=" + getNeedsActionCount() + ")";
                    constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") and " + hasAction;
                }
                if (StringHelper.isEmpty(constraint)) {
                    stringBuilder.append("goto ").append(linkBlocks.getLinkId()).append(LINE_SEPARATOR);
                    // дальше ничего не выполнится: остальные ссылки не нужны
                    return true;
                }
                stringBuilder.append("if ").append(constraint).append(" then ");
                stringBuilder.append("goto ").append(linkBlocks.getLinkId()).append(LINE_SEPARATOR);
            }
        }
        return false;
    }

    /**
     * Условие «выбирать нечего» для проматывания таймера (PlayerEngine.autoWaitTicks): ни одна
     * обычная ссылка не доступна — его отрицание. null — не проматывать (нет таймера или
     * автоссылок, либо есть ссылка без условия), пустая строка — обычных ссылок нет
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
                any.append(any.length() > 0 ? " or " : "").append("(").append(linkBlocks.getLinkConstraint()).append(")");
            }
        }
        if (!hasAuto) {
            return null;
        }
        return any.length() == 0 ? "" : "(" + any + ")";
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        StringBuilder postPage = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : pageBlocks.getLinksBuildingBlocks()) {
            postPage.append(linkBlocks.getLinkComment());
            postPage.append(linkBlocks.getLinkLabel());
            if (!linkBlocks.isTechnical()) {
                // переход по обычной ссылке — действие игрока (для автоссылок «needs action»);
                // он же снимает выбор предмета для применения
                postPage.append(ACTION_COUNT_VAR).append("=").append(ACTION_COUNT_VAR).append("+1").append(LINE_SEPARATOR);
                postPage.append("nlb_cnt_walk=nlb_cnt_walk+1").append(LINE_SEPARATOR);
                postPage.append("nlb_sel=0").append(LINE_SEPARATOR);
            } else if (linkBlocks.isAuto() && linkBlocks.isNeedsAction()) {
                // has_action() сбрасывает счётчики действий — те же, что COUNTRST (как в плеере)
                postPage.append(decorateCountResetOperation());
            }
            postPage.append(linkBlocks.getLinkModifications());
            postPage.append(linkBlocks.getLinkVariable());
            postPage.append(linkBlocks.getLinkVisitStateVariable());
            postPage.append(linkBlocks.getLinkGoTo());
        }
        return postPage.toString();
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        return "btn " + linkId + ", " + linkText + LINE_SEPARATOR;
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
        return "goto " + linkTarget + LINE_SEPARATOR + LINE_SEPARATOR;
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

    /**
     * @return
     */
    @Override
    protected String decorateNot() {
        return "not ";
    }

    /**
     * @return
     */
    @Override
    protected String decorateOr() {
        return "or";
    }

    /**
     * @return
     */
    @Override
    protected String decorateAnd() {
        return "and";
    }

    @Override
    protected String decorateExistence(final String decoratedVariable) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    /**
     * @param constraintVar
     * @return
     */
    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return "(" + constraintVar + ">0)";//"(" + constraintVar + " == 1)";
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return constraintVar;
    }

    /**
     * @return
     * @param isFinish
     */
    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return "end" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return ":" + linkId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "; Link -- " + comment + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @return
     */
    @Override
    protected String decorateLinkVariable(String variableName) {
        return "    " + variableName + "=1" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return "    " + linkVisitStateVariable + "=1" + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @return
     */
    @Override
    protected String decoratePageVariable(final String variableName) {
        return variableName + "=1" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTimerVariableInit(final String variableName) {
        // таймер страницы (tvarid): обнуляется при входе, +1 за ход игрока на странице
        return StringHelper.isEmpty(variableName) ? Constants.EMPTY_STRING : variableName + "=0" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTimerVariable(final String variableName) {
        return StringHelper.isEmpty(variableName) ? Constants.EMPTY_STRING
                : variableName + "=" + variableName + "+1" + LINE_SEPARATOR;
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

    @Override
    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return (
                    "pln " + caption.toUpperCase() + LINE_SEPARATOR
                            + "pln " + StringHelper.createRepeatedString(caption.length(), "-") + LINE_SEPARATOR
                            + "pln" + LINE_SEPARATOR
            );
        } else {
            return "";
        }
    }

    @Override
    protected String decoratePageNotes(String notes) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme) {
        // картинка выбирается при показе страницы (generatePageText, showImage)
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        return Constants.EMPTY_STRING;
    }

    /**
     * Подпись кнопки и альтернативный текст ссылки — одной строкой и без «pln »: раньше подпись
     * шла через expandVariables текста страницы, и кнопка называлась «pln Открыть дверь» (UrqW
     * берёт подписью всё после первой запятой btn)
     */
    /** Переменная в тексте UrqW: #имя$ (строковая — #%имя$) — значение подставляется при выводе */
    private String urqVariable(String name) {
        return "#" + (getVariableDataType(name) == Variable.DataType.STRING ? "%" : EMPTY_STRING) + name + "$";
    }

    @Override
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    // подпись кнопки — одна строка: '^' — пробел
                    result.append(urqString(textChunk.getText()).replace("^", " "));
                    break;
                case VARIABLE:
                    result.append(urqVariable(textChunk.getText()));
                    break;
                case ACTION_TEXT:
                    break;
                case NEWLINE:
                    result.append(" ");
                    break;
            }
        }
        return result.toString().strip();
    }

    /** Тексты объектов (общий код вызывает эту версию) — так же, как текст страницы */
    @Override
    protected String expandVariables(List<TextChunk> textChunks) {
        return expandVariables(textChunks, null);
    }

    /** Перевод строки в тексте UrqW — '^' INSTEAD */
    private static final String BR = "<br>";
    private static final java.util.regex.Pattern TRAILING_BR = java.util.regex.Pattern.compile("(\\s*<br>)+\\s*(?=\\r\\n|$)");

    /** Текст страницы: каждая строка — pln (раньше pln ставился перед каждым куском текста) */
    @Override
    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        StringBuilder result = new StringBuilder("pln ");
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    // '^' — перевод строки INSTEAD (как в Java-плеере): <br> в строке pln
                    result.append(urqString(textChunk.getText()).replace("^", BR));
                    break;
                case VARIABLE:
                    result.append(urqVariable(textChunk.getText()));
                    break;
                case ACTION_TEXT:
                    break;
                case NEWLINE:
                    result.append(getLineSeparator()).append("pln ");
                    break;
            }
        }
        // хвостовые '^' пустой строки не дают (плеер обрезает текст справа)
        return TRAILING_BR.matcher(result.toString()).replaceAll("");
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        return LINE_SEPARATOR;
    }

    /**
     * @param labelText
     * @param pageNumber
     * @param theme
     * @return
     */
    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        return ":" + labelText + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "; Page -- " + comment + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @param variableValue
     * @return
     */
    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return assignment(variableName, variableValue);
    }

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        // тег — nlb_tag_<номер объекта> (из переменной — её значение; без них — свой)
        return "nlb_tag_" + nameRef(objId, variable) + "=\"" + urqString(tag) + "\"" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        // тег страницы из переменной не читается: имя с «-» UrqW разбирает как вычитание
        return assignment(resultingVariable, "nlb_tag_" + nameRef(objId, objVariableName));
    }

    @Override
    protected String decorateWhile(final String constraint) {
        Block block = new Block(true);
        m_openBlocks.push(block);
        return ":" + block.loopLabel() + LINE_SEPARATOR
                + "if not (" + constraint + ") then goto " + block.endLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIf(final String constraint) {
        Block block = new Block(false);
        m_openBlocks.push(block);
        return "if not (" + constraint + ") then goto " + block.branchLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        // в инвентаре — nlb_at_N_inv (объект из переменной — её значение, номер объекта)
        Block block = new Block(false);
        m_openBlocks.push(block);
        String condition = "nlb_at_" + nameRef(objId, objVar) + "_" + INV_KEY + "=1";
        return "if not (" + condition + ") then goto " + block.branchLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElse() {
        Block block = m_openBlocks.peek();
        if (block == null) {
            return EMPTY_STRING;
        }
        String result = "goto " + block.endLabel() + LINE_SEPARATOR + ":" + block.branchLabel() + LINE_SEPARATOR;
        block.m_branch++;
        return result;
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        Block block = m_openBlocks.peek();
        if (block == null) {
            return EMPTY_STRING;
        }
        String result = "goto " + block.endLabel() + LINE_SEPARATOR + ":" + block.branchLabel() + LINE_SEPARATOR;
        block.m_branch++;
        return result + "if not (" + constraint + ") then goto " + block.branchLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateEnd() {
        Block block = m_openBlocks.poll();
        if (block == null) {
            return EMPTY_STRING;
        }
        if (block.m_loop) {
            return "goto " + block.loopLabel() + LINE_SEPARATOR + ":" + block.endLabel() + LINE_SEPARATOR;
        }
        // метка последней ветки нужна, если ELSE не было (на неё переходит ложное условие)
        return ":" + block.branchLabel() + LINE_SEPARATOR + ":" + block.endLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateReturn(String returnValue) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateHaveOperation(String variableName, String objId, String objVar) {
        String name = variableName.replaceAll("^\\((.*)\\)$", "$1").replaceAll("\\s*>\\s*0$", "").replaceAll("\\s*=\\s*1$", "").trim();
        return name + "=0" + LINE_SEPARATOR + "if nlb_at_" + nameRef(objId, objVar) + "_" + INV_KEY + "=1 then " + name + "=1" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateCloneOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        // последний владелец объекта — ключ контейнера (pN, oN); нигде — пусто
        return assignment(variableName, "nlb_in_" + nameRef(objId, objVar));
    }

    @Override
    protected String decorateGetIdOperation(final String variableName, final String objId, final String objVar) {
        // прототип: клонов в экспорте нет — сам объект
        return assignment(variableName, objId != null ? key(objId) : objVar);
    }

    @Override
    protected String decorateDelObj(String destinationId, final String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        String ref = objectId != null ? key(objectId) : objectVar;
        if (destinationName != null) {
            // место — список: nlb:rmv(список, obj) и obj.container = nil (из контейнеров не убирается)
            return listCall("nlb_lrmv", destinationName, ref) + assignment("nlb_po", ref)
                    + "nlb_in_#nlb_po$=\"\"" + LINE_SEPARATOR;
        }
        // из получателя, без него — из текущей комнаты; в других контейнерах объект остаётся
        String destination = destinationId != null ? "\"" + ckey(destinationId) + "\"" : "nlb_pkey";
        return assignment("nlb_po", ref) + "nlb_pd=" + destination + LINE_SEPARATOR + "proc nlb_del" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return assignment("nlb_po", objectId != null ? key(objectId) : objectVar) + "nlb_pd=\"" + INV_KEY + "\"" + LINE_SEPARATOR
                + "proc nlb_del" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        // без получателя — в себя (страницу или объект-контейнер; s в INSTEAD); из прежних мест
        // не убирается (objs():add); дубль (клон в INSTEAD) — не поддерживается
        return assignment("nlb_po", objectId != null ? key(objectId) : objectVar)
                + "nlb_pd=\"" + ckey(destinationId != null ? destinationId : selfId()) + "\"" + LINE_SEPARATOR
                + "proc nlb_add" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        // take: из текущей комнаты — в инвентарь; уже в инвентаре — ничего (клонов нет)
        return assignment("nlb_po", objectId != null ? key(objectId) : objectVar)
                + "nlb_tk=0" + LINE_SEPARATOR
                + "if nlb_at_#nlb_po$_" + INV_KEY + "=0 then proc nlb_take" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        // в контейнер, в список или (без получателя) — в себя
        String container = ckey(destinationId != null ? destinationId : selfId());
        return "nlb_lsrc=\"" + sourceListVariableName + "\"" + LINE_SEPARATOR
                + "nlb_ldst=\"" + (destinationListVariableName != null ? destinationListVariableName : "") + "\"" + LINE_SEPARATOR
                + "nlb_c=\"" + container + "\"" + LINE_SEPARATOR + "proc nlb_laddall" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        String container = srcObjId != null ? "\"" + srcObjId + "\"" : "nlb_id_#" + objectVar + "$";
        return assignment("nlb_c", container) + "nlb_lname=\"" + listVariableName + "\"" + LINE_SEPARATOR
                + "proc nlb_lobjs" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSSndOperation() {
        return playSoundOf(nameRef(null, null));
    }

    @Override
    protected String decorateWSndOperation() {
        // ww — объект, к которому применяют: в nlb_o_N_use это nlb_t (номер); вне применения — ничего
        return inUseLink() ? playSoundOf("#nlb_t$") : EMPTY_STRING;
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        return playSoundOf(nameRef(objectId, objectVar));
    }

    /** SND/SSND/WSND: звук объекта (страницы), если он есть; ref — номер, «pN» или подстановка */
    private static String playSoundOf(String ref) {
        return "if nlb_hassnd_" + ref + "=1 then proc nlb_snd_" + ref + LINE_SEPARATOR;
    }

    private static boolean needsImageProc(List<MediaChoice> images) {
        return images.size() > 1 || (images.size() == 1 && images.get(0).hasConstraint());
    }

    /**
     * Показать картинку (оператор image UrqW): одна без условия — сразу, иначе — выбор по тегу;
     * служебная страница, которую сейчас покинут (nlb_leave), картинок не показывает
     */
    private static String showImage(String ref, List<MediaChoice> images) {
        if (images.isEmpty()) {
            return EMPTY_STRING;
        }
        if (!needsImageProc(images)) {
            return "if nlb_leave=0 then image " + images.get(0).getPath() + LINE_SEPARATOR;
        }
        return "proc nlb_img_" + ref + LINE_SEPARATOR + "if nlb_leave=0 and nlb_img<>\"\" then image #%nlb_img$" + LINE_SEPARATOR;
    }

    /**
     * Картинка страницы или объекта (ModificationInterpreter.selectImage, pic в INSTEAD):
     * nlb_img_<ссылка> кладёт в nlb_img путь первой картинки, чьё условие совпало с тегом
     * nlb_tag_<ссылка> (без условия — подходит всегда), или ""
     */
    private static String imageProc(String ref, List<MediaChoice> images) {
        if (images.isEmpty()) {
            return EMPTY_STRING;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(":nlb_img_").append(ref).append(LINE_SEPARATOR);
        sb.append("nlb_img=\"\"").append(LINE_SEPARATOR);
        for (MediaChoice image : images) {
            sb.append("if nlb_img=\"\"");
            if (image.hasConstraint()) {
                sb.append(" and nlb_tag_").append(ref).append("=\"").append(urqString(image.getConstraint())).append("\"");
            }
            sb.append(" then nlb_img=\"").append(urqString(image.getPath())).append("\"").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);
        return sb.toString();
    }

    /**
     * Звук страницы или объекта (ModificationInterpreter.playSound, snd в INSTEAD): nlb_snd_<ссылка>.
     * Без условия — всегда, подряд идущие с условием — цепочка по тегу (nlb_sm — сработавший).
     * Музыка — переменная music UrqW (повторяется, тот же файл не перезапускается; "" — стоп),
     * эффект — play; у страницы эффект — фраза озвучки: при входе звучит одна, первая (nlb_voice)
     */
    private static String soundProc(String ref, List<MediaChoice> sounds, boolean page) {
        if (sounds.isEmpty()) {
            return EMPTY_STRING;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(":nlb_snd_").append(ref).append(LINE_SEPARATOR);
        if (page && sounds.stream().anyMatch(MediaChoice::isSfx)) {
            sb.append("nlb_voice=0").append(LINE_SEPARATOR);
        }
        for (List<MediaChoice> step : MediaChoice.soundSteps(sounds)) {
            if (step.size() == 1 && !step.get(0).hasConstraint()) {
                sb.append(soundStatement(step.get(0), page)).append(LINE_SEPARATOR);
                continue;
            }
            sb.append("nlb_sm=0").append(LINE_SEPARATOR);
            for (MediaChoice sound : step) {
                String condition = "nlb_sm=0 and nlb_tag_" + ref + "=\"" + urqString(sound.getConstraint()) + "\"";
                if (page && sound.isSfx() && !sound.isStop()) {
                    // вложенного if в однострочном if UrqW нет: фраза — отдельной строкой по nlb_pv
                    sb.append("if ").append(condition).append(" then nlb_sm=1 & nlb_pv=1").append(LINE_SEPARATOR);
                    sb.append("if nlb_pv=1 and nlb_voice=0 then nlb_voice=1 & play ").append(sound.getPath()).append(LINE_SEPARATOR);
                    sb.append("nlb_pv=0").append(LINE_SEPARATOR);
                } else {
                    sb.append("if ").append(condition).append(" then nlb_sm=1 & ").append(soundStatement(sound, page)).append(LINE_SEPARATOR);
                }
            }
        }
        sb.append("end").append(LINE_SEPARATOR);
        return sb.toString();
    }

    private static String soundStatement(MediaChoice sound, boolean page) {
        if (sound.isStop()) {
            return "music = \"\"";
        }
        if (!sound.isSfx()) {
            return "music = \"" + urqString(sound.getPath()) + "\"";
        }
        if (page) {
            return "if nlb_voice=0 then nlb_voice=1 & play " + sound.getPath();
        }
        return "play " + sound.getPath();
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        return listCall("nlb_lpush", listVariableName, selfValue());
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        return listCall("nlb_lpush", listVariableName, objectId != null ? key(objectId) : objectVar);
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        return "nlb_lname=\"" + listVariableName + "\"" + LINE_SEPARATOR + "proc nlb_lpop" + LINE_SEPARATOR
                + assignment(variableName, "nlb_lres");
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        return listCall("nlb_linject", listVariableName, selfValue());
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        return listCall("nlb_linject", listVariableName, objectId != null ? key(objectId) : objectVar);
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        return "nlb_lname=\"" + listVariableName + "\"" + LINE_SEPARATOR + "proc nlb_leject" + LINE_SEPARATOR
                + assignment(variableName, "nlb_lres");
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        if (destinationId != null) {
            return "nlb_c=\"" + ckey(destinationId) + "\"" + LINE_SEPARATOR + "proc nlb_clear" + LINE_SEPARATOR;
        } else if (destinationVar != null) {
            return "nlb_ln_" + destinationVar + "=0" + LINE_SEPARATOR;  // nlb:clear(список)
        }
        return "nlb_c=nlb_pkey" + LINE_SEPARATOR + "proc nlb_clear" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateClearInvOperation() {
        return "nlb_c=\"" + INV_KEY + "\"" + LINE_SEPARATOR + "proc nlb_clear" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        return assignment(variableName, "nlb_ln_" + listVariableName);
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        // rndN в UrqW — целое от 1 до N; #выражение$ UrqW вычисляет и подставляет в строку
        return variableName + "=rnd#" + maxValue + "$" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        return "nlb_achmax_" + achievementName + "=" + max + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        Integer site = m_achieveSites.computeIfAbsent(modificationId, k -> m_achieveSites.size() + 1);
        return "nlb_an=\"" + achievementName + "\"" + LINE_SEPARATOR + "nlb_ak=" + site + LINE_SEPARATOR
                + "proc nlb_achieve" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        return assignment(variableName, "nlb_ach_" + achievementName);
    }

    @Override
    protected String decorateGoToOperation(String locationId) {
        // goto уводит на страницу, оставшиеся модификации не выполняются: переход из модификаций
        // ссылки важнее цели ссылки (как в INSTEAD)
        return "goto " + locationId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        return assignment(variableName, countSum(statType));
    }

    /**
     * Присваивание выражения — с пробелами вокруг «=»: строку «x=(a-b)» UrqW принимает за вызов
     * функции (/^\S+\s*\(.*\)$/), вычисляет и отбрасывает — переменная не меняется
     * («Фронтир»: hjMod=(…), «Шапочка»: newFillingFound=(…), счётчики действий)
     */
    private static String assignment(String variableName, String value) {
        return variableName + " = " + value + LINE_SEPARATOR;
    }

    /** Сумма счётчиков действий игрока с последнего сброса (как PlayerContext.countGet): inv 1, act 2, use 4, walk 8 */
    private static String countSum(int statType) {
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < COUNT_TYPES.length; i++) {
            if (statType == 0 || (statType & (1 << i)) != 0) {
                if (sum.length() > 0) {
                    sum.append("+");
                }
                sum.append("(nlb_cnt_").append(COUNT_TYPES[i]).append("-nlb_cntr_").append(COUNT_TYPES[i]).append(")");
            }
        }
        return sum.length() > 0 ? sum.toString() : "0";
    }

    @Override
    protected String decorateCountResetOperation() {
        // сброс счётчиков действий — и для автоссылок «needs action»: они ждут действий игрока
        // с последнего сброса (как в плеере, PlayerEngine.hasAction)
        StringBuilder result = new StringBuilder();
        result.append(ACTION_COUNT_VAR).append("=0").append(LINE_SEPARATOR);
        for (String type : COUNT_TYPES) {
            result.append("nlb_cntr_").append(type).append("=nlb_cnt_").append(type).append(LINE_SEPARATOR);
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
        return "nlb_lname=\"" + listVariableName + "\"" + LINE_SEPARATOR + "proc nlb_lshuffle" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        // значение выражения — в ответ (#выражение$ UrqW подставляет при разборе строки)
        return "nlb_reply=nlb_reply+\"#" + variableName + "$\"" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        // описание объекта — в ответ (включённого); объект по имени, номер известен при сборке текста
        String n = NAME_MARK + objVariableName + END_MARK;
        return "proc nlb_o_" + n + "_on" + LINE_SEPARATOR + "if nlb_on=1 then proc nlb_o_" + n + "_text" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        if (objId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        // описания объектов с suppressDsc внутри объекта (ModificationInterpreter.pdscs)
        return "nlb_where=\"o" + key(objId) + "\"" + LINE_SEPARATOR + "proc nlb_pdscs" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        if (actingObjId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "proc nlb_o_" + key(actingObjId) + "_act" + LINE_SEPARATOR;
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
