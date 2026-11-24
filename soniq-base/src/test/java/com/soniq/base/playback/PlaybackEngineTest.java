package com.soniq.base.playback;

import org.junit.Assert;
import org.junit.Test;

/** Crossfade / gapless / resume decisions, all pure. */
public class PlaybackEngineTest {

    @Test
    public void crossfadeSecondsClampToSafeRange() {
        PlaybackEngine engine = new PlaybackEngine();
        engine.setCrossfadeSeconds(99);
        Assert.assertEquals(PlaybackEngine.MAX_CROSSFADE_SECONDS, engine.crossfadeSeconds());
        engine.setCrossfadeSeconds(-5);
        Assert.assertEquals(0, engine.crossfadeSeconds());
        Assert.assertFalse(engine.isCrossfadeEnabled());
        engine.setCrossfadeSeconds(6);
        Assert.assertTrue(engine.isCrossfadeEnabled());
        Assert.assertEquals(6000, engine.fadeMillis());
    }

    @Test
    public void prebufferWantedByGaplessOrCrossfadeOnly() {
        PlaybackEngine engine = new PlaybackEngine();
        Assert.assertFalse(engine.needsPrebuffer());
        Assert.assertFalse(engine.shouldPrebuffer("track:1"));

        engine.setGaplessEnabled(true);
        Assert.assertTrue(engine.shouldPrebuffer("track:1"));

        engine.setGaplessEnabled(false);
        engine.setCrossfadeSeconds(4);
        Assert.assertTrue(engine.shouldPrebuffer("track:1"));
    }

    @Test
    public void prebufferIsNotRequestedTwiceForTheSameTrack() {
        PlaybackEngine engine = new PlaybackEngine();
        engine.setGaplessEnabled(true);
        Assert.assertTrue(engine.shouldPrebuffer("a"));
        engine.onBufferPrepared("a");
        Assert.assertFalse(engine.shouldPrebuffer("a"));
        Assert.assertTrue(engine.isBufferReady("a", true));
        Assert.assertFalse(engine.isBufferReady("a", false));
        Assert.assertFalse(engine.isBufferReady("b", true));
        engine.onBufferReleased();
        Assert.assertTrue(engine.shouldPrebuffer("a"));
    }

    @Test
    public void blankIdentityNeverPrebuffers() {
        PlaybackEngine engine = new PlaybackEngine();
        engine.setGaplessEnabled(true);
        Assert.assertFalse(engine.shouldPrebuffer(""));
        Assert.assertFalse(engine.shouldPrebuffer(null));
    }

    @Test
    public void fadeCurvesAreLinearAndBounded() {
        int fadeMs = 4000;
        Assert.assertEquals(1f, PlaybackEngine.fadeOutVolume(9000, fadeMs), 0.001f);
        Assert.assertEquals(1f, PlaybackEngine.fadeOutVolume(4000, fadeMs), 0.001f);
        Assert.assertEquals(0.5f, PlaybackEngine.fadeOutVolume(2000, fadeMs), 0.001f);
        Assert.assertEquals(0f, PlaybackEngine.fadeOutVolume(0, fadeMs), 0.001f);

        Assert.assertEquals(0f, PlaybackEngine.fadeInVolume(0, fadeMs), 0.001f);
        Assert.assertEquals(0.25f, PlaybackEngine.fadeInVolume(1000, fadeMs), 0.001f);
        Assert.assertEquals(1f, PlaybackEngine.fadeInVolume(4000, fadeMs), 0.001f);
        Assert.assertEquals(1f, PlaybackEngine.fadeInVolume(9000, fadeMs), 0.001f);
    }

    @Test
    public void fadeIsInstantWhenDisabled() {
        Assert.assertEquals(1f, PlaybackEngine.fadeOutVolume(0, 0), 0.001f);
        Assert.assertEquals(1f, PlaybackEngine.fadeInVolume(0, 0), 0.001f);
    }

    @Test
    public void crossfadeStartsBeforeTheEndAndRefusesImpossibleTracks() {
        PlaybackEngine engine = new PlaybackEngine();
        engine.setCrossfadeSeconds(5);
        Assert.assertEquals(180_000 - 5_000, engine.crossfadeStartPosition(180_000));
        // Live streams and unknown durations cannot crossfade.
        Assert.assertEquals(-1, engine.crossfadeStartPosition(0));
        Assert.assertEquals(-1, engine.crossfadeStartPosition(-1));
        // A track shorter than the fade window would start the fade at 0.
        Assert.assertEquals(-1, engine.crossfadeStartPosition(4_000));

        engine.setCrossfadeSeconds(0);
        Assert.assertEquals(-1, engine.crossfadeStartPosition(180_000));
    }

    @Test
    public void resumeRequiresMeaningfulProgress() {
        Assert.assertFalse(new ResumePoint("id", "t", "a", 5_000, 200_000, 1L)
                .isWorthOffering());
        Assert.assertTrue(new ResumePoint("id", "t", "a", 45_000, 200_000, 1L)
                .isWorthOffering());
        // Ten seconds from the end is not a resume, it is the end.
        Assert.assertFalse(new ResumePoint("id", "t", "a", 192_000, 200_000, 1L)
                .isWorthOffering());
    }

    @Test
    public void resumeExpires() {
        long now = 1_700_000_000_000L;
        ResumePoint fresh = new ResumePoint("id", "t", "a", 60_000, 200_000, now - 60_000L);
        Assert.assertTrue(fresh.isUsable(now));

        long stale = now - (ResumePoint.MAX_AGE_DAYS + 1L) * 24L * 60L * 60L * 1000L;
        ResumePoint old = new ResumePoint("id", "t", "a", 60_000, 200_000, stale);
        Assert.assertFalse(old.isUsable(now));
        Assert.assertTrue(old.isWorthOffering());
    }

    @Test
    public void resumeRoundTripsThroughText() {
        ResumePoint point = new ResumePoint("yt:abc", "Song \u0001 with a separator",
                "Artist", 61_000, 240_000, 1_700_000_000_000L);
        ResumePoint decoded = ResumePoint.decode(point.encode());
        Assert.assertNotNull(decoded);
        Assert.assertEquals(point.identity, decoded.identity);
        Assert.assertEquals(point.title, decoded.title);
        Assert.assertEquals(point.author, decoded.author);
        Assert.assertEquals(point.positionMs, decoded.positionMs);
        Assert.assertEquals(point.durationMs, decoded.durationMs);
        Assert.assertEquals(point.savedAt, decoded.savedAt);
    }

    @Test
    public void malformedResumeTextIsRejected() {
        Assert.assertNull(ResumePoint.decode(""));
        Assert.assertNull(ResumePoint.decode(null));
        Assert.assertNull(ResumePoint.decode("only\u0001three\u0001fields"));
        Assert.assertNull(ResumePoint.decode("id\u0001t\u0001a\u0001nan\u0001200\u00011"));
    }

    @Test
    public void resumeLabelsReadNaturally() {
        ResumePoint point = new ResumePoint("id", "Song", "Artist", 222_000, 301_000, 1L);
        Assert.assertEquals("3:42 of 5:01", point.positionLabel());
        Assert.assertEquals("Artist · 3:42 of 5:01", point.summary());

        ResumePoint noDuration = new ResumePoint("id", "Song", "Artist", 222_000, 0, 1L);
        Assert.assertEquals("3:42", noDuration.positionLabel());
        Assert.assertEquals("Artist · 3:42", noDuration.summary());
    }

    @Test
    public void ofDropsPointsThatAreNotWorthSaving() {
        com.soniq.base.model.MediaItem item = new com.soniq.base.model.MediaItem();
        item.id = "abc";
        item.title = "Song";
        Assert.assertNotNull(ResumePoint.of(item, 60_000, 200_000, 1L));
        Assert.assertNull(ResumePoint.of(item, 1_000, 200_000, 1L));

        com.soniq.base.model.MediaItem nameless = new com.soniq.base.model.MediaItem();
        Assert.assertNull(ResumePoint.of(nameless, 60_000, 200_000, 1L));
        Assert.assertNull(ResumePoint.of(null, 60_000, 200_000, 1L));
    }
}
