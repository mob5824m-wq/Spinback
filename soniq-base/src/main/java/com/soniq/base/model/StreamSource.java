package com.soniq.base.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A directly playable stream for a track, as produced by a provider's
 * {@code resolve()}. Framework classes only need {@link #url} (plus
 * {@link #headers}, {@link #mimeType} and {@link #hls}); the metadata fields are
 * an optional, more accurate copy of what the player response reported.
 */
public final class StreamSource {

    public String url = "";
    public String mimeType = "";
    public int bitrate = 0;
    /** True for HLS manifests, which {@code MediaPlayer} plays without headers. */
    public boolean hls = false;
    /** Provider-specific diagnostics label (which client / CDN produced the URL). */
    public String sourceLabel = "";

    /** Corrected metadata discovered while resolving, or empty to keep the search result's. */
    public String title = "";
    public String author = "";
    public String album = "";
    public String artworkUrl = "";
    public long durationSec = 0L;

    public final Map<String, String> headers = new LinkedHashMap<>();

    public boolean hasStream() {
        return url != null && url.length() > 0;
    }

    public String describe() {
        if (!hasStream()) {
            return "no stream";
        }
        if (hls) {
            return "HLS" + (sourceLabel.length() > 0 ? " · " + sourceLabel : "");
        }
        return (bitrate > 0 ? (bitrate / 1000) + " kbps" + (sourceLabel.length() > 0 ? " · " : "")
                : "") + sourceLabel;
    }
}
