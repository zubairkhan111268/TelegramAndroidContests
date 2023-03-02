package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPStatusTextView extends FrameLayout {

    private TextView[] textView = new TextView[2];
    private TextView reconnectTextView;
    private VoIPTimerView timerView;

    private CharSequence nextTextToSet;
    private boolean nextTextHasEllipsis;
    private boolean animationInProgress;

    private Animator animator;
    private boolean timerShowing;
    private Animator reconnectAnimator;
    private boolean isVideo;
    private final GradientBackgroundFrameLayout gradientLayout;
    private boolean reconnectingShown;

    public VoIPStatusTextView(@NonNull Context context, GradientBackgroundFrameLayout gradientLayout) {
        super(context);
        this.gradientLayout=gradientLayout;
        for (int i = 0; i < 2; i++) {
            textView[i] = new TextView(context);
            textView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView[i].setTextColor(Color.WHITE);
            textView[i].setGravity(Gravity.CENTER_HORIZONTAL);
            textView[i].setCompoundDrawablePadding(AndroidUtilities.dp(4));
            addView(textView[i]);
        }

        reconnectTextView = new TextView(context);
        reconnectTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        reconnectTextView.setTextColor(Color.WHITE);
        reconnectTextView.setGravity(Gravity.CENTER);
        reconnectTextView.setText(LocaleController.getString("VoipWeakNetwork", R.string.VoipWeakNetwork));
        reconnectTextView.setVisibility(View.GONE);
        int pad=AndroidUtilities.dp(12);
        reconnectTextView.setPadding(pad, 0, pad, 0);
        Drawable bg=gradientLayout.newChildBackgroundDrawable(reconnectTextView, true, 12);
        bg.setAlpha(180);
        reconnectTextView.setBackground(bg);
        addView(reconnectTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        timerView = new VoIPTimerView(context);
        addView(timerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

    }

    public void setText(String nextString, boolean ellipsis, boolean animated) {
        if (TextUtils.isEmpty(textView[0].getText())) {
            animated = false;
        }

        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            animationInProgress = false;
            textView[0].setText(nextString);
            textView[0].setCompoundDrawablesWithIntrinsicBounds(null, null, ellipsis ? new VoIPEllipsisDrawable() : null, null);
            textView[0].setVisibility(View.VISIBLE);
            textView[1].setVisibility(View.GONE);
            timerView.setVisibility(View.GONE);

        } else {
            if (animationInProgress) {
                nextTextToSet = nextString;
                nextTextHasEllipsis=ellipsis;
                return;
            }

            if (timerShowing) {
                textView[0].setText(nextString);
                textView[0].setCompoundDrawablesWithIntrinsicBounds(null, null, ellipsis ? new VoIPEllipsisDrawable() : null, null);
                replaceViews(timerView, textView[0], null);
            } else {
                if (!textView[0].getText().equals(nextString)) {
                    textView[1].setText(nextString);
                    textView[1].setCompoundDrawablesWithIntrinsicBounds(null, null, ellipsis ? new VoIPEllipsisDrawable() : null, null);
                    replaceViews(textView[0], textView[1], () -> {
                        TextView v = textView[0];
                        textView[0] = textView[1];
                        textView[1] = v;
                    });
                }
            }
        }
    }

    public void showTimer(boolean animated) {
        if (TextUtils.isEmpty(textView[0].getText())) {
            animated = false;
        }
        if (timerShowing) {
            return;
        }
        timerView.updateTimer();
        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            timerShowing = true;
            animationInProgress = false;
            textView[0].setVisibility(View.GONE);
            textView[1].setVisibility(View.GONE);
            timerView.setVisibility(View.VISIBLE);
        } else {
            if (animationInProgress) {
                nextTextToSet = "timer";
                return;
            }
            timerShowing = true;
            replaceViews(textView[0], timerView, null);
        }
    }


    private void replaceViews(View out, View in, Runnable onEnd) {
        out.setVisibility(View.VISIBLE);
        in.setVisibility(View.VISIBLE);

        in.setTranslationY(AndroidUtilities.dp(15));
        in.setAlpha(0f);
        animationInProgress = true;
        AnimatorSet set=new AnimatorSet();
        CubicBezierInterpolator interpolator=new CubicBezierInterpolator(0.32, 0.00, 0.21, 1.00);
        ObjectAnimator transIn=ObjectAnimator.ofFloat(in, TRANSLATION_Y, AndroidUtilities.dp(3), 0).setDuration(267);
        transIn.setInterpolator(interpolator);
        ObjectAnimator transOut=ObjectAnimator.ofFloat(out, TRANSLATION_Y, 0, AndroidUtilities.dp(-3)).setDuration(267);
        transOut.setInterpolator(interpolator);
        ObjectAnimator alphaIn=ObjectAnimator.ofFloat(in, ALPHA, 0, 1).setDuration(67);
        alphaIn.setInterpolator(new LinearInterpolator());
        ObjectAnimator alphaOut=ObjectAnimator.ofFloat(out, ALPHA, 1, 0).setDuration(67);
        alphaOut.setInterpolator(new LinearInterpolator());
        set.playTogether(transIn, transOut, alphaIn, alphaOut);

        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                out.setVisibility(View.GONE);
                out.setAlpha(1f);
                out.setTranslationY(0);
                out.setScaleY(1f);
                out.setScaleX(1f);

                in.setAlpha(1f);
                in.setTranslationY(0);
                in.setVisibility(View.VISIBLE);
                in.setScaleY(1f);
                in.setScaleX(1f);

                if (onEnd != null) {
                    onEnd.run();
                }
                animationInProgress = false;
                if (nextTextToSet != null) {
                    if (nextTextToSet.equals("timer")) {
                        showTimer(true);
                    } else {
                        textView[1].setText(nextTextToSet);
                        textView[1].setCompoundDrawablesWithIntrinsicBounds(null, null, nextTextHasEllipsis ? new VoIPEllipsisDrawable() : null, null);
                        replaceViews(textView[0], textView[1], () -> {
                            TextView v = textView[0];
                            textView[0] = textView[1];
                            textView[1] = v;
                        });
                    }
                    nextTextToSet = null;
                }
            }
        });
        set.start();
        animator=set;
    }

    public void setSignalBarCount(int count) {
        timerView.setSignalBarCount(count);
    }

    public void showReconnect(boolean showReconnecting, boolean animated) {
        if(showReconnecting==reconnectingShown)
            return;
        if(reconnectAnimator!=null)
            reconnectAnimator.cancel();

        reconnectingShown=showReconnecting;

        if (!animated) {
            reconnectTextView.setVisibility(showReconnecting ? View.VISIBLE : View.GONE);
        } else {
            if (showReconnecting) {
                reconnectTextView.setVisibility(View.VISIBLE);
                AnimatorSet set = new AnimatorSet();
                ObjectAnimator scale1=ObjectAnimator.ofFloat(reconnectTextView, AndroidUtilities.VIEW_SCALE, 0.4f, 1.02f);
                scale1.setInterpolator(new CubicBezierInterpolator(0.48, 0.00, 0.35, 0.59));
                scale1.setDuration(200);

                ObjectAnimator scale2=ObjectAnimator.ofFloat(reconnectTextView, AndroidUtilities.VIEW_SCALE, 1f);
                scale2.setInterpolator(new CubicBezierInterpolator(0.25, -3.26, 0.57, 1.00));
                scale2.setDuration(133);

                ObjectAnimator alpha=ObjectAnimator.ofFloat(reconnectTextView, View.ALPHA, 0f, 1f).setDuration(100);
                alpha.setInterpolator(new LinearInterpolator());

                AnimatorSet scale=new AnimatorSet();
                scale.playSequentially(scale1, scale2);
                set.playTogether(scale, alpha);
                set.addListener(new AnimatorListenerAdapter(){
                    @Override
                    public void onAnimationEnd(Animator animation){
                        reconnectAnimator=null;
                    }
                });
                set.start();
                reconnectAnimator=set;
            } else {
                ObjectAnimator scale=ObjectAnimator.ofFloat(reconnectTextView, AndroidUtilities.VIEW_SCALE, 0.7f).setDuration(250);
                scale.setInterpolator(new CubicBezierInterpolator(0.17, 0.00, 0.18, 1.00));
                ObjectAnimator alpha=ObjectAnimator.ofFloat(reconnectTextView, View.ALPHA, 0f).setDuration(83);
                alpha.setInterpolator(new LinearInterpolator());
                AnimatorSet set=new AnimatorSet();
                set.playTogether(scale, alpha);
                set.addListener(new AnimatorListenerAdapter(){
                    @Override
                    public void onAnimationEnd(Animator animation){
                        reconnectAnimator=null;
                        reconnectTextView.setVisibility(GONE);
                    }
                });
                set.start();
                reconnectAnimator=set;
            }
        }
    }

    public void setIsVideo(boolean isVideo){
        if(this.isVideo==isVideo)
            return;
        this.isVideo=isVideo;

        if(isVideo){
            gradientLayout.unregisterChild(reconnectTextView);
            reconnectTextView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0x64000000));
            textView[0].setShadowLayer(AndroidUtilities.dpf2(1), 0, 0, 0x33000000);
            textView[1].setShadowLayer(AndroidUtilities.dpf2(1), 0, 0, 0x33000000);
            timerView.getTextPaint().setShadowLayer(AndroidUtilities.dpf2(1), 0, 0, 0x33000000);
        }else{
            Drawable bg=gradientLayout.newChildBackgroundDrawable(reconnectTextView, true, 12);
            bg.setAlpha(180);
            reconnectTextView.setBackground(bg);
            textView[0].setShadowLayer(0, 0, 0, 0);
            textView[1].setShadowLayer(0, 0, 0, 0);
            timerView.getTextPaint().setShadowLayer(0, 0, 0, 0);
        }
    }
}
