package com.soniq.base.model;

import org.junit.Assert;
import org.junit.Test;

public final class MediaItemTest {

    @Test
    public void artistIsBrowsableNotPlayable() {
        MediaItem artist = new MediaItem();
        artist.title = "Some Artist";
        artist.kind = MediaItem.KIND_ARTIST;
        artist.id = "UCabcdefghijklmnopqrstuv";
        Assert.assertTrue(artist.isArtist());
        Assert.assertTrue(artist.isBrowsable());
        Assert.assertFalse(artist.isPlayable());
        Assert.assertEquals("artist:UCabcdefghijklmnopqrstuv", artist.identity());
        // A browsable item's title is its primary line; the subtitle is just the kind.
        Assert.assertEquals("ARTIST", artist.subtitle());
        artist.author = " singer";
        Assert.assertEquals("ARTIST ·  singer", artist.subtitle());
    }

    @Test
    public void albumAndPlaylistAreCollections() {
        MediaItem album = new MediaItem();
        album.title = "Album";
        album.kind = MediaItem.KIND_ALBUM;
        album.id = "MPREb_12345";
        Assert.assertTrue(album.isCollection());
        Assert.assertEquals("album:MPREb_12345", album.identity());

        MediaItem playlist = new MediaItem();
        playlist.kind = MediaItem.KIND_PLAYLIST;
        playlist.id = "VLabc123";
        Assert.assertTrue(playlist.isPlaylist());
        Assert.assertFalse(playlist.isLocalPlaylist());
        Assert.assertEquals("playlist:VLabc123", playlist.identity());
    }

    @Test
    public void localPlaylistIsRecognisedByItsIdPrefix() {
        MediaItem playlist = new MediaItem();
        playlist.kind = MediaItem.KIND_PLAYLIST;
        playlist.id = MediaItem.LOCAL_PLAYLIST_PREFIX + "1234";
        Assert.assertTrue(playlist.isLocalPlaylist());
    }

    @Test
    public void trackIsPlayableAndIdentifiedByItsId() {
        MediaItem song = new MediaItem();
        song.title = "Song";
        song.id = "abcdefghijk";
        Assert.assertTrue(song.isPlayable());
        Assert.assertFalse(song.isBrowsable());
        Assert.assertEquals("track:abcdefghijk", song.identity());
    }

    @Test
    public void offlineTrackIsPlayableWithoutAnId() {
        MediaItem offline = new MediaItem();
        offline.title = "Offline";
        offline.localUri = "content://media/external/audio/media/1";
        Assert.assertTrue(offline.isPlayable());
        Assert.assertTrue(offline.isOffline());
        Assert.assertEquals("local:content://media/external/audio/media/1", offline.identity());
    }

    @Test
    public void emptyItemIsNeitherPlayableNorBrowsable() {
        MediaItem empty = new MediaItem();
        Assert.assertFalse(empty.isPlayable());
        Assert.assertFalse(empty.isBrowsable());
        Assert.assertEquals("", empty.identity());
    }

    @Test
    public void subtitleCombinesAuthorAlbumAndDuration() {
        MediaItem song = new MediaItem();
        song.author = "Artist";
        song.album = "Album";
        song.durationText = "3:21";
        Assert.assertEquals("Artist  •  Album  •  3:21", song.subtitle());
    }

    @Test
    public void durationTextFollowsSeconds() {
        MediaItem song = new MediaItem();
        song.setDurationSec(201L);
        Assert.assertEquals(201L, song.durationSec);
        Assert.assertEquals("3:21", song.durationText);
        song.setDurationSec(3725L);
        Assert.assertEquals("1:02:05", song.durationText);
        song.setDurationSec(-5L);
        Assert.assertEquals(0L, song.durationSec);
        Assert.assertEquals("0:00", song.durationText);
    }

    @Test
    public void copyIsIndependent() {
        MediaItem song = new MediaItem();
        song.id = "abc";
        song.title = "Title";
        MediaItem copy = song.copy();
        copy.title = "Changed";
        copy.localUri = "content://x";
        Assert.assertEquals("Title", song.title);
        Assert.assertEquals("", song.localUri);
        Assert.assertEquals("abc", copy.id);
    }
}
