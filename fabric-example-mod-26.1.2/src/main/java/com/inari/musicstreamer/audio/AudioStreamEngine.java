package com.inari.musicstreamer.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * 音声再生のコアエンジン。
 *
 * 構成:
 *   yt-dlp -g で直リンクURLを解決
 *   → ffmpeg でPCM(16bit/44.1kHz/stereo little-endian)にデコードしつつパイプ出力
 *   → javax.sound.sampled.SourceDataLine で再生
 *
 * Minecraft標準のSoundEngine(OpenAL)は使わず独立した再生系にしている。
 * 理由: 任意フォーマットのストリーミングデコード + シーク + 生PCMへのアクセス(波形/スペクトラム描画用)を
 * 素直に扱えるのがjavax.sound側であるため。
 *
 * スレッドセーフ性: play/pause/resume/seek/stop はどのスレッドから呼んでも良いが、
 * 内部で同期を取るため頻繁な連打は避けること(GUI側でデバウンス推奨)。
 */
public class AudioStreamEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("musicstreamer");

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNELS = 2;
    private static final int BYTES_PER_SAMPLE = 2; // 16bit
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE;
    private static final int READ_CHUNK_BYTES = 4096;

    /** 波形/スペクトラム描画用に保持する直近サンプル数(ステレオ2chをインターリーブしたshort配列) */
    private static final int VISUAL_BUFFER_SAMPLES = 4096;

    private final String ffmpegExecutable;
    private final YtDlpResolver resolver;

    private volatile PlaybackState state = PlaybackState.STOPPED;
    private volatile TrackInfo currentTrack;
    private volatile double startOffsetSeconds = 0;
    private volatile long bytesConsumed = 0;
    private volatile float volume = 1.0f; // 0.0 - 1.0

    private Process ffmpegProcess;
    private SourceDataLine line;
    private Thread pumpThread;
    private final Object lifecycleLock = new Object();

    // 波形/スペクトラム描画用のリングバッファ (short PCM interleaved)
    private final short[] visualRing = new short[VISUAL_BUFFER_SAMPLES];
    private int visualRingPos = 0;

    private Consumer<PlaybackState> onStateChanged;
    private Consumer<String> onError;

    public AudioStreamEngine(YtDlpResolver resolver) {
        this(resolver, "ffmpeg");
    }

    public AudioStreamEngine(YtDlpResolver resolver, String ffmpegExecutable) {
        this.resolver = resolver;
        this.ffmpegExecutable = ffmpegExecutable;
    }

    public void setOnStateChanged(Consumer<PlaybackState> listener) {
        this.onStateChanged = listener;
    }

    public void setOnError(Consumer<String> listener) {
        this.onError = listener;
    }

    /** 指定トラックを最初から再生する。呼び出しスレッドをブロックしないよう内部で非同期実行する。 */
    public void play(TrackInfo track) {
        playFrom(track, 0);
    }

    /** 指定トラックを指定秒数から再生する(シーク用にも使う) */
    public void playFrom(TrackInfo track, double startSeconds) {
        Thread starter = new Thread(() -> startPlaybackBlocking(track, startSeconds), "musicstreamer-start");
        starter.setDaemon(true);
        starter.start();
    }

    private void startPlaybackBlocking(TrackInfo track, double startSeconds) {
        synchronized (lifecycleLock) {
            stopInternal();
            setState(PlaybackState.LOADING);
            this.currentTrack = track;
            this.startOffsetSeconds = Math.max(0, startSeconds);
            this.bytesConsumed = 0;

            try {
                String streamUrl = resolver.resolveAudioStreamUrl(track.url());
                launchFfmpegAndLine(streamUrl, this.startOffsetSeconds);
                setState(PlaybackState.PLAYING);
            } catch (Exception e) {
                LOGGER.error("Failed to start playback for {}", track.url(), e);
                setState(PlaybackState.ERROR);
                if (onError != null) onError.accept("再生開始に失敗: " + e.getMessage());
                stopInternal();
            }
        }
    }

    private void launchFfmpegAndLine(String streamUrl, double startSeconds) throws IOException, LineUnavailableException {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegExecutable,
                "-hide_banner", "-loglevel", "error",
                "-ss", String.valueOf(startSeconds),
                "-i", streamUrl,
                "-f", "s16le",
                "-ac", String.valueOf(CHANNELS),
                "-ar", String.valueOf(SAMPLE_RATE),
                "-vn",
                "-"
        );
        pb.redirectErrorStream(false);
        ffmpegProcess = pb.start();

        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE, 16, CHANNELS, CHANNELS * BYTES_PER_SAMPLE, SAMPLE_RATE, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        applyVolumeToLine();
        line.start();

        InputStream pcmIn = ffmpegProcess.getInputStream();
        pumpThread = new Thread(() -> pumpLoop(pcmIn), "musicstreamer-pump");
        pumpThread.setDaemon(true);
        pumpThread.start();

        // ffmpegのstderrはエラー診断用に別スレッドで読み捨て/ログ出力しておく(溜まるとプロセスが止まるため必須)
        Thread errDrain = new Thread(() -> drainStream(ffmpegProcess.getErrorStream()), "musicstreamer-ffmpeg-stderr");
        errDrain.setDaemon(true);
        errDrain.start();
    }

    private void pumpLoop(InputStream pcmIn) {
        byte[] buffer = new byte[READ_CHUNK_BYTES];
        try {
            int read;
            while ((read = pcmIn.read(buffer)) != -1) {
                if (state == PlaybackState.PAUSED) {
                    // pause中はラインに書かない。ffmpeg側はOSパイプが埋まるとブロックし、事実上デコードも止まる。
                    // ただし読み取り自体は続けないとブロッキングが崩れるため、短いスリープで待機。
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                if (line == null || !line.isOpen()) break;
                line.write(buffer, 0, read);
                bytesConsumed += read;
                pushToVisualRing(buffer, read);
            }
        } catch (IOException e) {
            if (state != PlaybackState.STOPPED) {
                LOGGER.warn("PCM pump loop ended with error", e);
            }
        } finally {
            if (state == PlaybackState.PLAYING) {
                // ストリーム終端(曲終わり)
                setState(PlaybackState.STOPPED);
            }
        }
    }

    private void drainStream(InputStream err) {
        try {
            byte[] buf = new byte[1024];
            while (err.read(buf) != -1) {
                // 進捗ログは基本捨てる。デバッグ時はここでLOGGER.debugする。
            }
        } catch (IOException ignored) {
        }
    }

    private void pushToVisualRing(byte[] pcmBytes, int len) {
        synchronized (visualRing) {
            for (int i = 0; i + 1 < len; i += 2) {
                short sample = (short) ((pcmBytes[i] & 0xFF) | (pcmBytes[i + 1] << 8));
                visualRing[visualRingPos] = sample;
                visualRingPos = (visualRingPos + 1) % VISUAL_BUFFER_SAMPLES;
            }
        }
    }

    /** 波形/スペクトラム描画用に直近サンプルのスナップショットを取得する(GUI描画スレッドから呼ぶ) */
    public short[] getVisualSnapshot() {
        short[] copy = new short[VISUAL_BUFFER_SAMPLES];
        synchronized (visualRing) {
            System.arraycopy(visualRing, visualRingPos, copy, 0, VISUAL_BUFFER_SAMPLES - visualRingPos);
            System.arraycopy(visualRing, 0, copy, VISUAL_BUFFER_SAMPLES - visualRingPos, visualRingPos);
        }
        return copy;
    }

    public void pause() {
        if (state != PlaybackState.PLAYING) return;
        setState(PlaybackState.PAUSED);
        if (line != null) line.stop();
    }

    public void resume() {
        if (state != PlaybackState.PAUSED) return;
        if (line != null) line.start();
        setState(PlaybackState.PLAYING);
    }

    /** 現在のトラックの再生位置(秒)を、シーク後の位置から再生を開始する形でやり直す */
    public void seekTo(double seconds) {
        TrackInfo track = this.currentTrack;
        if (track == null) return;
        playFrom(track, seconds);
    }

    /** 現在の推定再生位置(秒) */
    public double getPositionSeconds() {
        return startOffsetSeconds + (bytesConsumed / (double) BYTES_PER_SECOND);
    }

    public void setVolume(float volume01) {
        this.volume = Math.max(0f, Math.min(1f, volume01));
        applyVolumeToLine();
    }

    private void applyVolumeToLine() {
        if (line == null || !line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        // 線形音量(0..1) -> dB変換。0の場合はmin側にクランプ。
        float dB;
        if (volume <= 0.0001f) {
            dB = gainControl.getMinimum();
        } else {
            dB = (float) (Math.log10(volume) * 20.0);
            dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
        }
        gainControl.setValue(dB);
    }

    public void stop() {
        synchronized (lifecycleLock) {
            stopInternal();
            setState(PlaybackState.STOPPED);
        }
    }

    private void stopInternal() {
        if (ffmpegProcess != null) {
            ffmpegProcess.destroyForcibly();
            ffmpegProcess = null;
        }
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
        if (pumpThread != null) {
            pumpThread.interrupt();
            pumpThread = null;
        }
    }

    private void setState(PlaybackState newState) {
        this.state = newState;
        if (onStateChanged != null) onStateChanged.accept(newState);
    }

    public PlaybackState getState() {
        return state;
    }

    public TrackInfo getCurrentTrack() {
        return currentTrack;
    }
}