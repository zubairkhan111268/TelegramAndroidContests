package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import androidx.annotation.NonNull;

public class SendAsChannelButton extends FrameLayout{
	private BackupImageView imageView;
	private MessagesController messagesController;
	private AvatarDrawable avatarDrawable;
	private Animator currentAnim;
	private ImageView cross;
	private ShapeDrawable crossBG;
	private boolean showClose;

	public SendAsChannelButton(@NonNull Context context, MessagesController messagesController, Theme.ResourcesProvider resourcesProvider){
		super(context);
		this.messagesController=messagesController;
		imageView=new BackupImageView(context);
		imageView.setRoundRadius(AndroidUtilities.dp(15));
		addView(imageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER));
		avatarDrawable=new AvatarDrawable(resourcesProvider);

		cross=new ImageView(context);
		cross.setImageResource(R.drawable.delete);
		cross.setScaleType(ImageView.ScaleType.CENTER);
		crossBG=new ShapeDrawable(new OvalShape());
		crossBG.getPaint().setColor(resourcesProvider.getColor(Theme.key_chat_messagePanelSend));
		cross.setBackground(crossBG);
		cross.setAlpha(0f);
		cross.setRotation(45);
		addView(cross, LayoutHelper.createFrame(30, 30, Gravity.CENTER));
	}

	public void setSelectedPeer(TLRPC.Peer peer){
		TLObject actualPeer;
		if(peer.user_id!=0){
			actualPeer=messagesController.getUser(peer.user_id);
		}else{
			actualPeer=messagesController.getChat(peer.channel_id);
		}
		avatarDrawable.setInfo(actualPeer);
		imageView.setForUserOrChat(actualPeer, avatarDrawable);
	}

	public void setShowClose(boolean showClose){
		if(this.showClose==showClose)
			return;
		this.showClose=showClose;
		if(currentAnim!=null)
			currentAnim.cancel();

		AnimatorSet set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofFloat(cross, "alpha", showClose ? 1f : 0f),
				ObjectAnimator.ofFloat(cross, "rotation", showClose ? 0f : 45f)
		);
		set.setDuration(200);
		set.setInterpolator(CubicBezierInterpolator.DEFAULT);
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				currentAnim=null;
			}
		});
		set.start();
		currentAnim=set;
	}
}
