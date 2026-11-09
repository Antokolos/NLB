package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.PageImpl;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Экспорт в AXMA Story Maker (.sm): текст книги не превращается в разметку и комментарии,
 * переменные выводятся макросом print, название и автор — в служебных абзацах StoryTitle и
 * StoryAuthor (формат — docs/export-formats.md)
 */
public class AsmExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private static void setField(NonLinearBookImpl nlb, String name, String value) throws Exception {
        Field field = NonLinearBookImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(nlb, value);
    }

    private String export(NonLinearBookImpl nlb) throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        nlb.exportToASMFile(file);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    public void storyTitleAndAuthor() throws Exception {
        TestBook b = new TestBook();
        b.page("Начало.");
        setField(b.nlb, "m_title", "Слуга Ра");
        setField(b.nlb, "m_author", "Антон Колосов");
        String sm = export(b.nlb);
        assertTrue(sm, sm.startsWith("128[::]0[::]0\n"));
        assertTrue(sm, sm.contains(":: StoryTitle[::]10-10-0\nСлуга Ра\n"));
        assertTrue(sm, sm.contains(":: StoryAuthor[::]10-10-0\nАнтон Колосов\n"));
    }

    @Test
    public void noSystemPassagesWithoutTitle() throws Exception {
        TestBook b = new TestBook();
        b.page("Начало.");
        String sm = export(b.nlb);
        assertFalse(sm, sm.contains("StoryTitle"));
        assertFalse(sm, sm.contains("StoryAuthor"));
    }

    @Test
    public void variablesArePrinted() throws Exception {
        TestBook b = new TestBook();
        PageImpl page = b.page("У вас $gold$ монет.");
        PageImpl next = b.page("Дальше.");
        b.link(page, next, "Купить за $price$");
        String sm = export(b.nlb);
        assertTrue(sm, sm.contains("У вас <<print $gold>> монет."));
        assertFalse(sm, sm.contains("$gold$"));
        assertTrue("в ссылке макросы AXMA не работают: " + sm, sm.contains("[[Купить за $price$|"));
    }

    @Test
    public void markupIsEscaped() throws Exception {
        TestBook b = new TestBook();
        PageImpl page = b.page(String.join("\n",
                "# не комментарий",
                "* не список",
                "* * *",
                "----",
                "''кавычки'', http://адрес, __подчёркивание__, ~~ ^^ === @@@ %%% {{{ }}}",
                "[[не ссылка]] и <<не макрос>>",
                "Одиночные ' / _ = -- остаются"));
        PageImpl next = b.page("Дальше.");
        b.link(page, next, "*Звезда");
        String sm = export(b.nlb);
        assertTrue(sm, sm.contains("\n&#35; не комментарий\n"));
        assertTrue(sm, sm.contains("\n&#42; не список\n"));
        assertTrue(sm, sm.contains("\n&#42; * *\n"));
        assertTrue(sm, sm.contains("\n&#45;&#45;&#45;&#45;\n"));
        assertTrue(sm, sm.contains("&#39;&#39;кавычки&#39;&#39;, http:&#47;&#47;адрес, "
                + "&#95;&#95;подчёркивание&#95;&#95;, &#126;&#126; &#94;&#94; &#61;&#61;&#61; "
                + "&#64;&#64;&#64; &#37;&#37;&#37; &#123;&#123;&#123; &#125;&#125;&#125;"));
        assertTrue(sm, sm.contains("&#91;&#91;не ссылка&#93;&#93; и &#60;&#60;не макрос&#62;&#62;"));
        assertTrue(sm, sm.contains("Одиночные ' / _ = -- остаются"));
        assertTrue("знак вида ссылки: " + sm, sm.contains("[[&#42;Звезда|"));
    }
}
