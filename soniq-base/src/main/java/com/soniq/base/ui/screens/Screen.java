package com.soniq.base.ui.screens;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.soniq.base.ui.Host;
import com.soniq.base.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/** Base for the scrollable tab screens. Subclasses rebuild their body on refresh. */
public abstract class Screen {

    protected final Host host;
    private final ScrollView scroll;
    protected final LinearLayout content;
    private final List<Future<?>> requests = new ArrayList<>();
    private volatile boolean released;

    protected Screen(Host host) {
        this.host = host;
        Context context = host.context();
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = Ui.column(context);
        content.setPadding(Ui.dp(context, 16), Ui.dp(context, 12),
                Ui.dp(context, 16), Ui.dp(context, 24));
        scroll.addView(content, new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public View view() {
        return scroll;
    }

    public void refresh() {
        if (released || !host.isAlive()) {
            return;
        }
        content.removeAllViews();
        build(content);
    }

    protected abstract void build(LinearLayout container);

    /** Runs an owned request and cancels it when this screen's host is destroyed. */
    protected final Future<?> runIo(Runnable runnable) {
        if (released || !host.isAlive()) {
            return null;
        }
        synchronized (requests) {
            for (int i = requests.size() - 1; i >= 0; i--) {
                if (requests.get(i).isDone()) {
                    requests.remove(i);
                }
            }
            try {
                Future<?> request = host.io().submit(new Runnable() {
                    @Override
                    public void run() {
                        if (!released && host.isAlive()) {
                            runnable.run();
                        }
                    }
                });
                requests.add(request);
                return request;
            } catch (RejectedExecutionException ignored) {
                return null;
            }
        }
    }

    protected final void post(Runnable runnable) {
        if (released || !host.isAlive()) {
            return;
        }
        host.main().post(new Runnable() {
            @Override
            public void run() {
                if (!released && host.isAlive()) {
                    runnable.run();
                }
            }
        });
    }

    /** Called by the host activity when its view hierarchy is no longer usable. */
    public void release() {
        released = true;
        synchronized (requests) {
            for (Future<?> request : requests) {
                request.cancel(true);
            }
            requests.clear();
        }
    }

    protected final int dp(int value) {
        return Ui.dp(host.context(), value);
    }
}
