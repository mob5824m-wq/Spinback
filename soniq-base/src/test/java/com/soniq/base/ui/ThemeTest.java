package com.soniq.base.ui;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class ThemeTest {
    @After public void tearDown() { Theme.reset(); }

    @Test public void labelsCanBeCustomizedAndReset() {
        Theme.HOME_LABEL = "Listen Now";
        Theme.SEARCH_LABEL = "Explore";
        Theme.LIBRARY_LABEL = "My Music";
        Theme.HOME_TITLE = "Listen Now";
        Theme.LIGHT_SYSTEM_BARS = true;
        Theme.BG = 0xFFFFFFFF;
        assertEquals("Listen Now", Tab.HOME.displayLabel());
        assertEquals("Explore", Tab.SEARCH.displayLabel());
        assertEquals("My Music", Tab.LIBRARY.displayLabel());
        assertEquals("Settings", Tab.SETTINGS.displayLabel());
        Theme.reset();
        assertEquals("Home", Tab.HOME.displayLabel());
        assertEquals("Search", Tab.SEARCH.displayLabel());
        assertEquals("Library", Tab.LIBRARY.displayLabel());
        assertEquals(0xFF0B0B0F, Theme.BG);
        assertFalse(Theme.LIGHT_SYSTEM_BARS);
        assertNull(Theme.HOME_TITLE);
    }
}
