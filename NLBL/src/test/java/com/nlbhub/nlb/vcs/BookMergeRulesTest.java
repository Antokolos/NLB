package com.nlbhub.nlb.vcs;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookMergeRulesTest {
    @Test
    public void bothAdded() {
        assertEquals("a\nb\nc\nd", BookMergeRules.mergeList("a\nb", "a\nb\nc", "a\nb\nd"));
        assertEquals("x\ny", BookMergeRules.mergeList(null, "x", "y"));
    }

    @Test
    public void deletionsAreKept() {
        assertEquals("удалено на сервере", "a\nc", BookMergeRules.mergeList("a\nb", "a\nb\nc", "a"));
        assertEquals("удалено у нас", "b\nd", BookMergeRules.mergeList("a\nb", "b", "a\nb\nd"));
        assertEquals("у нас список опустел (файл удалён)", "d", BookMergeRules.mergeList("a\nb", null, "a\nb\nd"));
    }

    @Test
    public void ourOrderWins() {
        assertEquals("b\na\nc", BookMergeRules.mergeList("a\nb", "b\na", "a\nb\nc"));
    }

    @Test
    public void reconcileWithDirs() {
        assertEquals(Arrays.asList("b", "a", "c", "d"),
                BookMergeRules.reconcile(Arrays.asList("b", "x", "a"), new HashSet<>(Arrays.asList("a", "b", "d", "c"))));
        assertEquals(Collections.emptyList(), BookMergeRules.reconcile(Arrays.asList("x"), Collections.emptySet()));
    }

    @Test
    public void kinds() {
        assertTrue(BookMergeRules.isList("pages/p/lnkorder"));
        assertTrue(BookMergeRules.isList("pages/p/links/l/modorder"));
        assertTrue(BookMergeRules.isList("autopgs"));
        assertTrue(BookMergeRules.isCoords("pages/p/coords/left"));
        assertFalse(BookMergeRules.isCoords("pages/p/text/en"));
        assertFalse(BookMergeRules.isList("pages/p/text/en"));
    }
}
