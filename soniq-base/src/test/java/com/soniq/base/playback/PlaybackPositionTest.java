package com.soniq.base.playback;

import org.junit.Assert;
import org.junit.Test;

public final class PlaybackPositionTest {

    /**
     * What {@code PlaybackService.startPlaybackNow()} does at track start:
     * anchor at the first native sample (here: a player stuck at zero) and
     * start playing. Seeding the baseline is what makes every later identical
     * sample count as stuck rather than authoritative.
     */
    private static PlaybackPosition playingAtZero() {
        PlaybackPosition position = new PlaybackPosition();
        position.anchor(0, 0L);
        position.setPlaying(true, 0L);
        return position;
    }

    @Test
    public void positiveNativeReadingIsAuthoritative() {
        PlaybackPosition position = playingAtZero();
        Assert.assertEquals(5000, position.update(5000, 1000L, 0));
    }

    @Test
    public void estimateAdvancesWhilePlayingWhenPlayerReportsZero() {
        PlaybackPosition position = playingAtZero();
        Assert.assertEquals(3000, position.update(0, 3000L, 0));
        Assert.assertEquals(4000, position.update(0, 4000L, 0));
        Assert.assertEquals(5000, position.update(0, 5000L, 0));
    }

    @Test
    public void stuckPositiveSampleStillAdvances() {
        // A player frozen on a small positive buffer sample (e.g. 23 ms) must
        // not freeze the timeline: the sample is accepted once, then the
        // estimate advances past it.
        PlaybackPosition position = new PlaybackPosition();
        position.anchor(23, 0L);
        position.setPlaying(true, 0L);
        Assert.assertEquals(23, position.update(23, 0L, 0));
        Assert.assertEquals(5023, position.update(23, 5000L, 0));
        Assert.assertEquals(6023, position.update(23, 6000L, 0));
    }

    @Test
    public void unavailableReadingUsesEstimate() {
        PlaybackPosition position = playingAtZero();
        Assert.assertEquals(2500, position.update(-1, 2500L, 0));
    }

    @Test
    public void pauseFreezesEstimateAndResumeContinuesFromIt() {
        PlaybackPosition position = playingAtZero();
        Assert.assertEquals(3000, position.update(0, 3000L, 0));
        position.setPlaying(false, 3000L);
        Assert.assertEquals(3000, position.update(0, 10_000L, 0));
        position.setPlaying(true, 10_000L);
        Assert.assertEquals(4000, position.update(0, 11_000L, 0));
    }

    @Test
    public void bufferingFreezesTheEstimateAndResumeContinuesFromIt() {
        // MEDIA_INFO_BUFFERING_START/END: while the player re-buffers, the
        // audio is not advancing, so neither must the clock. Seeded at 23 like
        // the upstream harness (a player stuck on a 23 ms buffer sample).
        PlaybackPosition position = new PlaybackPosition();
        position.anchor(23, 0L);
        position.setPlaying(true, 0L);
        Assert.assertEquals(2023, position.update(23, 2000L, 0));
        position.setBuffering(true, 2000L);
        Assert.assertEquals(2023, position.update(23, 6000L, 0));
        position.setBuffering(false, 6000L);
        Assert.assertEquals(4023, position.update(23, 8000L, 0));
    }

    @Test
    public void staleZeroNeverDragsTheTimelineBack() {
        PlaybackPosition position = playingAtZero();
        Assert.assertEquals(10_000, position.update(10_000, 1000L, 0));
        Assert.assertEquals(11_000, position.update(0, 2000L, 0));
        position.setPlaying(false, 2000L);
        Assert.assertEquals(11_000, position.update(0, 9000L, 0));
    }

    @Test
    public void seekJumpsImmediatelyAndIgnoresPreSeekReadings() {
        PlaybackPosition position = playingAtZero();
        position.update(60_000, 1000L, 0);
        position.seekTo(120_000, 1000L);
        Assert.assertTrue(position.isSeekPending());
        // Player still reports the old position for a moment.
        Assert.assertEquals(120_500, position.update(60_000, 1500L, 0));
        // Player confirms the new position: trust it again.
        Assert.assertEquals(121_000, position.update(121_000, 2100L, 0));
        Assert.assertFalse(position.isSeekPending());
        // A fresh native reading re-anchors "now", so it is returned as-is.
        Assert.assertEquals(121_500, position.update(121_500, 2500L, 0));
    }

    @Test
    public void stuckSampleNeverCancelsASeek() {
        // A player frozen on one sample (even past the grace window) must not
        // undo the seek: the estimate keeps advancing from the target.
        PlaybackPosition position = new PlaybackPosition();
        position.anchor(23, 0L);
        position.setPlaying(true, 0L);
        position.update(23, 1000L, 0);
        position.seekTo(90_000, 1000L);
        Assert.assertEquals(91_000, position.update(23, 2000L, 0));
        Assert.assertEquals(96_000, position.update(23, 7000L, 0));
        Assert.assertTrue(position.isSeekPending());
    }

    @Test
    public void changingReadingsAfterGraceShowReality() {
        // A player that keeps reporting a *different* position has rejected the
        // seek: after the grace window its position is accepted.
        PlaybackPosition position = playingAtZero();
        position.update(30_000, 1000L, 0);
        position.seekTo(200_000, 1000L);
        Assert.assertEquals(201_000, position.update(30_500, 2000L, 0));
        Assert.assertTrue(position.isSeekPending());
        Assert.assertEquals(31_500, position.update(31_500, 6500L, 0));
        Assert.assertFalse(position.isSeekPending());
    }

    @Test
    public void staleZeroCannotConfirmOrUndoANonZeroSeek() {
        PlaybackPosition position = playingAtZero();
        position.update(60_000, 0L, 0);
        position.seekTo(800, 500L);
        // A zero reading is never near a nonzero target.
        Assert.assertEquals(2800, position.update(0, 2500L, 0));
        Assert.assertTrue(position.isSeekPending());
        Assert.assertEquals(850, position.update(850, 3000L, 0));
        Assert.assertFalse(position.isSeekPending());
    }

    @Test
    public void seekToZeroIsConfirmedByAZeroReading() {
        PlaybackPosition position = playingAtZero();
        position.update(90_000, 1000L, 0);
        position.seekTo(0, 5000L);
        // Stale pre-seek sample: ignored, the estimate grows from the target.
        Assert.assertEquals(1500, position.update(90_000, 6500L, 0));
        // A zero is the expected confirmation of a seek to zero.
        Assert.assertEquals(0, position.update(0, 7500L, 0));
        Assert.assertFalse(position.isSeekPending());
        // Playing from zero again: changing readings are authoritative.
        Assert.assertEquals(3000, position.update(3000, 8000L, 0));
        Assert.assertEquals(7000, position.update(7000, 12_000L, 0));
    }

    @Test
    public void estimateIsClampedToDuration() {
        PlaybackPosition position = playingAtZero();
        Assert.assertEquals(3000, position.update(0, 5000L, 3000));
    }

    @Test
    public void resetForgetsTheStaleZeroGuard() {
        PlaybackPosition position = playingAtZero();
        position.update(5000, 1000L, 0);
        position.setPlaying(false, 1000L);
        Assert.assertEquals(5000, position.update(0, 2000L, 0));
        position.reset();
        Assert.assertEquals(0, position.update(0, 2000L, 0));
    }

    @Test
    public void resetForgetsAPendingSeek() {
        PlaybackPosition position = playingAtZero();
        position.update(5000, 1000L, 0);
        position.seekTo(0, 1000L);
        Assert.assertTrue(position.isSeekPending());
        position.reset();
        Assert.assertFalse(position.isSeekPending());
        position.setPlaying(true, 2000L);
        Assert.assertEquals(5000, position.update(5000, 2500L, 0));
    }

    @Test
    public void resetForgetsTheNativeBaseline() {
        // After a reset (track change) the first sample of the new track is
        // authoritative again, even if it repeats the previous baseline.
        PlaybackPosition position = playingAtZero();
        position.update(5000, 1000L, 0);
        position.reset();
        position.setPlaying(true, 2000L);
        Assert.assertEquals(7000, position.update(7000, 2500L, 0));
    }
}
