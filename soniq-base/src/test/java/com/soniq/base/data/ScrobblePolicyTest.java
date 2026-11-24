package com.soniq.base.data;

import org.junit.Assert;
import org.junit.Test;

public final class ScrobblePolicyTest {

    @Test
    public void thresholdIsHalfTheTrackForShortTracks() {
        Assert.assertEquals(90_000L, ScrobblePolicy.thresholdMs(180_000L));
        Assert.assertEquals(15_000L, ScrobblePolicy.thresholdMs(30_000L));
    }

    @Test
    public void thresholdIsCappedAtFourMinutesForLongTracks() {
        Assert.assertEquals(240_000L, ScrobblePolicy.thresholdMs(600_000L));
        Assert.assertEquals(240_000L, ScrobblePolicy.thresholdMs(3_600_000L));
        // exactly at the cap boundary: an 8-minute track halves to exactly 4 minutes
        Assert.assertEquals(240_000L, ScrobblePolicy.thresholdMs(480_000L));
    }

    @Test
    public void unknownDurationUsesTheFourMinuteRule() {
        Assert.assertEquals(240_000L, ScrobblePolicy.thresholdMs(0L));
        Assert.assertEquals(240_000L, ScrobblePolicy.thresholdMs(-5L));
    }

    @Test
    public void listenCountsOnlyOnceTheThresholdIsReached() {
        Assert.assertFalse(ScrobblePolicy.shouldScrobble(0L, 180_000L));
        Assert.assertFalse(ScrobblePolicy.shouldScrobble(89_999L, 180_000L));
        Assert.assertTrue(ScrobblePolicy.shouldScrobble(90_000L, 180_000L));
        Assert.assertTrue(ScrobblePolicy.shouldScrobble(500_000L, 180_000L));
    }

    @Test
    public void cleanStripsNullsAndCollapsesWhitespace() {
        Assert.assertEquals("Rick Astley", ScrobblePolicy.clean("  Rick   Astley "));
        Assert.assertEquals("Never Gonna Give You Up",
                ScrobblePolicy.clean("Never\tGonna\nGive\u0000 You Up"));
        Assert.assertEquals("", ScrobblePolicy.clean(null));
        Assert.assertEquals("", ScrobblePolicy.clean("   "));
    }

    @Test
    public void cleanCapsVeryLongFields() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            builder.append('x');
        }
        Assert.assertEquals(ScrobblePolicy.MAX_FIELD_LENGTH,
                ScrobblePolicy.clean(builder.toString()).length());
    }

    @Test
    public void artistAndTitleAreBothRequired() {
        Assert.assertTrue(ScrobblePolicy.isSubmittable("Artist", "Song"));
        Assert.assertFalse(ScrobblePolicy.isSubmittable("", "Song"));
        Assert.assertFalse(ScrobblePolicy.isSubmittable("Artist", "  "));
        Assert.assertFalse(ScrobblePolicy.isSubmittable(null, null));
    }
}
