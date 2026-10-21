package com.nlbhub.nlb.player;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Порции текста VN — как VNSTEADExportManager.getPreprocessedChunks: строка — порция, длинная —
 * по предложениям (порция больше 100 символов, хвост до 330 — к последней)
 */
public class TextPortionsTest {
    private static ScreenModel.Paragraph paragraph(String... runs) {
        ScreenModel.Paragraph p = new ScreenModel.Paragraph();
        for (int i = 0; i < runs.length; i++) {
            boolean link = runs[i].startsWith("{");
            String text = link ? runs[i].substring(1) : runs[i];
            p.m_runs.add(new ScreenModel.TextRun(
                    link ? ScreenModel.TextRun.Kind.ObjectLink : ScreenModel.TextRun.Kind.Plain, text, link ? "obj" : ""));
        }
        return p;
    }

    private static List<String> texts(List<List<ScreenModel.Paragraph>> portions) {
        List<String> result = new ArrayList<>();
        for (List<ScreenModel.Paragraph> portion : portions) {
            result.add(portion.get(0).getText());
        }
        return result;
    }

    private static String sentence(int length) {
        return "a".repeat(length - 1) + ".";
    }

    @Test
    public void linesArePortionsAndBlankLinesOnlySeparate() {
        List<ScreenModel.Paragraph> text = List.of(paragraph("Раз."), paragraph(" "), paragraph("Два!"));
        assertEquals(List.of("Раз.", "Два!"), texts(TextPortions.of(text)));
    }

    @Test
    public void shortLineIsOnePortion() {
        String line = sentence(60) + " " + sentence(39);
        assertEquals(List.of(line), texts(TextPortions.of(List.of(paragraph(line)))));
    }

    @Test
    public void longLineIsSplitBySentences() {
        // 60 + 50 > 100: первые два предложения — порция; хвост 300 с ней длиннее 330 — отдельно
        String a = sentence(60);
        String b = " " + sentence(49);
        String c = " " + sentence(299);
        List<String> portions = texts(TextPortions.of(List.of(paragraph(a + b + c))));
        assertEquals(List.of(a + b, c.substring(1)), portions);
    }

    @Test
    public void shortTailJoinsLastPortion() {
        String a = sentence(60);
        String b = " " + sentence(49);
        String c = " " + sentence(30);
        assertEquals(List.of(a + b + c), texts(TextPortions.of(List.of(paragraph(a + b + c)))));
    }

    @Test
    public void closingQuotesStayWithSentenceAndLinksSurvive() {
        String a = "«" + "a".repeat(100) + "!» ";
        ScreenModel.Paragraph p = paragraph(a, "{ключ", " " + "b".repeat(330) + ".");
        List<List<ScreenModel.Paragraph>> portions = TextPortions.of(List.of(p));
        assertEquals(2, portions.size());
        assertEquals(a.trim(), portions.get(0).get(0).getText().trim());
        ScreenModel.Paragraph second = portions.get(1).get(0);
        assertEquals(ScreenModel.TextRun.Kind.ObjectLink, second.getRuns().get(0).getKind());
        assertEquals("ключ", second.getRuns().get(0).getText());
    }
}
