package com.soniq.base.ui;

import com.soniq.base.R;

/** Bottom navigation destinations. */
public enum Tab {
    HOME("Home", R.drawable.soniq_ic_home),
    SEARCH("Search", R.drawable.soniq_ic_search),
    LIBRARY("Library", R.drawable.soniq_ic_library),
    SETTINGS("Settings", R.drawable.soniq_ic_settings);

    public final String label;
    public final int icon;

    Tab(String label, int icon) {
        this.label = label;
        this.icon = icon;
    }
}
