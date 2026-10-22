package io.github.luyzfernando08.tvstatic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/**
 * Snow drawn into a small bitmap and stretched over the screen without smoothing, so each dot stays a hard
 * block like an analogue set's. Timeline: the tube opens from a bright line, snow with a slow rolling band,
 * then the snow fades and the window, translucent, shows what is underneath.
 */
class StaticView extends View {
    static final int OPEN_MS = 300;
    static final int FADE_MS = 500;
    private static final int W = 240, H = 135;

    /** Snow time: the chosen length less the opening and the fade. */
    final int snowMs;
    private final Runnable done;
    private final Bitmap snow = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final int[] pixels = new int[W * H];
    private final Paint paint = new Paint();
    private final Paint fill = new Paint();
    private final Rect dst = new Rect();
    private long start;
    private long seed = System.nanoTime() | 1;
    private boolean finished;

    StaticView(Context context, int seconds, Runnable done) {
        super(context);
        snowMs = Math.max(500, seconds * 1000 - OPEN_MS - FADE_MS);
        this.done = done;
        paint.setFilterBitmap(false);
    }

    private int next() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (int) seed;
    }

    private void fillSnow(long t) {
        // A darker band rolls slowly down the picture, as when the vertical hold drifts.
        int band = (int) ((t / 6) % (H + 30)) - 30;
        for (int y = 0; y < H; y++) {
            boolean inBand = y >= band && y < band + 18;
            int row = y * W;
            for (int x = 0; x < W; x++) {
                int v = next() & 0xFF;
                if (inBand) v = v * 3 / 5;
                pixels[row + x] = 0xFF000000 | v << 16 | v << 8 | v;
            }
        }
        snow.setPixels(pixels, 0, W, 0, 0, W, H);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (start == 0) start = SystemClock.uptimeMillis();
        long t = SystemClock.uptimeMillis() - start;
        int w = getWidth(), h = getHeight();
        if (t >= OPEN_MS + snowMs + FADE_MS) {
            if (!finished) {
                finished = true;
                done.run();
            }
            return;
        }
        fillSnow(t);
        if (t < OPEN_MS) {
            // The tube opens: black, a bright line across the middle that grows into the picture.
            canvas.drawColor(Color.BLACK);
            float k = t / (float) OPEN_MS;
            int half = Math.max(2, (int) (h / 2f * k * k));
            dst.set(0, h / 2 - half, w, h / 2 + half);
            canvas.drawBitmap(snow, null, dst, paint);
            fill.setColor(Color.argb((int) (220 * (1 - k)), 255, 255, 255));
            canvas.drawRect(dst, fill);
        } else {
            int alpha = 255;
            if (t > OPEN_MS + snowMs) alpha = (int) (255 * (1 - (t - OPEN_MS - snowMs) / (float) FADE_MS));
            paint.setAlpha(alpha);
            dst.set(0, 0, w, h);
            canvas.drawBitmap(snow, null, dst, paint);
        }
        postInvalidateOnAnimation();
    }
}
