package com.soniq.base.data;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The four lyrics outcomes must stay distinct — that is the whole feature. */
public class LyricsResultTest {

    @Test
    public void foundCarriesLinesAndSyncFlag() {
        List<LyricLine> lines = new ArrayList<>();
        lines.add(new LyricLine(1000, "line one"));
        LyricsResult result = LyricsResult.found(lines, true);
        Assert.assertEquals(LyricsResult.Status.FOUND, result.status);
        Assert.assertTrue(result.hasLyrics());
        Assert.assertTrue(result.synced);
        Assert.assertEquals(1, result.lines.size());
        Assert.assertFalse(result.isRetryable());
    }

    @Test
    public void emptyFoundIsNotLyrics() {
        LyricsResult result = LyricsResult.found(new ArrayList<LyricLine>(), false);
        Assert.assertFalse(result.hasLyrics());
    }

    @Test
    public void instrumentalIsNotAFailure() {
        LyricsResult result = LyricsResult.instrumental();
        Assert.assertEquals(LyricsResult.Status.INSTRUMENTAL, result.status);
        Assert.assertFalse(result.hasLyrics());
        Assert.assertFalse(result.isRetryable());
        Assert.assertTrue(result.message().toLowerCase().contains("instrumental"));
    }

    @Test
    public void notFoundIsAPermanentAnswer() {
        LyricsResult result = LyricsResult.notFound();
        Assert.assertEquals(LyricsResult.Status.NOT_FOUND, result.status);
        Assert.assertFalse(result.hasLyrics());
        // A missing song will still be missing next time: do not offer retry.
        Assert.assertFalse(result.isRetryable());
        Assert.assertTrue(result.message().contains("No lyrics"));
    }

    @Test
    public void unavailableIsRetryableAndExplainsItself() {
        LyricsResult result = LyricsResult.unavailable("Network unreachable");
        Assert.assertEquals(LyricsResult.Status.UNAVAILABLE, result.status);
        Assert.assertTrue(result.isRetryable());
        Assert.assertEquals("Network unreachable", result.message());
    }

    @Test
    public void unavailableFallsBackToAGenericSentence() {
        Assert.assertEquals("Lyrics unavailable", LyricsResult.unavailable(null).message());
        Assert.assertEquals("Lyrics unavailable", LyricsResult.unavailable("  ").message());
    }

    @Test
    public void timingsAreDetectedFromAnyTimedLine() {
        List<LyricLine> untimed = new ArrayList<>();
        untimed.add(new LyricLine(-1, "no timestamp"));
        Assert.assertFalse(LyricsRepository.hasTimings(untimed));
        Assert.assertFalse(LyricsRepository.hasTimings(null));

        List<LyricLine> mixed = new ArrayList<>();
        mixed.add(new LyricLine(-1, "header"));
        mixed.add(new LyricLine(2_000, "timed"));
        Assert.assertTrue(LyricsRepository.hasTimings(mixed));
    }

    @Test
    public void repositoryTrustsTheProviderOverTheSearch() {
        LyricsRepository repository = new LyricsRepository(new LyricsApi());
        LyricsResult result = repository.lookup("Song", "Artist",
                com.soniq.base.model.LyricsState.INSTRUMENTAL);
        Assert.assertEquals(LyricsResult.Status.INSTRUMENTAL, result.status);
        // Provider answers are not cached: they are not a search result.
        Assert.assertEquals(0, repository.size());
    }

    @Test
    public void cacheRefusesToRememberFailures() {
        LyricsRepository repository = new LyricsRepository(new LyricsApi());
        String key = LyricsRepository.cacheKey("Song", "Artist");
        repository.put(key, LyricsResult.unavailable("timeout"));
        Assert.assertNull(repository.get(key));
        repository.put(key, LyricsResult.instrumental());
        Assert.assertNull(repository.get(key));
        repository.put(key, LyricsResult.notFound());
        Assert.assertNotNull(repository.get(key));
    }

    @Test
    public void cacheKeepsNewestFirstAndIsBounded() {
        LyricsRepository repository = new LyricsRepository(new LyricsApi());
        for (int i = 0; i < LyricsRepository.CACHE_LIMIT + 5; i++) {
            repository.put(LyricsRepository.cacheKey("Song " + i, "Artist"),
                    LyricsResult.notFound());
        }
        Assert.assertEquals(LyricsRepository.CACHE_LIMIT, repository.size());
        Assert.assertNotNull(repository.get(LyricsRepository.cacheKey(
                "Song " + (LyricsRepository.CACHE_LIMIT + 4), "Artist")));
        Assert.assertNull(repository.get(LyricsRepository.cacheKey("Song 0", "Artist")));
    }

    @Test
    public void cacheKeyNormalizesWhitespaceAndCase() {
        Assert.assertEquals(LyricsRepository.cacheKey(" Song ", "Artist"),
                LyricsRepository.cacheKey("song", "artist"));
        Assert.assertEquals("", LyricsRepository.cacheKey("  ", "artist"));
        Assert.assertEquals("", LyricsRepository.cacheKey(null, null));
    }

    @Test
    public void clearEmptiesTheCache() {
        LyricsRepository repository = new LyricsRepository(new LyricsApi());
        repository.put(LyricsRepository.cacheKey("A", "B"), LyricsResult.notFound());
        Assert.assertEquals(1, repository.size());
        repository.clear();
        Assert.assertEquals(0, repository.size());
    }
}
