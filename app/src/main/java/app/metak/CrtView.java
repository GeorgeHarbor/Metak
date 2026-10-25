package app.metak;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * Best-effort CRT power on/off: a full-screen overlay that collapses the picture to a bright
 * horizontal line, then to a center dot and black (off/dimmed), and expands back on (power on).
 */
class CrtView extends View {
    private static final long DURATION = 450;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float p = 1f; // 0 = collapsed to a dot on black, 1 = fully on (content visible)
    private ValueAnimator anim;

    CrtView(Context context) {
        super(context);
        setVisibility(GONE);
    }

    /** Expand from the dot, flash the picture in, then get out of the way. */
    void turnOn() {
        animateTo(1f);
    }

    /** Collapse the picture to a dot and hold black. */
    void turnOff() {
        animateTo(0f);
    }

    /** Jump straight to black with no animation (e.g. the screen already went off). */
    void snapOff() {
        if (anim != null) anim.cancel();
        p = 0f;
        setVisibility(VISIBLE);
        invalidate();
    }

    boolean isOff() {
        return getVisibility() == VISIBLE && p < 1f;
    }

    private void animateTo(float to) {
        if (anim != null) anim.cancel();
        setVisibility(VISIBLE);
        anim = ValueAnimator.ofFloat(p, to);
        anim.setDuration((long) (DURATION * Math.abs(p - to)));
        anim.addUpdateListener(a -> {
            p = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (p >= 1f) setVisibility(GONE); // fully on: stop intercepting
            }
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, thin = dp(2), dot = dp(4);

        if (p >= 0.70f) {
            // Reveal: the whole screen is white, then fades to let the content through.
            int a = (int) (255 * (1f - (p - 0.70f) / 0.30f));
            canvas.drawColor(Color.argb(a, 255, 255, 255));
            return;
        }
        canvas.drawColor(Color.BLACK);
        paint.setColor(Color.WHITE);
        if (p <= 0.12f) {
            // The bright dot in the middle, fading in/out against black at the collapsed end.
            paint.setAlpha((int) (255 * p / 0.12f));
            canvas.drawCircle(cx, cy, dot, paint);
            return;
        }
        paint.setAlpha(255);
        float halfW, halfH;
        if (p <= 0.45f) {            // the dot stretches into a horizontal line
            halfW = lerp(dot, w / 2f, (p - 0.12f) / 0.33f);
            halfH = thin;
        } else {                     // the line opens up into the full picture
            halfW = w / 2f;
            halfH = lerp(thin, h / 2f, (p - 0.45f) / 0.25f);
        }
        canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, thin, thin, paint);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
