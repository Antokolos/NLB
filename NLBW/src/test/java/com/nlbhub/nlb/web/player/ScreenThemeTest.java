package com.nlbhub.nlb.web.player;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Разбор темы INSTEAD: тема книги (__theme__) поверх базовой, экран темы 800×600 на 1920×1080,
 * режим картинки (scr.gfx.mode), места стрелок, цвет рамки
 */
public class ScreenThemeTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @Test
    public void bookThemeOverBaseTheme() throws Exception {
        File book = m_temp.newFolder("book");
        File theme = new File(book, "__theme__");
        assertTrue(theme.mkdirs());
        // как тема «Фронтира» (Rangers 3) из экспорта в INSTEAD
        Files.writeString(new File(theme, "theme.ini").toPath(), String.join("\n",
                "scr.w = 800", "scr.h = 600", "scr.gfx.h = -1", "scr.gfx.bg = gfx/bg7.png", "scr.col.bg = black",
                "scr.gfx.mode = fixed", "win.align = left", "win.x = 27", "win.y = 7", "win.w = 493", "win.h = 574",
                "win.fnt.size = 14", "win.gfx.up = gfx/up.png", "win.up.x = 521", "win.up.y = 24",
                "win.down.x = 521", "win.down.y = 569", "win.fnt.name = fonts/{sans,sans-b,sans-i,mono}.ttf",
                "inv.mode = vertical", "menu.gfx.button = gfx/menu.png", "menu.button.x = 768",
                "menu.button.y = 4"), StandardCharsets.UTF_8);
        JsonNode t = ScreenTheme.load(ScreenTheme.roots(book), false).toJson();
        // 800×600 -> 1920×1080: масштаб 1,8, поля по 240 слева и справа
        assertEquals(1.8, t.path("scale").asDouble(), 1e-9);
        assertEquals(240.0, t.path("screenArea").path("x").asDouble(), 1e-9);
        assertEquals(1440.0, t.path("screenArea").path("w").asDouble(), 1e-9);
        assertEquals("fixed", t.path("gfxMode").asText());
        assertTrue(t.path("gfxMaxHeight").isNull());  // scr.gfx.h = -1 — без предела
        assertEquals("#000000", t.path("background").asText());
        assertEquals("#000000", t.path("border").asText());  // scr.col.brd — из базовой темы INSTEAD
        assertEquals("game/gfx/bg7.png", t.path("backgroundImage").asText());
        assertEquals("", t.path("pageBackground").asText());  // gfx/bg.jpg темы NLB — тема заменена
        assertEquals("left", t.path("align").asText());
        assertEquals(false, t.path("inventoryHorizontal").asBoolean());
        JsonNode text = t.path("text");
        assertEquals(240 + 27 * 1.8, text.path("x").asDouble(), 1e-9);
        assertEquals(14 * 1.8, text.path("fontSize").asDouble(), 1e-9);
        assertEquals("game/fonts/sans.ttf", text.path("font").asText());
        assertEquals("game/fonts/sans-i.ttf", text.path("fontItalic").asText());
        assertEquals(240 + 521 * 1.8, text.path("upX").asDouble(), 1e-9);
        assertEquals(569 * 1.8, text.path("downY").asDouble(), 1e-9);
        assertTrue(t.path("inventory").path("upX").isNull());
        assertEquals("game/gfx/menu.png", t.path("themeMenuButton").asText());
        assertEquals(240 + 768 * 1.8, t.path("menuButtonX").asDouble(), 1e-9);
        assertEquals("", t.path("menuButton").asText());  // gfx/menubtn.png игры NLB — нет
        assertEquals("base/click.wav", t.path("clickSound").asText());  // из базовой темы
    }

    @Test
    public void builtInThemes() {
        // без темы книги: тема NLB (float, область scr.gfx) поверх стандартной темы INSTEAD (embedded)
        JsonNode t = ScreenTheme.load(ScreenTheme.roots(null), false).toJson();
        assertEquals("float", t.path("gfxMode").asText());
        assertEquals(640.0, t.path("gfxMaxHeight").asDouble(), 1e-9);
        assertEquals("#000000", t.path("border").asText());
        assertTrue(t.path("text").path("upX").isNull());
    }
}
