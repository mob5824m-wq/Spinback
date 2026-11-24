package com.soniq.base.data;

import org.junit.Assert;
import org.junit.Test;

public final class DownloadsTest {

    @Test
    public void sanitizeRemovesPathSeparatorsAndControlCharacters() {
        Assert.assertEquals("Artist - Title", Downloads.sanitize("Artist - Title"));
        Assert.assertEquals("ACDC BackinBlack", Downloads.sanitize("AC/DC: Back\\in*Black"));
        Assert.assertEquals("track", Downloads.sanitize("   "));
        Assert.assertEquals("track", Downloads.sanitize(".."));
    }

    @Test
    public void sanitizeTruncatesLongNames() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            builder.append('a');
        }
        Assert.assertEquals(80, Downloads.sanitize(builder.toString()).length());
    }
}
