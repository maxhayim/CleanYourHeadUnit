package io.github.luyzfernando08.tvstatic;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/** "Off the air": the tube opens, snow and hiss for a moment, then the picture underneath fades in. */
public class StaticActivity extends Activity {
    private static final int RATE = 22050;
    private AudioTrack hiss;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StaticView view = new StaticView(this, Prefs.seconds(this), this::finish);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(view);
        hiss = Prefs.sound(this) ? makeHiss(view.snowMs) : null;
        if (hiss != null) view.postDelayed(() -> {
            if (hiss != null) hiss.play();
        }, StaticView.OPEN_MS);
    }

    /** White noise for the snow's length, shaped so it starts and dies away with the picture. */
    private static AudioTrack makeHiss(int snowMs) {
        int samples = RATE * (snowMs + StaticView.FADE_MS) / 1000;
        short[] pcm = new short[samples];
        int fadeIn = RATE / 20, fadeOut = RATE * StaticView.FADE_MS / 1000;
        long seed = System.nanoTime() | 1;
        for (int i = 0; i < samples; i++) {
            seed ^= seed << 13;
            seed ^= seed >>> 7;
            seed ^= seed << 17;
            float gain = 0.3f;
            if (i < fadeIn) gain *= i / (float) fadeIn;
            if (i > samples - fadeOut) gain *= (samples - i) / (float) fadeOut;
            pcm[i] = (short) ((int) seed * gain / 65536f);
        }
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples * 2)
                    .build();
            track.write(pcm, 0, samples);
            return track;
        } catch (RuntimeException e) {
            return null;             // no sound is fine; the picture still plays
        }
    }

    /** Any key ends it at once, and is not passed on. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        if (hiss != null) {
            hiss.release();
            hiss = null;
        }
        super.onDestroy();
    }
}
