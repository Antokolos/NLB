package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.vcs.BookFileChange;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Изменения версии по элементам книги (синтетические изменения файлов, без git)
 */
public class BookHistoryTest {
    private static BookFileChange added(String path, String text) {
        return new BookFileChange(path, BookFileChange.Kind.ADDED, null, text, false, false, null, "a");
    }

    private static BookFileChange deleted(String path, String text) {
        return new BookFileChange(path, BookFileChange.Kind.DELETED, text, null, false, false, "b", null);
    }

    private static BookFileChange modified(String path, String before, String after) {
        return new BookFileChange(path, BookFileChange.Kind.MODIFIED, before, after, false, false, "b", "a");
    }

    private static BookFileChange binary(String path, BookFileChange.Kind kind) {
        return new BookFileChange(path, kind, null, null, true, false, "b", "a");
    }

    private static List<String> titles(List<BookHistory.ElementChange> elements) {
        return elements.stream().map(BookHistory.ElementChange::getTitle).collect(Collectors.toList());
    }

    @Test
    public void locate() {
        assertLocation("pages/p1/text/en", "pages/p1", "text/en", BookHistory.Type.PAGE);
        assertLocation("pages/p1/links/L1/text/en", "pages/p1/links/L1", "text/en", BookHistory.Type.LINK);
        assertLocation("pages/p1/links/L1/modifications/M1/type", "pages/p1/links/L1/modifications/M1", "type",
                BookHistory.Type.MODIFICATION);
        assertLocation("objs/o1/name", "objs/o1", "name", BookHistory.Type.OBJECT);
        assertLocation("vars/v1/value", "vars/v1", "value", BookHistory.Type.VARIABLE);
        assertLocation("images/doctor.png", "images/doctor.png", "", BookHistory.Type.IMAGE);
        assertLocation("images/doctor.png.preset", "images/doctor.png", ".preset", BookHistory.Type.IMAGE);
        assertLocation("sound/step.ogg.flag", "sound/step.ogg", ".flag", BookHistory.Type.SOUND);
        assertLocation("modules/forest", "modules/forest", "", BookHistory.Type.MODULE);
        assertLocation("title", "", "title", BookHistory.Type.BOOK);
        // внутренний модуль — книга внутри страницы
        assertLocation("pages/p1/module/pages/q1/text/en", "pages/p1/module/pages/q1", "text/en", BookHistory.Type.PAGE);
        assertLocation("pages/p1/module/startpoint", "pages/p1/module", "startpoint", BookHistory.Type.BOOK);
        assertLocation("pages/p1/module/images/x.png", "pages/p1/module/images/x.png", "", BookHistory.Type.IMAGE);
        assertLocation("misc/readme.txt", "misc/readme.txt", "", BookHistory.Type.FILE);
    }

    private static void assertLocation(String path, String key, String field, BookHistory.Type type) {
        final BookHistory.Location location = BookHistory.locate(path);
        assertEquals(path, key, location.m_key);
        assertEquals(path, field, location.m_field);
        assertEquals(path, type, location.m_type);
    }

    @Test
    public void elementsWithFieldsAndKinds() {
        final List<BookFileChange> files = Arrays.asList(
                // новая страница: имя — из её заголовка в изменениях
                added("pages/p3/caption/en", "Cellar"),
                added("pages/p3/text/en", "Dark and damp."),
                added("pages/p3/coords/left", "10.0"),
                // правка текста и перемещение страницы p1
                modified("pages/p1/text/en", "The shelves are empty.", "Only a bell."),
                modified("pages/p1/coords/left", "100.0", "140.0"),
                // только перемещение — шум
                modified("pages/p2/coords/left", "1.0", "2.0"),
                // картинка и её пресет
                binary("images/bell.png", BookFileChange.Kind.ADDED),
                added("images/bell.png.preset", "COMPRESSED"),
                // настройки книги
                modified("title", "Pantry", "Pantry tales"),
                modified("nlbversion", "1.1.1", "1.1.2")
        );
        final Map<String, String> captions = Map.of("pages/p1", "Pantry", "pages/p2", "Gate");
        final List<BookHistory.ElementChange> elements = BookHistory.group(null, files,
                (dir, previous) -> captions.containsKey(dir) ? Map.of("caption/en", captions.get(dir))
                        : Collections.emptyMap());
        // влияющее на игру — раньше шума; по типу, затем по заголовку
        assertEquals(Arrays.asList("Book settings", "Page \"Cellar\"", "Page \"Pantry\"", "Image \"bell.png\"",
                "Page \"Gate\""), titles(elements));
        final BookHistory.ElementChange settings = elements.get(0);
        assertEquals(BookFileChange.Kind.MODIFIED, settings.getKind());
        assertFalse(settings.isNoiseOnly());
        assertEquals(Arrays.asList("title", "NLBB version that saved the book"),
                settings.getFields().stream().map(BookHistory.FieldChange::getLabel).collect(Collectors.toList()));
        assertTrue(settings.getFields().get(1).isNoise());

        final BookHistory.ElementChange cellar = elements.get(1);
        assertEquals(BookFileChange.Kind.ADDED, cellar.getKind());
        assertEquals(Arrays.asList("caption (en)", "text (en)", "position on the scheme"),
                cellar.getFields().stream().map(BookHistory.FieldChange::getLabel).collect(Collectors.toList()));

        final BookHistory.ElementChange pantry = elements.get(2);
        assertEquals(BookFileChange.Kind.MODIFIED, pantry.getKind());
        assertEquals("text (en)", pantry.getFields().get(0).getLabel());
        assertEquals("The shelves are empty.", pantry.getFields().get(0).getBefore());
        assertEquals("Only a bell.", pantry.getFields().get(0).getAfter());
        assertTrue(pantry.getFields().get(1).isNoise());

        final BookHistory.ElementChange image = elements.get(3);
        assertEquals(BookFileChange.Kind.ADDED, image.getKind());
        assertEquals(Arrays.asList("export preset", "file"),
                image.getFields().stream().map(BookHistory.FieldChange::getLabel).collect(Collectors.toList()));
        assertTrue(image.getFields().get(1).isBinary());

        final BookHistory.ElementChange gate = elements.get(4);
        assertTrue(gate.isNoiseOnly());
    }

    @Test
    public void deletedElementsAreNamedFromTheirVersion() {
        final List<BookFileChange> files = new ArrayList<>(Arrays.asList(
                deleted("pages/p2/text/en", "A gate."),
                deleted("pages/p2/caption/en", "Gate"),
                deleted("pages/p1/links/L1/text/en", "To the gate"),
                deleted("pages/p1/links/L1/target", "p2"),
                // объект удалён: в git среди изменений все его файлы, имя — из них
                deleted("objs/o1/name", "bell"),
                deleted("objs/o1/containerid", "p2")
        ));
        final List<BookHistory.ElementChange> elements = BookHistory.group(null, files, (dir, previous) ->
                previous && dir.equals("objs/o1") ? Map.of("name", "bell") : Collections.emptyMap());
        // объект — с местом: его страница удалена тут же, её имя — из изменений
        assertEquals(Arrays.asList("Page \"Gate\"", "Link \"To the gate\" of page \"p1\"",
                "Object \"bell\" on page \"Gate\""), titles(elements));
        // ссылка на страницу в значении поля — её именем
        final BookHistory.FieldChange container = elements.get(2).getFields().stream()
                .filter(f -> f.getLabel().equals("container")).findFirst().get();
        assertEquals("Page \"Gate\"", container.getDisplayBefore());
        assertEquals("p2", container.getBefore());
        assertEquals("Page \"Gate\"", elements.get(1).getFields().stream()
                .filter(f -> f.getLabel().equals("target")).findFirst().get().getDisplayBefore());
        assertTrue(elements.stream().allMatch(e -> e.getKind() == BookFileChange.Kind.DELETED));
        // имени нет нигде — короткий id
        assertEquals("Object \"o2\"", BookHistory.group(null,
                Collections.singletonList(deleted("objs/o2/containerid", "p2")), null).get(0).getTitle());
    }

    @Test
    public void elementMissingFromTheBookIsNamedFromTheVersion() {
        // объекта уже нет в книге (удалён позже), а в этой версии у него сменилась картинка: имени
        // среди изменений нет — из самой версии (а если и там нет — из предыдущей)
        final List<BookFileChange> files = Collections.singletonList(modified("objs/o1/image", "a.png", "b.png"));
        assertEquals("Object \"bell\"", BookHistory.group(null, files, (dir, previous) ->
                previous && dir.equals("objs/o1") ? Map.of("name", "bell") : Collections.emptyMap()).get(0).getTitle());
        assertEquals("Object \"lamp\"", BookHistory.group(null, files, (dir, previous) ->
                dir.equals("objs/o1") ? Map.of("name", previous ? "bell" : "lamp") : Collections.emptyMap())
                .get(0).getTitle());
    }

    @Test
    public void newFieldOfAnOldElementIsAModification() {
        // у страницы, которая была и раньше, появилась картинка: страница изменена, а не добавлена
        final List<BookFileChange> files = Arrays.asList(
                added("pages/p1/image", "room.png"),
                // у старой картинки появился пресет: картинка изменена
                added("images/room.png.preset", "COMPRESSED"));
        final List<BookHistory.ElementChange> elements = BookHistory.group(null, files, (dir, previous) ->
                previous && dir.equals("pages/p1") ? Map.of("caption/en", "Pantry") : Collections.emptyMap());
        assertEquals(Arrays.asList("Page \"Pantry\"", "Image \"room.png\""), titles(elements));
        assertEquals(BookFileChange.Kind.MODIFIED, elements.get(0).getKind());
        assertEquals("image", elements.get(0).getFields().get(0).getLabel());
        assertEquals(BookFileChange.Kind.MODIFIED, elements.get(1).getKind());
        // поле удалено, а страница осталась — тоже изменение
        final List<BookHistory.ElementChange> removed = BookHistory.group(null,
                Collections.singletonList(deleted("pages/p1/image", "room.png")),
                (dir, previous) -> Map.of("caption/en", "Pantry"));
        assertEquals(BookFileChange.Kind.MODIFIED, removed.get(0).getKind());
    }

    @Test
    public void fieldsHaveHumanNames() {
        final List<BookHistory.ElementChange> elements = BookHistory.group(null, Arrays.asList(
                added("objs/o1/coordsor", "RightBottom"),
                added("objs/o1/offset", "0,0"),
                added("objs/o1/graphical", "true"),
                added("objs/o1/image", "mrsherrero.png")), null);
        // объект без имени (спрайт VN) — по картинке
        assertEquals("Object \"mrsherrero.png\"", elements.get(0).getTitle());
        assertEquals(Arrays.asList("\"graphical\" flag", "coordinates origin", "image", "offset on the screen"),
                elements.get(0).getFields().stream().map(BookHistory.FieldChange::getLabel).collect(Collectors.toList()));
    }

    @Test
    public void textsOfTheBookOnDisk() throws Exception {
        final java.nio.file.Path root = java.nio.file.Files.createTempDirectory("nlb-texts");
        try {
            final java.nio.file.Path caption = root.resolve("pages/p1/caption/en");
            java.nio.file.Files.createDirectories(caption.getParent());
            java.nio.file.Files.writeString(caption, "Pantry");
            java.nio.file.Files.write(root.resolve("pages/p1/blob"), new byte[]{1, 0, 2});
            assertEquals(Map.of("caption/en", "Pantry"), BookHistory.readTexts(root.toFile(), "pages/p1"));
            assertTrue(BookHistory.readTexts(root.toFile(), "pages/p2").isEmpty());
        } finally {
            com.nlbhub.nlb.vcs.TestDirs.delete(root);
        }
    }

    @Test
    public void languageOfTheBookIsPreferredForNames() {
        final List<BookFileChange> files = Arrays.asList(
                added("pages/p1/caption/en", "Pantry"),
                added("pages/p1/caption/ru", "Кладовая"));
        assertEquals("Pantry", BookHistory.name("pages/p1", files, null, null));
    }

    @Test
    public void moduleAndExternalModule() {
        final List<BookFileChange> files = Arrays.asList(
                modified("pages/p1/module/pages/q1/text/en", "Floor.", "First floor."),
                modified("pages/p1/module/startpoint", "q1", "q2"),
                new BookFileChange("modules/forest", BookFileChange.Kind.MODIFIED, null, null, false, true, "b", "a"));
        final List<BookHistory.ElementChange> elements = BookHistory.group(null, files,
                (dir, previous) -> dir.equals("pages/p1") ? Map.of("caption/en", "Tower")
                        : dir.equals("pages/p1/module/pages/q1") ? Map.of("caption/en", "Floor1") : Collections.emptyMap());
        assertEquals(Arrays.asList("Module \"Tower\" -> book settings", "Module \"Tower\" -> Page \"Floor1\"",
                "External module \"forest\""), titles(elements));
        assertEquals("module version", elements.get(2).getFields().get(0).getLabel());
    }
}
