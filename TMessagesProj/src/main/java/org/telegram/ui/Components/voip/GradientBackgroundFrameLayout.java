package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GradientBackgroundFrameLayout extends FrameLayout{
	private static final String TAG="GradientBackgroundFrame";

	// main, light, dark
	// -> top-left, top-right, bottom-right, bottom-left
	private static final int[][] GRADIENT_BLUE_VIOLET={
			{0xFF20A4D7, 0xFF3F8BEA, 0xFFB456D8, 0xFF8148EC},
			{0xFF2DC0F9, 0xFF57A1FF, 0xFFD664FF, 0xFF9258FD},
			{0xFF0F95C9, 0xFF287AE1, 0xFFA736D0, 0xFF6A2BDD}
	};
	private static final int[][] GRADIENT_BLUE_GREEN={
			{0xFF08B0A3, 0xFF17AAE4, 0xFF4576E9, 0xFF3B7AF1},
			{0xFF04DCCC, 0xFF28C2FF, 0xFF558BFF, 0xFF5FABFF},
			{0xFF009595, 0xFF0291C9, 0xFF2D60D6, 0xFF2C6ADF}
	};
	private static final int[][] GRADIENT_GREEN={
			{0xFFA9CC66, 0xFF5AB147, 0xFF07A9AC, 0xFF07BA63},
			{0xFFC7EF60, 0xFF6DD957, 0xFF00D2D5, 0xFF09E279},
			{0xFF8FBD37, 0xFF319D27, 0xFF008B8E, 0xFF01934C}
	};
	private static final int[][] GRADIENT_ORANGE_RED={
			{0xFFDB904C, 0xFFDE7238, 0xFFE86958, 0xFFE7618F},
			{0xFFFEB055, 0xFFFF8E51, 0xFFFF7866, 0xFFFF82A5},
			{0xFFC77616, 0xFFD75A16, 0xFFE23F29, 0xFFE6306F}
	};

	// this can probably be simplified, but I'm lazy enough to copy these numbers from after effects and put them through a spreadsheet
	private static final float[][][] POINTS_COORDS={
			{
				{-0.13f, -0.037f},
				{1.137f, -0.044f},
				{1.189f, 0.486f},
				{1.122f, 1.048f},
				{-0.204f, 1.037f},
				{-0.211f, 0.486f},
				{-0.152f, -0.077f},
				{1.144f, -0.08f},
				{1.226f, 0.496f},
				{1.174f, 1.041f},
				{-0.241f, 1.051f},
				{-0.3f, 0.493f},
			},
			{
				{1.1f, -0.055f},
				{1.159f, 0.482f},
				{1.152f, 1.041f},
				{-0.196f, 1.051f},
				{-0.219f, 0.442f},
				{-0.189f, -0.051f},
				{1.181f, -0.073f},
				{1.204f, 0.511f},
				{1.181f, 1.03f},
				{-0.241f, 1.033f},
				{-0.226f, 0.486f},
				{-0.211f, -0.073f},
			},
			{
				{-0.263f, 1.008f},
				{-0.226f, 0.486f},
				{-0.219f, -0.051f},
				{1.174f, -0.087f},
				{1.189f, 0.496f},
				{1.167f, 1.041f},
				{-0.219f, 1.037f},
				{-0.256f, 0.496f},
				{-0.233f, -0.091f},
				{1.174f, -0.087f},
				{1.256f, 0.496f},
				{1.27f, 1.012f},
			},
			{
				{1.189f, 1.019f},
				{-0.181f, 1.044f},
				{-0.256f, 0.471f},
				{-0.219f, -0.069f},
				{1.1f, -0.105f},
				{1.204f, 0.496f},
				{1.144f, 1.055f},
				{-0.307f, 1.048f},
				{-0.263f, 0.464f},
				{-0.196f, -0.084f},
				{1.219f, -0.102f},
				{1.285f, 0.489f},
			}
	};

	private static final int BITMAP_WIDTH=64;
	private static final int BITMAP_HEIGHT=64;
	private static final ArgbEvaluator ARGB_EVALUATOR=new ArgbEvaluator();

	private Runnable animUpdater=this::updateAnimation;
	private Bitmap mainGradient, lightGradient, darkGradient, transitionMainGradient, transitionLightGradient, transitionDarkGradient;
	private BitmapShader mainShader, lightShader, darkShader, transitionMainShader, transitionLightShader, transitionDarkShader;
	private Matrix shaderMatrix=new Matrix();
	private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
	private RectF tmpRect=new RectF();
	private int[] location={0, 0};
	private Path transitionClip=new Path();
	private int transitionCenterX, transitionCenterY, transitionRadius;
	private float[] points=new float[8];
	private GradientState currentState=new GradientState(), transitionTargetState, fadeTargetState;
	private int[][] tmpColorsArray=new int[3][4], anotherTmpColorsArray=new int[3][4];
	private Animator currentTransitionAnim;
	private HashSet<View> gradientChildViews=new HashSet<>();
	private float fadeFactor;
	private SimpleShaderDrawable background;
	private AnimationState state=AnimationState.STOPPED;
	private long stopTime, timeOffset;

	public GradientBackgroundFrameLayout(Context context){
		this(context, null);
	}

	public GradientBackgroundFrameLayout(Context context, AttributeSet attrs){
		this(context, attrs, 0);
	}

	public GradientBackgroundFrameLayout(Context context, AttributeSet attrs, int defStyle){
		super(context, attrs, defStyle);

		currentState.pattern=Arrays.asList(GradientStyle.BLUE_VIOLET, GradientStyle.BLUE_GREEN);
		currentState.animationStartTime=getAnimationTime();

		mainGradient=Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
		lightGradient=Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
		darkGradient=Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
		transitionMainGradient=Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
		transitionLightGradient=Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
		transitionDarkGradient=Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);

		mainShader=new BitmapShader(mainGradient, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		lightShader=new BitmapShader(lightGradient, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		darkShader=new BitmapShader(darkGradient, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		transitionMainShader=new BitmapShader(transitionMainGradient, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		transitionLightShader=new BitmapShader(transitionLightGradient, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		transitionDarkShader=new BitmapShader(transitionDarkGradient, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

		updateAnimation();
		background=new SimpleShaderDrawable();
	}

	@Override
	public void setBackground(Drawable background){
		throw new UnsupportedOperationException();
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom){
		super.setPadding(left, top, right, bottom);
	}

	@Override
	protected void onAttachedToWindow(){
		super.onAttachedToWindow();
		postOnAnimation(animUpdater);
	}

	private void updateAnimation(){
		long uptime=getAnimationTime();
		int time=(int)(uptime%24000);
		int phase=time/2000;
		int nextPhase=(phase+1)%12;
		float progress=(time%2000)/2000f;

		if(state==AnimationState.STOPPING && progress<0.05f){
			stopAnimation();
			progress=0f;
		}

		for(int i=0;i<4;i++){
			points[i*2]=POINTS_COORDS[i][phase][0]*(1f-progress)+POINTS_COORDS[i][nextPhase][0]*progress;
			points[i*2+1]=POINTS_COORDS[i][phase][1]*(1f-progress)+POINTS_COORDS[i][nextPhase][1]*progress;
		}

		updateGradients();
		invalidate();
		invalidateChildViewsThatNeedGradients();
		if(state!=AnimationState.STOPPED){
			postOnAnimation(animUpdater);
		}
	}

	private void updateGradients(){
		currentState.getCurrentColors(tmpColorsArray);
		if(fadeTargetState!=null){
			fadeTargetState.getCurrentColors(anotherTmpColorsArray);
			interpolateColors(fadeFactor, tmpColorsArray, anotherTmpColorsArray, tmpColorsArray);
		}
		Utilities.generateBetterGradient(mainGradient, points, tmpColorsArray[0]);
		Utilities.generateBetterGradient(lightGradient, points, tmpColorsArray[1]);
		Utilities.generateBetterGradient(darkGradient, points, tmpColorsArray[2]);

		if(transitionTargetState!=null){
			transitionTargetState.getCurrentColors(tmpColorsArray);
			Utilities.generateBetterGradient(transitionMainGradient, points, tmpColorsArray[0]);
			Utilities.generateBetterGradient(transitionLightGradient, points, tmpColorsArray[1]);
			Utilities.generateBetterGradient(transitionDarkGradient, points, tmpColorsArray[2]);
		}
	}

	private int dp(float dp){
		return Math.round(dp*getResources().getDisplayMetrics().density);
	}

	public Drawable newChildBackgroundDrawable(View child, boolean isDark, int radiusDp){
		gradientChildViews.add(child);
		return new ChildBackgroundDrawable(child, isDark, dp(radiusDp));
	}

	public Drawable newChildTooltipBackgroundDrawable(View child, boolean isDark, int radiusDp){
		gradientChildViews.add(child);
		return new TooltipBackgroundDrawable(child, isDark, dp(radiusDp));
	}

	public void unregisterChild(View child){
		gradientChildViews.remove(child);
	}

	private int distance(int x1, int y1, int x2, int y2){
		return Math.round((float)Math.hypot(x1-x2, y1-y2));
	}

	public void performCircularTransition(int centerX, int centerY, int initialRadius, GradientStyle... newPattern){
		transitionCenterX=centerX;
		transitionCenterY=centerY;
		transitionRadius=initialRadius;
		if(transitionTargetState!=null)
			currentTransitionAnim.cancel();
		transitionTargetState=new GradientState();
		transitionTargetState.animationStartTime=getAnimationTime();
		transitionTargetState.pattern=Arrays.asList(newPattern);

		int finalTransitionRadius=Math.max(Math.max(
				distance(centerX, centerY, 0, 0), // top-left
				distance(centerX, centerY, getWidth(), 0) // top-right
		), Math.max(
				distance(centerX, centerY, 0, getHeight()), // bottom-left
				distance(centerX, centerY, getWidth(), getHeight()) // bottom-right
		));

		ObjectAnimator anim=ObjectAnimator.ofInt(this, "currentTransitionRadius", finalTransitionRadius);
		anim.setDuration(467); // 28 frames @ 60fps
		anim.setInterpolator(new CubicBezierInterpolator(0.79, 0.00, 0.60, 1.00));
		anim.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				currentState=transitionTargetState;
				transitionTargetState=null;
				currentTransitionAnim=null;
				updateGradients();
				invalidateChildViewsThatNeedGradients();
			}
		});
		anim.start();
		currentTransitionAnim=anim;
	}

	public void performCrossfade(GradientStyle... newPattern){
		if(currentTransitionAnim!=null)
			currentTransitionAnim.cancel();
		fadeTargetState=new GradientState();
		fadeTargetState.animationStartTime=getAnimationTime();
		fadeTargetState.pattern=Arrays.asList(newPattern);

		ObjectAnimator anim=ObjectAnimator.ofFloat(this, "currentFadeFactor", 0f, 1f);
		anim.setDuration(167); // 10 frames @ 60 fps
		anim.setInterpolator(new LinearInterpolator());
		anim.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				currentState=fadeTargetState;
				fadeTargetState=null;
				currentTransitionAnim=null;
				updateGradients();
				invalidateChildViewsThatNeedGradients();
			}
		});
		anim.start();
		currentTransitionAnim=anim;
	}

	public void stopAnimation(){
		removeCallbacks(animUpdater);
		state=AnimationState.STOPPED;
		stopTime=SystemClock.uptimeMillis();
	}

	public void stopAndClear(){
		stopAnimation();
		super.setBackground(new ColorDrawable(0xff000000));
	}

	public void startAnimation(){
		if(state==AnimationState.STOPPING){
			state=AnimationState.RUNNING;
			return;
		}else if(state==AnimationState.RUNNING){
			return;
		}
		if(stopTime!=0){
			long offset=stopTime-SystemClock.uptimeMillis();
			timeOffset+=offset;
		}
		super.setBackground(background);
		state=AnimationState.RUNNING;
		updateAnimation();
	}

	public void stopAnimationDelayed(){
		if(state==AnimationState.RUNNING)
			state=AnimationState.STOPPING;
	}

	@Keep
	public void setCurrentTransitionRadius(int radius){
		transitionRadius=radius;
		invalidate();
		invalidateChildViewsThatNeedGradients();
	}

	@Keep
	public int getCurrentTransitionRadius(){
		return transitionRadius;
	}

	@Keep
	public void setCurrentFadeFactor(float factor){
		fadeFactor=factor;
	}

	public boolean isAnimatingGradient(){
		return true;
	}

	private void invalidateChildViewsThatNeedGradients(){
		for(View v:gradientChildViews){
			Drawable d=v.getBackground();
			if(d instanceof ChildBackgroundDrawable){
				d.invalidateSelf();
			}
		}
	}

	private long getAnimationTime(){
		long offset=timeOffset;
		long uptime=SystemClock.uptimeMillis();
		if(state==AnimationState.STOPPED){
			offset+=stopTime-uptime;
		}
		return uptime+offset;
	}

	private static int[][] getColorsForStyle(GradientStyle style){
		switch(style){
			case BLUE_VIOLET:
				return GRADIENT_BLUE_VIOLET;
			case BLUE_GREEN:
				return GRADIENT_BLUE_GREEN;
			case GREEN:
				return GRADIENT_GREEN;
			case ORANGE_RED:
				return GRADIENT_ORANGE_RED;
			default:
				throw new IllegalArgumentException();
		}
	}

	private static void interpolateColors(float fraction, int[][] from, int[][] to, int[][] out){
		for(int i=0;i<from.length;i++){
			for(int j=0;j<from[i].length;j++){
				out[i][j]=(Integer)ARGB_EVALUATOR.evaluate(fraction, from[i][j], to[i][j]);
			}
		}
	}

	public enum GradientStyle{
		BLUE_VIOLET,
		BLUE_GREEN,
		GREEN,
		ORANGE_RED
	}

	private enum AnimationState{
		RUNNING,
		STOPPED,
		STOPPING
	}

	private class GradientState{
		public List<GradientStyle> pattern;
		public long animationStartTime;

		public void getCurrentColors(int[][] out){
			if(pattern.size()==1){
				int[][] colors=getColorsForStyle(pattern.get(0));
				for(int i=0;i<3;i++){
					System.arraycopy(colors[i], 0, out[i], 0, 4);
				}
				return;
			}
			int time=(int)((getAnimationTime()-animationStartTime)%(pattern.size()*6000));
			int styleA=time/6000;
			int styleB=(styleA+1)%pattern.size();
			float fraction=time%6000/6000f;
			int[][] colorsA=getColorsForStyle(pattern.get(styleA)), colorsB=getColorsForStyle(pattern.get(styleB));
			interpolateColors(fraction, colorsA, colorsB, out);
		}
	}

	private class SimpleShaderDrawable extends Drawable{
		@Override
		public void draw(Canvas canvas){
			paint.setAlpha(255);
			shaderMatrix.setScale(getWidth()/(float)BITMAP_WIDTH, getHeight()/(float)BITMAP_HEIGHT);
			mainShader.setLocalMatrix(shaderMatrix);
			paint.setShader(mainShader);
			canvas.drawRect(getBounds(), paint);
			if(transitionTargetState!=null){
				transitionClip.rewind();
				transitionClip.addCircle(transitionCenterX, transitionCenterY, transitionRadius, Path.Direction.CW);
				canvas.save();
				canvas.clipPath(transitionClip);
				transitionMainShader.setLocalMatrix(shaderMatrix);
				paint.setShader(transitionMainShader);
				canvas.drawRect(getBounds(), paint);
				canvas.restore();
			}
		}

		@Override
		public void setAlpha(int alpha){

		}

		@Override
		public void setColorFilter(ColorFilter colorFilter){

		}

		@Override
		public int getOpacity(){
			return PixelFormat.OPAQUE;
		}
	}

	private class ChildBackgroundDrawable extends Drawable{
		protected final View child;
		protected final boolean isDark;
		protected final int radius;
		protected int alpha=255;

		private ChildBackgroundDrawable(View child, boolean isDark, int radius){
			this.child=child;
			this.isDark=isDark;
			this.radius=radius;
		}

		@Override
		public void draw(Canvas canvas){
			tmpRect.set(getBounds());
			if(transitionTargetState!=null && alpha<255){
				paint.setAlpha(255);
				canvas.saveLayerAlpha(tmpRect, alpha, Canvas.ALL_SAVE_FLAG);
			}else{
				paint.setAlpha(alpha);
			}
			BitmapShader shader=isDark ? darkShader : lightShader;
			shaderMatrix.setScale(getWidth()/(float)BITMAP_WIDTH, getHeight()/(float)BITMAP_HEIGHT);
			getLocationOnScreen(location);
			int parentX=location[0], parentY=location[1];
			child.getLocationOnScreen(location);
			int transX=parentX-location[0], transY=parentY-location[1];
			shaderMatrix.postTranslate(transX, transY);
			shader.setLocalMatrix(shaderMatrix);
			paint.setShader(shader);
			canvas.drawRoundRect(tmpRect, radius, radius, paint);
			if(transitionTargetState!=null){
				transitionClip.rewind();
				transitionClip.addCircle(transitionCenterX+transX, transitionCenterY+transY, transitionRadius, Path.Direction.CW);
				canvas.save();
				canvas.clipPath(transitionClip);
				shader=isDark ? transitionDarkShader : transitionLightShader;
				shader.setLocalMatrix(shaderMatrix);
				paint.setShader(shader);
				canvas.drawRoundRect(tmpRect, radius, radius, paint);
				canvas.restore();
				if(alpha<255)
					 canvas.restore(); // saveLayerAlpha
			}
		}

		@Override
		public void setAlpha(int alpha){
			this.alpha=alpha;
		}

		@Override
		public int getAlpha(){
			return alpha;
		}

		@Override
		public void setColorFilter(ColorFilter colorFilter){

		}

		@Override
		public int getOpacity(){
			return PixelFormat.TRANSLUCENT;
		}
	}

	private class TooltipBackgroundDrawable extends ChildBackgroundDrawable{
		private final Drawable arrow=getResources().getDrawable(R.drawable.tooltip_arrow_up);
		private final Paint dstInPaint=new Paint(), clearPaint=new Paint();

		private TooltipBackgroundDrawable(View child, boolean isDark, int radius){
			super(child, isDark, radius);
			dstInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
			clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		}

		@Override
		public void draw(@NonNull Canvas canvas){
			tmpRect.set(getBounds());
			if(transitionTargetState!=null && alpha<255){
				paint.setAlpha(255);
				canvas.saveLayerAlpha(tmpRect, alpha, Canvas.ALL_SAVE_FLAG);
			}else{
				paint.setAlpha(alpha);
			}
			paint.setAlpha(alpha);
			BitmapShader shader=isDark ? darkShader : lightShader;
			shaderMatrix.setScale(getWidth()/(float)BITMAP_WIDTH, getHeight()/(float)BITMAP_HEIGHT);
			getLocationOnScreen(location);
			int parentX=location[0], parentY=location[1];
			child.getLocationOnScreen(location);
			int transX=parentX-location[0], transY=parentY-location[1];
			shaderMatrix.postTranslate(transX, transY);
			shader.setLocalMatrix(shaderMatrix);
			paint.setShader(shader);

			canvas.drawRect(tmpRect, paint);
			if(transitionTargetState!=null){
				transitionClip.rewind();
				transitionClip.addCircle(transitionCenterX+transX, transitionCenterY+transY, transitionRadius, Path.Direction.CW);
				canvas.save();
				canvas.clipPath(transitionClip);
				shader=isDark ? transitionDarkShader : transitionLightShader;
				shader.setLocalMatrix(shaderMatrix);
				paint.setShader(shader);
				canvas.drawRect(tmpRect, paint);
				canvas.restore();
				if(alpha<255)
					canvas.restore(); // saveLayerAlpha
			}
			paint.setShader(null);
			paint.setAlpha(255);

			canvas.saveLayer(tmpRect, dstInPaint, Canvas.ALL_SAVE_FLAG);

			Rect bounds=getBounds();
			canvas.drawRect(bounds, clearPaint);
			int arrowX=bounds.centerX()-arrow.getIntrinsicWidth()/2;
			arrow.setBounds(arrowX, bounds.top, arrowX+arrow.getIntrinsicWidth(), bounds.top+arrow.getIntrinsicHeight());
			arrow.draw(canvas);
			tmpRect.top+=arrow.getIntrinsicHeight();
			canvas.drawRoundRect(tmpRect, radius, radius, paint);

			canvas.restore();
		}
	}
}
