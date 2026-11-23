package com.soniq.base.data;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public final class LyricsParserTest {

    @Test
    public void parsesSyncedTimestamps() {
        List<LyricLine> lines = LyricsParser.parse(
                "[00:12.50]Hello\n[01:02]World\n[02:03.4]Third");
        Assert.assertEquals(3, lines.size());
        Assert.assertEquals(12500L, lines.get(0).timeMs);
        Assert.assertEquals("Hello", lines.get(0).text);
        Assert.assertEquals(62000L, lines.get(1).timeMs);
        Assert.assertEquals(123400L, lines.get(2).timeMs);
    }

    @Test
    public void plainLyricsStayUntimed() {
        List<LyricLine> lines = LyricsParser.parse("Just words\nsecond line");
        Assert.assertEquals(2, lines.size());
        Assert.assertFalse(lines.get(0).isTimed());
        Assert.assertEquals("Just words", lines.get(0).text);
    }

    @Test
    public void malformedTimestampFallsBackToText() {
        List<LyricLine> lines = LyricsParser.parse("[not-a-time]still lyrics");
        Assert.assertEquals(1, lines.size());
        Assert.assertFalse(lines.get(0).isTimed());
        Assert.assertEquals("[not-a-time]still lyrics", lines.get(0).text);
    }

    @Test
    public void emptyInputHasNoLines() {
        Assert.assertTrue(LyricsParser.parse(null).isEmpty());
        Assert.assertTrue(LyricsParser.parse("   \n \n").isEmpty());
    }

    @Test
    public void activeIndexFollowsPlaybackPosition() {
        List<LyricLine> lines = LyricsParser.parse("[00:00]a\n[00:05]b\n[00:10]c");
        Assert.assertEquals(0, LyricsParser.activeIndex(lines, 0));
        Assert.assertEquals(0, LyricsParser.activeIndex(lines, 4999));
        Assert.assertEquals(1, LyricsParser.activeIndex(lines, 5000));
        Assert.assertEquals(2, LyricsParser.activeIndex(lines, 60000));
        Assert.assertEquals(-1, LyricsParser.activeIndex(lines, -1));
    }

    @Test
    public void syncedDetectionNeedsOneTimestamp() {
        Assert.assertFalse(LyricsParser.isSynced(LyricsParser.parse("plain text")));
        Assert.assertTrue(LyricsParser.isSynced(LyricsParser.parse("[00:01]timed")));
    }
}
