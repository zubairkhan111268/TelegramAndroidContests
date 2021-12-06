package org.telegram.ui.Components.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Path;
import android.graphics.Point;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import androidx.annotation.RequiresApi;

@RequiresApi(21)
public class ReactionAnimationSuperfluousEmojiFlyAwayOverlay extends ReactionAnimationOverlay{
	private TLRPC.TL_availableReaction reaction;
	private Point animFrom;

	public ReactionAnimationSuperfluousEmojiFlyAwayOverlay(ChatActivity abomination, ChatMessageCell cell, String reaction, Point animFrom){
		super(abomination, cell, null);
		this.reaction=abomination.getMediaDataController().getReaction(reaction);
		this.animFrom=animFrom;
	}

	@Override
	public void show(){
		windowView=new FrameLayout(activity);
		animationsWrap=new FrameLayout(activity);
		windowView.addView(animationsWrap);

		BackupImageView imageView=new BackupImageView(activity);
		animationsWrap.addView(imageView, LayoutHelper.createFrame(14, 14, Gravity.TOP | Gravity.LEFT, -7, -7, 0, 0));
		if(reaction!=null)
			ReactionUtils.loadWebpIntoImageView(reaction.static_icon, reaction, imageView);

		imageView.setTranslationX(animFrom.x);
		imageView.setTranslationY(animFrom.y);

		Path path=new Path();
		path.moveTo(animFrom.x, animFrom.y);
		float pathW=AndroidUtilities.dp(150), pathH=AndroidUtilities.dp(50);
		if(animFrom.x>AndroidUtilities.displaySize.x/2)
			path.cubicTo(animFrom.x-pathW/5f, animFrom.y-pathH, animFrom.x-pathW+pathW/5f, animFrom.y-pathH, animFrom.x-pathW, animFrom.y);
		else
			path.cubicTo(animFrom.x+pathW/2f, animFrom.y-pathH, animFrom.x+pathW-pathW/2f, animFrom.y-pathH, animFrom.x+pathW, animFrom.y);

		AnimatorSet set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofFloat(imageView, View.TRANSLATION_X, View.TRANSLATION_Y, path),
				ObjectAnimator.ofFloat(imageView, View.SCALE_X, 1f, 2f, 0.1f),
				ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 1f, 2f, 0.1f),
				ObjectAnimator.ofFloat(imageView, View.ROTATION, animFrom.x>AndroidUtilities.displaySize.x/2 ? -45f : 45f)
		);
		set.setDuration(250);
		set.setInterpolator(CubicBezierInterpolator.EASE_OUT);
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				dismiss();
			}
		});
		set.start();

		showWindow();
	}
}
