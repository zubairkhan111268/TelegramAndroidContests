package org.telegram.animcontest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class AnimationTimingCell extends View{

	private static final int OBJ_NONE=0;
	private static final int OBJ_EASING_TOP=1;
	private static final int OBJ_EASING_BOTTOM=2;
	private static final int OBJ_RANGE_LEFT=3;
	private static final int OBJ_RANGE_RIGHT=4;

	private AnimationSettings.TimingParameters timingParameters;
	private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
	private Path path=new Path();
	private DashPathEffect dashPathEffect;
	private Bitmap circleShadow, ovalShadow;
	private RectF rect=new RectF();
	private float touchslop;

	private String percentTop, percentBottom, msLeft, msRight;
	private float totalDuration;
	private int draggedObject;
	private float dragStartX, dragStartY;
	private int dragPointerID;
	private boolean dragStarted=false;

	public AnimationTimingCell(Context context){
		super(context);
		dashPathEffect=new DashPathEffect(new float[]{AndroidUtilities.dp(1f), AndroidUtilities.dp(7f)}, 0f);

		Paint shadowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		shadowPaint.setColor(0xFF000000);
		shadowPaint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0xFF000000);
		// r 9
		circleShadow=Bitmap.createBitmap(AndroidUtilities.dp(26), AndroidUtilities.dp(26), Bitmap.Config.ALPHA_8);
		new Canvas(circleShadow).drawCircle(circleShadow.getWidth()/2f, circleShadow.getHeight()/2f, AndroidUtilities.dp(9)-.1f, shadowPaint);
		ovalShadow=Bitmap.createBitmap(AndroidUtilities.dp(20), AndroidUtilities.dp(34), Bitmap.Config.ALPHA_8);
		rect.set(AndroidUtilities.dp(4)+.1f, AndroidUtilities.dp(4)+.1f, AndroidUtilities.dp(16)-.1f, AndroidUtilities.dp(30)-.1f);
		new Canvas(ovalShadow).drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), shadowPaint);

		touchslop=ViewConfiguration.get(context).getScaledTouchSlop();
	}

	public void setTimingParameters(AnimationSettings.TimingParameters timingParameters, long totalDuration){
		this.timingParameters=timingParameters;
		this.totalDuration=totalDuration;
		updateStrings();
		invalidate();
	}

	private void updateStrings(){
		percentTop=Math.round(timingParameters.easingEnd*100f)+"%";
		percentBottom=Math.round(timingParameters.easingStart*100f)+"%";
		msLeft=Math.round(totalDuration*timingParameters.startDelayFraction)+"ms";
		msRight=Math.round(totalDuration*timingParameters.endTimeFraction)+"ms";
	}

	private boolean hitTest(MotionEvent ev, float cx, float cy, float w, float h){
		float x=cx-w/2f, y=cy-h/2f;
		return ev.getX()>=x && ev.getY()>=y && ev.getX()<=x+w && ev.getY()<=y+h;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event){
		float hPadding=AndroidUtilities.dp(26), vPadding=AndroidUtilities.dp(43);
		float paddedWidth=getWidth()-hPadding*2;
		float paddedHeight=getHeight()-vPadding*2;
		float startOffset=timingParameters.startDelayFraction*paddedWidth;
		float endOffset=timingParameters.endTimeFraction*paddedWidth;
		float actualDurationWidth=endOffset-startOffset;

		if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
			if(event.getDownTime()==0) // ignore fucky stuff from RecyclerListView
				return true;
			float easingSliderKnobSize=AndroidUtilities.dp(36);
			float rangeSliderKnobH=AndroidUtilities.dp(44), rangeSliderKnobW=AndroidUtilities.dp(30);

			draggedObject=OBJ_NONE;
			dragStarted=false;
			if(hitTest(event, hPadding+startOffset+actualDurationWidth*(1f-timingParameters.easingEnd), vPadding, easingSliderKnobSize, easingSliderKnobSize)){
				draggedObject=OBJ_EASING_TOP;
			}else if(hitTest(event, hPadding+startOffset+actualDurationWidth*timingParameters.easingStart, getHeight()-vPadding, easingSliderKnobSize, easingSliderKnobSize)){
				draggedObject=OBJ_EASING_BOTTOM;
			}else if(hitTest(event, hPadding+startOffset, paddedHeight/2f+vPadding, rangeSliderKnobW, rangeSliderKnobH)){
				draggedObject=OBJ_RANGE_LEFT;
			}else if(hitTest(event, hPadding+endOffset, paddedHeight/2f+vPadding, rangeSliderKnobW, rangeSliderKnobH)){
				draggedObject=OBJ_RANGE_RIGHT;
			}else{
				return false;
			}
			dragStartX=event.getX();
			dragStartY=event.getY();
			dragPointerID=event.getPointerId(0);
			return true;
		}else if(event.getActionMasked()==MotionEvent.ACTION_MOVE || event.getActionMasked()==MotionEvent.ACTION_UP){
			if(draggedObject==OBJ_NONE || dragPointerID!=event.getPointerId(0))
				return false;

			if(!dragStarted && event.getActionMasked()==MotionEvent.ACTION_MOVE && Math.abs(event.getX()-dragStartX)>=touchslop && Math.abs(event.getX()-dragStartX)>Math.abs(event.getY()-dragStartY)){
				dragStarted=true;
				getParent().requestDisallowInterceptTouchEvent(true);
			}

			if(dragStarted){
				float x=event.getX()-hPadding;
				if(draggedObject==OBJ_EASING_TOP){
					timingParameters.easingEnd=1f-(Math.max(startOffset, Math.min(endOffset, x))-startOffset)/actualDurationWidth;
				}else if(draggedObject==OBJ_EASING_BOTTOM){
					timingParameters.easingStart=(Math.max(startOffset, Math.min(endOffset, x))-startOffset)/actualDurationWidth;
				}else if(draggedObject==OBJ_RANGE_LEFT){
					timingParameters.startDelayFraction=Math.min(Math.max(0f, x/paddedWidth), timingParameters.endTimeFraction-.25f);
				}else if(draggedObject==OBJ_RANGE_RIGHT){
					timingParameters.endTimeFraction=Math.max(Math.min(1f, x/paddedWidth), timingParameters.startDelayFraction+.25f);
				}
			}

			updateStrings();
			invalidate();
			return true;
		}
		return false;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(236));
	}

	@Override
	protected void onDraw(Canvas canvas){
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(AndroidUtilities.dp(2));
		float hPadding=AndroidUtilities.dp(26), vPadding=AndroidUtilities.dp(43);
		float paddedWidth=getWidth()-hPadding*2;
		float paddedHeight=getHeight()-vPadding*2;
		float startOffset=timingParameters.startDelayFraction*paddedWidth;
		float endOffset=timingParameters.endTimeFraction*paddedWidth;
		float actualDurationWidth=endOffset-startOffset;

		paint.setColor(Theme.getColor(Theme.key_player_progressBackground));
		path.rewind();
		path.moveTo(hPadding+startOffset, getHeight()-vPadding);
		path.cubicTo(actualDurationWidth*timingParameters.easingStart+hPadding+startOffset, getHeight()-vPadding,
				actualDurationWidth*(1f-timingParameters.easingEnd)+hPadding+startOffset, vPadding,
				hPadding+endOffset, vPadding);
		canvas.drawPath(path, paint);
		canvas.drawLine(hPadding, vPadding, getWidth()-hPadding, vPadding, paint);
		canvas.drawLine(hPadding, getHeight()-vPadding, getWidth()-hPadding, getHeight()-vPadding, paint);

		paint.setColor(Theme.getColor(Theme.key_player_progress));
		canvas.drawLine(hPadding+startOffset+actualDurationWidth*(1f-timingParameters.easingEnd), vPadding, hPadding+endOffset, vPadding, paint);
		canvas.drawLine(hPadding+startOffset, getHeight()-vPadding, hPadding+startOffset+actualDurationWidth*timingParameters.easingStart, getHeight()-vPadding, paint);

		path.rewind();
		paint.setPathEffect(dashPathEffect);
		paint.setColor(0xffffcd00);
		paint.setStrokeCap(Paint.Cap.ROUND);
		path.moveTo(hPadding+startOffset, vPadding);
		path.lineTo(hPadding+startOffset, getHeight()-vPadding);
		path.moveTo(hPadding+endOffset, vPadding);
		path.lineTo(hPadding+endOffset, getHeight()-vPadding);
		canvas.drawPath(path, paint);

		paint.setPathEffect(null);
		paint.setStrokeCap(Paint.Cap.BUTT);
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
		canvas.drawCircle(hPadding+startOffset, vPadding, AndroidUtilities.dp(5), paint);
		canvas.drawCircle(hPadding+endOffset, vPadding, AndroidUtilities.dp(5), paint);
		canvas.drawCircle(hPadding+startOffset, getHeight()-vPadding, AndroidUtilities.dp(5), paint);
		canvas.drawCircle(hPadding+endOffset, getHeight()-vPadding, AndroidUtilities.dp(5), paint);
		paint.setColor(0xffffcd00);
		canvas.drawCircle(hPadding+startOffset, vPadding, AndroidUtilities.dp(3), paint);
		canvas.drawCircle(hPadding+endOffset, vPadding, AndroidUtilities.dp(3), paint);
		canvas.drawCircle(hPadding+startOffset, getHeight()-vPadding, AndroidUtilities.dp(3), paint);
		canvas.drawCircle(hPadding+endOffset, getHeight()-vPadding, AndroidUtilities.dp(3), paint);

		paint.setColor(Theme.getColor(Theme.key_chat_goDownButtonShadow));
		paint.setAlpha(51);
		canvas.drawBitmap(circleShadow, hPadding+startOffset+actualDurationWidth*(1f-timingParameters.easingEnd)-circleShadow.getWidth()/2f, vPadding-circleShadow.getHeight()/2f, paint);
		canvas.drawBitmap(circleShadow, hPadding+startOffset+actualDurationWidth*timingParameters.easingStart-circleShadow.getWidth()/2f, getHeight()-vPadding-circleShadow.getHeight()/2f, paint);
		canvas.drawBitmap(ovalShadow, hPadding+startOffset-ovalShadow.getWidth()/2f, vPadding+paddedHeight/2f-ovalShadow.getHeight()/2f, paint);
		canvas.drawBitmap(ovalShadow, hPadding+endOffset-ovalShadow.getWidth()/2f, vPadding+paddedHeight/2f-ovalShadow.getHeight()/2f, paint);

		paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
		canvas.drawCircle(hPadding+startOffset+actualDurationWidth*(1f-timingParameters.easingEnd), vPadding, AndroidUtilities.dp(9), paint);
		canvas.drawCircle(hPadding+startOffset+actualDurationWidth*timingParameters.easingStart, getHeight()-vPadding, AndroidUtilities.dp(9), paint);
		rect.set(hPadding+startOffset-AndroidUtilities.dp(6), vPadding+paddedHeight/2f-AndroidUtilities.dp(13), hPadding+startOffset+AndroidUtilities.dp(6), vPadding+paddedHeight/2f+AndroidUtilities.dp(13));
		canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
		rect.set(hPadding+endOffset-AndroidUtilities.dp(6), vPadding+paddedHeight/2f-AndroidUtilities.dp(13), hPadding+endOffset+AndroidUtilities.dp(6), vPadding+paddedHeight/2f+AndroidUtilities.dp(13));
		canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);

		paint.setColor(Theme.getColor(Theme.key_player_progress));
		paint.setTextSize(AndroidUtilities.dp(12));
		canvas.drawText(percentTop, hPadding+startOffset+actualDurationWidth*(1f-timingParameters.easingEnd)-paint.measureText(percentTop)/2f, AndroidUtilities.dp(28), paint);
		canvas.drawText(percentBottom, hPadding+startOffset+actualDurationWidth*timingParameters.easingStart-paint.measureText(percentBottom)/2f, getHeight()-AndroidUtilities.dp(17), paint);
		paint.setColor(0xffffcd00);
		float leftWidth=paint.measureText(msLeft);
		float rightWidth=paint.measureText(msRight);
		float leftX, rightX, msY=vPadding+paddedHeight/2f+(-paint.ascent())/2f;
		leftX=hPadding+startOffset+AndroidUtilities.dp(12);
		rightX=hPadding+endOffset-rightWidth-AndroidUtilities.dp(12);
		if(leftWidth+rightWidth+AndroidUtilities.dp(36)>actualDurationWidth){
			float _leftX=hPadding+startOffset-leftWidth-AndroidUtilities.dp(12);
			if(_leftX>hPadding)
				leftX=_leftX;
			float _rightX=hPadding+endOffset+AndroidUtilities.dp(12);
			if(_rightX+rightWidth+hPadding<getWidth())
				rightX=_rightX;
		}
		canvas.drawText(msLeft, leftX, msY, paint);
		canvas.drawText(msRight, rightX, msY, paint);
	}
}
