package app.metak;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** A Watch Next card that fades out toward its top edge, unless any card in its row is focused. */
public class FadeCard extends FrameLayout {
    private static final float TOP_ALPHA = 0.25f; // opacity at the very top edge
    private final Paint fade = new Paint();
    private LinearGradient gradient;

    public FadeCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setLayerType(LAYER_TYPE_HARDWARE, null); // offscreen so DST_IN masks the whole card
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // Multiplies the card's alpha by this gradient: dim at the top, full at the bottom.
        gradient = new LinearGradient(0, 0, 0, h,
                (((int) (TOP_ALPHA * 255)) << 24) | 0xFFFFFF, Color.WHITE, Shader.TileMode.CLAMP);
        fade.setShader(gradient);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // The whole row loses its fade once any of its cards is highlighted.
        boolean rowFocused = getParent() instanceof View && ((View) getParent()).hasFocus();
        if (!rowFocused && gradient != null) canvas.drawRect(0, 0, getWidth(), getHeight(), fade);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        // Focus entering or leaving the row flips every card, not just this one.
        if (getParent() instanceof ViewGroup) {
            ViewGroup row = (ViewGroup) getParent();
            for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).invalidate();
        } else invalidate();
    }
}
