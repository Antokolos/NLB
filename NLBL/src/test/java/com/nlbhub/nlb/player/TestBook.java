package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.AbstractModifyingItem;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.ObjImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.domain.VariableImpl;
import com.nlbhub.nlb.util.MultiLangString;

/**
 * Книга в памяти: страницы, ссылки, переменные и модификации
 */
final class TestBook {
    final NonLinearBookImpl nlb = new NonLinearBookImpl();

    /**
     * Свой объект текста: setText у новой страницы или ссылки поменял бы общий текст по
     * умолчанию (DEFAULT_TEXT) сразу у всех
     */
    MultiLangString text(String text) {
        MultiLangString result = MultiLangString.createEmptyText();
        result.put(nlb.getLanguage(), text);
        return result;
    }

    PageImpl page(String text) {
        PageImpl page = new PageImpl(nlb);
        page.setTexts(text(text));
        nlb.addPage(page);
        if (nlb.getStartPoint() == null || nlb.getStartPoint().isEmpty()) {
            nlb.setStartPoint(page.getId());
        }
        return page;
    }

    LinkImpl link(PageImpl from, PageImpl to, String text) {
        LinkImpl link = new LinkImpl(from, to.getId());
        link.setTexts(text(text));
        from.addLink(link);
        return link;
    }

    /**
     * Объект на странице: dsc — описание (метка {…} — то, по чему щёлкают), disp — подпись в инвентаре
     */
    ObjImpl obj(PageImpl page, String name, String dsc, String disp, boolean takable) {
        ObjImpl obj = new ObjImpl(nlb);
        obj.setName(name);
        obj.setTexts(text(dsc));
        obj.setDisps(text(disp));
        obj.setActTexts(text(""));
        obj.setNouseTexts(text(""));
        obj.setTakable(takable);
        obj.setContainerId(page.getId());
        nlb.addObj(obj);
        page.addContainedObjId(obj.getId());
        return obj;
    }

    VariableImpl variable(Variable.Type type, Variable.DataType dataType, String name, String value) {
        VariableImpl variable = new VariableImpl(nlb, type, dataType, name, value, null);
        nlb.addVariable(variable);
        return variable;
    }

    /**
     * Модификация: varName — имя переменной-получателя (null — без неё), expr — выражение
     * или операнд (null — без него)
     */
    void mod(AbstractModifyingItem item, Modification.Type type, String varName, String expr) {
        ModificationImpl mod = new ModificationImpl(item);
        mod.setType(type.name());
        if (varName != null) {
            mod.setVarId(variable(Variable.Type.VAR, Variable.DataType.AUTO, varName, null).getId());
        }
        if (expr != null) {
            mod.setExprId(variable(Variable.Type.EXPRESSION, Variable.DataType.AUTO, null, expr).getId());
        }
        item.addModification(mod);
    }

    /**
     * Ограничение ссылки (выражение)
     */
    void constraint(LinkImpl link, String expr) {
        link.setConstrId(variable(Variable.Type.LINKCONSTRAINT, Variable.DataType.BOOLEAN, null, expr).getId());
    }

    PlayerEngine start() {
        PlayerEngine engine = create(true);
        engine.start();
        return engine;
    }

    /**
     * Движок без старта; deterministic = false — настоящая случайность с постоянным зерном
     */
    PlayerEngine create(boolean deterministic) {
        PlayerEngine.Settings settings = new PlayerEngine.Settings();
        settings.exportMode = PlayerEngine.ExportMode.STANDARD;
        settings.deterministic = deterministic;
        settings.randomSeed = 12345;
        return new PlayerEngine(nlb, settings);
    }

    /**
     * Сессия игрока (реальное время) в теме STANDARD
     */
    PlayerSession session() {
        PlayerEngine.Settings settings = new PlayerEngine.Settings();
        settings.exportMode = PlayerEngine.ExportMode.STANDARD;
        settings.deterministic = true;
        settings.gameInvText = "Хм... Странная штука...";
        settings.gameNouseText = "Не сработает...";
        return new PlayerSession(nlb, settings);
    }
}
