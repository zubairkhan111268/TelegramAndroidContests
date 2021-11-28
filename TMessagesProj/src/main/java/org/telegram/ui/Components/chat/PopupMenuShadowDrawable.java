package org.telegram.ui.Components.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*package*/ class PopupMenuShadowDrawable extends Drawable{
	private Bitmap shadowBitmap;
	private int shadowSize, shadowOffset;
	private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
	private Rect src=new Rect(), dst=new Rect();
	private RectF rectF=new RectF();
	private Theme.ResourcesProvider theme;

	public PopupMenuShadowDrawable(Theme.ResourcesProvider theme){
		this.theme=theme;
		shadowSize=AndroidUtilities.dp(30);
		shadowOffset=AndroidUtilities.dp(5);
		shadowBitmap=Bitmap.createBitmap(shadowSize, shadowSize, Bitmap.Config.ALPHA_8);
		Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(0xff000000);
		paint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.6f), 0xff000000);
		new Canvas(shadowBitmap).drawRoundRect(new RectF(shadowOffset, shadowOffset, shadowSize-shadowOffset, shadowSize-shadowOffset), AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
	}

	@Override
	public void draw(@NonNull Canvas canvas){
		Rect bounds=getBounds();
		paint.setColor(0x33000000);
		int patchSize=shadowSize/2;
		// top-left
		src.set(0, 0, patchSize, patchSize);
		dst.set(src);
		dst.offsetTo(bounds.left, bounds.top);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);
		// top-right
		src.offsetTo(shadowSize-patchSize, 0);
		dst.offsetTo(getBounds().right-patchSize, bounds.top);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);
		// bottom-left
		src.offsetTo(0, shadowSize-patchSize);
		dst.offsetTo(bounds.left, bounds.bottom-patchSize);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);
		// bottom-right
		src.offsetTo(shadowSize-patchSize, shadowSize-patchSize);
		dst.offsetTo(bounds.right-patchSize, bounds.bottom-patchSize);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);

		// top
		src.set(patchSize, 0, patchSize+1, patchSize);
		dst.set(bounds.left+patchSize, bounds.top, bounds.right-patchSize, bounds.top+patchSize);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);
		// bottom
		src.set(patchSize, shadowSize-patchSize, patchSize+1, shadowSize);
		dst.set(bounds.left+patchSize, bounds.bottom-patchSize, bounds.right-patchSize, bounds.bottom);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);
		// left
		src.set(0, patchSize, patchSize, patchSize+1);
		dst.set(bounds.left, bounds.top+patchSize, bounds.left+patchSize, bounds.bottom-patchSize);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);
		// right
		src.set(shadowSize-patchSize, patchSize, shadowSize, patchSize+1);
		dst.set(bounds.right-patchSize, bounds.top+patchSize, bounds.right, bounds.bottom-patchSize);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);

		paint.setColor(theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
		rectF.set(bounds.left+shadowOffset, bounds.top+shadowOffset, bounds.right-shadowOffset, bounds.bottom-shadowOffset);
		canvas.drawRoundRect(rectF, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
	}

	@Override
	public void setAlpha(int i){

	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter){

	}

	@Override
	public int getOpacity(){
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public boolean getPadding(@NonNull Rect padding){
		padding.set(shadowOffset, shadowOffset, shadowOffset, shadowOffset);
		return true;
	}
}
