package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class VoIPNotificationsLayout extends LinearLayout {

    private HashMap<String, TextView> viewsByTag = new HashMap<>();
    private ArrayList<TextView> viewToAdd = new ArrayList<>();
    private ArrayList<TextView> viewToRemove = new ArrayList<>();
    private TransitionSet transitionSet;
    private boolean lockAnimation;
    private boolean wasChanged;
    private Runnable onViewsUpdated;
    private GradientBackgroundFrameLayout gradientLayout;
    private boolean isVideo;

    public VoIPNotificationsLayout(Context context, GradientBackgroundFrameLayout gradientLayout) {
        super(context);
        this.gradientLayout=gradientLayout;
        setOrientation(VERTICAL);

        transitionSet = new TransitionSet();
        transitionSet.addTransition(new ChangeBounds().setDuration(200))
                .addTransition(new Visibility() {
                    @Override
                    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                        AnimatorSet set = new AnimatorSet();
                        ObjectAnimator scale1=ObjectAnimator.ofFloat(view, AndroidUtilities.VIEW_SCALE, 0.4f, 1.02f);
                        scale1.setInterpolator(new CubicBezierInterpolator(0.48, 0.00, 0.35, 0.59));
                        scale1.setDuration(200);

                        ObjectAnimator scale2=ObjectAnimator.ofFloat(view, AndroidUtilities.VIEW_SCALE, 1f);
                        scale2.setInterpolator(new CubicBezierInterpolator(0.25, -3.26, 0.57, 1.00));
                        scale2.setDuration(133);

                        ObjectAnimator alpha=ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).setDuration(100);
                        alpha.setInterpolator(new LinearInterpolator());

                        AnimatorSet scale=new AnimatorSet();
                        scale.playSequentially(scale1, scale2);
                        set.playTogether(scale, alpha);

                        return set;
                    }

                    @Override
                    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues){
                        ObjectAnimator scale=ObjectAnimator.ofFloat(view, AndroidUtilities.VIEW_SCALE, 0.7f).setDuration(250);
                        scale.setInterpolator(new CubicBezierInterpolator(0.17, 0.00, 0.18, 1.00));
                        ObjectAnimator alpha=ObjectAnimator.ofFloat(view, View.ALPHA, 0f).setDuration(83);
                        alpha.setInterpolator(new LinearInterpolator());
                        AnimatorSet set=new AnimatorSet();
                        set.playTogether(scale, alpha);
                        return set;
                    }
                }.setDuration(200));
        transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
    }

    public void addNotification(String text, String tag, boolean animated) {
        if (viewsByTag.get(tag) != null) {
            return;
        }

        TextView view=new TextView(getContext());
        view.setSingleLine();
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTextColor(0xffffffff);
        view.setText(text);
        view.setTag(tag);
        view.setGravity(Gravity.CENTER);
        int pad=AndroidUtilities.dp(12);
        view.setPadding(pad, 0, pad, 0);
        view.setBackground(getBackgroundDrawableForView(view));
        viewsByTag.put(tag, view);

//        if (animated) {
//            view.startAnimation();
//        }
        if (lockAnimation) {
            viewToAdd.add(view);
        } else {
            wasChanged = true;
            addView(view, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_HORIZONTAL, 4, 0, 0, 4));
        }
    }

    public void removeNotification(String tag) {
        TextView view = viewsByTag.remove(tag);
        if (view != null) {
            if (lockAnimation) {
                if (viewToAdd.remove(view)) {
                    return;
                }
                viewToRemove.add(view);
            } else {
                wasChanged = true;
                removeView(view);
                gradientLayout.unregisterChild(view);
            }
        }
    }

    private void lock() {
        lockAnimation = true;
        AndroidUtilities.runOnUIThread(() -> {
            lockAnimation = false;
            runDelayed();
        }, 700);
    }

    private void runDelayed() {
        if (viewToAdd.isEmpty() && viewToRemove.isEmpty()) {
            return;
        }
        ViewParent parent = getParent();
        if (parent != null) {
            TransitionManager.beginDelayedTransition(this, transitionSet);
        }

        for (int i = 0; i < viewToAdd.size(); i++) {
            TextView view = viewToAdd.get(i);
            for (int j = 0; j < viewToRemove.size(); j++) {
                if (view.getTag().equals(viewToRemove.get(j).getTag())) {
                    viewToAdd.remove(i);
                    viewToRemove.remove(j);
                    i--;
                    break;
                }
            }
        }

        for (int i = 0; i < viewToAdd.size(); i++) {
            addView(viewToAdd.get(i), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_HORIZONTAL, 4, 0, 0, 4));
        }
        for (int i = 0; i < viewToRemove.size(); i++) {
            removeView(viewToRemove.get(i));
            gradientLayout.unregisterChild(viewToRemove.get(i));
        }
        viewsByTag.clear();
        for (int i = 0; i < getChildCount(); i++) {
            TextView v = (TextView) getChildAt(i);
            viewsByTag.put((String)v.getTag(), v);
        }
        viewToAdd.clear();
        viewToRemove.clear();
        lock();
        if (onViewsUpdated != null) {
            onViewsUpdated.run();
        }
    }

    public void beforeLayoutChanges() {
        wasChanged = false;
        if (!lockAnimation) {
            ViewParent parent = getParent();
            if (parent != null) {
                TransitionManager.beginDelayedTransition(this, transitionSet);
            }
        }
    }

    public void animateLayoutChanges() {
        if (wasChanged) {
            lock();
        }
        wasChanged = false;
    }

    public int getChildsHight() {
        int n = getChildCount();
        return (n > 0 ? AndroidUtilities.dp(16) : 0) + n * AndroidUtilities.dp(32);
    }

    public void setOnViewsUpdated(Runnable onViewsUpdated) {
        this.onViewsUpdated = onViewsUpdated;
    }

    public void setIsVideo(boolean isVideo){
        if(this.isVideo==isVideo)
            return;

        this.isVideo=isVideo;
        for(int i=0;i<getChildCount();i++){
            View v=getChildAt(i);
            gradientLayout.unregisterChild(v);
            v.setBackground(getBackgroundDrawableForView(v));
        }
    }

    private Drawable getBackgroundDrawableForView(View v){
        if(isVideo){
            return Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0x64000000);
        }else{
            Drawable bg=gradientLayout.newChildBackgroundDrawable(v, true, 12);
            bg.setAlpha(180);
            return bg;
        }
    }
}
