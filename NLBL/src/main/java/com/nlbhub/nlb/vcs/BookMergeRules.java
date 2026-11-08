package com.nlbhub.nlb.vcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Механические пересечения правок при слиянии (pull), которые автору показывать незачем:
 * <ul>
 *     <li>списки порядка — lnkorder (ссылки), modorder (модификации), content (объекты внутри),
 *     autopgs (страницы с автопереходами): id по строке; оба добавили ссылку на одну страницу —
 *     обе строки дописаны в конец, git видит пересечение. Слияние списков: наш порядок без
 *     удалённого на сервере, затем добавленное на сервере (в его порядке);</li>
 *     <li>положение и размер на схеме (coords/left, top, width, height): на игру не влияют —
 *     остаётся наше;</li>
 *     <li>nlbversion (версия редактора, сохранявшего книгу, #166) — бо́льшая из двух.</li>
 * </ul>
 * И сверка после любого слияния: lnkorder и modorder должны перечислять ровно те ссылки и
 * модификации, папки которых есть (иначе книга не открывается — createSortedDirList); git
 * может слить файлы без пересечений, но рассогласованно (ссылку удалили на сервере, а у себя
 * правили её текст и оставили свою версию).
 */
public final class BookMergeRules {
    private static final Set<String> LISTS = new LinkedHashSet<>(Arrays.asList("lnkorder", "modorder", "content", "autopgs"));
    private static final String SEPARATOR = "\n";

    private BookMergeRules() {
    }

    /**
     * Файл — список id (сливается объединением)
     */
    public static boolean isList(final String path) {
        return LISTS.contains(lastSegment(path));
    }

    /**
     * Файл — версия редактора, сохранявшего книгу (сливается выбором бо́льшей)
     */
    public static boolean isNlbVersion(final String path) {
        return "nlbversion".equals(lastSegment(path));
    }

    public static String mergeNlbVersion(final String ours, final String theirs) {
        return com.nlbhub.nlb.api.NLBVersion.max(ours, theirs);
    }

    /**
     * Файл — положение или размер элемента на схеме
     */
    public static boolean isCoords(final String path) {
        final String[] parts = path.split("/");
        return parts.length >= 2 && "coords".equals(parts[parts.length - 2]);
    }

    /**
     * Слить списки id: base — общий предок (null — файла не было), ours/theirs — null, если
     * файл удалён на этой стороне (= пустой список)
     */
    public static String mergeList(final String base, final String ours, final String theirs) {
        final List<String> b = split(base);
        final List<String> o = split(ours);
        final List<String> t = split(theirs);
        final Set<String> result = new LinkedHashSet<>();
        for (String id : o) {
            // удалённое на сервере — убрать
            if (!b.contains(id) || t.contains(id)) {
                result.add(id);
            }
        }
        for (String id : t) {
            // добавленное на сервере — в конец; удалённое у нас — не возвращать
            if (!b.contains(id) || o.contains(id)) {
                result.add(id);
            }
        }
        return String.join(SEPARATOR, result);
    }

    /**
     * Порядок, сверенный с имеющимися папками: id без папки — убрать, папки без id — в конец (по
     * имени, чтобы результат не зависел от ФС)
     */
    public static List<String> reconcile(final List<String> order, final Collection<String> dirs) {
        final List<String> result = new ArrayList<>();
        for (String id : order) {
            if (dirs.contains(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        for (String dir : new TreeSet<>(dirs)) {
            if (!result.contains(dir)) {
                result.add(dir);
            }
        }
        return result;
    }

    public static List<String> split(final String list) {
        final List<String> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (String line : list.split("\r?\n")) {
            if (!line.trim().isEmpty()) {
                result.add(line.trim());
            }
        }
        return result;
    }

    public static String join(final List<String> ids) {
        return String.join(SEPARATOR, ids);
    }

    private static String lastSegment(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
