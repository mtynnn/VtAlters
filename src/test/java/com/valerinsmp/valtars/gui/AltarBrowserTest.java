package com.valerinsmp.valtars.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AltarBrowserTest {
    @Test
    void paginationKeepsEmptyAndFullBoundaryPagesStable() {
        assertEquals(1, AltarBrowser.pageCount(0));
        assertEquals(1, AltarBrowser.pageCount(45));
        assertEquals(2, AltarBrowser.pageCount(46));
        assertEquals(1, AltarBrowser.clampPage(46, 0));
        assertEquals(2, AltarBrowser.clampPage(46, 9));
    }
}
