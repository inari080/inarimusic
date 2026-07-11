package com.inari.musicstreamer.audio;

/**
 * 1トラック分のメタ情報。
 * durationSeconds は不明な場合 -1 (flat-playlist取得時点では未確定なことが多い)。
 */
public record TrackInfo(String videoId, String title, String url, int durationSeconds) {

    public static TrackInfo of(String videoId, String title) {
        return new TrackInfo(videoId, title, "https://www.youtube.com/watch?v=" + videoId, -1);
    }

    public TrackInfo withDuration(int seconds) {
        return new TrackInfo(videoId, title, url, seconds);
    }
}