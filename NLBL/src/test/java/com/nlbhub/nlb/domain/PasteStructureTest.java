package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.Variable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Копирование и вставка сохраняют структуру книги: вложенность объектов (#4), страницу возврата
 * во вставленном модуле (#3), ограничение вложенного модуля (#1), положение подписей ссылок (#2),
 * флажок «autowire» (#75)
 */
public class PasteStructureTest {

    private static Set<String> ids(Collection<? extends Object> items) {
        final Set<String> result = new HashSet<>();
        for (Object item : items) {
            result.add(item instanceof Page ? ((Page) item).getId() : ((Obj) item).getId());
        }
        return result;
    }

    private static List<PageImpl> pagesOf(NonLinearBookImpl nlb) {
        final List<PageImpl> result = new ArrayList<>();
        for (String id : nlb.getPages().keySet()) {
            result.add(nlb.getPageImplById(id));
        }
        return result;
    }

    /** Вставляет буфер в книгу и возвращает страницы, которых до вставки не было */
    private static List<PageImpl> pasteNewPages(NonLinearBookImpl target) {
        final Set<String> before = new HashSet<>(target.getPages().keySet());
        target.createPasteCommand(Clipboard.singleton().getNonLinearBook()).execute();
        final List<PageImpl> result = new ArrayList<>();
        for (String id : target.getPages().keySet()) {
            if (!before.contains(id)) {
                result.add(target.getPageImplById(id));
            }
        }
        return result;
    }

    private static PageImpl byText(List<PageImpl> pages, String text) {
        for (PageImpl page : pages) {
            if (text.equals(page.getText())) {
                return page;
            }
        }
        final List<String> texts = new ArrayList<>();
        for (PageImpl page : pages) {
            texts.add(page.getText());
        }
        throw new AssertionError("нет страницы «" + text + "» среди " + texts);
    }

    private static PageImpl page(NonLinearBookImpl nlb, String text) {
        final PageImpl page = new PageImpl(nlb);
        page.setText(text);
        nlb.addPage(page);
        return page;
    }

    private static ObjImpl obj(NonLinearBookImpl nlb, String name) {
        final ObjImpl obj = new ObjImpl(nlb);
        obj.setName(name);
        nlb.addObj(obj);
        return obj;
    }

    private static void contain(AbstractNodeItem container, ObjImpl obj) {
        container.addContainedObjId(obj.getId());
        obj.setContainerId(container.getId());
    }

    private static ObjImpl copyOf(NonLinearBookImpl nlb, ObjImpl original) {
        for (Obj obj : nlb.getObjs().values()) {
            if (original.getName().equals(obj.getName()) && !obj.getId().equals(original.getId())) {
                return nlb.getObjImplById(obj.getId());
            }
        }
        throw new AssertionError("нет копии объекта " + original.getName());
    }

    @Test
    public void containmentIsPreserved() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl room = page(nlb, "комната");
        final ObjImpl box = obj(nlb, "box");
        final ObjImpl coin = obj(nlb, "coin");
        final ObjImpl key = obj(nlb, "key");
        contain(room, box);
        contain(box, coin);
        contain(box, key);

        nlb.createCopyCommand(List.of(room.getId()), List.of(box.getId(), coin.getId(), key.getId())).execute();
        final PageImpl roomCopy = byText(pasteNewPages(nlb), "комната");
        final ObjImpl boxCopy = copyOf(nlb, box);
        final ObjImpl coinCopy = copyOf(nlb, coin);
        final ObjImpl keyCopy = copyOf(nlb, key);

        assertEquals("ящик лежит в копии комнаты", roomCopy.getId(), boxCopy.getContainerId());
        assertEquals(List.of(boxCopy.getId()), roomCopy.getContainedObjIds());
        assertEquals("монета и ключ — в копии ящика, в том же порядке",
                List.of(coinCopy.getId(), keyCopy.getId()), boxCopy.getContainedObjIds());
        assertEquals(boxCopy.getId(), coinCopy.getContainerId());
        assertEquals("оригинал не тронут", List.of(coin.getId(), key.getId()), box.getContainedObjIds());
    }

    @Test
    public void containmentInUncopiedContainerIsDropped() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl room = page(nlb, "комната");
        final ObjImpl coin = obj(nlb, "coin");
        contain(room, coin);

        nlb.createCopyCommand(Collections.emptyList(), List.of(coin.getId())).execute();
        nlb.createPasteCommand(Clipboard.singleton().getNonLinearBook()).execute();
        final ObjImpl coinCopy = copyOf(nlb, coin);
        assertEquals("контейнер не копировали — копия ни в чём не лежит", "", coinCopy.getContainerId());
        assertEquals("в комнате только оригинал", List.of(coin.getId()), room.getContainedObjIds());
    }

    @Test
    public void returnPageIdIsTranslated() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl entry = page(nlb, "вход в модуль");
        final PageImpl exit = page(nlb, "после модуля");
        final PageImpl outside = page(nlb, "вне копии");
        final NonLinearBookImpl module = entry.getModuleImpl();
        final PageImpl inner = page(module, "внутри");
        inner.setReturnPageId(exit.getId());
        final PageImpl inner2 = page(module, "внутри 2");
        inner2.setReturnPageId(outside.getId());

        nlb.createCopyCommand(List.of(entry.getId(), exit.getId()), Collections.emptyList()).execute();
        final List<PageImpl> pasted = pasteNewPages(nlb);
        final PageImpl entryCopy = byText(pasted, "вход в модуль");
        final PageImpl exitCopy = byText(pasted, "после модуля");
        final List<PageImpl> innerCopies = pagesOf(entryCopy.getModuleImpl());
        assertEquals(exitCopy.getId(), byText(innerCopies, "внутри").getReturnPageId());
        assertEquals("страница возврата вне копии остаётся прежней",
                outside.getId(), byText(innerCopies, "внутри 2").getReturnPageId());
        assertEquals("оригинал не тронут", exit.getId(), inner.getReturnPageId());
    }

    @Test
    public void nestedModuleConstraintIsCopied() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl module1Page = page(nlb, "модуль 1");
        final NonLinearBookImpl module1 = module1Page.getModuleImpl();
        final PageImpl module2Page = page(module1, "модуль 2");
        final NonLinearBookImpl module2 = module2Page.getModuleImpl();
        final PageImpl module3Page = page(module2, "модуль 3");
        page(module3Page.getModuleImpl(), "лист");
        final VariableImpl constraint = new VariableImpl(module2, Variable.Type.MODCONSTRAINT,
                Variable.DataType.BOOLEAN, Variable.DEFAULT_NAME, "flag == 1", module3Page.getFullId());
        module2.addVariable(constraint);
        module3Page.setModuleConstrId(constraint.getId());
        final VariableImpl constraint2 = new VariableImpl(module1, Variable.Type.MODCONSTRAINT,
                Variable.DataType.BOOLEAN, Variable.DEFAULT_NAME, "flag == 2", module2Page.getFullId());
        module1.addVariable(constraint2);
        module2Page.setModuleConstrId(constraint2.getId());

        nlb.createCopyCommand(List.of(module1Page.getId()), Collections.emptyList()).execute();
        final PageImpl module1Copy = byText(pasteNewPages(nlb), "модуль 1");
        final PageImpl module2Copy = byText(pagesOf(module1Copy.getModuleImpl()), "модуль 2");
        final PageImpl module3Copy = byText(pagesOf(module2Copy.getModuleImpl()), "модуль 3");
        final Variable copied2 = module1Copy.getModuleImpl().getVariableById(module2Copy.getModuleConstrId());
        assertNotNull("ограничение модуля 2 есть", copied2);
        assertEquals("flag == 2", copied2.getValue());
        final Variable copied3 = module2Copy.getModuleImpl().getVariableById(module3Copy.getModuleConstrId());
        assertNotNull("ограничение модуля 3 есть", copied3);
        assertEquals("flag == 1", copied3.getValue());
        assertEquals(1, module3Copy.getModuleImpl().getPages().size());
    }

    @Test
    public void linkTextPositionIsPreserved() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl from = page(nlb, "откуда");
        final PageImpl to = page(nlb, "куда");
        final LinkImpl link = new LinkImpl(from, to.getId());
        link.getCoords().setLeft(17);
        link.getCoords().setTop(-23);
        link.getCoords().setWidth(120);
        link.getCoords().setHeight(30);
        from.addLink(link);

        nlb.createCopyCommand(List.of(from.getId(), to.getId()), Collections.emptyList()).execute();
        final PageImpl fromCopy = byText(pasteNewPages(nlb), "откуда");
        final Link linkCopy = fromCopy.getLinks().get(0);
        assertNotEquals(link.getId(), linkCopy.getId());
        assertEquals(17, linkCopy.getCoords().getLeft(), 0);
        assertEquals(-23, linkCopy.getCoords().getTop(), 0);
        assertEquals(120, linkCopy.getCoords().getWidth(), 0);
        assertEquals(30, linkCopy.getCoords().getHeight(), 0);
    }

    @Test
    public void autowireSurvivesCutAndPasteToAnotherModule() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl auto = page(nlb, "автопереход");
        nlb.addAutowiredPageId(auto.getId());
        final PageImpl modulePage = page(nlb, "модуль");

        nlb.createCopyCommand(List.of(auto.getId()), Collections.emptyList()).execute();
        final NonLinearBookImpl module = modulePage.getModuleImpl();
        final PageImpl copy = byText(pasteNewPages(module), "автопереход");
        assertTrue("флажок autowire сохранён", copy.isAutowire());
        assertTrue(module.isAutowired(copy.getId()));
    }

    @Test
    public void undoAndRedoOfPasteKeepStructure() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl room = page(nlb, "комната");
        final ObjImpl box = obj(nlb, "box");
        final ObjImpl coin = obj(nlb, "coin");
        contain(room, box);
        contain(box, coin);
        nlb.addAutowiredPageId(room.getId());

        nlb.createCopyCommand(List.of(room.getId()), List.of(box.getId(), coin.getId())).execute();
        final NonLinearBookImpl.PasteCommand paste = nlb.createPasteCommand(Clipboard.singleton().getNonLinearBook());
        paste.execute();
        final ObjImpl boxCopy = copyOf(nlb, box);
        paste.revert();
        assertTrue("копии удалены", boxCopy.isDeleted());
        assertEquals("оригиналы на месте", List.of(box.getId()), room.getContainedObjIds());
        assertEquals(List.of(coin.getId()), box.getContainedObjIds());
        assertEquals("в списке autowire только оригинал", List.of(room.getId()), nlb.getAutowiredPagesIds());
        paste.execute();
        assertEquals(1, boxCopy.getContainedObjIds().size());
        assertTrue(!boxCopy.isDeleted());
    }
}
