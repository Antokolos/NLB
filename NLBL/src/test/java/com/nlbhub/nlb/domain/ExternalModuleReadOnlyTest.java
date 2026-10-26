package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.player.ReferenceDataTest;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Вкладка внешнего модуля показывает копию модуля, сделанную при загрузке книги-родителя:
 * правки в ней не сохранялись бы. Поэтому фасад такой вкладки — только для чтения: правка не
 * меняет модель, автору — сообщение; вкладки внутренних модулей правятся как раньше. Книга —
 * «Фронтир» (NLB_TEST_BOOKS, с внешними модулями); нет книги — пропуск.
 */
public class ExternalModuleReadOnlyTest {
    @After
    public void tearDown() {
        NonLinearBookFacade.setRefusedChangeNotifier(reason -> { });
    }

    @Test
    public void externalModuleTabIsReadOnly() throws Exception {
        File book = new File(ReferenceDataTest.booksDir(), "Frontier");
        Assume.assumeTrue("Нет книги " + book, new File(book, "modules/frontierwarriors/startpoint").isFile());
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        NonLinearBookFacade facade = new NonLinearBookFacade(new Author("test", ""), new DummyVCSAdapter());
        facade.createNewBook();
        facade.load(book.getPath(), new DummyProgressData());
        assertNull(facade.getReadOnlyReason());

        List<String> refused = new ArrayList<>();
        NonLinearBookFacade.setRefusedChangeNotifier(refused::add);
        Page external = null;
        Page internal = null;
        for (Page page : facade.getNlb().getPages().values()) {
            if (!page.getModule().isEmpty()) {
                if (page.isModuleExternal()) {
                    external = page;
                } else {
                    internal = page;
                }
            }
        }
        assertNotNull(external);
        assertNotNull(internal);

        NonLinearBookFacade module = facade.createModuleFacade(external.getId());
        String reason = module.getReadOnlyReason();
        assertNotNull(reason);
        assertTrue(reason, reason.contains("modules/" + external.getModuleName()));
        String start = module.getNlb().getStartPoint();
        Page other = null;
        for (Page page : module.getNlb().getPages().values()) {
            if (!page.getId().equals(start)) {
                other = page;
            }
        }
        assertNotNull(other);
        module.changeStartPoint(other.getId());
        module.deleteNode(module.getNlb().getPageById(start));
        assertEquals("правка не принята", start, module.getNlb().getStartPoint());
        assertFalse(module.getNlb().getPageById(start).isDeleted());
        assertEquals(2, refused.size());
        assertFalse(module.canUndo());

        // вкладка внутреннего модуля правится как раньше
        NonLinearBookFacade inner = facade.createModuleFacade(internal.getId());
        assertNull(inner.getReadOnlyReason());
        String innerStart = inner.getNlb().getStartPoint();
        for (Page page : inner.getNlb().getPages().values()) {
            if (!page.getId().equals(innerStart)) {
                inner.changeStartPoint(page.getId());
                assertEquals(page.getId(), inner.getNlb().getStartPoint());
                break;
            }
        }
        assertEquals(2, refused.size());
    }
}
