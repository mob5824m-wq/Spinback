package com.soniq.base.playback;

import com.soniq.base.model.MediaItem;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PlaybackQueueTest {

    private static String id(int index) {
        return "video" + String.format("%06d", index);
    }

    private static MediaItem song(String id) {
        MediaItem item = new MediaItem();
        item.id = id;
        item.title = "Song " + id;
        return item;
    }

    private static List<MediaItem> songs(int count) {
        List<MediaItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(song(id(i)));
        }
        return items;
    }

    @Test
    public void replaceCurrentSwapsMetadataButKeepsOrderAndRejectsOtherIds() {
        PlaybackQueue queue = new PlaybackQueue(new Random(1));
        queue.setItems(songs(3));
        queue.playAt(1);
        MediaItem corrected = queue.current().copy();
        corrected.author = "Someone";
        Assert.assertTrue(queue.replaceCurrent(corrected));
        Assert.assertEquals("Someone", queue.current().author);
        Assert.assertEquals(id(1), queue.current().id);
        Assert.assertEquals(id(2), queue.next(true).id);
        Assert.assertFalse(queue.replaceCurrent(song(id(0))));
        Assert.assertEquals(id(2), queue.current().id);
        PlaybackQueue empty = new PlaybackQueue(new Random(1));
        Assert.assertFalse(empty.replaceCurrent(song(id(0))));
    }

    @Test
    public void nextAdvancesInOrderAndStopsAtTheEnd() {
        PlaybackQueue queue = new PlaybackQueue(new Random(1));
        queue.setItems(songs(3));
        queue.playAt(0);
        Assert.assertEquals(id(0), queue.current().id);
        Assert.assertEquals(id(1), queue.next(true).id);
        Assert.assertEquals(id(2), queue.next(true).id);
        Assert.assertNull(queue.next(true));
        Assert.assertEquals(id(2), queue.current().id);
    }

    @Test
    public void repeatAllWrapsAround() {
        PlaybackQueue queue = new PlaybackQueue(new Random(1));
        queue.setItems(songs(3));
        queue.setRepeat(PlaybackQueue.REPEAT_ALL);
        queue.playAt(2);
        Assert.assertEquals(id(0), queue.next(false).id);
    }

    @Test
    public void repeatOneOnlyRepeatsOnNaturalAdvance() {
        PlaybackQueue queue = new PlaybackQueue(new Random(1));
        queue.setItems(songs(3));
        queue.setRepeat(PlaybackQueue.REPEAT_ONE);
        queue.playAt(0);
        Assert.assertEquals(id(0), queue.next(false).id);
        Assert.assertEquals(id(1), queue.next(true).id);
    }

    @Test
    public void previousStepsBackAndStaysAtTheStart() {
        PlaybackQueue queue = new PlaybackQueue(new Random(1));
        queue.setItems(songs(3));
        queue.playAt(1);
        Assert.assertEquals(id(0), queue.previous(true).id);
        Assert.assertEquals(id(0), queue.previous(true).id);
    }

    @Test
    public void shuffleKeepsTheCurrentSongFirst() {
        PlaybackQueue queue = new PlaybackQueue(new Random(7));
        List<MediaItem> items = songs(8);
        queue.setItems(items);
        queue.playAt(5);
        queue.setShuffle(true);
        List<MediaItem> ordered = queue.ordered();
        Assert.assertEquals(8, ordered.size());
        Assert.assertEquals(id(5), ordered.get(0).id);
        Assert.assertEquals(id(5), queue.current().id);
    }

    @Test
    public void removeAdjustsTheRemainingOrder() {
        PlaybackQueue queue = new PlaybackQueue(new Random(1));
        queue.setItems(songs(3));
        queue.playAt(1);
        queue.remove(0);
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(id(1), queue.current().id);
        Assert.assertEquals(id(1), queue.ordered().get(0).id);
        Assert.assertEquals(id(2), queue.ordered().get(1).id);
    }

    @Test
    public void setItemsFiltersOutBrowsableEntries() {
        MediaItem artist = new MediaItem();
        artist.title = "Artist";
        artist.kind = MediaItem.KIND_ARTIST;
        artist.id = "UCabcdefghijklmnopqrstuv";
        List<MediaItem> items = new ArrayList<>();
        items.add(artist);
        items.add(song(id(0)));
        PlaybackQueue queue = new PlaybackQueue(new Random(1));
        queue.setItems(items);
        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(id(0), queue.current().id);
    }
}
