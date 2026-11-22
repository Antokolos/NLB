package com.nlbhub.nlb.builder.form;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Два текста рядом с подсветкой различий по словам: в первом — чего нет во втором (красным), во
 * втором — чего нет в первом (зелёным); общая часть — наибольшая общая подпоследовательность слов.
 * Окно слияния (своё / с сервера) и история версий (было / стало)
 */
final class WordDiff {
    static final Color REMOVED = new Color(0xFFD6D6);
    static final Color ADDED = new Color(0xD3F5D3);
    /**
     * Больше — без подсветки (таблица наибольшей общей подпоследовательности — слова × слова)
     */
    private static final long MAX_CELLS = 4_000_000L;
    private static final Pattern TOKEN = Pattern.compile("\\s+|[\\p{L}\\p{N}_]+|[^\\s\\p{L}\\p{N}_]");

    private WordDiff() {
    }

    static void show(JTextPane first, String a, JTextPane second, String b) {
        final List<String> tokensA = tokens(a);
        final List<String> tokensB = tokens(b);
        final boolean[] commonA = new boolean[tokensA.size()];
        final boolean[] commonB = new boolean[tokensB.size()];
        if ((long) tokensA.size() * tokensB.size() <= MAX_CELLS) {
            final int[][] lcs = new int[tokensA.size() + 1][tokensB.size() + 1];
            for (int i = tokensA.size() - 1; i >= 0; i--) {
                for (int j = tokensB.size() - 1; j >= 0; j--) {
                    lcs[i][j] = tokensA.get(i).equals(tokensB.get(j))
                            ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            while (i < tokensA.size() && j < tokensB.size()) {
                if (tokensA.get(i).equals(tokensB.get(j))) {
                    commonA[i++] = true;
                    commonB[j++] = true;
                } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                    i++;
                } else {
                    j++;
                }
            }
        }
        fill(first, tokensA, commonA, REMOVED);
        fill(second, tokensB, commonB, ADDED);
    }

    private static void fill(JTextPane pane, List<String> tokens, boolean[] common, Color color) {
        pane.setText("");
        final StyledDocument doc = pane.getStyledDocument();
        final SimpleAttributeSet plain = new SimpleAttributeSet();
        final SimpleAttributeSet marked = new SimpleAttributeSet();
        StyleConstants.setBackground(marked, color);
        try {
            for (int i = 0; i < tokens.size(); i++) {
                final String token = tokens.get(i);
                final boolean space = token.trim().isEmpty();
                doc.insertString(doc.getLength(), token, common[i] || space ? plain : marked);
            }
        } catch (BadLocationException e) {
            pane.setText(String.join("", tokens));
        }
        pane.setCaretPosition(0);
    }

    static List<String> tokens(String text) {
        final List<String> result = new ArrayList<>();
        final Matcher matcher = TOKEN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }
}
