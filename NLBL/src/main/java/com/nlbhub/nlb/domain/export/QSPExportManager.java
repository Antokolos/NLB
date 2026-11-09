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

    public QSPExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

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
        final List<ObjBuildingBlocks> objs = nlbBuildingBlocks.getObjsBuildingBlocks();
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
            String disp = StringHelper.isEmpty(obj.getObjDisp()) ? obj.getObjId() : obj.getObjDisp();
            sb.append("$nlb_disp['").append(obj.getObjId()).append("'] = '").append(disp).append("'").append(LINE_SEPARATOR);
            sb.append("$nlb_byname['").append(disp).append("'] = '").append(obj.getObjId()).append("'").append(LINE_SEPARATOR);
        }
        if (!pages.isEmpty()) {
            sb.append("GOTO '").append(pages.get(0).getPageId()).append("'").append(LINE_SEPARATOR);
        }
        sb.append("-").append(LINE_SEPARATOR);
        sb.append(SERVICE_LOCATIONS.replace("\n", LINE_SEPARATOR)
                .replace("%GAME_INV%", m_gameInvText).replace("%NLB_INV%", INVENTORY)
                .replace("%COUNT%", ACTION_COUNT_VAR));
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
     * ARGS[0] в ARGS[1] (с инвентарём QSP).
     */
    private static final String SERVICE_LOCATIONS = String.join("\n",
            "#nlb_show_in",
            "LOCAL nlb_i",
            "nlb_i = 0",
            "LOOP WHILE nlb_i < nlb_objs_n:",
            "    IF $nlb_in[$nlb_objs[nlb_i]] = $ARGS[0]: GOSUB 'nlb_o_' + $nlb_objs[nlb_i] + '_dsc'",
            "    nlb_i = nlb_i + 1",
            "END",
            "-",
            "#nlb_act",
            "*CLR",
            "IF $nlb_sel <> '':",
            "    GOSUB 'nlb_use', $nlb_sel, $ARGS[0]",
            "ELSE",
            "    %COUNT% = %COUNT% + 1",
            "    GOSUB 'nlb_o_' + $ARGS[0] + '_act'",
            "END",
            "GOSUB 'nlb_refresh'",
            "-",
            "#nlb_refresh",
            "$nlb_reply = $MAINTXT",
            "*CLR",
            "GOSUB $CURLOC + '_show'",
            "IF $nlb_reply <> '': *PL $nlb_reply",
            "GOSUB $CURLOC + '_turn'",
            "-",
            "#nlb_onobjsel",
            "$nlb_o = $nlb_byname[$SELOBJ]",
            "IF $nlb_o = '': EXIT",
            "*CLR",
            "IF $nlb_sel = '':",
            "    %COUNT% = %COUNT% + 1",
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
            "-",
            "#nlb_use",
            "%COUNT% = %COUNT% + 1",
            "nlb_printed = 0",
            "GOSUB 'nlb_o_' + $ARGS[0] + '_use', $ARGS[1]",
            "IF nlb_printed = 0: GOSUB 'nlb_o_' + $ARGS[0] + '_nouse'",
            "$nlb_sel = ''",
            "UNSEL",
            "-",
            "#nlb_place",
            "IF $nlb_in[$ARGS[0]] = '%NLB_INV%' AND $ARGS[1] <> '%NLB_INV%': DELOBJ $nlb_disp[$ARGS[0]]",
            "IF $nlb_in[$ARGS[0]] <> '%NLB_INV%' AND $ARGS[1] = '%NLB_INV%': ADDOBJ $nlb_disp[$ARGS[0]]",
            "$nlb_in[$ARGS[0]] = $ARGS[1]",
            "-",
            "");

    private String m_gameInvText = EMPTY_STRING;
    private String m_gameNouseText = EMPTY_STRING;

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
        sb.append(objBlocks.getObjText());
        sb.append("GOSUB 'nlb_show_in', '").append(id).append("'").append(LINE_SEPARATOR);
        if (constrained) {
            sb.append("END").append(LINE_SEPARATOR);
        }
        sb.append("-").append(LINE_SEPARATOR);

        sb.append(prefix).append("_acttext").append(LINE_SEPARATOR);
        sb.append(objBlocks.getObjActStart());
        sb.append("-").append(LINE_SEPARATOR);

        sb.append(prefix).append("_mods").append(LINE_SEPARATOR);
        sb.append(objBlocks.getObjModifications());
        sb.append(objBlocks.getObjVariable());
        sb.append("-").append(LINE_SEPARATOR);

        sb.append(prefix).append("_act").append(LINE_SEPARATOR);
        sb.append("GOSUB 'nlb_o_").append(id).append("_acttext'").append(LINE_SEPARATOR);
        sb.append("GOSUB 'nlb_o_").append(id).append("_mods'").append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(common)) {
            // общий объект (objActA): его текст — если у объекта своего нет, затем его модификации
            if (StringHelper.isEmpty(objBlocks.getObjActStart())) {
                sb.append("GOSUB 'nlb_o_").append(common).append("_acttext'").append(LINE_SEPARATOR);
            }
            sb.append("GOSUB 'nlb_o_").append(common).append("_mods'").append(LINE_SEPARATOR);
        }
        if (objBlocks.isTakable()) {
            // decorateObjTak: взять общий объект, если он переносимый, иначе этот
            String toTake = StringHelper.notEmpty(common) && isTakable(common) ? common : id;
            sb.append("GOSUB 'nlb_place', '").append(toTake).append("', '").append(INVENTORY).append("'").append(LINE_SEPARATOR);
        }
        sb.append("-").append(LINE_SEPARATOR);

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
        sb.append("-").append(LINE_SEPARATOR);

        sb.append(prefix).append("_nouse").append(LINE_SEPARATOR);
        String nouse = objBlocks.getObjNouse();
        sb.append("'").append(StringHelper.notEmpty(nouse) ? nouse : m_gameNouseText).append("'").append(LINE_SEPARATOR);
        sb.append("-").append(LINE_SEPARATOR);
        return sb.toString();
    }

    private boolean isTakable(String objId) {
        Boolean takable = m_takable.get(objId);
        return takable != null && takable;
    }

    /** Переносимость объектов по id (для общих объектов): заполняется до вывода объектов */
    private final java.util.Map<String, Boolean> m_takable = new java.util.HashMap<>();

    @Override
    protected String generateNLBText(final NLBBuildingBlocks nlbBlocks) throws com.nlbhub.nlb.exception.NLBConsistencyException {
        for (ObjBuildingBlocks obj : nlbBlocks.getObjsBuildingBlocks()) {
            m_takable.put(obj.getObjId(), obj.isTakable());
        }
        return super.generateNLBText(nlbBlocks);
    }

    // --- части объекта (ExportManager.createObjBuildingBlocks)

    @Override
    protected String decorateObjLabel(String id) {
        return id;
    }

    @Override
    protected String decorateObjComment(String name) {
        return "! Object -- " + (name == null ? EMPTY_STRING : name) + LINE_SEPARATOR;
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
        String link = "<a href=\"EXEC:GOSUB ''nlb_act'', ''" + objId + "''\">";
        String text = objText.replaceAll("\\{([^}]*)\\}", java.util.regex.Matcher.quoteReplacement(link) + "$1</a>");
        return "'" + text + "'" + LINE_SEPARATOR;
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
                    result.append(qspString(textChunk.getText()));
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
        return result.toString();
    }

    @Override
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        // подпись действия и текст применения — одна строка
        return expandVariables(textChunks).replace(LINE_SEPARATOR, " ");
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
        stringBuilder.append("GOSUB '").append(pageId).append("_show'").append(LINE_SEPARATOR);
        stringBuilder.append("GOSUB '").append(pageId).append("_turn'").append(LINE_SEPARATOR);
        stringBuilder.append("-").append(LINE_SEPARATOR);
        stringBuilder.append("#").append(pageId).append("_show").append(LINE_SEPARATOR);
        stringBuilder.append(pageBlocks.getPageCaption());
        stringBuilder.append(pageBlocks.getPageImage());
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append("GOSUB 'nlb_show_in', '").append(pageId).append("'").append(LINE_SEPARATOR);
        stringBuilder.append("-").append(LINE_SEPARATOR);
        stringBuilder.append("#").append(pageId).append("_turn").append(LINE_SEPARATOR);
        stringBuilder.append("CLA").append(LINE_SEPARATOR);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
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
            } else if (waitsAction) {
                stringBuilder.append("    ").append(ACTION_COUNT_VAR).append(" = 0").append(LINE_SEPARATOR);
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
        stringBuilder.append(pageBlocks.getPageEnd());
        return stringBuilder.toString();
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
        return "\\$" + constraintVar;
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
        return "! Link -- " + comment + LINE_SEPARATOR;
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
        return "-" + LINE_SEPARATOR;
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
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageTimerVariable(final String variableName) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
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
        // TODO: support image constraints
        ImagePathData pageImagePathData = pageImagePathDatas.get(0);
        if (pageImagePathData.getMaxFrameNumber() == 0) {
            String pageImagePath = pageImagePathData.getImagePath();
            if (StringHelper.isEmpty(pageImagePath)) {
                return Constants.EMPTY_STRING;
            } else {
                // USEHTML включает начало игры (nlb_start)
                return "'<img src=\"" + pageImagePath + "\">'" + LINE_SEPARATOR;
            }
        } else {
            // TODO: support animated images
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        // TODO: implement and use
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
        return "! Page -- " + comment + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + LINE_SEPARATOR;
    }

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        // TODO: implement
        return EMPTY_STRING;
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
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCloneOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateGetIdOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateDelObj(String destinationId, final String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (objectId == null || destinationName != null) {
            return EMPTY_STRING;  // объект или место из переменной — не поддерживается
        }
        String destination = destinationId != null ? "'" + destinationId + "'" : "$CURLOC";
        return "IF $nlb_in['" + objectId + "'] = " + destination + ": GOSUB 'nlb_place', '" + objectId + "', ''" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "IF $nlb_in['" + objectId + "'] = '" + INVENTORY + "': GOSUB 'nlb_place', '" + objectId + "', ''" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        // без назначения — в текущую комнату (here() в INSTEAD)
        String destination = destinationId != null ? "'" + destinationId + "'" : "$CURLOC";
        return "GOSUB 'nlb_place', '" + objectId + "', " + destination + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "GOSUB 'nlb_place', '" + objectId + "', '" + INVENTORY + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSSndOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWSndOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        // TODO: implement adding to the arbitrary list
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateClearInvOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        // rnd(n) в NLB — целое от 1 до n
        return variableName + " = RAND(1, " + maxValue + ")" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        // TODO: implement
        return EMPTY_STRING;
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
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountResetOperation() {
        // TODO: implement
        return EMPTY_STRING;
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
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        // вывод значения выражения без перевода строки
        return "*P '<<" + variableName.replace("'", "''") + ">>'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        // TODO: implement
        return EMPTY_STRING;
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
