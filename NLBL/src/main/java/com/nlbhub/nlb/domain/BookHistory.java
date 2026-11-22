package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.vcs.BookFileChange;
import com.nlbhub.nlb.vcs.BookMergeRules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Изменения версии книги словами автора: изменённые файлы ({@link BookFileChange}) собраны по
 * элементам книги — страница, объект, ссылка, модификация, переменная, картинка или звук (с её
 * .flag/.preset/.constrid/.redirect), внешний модуль, настройки книги. У элемента — что с ним
 * («добавлен / удалён / изменён») и поля «было / стало».
 * <p>
 * Шум — изменения, которые на игру не влияют и автору обычно неинтересны (как в
 * {@link BookMergeRules}): положение на схеме, порядок ссылок/модификаций/объектов, версия
 * редактора. Поле помечается {@link FieldChange#isNoise()}, элемент только с шумом —
 * {@link ElementChange#isNoiseOnly()}; окно истории сворачивает их.
 * <p>
 * Имя элемента — из книги (как его видит автор сейчас); элемента в книге нет (удалён) — из файлов
 * самой версии: сначала из изменений, затем из {@link VersionTexts} (версия и предыдущая).
 */
public final class BookHistory {
    /**
     * Тексты файлов в папке элемента в версии и в предыдущей версии ({путь от папки: текст});
     * обычно — VCSAdapter.readVersionTexts
     */
    public interface VersionTexts {
        Map<String, String> texts(String dirPath, boolean previous);

        /**
         * Есть ли в версии (или предыдущей) папка элемента; по умолчанию — по её текстам
         */
        default boolean exists(String dirPath, boolean previous) {
            return !texts(dirPath, previous).isEmpty();
        }
    }

    public enum Type {
        BOOK, PAGE, LINK, OBJECT, MODIFICATION, VARIABLE, IMAGE, SOUND, MODULE, FILE
    }

    /**
     * Поле элемента: файл и «было / стало»
     */
    public static final class FieldChange {
        private final BookFileChange m_file;
        private final String m_fieldPath;
        private final String m_label;
        private final boolean m_noise;
        private final String m_displayBefore;
        private final String m_displayAfter;

        FieldChange(BookFileChange file, String fieldPath, String label, boolean noise, String displayBefore,
                    String displayAfter) {
            m_file = file;
            m_fieldPath = fieldPath;
            m_label = label;
            m_noise = noise;
            m_displayBefore = displayBefore;
            m_displayAfter = displayAfter;
        }

        /**
         * Значение «было» для показа: ссылка на элемент (контейнер, цель ссылки, стартовая
         * страница…) — его имя («Page "Pantry"»), иначе как в файле
         */
        public String getDisplayBefore() {
            return m_displayBefore;
        }

        /**
         * Значение «стало» для показа (см. {@link #getDisplayBefore()})
         */
        public String getDisplayAfter() {
            return m_displayAfter;
        }

        /**
         * Файл (путь, вид, тексты, id содержимого)
         */
        public BookFileChange getFile() {
            return m_file;
        }

        /**
         * Путь поля внутри элемента («text/ru»); пусто — сам файл (картинка, звук)
         */
        public String getFieldPath() {
            return m_fieldPath;
        }

        /**
         * Поле словами автора: «text (ru)», «position on the scheme», «file»
         */
        public String getLabel() {
            return m_label;
        }

        public BookFileChange.Kind getKind() {
            return m_file.getKind();
        }

        public String getBefore() {
            return m_file.getBefore();
        }

        public String getAfter() {
            return m_file.getAfter();
        }

        public boolean isBinary() {
            return m_file.isBinary();
        }

        public boolean isNoise() {
            return m_noise;
        }

        @Override
        public String toString() {
            return m_label + " " + m_file.getKind();
        }
    }

    /**
     * Элемент книги и его изменённые поля
     */
    public static final class ElementChange {
        private final String m_key;
        private final Type m_type;
        private final String m_title;
        private final BookFileChange.Kind m_kind;
        private final List<FieldChange> m_fields;

        ElementChange(String key, Type type, String title, BookFileChange.Kind kind, List<FieldChange> fields) {
            m_key = key;
            m_type = type;
            m_title = title;
            m_kind = kind;
            m_fields = Collections.unmodifiableList(fields);
        }

        /**
         * Папка элемента относительно папки книги («pages/&lt;id&gt;»; у картинки — путь файла;
         * настройки книги — «» или «pages/&lt;id&gt;/module»)
         */
        public String getKey() {
            return m_key;
        }

        public Type getType() {
            return m_type;
        }

        /**
         * Элемент словами автора: «Page "Pantry"», «Link "To the gate" of page "Pantry"»
         */
        public String getTitle() {
            return m_title;
        }

        /**
         * Добавлен (все файлы новые), удалён (все удалены) или изменён
         */
        public BookFileChange.Kind getKind() {
            return m_kind;
        }

        public List<FieldChange> getFields() {
            return m_fields;
        }

        /**
         * Изменён только шум (положение на схеме, порядок): автору обычно неинтересно
         */
        public boolean isNoiseOnly() {
            return m_kind == BookFileChange.Kind.MODIFIED && m_fields.stream().allMatch(FieldChange::isNoise);
        }

        @Override
        public String toString() {
            return m_title + " " + m_kind + " " + m_fields;
        }
    }

    /**
     * Где файл: элемент, поле, тип
     */
    static final class Location {
        final String m_key;
        final String m_field;
        final Type m_type;

        Location(String key, String field, Type type) {
            m_key = key;
            m_field = field;
            m_type = type;
        }
    }

    /**
     * Поля-ссылки на страницу или объект (значение — id): показываются именем элемента
     */
    private static final java.util.Set<String> REFERENCES = new java.util.HashSet<>(Arrays.asList(
            "containerid", "target", "startpoint", "retpage", "commonto", "morphover", "morphout"));
    private static final List<String> MEDIA_META = Arrays.asList(".flag", ".preset", ".constrid", ".redirect");
    private static final Map<String, String> MEDIA_FIELDS = new LinkedHashMap<>();

    static {
        MEDIA_FIELDS.put("", "file");
        MEDIA_FIELDS.put(".flag", "sound effect (SFX) flag");
        MEDIA_FIELDS.put(".preset", "export preset");
        MEDIA_FIELDS.put(".constrid", "condition");
        MEDIA_FIELDS.put(".redirect", "redirect");
    }

    private BookHistory() {
    }

    /**
     * Изменения по элементам: сначала то, что влияет на игру (настройки книги, страницы, ссылки,
     * объекты, модификации, переменные, картинки, звуки, модули), затем элементы только с шумом;
     * внутри — по заголовку
     *
     * @param book  книга, как она открыта в редакторе (имена элементов); может быть null
     * @param files изменённые файлы версии
     * @param texts тексты версии — для имён удалённых элементов; может быть null
     */
    public static List<ElementChange> group(final NonLinearBook book, final List<BookFileChange> files,
                                            final VersionTexts texts) {
        final Map<String, List<BookFileChange>> byKey = new LinkedHashMap<>();
        final Map<String, Location> locations = new LinkedHashMap<>();
        for (BookFileChange file : files) {
            final Location location = locate(file.getPath());
            byKey.computeIfAbsent(location.m_key, k -> new ArrayList<>()).add(file);
            locations.putIfAbsent(location.m_key, location);
        }
        // сначала — что с элементом (добавлен, удалён, изменён)
        final Map<String, BookFileChange.Kind> kinds = new LinkedHashMap<>();
        for (Map.Entry<String, List<BookFileChange>> entry : byKey.entrySet()) {
            boolean allAdded = true;
            boolean allDeleted = true;
            for (BookFileChange file : entry.getValue()) {
                allAdded &= file.getKind() == BookFileChange.Kind.ADDED;
                allDeleted &= file.getKind() == BookFileChange.Kind.DELETED;
            }
            kinds.put(entry.getKey(), kind(locations.get(entry.getKey()), entry.getValue(), allAdded, allDeleted, texts));
        }
        // имя — из файлов самого элемента (словарь по элементам: перебор всех файлов для каждого
        // элемента на большой версии — тысячи элементов на десятки тысяч файлов — слишком долог).
        // Элемент добавлен или удалён целиком — все его файлы среди изменений, версию не читать
        // Имя каждой папки ищется один раз (на неё ссылаются многие поля)
        final Map<String, java.util.Optional<String>> known = new java.util.HashMap<>();
        final BookFilePaths.Names names = elementDir -> known.computeIfAbsent(elementDir, dir -> {
            final BookFileChange.Kind kind = kinds.get(dir);
            final boolean whole = kind == BookFileChange.Kind.ADDED || kind == BookFileChange.Kind.DELETED;
            return java.util.Optional.ofNullable(
                    name(dir, byKey.getOrDefault(dir, Collections.emptyList()), whole ? null : texts, book));
        }).orElse(null);
        final List<ElementChange> result = new ArrayList<>();
        for (Map.Entry<String, List<BookFileChange>> entry : byKey.entrySet()) {
            final Location location = locations.get(entry.getKey());
            final List<FieldChange> fields = new ArrayList<>();
            for (BookFileChange file : entry.getValue()) {
                final Location fileLocation = locate(file.getPath());
                final boolean reference = REFERENCES.contains(lastSegment(fileLocation.m_field));
                final String root = bookRoot(location.m_key);
                fields.add(new FieldChange(file, fileLocation.m_field, label(fileLocation), isNoise(file.getPath(), location),
                        reference ? reference(book, root, file.getBefore(), names) : file.getBefore(),
                        reference ? reference(book, root, file.getAfter(), names) : file.getAfter()));
            }
            final BookFileChange.Kind kind = kinds.get(entry.getKey());
            final boolean whole = kind == BookFileChange.Kind.ADDED || kind == BookFileChange.Kind.DELETED;
            fields.sort(Comparator.comparing(FieldChange::isNoise).thenComparing(FieldChange::getLabel));
            String title = title(book, location, names);
            if (location.m_type == Type.OBJECT) {
                // объекты с одной картинкой (спрайт на нескольких страницах) различаются местом
                final String container = container(book, location.m_key, entry.getValue(), whole ? null : texts);
                if (container != null && !container.trim().isEmpty()) {
                    final String where = reference(book, bookRoot(location.m_key), container.trim(), names);
                    if (!where.equals(container.trim())) {
                        title += (where.startsWith("Page") ? " on " : " in ")
                                + Character.toLowerCase(where.charAt(0)) + where.substring(1);
                    }
                }
            }
            result.add(new ElementChange(location.m_key, location.m_type, title, kind, fields));
        }
        result.sort(Comparator.comparing(ElementChange::isNoiseOnly)
                .thenComparing(ElementChange::getType)
                .thenComparing(ElementChange::getTitle, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Добавлен ли элемент целиком, удалён или изменён. Картинка или звук — как сам файл (новый
     * .preset у старой картинки — изменение). Страница, объект…: все файлы новые и папки элемента
     * не было в предыдущей версии — добавлен (новое поле старой страницы — изменение); все удалены
     * и папки не осталось — удалён. Без текстов версий — только по файлам. Настройки книги и
     * прочие файлы — всегда изменение
     */
    private static BookFileChange.Kind kind(final Location location, final List<BookFileChange> files,
                                            final boolean allAdded, final boolean allDeleted, final VersionTexts texts) {
        switch (location.m_type) {
            case BOOK:
            case FILE:
                return BookFileChange.Kind.MODIFIED;
            case IMAGE:
            case SOUND:
                for (BookFileChange file : files) {
                    if (locate(file.getPath()).m_field.isEmpty()) {
                        return file.getKind();
                    }
                }
                return BookFileChange.Kind.MODIFIED;
            default:
                if (allAdded && (texts == null || !texts.exists(location.m_key, true))) {
                    return BookFileChange.Kind.ADDED;
                }
                if (allDeleted && (texts == null || !texts.exists(location.m_key, false))) {
                    return BookFileChange.Kind.DELETED;
                }
                return BookFileChange.Kind.MODIFIED;
        }
    }

    /**
     * Элемент и поле по пути файла (относительно папки книги)
     */
    static Location locate(final String path) {
        final String[] parts = path.split("/");
        int i = 0;
        while (i < parts.length) {
            final String head = parts[i];
            if (("pages".equals(head) || "objs".equals(head)) && i + 1 < parts.length) {
                int j = i + 2;
                Type type = "pages".equals(head) ? Type.PAGE : Type.OBJECT;
                if (type == Type.PAGE && j < parts.length && "module".equals(parts[j])) {
                    // внутренний модуль: дальше — книга модуля
                    i = j + 1;
                    continue;
                }
                if (j + 1 < parts.length && "links".equals(parts[j])) {
                    type = Type.LINK;
                    j += 2;
                }
                if (j + 1 < parts.length && "modifications".equals(parts[j])) {
                    type = Type.MODIFICATION;
                    j += 2;
                }
                return new Location(join(parts, 0, j), join(parts, j, parts.length), type);
            }
            if ("vars".equals(head) && i + 1 < parts.length) {
                return new Location(join(parts, 0, i + 2), join(parts, i + 2, parts.length), Type.VARIABLE);
            }
            if ((NonLinearBook.IMAGES_DIR_NAME.equals(head) || NonLinearBook.SOUND_DIR_NAME.equals(head)) && i + 1 < parts.length) {
                final String file = join(parts, i + 1, parts.length);
                String meta = "";
                for (String ext : MEDIA_META) {
                    if (file.endsWith(ext) && file.length() > ext.length()) {
                        meta = ext;
                    }
                }
                final String base = file.substring(0, file.length() - meta.length());
                return new Location(join(parts, 0, i + 1) + "/" + base, meta,
                        NonLinearBook.IMAGES_DIR_NAME.equals(head) ? Type.IMAGE : Type.SOUND);
            }
            if ("modules".equals(head) && i + 1 < parts.length) {
                return new Location(join(parts, 0, i + 2), join(parts, i + 2, parts.length), Type.MODULE);
            }
            if (i == parts.length - 1) {
                return new Location(join(parts, 0, i), head, Type.BOOK);
            }
            break;
        }
        return new Location(path, "", Type.FILE);
    }

    private static String join(final String[] parts, final int from, final int to) {
        return from >= to ? "" : String.join("/", Arrays.copyOfRange(parts, from, to));
    }

    private static String label(final Location location) {
        if (location.m_type == Type.IMAGE || location.m_type == Type.SOUND) {
            return MEDIA_FIELDS.getOrDefault(location.m_field, location.m_field);
        }
        if (location.m_type == Type.MODULE) {
            return "module version";
        }
        if (location.m_type == Type.FILE) {
            return "file";
        }
        if (location.m_field.isEmpty()) {
            return "";
        }
        return BookFilePaths.fieldName(location.m_field);
    }

    private static boolean isNoise(final String path, final Location location) {
        if (location.m_type == Type.IMAGE || location.m_type == Type.SOUND || location.m_type == Type.FILE
                || location.m_type == Type.MODULE) {
            return false;
        }
        return BookMergeRules.isCoords(path) || BookMergeRules.isList(path) || BookMergeRules.isNlbVersion(path);
    }

    private static String lastSegment(final String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Папка книги, в которой лежит элемент: «» — главная, «pages/&lt;id&gt;/module/» — внутренний
     * модуль (ссылки элемента — на страницы и объекты этой книги)
     */
    static String bookRoot(final String key) {
        final int module = key.lastIndexOf("/module/");
        return module < 0 ? "" : key.substring(0, module + "/module/".length());
    }

    /**
     * Значение поля-ссылки именем элемента: «Page "Pantry"», «Object "bell"» (root — папка книги
     * элемента); не нашёлся (или это не id) — как есть
     */
    static String reference(final NonLinearBook book, final String root, final String value,
                            final BookFilePaths.Names names) {
        if (value == null) {
            return null;
        }
        final String id = value.trim();
        if (id.isEmpty() || id.contains("\n") || id.contains(" ")) {
            return value;
        }
        for (String dir : new String[]{root + "pages/" + id, root + "objs/" + id}) {
            if (BookFilePaths.inBook(book, dir)) {
                return BookFilePaths.describe(book, dir, names);
            }
        }
        for (String dir : new String[]{root + "pages/" + id, root + "objs/" + id}) {
            final String name = names.name(dir);
            if (name != null && !name.trim().isEmpty()) {
                return BookFilePaths.describe(book, dir, names);
            }
        }
        return value;
    }

    /**
     * Где лежит объект (id контейнера): в книге, иначе в изменениях, иначе в версии
     */
    private static String container(final NonLinearBook book, final String key, final List<BookFileChange> files,
                                    final VersionTexts texts) {
        final String id = lastSegment(key);
        if (book != null && key.equals("objs/" + id) && book.getObjById(id) != null) {
            return book.getObjById(id).getContainerId();
        }
        for (BookFileChange file : files) {
            if (file.getPath().equals(key + "/containerid")) {
                return file.getAfter() != null ? file.getAfter() : file.getBefore();
            }
        }
        if (texts != null) {
            for (boolean previous : new boolean[]{false, true}) {
                final String container = texts.texts(key, previous).get("containerid");
                if (container != null) {
                    return container;
                }
            }
        }
        return null;
    }

    private static String title(final NonLinearBook book, final Location location, final BookFilePaths.Names names) {
        switch (location.m_type) {
            case BOOK:
                if (location.m_key.isEmpty()) {
                    return "Book settings";
                }
                // настройки внутреннего модуля: «Module "Tower" ->» + «book settings»
                return BookFilePaths.describe(book, location.m_key, names) + " book settings";
            case IMAGE:
            case SOUND: {
                final int slash = location.m_key.lastIndexOf('/');
                final String prefix = moduleTitle(book, location.m_key, names);
                return prefix + (location.m_type == Type.IMAGE ? "Image" : "Sound") + " \""
                        + location.m_key.substring(slash + 1) + "\"";
            }
            case FILE:
                return "File \"" + location.m_key + "\"";
            default:
                return BookFilePaths.describe(book, location.m_key, names);
        }
    }

    /**
     * «Module "Tower" -> » для медиа внутреннего модуля; иначе пусто
     */
    private static String moduleTitle(final NonLinearBook book, final String key, final BookFilePaths.Names names) {
        final int module = key.lastIndexOf("/module/");
        if (module < 0) {
            return "";
        }
        return BookFilePaths.describe(book, key.substring(0, module + "/module".length()), names) + " ";
    }

    /**
     * Поля, из которых берётся имя элемента, по порядку
     */
    private static List<String> nameFields(final String elementDir) {
        final String[] parts = elementDir.split("/");
        final String type = parts.length >= 2 ? parts[parts.length - 2] : "";
        switch (type) {
            case "pages":
                // страница-модуль часто без заголовка и текста — по имени модуля
                return Arrays.asList("caption", "text", "modname");
            case "objs":
                // у спрайта VN имени нет — его картинка
                return Arrays.asList("name", "disp", "image", "text");
            case "links":
                return Collections.singletonList("text");
            case "vars":
                return Collections.singletonList("name");
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Имя элемента, которого нет в книге: из изменений версии («стало», затем «было»), затем из
     * файлов версии и предыдущей версии. Поле на нескольких языках — сначала язык книги
     */
    static String name(final String elementDir, final List<BookFileChange> files, final VersionTexts texts,
                       final NonLinearBook book) {
        final String language = book == null ? null : book.getLanguage();
        for (String field : nameFields(elementDir)) {
            final Map<String, String> found = new LinkedHashMap<>();
            final String prefix = elementDir + "/" + field;
            for (BookFileChange file : files) {
                final String path = file.getPath();
                if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                    final String text = file.getAfter() != null && !file.getAfter().trim().isEmpty()
                            ? file.getAfter() : file.getBefore();
                    if (text != null) {
                        found.put(path.substring(elementDir.length() + 1), text);
                    }
                }
            }
            String name = pick(found, field, language);
            if (name == null && texts != null) {
                for (boolean previous : new boolean[]{false, true}) {
                    final Map<String, String> version = new LinkedHashMap<>();
                    if (!texts.exists(elementDir, previous)) {
                        continue;  // папки в этой версии нет — читать нечего (список папок закеширован)
                    }
                    for (Map.Entry<String, String> entry : texts.texts(elementDir, previous).entrySet()) {
                        if (entry.getKey().equals(field) || entry.getKey().startsWith(field + "/")) {
                            version.put(entry.getKey(), entry.getValue());
                        }
                    }
                    name = pick(version, field, language);
                    if (name != null) {
                        break;
                    }
                }
            }
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    /**
     * Тексты файлов в папке dirPath книги на диске ({путь от папки: текст}); нет папки — пусто.
     * Как у версии в git: файлы больше 256 КБ и двоичные (с нулевым байтом) пропускаются
     */
    public static Map<String, String> readTexts(final java.io.File rootDir, final String dirPath) {
        final Map<String, String> result = new LinkedHashMap<>();
        if (rootDir == null) {
            return result;
        }
        final java.nio.file.Path dir = rootDir.toPath().resolve(dirPath);
        if (!java.nio.file.Files.isDirectory(dir)) {
            return result;
        }
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(dir)) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.sorted()::iterator) {
                if (!java.nio.file.Files.isRegularFile(path) || java.nio.file.Files.size(path) > 256 * 1024) {
                    continue;
                }
                final byte[] bytes = java.nio.file.Files.readAllBytes(path);
                boolean binary = false;
                for (byte b : bytes) {
                    binary |= b == 0;
                }
                if (!binary) {
                    result.put(dir.relativize(path).toString().replace(java.io.File.separatorChar, '/'),
                            new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (java.io.IOException e) {
            return result;
        }
        return result;
    }

    private static String pick(final Map<String, String> found, final String field, final String language) {
        if (language != null) {
            final String text = found.get(field + "/" + language);
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        for (String text : found.values()) {
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        return null;
    }
}
