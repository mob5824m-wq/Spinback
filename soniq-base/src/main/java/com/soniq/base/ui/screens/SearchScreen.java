package com.soniq.base.ui.screens;

import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.data.SearchHistory;
import com.soniq.base.model.MediaItem;
import com.soniq.base.provider.MusicProvider;
import com.soniq.base.ui.A11y;
import com.soniq.base.ui.Host;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;
import com.soniq.base.ui.views.Chip;
import com.soniq.base.ui.views.TrackRow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** Catalogue search with song / artist / album / playlist filters. */
public final class SearchScreen extends Screen {

    private static final String[][] MODES = {
            {"Songs", MusicProvider.FILTER_SONGS},
            {"Artists", MusicProvider.FILTER_ARTISTS},
            {"Albums", MusicProvider.FILTER_ALBUMS},
            {"Playlists", MusicProvider.FILTER_PLAYLISTS}
    };

    private final List<MediaItem> results = new ArrayList<>();
    private String query = "";
    private String mode = MusicProvider.FILTER_SONGS;
    private boolean searching;
    private int request;
    private Future<?> searchTask;
    private LinearLayout resultsContainer;
    private TextView statusView;
    private Chip[] chips;
    private EditText input;
    private LinearLayout historyRow;
    private SearchHistory history;

    public SearchScreen(Host host) {
        super(host);
    }

    @Override
    protected void build(LinearLayout container) {
        container.addView(Ui.text(host.context(), "Search", 28f, Theme.TEXT, true));
        container.addView(Ui.text(host.context(),
                "Search " + host.provider().displayName(), 12f, Theme.TEXT_DIM, false));

        FrameLayout searchBox = new FrameLayout(host.context());
        searchBox.setBackground(searchBackground());
        int height = dp(48);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        boxParams.topMargin = dp(14);
        searchBox.setLayoutParams(boxParams);

        android.widget.ImageView searchIcon = new android.widget.ImageView(host.context());
        searchIcon.setImageResource(R.drawable.soniq_ic_search);
        searchIcon.setColorFilter(Theme.TEXT_DIM);
        searchIcon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(22), dp(22));
        iconParams.gravity = Gravity.CENTER_VERTICAL;
        iconParams.leftMargin = dp(14);
        searchBox.addView(searchIcon, iconParams);

        input = new EditText(host.context());
        input.setHint("Songs, artists, albums, playlists…");
        input.setContentDescription("Search songs, artists, albums, or playlists");
        input.setHintTextColor(Theme.TEXT_FAINT);
        input.setTextColor(Theme.TEXT);
        input.setTextSize(14f);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(46), 0, dp(14), 0);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setText(query);
        input.setSelection(input.getText().length());
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch();
                    return true;
                }
                return false;
            }
        });
        searchBox.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(searchBox);

        HorizontalScrollView chipScroll = new HorizontalScrollView(host.context());
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(0, dp(10), 0, dp(2));
        LinearLayout chipRow = Ui.row(host.context());
        chips = new Chip[MODES.length];
        for (int i = 0; i < MODES.length; i++) {
            final String value = MODES[i][1];
            Chip chip = new Chip(host.context(), MODES[i][0], new Runnable() {
                @Override
                public void run() {
                    mode = value;
                    updateChips();
                    if (query.length() > 0) {
                        performSearch();
                    }
                }
            });
            chip.setSelected(value.equals(mode));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.rightMargin = dp(8);
            chipRow.addView(chip, chipParams);
            chips[i] = chip;
        }
        chipScroll.addView(chipRow);
        container.addView(chipScroll);

        historyRow = Ui.column(host.context());
        container.addView(historyRow);
        renderHistory();

        statusView = Ui.text(host.context(), "", 12f, Theme.TEXT_DIM, false);
        statusView.setPadding(dp(4), dp(10), dp(4), dp(6));
        container.addView(statusView);

        resultsContainer = Ui.column(host.context());
        container.addView(resultsContainer);
        renderResults();
    }

    private android.graphics.drawable.GradientDrawable searchBackground() {
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(14));
        background.setColor(Theme.SURFACE_HIGH);
        background.setStroke(dp(1), Theme.OUTLINE);
        return background;
    }

    /**
     * Recent searches: tap to run again, long-press to forget one. Hidden
     * entirely when empty, so a first run stays clean.
     */
    private void renderHistory() {
        if (historyRow == null) {
            return;
        }
        historyRow.removeAllViews();
        if (history == null) {
            history = new SearchHistory(host.context());
        }
        final List<String> recent = history.recentQueries(6);
        if (recent.isEmpty()) {
            return;
        }
        LinearLayout header = Ui.row(host.context());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.text(host.context(), "Recent searches", 12f, Theme.TEXT_DIM, true);
        header.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = Ui.text(host.context(), "Clear", 12f, Theme.ACCENT, false);
        header.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        clear.setClickable(true);
        clear.setFocusable(true);
        clear.setMinimumHeight(dp(48));
        clear.setGravity(Gravity.CENTER_VERTICAL);
        A11y.label(clear, "Clear search history");
        clear.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                history.clear();
                renderHistory();
                host.showToast("Search history cleared");
            }
        });
        header.setPadding(0, dp(4), 0, dp(4));
        historyRow.addView(header);

        HorizontalScrollView scroll = new HorizontalScrollView(host.context());
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(host.context());
        for (int i = 0; i < recent.size(); i++) {
            final String value = recent.get(i);
            Chip chip = new Chip(host.context(), value, new Runnable() {
                @Override
                public void run() {
                    if (input != null) {
                        input.setText(value);
                        input.setSelection(input.getText().length());
                    }
                    performSearch();
                }
            });
            chip.setOnLongClickListener(new android.view.View.OnLongClickListener() {
                @Override
                public boolean onLongClick(android.view.View view) {
                    history.remove(value);
                    renderHistory();
                    return true;
                }
            });
            A11y.label(chip, "Search again for " + value);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(8);
            chips.addView(chip, params);
        }
        scroll.addView(chips);
        historyRow.addView(scroll);
    }

    /** Stores the query for next launch; failures never interrupt a search. */
    private void rememberQuery(String value) {
        try {
            if (history == null) {
                history = new SearchHistory(host.context());
            }
            history.record(value, System.currentTimeMillis());
            renderHistory();
        } catch (RuntimeException ignored) {
            // A full disk must not stop searching.
        }
    }

    private void updateChips() {
        if (chips == null) {
            return;
        }
        for (int i = 0; i < MODES.length && i < chips.length; i++) {
            chips[i].setSelected(MODES[i][1].equals(mode));
        }
    }

    /** Pre-fills the search box and runs a query (used by deep links / chips). */
    public void submit(String value) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        query = value.trim();
        if (input != null) {
            input.setText(query);
            input.setSelection(input.getText().length());
        }
        performSearch();
    }

    private void performSearch() {
        if (input == null || !host.isAlive()) {
            return;
        }
        String value = input.getText().toString().trim();
        if (value.length() == 0) {
            request++;
            if (searchTask != null) {
                searchTask.cancel(true);
                searchTask = null;
            }
            query = "";
            searching = false;
            results.clear();
            if (statusView != null) {
                statusView.setText("Enter a search term to begin.");
            }
            renderResults();
            host.showToast("Type a song, artist, album or playlist first.");
            return;
        }
        query = value;
        searching = true;
        rememberQuery(value);
        final int currentRequest = ++request;
        final String requestQuery = query;
        final String requestMode = mode;
        if (searchTask != null) {
            searchTask.cancel(true);
            searchTask = null;
        }
        results.clear();
        if (statusView != null) {
            statusView.setText("Searching " + requestMode + " for \"" + requestQuery + "\"…");
        }
        renderResults();
        searchTask = runIo(new Runnable() {
            @Override
            public void run() {
                List<MediaItem> foundValue = null;
                Exception failureValue = null;
                try {
                    foundValue = host.provider().search(requestQuery, requestMode);
                } catch (Exception error) {
                    failureValue = error;
                }
                final List<MediaItem> found = foundValue;
                final Exception failure = failureValue;
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (currentRequest != request) {
                            return;
                        }
                        searchTask = null;
                        searching = false;
                        results.clear();
                        if (failure == null && found != null) {
                            results.addAll(found);
                        }
                        if (statusView != null) {
                            if (failure != null) {
                                statusView.setText("Search unavailable: " + failure.getMessage());
                            } else {
                                statusView.setText(results.size() + " "
                                        + (results.size() == 1 ? "result" : "results")
                                        + " · " + requestMode);
                            }
                        }
                        renderResults();
                    }
                });
            }
        });
    }

    @Override
    public void release() {
        request++;
        if (searchTask != null) {
            searchTask.cancel(true);
            searchTask = null;
        }
        super.release();
    }

    private void renderResults() {
        if (resultsContainer == null) {
            return;
        }
        resultsContainer.removeAllViews();
        if (searching) {
            resultsContainer.addView(Ui.caption(host.context(), "Loading…"));
            return;
        }
        if (results.isEmpty()) {
            resultsContainer.addView(Ui.emptyState(host.context(),
                    "No results yet",
                    query.length() == 0
                            ? "Search for a song, artist, album or playlist."
                            : "Nothing matched this filter. Try Songs or another spelling."));
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            final int index = i;
            final MediaItem item = results.get(i);
            resultsContainer.addView(new TrackRow(host.context(), item, i, true, false,
                    new TrackRow.Callback() {
                        @Override
                        public void onClick(MediaItem clicked, int position) {
                            if (clicked.isArtist()) {
                                host.openArtist(clicked);
                            } else if (clicked.isCollection()) {
                                host.openCollection(clicked);
                            } else {
                                host.play(results, index);
                            }
                        }

                        @Override
                        public void onMore(android.view.View anchor,
                                            MediaItem clicked, int position) {
                            if (clicked.isBrowsable()) {
                                host.addToPlaylist(clicked);
                            } else {
                                host.showTrackMenu(clicked);
                            }
                        }

                        @Override
                        public void onLongClick(MediaItem clicked, int position) {
                            if (clicked.isBrowsable()) {
                                host.addToPlaylist(clicked);
                            } else {
                                host.addToPlaylist(clicked);
                            }
                        }
                    }));
        }
    }
}
