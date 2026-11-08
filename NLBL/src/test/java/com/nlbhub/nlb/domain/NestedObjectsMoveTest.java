package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.SearchResultTableModel;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #174: C содержит A, A содержит B. Объект нельзя положить в то, что лежит в нём (кольцо
 * вложенности), перетаскивание сдвигает каждый вложенный объект ровно один раз, кольцо из
 * старой книги находит проверка книги и не зацикливает обходы
 */
public class NestedObjectsMoveTest {
    private NonLinearBookFacade m_facade;
    private NonLinearBookImpl m_nlb;
    private ObjImpl m_a;
    private ObjImpl m_b;
    private ObjImpl m_c;

    private ObjImpl obj(String name, float left, float top) {
        final ObjImpl obj = new ObjImpl(m_nlb, left, top);
        obj.setName(name);
        m_nlb.addObj(obj);
        return obj;
    }

    private static void contain(ObjImpl container, ObjImpl obj) {
        container.addContainedObjId(obj.getId());
        obj.setContainerId(container.getId());
    }

    @Before
    public void setUp() throws Exception {
        m_facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        m_facade.createNewBook();
        m_nlb = (NonLinearBookImpl) m_facade.getNlb();
        m_c = obj("C", 0, 0);
        m_a = obj("A", 10, 10);
        m_b = obj("B", 20, 20);
        contain(m_c, m_a);
        contain(m_a, m_b);
    }

    @Test
    public void objCannotBePutIntoItsDescendant() {
        m_facade.changeContainer(m_c.getContainerId(), m_b.getId(), m_c.getId());
        assertEquals("C не кладётся во внука B", "", m_c.getContainerId());
        assertTrue(m_b.getContainedObjIds().isEmpty());
        m_facade.changeContainer(m_c.getContainerId(), m_a.getId(), m_c.getId());
        assertEquals("и в ребёнка A", "", m_c.getContainerId());
        m_facade.changeContainer(m_c.getContainerId(), m_c.getId(), m_c.getId());
        assertEquals("и в себя", "", m_c.getContainerId());
        assertTrue(m_nlb.getObjsInContainerLoop().isEmpty());
    }

    @Test
    public void objCanStillBeMovedBetweenContainers() {
        final ObjImpl d = obj("D", 100, 100);
        m_facade.changeContainer(m_b.getContainerId(), d.getId(), m_b.getId());
        assertEquals(d.getId(), m_b.getContainerId());
        assertTrue(m_a.getContainedObjIds().isEmpty());
        m_facade.changeContainer(d.getContainerId(), m_c.getId(), d.getId());
        assertEquals("D (с B) — в C", m_c.getId(), d.getContainerId());
    }

    @Test
    public void draggingContainerMovesEachNestedObjectOnce() {
        m_facade.updateNodeCoords(m_c, Collections.emptySet(), 5, 7);
        assertEquals(5, m_c.getCoords().getLeft(), 0);
        assertEquals(15, m_a.getCoords().getLeft(), 0);
        assertEquals("внук сдвинут один раз, а не дважды", 25, m_b.getCoords().getLeft(), 0);
        assertEquals(27, m_b.getCoords().getTop(), 0);
        m_facade.undo();
        assertEquals("отмена возвращает внука на место", 20, m_b.getCoords().getLeft(), 0);
        assertEquals(10, m_a.getCoords().getLeft(), 0);
    }

    @Test
    public void draggingObjWithSelectedContainerMovesEachOnce() {
        // Тащим A, его контейнер C тоже выделен
        m_facade.updateNodeCoords(m_a, Set.of(m_c), 5, 0);
        assertEquals(5, m_c.getCoords().getLeft(), 0);
        assertEquals(15, m_a.getCoords().getLeft(), 0);
        assertEquals(25, m_b.getCoords().getLeft(), 0);
    }

    @Test
    public void containerLoopFromOldBookIsReportedAndDoesNotHang() throws Exception {
        // Кольцо, которое мог оставить старый редактор: C лежит в B
        contain(m_b, m_c);
        final List<ObjImpl> loop = m_nlb.getObjsInContainerLoop();
        assertEquals(3, loop.size());
        assertFalse(m_nlb.isObjContainedIn(m_a.getId(), "нет такого"));
        m_facade.updateNodeCoords(m_c, Collections.emptySet(), 1, 0);
        assertEquals(21, m_b.getCoords().getLeft(), 0);
        final SearchResultTableModel check = m_nlb.checkBook("");
        int loops = 0;
        for (int row = 0; row < check.getRowCount(); row++) {
            for (int col = 0; col < check.getColumnCount(); col++) {
                final Object value = check.getValueAt(row, col);
                if (value != null && value.toString().startsWith("Container loop")) {
                    loops++;
                }
            }
        }
        assertEquals("проверка книги называет все три объекта кольца", 3, loops);
        // Разорвать кольцо можно: вынуть C из B
        m_facade.changeContainer(m_b.getId(), null, m_c.getId());
        assertTrue(m_nlb.getObjsInContainerLoop().isEmpty());
    }
}
