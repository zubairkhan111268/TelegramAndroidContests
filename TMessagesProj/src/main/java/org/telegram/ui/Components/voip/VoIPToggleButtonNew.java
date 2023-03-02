package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

public class VoIPToggleButtonNew extends FrameLayout {
    public static final int COLOR_USE_GRADIENT=0;

    private FrameLayout textLayoutContainer;
    private TextView[] textView = new TextView[2];

    private float replaceProgress;
    private Animator replaceAnimator;

    private int currentIconColor=0xff00ff00;
    private int currentBackgroundColor;
    private String currentText;
    public int animationDelay;

    private Drawable rippleDrawable;

    private boolean checkableForAccessibility;
    private boolean checkable;
    private boolean checked;
    private float checkedProgress;
    private int backgroundCheck1;
    private int backgroundCheck2;

    private float radius=52f;
    private ValueAnimator checkAnimator;

    private RLottieDrawable currentDrawable;
    private RLottieImageView iconView;
    private FrameLayout iconWrap;
    private View gradientBackground, colorBackground, gradientIconBackground, colorIconBackground;
    private ShapeDrawable colorBackgroundDrawable, colorIconBackgroundDrawable;
    private GradientBackgroundFrameLayout gradientLayout;
    private AnimatorSet colorChangeAnimator, clickAnimator;
    private float buttonScale=1;
    private boolean useLayers=true;
    private Paint iconPaint, paintForTransparencyHack;
    private RectF tmpRect=new RectF();

    public static final Property<VoIPToggleButtonNew, Float> BUTTON_SCALE=new Property<VoIPToggleButtonNew, Float>(Float.class, "buttonScale"){
        @Override
        public Float get(VoIPToggleButtonNew object){
            return object.buttonScale;
        }

        @Override
        public void set(VoIPToggleButtonNew object, Float value){
            object.buttonScale=value;
            object.iconWrap.setScaleX(value);
            object.iconWrap.setScaleY(value);
            object.gradientIconBackground.setScaleX(value);
            object.gradientIconBackground.setScaleY(value);
            object.colorIconBackground.setScaleX(value);
            object.colorIconBackground.setScaleY(value);
        }
    };

    public static final Property<VoIPToggleButtonNew, Float> TEXT_ALPHA=new Property<VoIPToggleButtonNew, Float>(Float.class, "textAlpha"){
        @Override
        public Float get(VoIPToggleButtonNew object){
            return object.textLayoutContainer.getAlpha();
        }

        @Override
        public void set(VoIPToggleButtonNew object, Float value){
            object.textLayoutContainer.setAlpha(value);
        }
    };

    public VoIPToggleButtonNew(@NonNull Context context, GradientBackgroundFrameLayout gradientLayout) {
        super(context);
        setWillNotDraw(false);
        this.gradientLayout=gradientLayout;

        textLayoutContainer = new FrameLayout(context);
        addView(textLayoutContainer);

        for (int i = 0; i < 2; i++) {
            TextView textView = new TextView(context);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setTextColor(Color.WHITE);
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            textLayoutContainer.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, radius + 4, 0, 0));
            this.textView[i] = textView;
        }
        textView[1].setVisibility(View.GONE);
        iconWrap=new FrameLayout(context);

        gradientBackground=new View(context);
        gradientBackground.setBackground(gradientLayout.newChildBackgroundDrawable(gradientBackground, false, 26));
        iconWrap.addView(gradientBackground, 0, LayoutHelper.createFrame(52, 52, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        colorBackground=new View(context);
        colorBackground.setBackground(colorBackgroundDrawable=new ShapeDrawable(new OvalShape()));
        colorBackground.setVisibility(GONE);
        iconWrap.addView(colorBackground, 1, LayoutHelper.createFrame(52, 52, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        colorIconBackground=new View(context);
        colorIconBackground.setBackground(colorIconBackgroundDrawable=new ShapeDrawable(new OvalShape()));
        addView(colorIconBackground, 0, LayoutHelper.createFrame(52, 52, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        gradientIconBackground=new View(context);
        gradientIconBackground.setBackground(gradientLayout.newChildBackgroundDrawable(iconWrap, true, 26));
        gradientIconBackground.setVisibility(GONE);
        addView(gradientIconBackground, 1, LayoutHelper.createFrame(52, 52, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        iconView=new RLottieImageView(context);
        iconPaint=new Paint();
        iconPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        iconView.setLayerType(LAYER_TYPE_HARDWARE, iconPaint);
        iconWrap.addView(iconView);
        iconWrap.setLayerType(LAYER_TYPE_HARDWARE, null);
        addView(iconWrap, LayoutHelper.createFrame(52, 52, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        paintForTransparencyHack=new Paint();
        paintForTransparencyHack.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime){
        if(child==colorIconBackground && currentBackgroundColor!=COLOR_USE_GRADIENT && colorBackgroundDrawable.getPaint().getAlpha()<255){
            float scaledW=child.getWidth()*child.getScaleX();
            float scaledH=child.getHeight()*child.getScaleY();
            float x=child.getX()+child.getWidth()/2f-scaledW/2f;
            float y=child.getY()+child.getHeight()/2f-scaledH/2f;
            tmpRect.set(x, y, x+scaledW, y+scaledH);
            canvas.saveLayer(tmpRect, null, Canvas.ALL_SAVE_FLAG);
            boolean res=super.drawChild(canvas, child, drawingTime);
            Bitmap bitmap=iconView.getAnimatedDrawable().getAnimatedBitmap();
            canvas.drawBitmap(bitmap, null, tmpRect, paintForTransparencyHack);
            canvas.restore();
            return res;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void setBackgroundColor(int backgroundColor, int backgroundColorChecked) {
        backgroundCheck1 = backgroundColor;
        backgroundCheck2 = backgroundColorChecked;
        invalidate();
    }

    public void setLoopAnimation(boolean loop){
        iconView.setAutoRepeat(loop);
    }

    public void forceStartAnimation(){
        currentDrawable.setCurrentFrame(0, false);
        iconView.playAnimation();
    }

    public void setData(RLottieDrawable icon, int iconColor, int backgroundColor, String text, boolean animateIcon, boolean animated) {
        if (getVisibility() != View.VISIBLE) {
            animated = false;
            setVisibility(View.VISIBLE);
        }

        if (currentDrawable==icon && currentIconColor == iconColor && (/*checkable ||*/ currentBackgroundColor == backgroundColor) && (currentText != null && currentText.equals(text))) {
            return;
        }

        if(colorChangeAnimator!=null){
            colorChangeAnimator.cancel();
            colorChangeAnimator=null;
        }

        ArrayList<Animator> animators=new ArrayList<>();
        AnimatorSet set=new AnimatorSet();
        int radius=AndroidUtilities.dp(26);
        boolean bgIsTransparent=(backgroundColor!=COLOR_USE_GRADIENT && (backgroundColor & 0xFF000000)!=0xFF000000) || (currentBackgroundColor!=COLOR_USE_GRADIENT && (currentBackgroundColor & 0xFF000000)!=0xFF000000);

        if(currentBackgroundColor!=backgroundColor){
            if(backgroundColor==COLOR_USE_GRADIENT){
                gradientBackground.setVisibility(VISIBLE);
                if(iconWrap.indexOfChild(gradientBackground)==0){
                    iconWrap.removeView(gradientBackground);
                    iconWrap.addView(gradientBackground, 1);
                }
            }else{
                colorBackgroundDrawable.getPaint().setColor(backgroundColor);
                colorBackground.setVisibility(VISIBLE);
                colorBackground.invalidate();
                if(iconWrap.indexOfChild(colorBackground)==0){
                    iconWrap.removeView(colorBackground);
                    iconWrap.addView(colorBackground, 1);
                }
            }
            if(bgIsTransparent){
                if(backgroundColor==COLOR_USE_GRADIENT){
                    colorBackground.setVisibility(GONE);
                }else{
                    gradientBackground.setVisibility(GONE);
                    if(currentBackgroundColor==COLOR_USE_GRADIENT){
                        colorBackgroundDrawable.getPaint().setColor(backgroundColor);
                    }else{
                        ObjectAnimator anim=ObjectAnimator.ofInt(colorBackgroundDrawable.getPaint(), "color", currentBackgroundColor, backgroundColor);
                        anim.setEvaluator(new ArgbEvaluator());
                        anim.addUpdateListener(animation->colorBackground.invalidate());
                        animators.add(anim);
                    }
                }
            }else{
                if(backgroundColor==COLOR_USE_GRADIENT){
                    if(animated && Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
                        Animator reveal=ViewAnimationUtils.createCircularReveal(gradientBackground, radius, radius, 0, radius);
                        reveal.addListener(new AnimatorListenerAdapter(){
                            @Override
                            public void onAnimationEnd(Animator animation){
                                if(colorChangeAnimator==set)
                                    colorBackground.setVisibility(GONE);
                            }
                        });
                        animators.add(reveal);
                    }else{
                        colorBackground.setVisibility(GONE);
                    }
                }else{
                    if(animated && Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
                        Animator reveal=ViewAnimationUtils.createCircularReveal(colorBackground, radius, radius, 0, radius);
                        reveal.addListener(new AnimatorListenerAdapter(){
                            @Override
                            public void onAnimationEnd(Animator animation){
                                if(colorChangeAnimator==set)
                                    gradientBackground.setVisibility(GONE);
                            }
                        });
                        animators.add(reveal);
                    }else{
                        gradientBackground.setVisibility(GONE);
                    }
                }
            }
        }

        if(currentIconColor!=iconColor){
            if(iconColor==COLOR_USE_GRADIENT){
                gradientIconBackground.setVisibility(VISIBLE);
                if(indexOfChild(gradientIconBackground)==0){
                    removeView(gradientIconBackground);
                    addView(gradientIconBackground, 1);
                }
            }else{
                colorIconBackground.setVisibility(VISIBLE);
                colorIconBackgroundDrawable.getPaint().setColor(iconColor);
                colorIconBackground.invalidate();
                if(indexOfChild(colorIconBackground)==0){
                    removeView(colorIconBackground);
                    addView(colorIconBackground, 1);
                }
            }
            if(bgIsTransparent){
                if(iconColor==COLOR_USE_GRADIENT){
                    colorIconBackground.setVisibility(GONE);
                }else{
                    gradientIconBackground.setVisibility(GONE);
                    if(currentIconColor==COLOR_USE_GRADIENT){
                        colorIconBackgroundDrawable.getPaint().setColor(iconColor);
                    }else{
                        ObjectAnimator anim=ObjectAnimator.ofInt(colorIconBackgroundDrawable.getPaint(), "color", currentIconColor, iconColor);
                        anim.setEvaluator(new ArgbEvaluator());
                        anim.addUpdateListener(animation->colorIconBackground.invalidate());
                        animators.add(anim);
                    }
                }
            }else{
                if(iconColor==COLOR_USE_GRADIENT){
                    if(animated && Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
                        Animator reveal=ViewAnimationUtils.createCircularReveal(gradientIconBackground, radius, radius, 0, radius);
                        reveal.addListener(new AnimatorListenerAdapter(){
                            @Override
                            public void onAnimationEnd(Animator animation){
                                if(colorChangeAnimator==set)
                                    colorIconBackground.setVisibility(GONE);
                            }
                        });
                        animators.add(reveal);
                    }else{
                        colorIconBackground.setVisibility(GONE);
                    }
                }else{
                    if(animated && Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
                        Animator reveal=ViewAnimationUtils.createCircularReveal(colorIconBackground, radius, radius, 0, radius);
                        reveal.addListener(new AnimatorListenerAdapter(){
                            @Override
                            public void onAnimationEnd(Animator animation){
                                if(colorChangeAnimator==set)
                                    gradientIconBackground.setVisibility(GONE);
                            }
                        });
                        animators.add(reveal);
                    }else{
                        gradientIconBackground.setVisibility(GONE);
                    }
                }
            }
        }

        if(!animators.isEmpty()){
            set.playTogether(animators);
            set.setDuration(267);
            set.setInterpolator(new CubicBezierInterpolator(0.36, 0.00, 0.15, 1.00));
            set.addListener(new AnimatorListenerAdapter(){
                @Override
                public void onAnimationEnd(Animator animation){
                    colorChangeAnimator=null;
                }
            });
            set.start();
            colorChangeAnimator=set;
        }

        if(animated && animateIcon){
            icon.setCurrentFrame(0, false);
            iconView.setAnimation(icon);
            iconView.playAnimation();
        }else{
            icon.setCurrentFrame(icon.getFramesCount()-1, false);
            iconView.setAnimation(icon);
            iconView.stopAnimation();
        }

        if (Color.alpha(backgroundColor) == 255 && AndroidUtilities.computePerceivedBrightness(backgroundColor) > 0.5) {
            rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(radius), 0, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.1f *(float) 1.0)));
            rippleDrawable.setCallback(this);
        } else {
            rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(radius), 0, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f *(float) 1.0)));
            rippleDrawable.setCallback(this);
        }

        if (replaceAnimator != null) {
            replaceAnimator.cancel();
        }

        currentDrawable=icon;
        currentIconColor = iconColor;
        currentBackgroundColor = backgroundColor;
        currentText = text;

        if (!animated) {
            textView[0].setText(text);
            replaceProgress = 0f;
            invalidate();
        } else {
            boolean animateText = !textView[0].getText().toString().equals(text);

            if (!animateText) {
                textView[0].setText(text);
            } else {
                textView[1].setText(text);
                textView[1].setVisibility(View.VISIBLE);
                textView[1].setAlpha(0);
                AnimatorSet replaceSet=new AnimatorSet();
                CubicBezierInterpolator interpolator=new CubicBezierInterpolator(0.33, 0.00, 0.00, 1.00);
                ObjectAnimator transOut=ObjectAnimator.ofFloat(textView[0], TRANSLATION_Y, 0, AndroidUtilities.dp(-2)).setDuration(200);
                ObjectAnimator transIn=ObjectAnimator.ofFloat(textView[1], TRANSLATION_Y, AndroidUtilities.dp(2), 0).setDuration(200);
                ObjectAnimator alphaOut=ObjectAnimator.ofFloat(textView[0], ALPHA, 1, 0).setDuration(67);
                ObjectAnimator alphaIn=ObjectAnimator.ofFloat(textView[1], ALPHA, 0, 1).setDuration(67);
                transOut.setInterpolator(interpolator);
                transIn.setInterpolator(interpolator);
                alphaOut.setInterpolator(new LinearInterpolator());
                alphaIn.setInterpolator(new LinearInterpolator());
                replaceSet.playTogether(transOut, transIn, alphaOut, alphaIn);

                replaceSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        replaceAnimator = null;
                        if (animateText) {
                            TextView tv = textView[0];
                            textView[0] = textView[1];
                            textView[1] = tv;
                            textView[1].setVisibility(View.GONE);
                        }
                        replaceProgress = 0f;
                        invalidate();
                    }
                });
                replaceAnimator=replaceSet;
                replaceSet.start();
                invalidate();
            }
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (rippleDrawable != null) {
            rippleDrawable.setState(getDrawableState());
        }
    }

    @Override
    public boolean verifyDrawable(Drawable drawable) {
        return rippleDrawable == drawable || super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (rippleDrawable != null) {
            rippleDrawable.jumpToCurrentState();
        }
    }

    public void setCheckableForAccessibility(boolean checkableForAccessibility) {
        this.checkableForAccessibility = checkableForAccessibility;
    }

    public void setChecked(boolean value, boolean animated) {
        if (checked == value) {
            return;
        }
        checked = value;
        if (checkable) {
            if (animated) {
                if (checkAnimator != null) {
                    checkAnimator.removeAllListeners();
                    checkAnimator.cancel();
                }
                checkAnimator = ValueAnimator.ofFloat(checkedProgress, checked ? 1f : 0);
                checkAnimator.addUpdateListener(valueAnimator -> {
                    checkedProgress = (float) valueAnimator.getAnimatedValue();
                    setBackgroundColor(backgroundCheck1, backgroundCheck2);
                });
                checkAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        checkedProgress = checked ? 1f : 0;
                        setBackgroundColor(backgroundCheck1, backgroundCheck2);
                    }
                });
                checkAnimator.setDuration(150);
                checkAnimator.start();
            } else {
                checkedProgress = checked ? 1f : 0;
                setBackgroundColor(backgroundCheck1, backgroundCheck2);
            }
        }
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(currentText);
        if (checkable || checkableForAccessibility) {
            info.setClassName(ToggleButton.class.getName());
            info.setCheckable(true);
            info.setChecked(checked);
        } else {
            info.setClassName(Button.class.getName());
        }
    }

    public void animateClick(){
        if(clickAnimator!=null)
            clickAnimator.cancel();
        ObjectAnimator animIn=ObjectAnimator.ofFloat(this, BUTTON_SCALE, 0.8f);
        animIn.setDuration(167);
        animIn.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.67, 1.00));
        ObjectAnimator animOut=ObjectAnimator.ofFloat(this, BUTTON_SCALE, 1);
        animOut.setDuration(167);
        animOut.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.67, 1.00));
        AnimatorSet set=new AnimatorSet();
        set.playSequentially(animIn, animOut);
        set.addListener(new AnimatorListenerAdapter(){
            @Override
            public void onAnimationEnd(Animator animation){
                clickAnimator=null;
            }
        });
        set.start();
        clickAnimator=set;
    }

    public RLottieDrawable getCurrentDrawable(){
        return currentDrawable;
    }
}