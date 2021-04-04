package org.telegram.animcontest;

import android.graphics.Canvas;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.Emoji;

/**
 * Draws a single line that never clips
 */
public class NonClippingStaticLayout extends StaticLayout{
	public NonClippingStaticLayout(CharSequence source, TextPaint paint, int width, Alignment align, float spacingmult, float spacingadd, boolean includepad){
		super(source, paint, width, align, spacingmult, spacingadd, includepad);
		if(source instanceof Spannable){
			Emoji.EmojiSpan[] spans=((Spannable)source).getSpans(0, source.length(), Emoji.EmojiSpan.class);
			for(Emoji.EmojiSpan span:spans){
				((Emoji.EmojiDrawable)span.getDrawable()).setForceDraw(true);
			}
		}
	}

//	public NonClippingStaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerwidth, Alignment align, float spacingmult, float spacingadd, boolean includepad){
//		super(source, bufstart, bufend, paint, outerwidth, align, spacingmult, spacingadd, includepad);
//	}
//
//	public NonClippingStaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerwidth, Alignment align, float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth){
//		super(source, bufstart, bufend, paint, outerwidth, align, spacingmult, spacingadd, includepad, ellipsize, ellipsizedWidth);
//	}


	public long getLineRangeForDraw(Canvas canvas) {
		return 0L;
	}
}
