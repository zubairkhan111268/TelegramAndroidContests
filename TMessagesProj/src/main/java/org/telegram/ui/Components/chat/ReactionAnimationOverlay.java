package org.telegram.ui.Components.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ReactionAnimationOverlay implements ImageReceiver.ImageReceiverDelegate{

	private ChatActivity abomination;
	private ChatMessageCell cell;
	private Rect animFrom, animTo=new Rect();
	private String reaction;
	private Runnable onDismissedAction, startAction;
	private boolean dismissed;
	private int smallAnimSize, bigAnimSize;

	private WindowManager wm;
	private FrameLayout windowView, animationsWrap, animationsWrap2;
	private Activity activity;
	private BackupImageView effectAnimationView, activateAnimationView;
	private int numAnimationsReady;
	private int listScrollOffset=0;
	private RecyclerView.OnScrollListener listScrollListener=new RecyclerView.OnScrollListener(){
		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
			listScrollOffset-=dy;
			if(animationsWrap!=null){
				animationsWrap.setTranslationY(listScrollOffset);
			}
		}
	};

	public ReactionAnimationOverlay(ChatActivity abomination, ChatMessageCell cell, Rect animFrom, String reaction, Runnable startAction){
		this.abomination=abomination;
		this.cell=cell;
		this.animFrom=animFrom;
		this.reaction=reaction;
		this.startAction=startAction;
		activity=abomination.getParentActivity();

		wm=activity.getWindowManager();
	}

	public void show(){
		cell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				cell.getViewTreeObserver().removeOnPreDrawListener(this);

				actuallyShow();

				return true;
			}
		});
	}

	public void dismiss(){
		if(dismissed)
			return;
		dismissed=true;
		abomination.getChatListView().removeOnScrollListener(listScrollListener);
		wm.removeView(windowView);
		if(onDismissedAction!=null)
			onDismissedAction.run();
	}

	public void setOnDismissedAction(Runnable onDismissedAction){
		this.onDismissedAction=onDismissedAction;
	}

	private void actuallyShow(){
		TLRPC.TL_availableReaction aReaction=abomination.getMediaDataController().getReaction(reaction);
		if(aReaction==null){
			dismissed=true;
			if(onDismissedAction!=null)
				onDismissedAction.run();
			return;
		}
		RecyclerListView chatListView=abomination.getChatListView();
		chatListView.addOnScrollListener(listScrollListener);
		updateTargetBounds();
		if(animFrom==null)
			animFrom=animTo;

		bigAnimSize=Math.round(Math.min(AndroidUtilities.dp(350), Math.min(activity.getResources().getDisplayMetrics().widthPixels, activity.getResources().getDisplayMetrics().heightPixels))*0.8f);
		smallAnimSize=bigAnimSize/2;

		windowView=new FrameLayout(activity);
		animationsWrap=new FrameLayout(activity);
		animationsWrap2=new FrameLayout(activity);
		windowView.addView(animationsWrap);
		FrameLayout.LayoutParams flp=new FrameLayout.LayoutParams(bigAnimSize, bigAnimSize, Gravity.TOP | Gravity.LEFT);
		flp.topMargin=-bigAnimSize/2;
		flp.leftMargin=-bigAnimSize/2-smallAnimSize/2;
		animationsWrap.addView(animationsWrap2, flp);

		activateAnimationView=new BackupImageView(activity);
		animationsWrap2.addView(activateAnimationView, new FrameLayout.LayoutParams(smallAnimSize, smallAnimSize, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
		effectAnimationView=new BackupImageView(activity);
		animationsWrap2.addView(effectAnimationView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		animationsWrap2.setPivotX(bigAnimSize/2f+smallAnimSize/2f);
		animationsWrap2.setPivotY(bigAnimSize/2f);

		animationsWrap2.setTranslationX(animFrom.centerX());
		animationsWrap2.setTranslationY(animFrom.centerY());
		float scale=Math.min(animFrom.width(), animFrom.height())/(float)smallAnimSize;
		animationsWrap2.setScaleX(scale);
		animationsWrap2.setScaleY(scale);

		animationsWrap2.setAlpha(0f);
		activateAnimationView.getImageReceiver().setDelegate(this);
		effectAnimationView.getImageReceiver().setDelegate(this);
		effectAnimationView.setLayerNum(Integer.MAX_VALUE);

		WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
		lp.width=lp.height=WindowManager.LayoutParams.MATCH_PARENT;
		lp.type=WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
		lp.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
		lp.format=PixelFormat.TRANSLUCENT;
		lp.token=chatListView.getWindowToken();
		wm.addView(windowView, lp);

		ReactionUtils.loadAnimationIntoImageView(aReaction.effect_animation, aReaction, effectAnimationView, Math.round(bigAnimSize/AndroidUtilities.density));
		ReactionUtils.loadAnimationIntoImageView(aReaction.activate_animation, aReaction, activateAnimationView, Math.round(smallAnimSize/AndroidUtilities.density));
	}

	@Override
	public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache){
	}

	@Override
	public void onAnimationReady(ImageReceiver imageReceiver){
		numAnimationsReady++;
		imageReceiver.getLottieAnimation().stop();
		imageReceiver.getLottieAnimation().setCurrentFrame(0, true);
		if(numAnimationsReady==2)
			bringItOn();
	}

	private void bringItOn(){
		if(startAction!=null)
			startAction.run();
		animationsWrap2.setAlpha(1f);
		effectAnimationView.getImageReceiver().getLottieAnimation().setAutoRepeat(0);
		activateAnimationView.getImageReceiver().getLottieAnimation().start();
		effectAnimationView.getImageReceiver().getLottieAnimation().start();

		AnimatorSet set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofFloat(animationsWrap2, View.SCALE_X, 1f),
				ObjectAnimator.ofFloat(animationsWrap2, View.SCALE_Y, 1f),
				ObjectAnimator.ofFloat(animationsWrap2, View.TRANSLATION_X, animTo.centerX()),
				ObjectAnimator.ofFloat(animationsWrap2, View.TRANSLATION_Y, animTo.centerY())
		);
		set.setDuration(250);
		set.setInterpolator(CubicBezierInterpolator.DEFAULT);
		set.start();

		long effectDuration=effectAnimationView.getImageReceiver().getLottieAnimation().getDuration();

		windowView.postDelayed(this::dismissAnimated, Math.max(250, effectDuration));
	}

	private void dismissAnimated(){
		updateTargetBounds();
		abomination.rotateMotionBackgroundDrawable();

		float scale=Math.min(animTo.width(), animTo.height())/(float)smallAnimSize;
		AnimatorSet set=new AnimatorSet();
		ObjectAnimator alphaAnim;
		set.playTogether(
				ObjectAnimator.ofFloat(animationsWrap2, View.SCALE_X, scale).setDuration(200),
				ObjectAnimator.ofFloat(animationsWrap2, View.SCALE_Y, scale).setDuration(200),
				ObjectAnimator.ofFloat(animationsWrap2, View.TRANSLATION_X, animTo.centerX()).setDuration(200),
				ObjectAnimator.ofFloat(animationsWrap2, View.TRANSLATION_Y, animTo.centerY()-animationsWrap.getTranslationY()).setDuration(200),
				alphaAnim=ObjectAnimator.ofFloat(animationsWrap2, View.ALPHA, 0f).setDuration(100)
		);
		alphaAnim.setStartDelay(100);
		set.setInterpolator(CubicBezierInterpolator.DEFAULT);
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				dismiss();
			}
		});
		set.start();
	}

	private void updateTargetBounds(){
		MessageCellReactionsLayout reactionsLayout=cell.getReactionsLayout();
		MessageCellReactionButton btn=reactionsLayout!=null ? reactionsLayout.getReactionButton(reaction) : null;
		if(btn!=null){
			View icon=btn.getIcon();
			int[] loc={0, 0};
			btn.getLocationOnScreen(loc);
			int x=Math.round(loc[0]+btn.getWidth()*btn.getScaleX()/2f)-btn.getWidth()/2;
			int y=Math.round(loc[1]+btn.getHeight()*btn.getScaleY()/2f)-btn.getHeight()/2;
			animTo.set(x+icon.getLeft(), y+icon.getTop(), x+icon.getRight(), y+icon.getBottom());
		}else if(abomination.getCurrentUser()!=null){
			MessageObject reactionMsg=cell.getCurrentMessagesGroup()!=null ? cell.getCurrentMessagesGroup().messages.get(0) : cell.getMessageObject();
			ChatMessageCell reactionsCell=cell.getCurrentMessagesGroup()!=null ? cell.findSiblingReactionsCell() : cell;
			if(reactionsCell==null)
				reactionsCell=cell;
			int index=-1;
			int i=0;
			for(TLRPC.TL_messageUserReaction reaction:reactionMsg.messageOwner.reactions.recent_reactons){
				if(reaction.user_id==abomination.getUserConfig().getClientUserId()){
					index=i;
					break;
				}
				i++;
			}
			ImageReceiver icon=index!=-1 ? reactionsCell.getPmReactionIcon(index) : null;
			if(icon==null){
				if(animTo.isEmpty()){
					animTo.set(0, 0, AndroidUtilities.dp(20), AndroidUtilities.dp(20));
					animTo.offset(activity.getResources().getDisplayMetrics().widthPixels/2-animTo.width()/2, activity.getResources().getDisplayMetrics().heightPixels/2-animTo.height()/2);
				}
				return;
			}
			int x=Math.round(icon.getImageX()), y=Math.round(icon.getImageY()), w=Math.round(icon.getImageWidth()), h=Math.round(icon.getImageHeight());
			if(x==0 || y==0){
				x=reactionsCell.getTimeX();
				y=reactionsCell.getTimeY();
				w=h=AndroidUtilities.dp(14);
			}
			int[] loc={0, 0};
			reactionsCell.getLocationOnScreen(loc);
			x+=loc[0];
			y+=loc[1];
			animTo.set(x, y, x+w, y+h);
		}else if(animTo.isEmpty()){
			animTo.set(0, 0, AndroidUtilities.dp(20), AndroidUtilities.dp(20));
			animTo.offset(activity.getResources().getDisplayMetrics().widthPixels/2-animTo.width()/2, activity.getResources().getDisplayMetrics().heightPixels/2-animTo.height()/2);
		}
	}
}
