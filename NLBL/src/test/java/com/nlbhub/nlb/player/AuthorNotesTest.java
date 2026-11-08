package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #187: заметки автора у страницы — сохраняются с книгой, переносятся вставкой, но не попадают в
 * игру и в экспорт INSTEAD (в отличие от заголовка, который экспорт выводит как notes локации)
 */
public class AuthorNotesTest {
    private static final String NOTES = "TODO: проверить ветку с волком\nи дописать концовку";

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private static void deleteTree(Path dir) throws Exception {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Test
    public void notesAreSavedAndLoaded() throws Exception {
        final Path dir = Files.createTempDirectory("author-notes");
        try {
            final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
            facade.createNewBook();
            final NonLinearBookImpl nlb = (NonLinearBookImpl) facade.getNlb();
            final PageImpl page = new PageImpl(nlb);
            page.setText("Текст");
            page.setAuthorNotes(NOTES);
            nlb.addPage(page);
            final PageImpl plain = new PageImpl(nlb);
            nlb.addPage(plain);
            final File book = dir.resolve("book").toFile();
            facade.saveAs(book, new DummyProgressData());

            assertEquals(NOTES, Files.readString(new File(book, "pages/" + page.getId() + "/notes").toPath(),
                    StandardCharsets.UTF_8));
            assertFalse("без заметок — без файла", new File(book, "pages/" + plain.getId() + "/notes").exists());

            final NonLinearBookImpl loaded = new NonLinearBookImpl();
            loaded.load(book.getPath(), new DummyProgressData());
            assertEquals(NOTES, loaded.getPageById(page.getId()).getAuthorNotes());
            assertEquals("", loaded.getPageById(plain.getId()).getAuthorNotes());
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void notesArePastedWithPage() throws Exception {
        // как редактор: копировать и вставить через фасад
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        final NonLinearBookImpl nlb = (NonLinearBookImpl) facade.getNlb();
        final PageImpl page = new PageImpl(nlb);
        page.setText("Оригинал");
        page.setAuthorNotes(NOTES);
        nlb.addPage(page);
        facade.copy(List.of(page.getId()), List.of());
        facade.paste();
        int withNotes = 0;
        for (Page p : nlb.getPages().values()) {
            if (NOTES.equals(p.getAuthorNotes())) {
                withNotes++;
            }
        }
        assertEquals("у копии — те же заметки", 2, withNotes);
    }

    @Test
    public void notesAreNotExportedToInstead() throws Exception {
        final TestBook book = new TestBook();
        final PageImpl start = book.page("Начало");
        start.setCaption("Заголовок локации");
        start.setAuthorNotes("СЕКРЕТНАЯ ЗАМЕТКА АВТОРА");
        final File file = File.createTempFile("nlb-export", ".lua");
        try {
            book.nlb.exportToSTEADFile(file);
            final String lua = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            assertFalse("заметки автора в экспорт не попадают", lua.contains("СЕКРЕТНАЯ ЗАМЕТКА АВТОРА"));
            assertTrue("notes локации — по-прежнему заголовок", lua.contains("notes = 'Заголовок локации'"));
        } finally {
            assertTrue(file.delete());
        }
    }

    @Test
    public void notesAreNotShownInGame() {
        final TestBook book = new TestBook();
        final PageImpl start = book.page("Начало");
        start.setAuthorNotes("СЕКРЕТНАЯ ЗАМЕТКА АВТОРА");
        final PlayerEngine engine = book.start();
        final StringBuilder screen = new StringBuilder();
        for (ScreenModel.Paragraph paragraph : engine.screen().getPageText()) {
            screen.append(paragraph.getText());
        }
        assertFalse(screen.toString().contains("СЕКРЕТНАЯ"));
    }
}
