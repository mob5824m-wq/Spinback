package com.soniq.base.playback;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import com.soniq.base.data.NetworkPolicy;

/** Reads the current connection and answers "may we stream / download now?". */
public final class NetworkStatus {

    private NetworkStatus() {
    }

    /** One of the {@link NetworkPolicy} constants; never throws. */
    public static int currentType(Context context) {
        if (context == null) {
            return NetworkPolicy.UNKNOWN;
        }
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return NetworkPolicy.UNKNOWN;
        }
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                android.net.Network network = manager.getActiveNetwork();
                if (network == null) {
                    return NetworkPolicy.UNKNOWN;
                }
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities == null) {
                    return NetworkPolicy.UNKNOWN;
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return NetworkPolicy.WIFI;
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return NetworkPolicy.ETHERNET;
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return NetworkPolicy.CELLULAR;
                }
                // VPN-over-something, Bluetooth tethering, etc.
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        ? NetworkPolicy.ETHERNET : NetworkPolicy.OTHER_METERED;
            }
            NetworkInfo info = manager.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) {
                return NetworkPolicy.UNKNOWN;
            }
            switch (info.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    return NetworkPolicy.WIFI;
                case ConnectivityManager.TYPE_ETHERNET:
                    return NetworkPolicy.ETHERNET;
                case ConnectivityManager.TYPE_MOBILE:
                    return NetworkPolicy.CELLULAR;
                default:
                    return info.isRoaming() ? NetworkPolicy.OTHER_METERED : NetworkPolicy.UNKNOWN;
            }
        } catch (RuntimeException unavailable) {
            // SecurityException on a locked-down ROM: treat as unknown, never crash playback.
            return NetworkPolicy.UNKNOWN;
        }
    }

    /** Human label for the current connection ("Wi-Fi", "Mobile data"). */
    public static String label(Context context) {
        return NetworkPolicy.labelFor(currentType(context));
    }
}
