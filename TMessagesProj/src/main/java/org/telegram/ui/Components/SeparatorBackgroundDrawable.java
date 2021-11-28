package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SeparatorBackgroundDrawable extends Drawable{
	private final String backgroundColorKey;
	private final int separatorOffsetLeft;
	private boolean drawSeparator;
	private Paint paint=new Paint();

	public SeparatorBackgroundDrawable(String backgroundColorKey, int separatorOffsetLeft){
		this.backgroundColorKey=backgroundColorKey;
		this.separatorOffsetLeft=separatorOffsetLeft;
	}

	@Override
	public void draw(@NonNull Canvas canvas){
		paint.setColor(Theme.getColor(backgroundColorKey));
		canvas.drawRect(getBounds(), paint);
		if(drawSeparator){
			int width=getBounds().width();
			int height=getBounds().height();
			canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(separatorOffsetLeft), height - 1, width - (LocaleController.isRTL ? AndroidUtilities.dp(separatorOffsetLeft) : 0), height - 1, Theme.dividerPaint);
		}
	}

	@Override
	public void setAlpha(int i){

	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter){

	}

	@Override
	public int getOpacity(){
		return 0;
	}

	public void setDrawSeparator(boolean drawSeparator){
		this.drawSeparator=drawSeparator;
	}
}
