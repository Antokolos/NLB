package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Имена объектов в модификациях — глобальные, как в INSTEAD и Java-плеере: модификация корневой
 * книги может назвать объект вложенного модуля. «Фронтир»: syncDataObj (корень) кладёт
 * cardPlaceholderObj (модуль боя, два уровня вглубь) в держатели карт. Раньше экспорт искал имя
 * только у себя и у родителей, считал его переменной ($cardPlaceholderObj в QSP), и объект никуда
 * не клался. Книга — NLB_TEST_BOOKS; нет — пропуск.
 */
public class ExportObjectNamesAcrossModulesTest {
    private static final String CARD_PLACEHOLDER = "755da6dd-ed4b-405c-bd41-2779f90913b2";

    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @Test
    public void rootModificationNamesObjectOfNestedModule() throws Exception {
        File bookDir = new File(ReferenceDataTest.booksDir(), "Frontier");
        Assume.assumeTrue("Нет книги " + bookDir, new File(bookDir, "startpoint").isFile());
        Assume.assumeFalse("Frontier — только не в Windows (долго)", File.separatorChar == '\\');
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        NonLinearBookImpl book = ConsolePlayer.loadBook(bookDir);
        assertNotNull(book);
        File file = new File(m_temp.getRoot(), "book.txt");
        book.exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE);
        assertFalse("имя объекта не стало переменной", qsp.contains("GOSUB 'nlb_add', $cardPlaceholderObj"));
        assertTrue("объект модуля — по id", qsp.contains("GOSUB 'nlb_add', '" + CARD_PLACEHOLDER + "', "));
    }
}
