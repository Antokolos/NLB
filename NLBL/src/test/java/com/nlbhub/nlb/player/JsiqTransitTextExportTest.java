package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.PageImpl;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * Текст служебной страницы в jsIQ2: страница, которую покидают автоссылкой (или GOTO из её
 * модификаций), остаётся на экране перед текстом следующей статьи, как в плеере — там события
 * всех страниц одного хода идут подряд (бой «Фронтира»: «Применяются эффекты карт…», «Раунд
 * схватки начинается…»). Перед переходом NLB.carry переносит ответ и текст статьи (без сцены,
 * ссылок и мест, которые заполняет общий код) в vars.nlb_prev; следующая статья показывает его в
 * месте nlb_prev; новый ход (ссылка, щелчок) начинает заново
 */
public class JsiqTransitTextExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private String export() throws Exception {
        TestBook b = new TestBook();
        PageImpl fight = b.page("Выберите своё действие.");
        PageImpl effects = b.page("Применяются эффекты карт.");
        PageImpl round = b.page("Раунд схватки начинается...");
        b.link(fight, effects, "Удар");
        LinkImpl auto = b.link(effects, round, "");
        auto.setAuto(true);
        LinkImpl back = b.link(round, fight, "");
        back.setAuto(true);
        NonLinearBookImpl nlb = b.nlb;
        File file = new File(m_temp.getRoot(), "book.xml");
        nlb.exportToJSIQFile(file);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    public void autoLinkCarriesText() throws Exception {
        String xml = export();
        assertTrue("автоссылка: текст статьи переносится перед переходом: " + xml,
                xml.contains("vars.nlb_goto = null; NLB.carry(vars); setTimeout(function() { jsIQ.showArticle(to); }, 0); return true; "));
        assertTrue("GOTO из модификаций страницы — так же: " + xml,
                xml.contains("vars.nlb_goto = null; NLB.carry(vars); setTimeout(function() { jsIQ.showArticle(to); }, 0); return; }"));
        assertTrue("место для перенесённых текстов — первым в статье: " + xml,
                xml.contains("<div class=\"nlb_prev\"></div>Выберите своё действие."));
    }

    @Test
    public void runtime() throws Exception {
        String xml = export();
        assertTrue("ответ — перед текстом страницы, затем сам ответ сброшен: " + xml,
                xml.contains("var html = vars.nlb_reply ? '<div class=\"nlb_said\">' + vars.nlb_reply + '</div>' : '';")
                        && xml.contains("vars.nlb_reply = '';\n    if (t && t.length) {"));
        assertTrue("сцена, ответ и инвентарь не переносятся, ссылки — текстом: " + xml,
                xml.contains("c.find('.nlb_prev, .nlb_reply, .nlb_inv, .nlb_scene').remove();")
                        && xml.contains("c.find('a').each(function () { $(this).replaceWith($(this).contents()); });"));
        assertTrue("переменные и картинка — как показали на той странице: " + xml,
                xml.contains("c.find('.nlb_v, .nlb_pic').removeClass('nlb_v nlb_pic');"));
        assertTrue("новый ход — заново (ссылка и щелчок): " + xml,
                xml.contains("vars.nlb_reply = '';\n    vars.nlb_prev = '';\n    vars.nlb_sel = '';")
                        && xml.contains("var vars = N.vars();\n    vars.nlb_reply = '';\n    vars.nlb_prev = '';"));
        assertTrue("показ — в месте nlb_prev: " + xml,
                xml.contains("c.find('.nlb_prev').each(function () { $(this).html(vars.nlb_prev || ''); });"));
    }
}
