package com.soniq.base.playback;

import org.junit.Assert;
import org.junit.Test;

public final class PlaybackPositionTest {

    private static PlaybackPosition playingAtZero() {
        PlaybackPosition position = new PlaybackPosition();
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
    public void seekToZeroAfterNonZeroGrowsFromZero() {
        PlaybackPosition position = playingAtZero();
        position.update(90_000, 1000L, 0);
        position.seekTo(0, 5000L);
        Assert.assertEquals(1500, position.update(90_000, 6500L, 0));
        Assert.assertEquals(2500, position.update(0, 7500L, 0));
        // Playing from zero: readings keep being "far" from target 0 only until the
        // grace window closes; both paths must agree with the estimate by then.
        Assert.assertEquals(3000, position.update(3000, 8000L, 0));
        Assert.assertEquals(7000, position.update(7000, 12_000L, 0));
        Assert.assertFalse(position.isSeekPending());
    }

    @Test
    public void rejectedSeekEventuallyShowsReality() {
        PlaybackPosition position = playingAtZero();
        position.update(30_000, 1000L, 0);
        position.seekTo(200_000, 1000L);
        // Player never moves: after the grace window we accept its position.
        Assert.assertEquals(201_000, position.update(30_000, 2000L, 0));
        Assert.assertEquals(30_000, position.update(30_000, 6500L, 0));
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
}
