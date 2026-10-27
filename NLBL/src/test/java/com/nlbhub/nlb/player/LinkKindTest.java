package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.config.Settings;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.util.FileManipulator;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Вид ссылки на странице STANDARD: переход ways или выбор после текста (xact). Реплики («-»,
 * кавычки) и «Далее» — всегда выборы; остальное — по виду ссылки (файл kind), а вид «авто» — по
 * настройке книги (файл choicelnk). Экспорт в INSTEAD и плеер решают одинаково
 */
public class LinkKindTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        // экспорт берёт настройки из PropertyManager, а тот — из cfg/config.xml (в NLBL его нет)
        Settings settings = PropertyManager.readSettings(new File("../NLBW/cfg/config.xml"));
        Field field = PropertyManager.class.getDeclaredField("m_settings");
        field.setAccessible(true);
        field.set(null, settings);
    }

    private static final class Book {
        final TestBook book = new TestBook();
        final PageImpl start = book.page("Старт");
        final List<LinkImpl> links = new ArrayList<>();

        Book(boolean choiceLinks) {
            book.nlb.setChoiceLinks(choiceLinks);
            add("Холл", Link.Kind.AUTO);
            add("Принять бой", Link.Kind.CHOICE);
            add("Назад", Link.Kind.WAY);
            add("- Реплика", Link.Kind.WAY);
        }

        private void add(String text, Link.Kind kind) {
            LinkImpl link = book.link(start, book.page(text + "!"), text);
            link.setKind(kind);
            links.add(link);
        }

        /**
         * Тексты вариантов плеера, которые идут после текста (не ways)
         */
        List<String> playerChoices() {
            List<String> result = new ArrayList<>();
            for (PlayerEngine.Choice choice : book.start().view().getChoices()) {
                if (choice.isInlineLink()) {
                    result.add(choice.getText());
                }
            }
            return result;
        }

        /**
         * Тексты ссылок, которые экспорт кладёт в way = {…} комнаты
         */
        List<String> exportWays() throws Exception {
            File file = File.createTempFile("nlb-export", ".lua");
            try {
                book.nlb.exportToSTEADFile(file);
                String lua = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                int way = lua.indexOf("    way = {");
                String ways = lua.substring(way, lua.indexOf("},", way));
                List<String> result = new ArrayList<>();
                for (LinkImpl link : links) {
                    if (ways.contains(link.getId().replace('-', '_'))) {
                        result.add(link.getText());
                    }
                }
                return result;
            } finally {
                file.delete();
            }
        }
    }

    @Test
    public void waysByDefault() throws Exception {
        Book book = new Book(false);
        assertEquals(Arrays.asList("Принять бой", "- Реплика"), book.playerChoices());
        assertEquals(Arrays.asList("Холл", "Назад"), book.exportWays());
    }

    @Test
    public void choicesByBookSetting() throws Exception {
        Book book = new Book(true);
        assertEquals(Arrays.asList("Холл", "Принять бой", "- Реплика"), book.playerChoices());
        assertEquals(Arrays.asList("Назад"), book.exportWays());
    }

    @Test
    public void kindIsSavedAndReadBack() throws Exception {
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт");
        File linksDir = m_temp.newFolder("links");
        FileManipulator files = new FileManipulator(new DummyVCSAdapter(), m_temp.getRoot());
        for (Link.Kind kind : Link.Kind.values()) {
            LinkImpl link = book.link(start, start, kind.name());
            link.setKind(kind);
            link.writeLink(files, linksDir);
            File kindFile = new File(new File(linksDir, link.getId()), "kind");
            // вид по умолчанию не пишется: старые книги и книги без разметки не меняются
            assertEquals(kind != Link.Kind.AUTO, kindFile.exists());
            LinkImpl read = new LinkImpl(start);
            read.readLink(new File(linksDir, link.getId()));
            assertEquals(kind, read.getKind());
        }
        // значение из будущей версии — «авто»
        LinkImpl link = book.link(start, start, "x");
        link.writeLink(files, linksDir);
        Files.write(new File(new File(linksDir, link.getId()), "kind").toPath(), "SOMETHING".getBytes(StandardCharsets.UTF_8));
        LinkImpl read = new LinkImpl(start);
        read.readLink(new File(linksDir, link.getId()));
        assertEquals(Link.Kind.AUTO, read.getKind());
        assertFalse(read.isAuto());
        assertTrue(read.getModifications().isEmpty());
    }
}
