/**
 * @(#)ASMJSExportManager.java
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
 * Copyright (c) 2026 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Экспорт в AXMA Story Maker JS (https://axmajs.ru) — новую версию AXMA: тело абзаца в ней —
 * JavaScript, строка с «\» в начале — текст (docs/export-formats.md, «AXMA Story Maker JS»).
 * Отдельный от {@link ASMExportManager} (классический AXMA, макросы &lt;&lt;…&gt;&gt;): автоперевод
 * старого формата в AXMA JS ломается на кавычках в тексте, на переменных с именами встроенных
 * функций (random, back…), на необъявленных переменных и на ссылках с подписью из переменной.
 *
 * Устройство:
 * - переменные книги — свойства объекта v (v.имя): необъявленная — undefined, а не ошибка, и имена
 *   не сталкиваются со встроенными функциями AXMA JS; служебное состояние — объект nlb;
 * - страница — абзац с её id: модификации входа (если это не повторный показ), текст —
 *   print(выражение) (переменные — значениями), ссылки — print(button(подпись, абзац ссылки, ""));
 * - ссылка — абзац с её id: счётчик действий, модификации ссылки, goto(цель); GOTO из модификаций
 *   — goto() (отменяет остальной код абзаца);
 * - автоссылки — до обычных ссылок и вместо них: на странице с текстом — единственной кнопкой, без
 *   текста — сразу goto() (как в экспорте AXMA, раздел «Автоссылки»).
 *
 * Объекты, инвентарь, контейнеры, списки, теги, медиа и достижения — следующие этапы (пока
 * модификации с ними выводятся комментарием «не поддерживается»).
 */
public class ASMJSExportManager extends TextExportManager {
    private static final String LINE_SEPARATOR = "\n";
    /** Переменная в тексте до сборки выражения: VAR_START имя VAR_END */
    private static final String VAR_START = "\u0001";
    private static final String VAR_END = "\u0002";
    private static final Pattern VAR_MARK = Pattern.compile(VAR_START + "([^" + VAR_END + "]*)" + VAR_END);
    private static final String[] COUNT_TYPES = {"inv", "act", "use", "walk"};

    private String m_language = "en";

    public ASMJSExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    // --- текст как выражение JavaScript

    /** Строковый литерал JavaScript в двойных кавычках */
    static String jsString(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '<': sb.append("\\u003c"); break;  // «</script>» в тексте не закроет скрипт игры
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Текст (после expandVariables: HTML, переменные — метками) как выражение JavaScript:
     * "…" + v.имя + "…"
     */
    static String js(String text) {
        if (StringHelper.isEmpty(text)) {
            return "\"\"";
        }
        Matcher m = VAR_MARK.matcher(text);
        StringBuilder result = new StringBuilder();
        int at = 0;
        while (m.find()) {
            if (m.start() > at) {
                result.append(result.length() > 0 ? " + " : "").append(jsString(text.substring(at, m.start())));
            }
            result.append(result.length() > 0 ? " + " : "").append("nlb.str(").append(variable(m.group(1))).append(")");
            at = m.end();
        }
        if (at < text.length() || result.length() == 0) {
            result.append(result.length() > 0 ? " + " : "").append(jsString(text.substring(at)));
        }
        return result.toString();
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*");

    /** Переменная книги: v.имя (имя не идентификатор JavaScript — v["имя"]) */
    static String variable(String name) {
        return IDENTIFIER.matcher(name).matches() ? "v." + name : "v[" + jsString(name) + "]";
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String todo(String what) {
        return "/* NLB: " + what + " — в экспорте AXMA JS пока не поддерживается */ ";
    }

    @Override
    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        return expandVariables(textChunks);
    }

    /** Текст — HTML, '^' (перевод строки INSTEAD) и строки — &lt;br&gt;, переменная — меткой */
    @Override
    protected String expandVariables(List<TextChunk> textChunks) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(html(textChunk.getText()).replace("^", "<br>"));
                    break;
                case ACTION_TEXT:
                    break;  // $^$ — текст действия (VN): как в других экспортах, не выводится
                case VARIABLE:
                    result.append(VAR_START).append(textChunk.getText()).append(VAR_END);
                    break;
                case NEWLINE:
                    result.append("<br>");
                    break;
            }
        }
        return result.toString();
    }

    /** Подпись ссылки — одной строкой */
    @Override
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        return expandVariables(textChunks).replace("<br>", " ").trim();
    }

    // --- файл и служебные абзацы

    private static String passage(String title) {
        return ":: " + title + "[::]10-10-0" + LINE_SEPARATOR;
    }

    /** Текстовый абзац (StoryTitle и др.): только текст, строкой «\» */
    private static String textPassage(String title, String text) {
        return passage(title) + "\\ " + html(text.trim()).replace("\n", " ") + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        m_language = StringHelper.isEmpty(nlbBuildingBlocks.getLang()) ? "en" : nlbBuildingBlocks.getLang();
        StringBuilder sb = new StringBuilder("ASMJS[::]128[::]0[::]0[::]0[::]mw[::]html").append(LINE_SEPARATOR);
        String title = StringHelper.notEmpty(nlbBuildingBlocks.getTitle()) ? nlbBuildingBlocks.getTitle() : "NLB";
        String author = StringHelper.notEmpty(nlbBuildingBlocks.getAuthor()) ? nlbBuildingBlocks.getAuthor() : "NLB";
        // StoryTitle, StoryAuthor и StorySubtitle обязательны для публикации в библиотеку AXMA JS
        sb.append(textPassage("StoryTitle", title));
        sb.append(textPassage("StoryAuthor", author));
        sb.append(textPassage("StorySubtitle", title));
        sb.append(passage("nlb_init")).append(initCode()).append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        return sb.toString();
    }

    /** Начальное состояние: переменные книги (v) и служебное (nlb) — счётчики действий, страница */
    private String initCode() {
        StringBuilder counters = new StringBuilder();
        for (String type : COUNT_TYPES) {
            counters.append(counters.length() > 0 ? ", " : "").append(type).append(": 0");
        }
        return "v = {}; nlb = {page: \"\", redraw: 0, ac: 0, cnt: {" + counters + "}, cntr: {" + counters + "}};"
                + LINE_SEPARATOR
                // значение переменной в тексте: undefined — пусто, как незаданная в плеере
                + "nlb.str = function (x) { return x === undefined || x === null ? \"\" : String(x); };";
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        return "";  // объекты — следующий этап
    }

    // --- страница

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        final String pageId = pageBlocks.getPageId();
        StringBuilder sb = new StringBuilder();
        sb.append(pageBlocks.getPageLabel());
        // модификации входа — до текста; повторный показ (nlb.redraw — после действия) — без них,
        // но с тиком таймера страницы
        sb.append("nlb.page = ").append(jsString(pageId)).append(";").append(LINE_SEPARATOR);
        sb.append("if (nlb.redraw) { nlb.redraw = 0; ").append(pageBlocks.isHasPageTimer() ? pageBlocks.getPageTimerVariable() : "")
                .append("} else { ").append(pageBlocks.getPageModifications()).append(pageBlocks.getPageVariable());
        if (pageBlocks.isHasPageTimer()) {
            sb.append(pageBlocks.getPageTimerVariableInit());
        }
        sb.append("}").append(LINE_SEPARATOR);
        sb.append(pageBlocks.getPageCaption());
        String text = pageBlocks.getPageTextStart();
        if (StringHelper.notEmpty(text) && !text.trim().isEmpty()) {
            // print не переводит строку: текст — абзацем перед ссылками
            sb.append("print(").append(js(text.trim())).append(" + \"<br><br>\");").append(LINE_SEPARATOR);
        }
        List<LinkBuildingBlocks> links = pageBlocks.getLinksBuildingBlocks();
        sb.append(timerWait(pageBlocks));
        // автоссылки — до обычных и вместо них: первая с выполненным условием
        boolean open = false;
        boolean unconditional = false;
        for (LinkBuildingBlocks link : links) {
            if (!link.isAuto()) {
                continue;
            }
            String constraint = autoConstraint(link);
            if (StringHelper.notEmpty(constraint)) {
                sb.append(open ? "} else if (" : "if (").append(constraint).append(") { ");
                open = true;
            } else if (open) {
                sb.append("} else { ");
            }
            sb.append(autoTransition(pageBlocks, link));
            if (StringHelper.isEmpty(constraint)) {
                unconditional = true;
                break;
            }
        }
        if (!unconditional) {
            if (open) {
                sb.append("} else {").append(LINE_SEPARATOR);
            }
            for (LinkBuildingBlocks link : links) {
                if (link.isAuto()) {
                    continue;
                }
                boolean constrained = StringHelper.notEmpty(link.getLinkConstraint());
                if (constrained) {
                    sb.append("if (").append(link.getLinkConstraint()).append(") { ");
                }
                sb.append(link.getLinkStart());
                if (constrained) {
                    sb.append("}");
                    if (link.hasShownAltText()) {
                        // альтернативный текст недоступной ссылки (#135)
                        sb.append(" else { print(").append(js(link.getLinkAltText())).append(" + \"<br>\"); }");
                    }
                    sb.append(LINE_SEPARATOR);
                }
            }
        }
        if (open) {
            sb.append("}").append(LINE_SEPARATOR);
        }
        sb.append(LINE_SEPARATOR);
        return sb.toString();
    }

    /** Условие автоссылки; у «needs action» — и нужное число действий игрока */
    private String autoConstraint(LinkBuildingBlocks link) {
        String constraint = link.getLinkConstraint();
        if (link.isNeedsAction()) {
            String hasAction = "(nlb.ac >= " + getNeedsActionCount() + ")";
            constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") && " + hasAction;
        }
        return constraint;
    }

    /** Автоссылка: на странице с текстом — кнопкой (goto стёр бы текст), без текста — сразу goto */
    private String autoTransition(PageBuildingBlocks pageBlocks, LinkBuildingBlocks link) {
        if (!pageBlocks.isHasPageText()) {
            return "goto(" + jsString(link.getLinkId()) + "); ";
        }
        return decorateLinkStart(link.getLinkId(), link.getLinkText(), true, true, false, 0, link.getTheme());
    }

    /**
     * Выбирать нечего, а автоссылки ждут таймера — время проматывается тик за тиком, пока условие
     * какой-нибудь автоссылки не станет истинным (PlayerEngine.autoWaitTicks)
     */
    private String timerWait(PageBuildingBlocks pageBlocks) {
        if (!pageBlocks.isHasPageTimer() || pageBlocks.getTimerWaitTicks() <= 0) {
            return "";
        }
        StringBuilder autos = new StringBuilder();
        StringBuilder any = new StringBuilder();
        for (LinkBuildingBlocks link : pageBlocks.getLinksBuildingBlocks()) {
            if (link.isAuto()) {
                String constraint = autoConstraint(link);
                if (StringHelper.isEmpty(constraint)) {
                    return "";
                }
                autos.append(autos.length() > 0 ? " || " : "").append("(").append(constraint).append(")");
            } else if (StringHelper.isEmpty(link.getLinkConstraint())) {
                return "";
            } else {
                any.append(any.length() > 0 ? " || " : "").append("(").append(link.getLinkConstraint()).append(")");
            }
        }
        if (autos.length() == 0) {
            return "";
        }
        String loop = "for (var nlb_t = 0; nlb_t < " + pageBlocks.getTimerWaitTicks() + " && !(" + autos + "); nlb_t++) { "
                + pageBlocks.getPageTimerVariable() + "}";
        return (any.length() > 0 ? "if (!(" + any + ")) { " + loop + " }" : loop) + LINE_SEPARATOR;
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        StringBuilder sb = new StringBuilder();
        for (LinkBuildingBlocks link : pageBlocks.getLinksBuildingBlocks()) {
            sb.append(link.getLinkLabel());
            if (!link.isTechnical()) {
                // переход по обычной ссылке — действие игрока (needs action, COUNTGET)
                sb.append(count("walk"));
            } else if (link.isAuto() && link.isNeedsAction()) {
                sb.append(decorateCountResetOperation());  // has_action() сбрасывает счётчики
            }
            sb.append(link.getLinkModifications());
            sb.append(link.getLinkVariable());
            sb.append(link.getLinkVisitStateVariable());
            sb.append(link.getLinkGoTo());
            sb.append(LINE_SEPARATOR);
        }
        return sb.toString();
    }

    private static String count(String type) {
        return "nlb.ac++; nlb.cnt." + type + "++; ";
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        String label = passage(labelText);
        if (pageNumber == 1) {
            // Start — первый абзац игры: начальное состояние и переход на первую страницу
            return passage("Start") + "display(\"nlb_init\");" + LINE_SEPARATOR + "goto(" + jsString(labelText) + ");"
                    + LINE_SEPARATOR + LINE_SEPARATOR + label;
        }
        return label;
    }

    @Override
    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return "print(" + jsString("<b>" + html(caption.toUpperCase()) + "</b><br>") + ");" + LINE_SEPARATOR;
        }
        return "";
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        return "";
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "";
    }

    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return "";
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    // --- ссылки

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return passage(linkId);
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "";
    }

    /** Кнопка ссылки: подпись (с переменными — значениями; пустая — подпись по умолчанию) -> абзац ссылки */
    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        String label = linkText == null || linkText.trim().isEmpty() ? defaultLinkText() : linkText;
        return "print(button(" + js(label) + ", " + jsString(linkId) + ", \"\")); " + LINE_SEPARATOR;
    }

    private String defaultLinkText() {
        String text = Link.DEFAULT_TEXT.get(m_language);
        return StringHelper.isEmpty(text) ? Link.DEFAULT_TEXT.get("en") : text;
    }

    @Override
    protected String decorateLinkGoTo(String linkId, String linkText, String linkSource, int sourcePageNumber,
                                      String linkTarget, int targetPageNumber, boolean isTechnical, Theme theme) {
        return "goto(" + jsString(linkTarget) + ");";
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return variable(variableName) + " = true; ";
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return variable(linkVisitStateVariable) + " = true; ";
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        return variable(variableName) + " = true; ";
    }

    @Override
    protected String decoratePageTimerVariableInit(String variableName) {
        return StringHelper.isEmpty(variableName) ? "" : variable(variableName) + " = 0; ";
    }

    @Override
    protected String decoratePageTimerVariable(String variableName) {
        return StringHelper.isEmpty(variableName) ? "" : variable(variableName) + " = (" + variable(variableName) + " || 0) + 1; ";
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageThemeModifications(String modificationsText) {
        return "";
    }

    @Override
    protected String decoratePageNotes(String notes) {
        return "";
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, boolean imageBackground, Theme theme) {
        return "";  // медиа — следующий этап
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        return "";
    }

    // --- выражения и управление

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + "; ";
    }

    @Override
    protected String decorateWhile(String constraint) {
        return "while (" + constraint + ") { ";
    }

    @Override
    protected String decorateIf(String constraint) {
        return "if (" + constraint + ") { ";
    }

    @Override
    protected String decorateElse() {
        return "} else { ";
    }

    @Override
    protected String decorateElseIf(String constraint) {
        return "} else if (" + constraint + ") { ";
    }

    @Override
    protected String decorateEnd() {
        return "} ";
    }

    @Override
    protected String decorateReturn(String returnValue) {
        return "";
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        // rnd(n) в NLB — целое от 1 до n; random(min, max) AXMA JS — включительно
        return variableName + " = random(1, " + maxValue + "); ";
    }

    /** GOTO: goto() отменяет остальной код абзаца — переход из модификаций ссылки важнее её цели */
    @Override
    protected String decorateGoToOperation(String locationId) {
        return "goto(" + jsString(locationId) + "); ";
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < COUNT_TYPES.length; i++) {
            if (statType == 0 || (statType & (1 << i)) != 0) {
                sum.append(sum.length() > 0 ? " + " : "").append("(nlb.cnt.").append(COUNT_TYPES[i])
                        .append(" - nlb.cntr.").append(COUNT_TYPES[i]).append(")");
            }
        }
        return variableName + " = " + (sum.length() > 0 ? sum.toString() : "0") + "; ";
    }

    @Override
    protected String decorateCountResetOperation() {
        StringBuilder sb = new StringBuilder("nlb.ac = 0; ");
        for (String type : COUNT_TYPES) {
            sb.append("nlb.cntr.").append(type).append(" = nlb.cnt.").append(type).append("; ");
        }
        return sb.toString();
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        return "print(nlb.str(" + variableName + ")); ";
    }

    @Override
    protected String decorateTrue() {
        return "true";
    }

    @Override
    protected String decorateFalse() {
        return "false";
    }

    @Override
    protected String decorateEq() {
        return "==";
    }

    @Override
    protected String decorateNEq() {
        return "!=";
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
        return "!";
    }

    @Override
    protected String decorateOr() {
        return "||";
    }

    @Override
    protected String decorateAnd() {
        return "&&";
    }

    @Override
    protected String decorateExistence(String decoratedVariable) {
        return "";
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return variable(constraintVar);
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return variable(constraintVar);
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return variable(constraintVar);
    }

    // --- следующие этапы: объекты, инвентарь, контейнеры, списки, теги, медиа, достижения

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        return todo("TAG");
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        return todo("GETTAG");
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        return todo("IFHAVE") + "if (false) { ";
    }

    @Override
    protected String decorateHaveOperation(String variableName, String objId, String objVar) {
        return todo("HAVE");
    }

    @Override
    protected String decorateCloneOperation(String variableName, String objId, String objVar) {
        return todo("CLONE");
    }

    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        return todo("CNTNR");
    }

    @Override
    protected String decorateGetIdOperation(String variableName, String objId, String objVar) {
        return todo("ID");
    }

    @Override
    protected String decorateDelObj(String destinationId, String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        return todo("REMOVE");
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return todo("RMINV");
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        return todo("ADD");
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return todo("ADDINV");
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        return todo("ADDALL");
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        return todo("OBJS");
    }

    @Override
    protected String decorateSSndOperation() {
        return todo("SSND");
    }

    @Override
    protected String decorateWSndOperation() {
        return todo("WSND");
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        return todo("SND");
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        return todo("SPUSH");
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        return todo("WPUSH");
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        return todo("PUSH");
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        return todo("POP");
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        return todo("SINJECT");
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        return todo("INJECT");
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        return todo("EJECT");
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        return todo("CLEAR");
    }

    @Override
    protected String decorateClearInvOperation() {
        return todo("CLRINV");
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        return todo("SIZE");
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        return todo("ACHMAX");
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        return todo("ACHIEVE");
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        return todo("ACHIEVED");
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        return todo("SNAPSHOT");
    }

    @Override
    protected String decorateOpenURLOperation(String url) {
        return todo("OPENURL");
    }

    @Override
    protected String decorateWinGeomOperation(String arg) {
        return todo("WINGEOM");
    }

    @Override
    protected String decorateInvGeomOperation(String arg) {
        return todo("INVGEOM");
    }

    @Override
    protected String decorateWinColorOperation(String arg) {
        return todo("WINCOLOR");
    }

    @Override
    protected String decorateInvColorOperation(String arg) {
        return todo("INVCOLOR");
    }

    @Override
    protected String decorateShuffleOperation(String listVariableName) {
        return todo("SHUFFLE");
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        return todo("DSC");
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        return todo("PDSC");
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        return todo("PDSCS");
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        return todo("ACT");
    }

    @Override
    protected String decorateActtOperation(String resultVariableName, String actObjVariable, String actObjId) {
        return todo("ACTT");
    }

    @Override
    protected String decorateActfOperation(String actingObjVariable, String actingObjId) {
        return todo("ACTF");
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        return todo("USE");
    }
}
