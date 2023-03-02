package org.telegram.ui.Components.voip;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VoIPEllipsisDrawable extends Drawable{
	private final CubicBezierInterpolator interpolator=new CubicBezierInterpolator(0.33, 0.00, 0.67, 1.00);
	private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

	public VoIPEllipsisDrawable(){
		paint.setColor(0xffffffff);
	}

	@Override
	public void draw(@NonNull Canvas canvas){
		canvas.save();
		Rect bounds=getBounds();
		canvas.translate(bounds.left, bounds.top);
		long time=SystemClock.uptimeMillis()%250+500;
		for(int i=0;i<3;i++){
			long pointTime=(time+i*250L)%750;
			float moveFraction=Math.min(1, pointTime/667f);
			float scale;
			if(moveFraction<=0.425f){
				scale=interpolator.getInterpolation(moveFraction/0.425f);
			}else{
				scale=1f-interpolator.getInterpolation((moveFraction-0.425f)/0.575f);
			}
			moveFraction=interpolator.getInterpolation(moveFraction);
			canvas.drawCircle(AndroidUtilities.dpf2(1.667f+moveFraction*16f), AndroidUtilities.dp(3), AndroidUtilities.dpf2(2*scale), paint);
		}
		canvas.restore();
		invalidateSelf();
	}

	@Override
	public void setAlpha(int alpha){

	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter){

	}

	@Override
	public int getOpacity(){
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public int getIntrinsicWidth(){
		return AndroidUtilities.dp(20);
	}

	@Override
	public int getIntrinsicHeight(){
		return AndroidUtilities.dp(7);
	}
}
