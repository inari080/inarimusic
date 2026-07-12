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

    /** yt-dlpの実行ファイルをダウンロードし、保存先の絶対パスを返す(全OS対応)。 */
    public static String downloadYtDlp() throws IOException, InterruptedException {
        Files.createDirectories(BIN_DIR);
        String asset = ytDlpAssetName();
        Path dest = BIN_DIR.resolve(asset);
        downloadFile("https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + asset, dest);
        dest.toFile().setExecutable(true);
        return dest.toAbsolutePath().toString();
    }

    /** ffmpeg (Windows専用、静的ビルドzipを展開してffmpeg.exeを取り出す)。 */
    public static String downloadFfmpegWindows() throws IOException, InterruptedException {
        Files.createDirectories(BIN_DIR);
        Path zipPath = BIN_DIR.resolve("ffmpeg-essentials.zip");
        downloadFile("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip", zipPath);

        Path exeDest = BIN_DIR.resolve("ffmpeg.exe");
        boolean found = false;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // zip内は "ffmpeg-x.y-essentials_build/bin/ffmpeg.exe" のような構造
                if (entry.getName().replace('\\', '/').endsWith("/bin/ffmpeg.exe")) {
                    Files.copy(zis, exeDest, StandardCopyOption.REPLACE_EXISTING);
                    found = true;
                    break;
                }
            }
        }
        Files.deleteIfExists(zipPath);
        if (!found) {
            throw new IOException("zip内にffmpeg.exeが見つかりませんでした(配布元の構成が変わった可能性があります)");
        }
        return exeDest.toAbsolutePath().toString();
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