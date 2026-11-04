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
 * автора); не нашёлся — короткий id.
 */
public final class BookFilePaths {
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
    }

    private BookFilePaths() {
    }

    /**
     * @param book книга (главная), может быть null — тогда только по пути
     * @param path путь файла относительно папки книги, через «/»
     */
    public static String describe(final NonLinearBook book, final String path) {
        final String[] parts = path.split("/");
        return describe(book, parts, 0, "");
    }

    private static String describe(final NonLinearBook book, final String[] parts, final int i, final String prefix) {
        if (i >= parts.length) {
            return prefix.isEmpty() ? "the book" : prefix.trim();
        }
        final String head = parts[i];
        if ("pages".equals(head) && i + 1 < parts.length) {
            final Page page = book == null ? null : book.getPageById(parts[i + 1]);
            final String name = prefix + "Page \"" + pageName(page, parts[i + 1]) + "\"";
            if (i + 2 < parts.length && "module".equals(parts[i + 2])) {
                final String module = page != null && page.getModuleName() != null && !page.getModuleName().isEmpty()
                        ? page.getModuleName() : shortId(parts[i + 1]);
                return describe(page == null ? null : page.getModule(), parts, i + 3,
                        prefix + "Module \"" + module + "\" -> ");
            }
            return item(page, name, parts, i + 2);
        }
        if ("objs".equals(head) && i + 1 < parts.length) {
            final Obj obj = book == null ? null : book.getObjById(parts[i + 1]);
            final String name = prefix + "Object \"" + (obj != null && obj.getName() != null && !obj.getName().isEmpty()
                    ? obj.getName() : shortId(parts[i + 1])) + "\"";
            return item(obj, name, parts, i + 2);
        }
        if ("vars".equals(head) && i + 1 < parts.length) {
            final Variable variable = book == null ? null : book.getVariableById(parts[i + 1]);
            final String name;
            if (variable == null) {
                name = "Variable or expression " + shortId(parts[i + 1]);
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
    private static String item(final NodeItem item, final String name, final String[] parts, final int i) {
        if (i + 1 < parts.length && "links".equals(parts[i])) {
            final Link link = item == null ? null : item.getLinkById(parts[i + 1]);
            final String text = link != null && link.getText() != null && !link.getText().isEmpty()
                    ? link.getText() : shortId(parts[i + 1]);
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
