package com.soniq.base.data;

import org.junit.Assert;
import org.junit.Test;

/** "Wi-Fi only" must never block Wi-Fi, and must never quietly spend data. */
public class NetworkPolicyTest {

    @Test
    public void everythingIsAllowedWhenTheSwitchIsOff() {
        NetworkPolicy policy = new NetworkPolicy(false);
        Assert.assertTrue(policy.allowsDownload(NetworkPolicy.CELLULAR));
        Assert.assertTrue(policy.allowsStreaming(NetworkPolicy.UNKNOWN));
        Assert.assertEquals("", policy.blockedReason(NetworkPolicy.CELLULAR, true));
    }

    @Test
    public void wifiOnlyBlocksDownloadsOffWifi() {
        NetworkPolicy policy = new NetworkPolicy(true);
        Assert.assertTrue(policy.allowsDownload(NetworkPolicy.WIFI));
        Assert.assertTrue(policy.allowsDownload(NetworkPolicy.ETHERNET));
        Assert.assertFalse(policy.allowsDownload(NetworkPolicy.CELLULAR));
        Assert.assertFalse(policy.allowsDownload(NetworkPolicy.OTHER_METERED));
        // Unknown is treated as metered for downloads: never gamble with data.
        Assert.assertFalse(policy.allowsDownload(NetworkPolicy.UNKNOWN));
    }

    @Test
    public void blockedDownloadsExplainWhy() {
        NetworkPolicy policy = new NetworkPolicy(true);
        String reason = policy.blockedReason(NetworkPolicy.CELLULAR, true);
        Assert.assertTrue(reason.contains("Wi-Fi only"));
        Assert.assertTrue(reason.contains("Mobile data"));
        Assert.assertEquals("", policy.blockedReason(NetworkPolicy.WIFI, true));
    }

    @Test
    public void streamingToleratesAnUnknownNetworkButNotKnownCellular() {
        NetworkPolicy policy = new NetworkPolicy(true);
        Assert.assertTrue(policy.allowsStreaming(NetworkPolicy.WIFI));
        Assert.assertTrue(policy.allowsStreaming(NetworkPolicy.UNKNOWN));
        Assert.assertFalse(policy.allowsStreaming(NetworkPolicy.CELLULAR));
        Assert.assertFalse(policy.allowsStreaming(NetworkPolicy.OTHER_METERED));
    }

    @Test
    public void unmeteredClassification() {
        Assert.assertTrue(NetworkPolicy.isUnmetered(NetworkPolicy.WIFI));
        Assert.assertTrue(NetworkPolicy.isUnmetered(NetworkPolicy.ETHERNET));
        Assert.assertFalse(NetworkPolicy.isUnmetered(NetworkPolicy.CELLULAR));
        Assert.assertFalse(NetworkPolicy.isUnmetered(NetworkPolicy.UNKNOWN));
    }

    @Test
    public void labelsAreHumanReadable() {
        Assert.assertEquals("Wi-Fi", NetworkPolicy.labelFor(NetworkPolicy.WIFI));
        Assert.assertEquals("Mobile data", NetworkPolicy.labelFor(NetworkPolicy.CELLULAR));
        Assert.assertEquals("Unknown connection", NetworkPolicy.labelFor(NetworkPolicy.UNKNOWN));
    }

    @Test
    public void blockedReasonIsEmptyWhenAllowed() {
        NetworkPolicy policy = new NetworkPolicy(false);
        Assert.assertEquals("", policy.blockedReason(NetworkPolicy.CELLULAR, false));
        Assert.assertEquals("", policy.blockedReason(NetworkPolicy.CELLULAR, true));
    }
}
