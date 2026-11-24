package com.soniq.base.data;

/**
 * The "Wi-Fi only" rule, kept pure so both streaming and downloads can ask the
 * same question and the answer is unit-tested.
 *
 * <p>Honesty rule: when a host app has not told us the connection type, we say
 * {@link #UNKNOWN} and treat it as "may be metered" for downloads, rather than
 * guessing that data is free.
 */
public final class NetworkPolicy {

    public static final int UNKNOWN = 0;
    public static final int WIFI = 1;
    public static final int CELLULAR = 2;
    public static final int ETHERNET = 3;
    public static final int OTHER_METERED = 4;

    private final boolean wifiOnly;

    public NetworkPolicy(boolean wifiOnly) {
        this.wifiOnly = wifiOnly;
    }

    public boolean isWifiOnly() {
        return wifiOnly;
    }

    /** True for connections that are never billed by the byte. */
    public static boolean isUnmetered(int networkType) {
        return networkType == WIFI || networkType == ETHERNET;
    }

    public static String labelFor(int networkType) {
        switch (networkType) {
            case WIFI:
                return "Wi-Fi";
            case CELLULAR:
                return "Mobile data";
            case ETHERNET:
                return "Ethernet";
            case OTHER_METERED:
                return "Metered network";
            default:
                return "Unknown connection";
        }
    }

    /**
     * May a fresh download start? With Wi-Fi only on, only unmetered networks
     * qualify; an unknown network is treated as metered so we never spend
     * somebody's data plan on a guess.
     */
    public boolean allowsDownload(int networkType) {
        return !wifiOnly || isUnmetered(networkType);
    }

    /**
     * May streaming continue? Streams are smaller than downloads, so an unknown
     * network is allowed here; the rule is "off Wi-Fi, do not stream".
     */
    public boolean allowsStreaming(int networkType) {
        return !wifiOnly || isUnmetered(networkType) || networkType == UNKNOWN;
    }

    /** Short explanation shown on a disabled button, or "" when allowed. */
    public String blockedReason(int networkType, boolean download) {
        if (download ? allowsDownload(networkType) : allowsStreaming(networkType)) {
            return "";
        }
        return "Wi-Fi only is on · now using " + labelFor(networkType);
    }
}
