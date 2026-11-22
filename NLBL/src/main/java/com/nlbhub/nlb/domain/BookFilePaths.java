package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.NodeItem;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.Variable;

import java.util.HashMap;
import java.util.Map;

/**
 * Файл книги — словами автора: «pages/&lt;id&gt;/text/ru» -> «Page "Pantry": text (ru)». Нужен,
 * когда git сообщает о файлах (правки соавторов пересеклись): каждое поле книги — отдельный
 * файл, так что путь однозначно называет элемент и поле. Элемент ищется в книге (ее версии у
 * автора); не нашёлся — имя из {@link Names} (история: элемент удалён, его имя — в той версии
 * книги), иначе короткий id.
 */
public final class BookFilePaths {
    /**
     * Имя элемента, которого нет в книге: elementDir — его папка относительно папки книги
     * («pages/&lt;id&gt;», «objs/&lt;id&gt;», «pages/&lt;id&gt;/links/&lt;id&gt;», «vars/&lt;id&gt;»);
     * null или пусто — не знаем
     */
    public interface Names {
        String name(String elementDir);
    }

    private static final Names NO_NAMES = elementDir -> null;

    private static final Map<String, String> FIELDS = new HashMap<>();

    static {
        FIELDS.put("text", "text");
        FIELDS.put("caption", "caption");
        FIELDS.put("use_capt", "\"use caption\" flag");
        FIELDS.put("coords", "position on the scheme");
        FIELDS.put("lnkorder", "order of links");
        FIELDS.put("modorder", "order of modifications");
        FIELDS.put("content", "contents (objects inside)");
        FIELDS.put("travtext", "text of the module entry");
        FIELDS.put("rettext", "text of the return from the module");
        FIELDS.put("aintext", "text of the incoming autowire");
        FIELDS.put("aouttext", "text of the outgoing autowire");
        FIELDS.put("modname", "module name");
        FIELDS.put("target", "target");
        FIELDS.put("name", "name");
        FIELDS.put("disp", "displayed name");
        FIELDS.put("acttext", "action text");
        FIELDS.put("image", "image");
        FIELDS.put("imagebg", "background image");
        FIELDS.put("sound", "sound");
        FIELDS.put("value", "value");
        FIELDS.put("varid", "variable");
        FIELDS.put("exprid", "expression");
        FIELDS.put("type", "type");
        FIELDS.put("containerid", "container");
        FIELDS.put("title", "title");
        FIELDS.put("startpoint", "start page");
        FIELDS.put("language", "language");
        FIELDS.put("author", "author");
        FIELDS.put("version", "version");
        FIELDS.put("notes", "author notes");
        FIELDS.put("nlbversion", "NLBB version that saved the book");
        // остальные поля страниц, объектов, ссылок, модификаций и книги (история версий)
        FIELDS.putIfAbsent("actonkey", "\"act on key\" flag");
        FIELDS.putIfAbsent("alt_text", "alternative text");
        FIELDS.putIfAbsent("animated", "\"animated image\" flag");
        FIELDS.putIfAbsent("autfirst", "\"autowires first\" flag");
        FIELDS.putIfAbsent("auto", "\"automatic\" flag");
        FIELDS.putIfAbsent("auto_in", "incoming autowire flag");
        FIELDS.putIfAbsent("auto_out", "outgoing autowire flag");
        FIELDS.putIfAbsent("autoid", "incoming autowire condition");
        FIELDS.putIfAbsent("autopgs", "pages with autowires");
        FIELDS.putIfAbsent("autoret", "\"automatic return from the module\" flag");
        FIELDS.putIfAbsent("autotrav", "\"automatic module entry\" flag");
        FIELDS.putIfAbsent("autoutid", "outgoing autowire condition");
        FIELDS.putIfAbsent("cachetxt", "\"cache text\" flag");
        FIELDS.putIfAbsent("callback", "\"callback\" flag");
        FIELDS.putIfAbsent("choicelnk", "choice link");
        FIELDS.putIfAbsent("clearutt", "\"clear utterance\" flag");
        FIELDS.putIfAbsent("collapsable", "\"collapsable\" flag");
        FIELDS.putIfAbsent("commonto", "\"common to\" object");
        FIELDS.putIfAbsent("constrid", "condition");
        FIELDS.putIfAbsent("coordsor", "coordinates origin");
        FIELDS.putIfAbsent("datatype", "data type");
        FIELDS.putIfAbsent("deftagid", "default tag");
        FIELDS.putIfAbsent("effect", "appearance effect");
        FIELDS.putIfAbsent("external", "\"external\" flag");
        FIELDS.putIfAbsent("extmod", "external module");
        FIELDS.putIfAbsent("fill", "fill colour");
        FIELDS.putIfAbsent("forcedsc", "\"force description\" flag");
        FIELDS.putIfAbsent("fullauto", "\"full autowire\" flag");
        FIELDS.putIfAbsent("globauto", "\"global autowire\" flag");
        FIELDS.putIfAbsent("graphical", "\"graphical\" flag");
        FIELDS.putIfAbsent("imganim", "\"animated image\" flag");
        FIELDS.putIfAbsent("imginv", "\"image in the inventory\" flag");
        FIELDS.putIfAbsent("imgscene", "\"image in the scene\" flag");
        FIELDS.putIfAbsent("kind", "kind");
        FIELDS.putIfAbsent("license", "license");
        FIELDS.putIfAbsent("loadonce", "\"load once\" flag");
        FIELDS.putIfAbsent("looped", "\"looped\" flag");
        FIELDS.putIfAbsent("maxframe", "last frame");
        FIELDS.putIfAbsent("modcnsid", "module condition");
        FIELDS.putIfAbsent("morphout", "\"morph out\" object");
        FIELDS.putIfAbsent("morphover", "\"morph over\" object");
        FIELDS.putIfAbsent("mvdirection", "movement direction");
        FIELDS.putIfAbsent("needsact", "\"needs action\" flag");
        FIELDS.putIfAbsent("noactredraw", "\"no redraw on action\" flag");
        FIELDS.putIfAbsent("nosave", "\"no save\" flag");
        FIELDS.putIfAbsent("nousetxt", "\"cannot use\" text");
        FIELDS.putIfAbsent("offset", "offset on the screen");
        FIELDS.putIfAbsent("once", "\"once\" flag");
        FIELDS.putIfAbsent("pausef", "pause frames");
        FIELDS.putIfAbsent("perfgame", "\"perfect game\" achievement");
        FIELDS.putIfAbsent("preloadf", "preloaded frames");
        FIELDS.putIfAbsent("preserved", "\"preserved\" flag");
        FIELDS.putIfAbsent("retpage", "return page");
        FIELDS.putIfAbsent("screen", "screen size");
        FIELDS.putIfAbsent("showoncur", "\"show on cursor\" flag");
        FIELDS.putIfAbsent("soundsfx", "\"sound effect (SFX)\" flag");
        FIELDS.putIfAbsent("startframe", "first frame");
        FIELDS.putIfAbsent("stroke", "line colour");
        FIELDS.putIfAbsent("suppdsc", "\"suppress description\" flag");
        FIELDS.putIfAbsent("suppmed", "\"suppress media\" flag");
        FIELDS.putIfAbsent("suppsou", "\"suppress sound\" flag");
        FIELDS.putIfAbsent("takable", "\"takable\" flag");
        FIELDS.putIfAbsent("theme", "theme");
        FIELDS.putIfAbsent("tvarid", "time variable");
        FIELDS.putIfAbsent("txtcolor", "text colour");
        FIELDS.putIfAbsent("use_mpl", "\"use module page list\" flag");
    }

    private BookFilePaths() {
    }

    /**
     * @param book книга (главная), может быть null — тогда только по пути
     * @param path путь файла относительно папки книги, через «/»
     */
    public static String describe(final NonLinearBook book, final String path) {
        return describe(book, path, NO_NAMES);
    }

    /**
     * @param names имена элементов, которых нет в книге
     */
    public static String describe(final NonLinearBook book, final String path, final Names names) {
        final String[] parts = path.split("/");
        return describe(book, parts, 0, "", names == null ? NO_NAMES : names);
    }

    /**
     * Поле элемента словами автора: «text/ru» -> «text (ru)», «coords/left» -> «position on the
     * scheme»; пусто — «»
     */
    public static String fieldName(final String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) {
            return "";
        }
        return field(fieldPath.split("/"), 0).substring(2);
    }

    /**
     * Есть ли элемент (папка «pages/&lt;id&gt;», «objs/&lt;id&gt;», в том числе внутри модулей:
     * «pages/&lt;id&gt;/module/objs/&lt;id&gt;») в книге
     */
    public static boolean inBook(final NonLinearBook book, final String elementDir) {
        final String[] parts = elementDir.split("/");
        NonLinearBook current = book;
        int i = 0;
        while (current != null && i + 1 < parts.length) {
            if ("pages".equals(parts[i])) {
                final Page page = current.getPageById(parts[i + 1]);
                if (page == null) {
                    return false;
                }
                if (i + 2 == parts.length) {
                    return true;
                }
                if ("module".equals(parts[i + 2])) {
                    current = page.getModule();
                    i += 3;
                    continue;
                }
                return false;
            }
            if ("objs".equals(parts[i])) {
                return i + 2 == parts.length && current.getObjById(parts[i + 1]) != null;
            }
            return false;
        }
        return false;
    }

    private static String dir(final String[] parts, final int end) {
        return String.join("/", java.util.Arrays.copyOfRange(parts, 0, end));
    }

    private static String known(final Names names, final String[] parts, final int end) {
        final String name = names.name(dir(parts, end));
        return name == null || name.trim().isEmpty() ? null : abbreviate(name);
    }

    private static String describe(final NonLinearBook book, final String[] parts, final int i, final String prefix,
                                   final Names names) {
        if (i >= parts.length) {
            return prefix.isEmpty() ? "the book" : prefix.trim();
        }
        final String head = parts[i];
        if ("pages".equals(head) && i + 1 < parts.length) {
            final Page page = book == null ? null : book.getPageById(parts[i + 1]);
            final String known = page == null ? known(names, parts, i + 2) : null;
            final String name = prefix + "Page \"" + (known != null ? known : pageName(page, parts[i + 1])) + "\"";
            if (i + 2 < parts.length && "module".equals(parts[i + 2])) {
                final String module = page != null && page.getModuleName() != null && !page.getModuleName().isEmpty()
                        ? page.getModuleName() : known != null ? known : shortId(parts[i + 1]);
                return describe(page == null ? null : page.getModule(), parts, i + 3,
                        prefix + "Module \"" + module + "\" -> ", names);
            }
            return item(page, name, parts, i + 2, names);
        }
        if ("objs".equals(head) && i + 1 < parts.length) {
            final Obj obj = book == null ? null : book.getObjById(parts[i + 1]);
            final String known = obj == null ? known(names, parts, i + 2) : null;
            final String name = prefix + "Object \"" + (obj != null ? objName(obj, parts[i + 1])
                    : known != null ? known : shortId(parts[i + 1])) + "\"";
            return item(obj, name, parts, i + 2, names);
        }
        if ("vars".equals(head) && i + 1 < parts.length) {
            final Variable variable = book == null ? null : book.getVariableById(parts[i + 1]);
            final String name;
            if (variable == null) {
                final String known = known(names, parts, i + 2);
                name = known != null ? "Variable \"" + known + "\"" : "Variable or expression " + shortId(parts[i + 1]);
            } else if (variable.getType() == Variable.Type.EXPRESSION || variable.getType().name().endsWith("CONSTRAINT")) {
                // выражение модификации или условие: имени нет, есть текст
                name = (variable.getType() == Variable.Type.EXPRESSION ? "Expression" : "Condition")
                        + " \"" + abbreviate(variable.getValue()) + "\"";
            } else {
                name = "Variable \"" + variable.getName() + "\"";
            }
            return prefix + name + field(parts, i + 2);
        }
        if ("modules".equals(head) && i + 1 < parts.length) {
            return prefix + "External module \"" + parts[i + 1] + "\"";
        }
        if (i + 1 == parts.length && FIELDS.containsKey(head)) {
            return prefix + "Book settings: " + FIELDS.get(head);
        }
        return prefix + "File " + String.join("/", java.util.Arrays.copyOfRange(parts, i, parts.length));
    }

    /**
     * Поле страницы или объекта, его ссылка или модификация
     */
    private static String item(final NodeItem item, final String name, final String[] parts, final int i,
                               final Names names) {
        if (i + 1 < parts.length && "links".equals(parts[i])) {
            final Link link = item == null ? null : item.getLinkById(parts[i + 1]);
            final String known = link == null ? known(names, parts, i + 2) : null;
            final String text = link != null && link.getText() != null && !link.getText().isEmpty()
                    ? link.getText() : known != null ? known : shortId(parts[i + 1]);
            final String linkName = "Link \"" + abbreviate(text) + "\" of " + lowerFirst(name);
            if (i + 3 < parts.length && "modifications".equals(parts[i + 2])) {
                return "Modification of " + lowerFirst(linkName) + field(parts, i + 4);
            }
            return linkName + field(parts, i + 2);
        }
        if (i + 1 < parts.length && "modifications".equals(parts[i])) {
            return "Modification of " + lowerFirst(name) + field(parts, i + 2);
        }
        return name + field(parts, i);
    }

    /**
     * «: text (ru)»; пусто — сам элемент целиком
     */
    private static String field(final String[] parts, final int i) {
        if (i >= parts.length) {
            return "";
        }
        final String field = FIELDS.getOrDefault(parts[i], parts[i]);
        if (i + 1 < parts.length && !"coords".equals(parts[i])) {
            return ": " + field + " (" + parts[i + 1] + ")";
        }
        return ": " + field;
    }

    /**
     * Имя объекта; без имени (спрайт VN) — его картинка
     */
    private static String objName(final Obj obj, final String id) {
        if (obj.getName() != null && !obj.getName().trim().isEmpty()) {
            return obj.getName();
        }
        if (obj.getImageFileName() != null && !obj.getImageFileName().trim().isEmpty()) {
            return abbreviate(obj.getImageFileName());
        }
        return shortId(id);
    }

    private static String pageName(final Page page, final String id) {
        if (page == null) {
            return shortId(id);
        }
        if (page.getCaption() != null && !page.getCaption().trim().isEmpty()) {
            return abbreviate(page.getCaption());
        }
        if (page.getText() != null && !page.getText().trim().isEmpty()) {
            return abbreviate(page.getText());
        }
        // страница-модуль часто без заголовка и текста — по имени модуля
        if (page.getModuleName() != null && !page.getModuleName().trim().isEmpty()) {
            return abbreviate(page.getModuleName());
        }
        return shortId(id);
    }

    private static String abbreviate(final String text) {
        if (text == null) {
            return "";
        }
        final String line = text.trim().replaceAll("\\s+", " ");
        return line.length() <= 40 ? line : line.substring(0, 39) + "…";
    }

    private static String shortId(final String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private static String lowerFirst(final String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
