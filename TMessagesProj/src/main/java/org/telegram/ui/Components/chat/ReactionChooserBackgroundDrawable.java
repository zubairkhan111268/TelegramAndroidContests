package org.telegram.ui.Components.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*package*/ class ReactionChooserBackgroundDrawable extends Drawable{
	private Bitmap shadowBitmap;
	private RectF rect=new RectF();
	private Rect src=new Rect(), dst=new Rect();
	private final int shadowOffset, mainRectHeight, largeCircleSize, smallCircleSize, largeCircleX, largeCircleY, smallCircleX, smallCircleY;
	private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
	private Theme.ResourcesProvider theme;

	public ReactionChooserBackgroundDrawable(Theme.ResourcesProvider theme){
		this.theme=theme;
		shadowOffset=AndroidUtilities.dp(5);
		mainRectHeight=AndroidUtilities.dp(44);
		largeCircleSize=AndroidUtilities.dp(14);
		smallCircleSize=AndroidUtilities.dp(7);
		largeCircleX=AndroidUtilities.dp(37)+shadowOffset;
		largeCircleY=AndroidUtilities.dp(43)+shadowOffset;
		smallCircleX=AndroidUtilities.dp(33)+shadowOffset;
		smallCircleY=AndroidUtilities.dp(57.5f)+shadowOffset;

		shadowBitmap=Bitmap.createBitmap(AndroidUtilities.dp(90), AndroidUtilities.dp(44+10+17), Bitmap.Config.ALPHA_8);
		Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(0xff000000);
		paint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.6f), 0xff000000);

		Canvas canvas=new Canvas(shadowBitmap);
		rect.set(shadowOffset, shadowOffset, shadowBitmap.getWidth()-shadowOffset, shadowOffset+mainRectHeight);
		Path path=new Path();
		path.addRoundRect(rect, mainRectHeight/2f, mainRectHeight/2f, Path.Direction.CW);
		path.addCircle(shadowBitmap.getWidth()-largeCircleX, largeCircleY, largeCircleSize/2f, Path.Direction.CW);
		path.addCircle(shadowBitmap.getWidth()-smallCircleX, smallCircleY, smallCircleSize/2f, Path.Direction.CW);
		canvas.drawPath(path, paint);
	}

	@Override
	public void draw(@NonNull Canvas canvas){
		Rect bounds=getBounds();
		src.set(0, 0, AndroidUtilities.dp(23)+shadowOffset, mainRectHeight+shadowOffset*2);
		dst.set(src);
		dst.offset(bounds.left, bounds.top);
		paint.setColor(0x33000000);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);

		src.set(AndroidUtilities.dp(25)+shadowOffset, 0, shadowBitmap.getWidth(), shadowBitmap.getHeight());
		dst.set(bounds.right-src.width(), bounds.top, bounds.right, bounds.top+src.height());
		canvas.drawBitmap(shadowBitmap, src, dst, paint);

		src.set(AndroidUtilities.dp(23)+shadowOffset, 0, AndroidUtilities.dp(23)+shadowOffset+1, shadowOffset);
		dst.set(bounds.left+AndroidUtilities.dp(23), bounds.top, bounds.right-(shadowBitmap.getWidth()-(AndroidUtilities.dp(25)+shadowOffset)), bounds.top+shadowOffset);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);

		src.offset(0, mainRectHeight+shadowOffset);
		dst.offset(0, mainRectHeight+shadowOffset);
		canvas.drawBitmap(shadowBitmap, src, dst, paint);

		Integer color=theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
		paint.setColor(color!=null ? color : Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
		rect.set(bounds.left+shadowOffset, bounds.top+shadowOffset, bounds.right-shadowOffset, bounds.top+shadowOffset+mainRectHeight);
		canvas.drawRoundRect(rect, mainRectHeight/2f, mainRectHeight/2f, paint);
		canvas.drawCircle(bounds.right-largeCircleX, largeCircleY, largeCircleSize/2f, paint);
		canvas.drawCircle(bounds.right-smallCircleX, smallCircleY, smallCircleSize/2f, paint);
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
}
