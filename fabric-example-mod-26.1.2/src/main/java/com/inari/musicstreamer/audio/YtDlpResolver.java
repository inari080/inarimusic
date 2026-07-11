package com.inari.musicstreamer.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * yt-dlp をサブプロセスとして呼び出し、プレイリスト展開・音声ストリームURL解決を行う。
 *
 * 前提: yt-dlp と ffmpeg が PATH 上にあること (もしくは executablePath を絶対パスに変更すること)。
 * 参考: https://github.com/yt-dlp/yt-dlp
 *
 * 注意: YouTube利用規約上、個人的なローカル再生の範囲で使うこと。恒久保存/再配布は避ける。
 */
public class YtDlpResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("musicstreamer");

    /** yt-dlp実行ファイル名。環境によって "yt-dlp" or "yt-dlp.exe" フルパス指定に変更可 */
    private final String ytDlpExecutable;

    public YtDlpResolver() {
        this("yt-dlp");
    }

    public YtDlpResolver(String ytDlpExecutable) {
        this.ytDlpExecutable = ytDlpExecutable;
    }

    /**
     * プレイリストURL(または単曲URL)を展開してトラック一覧を返す。
     * --flat-playlist を使うため高速だが、durationは取得できない(-1になる)。
     * 単曲URLを渡した場合は要素数1のリストが返る。
     */
    public List<TrackInfo> resolvePlaylist(String playlistUrl) throws IOException, InterruptedException {
        List<String> lines = runAndCaptureLines(List.of(
                ytDlpExecutable,
                "--flat-playlist",
                "--print", "%(id)s;;%(title)s",
                playlistUrl
        ), 30);

        List<TrackInfo> tracks = new ArrayList<>();
        for (String line : lines) {
            int sep = line.indexOf(";;");
            if (sep < 0) continue;
            String id = line.substring(0, sep).trim();
            String title = line.substring(sep + 2).trim();
            if (!id.isEmpty()) {
                tracks.add(TrackInfo.of(id, title.isEmpty() ? id : title));
            }
        }
        LOGGER.info("Resolved {} tracks from {}", tracks.size(), playlistUrl);
        return tracks;
    }

    /**
     * 指定トラックのベスト音声ストリームの直リンクURLを取得する。
     * 再生直前に呼ぶこと(URLは短時間で失効するため事前に一括取得しない)。
     */
    public String resolveAudioStreamUrl(String videoUrl) throws IOException, InterruptedException {
        List<String> lines = runAndCaptureLines(List.of(
                ytDlpExecutable,
                "-f", "bestaudio",
                "-g",
                videoUrl
        ), 20);
        if (lines.isEmpty()) {
            throw new IOException("yt-dlp returned no stream URL for " + videoUrl);
        }
        // -g は複数フォーマットが該当する場合複数行返すことがあるため最後の行(通常最適)を使う
        return lines.get(lines.size() - 1).trim();
    }

    /** 動画/トラックの正式なタイトルと再生時間(秒)を取得する(単曲解決時のみ使用推奨、重いので多用しない) */
    public TrackInfo resolveTrackMeta(String videoUrl, String videoId) throws IOException, InterruptedException {
        List<String> lines = runAndCaptureLines(List.of(
                ytDlpExecutable,
                "--print", "%(title)s;;%(duration)s",
                videoUrl
        ), 20);
        if (lines.isEmpty()) {
            return TrackInfo.of(videoId, videoId);
        }
        String line = lines.get(0);
        int sep = line.lastIndexOf(";;");
        if (sep < 0) {
            return TrackInfo.of(videoId, line.trim());
        }
        String title = line.substring(0, sep).trim();
        int duration;
        try {
            duration = Integer.parseInt(line.substring(sep + 2).trim());
        } catch (NumberFormatException e) {
            duration = -1;
        }
        return TrackInfo.of(videoId, title).withDuration(duration);
    }

    private List<String> runAndCaptureLines(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false); // stderrは進捗ログ等が混ざるので分離
        Process process = pb.start();

        List<String> outLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outLines.add(line);
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("yt-dlp timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            LOGGER.warn("yt-dlp exited with code {} for command: {}", process.exitValue(), String.join(" ", command));
        }
        return outLines;
    }
}