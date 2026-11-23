package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Удалённый объект убирается и с диска, и из модели книги при сохранении: книга больше не держит
 * его в памяти (раньше removeDeletedObjs чистил m_pages вместо m_objs, и объект оставался в модели
 * после save — повторное открытие книги его уже не показывало, а текущая сессия редактора
 * продолжала считать его частью книги).
 */
public class DeletedObjLeavesModelTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @Test
    public void deletedObjLeavesBookModelAfterSave() throws Exception {
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        final NonLinearBookImpl nlb = (NonLinearBookImpl) facade.getNlb();
        final PageImpl page = new PageImpl(nlb);
        page.setTexts(texts(nlb, "Страница"));
        nlb.addPage(page);
        nlb.setStartPoint(page.getId());
        final ObjImpl kept = obj(nlb, page, "Kept");
        final ObjImpl deleted = obj(nlb, page, "Deleted");

        final File book = m_temp.newFolder("book");
        facade.saveAs(book, new DummyProgressData());
        assertTrue("объект сохранён", new File(book, "objs/" + deleted.getId()).isDirectory());

        facade.deleteNode(deleted);
        facade.save(false, new DummyProgressData());

        assertNull("удалённый объект остался в модели книги", nlb.getObjImplById(deleted.getId()));
        assertFalse("каталог удалённого объекта остался на диске",
                new File(book, "objs/" + deleted.getId()).exists());
        assertNotNull("оставшийся объект не тронут", nlb.getObjImplById(kept.getId()));
        assertTrue("каталог оставшегося объекта на месте",
                new File(book, "objs/" + kept.getId()).isDirectory());
    }

    private static ObjImpl obj(final NonLinearBookImpl nlb, final PageImpl page, final String name) {
        final ObjImpl obj = new ObjImpl(nlb);
        obj.setName(name);
        obj.setTexts(texts(nlb, name + " description"));
        obj.setDisps(texts(nlb, name));
        obj.setActTexts(texts(nlb, ""));
        obj.setNouseTexts(texts(nlb, ""));
        obj.setContainerId(page.getId());
        nlb.addObj(obj);
        page.addContainedObjId(obj.getId());
        return obj;
    }

    private static MultiLangString texts(final NonLinearBookImpl nlb, final String text) {
        final MultiLangString result = MultiLangString.createEmptyText();
        result.put(nlb.getLanguage(), text);
        return result;
    }
}
