package com.tvlink.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.ImageButton;

/** Draws the remote controls without relying on the device's symbol fonts. */
final class RemoteIconView extends ImageButton {
    private final String icon;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    RemoteIconView(Context context, String icon, String label, int color) {
        super(context);
        this.icon = icon;
        setContentDescription(label);
        ink.setColor(color);
        ink.setStrokeWidth(2);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setStyle(Paint.Style.STROKE);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(32 * getResources().getDisplayMetrics().density,
                Math.min(getWidth(), getHeight()) * .65f);
        canvas.save();
        canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
        canvas.scale(size / 24, size / 24);
        switch (icon) {
            case "up": line(canvas, 5, 15, 12, 8, 19, 15); break;
            case "down": line(canvas, 5, 9, 12, 16, 19, 9); break;
            case "left": line(canvas, 15, 5, 8, 12, 15, 19); break;
            case "right": line(canvas, 9, 5, 16, 12, 9, 19); break;
            case "back":
                line(canvas, 11, 5, 4, 12, 11, 19);
                line(canvas, 4, 12, 21, 12);
                break;
            case "power":
                canvas.drawArc(new RectF(4, 4, 20, 20), -50, 280, false, ink);
                line(canvas, 12, 2, 12, 11);
                break;
            case "home":
                line(canvas, 3, 10, 12, 3, 21, 10, 21, 21, 15, 21, 15, 15,
                        9, 15, 9, 21, 3, 21, 3, 10);
                break;
            case "mic":
                canvas.drawRoundRect(9, 2, 15, 14, 3, 3, ink);
                canvas.drawArc(new RectF(5, 5, 19, 19), 0, 180, false, ink);
                line(canvas, 12, 19, 12, 23);
                break;
            default:
                line(canvas, 3, 9, 7, 9, 12, 5, 12, 19, 7, 15, 3, 15, 3, 9);
                if ("mute".equals(icon)) {
                    line(canvas, 16, 9, 22, 15);
                    line(canvas, 16, 15, 22, 9);
                } else {
                    line(canvas, 16, 12, 22, 12);
                    if ("volume_up".equals(icon)) line(canvas, 19, 9, 19, 15);
                }
        }
        canvas.restore();
    }

    private void line(Canvas canvas, float... points) {
        path.reset();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        canvas.drawPath(path, ink);
    }
}
