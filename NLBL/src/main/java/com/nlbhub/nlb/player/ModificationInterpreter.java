/**
 * @(#)ModificationInterpreter.java
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

import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.Variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Исполнение модификаций (все типы), выражения, подстановка переменных в текст, действия
 * объектов, выбор картинок и звуков по тегам. Порт ModificationInterpreter из NLBNavigator;
 * эталон семантики — ExportManager.buildModificationsText и nlb.lua.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class ModificationInterpreter {
    /**
     * Где исполняются модификации
     */
    public static final class ExecContext {
        /**
         * Книга, которой принадлежит модифицирующий элемент (для поиска переменных по id)
         */
        private final NonLinearBook m_book;
        /**
         * "s": id страницы или экземпляра объекта
         */
        private final String m_self;
        /**
         * "ww": второй аргумент usea(s, w, ww); вне use-обработчика — null (в Lua nil)
         */
        private final String m_ww;

        public ExecContext(NonLinearBook book, String self, String ww) {
            m_book = book;
            m_self = self;
            m_ww = ww;
        }
    }

    private enum Flow {NORMAL, RETURN}

    /**
     * Защита от бесконечной рекурсии ACT/USE
     */
    private static final int MAX_CALL_DEPTH = 64;
    private static final long MAX_WHILE_ITERATIONS = 1000000;
    /**
     * Значение переменной или элемента списка, ссылающееся на список (listobj в STEAD)
     */
    private static final String LIST_REF_PREFIX = "@list:";
    private static final String VOID = "VOID";
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$([^\\s$]*)\\$");
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^(.*\\D|)(\\d*)(\\..*)$", Pattern.DOTALL);

    private final NonLinearBook m_rootBook;
    private final PlayerContext m_context;
    private final Map<String, Obj> m_objs = new TreeMap<>();
    private final Map<String, NonLinearBook> m_objBooks = new TreeMap<>();
    private final Map<String, String> m_objIdsByName = new TreeMap<>();
    private final Map<String, Page> m_pages = new TreeMap<>();
    private final Map<String, NonLinearBook> m_pageBooks = new TreeMap<>();
    private final Set<String> m_useTargets = new HashSet<>();
    private final Map<String, String> m_mediaConstraints;
    private final Map<String, String> m_mediaRedirects;
    private final Map<String, Boolean> m_mediaFlags;

    private int m_depth = 0;
    private String m_lastText = "";
    /**
     * Значение последнего RETURN: null — RETURN не исполнялся
     */
    private Value m_lastReturnValue = null;
    private BiPredicate<String, Modification.Type> m_externalCallHandler = null;
    private Consumer<String> m_warnings = message -> System.err.println(message);

    public ModificationInterpreter(NonLinearBook rootBook, PlayerContext context) {
        m_rootBook = rootBook;
        m_context = context;
        indexBook(rootBook);
        m_mediaConstraints = rootBook == null ? Collections.emptyMap() : rootBook.getMediaToConstraintMap();
        m_mediaRedirects = rootBook == null ? Collections.emptyMap() : rootBook.getMediaRedirectsMap();
        m_mediaFlags = rootBook == null ? Collections.emptyMap() : rootBook.getMediaFlagsMap();
    }

    /**
     * Обработчик вызова внешней игры: ACT/ACTF с именем, которого нет среди объектов книги.
     * Возвращает true, если имя — внешняя игра
     */
    public void setExternalCallHandler(BiPredicate<String, Modification.Type> handler) {
        m_externalCallHandler = handler;
    }

    /**
     * Куда писать предупреждения (объект модификации не найден и т.п.); по умолчанию — stderr
     */
    public void setWarnings(Consumer<String> warnings) {
        m_warnings = warnings;
    }

    // ------------------------------------------------------------------ индекс книги

    private void indexBook(NonLinearBook book) {
        if (book == null) {
            return;
        }
        // Корень индексируется первым: при совпадении id/имён (один внешний модуль на нескольких
        // страницах) побеждает первое вхождение
        for (Obj obj : PlayerContext.sortedObjs(book)) {
            if (obj.isDeleted()) {
                continue;
            }
            m_objs.putIfAbsent(obj.getId(), obj);
            m_objBooks.putIfAbsent(obj.getId(), book);
            if (!PlayerContext.isEmpty(obj.getName())) {
                m_objIdsByName.putIfAbsent(obj.getName(), obj.getId());
            }
            for (Link link : obj.getLinks()) {
                if (!link.isDeleted()) {
                    m_useTargets.add(link.getTarget());
                }
            }
        }
        List<Page> pages = PlayerContext.sortedPages(book);
        for (Page page : pages) {
            if (page.isDeleted()) {
                continue;
            }
            m_pages.putIfAbsent(page.getId(), page);
            m_pageBooks.putIfAbsent(page.getId(), book);
        }
        for (Page page : pages) {
            NonLinearBook module = page.getModule();
            if (module != null && !module.isEmpty()) {
                indexBook(module);
            }
        }
    }

    public Obj findObj(String objId) {
        return objId == null ? null : m_objs.get(objId);
    }

    public Page findPage(String pageId) {
        return pageId == null ? null : m_pages.get(pageId);
    }

    public NonLinearBook bookOfObj(String objId) {
        NonLinearBook book = m_objBooks.get(objId);
        return book == null ? m_rootBook : book;
    }

    public NonLinearBook bookOfPage(String pageId) {
        NonLinearBook book = m_pageBooks.get(pageId);
        return book == null ? m_rootBook : book;
    }

    public String objIdByName(String name) {
        String id = m_objIdsByName.get(name);
        return id == null ? "" : id;
    }

    /**
     * Все объекты книги и модулей (id -> объект, по порядку id)
     */
    public Map<String, Obj> objects() {
        return Collections.unmodifiableMap(m_objs);
    }

    /**
     * На объект ведут use-ссылки других объектов (ExportData.hasInwardLinks)
     */
    public boolean hasInwardUseLinks(String objId) {
        return m_useTargets.contains(objId);
    }

    // ------------------------------------------------------------------ выражения

    /**
     * Вычисляет выражение книги; неизвестные переменные — false
     */
    public Value evaluate(String expression) {
        return Expression.evaluate(expression, name -> {
            Value value = m_context.findVar(name);
            return value == null ? Value.FALSE : value;
        });
    }

    /**
     * Истинность значения в условиях (Lua): ложны только nil и false
     */
    public static boolean isTruthy(Value value) {
        return value.isTruthy();
    }

    /**
     * Ограничение по id переменной; пустое или удалённое — выполняется
     */
    public boolean evaluateConstraint(NonLinearBook book, String constrId, boolean positive) {
        if (PlayerContext.isEmpty(constrId)) {
            return true;
        }
        Variable constraint = book == null ? null : book.getVariableById(constrId);
        if (constraint == null || constraint.isDeleted()) {
            return true;
        }
        boolean value = evaluate(constraint.getValue()).isTruthy();
        return positive ? value : !value;
    }

    public boolean evaluateConstraint(NonLinearBook book, String constrId) {
        return evaluateConstraint(book, constrId, true);
    }

    /**
     * Текст книги с подставленными $var$ и $^$ (текст действий на странице). Строки — по '\n',
     * пробелы в конце строки убираются (как StringHelper::getTextChunks в NLBNavigator)
     */
    public String expandText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        List<String> lines = splitLines(text);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result.append('\n');
            }
            String line = stripTrailing(lines.get(i));
            Matcher matcher = VAR_PATTERN.matcher(line);
            int last = 0;
            while (matcher.find()) {
                result.append(line, last, matcher.start());
                String name = matcher.group(1);
                if (name.equals("^")) {
                    result.append(m_lastText);
                } else {
                    result.append(m_context.getVar(name).toDisplayString());
                }
                last = matcher.end();
            }
            result.append(line.substring(last));
        }
        return result.toString();
    }

    /**
     * Строки текста, как их читает std::getline: по '\n', без пустой строки после последнего '\n'
     */
    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                lines.add(text.substring(start));
                break;
            }
            lines.add(text.substring(start, end));
            start = end + 1;
        }
        return lines;
    }

    /**
     * Убирает пробельные символы в конце (isspace в C: пробел, \t, \n, \v, \f, \r)
     */
    static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && isCSpace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    static boolean isCSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r';
    }

    // ------------------------------------------------------------------ исполнение

    /**
     * Исполняет модификации элемента книги
     *
     * @return false, если исполнение прервал RETURN
     */
    public boolean execute(List<Modification> modifications, ExecContext ctx) {
        m_lastReturnValue = null;
        if (m_depth >= MAX_CALL_DEPTH) {
            throw new PlayerException("Modification call depth exceeded (recursive ACT/USE?)");
        }
        // ExportManager.buildModificationsText: удалённые пропускаются; external-модификации
        // исполняются только внутри внешнего модуля
        Page modulePage = ctx.m_book == null ? null : ctx.m_book.getParentPage();
        final boolean inExternalModule = modulePage != null && modulePage.isModuleExternal();
        List<Modification> mods = new ArrayList<>();
        for (Modification mod : modifications) {
            if (mod != null && !mod.isDeleted() && (!mod.isExternal() || inExternalModule)) {
                mods.add(mod);
            }
        }
        m_depth++;
        try {
            return executeRange(mods, 0, mods.size(), ctx) != Flow.RETURN;
        } finally {
            m_depth--;
        }
    }

    /**
     * Значение последнего RETURN; null — RETURN не исполнялся, {@link Value#NONE} — без значения
     */
    public Value lastReturnValue() {
        return m_lastReturnValue;
    }

    private static boolean isBlockOpener(Modification.Type type) {
        return type == Modification.Type.IF || type == Modification.Type.IFHAVE || type == Modification.Type.WHILE;
    }

    private int findBlockEnd(List<Modification> mods, int opener, int end) {
        int depth = 0;
        for (int j = opener + 1; j < end; j++) {
            Modification.Type type = mods.get(j).getType();
            if (isBlockOpener(type)) {
                depth++;
            } else if (type == Modification.Type.END) {
                if (depth == 0) {
                    return j;
                }
                depth--;
            }
        }
        throw new PlayerException("Unbalanced modifications: no END for modification " + mods.get(opener).getId());
    }

    private Flow executeRange(List<Modification> mods, int begin, int end, ExecContext ctx) {
        int i = begin;
        while (i < end) {
            Modification mod = mods.get(i);
            Modification.Type type = mod.getType();
            if (type == Modification.Type.IF || type == Modification.Type.IFHAVE) {
                final int blockEnd = findBlockEnd(mods, i, end);
                // Границы веток: ELSEIF/ELSE на глубине 0 внутри блока
                List<Integer> markers = new ArrayList<>();
                markers.add(i);
                int depth = 0;
                for (int j = i + 1; j < blockEnd; j++) {
                    Modification.Type t = mods.get(j).getType();
                    if (isBlockOpener(t)) {
                        depth++;
                    } else if (t == Modification.Type.END) {
                        depth--;
                    } else if (depth == 0 && (t == Modification.Type.ELSEIF || t == Modification.Type.ELSE)) {
                        markers.add(j);
                    }
                }
                markers.add(blockEnd);
                for (int m = 0; m + 1 < markers.size(); m++) {
                    Modification head = mods.get(markers.get(m));
                    boolean taken;
                    if (head.getType() == Modification.Type.ELSE) {
                        taken = true;
                    } else if (head.getType() == Modification.Type.IFHAVE) {
                        Variable expr = variableOf(ctx, head.getExprId());
                        String obj = expr == null ? null : objOperand(expr.getValue());
                        taken = obj != null && m_context.inInventory(obj);
                    } else {
                        Variable expr = variableOf(ctx, head.getExprId());
                        taken = expr != null && evaluate(expr.getValue()).isTruthy();
                    }
                    if (taken) {
                        if (executeRange(mods, markers.get(m) + 1, markers.get(m + 1), ctx) == Flow.RETURN) {
                            return Flow.RETURN;
                        }
                        break;
                    }
                }
                i = blockEnd + 1;
            } else if (type == Modification.Type.WHILE) {
                final int blockEnd = findBlockEnd(mods, i, end);
                Variable expr = variableOf(ctx, mod.getExprId());
                long iterations = 0;
                while (expr != null && evaluate(expr.getValue()).isTruthy()) {
                    if (++iterations > MAX_WHILE_ITERATIONS) {
                        throw new PlayerException("WHILE loop limit exceeded in modification " + mod.getId());
                    }
                    if (executeRange(mods, i + 1, blockEnd, ctx) == Flow.RETURN) {
                        return Flow.RETURN;
                    }
                }
                i = blockEnd + 1;
            } else if (type == Modification.Type.ELSE || type == Modification.Type.ELSEIF
                    || type == Modification.Type.END) {
                throw new PlayerException("Unbalanced modifications: unexpected block marker " + mod.getId());
            } else if (type == Modification.Type.RETURN) {
                // decorateReturn: "return <выражение>" из enter/actf — значение решает, отменяется ли переход
                Variable expr = variableOf(ctx, mod.getExprId());
                m_lastReturnValue = (expr != null && !expr.getValue().trim().isEmpty())
                        ? evaluate(expr.getValue()) : Value.NONE;
                return Flow.RETURN;
            } else {
                executeOne(mod, ctx);
                i++;
            }
        }
        return Flow.NORMAL;
    }

    private Variable variableOf(ExecContext ctx, String varId) {
        if (PlayerContext.isEmpty(varId) || ctx.m_book == null) {
            return null;
        }
        Variable variable = ctx.m_book.getVariableById(varId);
        return (variable != null && !variable.isDeleted()) ? variable : null;
    }

    private String objByNameOnly(String name) {
        String id = objIdByName(name);
        return id.isEmpty() ? null : id;
    }

    /**
     * Объект по имени или по переменной, хранящей ссылку на экземпляр; null — нет такого
     */
    private String objOperand(String name) {
        if (PlayerContext.isEmpty(name)) {
            return null;
        }
        String byName = objByNameOnly(name);
        if (byName != null) {
            return byName;
        }
        if (m_context.hasVar(name)) {
            Value value = m_context.getVar(name);
            if (value.isString() && !value.asString().isEmpty() && !value.asString().startsWith(LIST_REF_PREFIX)) {
                return value.asString();
            }
        }
        return null;
    }

    /**
     * Элемент списка как значение переменной (POP/EJECT)
     */
    static Value parseListValue(String value) {
        if (value.equals("true")) {
            return Value.TRUE;
        }
        if (value.equals("false")) {
            return Value.FALSE;
        }
        if (!value.isEmpty()) {
            Long asInt = parseCLong(value);
            if (asInt != null) {
                return Value.of(asInt);
            }
            Double asReal = parseCDouble(value);
            if (asReal != null) {
                return Value.of(asReal);
            }
        }
        return Value.of(value);
    }

    /**
     * strtoll(value, &end, 10) с проверкой, что прочитана вся строка
     */
    private static Long parseCLong(String value) {
        int i = 0;
        while (i < value.length() && isCSpace(value.charAt(i))) i++;
        int start = i;
        if (i < value.length() && (value.charAt(i) == '+' || value.charAt(i) == '-')) i++;
        int digits = i;
        while (i < value.length() && Character.isDigit(value.charAt(i)) && value.charAt(i) < 128) i++;
        if (i == digits || i != value.length()) {
            return null;
        }
        try {
            return Long.parseLong(value.substring(start).replace("+", ""));
        } catch (NumberFormatException e) {
            return value.charAt(start) == '-' ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    /**
     * strtod(value, &end) с проверкой, что прочитана вся строка (десятичная запись, inf, nan)
     */
    private static Double parseCDouble(String value) {
        String s = value;
        int i = 0;
        while (i < s.length() && isCSpace(s.charAt(i))) i++;
        String body = s.substring(i);
        if (body.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) {
            return Double.parseDouble(body);
        }
        String lower = body.toLowerCase();
        if (lower.matches("[+-]?(inf|infinity)")) {
            return lower.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (lower.matches("[+-]?nan")) {
            return Double.NaN;
        }
        return null;
    }

    private String resolveListName(String name) {
        if (!PlayerContext.isEmpty(name) && m_context.hasVar(name)) {
            Value value = m_context.getVar(name);
            if (value.isString() && value.asString().startsWith(LIST_REF_PREFIX)) {
                return value.asString().substring(LIST_REF_PREFIX.length());
            }
        }
        return name;
    }

    private String valueToListItem(String name) {
        String obj = objOperand(name);
        if (obj != null) {
            return obj;
        }
        // Список как элемент другого списка (PUSH args | weakDeck): кладётся ссылка на список
        String listName = resolveListName(name);
        if (!listName.equals(name) || m_context.listExists(listName)) {
            return LIST_REF_PREFIX + listName;
        }
        return m_context.getVar(name).toDisplayString();
    }

    private void printText(String text) {
        m_context.emit(PlayerContext.OutputKind.Text, text);
        m_lastText += text;
    }

    /**
     * Тег экземпляра; без явного тега — значение переменной тега по умолчанию
     */
    public String tagOf(String itemId) {
        String tag = m_context.tag(itemId);
        if (tag != null) {
            return tag;
        }
        // Тег по умолчанию: значение переменной defaultTagId (var { tag = '...' } в STEAD)
        String defaultTagId = "";
        NonLinearBook book = m_rootBook;
        Page page = findPage(itemId);
        if (page != null) {
            defaultTagId = page.getDefaultTagId();
            book = bookOfPage(itemId);
        } else {
            Obj obj = findObj(m_context.protoOf(itemId));
            if (obj != null) {
                defaultTagId = obj.getDefaultTagId();
                book = bookOfObj(obj.getId());
            }
        }
        Variable tagVar = PlayerContext.isEmpty(defaultTagId) ? null : book.getVariableById(defaultTagId);
        return tagVar == null ? "" : tagVar.getValue();
    }

    static String mediaKey(String externalHierarchy, String fileName) {
        return PlayerContext.isEmpty(externalHierarchy) ? fileName : externalHierarchy + "/" + fileName;
    }

    /**
     * Имена файлов через ';' (Constants.MEDIA_FILE_NAME_SEP), пустые пропускаются
     */
    static List<String> splitMediaNames(String names) {
        List<String> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        for (String name : names.split(";", -1)) {
            if (!name.isEmpty()) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * Звук страницы или объекта. Звуки без ограничения играют всегда; подряд идущие звуки с
     * ограничением образуют цепочку if/elseif по тегу (срабатывает первый подходящий).
     * Обычный звук — музыка; SFX страницы — озвучка (очередь страницы), SFX объекта — эффект
     */
    public void playSound(String itemId) {
        if (m_rootBook != null && (m_rootBook.isSuppressMedia() || m_rootBook.isSuppressSound())) {
            return;
        }
        String names;
        String hierarchy;
        boolean sfx;
        boolean isPage = false;
        Page page = findPage(itemId);
        if (page != null) {
            isPage = true;
            names = page.getSoundFileName();
            hierarchy = page.getExternalHierarchy();
            sfx = page.isSoundSFX();
        } else {
            Obj obj = findObj(m_context.protoOf(itemId));
            if (obj == null) {
                return;
            }
            names = obj.getSoundFileName();
            hierarchy = obj.getExternalHierarchy();
            sfx = obj.isSoundSFX();
        }
        final String tag = tagOf(itemId);
        boolean chainMatched = false;
        for (String name : splitMediaNames(names)) {
            final String key = mediaKey(hierarchy, name);
            String constraint = m_mediaConstraints.get(key);
            if (constraint != null && !constraint.isEmpty()) {
                if (chainMatched || !tag.equals(constraint)) {
                    continue;
                }
                chainMatched = true;
            } else {
                chainMatched = false;
            }
            String redirect = m_mediaRedirects.get(name);
            final String file = redirect != null ? redirect : name;
            if (name.equals(VOID)) {
                m_context.emit(PlayerContext.OutputKind.Music, "");  // stop_music()
                continue;
            }
            Boolean flag = m_mediaFlags.get(key);
            final boolean isSfx = sfx || (flag != null && flag);
            m_context.emit(!isSfx ? PlayerContext.OutputKind.Music
                    : isPage ? PlayerContext.OutputKind.Voice : PlayerContext.OutputKind.Sound, file);
        }
    }

    /**
     * Картинка страницы или объекта: первая из списка, чьё условие совпадает с тегом (без условия
     * — подходит всегда), с учётом переадресации. Анимированная — шаблон кадра с %d и число
     * кадров (число в имени файла перед расширением)
     *
     * @return null — картинки нет
     */
    public PlayerContext.OutputItem selectImage(String itemId) {
        if (m_rootBook != null && m_rootBook.isSuppressMedia()) {
            return null;
        }
        String names;
        String hierarchy;
        boolean animated;
        boolean removeFrameNumber = false;
        Page page = findPage(itemId);
        if (page != null) {
            names = page.getImageFileName();
            hierarchy = page.getExternalHierarchy();
            animated = page.isImageAnimated();
        } else {
            Obj obj = findObj(m_context.protoOf(itemId));
            if (obj == null) {
                return null;
            }
            names = obj.getImageFileName();
            hierarchy = obj.getExternalHierarchy();
            animated = obj.isAnimatedImage();
            removeFrameNumber = animated && obj.isGraphical();
        }
        // decoratePageImage: цепочка if/elseif по s.tag, без ограничения — "true"; первая подходящая
        final String tag = tagOf(itemId);
        for (String name : splitMediaNames(names)) {
            String constraint = m_mediaConstraints.get(mediaKey(hierarchy, name));
            if (constraint != null && !constraint.isEmpty() && !tag.equals(constraint)) {
                continue;
            }
            String redirect = m_mediaRedirects.get(name);
            final String file = redirect != null ? redirect : name;
            // getImagePath: у анимированной картинки число в имени — количество кадров,
            // кадры подставляются по таймеру: prefix1.ext ... prefixN.ext
            Matcher match = FILE_NAME_PATTERN.matcher(file);
            if (animated && match.matches() && !match.group(2).isEmpty()) {
                String prefix = match.group(1);
                if (removeFrameNumber && prefix.endsWith(".")) {
                    prefix = prefix.substring(0, prefix.length() - 1);
                }
                int frames;
                try {
                    frames = Integer.parseInt(match.group(2));
                } catch (NumberFormatException e) {
                    frames = 0;
                }
                if (frames > 0) {
                    return new PlayerContext.OutputItem(PlayerContext.OutputKind.Animation,
                            prefix + "%d" + match.group(3), frames);
                }
            }
            return new PlayerContext.OutputItem(PlayerContext.OutputKind.Image, file, 0);
        }
        return null;
    }

    public void showImage(String itemId) {
        PlayerContext.OutputItem image = selectImage(itemId);
        if (image != null) {
            m_context.emit(image.getKind(), image.getText(), image.getFrames());
        }
    }

    /**
     * INSTEAD take(obj): убирает объект из текущей комнаты и кладёт в инвентарь
     */
    private void take(String instanceId) {
        final String here = m_context.currentPageId();
        if (!here.isEmpty() && m_context.containerHas(here, instanceId)) {
            m_context.containerRemove(here, instanceId);
        }
        m_context.inventoryAdd(instanceId);
    }

    /**
     * nlb.lua addf: target null — инвентарь
     */
    private void addf(String target, String instanceId, boolean unique) {
        if (target == null) {
            if (!m_context.inInventory(instanceId)) {
                take(instanceId);
            } else if (!unique) {
                take(m_context.cloneInstance(instanceId));
            }
            return;
        }
        if (!m_context.containerHas(target, instanceId)) {
            m_context.containerAdd(target, instanceId);
        } else if (!unique) {
            m_context.containerAdd(target, m_context.cloneInstance(instanceId));
        }
    }

    private void pdscf(String instanceId) {
        if (!isObjEnabled(instanceId)) {
            return;
        }
        final String text = objDscF(instanceId);
        if (!text.isEmpty()) {
            m_context.emit(PlayerContext.OutputKind.Text, text);
            m_lastText += " " + text;
        }
    }

    private void pdscs(String ownerId) {
        // Копия: исполнение может менять контейнер
        List<String> contents = new ArrayList<>(m_context.containerContents(ownerId));
        for (String instanceId : contents) {
            Obj obj = findObj(m_context.protoOf(instanceId));
            if (obj != null && obj.isSuppressDsc()) {
                pdscs(instanceId);
                pdscf(instanceId);
            }
        }
    }

    private String objOrWarn(Modification mod, String name) {
        String obj = objOperand(name);
        if (obj == null) {
            m_warnings.accept("Warning: modification " + mod.getId() + ": object '" + name + "' not found");
        }
        return obj;
    }

    private void requireVar(Modification mod, Variable var) {
        if (var == null) {
            throw new PlayerException("Variable with id = " + mod.getVarId()
                    + " cannot be found for modification " + mod.getId());
        }
    }

    private static int parseCInt(String value) {
        // std::stoi: ведущие пробелы, знак, цифры; хвост игнорируется
        Matcher matcher = Pattern.compile("^[ \\t\\n\\x0B\\f\\r]*([+-]?\\d+)").matcher(value);
        if (!matcher.find()) {
            throw new PlayerException("stoi: invalid argument '" + value + "'");
        }
        return Integer.parseInt(matcher.group(1).replace("+", ""));
    }

    private void executeOne(Modification mod, ExecContext ctx) {
        final Variable var = variableOf(ctx, mod.getVarId());
        final Variable expr = variableOf(ctx, mod.getExprId());
        final String varName = var == null ? "" : var.getName();
        final String exprValue = expr == null ? "" : expr.getValue();
        // Операнды-списки: переменная может хранить ссылку на другой список (listobj в STEAD)
        final String varList = resolveListName(varName);
        final String exprList = resolveListName(exprValue);
        final Modification.Type type = mod.getType();

        switch (type) {
            case ASSIGN:
                requireVar(mod, var);
                m_context.setVar(varName, evaluate(exprValue));
                break;
            case TAG: {
                String target = var != null ? objOperand(varName) : ctx.m_self;
                if (target != null) {
                    m_context.setTag(target, exprValue);
                }
                break;
            }
            case GETTAG: {
                requireVar(mod, var);
                String target = exprValue.isEmpty() ? ctx.m_self : objOrWarn(mod, exprValue);
                m_context.setVar(varName, target != null ? Value.of(tagOf(target)) : Value.FALSE);
                break;
            }
            case HAVE: {
                requireVar(mod, var);
                String obj = objOperand(exprValue);
                m_context.setVar(varName, Value.of(obj != null && m_context.inInventory(obj)));
                break;
            }
            case CLONE: {
                requireVar(mod, var);
                String source = exprValue.isEmpty() ? ctx.m_self : objOrWarn(mod, exprValue);
                m_context.setVar(varName, source != null ? Value.of(m_context.cloneInstance(source)) : Value.FALSE);
                break;
            }
            case CNTNR: {
                requireVar(mod, var);
                String obj = exprValue.isEmpty() ? ctx.m_self : objOrWarn(mod, exprValue);
                String container = obj != null ? m_context.containerOf(obj) : "";
                m_context.setVar(varName, container.isEmpty() ? Value.FALSE : Value.of(container));
                break;
            }
            case ID: {
                requireVar(mod, var);
                String obj = objOrWarn(mod, exprValue);
                m_context.setVar(varName, obj != null ? Value.of(m_context.protoOf(obj)) : Value.FALSE);
                break;
            }
            case ADD:
            case ADDU: {
                // Получатель — объект по ИМЕНИ переменной, иначе s (decorateAddObj)
                String destination = var != null ? objByNameOnly(varName) : null;
                if (destination == null) {
                    destination = ctx.m_self;
                }
                String obj = objOrWarn(mod, exprValue);
                if (obj != null) {
                    addf(destination, obj, type == Modification.Type.ADDU);
                }
                break;
            }
            case ADDINV: {
                String obj = objOrWarn(mod, exprValue);
                if (obj != null) {
                    addf(null, obj, false);
                }
                break;
            }
            case ADDALL:
            case ADDALLU: {
                final boolean unique = type == Modification.Type.ADDALLU;
                String destination = var != null ? objByNameOnly(varName) : null;
                for (String item : m_context.listItems(exprList)) {
                    if (destination != null) {
                        addf(destination, item, unique);
                    } else if (var != null) {
                        m_context.listPush(varList, item);  // получатель — список
                    } else {
                        addf(ctx.m_self, item, unique);
                    }
                }
                break;
            }
            case REMOVE: {
                String obj = objOrWarn(mod, exprValue);
                if (obj == null) {
                    break;
                }
                String destination = var != null ? objByNameOnly(varName) : null;
                if (destination != null) {
                    m_context.containerRemove(destination, obj);
                } else if (var != null) {
                    m_context.listRemove(varList, obj);       // nlb:rmv(список, obj)
                    m_context.containerRemove("", obj);       // obj.container = nil
                } else {
                    m_context.containerRemove(m_context.currentPageId(), obj);  // objs():del — текущая комната
                }
                break;
            }
            case RMINV: {
                String obj = objOperand(exprValue);
                if (obj != null) {
                    m_context.inventoryRemove(obj);
                }
                break;
            }
            case CLEAR:
                if (exprValue.isEmpty()) {
                    m_context.containerClear(m_context.currentPageId());
                } else {
                    String objId = objByNameOnly(exprValue);
                    if (objId != null) {
                        m_context.containerClear(objId);
                    } else {
                        m_context.listClear(exprList);  // nlb:clear(listobj)
                    }
                }
                break;
            case CLRINV:
                m_context.inventoryClear();
                break;
            case OBJS: {
                requireVar(mod, var);
                String source = objOrWarn(mod, exprValue);
                if (source != null) {
                    for (String item : new ArrayList<>(m_context.containerContents(source))) {
                        m_context.listPush(varList, item);  // pushObjs: push каждого -> обратный порядок
                    }
                }
                break;
            }
            case SSND:
                playSound(ctx.m_self);
                break;
            case WSND:
                if (ctx.m_ww != null) {
                    playSound(ctx.m_ww);
                }
                break;
            case SND: {
                String obj = objOperand(exprValue);
                playSound(obj != null ? obj : ctx.m_self);
                break;
            }
            case SPUSH:
                m_context.listPush(exprList, ctx.m_self);
                break;
            case WPUSH:
                if (ctx.m_ww != null) {
                    m_context.listPush(exprList, ctx.m_ww);  // в Lua пушится nil
                }
                break;
            case PUSH:
                requireVar(mod, var);
                m_context.listPush(varList, valueToListItem(exprValue));
                break;
            case POP: {
                requireVar(mod, var);
                String value = m_context.listPop(exprList);
                m_context.setVar(varName, value != null ? parseListValue(value) : Value.FALSE);
                break;
            }
            case SINJECT:
                m_context.listInject(exprList, ctx.m_self);
                break;
            case INJECT:
                requireVar(mod, var);
                m_context.listInject(varList, valueToListItem(exprValue));
                break;
            case EJECT: {
                requireVar(mod, var);
                String value = m_context.listEject(exprList);
                m_context.setVar(varName, value != null ? parseListValue(value) : Value.FALSE);
                break;
            }
            case SHUFFLE:
                m_context.listShuffle(exprList);
                break;
            case PRN:
                printText(m_context.getVar(exprValue).toDisplayString());
                break;
            case DSC: {
                requireVar(mod, var);
                String obj = objOrWarn(mod, exprValue);
                m_context.setVar(varName, Value.of(obj != null ? objDscF(obj) : ""));
                break;
            }
            case PDSC: {
                String obj = objOrWarn(mod, exprValue);
                if (obj != null) {
                    pdscf(obj);
                }
                break;
            }
            case PDSCS: {
                String owner = exprValue.isEmpty() ? ctx.m_self : objOrWarn(mod, exprValue);
                if (owner != null) {
                    pdscs(owner);
                }
                break;
            }
            case ACT: {
                // Операнд-список (listobj): act применяется к каждому элементу списка
                String obj = objOperand(exprValue);
                if (obj != null) {
                    objActA(obj);
                } else if (m_context.listExists(exprList) || m_context.hasVar(exprValue)) {
                    for (String item : m_context.listItems(exprList)) {
                        objActA(item);
                    }
                } else if (!(m_externalCallHandler != null && m_externalCallHandler.test(exprValue, type))) {
                    objOrWarn(mod, exprValue);
                }
                break;
            }
            case ACTT: {
                requireVar(mod, var);
                String obj = objOrWarn(mod, exprValue);
                m_context.setVar(varName, Value.of(obj != null ? objActT(obj) : ""));
                break;
            }
            case ACTF: {
                String obj = objOperand(exprValue);
                if (obj != null) {
                    objActF(obj);
                } else if (m_context.listExists(exprList) || m_context.hasVar(exprValue)) {
                    for (String item : m_context.listItems(exprList)) {
                        objActF(item);
                    }
                } else if (!(m_externalCallHandler != null && m_externalCallHandler.test(exprValue, type))) {
                    objOrWarn(mod, exprValue);
                }
                break;
            }
            case USE: {
                // nlb:usea(source, target) -> source:usea(target), ww = nil. Списки (listobj):
                //  источник-список: usef — каждый элемент применяется к цели;
                //  цель-список: useda -> usedf — источник применяется к каждому элементу (w = ww = элемент)
                requireVar(mod, var);
                String source = objOperand(varName);
                String target = objOperand(exprValue);
                // Пустой список в STEAD — по-прежнему listobj; у нас он не хранится, поэтому список —
                // это и объявленная переменная без ссылки на объект
                final boolean sourceIsList = source == null && (m_context.listExists(varList) || m_context.hasVar(varName));
                final boolean targetIsList = target == null && (m_context.listExists(exprList) || m_context.hasVar(exprValue));
                if (source == null && !sourceIsList) {
                    objOrWarn(mod, varName);
                }
                if (target == null && !targetIsList) {
                    objOrWarn(mod, exprValue);
                }
                List<String> sources = new ArrayList<>();
                if (source != null) {
                    sources.add(source);
                } else if (sourceIsList) {
                    sources.addAll(m_context.listItems(varList));
                }
                for (String s : sources) {
                    if (target != null) {
                        objUseA(s, target, sourceIsList ? target : null);
                    } else if (targetIsList) {
                        for (String item : m_context.listItems(exprList)) {
                            objUseA(s, item, item);
                        }
                    }
                }
                break;
            }
            case SIZE:
                requireVar(mod, var);
                m_context.setVar(varName, Value.of((long) m_context.listSize(exprList)));
                break;
            case RND: {
                requireVar(mod, var);
                Value max = evaluate(exprValue);
                long maxValue = max.isNumber() ? (long) max.asDouble() : 1;
                m_context.setVar(varName, Value.of(m_context.random(maxValue)));
                break;
            }
            case ACHMAX:
                requireVar(mod, var);
                m_context.setAchievementMax(varName, parseCInt(exprValue));
                break;
            case ACHIEVE:
                m_context.achieve(exprValue, mod.getId());
                break;
            case ACHIEVED:
                requireVar(mod, var);
                m_context.setVar(varName, Value.of((long) m_context.achievementCount(exprValue)));
                break;
            case GOTO:
                m_context.requestGoto(exprValue);
                break;
            case SNAPSHOT:
                m_context.makeSnapshot();
                break;
            case COUNTGET:
                requireVar(mod, var);
                m_context.setVar(varName, Value.of(m_context.countGet(parseCInt(exprValue))));
                break;
            case COUNTRST:
                m_context.countReset();
                break;
            // Оформление окна INSTEAD: сообщаем, применяет его модель экрана
            case OPENURL:
                m_context.emit(PlayerContext.OutputKind.Info, "[URL: " + exprValue + "]");
                break;
            case WINGEOM:
                m_context.emit(PlayerContext.OutputKind.Info, "[WINGEOM: " + exprValue + "]");
                break;
            case INVGEOM:
                m_context.emit(PlayerContext.OutputKind.Info, "[INVGEOM: " + exprValue + "]");
                break;
            case WINCOLOR:
                m_context.emit(PlayerContext.OutputKind.Info, "[WINCOLOR: " + exprValue + "]");
                break;
            case INVCOLOR:
                m_context.emit(PlayerContext.OutputKind.Info, "[INVCOLOR: " + exprValue + "]");
                break;
            case WHILE:
            case IF:
            case IFHAVE:
            case ELSE:
            case ELSEIF:
            case END:
            case RETURN:
            default:
                break;  // обрабатываются в executeRange
        }
    }

    // ------------------------------------------------------------------ объекты

    private void runObjModifications(Obj obj, String selfInstance) {
        NonLinearBook book = bookOfObj(obj.getId());
        execute(obj.getModifications(), new ExecContext(book, selfInstance, null));
        // generateObjText: после модификаций — переменная объекта = true
        setVarTrue(book, obj.getVarId());
    }

    private void setVarTrue(NonLinearBook book, String varId) {
        if (PlayerContext.isEmpty(varId) || book == null) {
            return;
        }
        Variable variable = book.getVariableById(varId);
        if (variable != null && !variable.isDeleted() && !PlayerContext.isEmpty(variable.getName())) {
            m_context.setVar(variable.getName(), Value.TRUE);
        }
    }

    public String objActT(String instanceId) {
        Obj obj = findObj(m_context.protoOf(instanceId));
        return obj == null ? "" : expandText(obj.getActText());
    }

    public String objDscF(String instanceId) {
        Obj obj = findObj(m_context.protoOf(instanceId));
        return obj == null ? "" : expandText(obj.getText());
    }

    public void objActF(String instanceId) {
        Obj obj = findObj(m_context.protoOf(instanceId));
        if (obj != null) {
            runObjModifications(obj, instanceId);
            // decorateObjActEnd(collapseOnAct): в конце actf сворачиваемый объект переключается
            if (obj.isCollapsable() && obj.isGraphical()) {
                m_context.toggleCollapsed(instanceId);
            }
        }
    }

    /**
     * decorateObjActStart: acta = actp(); actf(); [cmn.actp()]; cmn.actf(s)
     */
    public void objActA(String instanceId) {
        Obj obj = findObj(m_context.protoOf(instanceId));
        if (obj == null) {
            return;
        }
        final String actText = objActT(instanceId);
        if (!actText.isEmpty()) {
            // actp: nlb:curloc().lasttext = s.actt(s) — текст действия ЗАМЕНЯЕТ lasttext
            m_context.emit(PlayerContext.OutputKind.Text, actText);
            m_lastText = actText;
        }
        runObjModifications(obj, instanceId);
        Obj common = commonOf(obj);
        if (common != null) {
            if (actText.isEmpty()) {
                final String commonActText = expandText(common.getActText());
                if (!commonActText.isEmpty()) {
                    printText(commonActText);
                }
            }
            runObjModifications(common, instanceId);  // аргумент s подменён текущим объектом
        }
    }

    public Obj commonOf(Obj obj) {
        final String id = obj == null ? "" : obj.getCommonToId();
        if (PlayerContext.isEmpty(id)) {
            return null;
        }
        Obj direct = findObj(id);
        if (direct != null) {
            return direct;
        }
        Variable var = bookOfObj(obj.getId()).getVariableById(id);
        return (var != null && !var.isDeleted()) ? findObj(var.getValue()) : null;
    }

    public boolean isObjEnabled(String instanceId) {
        Obj obj = findObj(m_context.protoOf(instanceId));
        if (obj == null) {
            return false;
        }
        return evaluateConstraint(bookOfObj(obj.getId()), obj.getConstrId());
    }

    /**
     * usea: тексты применения и модификации use-ссылок
     *
     * @return был ли выведен текст
     */
    public boolean objUseA(String sourceInstanceId, String targetInstanceId, String ww) {
        return objUse(sourceInstanceId, targetInstanceId, ww, true);
    }

    /**
     * usef: только модификации use-ссылок
     */
    public void objUseF(String sourceInstanceId, String targetInstanceId, String ww) {
        objUse(sourceInstanceId, targetInstanceId, ww, false);
    }

    private Boolean useConstraint(NonLinearBook book, String moduleConstrId, Link link) {
        Variable c = PlayerContext.isEmpty(link.getConstrId()) ? null : book.getVariableById(link.getConstrId());
        if (c != null && c.isDeleted()) {
            c = null;
        }
        Variable m = (link.isObeyToModuleConstraint() && !PlayerContext.isEmpty(moduleConstrId))
                ? book.getVariableById(moduleConstrId) : null;
        if (c == null && m == null) {
            return null;  // неограниченная ссылка
        }
        boolean value = true;
        if (m != null) {
            value = evaluate(m.getValue()).isTruthy();
        }
        if (c != null) {
            value = value && evaluate(c.getValue()).isTruthy();
        }
        return link.isPositiveConstraint() ? value : !value;
    }

    private boolean objUse(String sourceInstanceId, String targetInstanceId, String ww, boolean printTexts) {
        Obj source = findObj(m_context.protoOf(sourceInstanceId));
        if (source == null) {
            return false;
        }
        final NonLinearBook book = bookOfObj(source.getId());
        final String targetProto = m_context.protoOf(targetInstanceId);  // w.nlbid == target.nlbid
        List<Link> uses = new ArrayList<>();
        for (Link link : source.getLinks()) {
            if (!link.isDeleted() && link.getTarget().equals(targetProto)) {
                uses.add(link);
            }
        }
        // createUseBuildingBlocks: ограничение ссылки с учётом модульного ограничения
        Page modulePage = book.getParentPage();
        final String moduleConstrId = modulePage == null ? "" : modulePage.getModuleConstrId();

        // usep: тексты успеха/неудачи; lasttext очищается, тексты применения дописываются;
        // если текста не было — восстанавливается
        boolean wasText = false;
        final String previousLastText = m_lastText;
        if (printTexts) {
            m_lastText = "";
            for (Link link : uses) {
                final String success = expandText(link.getText());
                if (success.isEmpty()) {
                    continue;  // в STEAD-коде else-ветка вложена в проверку непустого текста успеха
                }
                Boolean constraint = useConstraint(book, moduleConstrId, link);
                if (constraint == null || constraint) {
                    printText(success + " ");
                    wasText = true;
                } else {
                    final String failure = expandText(link.getAltText());
                    if (!failure.isEmpty()) {
                        printText(failure + " ");
                        wasText = true;
                    }
                }
            }
            if (!wasText) {
                m_lastText = previousLastText;
            }
        }
        // usef: модификации и переменные use-ссылок (ограничение проверяется заново)
        for (Link link : uses) {
            Boolean constraint = useConstraint(book, moduleConstrId, link);
            if (constraint != null && !constraint) {
                continue;
            }
            execute(link.getModifications(), new ExecContext(book, sourceInstanceId, ww));
            setVarTrue(book, link.getVarId());
        }
        return wasText;
    }

    // ------------------------------------------------------------------ текст действий ($^$)

    /**
     * Текст, накопленный действиями на текущей странице (nlb:curloc().lasttext)
     */
    public String lastText() {
        return m_lastText;
    }

    public void resetLastText() {
        m_lastText = "";
    }

    public void setLastText(String text) {
        m_lastText = text;
    }
}
