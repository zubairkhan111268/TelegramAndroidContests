package org.telegram.ui.Components.chat;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ServiceMessageBackgroundDrawable extends Drawable{

	private View bgView, fgView;
	private Theme.ResourcesProvider resProvider;
	private RectF rect=new RectF();

	public ServiceMessageBackgroundDrawable(View bgView, View fgView, Theme.ResourcesProvider resProvider){
		this.bgView=bgView;
		this.fgView=fgView;
		this.resProvider=resProvider;
	}

	@Override
	public void draw(@NonNull Canvas canvas){
		Theme.applyServiceShaderMatrixForView(fgView, bgView);
		rect.set(getBounds());
		float radius=getBounds().height()/2f;
		canvas.drawRoundRect(rect, radius, radius, getThemedPaint(isPressed() ? Theme.key_paint_chatActionBackgroundSelected : Theme.key_paint_chatActionBackground));
		if (resProvider.hasGradientService()) {
			canvas.drawRoundRect(rect, radius, radius, Theme.chat_actionBackgroundGradientDarkenPaint);
		}
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

	private boolean isPressed(){
		for(int state:getState()){
			if(state==android.R.attr.state_pressed)
				return true;
		}
		return false;
	}

	private Paint getThemedPaint(String key){
		Paint paint=resProvider.getPaint(key);
		return paint==null ? Theme.getThemePaint(key) : paint;
	}

	@Override
	public boolean isStateful(){
		return true;
	}

	@Override
	protected boolean onStateChange(int[] state){
		invalidateSelf();
		return true;
	}
}
