package org.telegram.ui.Components.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

import androidx.annotation.NonNull;

public class RoundedFrameLayout extends FrameLayout{

	private Paint clearPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
	private Path path=new Path();
	private RectF rectF=new RectF();

	public RoundedFrameLayout(@NonNull Context context){
		super(context);
		setLayerType(LAYER_TYPE_HARDWARE, null);
		clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh){
		super.onSizeChanged(w, h, oldw, oldh);
		rectF.set(0, 0, w, h);
		path.rewind();
		path.addRoundRect(rectF, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Path.Direction.CW);
		path.toggleInverseFillType();
	}

	@Override
	protected void dispatchDraw(Canvas canvas){
		super.dispatchDraw(canvas);
		canvas.drawPath(path, clearPaint);
	}
}
