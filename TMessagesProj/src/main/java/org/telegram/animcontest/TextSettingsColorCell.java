/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.animcontest;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Locale;

import androidx.core.graphics.ColorUtils;

public class TextSettingsColorCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private boolean needDivider;
    private boolean canDisable;
    private int color;
    private RectF rect=new RectF();
    private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    public TextSettingsColorCell(Context context) {
        this(context, 21);
    }

    public TextSettingsColorCell(Context context, int padding) {
        super(context);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, 0, padding, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity(Gravity.CENTER);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
//        if(Build.VERSION.SDK_INT>=21){
//            valueTextView.setOutlineProvider(new ViewOutlineProvider(){
//                @Override
//                public void getOutline(View view, Outline outline){
//                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(6));
//                }
//            });
//            valueTextView.setClipToOutline(true);
//        }
        addView(valueTextView, LayoutHelper.createFrame(76, 28, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding, 0, padding, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    	super.onMeasure(MeasureSpec.getSize(widthMeasureSpec) | MeasureSpec.EXACTLY, (AndroidUtilities.dp(50)+(needDivider ? 1 : 0)) | MeasureSpec.EXACTLY);
//        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

//        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
//        int width = availableWidth / 2;
//        if (valueImageView.getVisibility() == VISIBLE) {
//            valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
//        }
//        if (valueTextView.getVisibility() == VISIBLE) {
//            valueTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
//            width = availableWidth - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(8);
//        } else {
//            width = availableWidth;
//        }
//        textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    public TextView getTextView() {
        return textView;
    }

    public void setCanDisable(boolean value) {
        canDisable = value;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public void setText(String text, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndColor(String text, int color, boolean divider) {
        textView.setText(text);
        valueTextView.setText(String.format(Locale.US, "#%06X", color& 0x00FFFFFF));
        valueTextView.setVisibility(VISIBLE);
//        valueTextView.setBackgroundColor(color);
		this.color=color;
        valueTextView.setTextColor(ColorUtils.calculateContrast(0xFFFFFFFF, color)>ColorUtils.calculateContrast(0xFF000000, color) ? 0xFFFFFFFF: 0xFF000000);
        needDivider = divider;
        setWillNotDraw(!divider);
        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setColor(color);
        rect.set(valueTextView.getLeft(), valueTextView.getTop(), valueTextView.getRight(), valueTextView.getBottom());
        int radius=AndroidUtilities.dp(6);
        canvas.drawRoundRect(rect, radius, radius, paint);

        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(isEnabled());
    }
}
