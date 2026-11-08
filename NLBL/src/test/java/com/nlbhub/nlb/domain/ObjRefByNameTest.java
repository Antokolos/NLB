package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.SearchResultTableModel;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * #164: ссылки на объект по имени (morphover, morphout, commonTo) находят объект, даже если
 * он получил это имя позже ссылки, следуют за переименованием, а ненайденные видны в проверке книги
 */
public class ObjRefByNameTest {
    private NonLinearBookFacade m_facade;
    private NonLinearBookImpl m_nlb;

    @Before
    public void setUp() {
        m_facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        m_facade.createNewBook();
        m_nlb = (NonLinearBookImpl) m_facade.getNlb();
    }

    private Obj newObj() {
        final Obj obj = m_facade.createObj(10, 10);
        m_facade.addObj(obj);
        return m_nlb.getObjById(obj.getId());
    }

    /** Правка объекта, как OK в его диалоге: имя и ссылки, остальное — как было */
    private void edit(Obj obj, String name, String commonTo, String morphOver, String morphOut) {
        m_facade.updateObj(
                obj, "", "", "", commonTo, name,
                obj.getImageFileName(), obj.getSoundFileName(), obj.isSoundSFX(), obj.isAnimatedImage(),
                obj.isSuppressDsc(), obj.getDisps(), obj.getTexts(), obj.getActTexts(), obj.getNouseTexts(),
                obj.isGraphical(), obj.isShowOnCursor(), obj.isPreserved(), obj.isLoadOnce(), obj.isCollapsable(),
                obj.getOffset(), obj.getMovementDirection(), obj.getEffect(), obj.getStartFrame(), obj.getMaxFrame(),
                obj.getPreloadFrames(), obj.getPauseFrames(), obj.getCoordsOrigin(), obj.isClearUnderTooltip(),
                obj.isActOnKey(), obj.isCacheText(), obj.isLooped(), obj.isNoRedrawOnAct(),
                morphOver, morphOut, obj.isTakable(), obj.isCallback(), obj.isImageInScene(), obj.isImageInInventory()
        );
    }

    private Variable var(String id) {
        return m_nlb.getVariableById(id);
    }

    private int objectReferenceProblems() throws Exception {
        final SearchResultTableModel check = m_nlb.checkBook("");
        int problems = 0;
        for (int row = 0; row < check.getRowCount(); row++) {
            for (int col = 0; col < check.getColumnCount(); col++) {
                final Object value = check.getValueAt(row, col);
                if (value != null && value.toString().startsWith("Referenced object not found")) {
                    problems++;
                }
            }
        }
        return problems;
    }

    @Test
    public void referenceFindsObjectNamedLater() throws Exception {
        // шаги из #164: два объекта без имён, у первого morphover = obj2, потом второй назван obj2
        final Obj first = newObj();
        final Obj second = newObj();
        edit(first, "button", "", "obj2", "obj2out");
        assertNull("объекта obj2 ещё нет", first.getMorphOverObj());
        assertEquals("проверка книги видит обе ненайденные ссылки", 2, objectReferenceProblems());

        edit(second, "obj2", "", "", "");
        assertEquals("morphover нашёл объект, названный позже", second.getId(), first.getMorphOverObj().getId());
        assertNull("morphout с другим именем — по-прежнему нет", first.getMorphOutObj());
        assertEquals(1, objectReferenceProblems());

        // правки объекта отменяются его собственной историей (как в диалоге объекта)
        m_facade.undo(second.getId());
        assertNull("отмена переименования возвращает ссылку как было", first.getMorphOverObj());
        assertEquals("obj2", var(((ObjImpl) first).getMorphOverId()).getName());
        m_facade.redo(second.getId());
        assertEquals(second.getId(), first.getMorphOverObj().getId());
    }

    @Test
    public void referencesFollowRename() {
        final Obj common = newObj();
        edit(common, "commonObj", "", "", "");
        final Obj user = newObj();
        edit(user, "user", "commonObj", "", "");
        assertEquals(common.getId(), user.getCommonToObj(m_nlb).getId());

        edit(common, "sharedObj", "", "", "");
        final Variable commonTo = var(user.getCommonToId());
        assertEquals("имя ссылки — новое имя объекта", "sharedObj", commonTo.getName());
        assertEquals(common.getId(), user.getCommonToObj(m_nlb).getId());

        // новый объект со старым именем ссылку не перехватывает
        final Obj impostor = newObj();
        edit(impostor, "commonObj", "", "", "");
        assertEquals(common.getId(), user.getCommonToObj(m_nlb).getId());

        m_facade.undo(common.getId());    // переименование common
        assertEquals("отмена переименования", "commonObj", var(user.getCommonToId()).getName());
        assertEquals(common.getId(), user.getCommonToObj(m_nlb).getId());
    }

    @Test
    public void deletedObjectDoesNotHoldName() {
        final Obj old = newObj();
        edit(old, "door", "", "", "");
        m_facade.deleteNode(old);
        final Obj user = newObj();
        edit(user, "user", "", "door", "");
        assertNull("удалённый объект по имени не находится", user.getMorphOverObj());
        final Obj door = newObj();
        edit(door, "door", "", "", "");
        assertEquals("новый объект с этим именем подхватил ссылку", door.getId(), user.getMorphOverObj().getId());
    }
}
