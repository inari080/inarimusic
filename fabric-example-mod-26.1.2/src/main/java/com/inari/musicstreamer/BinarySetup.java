package com.inari.musicstreamer;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BinarySetup {
    private static final Path BIN_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("musicstreamer/bin");

    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private static String ytDlpAssetName() {
        if (isWindows()) return "yt-dlp.exe";
        if (isMac()) return "yt-dlp_macos";
        return "yt-dlp";
    }

    /** @param onProgress 0〜100の進捗パーセントを受け取るコールバック(不要ならnull可) */
    public static String downloadYtDlp(IntConsumer onProgress) throws IOException, InterruptedException {
        Files.createDirectories(BIN_DIR);
        String asset = ytDlpAssetName();
        Path dest = BIN_DIR.resolve(asset);
        downloadFile("https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + asset, dest, onProgress);
        dest.toFile().setExecutable(true);
        return dest.toAbsolutePath().toString();
    }

    public static String downloadFfmpegWindows(IntConsumer onProgress) throws IOException, InterruptedException {
        Files.createDirectories(BIN_DIR);
        Path zipPath = BIN_DIR.resolve("ffmpeg-essentials.zip");
        downloadFile("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip", zipPath, onProgress);

        Path exeDest = BIN_DIR.resolve("ffmpeg.exe");
        boolean found = false;
        try {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().replace('\\', '/').endsWith("/bin/ffmpeg.exe")) {
                        Files.copy(zis, exeDest, StandardCopyOption.REPLACE_EXISTING);
                        found = true;
                        break;
                    }
                }
            }
        } finally {
            Files.deleteIfExists(zipPath);
        }
        if (!found) {
            throw new IOException("zip内にffmpeg.exeが見つかりませんでした(配布元の構成が変わった可能性があります)");
        }
        return exeDest.toAbsolutePath().toString();
    }

    public static boolean isYtDlpWorking(String path) {
        return commandWorks(java.util.List.of(path, "--version"));
    }

    public static boolean isFfmpegWorking(String path) {
        return commandWorks(java.util.List.of(path, "-version"));
    }

    private static boolean commandWorks(java.util.List<String> command) {
        if (command.get(0) == null || command.get(0).isBlank()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (InputStream in = process.getInputStream()) {
                in.readAllBytes();
            }
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** ストリームで読みながらファイルに書き込み、受信バイト数から進捗%を計算してコールバックする */
    private static void downloadFile(String url, Path dest, IntConsumer onProgress)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", BROWSER_UA)
                .GET()
                .build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("ダウンロード失敗 (HTTP " + response.statusCode() + "): " + url);
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long downloaded = 0;
        int lastPercent = -1;

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (total > 0 && onProgress != null) {
                    int percent = (int) (downloaded * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        onProgress.accept(percent);
                    }
                }
            }
        }

        long size = Files.size(dest);
        if (size < 1_000_000) { // 1MB未満はエラーページ等の誤取得とみなす
            Files.deleteIfExists(dest);
            throw new IOException("ダウンロードしたファイルが小さすぎます(" + size
                    + "バイト)。配布元がブロックしている可能性があります: " + url);
        }
    }
}