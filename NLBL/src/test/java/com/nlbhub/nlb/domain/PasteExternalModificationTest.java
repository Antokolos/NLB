package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Variable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #283: копирование и вставка объекта сохраняют флаг «external» у его модификаций (и у
 * модификаций его ссылок); обычные модификации остаются обычными
 */
public class PasteExternalModificationTest {
    private static ModificationImpl modification(NonLinearBookImpl nlb, AbstractModifyingItem item, String target,
                                                 String name, boolean external) {
        final ModificationImpl mod = new ModificationImpl(item);
        mod.setType(Modification.Type.ASSIGN.name());
        mod.setExternal(external);
        final VariableImpl var = new VariableImpl(nlb, Variable.Type.VAR, Variable.DataType.AUTO, name, null,
                target + ";" + mod.getId());
        final VariableImpl expr = new VariableImpl(nlb, Variable.Type.EXPRESSION, Variable.DataType.AUTO, null, "1",
                target + ";" + mod.getId());
        nlb.addVariable(var);
        nlb.addVariable(expr);
        mod.setVarId(var.getId());
        mod.setExprId(expr.getId());
        item.addModification(mod);
        return mod;
    }

    @Test
    public void pastedObjKeepsExternalFlags() {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();
        final PageImpl page = new PageImpl(nlb);
        nlb.addPage(page);
        final ObjImpl key = new ObjImpl(nlb);
        key.setName("key");
        nlb.addObj(key);
        final ObjImpl door = new ObjImpl(nlb);
        door.setName("door");
        nlb.addObj(door);
        modification(nlb, key, key.getId(), "ext", true);
        modification(nlb, key, key.getId(), "plain", false);
        final LinkImpl use = new LinkImpl(key, door.getId());
        key.addLink(use);
        modification(nlb, use, key.getId() + ";" + use.getId(), "linkExt", true);

        nlb.createCopyCommand(Collections.emptyList(), List.of(key.getId(), door.getId())).execute();
        nlb.createPasteCommand(Clipboard.singleton().getNonLinearBook()).execute();

        final List<Obj> copies = new ArrayList<>();
        for (Obj obj : nlb.getObjs().values()) {
            if ("key".equals(obj.getName()) && !obj.getId().equals(key.getId())) {
                copies.add(obj);
            }
        }
        assertEquals("вставлена одна копия ключа", 1, copies.size());
        final Obj copy = copies.get(0);
        assertEquals(2, copy.getModifications().size());
        for (Modification mod : copy.getModifications()) {
            final String name = nlb.getVariableById(mod.getVarId()).getName();
            if ("ext".equals(name)) {
                assertTrue("external сохранён", mod.isExternal());
            } else {
                assertFalse("обычная модификация — обычная", mod.isExternal());
            }
            assertEquals(Modification.Type.ASSIGN, mod.getType());
        }
        final Link copyUse = copy.getLinks().get(0);
        assertTrue("external модификации ссылки сохранён", copyUse.getModifications().get(0).isExternal());
    }
}
