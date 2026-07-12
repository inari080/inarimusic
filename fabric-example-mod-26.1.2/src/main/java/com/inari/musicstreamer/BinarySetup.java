package com.inari.musicstreamer;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * yt-dlp / ffmpeg をユーザーが手動インストールしなくて済むように、
 * 公式配布元から実行ファイルを自動ダウンロードするユーティリティ。
 * 保存先: .minecraft/config/musicstreamer/bin/
 */
public final class BinarySetup {
    private static final Path BIN_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("musicstreamer/bin");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL) // GitHubのlatest/downloadはリダイレクトするため必須
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

    /** 現在設定されているパスで yt-dlp が実際に動くか確認する */
    public static boolean isYtDlpWorking(String path) {
        return commandWorks(List.of(path, "--version"));
    }

    /** 現在設定されているパスで ffmpeg が実際に動くか確認する */
    public static boolean isFfmpegWorking(String path) {
        return commandWorks(List.of(path, "-version"));
    }

    private static boolean commandWorks(List<String> command) {
        if (command.get(0) == null || command.get(0).isBlank()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var in = process.getInputStream()) {
                in.readAllBytes();
            }
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** yt-dlpの実行ファイルをダウンロードし、保存先の絶対パスを返す(全OS対応)。 */
    public static String downloadYtDlp() throws IOException, InterruptedException {
        Files.createDirectories(BIN_DIR);
        String asset = ytDlpAssetName();
        Path dest = BIN_DIR.resolve(asset);
        downloadFile("https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + asset, dest);
        dest.toFile().setExecutable(true);
        return dest.toAbsolutePath().toString();
    }

    /** 各OSに合わせたffmpegの自動セットアップ。保存先の絶対パスを返す。 */
    public static String downloadFfmpeg() throws IOException, InterruptedException {
        if (isWindows()) {
            return downloadFfmpegWindows();
        } else if (isMac()) {
            return downloadFfmpegMac();
        } else {
            return downloadFfmpegLinux();
        }
    }

    /** ffmpeg (Windows専用、静的ビルドzipを展開してffmpeg.exeを取り出す)。 */
    private static String downloadFfmpegWindows() throws IOException, InterruptedException {
        Files.createDirectories(BIN_DIR);
        Path zipPath = BIN_DIR.resolve("ffmpeg-essentials.zip");
        downloadFile("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip", zipPath);

        Path exeDest = BIN_DIR.resolve("ffmpeg.exe");
        boolean found = false;
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
        Files.deleteIfExists(zipPath);
        if (!found) {
            throw new IOException("zip内にffmpeg.exeが見つかりませんでした。");
        }
        return exeDest.toAbsolutePath().toString();
    }

    /** ffmpeg (Mac専用、Evermeetの提供する信頼性の高い静的ビルドzipを展開)。 */
    private static String downloadFfmpegMac() throws IOException, InterruptedException {
        Files.createDirectories(BIN_DIR);
        Path zipPath = BIN_DIR.resolve("ffmpeg-mac.zip");
        // Evermeet.cxの最新安定版URL
        downloadFile("https://evermeet.cx", zipPath);

        Path dest = BIN_DIR.resolve("ffmpeg");
        boolean found = false;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // zip直下にそのまま ffmpeg が入っている
                if (entry.getName().equals("ffmpeg")) {
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                    found = true;
                    break;
                }
            }
        }
        Files.deleteIfExists(zipPath);
        if (!found) {
            throw new IOException("zip内にffmpegバイナリが見つかりませんでした。");
        }
        dest.toFile().setExecutable(true);
        return dest.toAbsolutePath().toString();
    }

    /** ffmpeg (Linux専用、johnvansickleの静的ビルド.tar.xzからOSのtarコマンドを使って展開)。 */
    private static String downloadFfmpegLinux() throws IOException, InterruptedException {
        Files.createDirectories(BIN_DIR);
        Path tarXzPath = BIN_DIR.resolve("ffmpeg-linux.tar.xz");
        // 汎用性の高いx86_64用静的ビルド
        downloadFile("https://johnvansickle.com", tarXzPath);

        Path dest = BIN_DIR.resolve("ffmpeg");

        // Java標準機能では .tar.xz の解凍が難しいため、Linux環境に必ずある OSの `tar` コマンドを利用
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "tar", "-xJf", tarXzPath.toString(),
                    "-C", BIN_DIR.toString(),
                    "--wildcards", "*/ffmpeg",
                    "--strip-components", "1"
            );
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                throw new IOException("tarコマンドによる展開に失敗しました。");
            }
        } catch (Exception e) {
            Files.deleteIfExists(tarXzPath);
            throw new IOException("Linux環境でのffmpeg展開に失敗しました: " + e.getMessage(), e);
        }

        Files.deleteIfExists(tarXzPath);

        if (!Files.exists(dest)) {
            throw new IOException("アーカイブ内にffmpegバイナリが見つかりませんでした。");
        }

        dest.toFile().setExecutable(true);
        return dest.toAbsolutePath().toString();
    }

    private static void downloadFile(String url, Path dest) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "MusicStreamerMod")
                .GET()
                .build();
        HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(dest));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("ダウンロード失敗 (HTTP " + response.statusCode() + "): " + url);
        }
    }
}
