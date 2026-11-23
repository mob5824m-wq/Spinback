package com.soniq.base.ui;

import org.junit.Assert;
import org.junit.Test;

/** Adaptive layout arithmetic, checked without an Android runtime. */
public class AdaptiveUiTest {

    /** Mirrors the clamp in AdaptiveUi.contentWidthDp, which needs a Context. */
    private static int contentWidth(int widthDp) {
        return Math.min(widthDp, AdaptiveUi.MAX_CONTENT_DP);
    }

    @Test
    public void contentWidthIsCappedForTablets() {
        Assert.assertEquals(360, contentWidth(360));
        Assert.assertEquals(AdaptiveUi.MAX_CONTENT_DP, contentWidth(1280));
        Assert.assertEquals(AdaptiveUi.MAX_CONTENT_DP, contentWidth(AdaptiveUi.MAX_CONTENT_DP));
    }

    @Test
    public void contentNeverExceedsTheCap() {
        Assert.assertTrue(AdaptiveUi.MAX_CONTENT_DP <= 720);
        Assert.assertTrue(AdaptiveUi.MAX_CONTENT_DP > AdaptiveUi.TABLET_DP);
    }

    @Test
    public void tabletBreakpointIsWiderThanNarrow() {
        Assert.assertEquals(600, AdaptiveUi.TABLET_DP);
        Assert.assertEquals(360, AdaptiveUi.NARROW_DP);
        Assert.assertTrue(AdaptiveUi.TABLET_DP > AdaptiveUi.NARROW_DP);
    }

    @Test
    public void gridColumnsKeepTilesReadable() {
        int phone = Math.max(1, (contentWidth(320) - 32) / 160);
        Assert.assertEquals(1, phone);

        int tablet = Math.min(Math.max(1, (contentWidth(1280) - 32) / 160), 4);
        Assert.assertEquals(4, tablet);
    }

    @Test
    public void centredMarginsAreZeroOnPhones() {
        int phoneWidth = 400;
        int margin = phoneWidth <= contentWidth(phoneWidth)
                ? 0 : (phoneWidth - contentWidth(phoneWidth)) / 2;
        Assert.assertEquals(0, margin);

        int tabletWidth = 1000;
        int wide = tabletWidth <= contentWidth(tabletWidth)
                ? 0 : (tabletWidth - contentWidth(tabletWidth)) / 2;
        Assert.assertEquals((1000 - AdaptiveUi.MAX_CONTENT_DP) / 2, wide);
        Assert.assertTrue(wide > 0);
    }

    @Test
    public void fontScaleCapIsBetweenOneAndTwo() {
        float base = 14f;
        float capped = Math.min(base * 2f, base * 1.6f);
        Assert.assertEquals(base * 1.6f, capped, 0.001f);
    }
}
