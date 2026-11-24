package com.soniq.base.ui.screens;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.data.ListeningRecap;
import com.soniq.base.data.ListeningRecapApi;
import com.soniq.base.data.Settings;
import com.soniq.base.ui.Host;
import com.soniq.base.ui.NetworkImage;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;
import com.soniq.base.ui.views.Chip;

import java.util.Calendar;

/**
 * Year-in-review recap ("Wrapped"): the user's ListenBrainz statistics for one
 * whole calendar year — totals, a month-by-month activity chart, top artists,
 * songs, albums and genres, and the most played era. Any year can be opened
 * from the year switcher; a year still in progress is labelled as such and its
 * totals are partial. Nothing here is a December-only summary.
 *
 * <p>Statistics are read-only and require no token, but ListenBrainz must know
 * the user name (saved with the scrobbling settings). Without it, or without
 * published statistics, the sheet explains what is missing and links to the
 * settings tab; it never claims data it does not have.
 */
public final class RecapSheet {

    private final Host host;
    private final FrameLayout root;
    private final HorizontalScrollView yearScroll;
    private final LinearLayout yearRow;
    private final LinearLayout body;
    private final TextView titleView;
    private final TextView rangeView;
    private final TextView captionView;

    private final ListeningRecapApi api;
    private int year;
    /** Bumped per request so a late answer can never replace a newer year. */
    private int request;
    private ListeningRecap recap;

    public RecapSheet(Host host) {
        this.host = host;
        this.api = new ListeningRecapApi(host.context());
        this.year = Calendar.getInstance().get(Calendar.YEAR);

        root = new FrameLayout(host.context());
        root.setVisibility(View.GONE);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.withAlpha(Theme.SECONDARY, 0x30), Theme.BG, Theme.BG}));

        ScrollView scroll = new ScrollView(host.context());
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout column = Ui.column(host.context());
        column.setPadding(dp(16), dp(8), dp(16), dp(32));
        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = Ui.row(host.context());
        top.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_arrow_back,
                Theme.TEXT, 44, 22, "Back", new Runnable() {
                    @Override
                    public void run() {
                        hide();
                    }
                }));
        TextView kicker = Ui.text(host.context(), "YEAR IN REVIEW", 11f, Theme.TEXT_DIM, true);
        kicker.setGravity(Gravity.CENTER);
        top.addView(kicker, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_share,
                Theme.TEXT, 44, 20, "Share your year in review", new Runnable() {
                    @Override
                    public void run() {
                        share();
                    }
                }));
        column.addView(top);

        titleView = Ui.text(host.context(), "", 34f, Theme.TEXT, true);
        titleView.setPadding(dp(4), dp(6), dp(4), 0);
        column.addView(titleView);
        rangeView = Ui.text(host.context(), "", 12f, Theme.TEXT_DIM, false);
        rangeView.setPadding(dp(4), dp(4), dp(4), 0);
        column.addView(rangeView);

        yearRow = Ui.row(host.context());
        yearScroll = new HorizontalScrollView(host.context());
        yearScroll.setHorizontalScrollBarEnabled(false);
        yearScroll.setPadding(0, dp(12), 0, dp(2));
        yearScroll.addView(yearRow);
        column.addView(yearScroll);

        captionView = Ui.text(host.context(), "", 12f, Theme.TEXT_DIM, false);
        captionView.setPadding(dp(4), dp(8), dp(4), 0);
        column.addView(captionView);

        body = Ui.column(host.context());
        body.setPadding(0, dp(8), 0, 0);
        column.addView(body);
    }

    public View view() {
        return root;
    }

    public boolean isShowing() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void show() {
        if (!host.isAlive()) {
            return;
        }
        root.setVisibility(View.VISIBLE);
        load(year);
    }

    public void hide() {
        root.setVisibility(View.GONE);
        cancel();
        request++;
    }

    public void release() {
        cancel();
        request++;
    }

    private void cancel() {
        // Requests are owned by the host executor; the request counter below
        // stops any late answer from rendering into a newer year.
    }

    /* ================================================================== data */

    private void load(final int targetYear) {
        year = targetYear;
        cancel();
        final int current = ++request;
        ListeningRecap cached = recap;
        if (cached != null && cached.year == targetYear) {
            render(cached);
            return;
        }
        recap = null;
        titleView.setText(String.valueOf(targetYear));
        rangeView.setText("Reading your ListenBrainz statistics…");
        captionView.setText("");
        body.removeAllViews();
        body.addView(Ui.caption(host.context(), "This can take a few seconds."));
        renderYears();
        host.io().execute(new Runnable() {
            @Override
            public void run() {
                // Settings reads hit disk: resolve the user name on this thread, not the UI.
                ListeningRecap loaded = null;
                String failure = null;
                try {
                    loaded = api.load(api.userName(new Settings(host.context())), targetYear);
                } catch (Exception error) {
                    failure = error.getMessage() == null
                            ? "network error" : error.getMessage();
                }
                final ListeningRecap result = loaded;
                final String error = failure;
                host.main().post(new Runnable() {
                    @Override
                    public void run() {
                        if (!host.isAlive() || current != request) {
                            return;
                        }
                        if (error != null) {
                            titleView.setText(String.valueOf(targetYear));
                            rangeView.setText("Statistics are unavailable");
                            body.removeAllViews();
                            body.addView(message("Could not read ListenBrainz: " + error,
                                    "Retry", new Runnable() {
                                        @Override
                                        public void run() {
                                            load(targetYear);
                                        }
                                    }));
                            return;
                        }
                        recap = result;
                        render(result);
                    }
                });
            }
        });
    }

    private void renderYears() {
        yearRow.removeAllViews();
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = thisYear; y >= Math.max(2005, thisYear - 12); y--) {
            final int candidate = y;
            Chip chip = new Chip(host.context(), String.valueOf(y), new Runnable() {
                @Override
                public void run() {
                    if (candidate != year) {
                        load(candidate);
                    }
                }
            });
            chip.setSelected(y == year);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(8);
            chip.setLayoutParams(params);
            yearRow.addView(chip);
        }
    }

    private void render(ListeningRecap result) {
        if (result == null) {
            return;
        }
        year = result.year;
        renderYears();
        titleView.setText(result.year > 0 ? String.valueOf(result.year) : "Year in review");
        rangeView.setText(result.rangeLabel.length() > 0
                ? result.rangeLabel
                : "ListenBrainz statistics");
        captionView.setText(result.userName.length() > 0
                ? "Scrobbles from ListenBrainz · @" + result.userName : "");
        body.removeAllViews();

        if (result.isEmpty()) {
            body.addView(message(result.note.length() > 0
                            ? result.note
                            : "No listening statistics were published for this year.",
                    "Search for a song", new Runnable() {
                        @Override
                        public void run() {
                            hide();
                            host.openSearch("");
                        }
                    }));
            return;
        }

        body.addView(headline(result));
        body.addView(monthChart(result));

        String era = result.topEraLabel();
        String busiestMonth = result.busiestMonth(result.year);
        if (era.length() > 0 || busiestMonth.length() > 0
                || result.busiestDayOfWeek.length() > 0) {
            body.addView(Ui.sectionHeader(host.context(), "Highlights"));
            LinearLayout row = Ui.row(host.context());
            if (era.length() > 0) {
                row.addView(statBlock("Most played era", era, ""), halfWeight());
            }
            if (busiestMonth.length() > 0) {
                row.addView(statBlock("Busiest month", busiestMonth, ""), halfWeight());
            }
            body.addView(row);
            if (result.busiestDayOfWeek.length() > 0) {
                body.addView(statBlock("Busiest day of the week",
                        result.busiestDayOfWeek, ""), Ui.matchWrap());
            }
        }

        addArtistList(result);
        addTrackList(result);
        addReleaseList(result);
        addGenreList(result);

        if (result.note.length() > 0) {
            body.addView(Ui.caption(host.context(), result.note));
        }
        TextView footer = Ui.text(host.context(),
                "Statistics calculated by ListenBrainz from your scrobbles. "
                        + "Cover art from the Cover Art Archive.",
                10f, Theme.TEXT_FAINT, false);
        footer.setPadding(dp(4), dp(18), dp(4), 0);
        body.addView(footer);
    }

    /* ================================================================== views */

    private View headline(ListeningRecap result) {
        LinearLayout card = Ui.column(host.context());
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(Ui.rounded(host.context(), Theme.SURFACE, 20f));

        TextView listens = Ui.text(host.context(),
                ListeningRecap.formatCount(result.totalListens), 40f, Theme.TEXT, true);
        card.addView(listens);
        card.addView(Ui.text(host.context(),
                result.totalListens == 1L ? "listen" : "listens", 13f, Theme.TEXT_DIM, false));
        if (result.partial) {
            card.addView(Ui.text(host.context(),
                    "Still counting — the year is not over", 11f, Theme.ACCENT, true));
        }

        String time = ListeningRecap.formatListeningTime(result.totalListeningTimeSec);
        if (time.length() > 0) {
            card.addView(statLine("Listening time", time));
        }
        if (result.totalArtists > 0L) {
            card.addView(statLine("Artists heard", ListeningRecap.formatCount(result.totalArtists)));
        }
        if (result.totalTracks > 0L) {
            card.addView(statLine("Different tracks",
                    ListeningRecap.formatCount(result.totalTracks)));
        }
        if (result.totalReleases > 0L) {
            card.addView(statLine("Albums",
                    ListeningRecap.formatCount(result.totalReleases)));
        }
        if (result.newArtists > 0L) {
            card.addView(statLine("New artists discovered",
                    ListeningRecap.formatCount(result.newArtists)));
        }
        return card;
    }

    private View statLine(String label, String value) {
        LinearLayout row = Ui.row(host.context());
        row.setPadding(0, dp(8), 0, 0);
        row.addView(Ui.text(host.context(), label, 12f, Theme.TEXT_DIM, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(host.context(), value, 13f, Theme.TEXT, true));
        return row;
    }

    private View statBlock(String label, String value, String detail) {
        LinearLayout block = Ui.column(host.context());
        block.setPadding(dp(16), dp(14), dp(16), dp(14));
        block.setBackground(Ui.rounded(host.context(), Theme.SURFACE, 16f));
        block.addView(Ui.text(host.context(), value, 22f, Theme.TEXT, true));
        block.addView(Ui.text(host.context(), label, 11f, Theme.TEXT_DIM, false));
        if (detail.length() > 0) {
            block.addView(Ui.text(host.context(), detail, 11f, Theme.ACCENT, false));
        }
        return block;
    }

    /** Twelve bars, January to December, scaled to the busiest month. */
    private View monthChart(ListeningRecap result) {
        long[] months = result.monthlyTotals(result.year);
        LinearLayout wrapper = Ui.column(host.context());
        wrapper.addView(Ui.sectionHeader(host.context(), "By month"));

        long peak = 0L;
        for (long value : months) {
            peak = Math.max(peak, value);
        }
        if (peak <= 0L) {
            wrapper.addView(Ui.caption(host.context(),
                    "ListenBrainz did not publish a day-by-day breakdown for this year."));
            return wrapper;
        }

        LinearLayout chart = Ui.row(host.context());
        chart.setGravity(Gravity.BOTTOM);
        chart.setPadding(dp(4), 0, dp(4), 0);
        String[] labels = {"J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"};
        int barHeight = dp(96);
        for (int i = 0; i < months.length; i++) {
            int height = months[i] <= 0L
                    ? dp(2)
                    : Math.max(dp(4), Math.round(barHeight * (months[i] / (float) peak)));
            LinearLayout slot = Ui.column(host.context());
            slot.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            View bar = new View(host.context());
            bar.setBackground(Ui.rounded(host.context(),
                    peak == months[i] ? Theme.ACCENT : Theme.withAlpha(Theme.ACCENT, 0x66), 4f));
            slot.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height));
            TextView label = Ui.text(host.context(), labels[i], 10f, Theme.TEXT_FAINT, false);
            label.setGravity(Gravity.CENTER);
            slot.addView(label);
            slot.setContentDescription(monthName(i) + ": "
                    + ListeningRecap.formatCount(months[i])
                    + (months[i] == 1L ? " listen" : " listens"));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.leftMargin = dp(2);
            params.rightMargin = dp(2);
            chart.addView(slot, params);
        }
        wrapper.addView(chart);
        String busiest = result.busiestMonth(result.year);
        if (busiest.length() > 0) {
            wrapper.addView(Ui.caption(host.context(), "Most listens in " + busiest + "."));
        }
        return wrapper;
    }

    private static String monthName(int index) {
        String[] names = {"January", "February", "March", "April", "May", "June", "July",
                "August", "September", "October", "November", "December"};
        return names[index];
    }

    private void addArtistList(ListeningRecap result) {
        if (result.topArtists.isEmpty()) {
            return;
        }
        body.addView(Ui.sectionHeader(host.context(), "Top artists"));
        for (int i = 0; i < result.topArtists.size(); i++) {
            ListeningRecap.Artist artist = result.topArtists.get(i);
            body.addView(rankRow(i + 1, artist.coverUrl, artist.name, "",
                    ListeningRecap.formatCount(artist.listenCount) + listensWord(artist.listenCount),
                    null, artist.name));
        }
    }

    private void addTrackList(ListeningRecap result) {
        if (result.topTracks.isEmpty()) {
            return;
        }
        body.addView(Ui.sectionHeader(host.context(), "Top songs"));
        for (int i = 0; i < result.topTracks.size(); i++) {
            final ListeningRecap.Track track = result.topTracks.get(i);
            String detail = track.artist;
            if (detail.length() == 0) {
                detail = track.album;
            }
            body.addView(rankRow(i + 1, track.coverUrl, track.title, detail,
                    ListeningRecap.formatCount(track.listenCount) + listensWord(track.listenCount),
                    new Runnable() {
                        @Override
                        public void run() {
                            hide();
                            host.openSearch(track.title + " "
                                    + (track.artist.length() > 0 ? track.artist : ""));
                        }
                    }, track.title));
        }
    }

    private void addReleaseList(ListeningRecap result) {
        if (result.topReleases.isEmpty()) {
            return;
        }
        body.addView(Ui.sectionHeader(host.context(), "Top albums"));
        for (int i = 0; i < result.topReleases.size(); i++) {
            final ListeningRecap.Release release = result.topReleases.get(i);
            body.addView(rankRow(i + 1, release.coverUrl, release.title, release.artist,
                    ListeningRecap.formatCount(release.listenCount) + listensWord(release.listenCount),
                    new Runnable() {
                        @Override
                        public void run() {
                            hide();
                            host.openSearch(release.title + " " + release.artist);
                        }
                    }, release.title));
        }
    }

    private void addGenreList(ListeningRecap result) {
        if (result.topGenres.isEmpty()) {
            return;
        }
        body.addView(Ui.sectionHeader(host.context(), "Top genres"));
        for (int i = 0; i < result.topGenres.size(); i++) {
            ListeningRecap.Genre genre = result.topGenres.get(i);
            String detail = genre.percent > 0.0
                    ? String.format(java.util.Locale.US, "%.1f%% of your listens", genre.percent)
                    : "" ;
            body.addView(rankRow(i + 1, "", capitalise(genre.name), detail,
                    ListeningRecap.formatCount(genre.listenCount) + listensWord(genre.listenCount),
                    null, genre.name));
        }
    }

    private LinearLayout rankRow(int rank, String coverUrl, String title, String detail,
                                 String trailing, final Runnable onClick,
                                 String contentDescription) {
        LinearLayout row = Ui.row(host.context());
        row.setPadding(dp(8), dp(8), dp(10), dp(8));
        row.setBackground(Ui.ripple(host.context(), Theme.SURFACE, 12f));

        TextView rankView = Ui.text(host.context(), String.valueOf(rank), 12f,
                rank == 1 ? Theme.ACCENT : Theme.TEXT_FAINT, true);
        rankView.setGravity(Gravity.CENTER);
        row.addView(rankView, new LinearLayout.LayoutParams(dp(20),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (coverUrl.length() > 0) {
            NetworkImage cover = new NetworkImage(host.context());
            cover.setRadiusDp(6f);
            cover.setContentDescription(contentDescription);
            LinearLayout.LayoutParams coverParams =
                    new LinearLayout.LayoutParams(dp(42), dp(42));
            coverParams.leftMargin = dp(4);
            coverParams.rightMargin = dp(10);
            row.addView(cover, coverParams);
            cover.load(coverUrl);
        } else {
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(14),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            gap.leftMargin = dp(4);
            row.addView(new View(host.context()), gap);
        }

        LinearLayout info = Ui.column(host.context());
        TextView titleView = Ui.text(host.context(), title, 14f, Theme.TEXT, true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(titleView);
        if (detail.length() > 0) {
            TextView detailView = Ui.text(host.context(), detail, 12f, Theme.TEXT_DIM, false);
            detailView.setMaxLines(1);
            detailView.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(detailView);
        }
        row.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView trailingView = Ui.text(host.context(), trailing, 11f, Theme.TEXT_FAINT, false);
        trailingView.setGravity(Gravity.END);
        row.addView(trailingView);

        row.setContentDescription(title + ", " + trailing);
        if (onClick != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onClick.run();
                }
            });
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(2);
        row.setLayoutParams(params);
        return row;
    }

    private View message(String text, String actionLabel, final Runnable action) {
        LinearLayout wrapper = Ui.column(host.context());
        wrapper.setPadding(dp(18), dp(20), dp(18), dp(20));
        wrapper.setBackground(Ui.rounded(host.context(), Theme.SURFACE, 16f));
        TextView body = Ui.text(host.context(), text, 13f, Theme.TEXT_DIM, false);
        body.setLineSpacing(dp(3), 1f);
        wrapper.addView(body);
        if (actionLabel != null && action != null) {
            LinearLayout button = Ui.row(host.context());
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(18), dp(10), dp(18), dp(10));
            button.setBackground(Ui.ripple(host.context(), Theme.SURFACE_HIGH, 20f));
            button.addView(Ui.text(host.context(), actionLabel, 13f, Theme.ACCENT, true));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(14);
            button.setLayoutParams(params);
            button.setClickable(true);
            button.setFocusable(true);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    action.run();
                }
            });
            wrapper.addView(button);
        }
        return wrapper;
    }

    private void share() {
        if (recap == null || recap.isEmpty()) {
            host.showToast("There is no recap to share yet.");
            return;
        }
        host.shareText("Share your " + recap.year + " in review", recap.summaryText());
    }

    /* ================================================================ helpers */

    private static String listensWord(long count) {
        return count == 1L ? " listen" : " listens";
    }

    private static String capitalise(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private LinearLayout.LayoutParams halfWeight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = dp(8);
        return params;
    }

    private int dp(int value) {
        return Ui.dp(host.context(), value);
    }
}
