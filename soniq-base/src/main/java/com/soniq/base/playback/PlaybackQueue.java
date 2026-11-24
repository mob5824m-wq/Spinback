package com.soniq.base.playback;

import com.soniq.base.model.MediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Queue, shuffle and repeat logic, kept free of Android types so it can be
 * unit-tested on the JVM.
 *
 * <p>{@code order} maps playback position -> item index. Shuffle rebuilds the
 * order while keeping the current item first, so toggling shuffle never changes
 * the song that is playing.
 */
public final class PlaybackQueue {

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private final List<MediaItem> items = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private final Random random;

    private int position = -1;
    private boolean shuffle = false;
    private int repeat = REPEAT_OFF;

    public PlaybackQueue() {
        this(new Random());
    }

    public PlaybackQueue(Random random) {
        this.random = random == null ? new Random() : random;
    }

    public synchronized void setItems(List<MediaItem> source) {
        items.clear();
        if (source != null) {
            for (MediaItem item : source) {
                if (item != null && item.isPlayable()) {
                    items.add(item);
                }
            }
        }
        rebuildOrder(-1);
    }

    public synchronized void clear() {
        items.clear();
        order.clear();
        position = -1;
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    public synchronized List<MediaItem> items() {
        return new ArrayList<>(items);
    }

    /** Items in the order they will be played (the visible Up Next list). */
    public synchronized List<MediaItem> ordered() {
        List<MediaItem> out = new ArrayList<>();
        for (Integer index : order) {
            if (index >= 0 && index < items.size()) {
                out.add(items.get(index));
            }
        }
        return out;
    }

    public synchronized int position() {
        return position;
    }

    public synchronized int currentIndex() {
        if (position < 0 || position >= order.size()) {
            return -1;
        }
        int index = order.get(position);
        return index >= 0 && index < items.size() ? index : -1;
    }

    public synchronized MediaItem current() {
        int index = currentIndex();
        return index >= 0 ? items.get(index) : null;
    }

    /**
     * Replaces the current item in place — used to fold metadata the provider
     * corrected while resolving into the queued copy. {@code item} must carry
     * the same identity as the current one so queue order and history are
     * untouched. Returns false if nothing is current or the ids differ.
     */
    public synchronized boolean replaceCurrent(MediaItem item) {
        int index = currentIndex();
        if (index < 0 || item == null
                || !items.get(index).identity().equals(item.identity())) {
            return false;
        }
        items.set(index, item);
        return true;
    }

    public synchronized MediaItem at(int itemIndex) {
        return itemIndex >= 0 && itemIndex < items.size() ? items.get(itemIndex) : null;
    }

    public synchronized int indexOf(MediaItem item) {
        if (item == null) {
            return -1;
        }
        String identity = item.identity();
        if (identity.length() == 0) {
            return -1;
        }
        for (int i = 0; i < items.size(); i++) {
            if (identity.equals(items.get(i).identity())) {
                return i;
            }
        }
        return -1;
    }

    /** Starts playback at an item index (not a position in the shuffled order). */
    public synchronized void playAt(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= items.size()) {
            return;
        }
        int orderIndex = order.indexOf(Integer.valueOf(itemIndex));
        if (orderIndex < 0) {
            order.add(Integer.valueOf(itemIndex));
            orderIndex = order.size() - 1;
        }
        position = orderIndex;
    }

    /**
     * Advances. Returns null at the end of a non-repeating queue (the caller
     * keeps the last item selected).
     */
    public synchronized MediaItem next(boolean userInitiated) {
        if (items.isEmpty()) {
            return null;
        }
        if (repeat == REPEAT_ONE && !userInitiated) {
            return current();
        }
        if (position + 1 < order.size()) {
            position++;
        } else if (repeat == REPEAT_ALL) {
            position = 0;
        } else {
            position = order.size() - 1;
            return null;
        }
        return current();
    }

    public synchronized MediaItem previous(boolean userInitiated) {
        if (items.isEmpty()) {
            return null;
        }
        if (position > 0) {
            position--;
        } else if (repeat == REPEAT_ALL) {
            position = order.size() - 1;
        }
        return current();
    }

    public synchronized void setShuffle(boolean enabled) {
        if (shuffle == enabled) {
            return;
        }
        shuffle = enabled;
        rebuildOrder(currentIndex());
    }

    public synchronized boolean isShuffle() {
        return shuffle;
    }

    public synchronized void setRepeat(int mode) {
        repeat = ((mode % 3) + 3) % 3;
    }

    public synchronized int repeatMode() {
        return repeat;
    }

    public synchronized int cycleRepeat() {
        repeat = (repeat + 1) % 3;
        return repeat;
    }

    public synchronized void add(MediaItem item) {
        if (item == null || !item.isPlayable()) {
            return;
        }
        items.add(item);
        order.add(Integer.valueOf(items.size() - 1));
        if (position < 0) {
            position = 0;
        }
    }

    public synchronized void addAll(List<MediaItem> source) {
        if (source == null) {
            return;
        }
        for (MediaItem item : source) {
            add(item);
        }
    }

    public synchronized void remove(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= items.size()) {
            return;
        }
        int orderIndex = order.indexOf(Integer.valueOf(itemIndex));
        items.remove(itemIndex);
        if (orderIndex >= 0) {
            order.remove(orderIndex);
        }
        for (int i = 0; i < order.size(); i++) {
            int value = order.get(i);
            if (value > itemIndex) {
                order.set(i, Integer.valueOf(value - 1));
            }
        }
        if (order.isEmpty()) {
            position = -1;
            return;
        }
        if (orderIndex >= 0) {
            if (position >= order.size()) {
                position = order.size() - 1;
            } else if (orderIndex < position) {
                position--;
            }
        }
    }

    public synchronized int repeatIconState() {
        return repeat;
    }

    private void rebuildOrder(int keepItemIndex) {
        order.clear();
        if (items.isEmpty()) {
            position = -1;
            return;
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            indices.add(Integer.valueOf(i));
        }
        if (shuffle) {
            if (keepItemIndex >= 0 && keepItemIndex < items.size()) {
                indices.remove(Integer.valueOf(keepItemIndex));
                Collections.shuffle(indices, random);
                indices.add(0, Integer.valueOf(keepItemIndex));
            } else {
                Collections.shuffle(indices, random);
            }
        }
        order.addAll(indices);
        if (keepItemIndex >= 0) {
            position = order.indexOf(Integer.valueOf(keepItemIndex));
        }
        if (position < 0) {
            position = 0;
        }
    }
}
