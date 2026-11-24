package com.soniq.base.data;

import com.soniq.base.model.MediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ordering rules for library and playlist lists, kept pure so the "why is this
 * first?" question has one answer and a test.
 *
 * <p>Recent-first is the default everywhere; title/artist sorting is stable so
 * equal keys keep their previous order instead of jumping around on refresh.
 */
public final class LibrarySort {

    private LibrarySort() {
    }

    /** Returns a new list in the requested order; never mutates the input. */
    public static List<MediaItem> apply(List<MediaItem> items, int sort) {
        List<MediaItem> out = new ArrayList<>();
        if (items != null) {
            for (MediaItem item : items) {
                if (item != null) {
                    out.add(item);
                }
            }
        }
        if (sort == Settings.SORT_TITLE) {
            Collections.sort(out, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem a, MediaItem b) {
                    int byTitle = key(a.title).compareTo(key(b.title));
                    return byTitle != 0 ? byTitle : key(a.author).compareTo(key(b.author));
                }
            });
        } else if (sort == Settings.SORT_ARTIST) {
            Collections.sort(out, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem a, MediaItem b) {
                    int byArtist = key(a.author).compareTo(key(b.author));
                    return byArtist != 0 ? byArtist : key(a.title).compareTo(key(b.title));
                }
            });
        } else if (sort == Settings.SORT_DURATION) {
            Collections.sort(out, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem a, MediaItem b) {
                    long left = a.durationSec;
                    long right = b.durationSec;
                    if (left == right) {
                        return key(a.title).compareTo(key(b.title));
                    }
                    // Unknown durations sort last instead of pretending to be 0:00.
                    if (left <= 0L) {
                        return 1;
                    }
                    if (right <= 0L) {
                        return -1;
                    }
                    return left < right ? -1 : 1;
                }
            });
        }
        return out;
    }

    /** Lower-cased, accent-insensitive-enough comparison key. */
    static String key(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        // Strip a leading "The " so "The Beatles" files under B, as users expect.
        if (trimmed.length() > 4 && trimmed.regionMatches(true, 0, "The ", 0, 4)) {
            trimmed = trimmed.substring(4);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** One-line description of a sort choice for the settings row. */
    public static String describe(int sort) {
        switch (sort) {
            case Settings.SORT_TITLE:
                return "Title (A–Z)";
            case Settings.SORT_ARTIST:
                return "Artist (A–Z)";
            case Settings.SORT_DURATION:
                return "Duration (shortest first)";
            default:
                return "Recently played";
        }
    }
}
