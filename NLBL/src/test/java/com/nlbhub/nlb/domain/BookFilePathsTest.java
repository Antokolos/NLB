package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.player.ReferenceDataTest;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Файлы книги словами автора (сообщение о пересёкшихся правках при pull) — на bookU
 */
public class BookFilePathsTest {
    private static final String PANTRY = "pages/0098f758-2bfd-5605-bdca-705985829a50";

    @Test
    public void pathsAreNamedInBookTerms() throws Exception {
        final File bookU = new File(ReferenceDataTest.booksDir(), "bookU");
        Assume.assumeTrue("Нет книги " + bookU, new File(bookU, "startpoint").isFile());
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        facade.load(bookU.getPath(), new DummyProgressData());
        final NonLinearBook book = facade.getNlb();

        assertEquals("Page \"Pantry\": text (en)", BookFilePaths.describe(book, PANTRY + "/text/en"));
        assertEquals("Page \"Pantry\": position on the scheme", BookFilePaths.describe(book, PANTRY + "/coords/left"));
        assertEquals("Page \"Pantry\": order of links", BookFilePaths.describe(book, PANTRY + "/lnkorder"));
        assertEquals("Link \"To the gate\" of page \"Pantry\": text (en)",
                BookFilePaths.describe(book, PANTRY + "/links/593c8954-6a6a-5b15-a892-f9f599b6fb77/text/en"));
        assertEquals("Modification of page \"Pantry\": type",
                BookFilePaths.describe(book, PANTRY + "/modifications/25cc6080-8168-5abe-bb86-d863e54e2a36/type"));
        assertEquals("Object \"pear\": displayed name (en)",
                BookFilePaths.describe(book, "objs/4edde2c4-4674-5dd3-8684-198fefa71fde/disp/en"));
        assertEquals("Variable \"usedBell\": name",
                BookFilePaths.describe(book, "vars/1428e0a9-6766-5be7-8e33-812ab3632fe0/name"));
        assertEquals("Expression \"apple\": value",
                BookFilePaths.describe(book, "vars/1bc30b2d-c413-5b5d-ad87-bad1f0f88e20/value"));
        assertEquals("Module \"Tower\" -> Page \"Floor1\": text (en)", BookFilePaths.describe(book,
                "pages/ff776c3d-8988-5aef-bcb7-018351419fc6/module/pages/e2bb9196-5f14-571a-a18c-274d3cd9361b/text/en"));
        assertEquals("Book settings: title", BookFilePaths.describe(book, "title"));
        assertEquals("External module \"frontierwarriors\"", BookFilePaths.describe(book, "modules/frontierwarriors"));
        assertEquals("Page \"abcdef12\": text (ru)", BookFilePaths.describe(book, "pages/abcdef12-0000/text/ru"));
        assertEquals("File images/door.png", BookFilePaths.describe(book, "images/door.png"));
        facade.clear();
    }
}
