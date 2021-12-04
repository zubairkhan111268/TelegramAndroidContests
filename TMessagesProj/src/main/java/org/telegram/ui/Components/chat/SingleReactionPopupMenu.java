package org.telegram.ui.Components.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SingleReactionPopupMenu{
	private ChatActivity abomination;
	private MessageObject message;
	private MessageCellReactionButton srcButton;

	private FrameLayout windowView, shadowView, clippingView;
	private LinearLayout wrap;
	private View scrim;
	private WindowManager wm;
	private RecyclerListView list;
	private ReactionsViewRecyclerAdapter adapter;
	private boolean isShowing;
	private SpringAnimation showAnim;
	private Runnable onDismissAction;
	private String offset;

	private int totalReactions;
	private ArrayList<TLRPC.TL_messageUserReaction> reactions=new ArrayList<>();
	private boolean loading;
	private final int classGuid=ConnectionsManager.generateClassGuid();

	private AnimatorSet scrimAnimatorSet;

	public SingleReactionPopupMenu(ChatActivity abomination, MessageObject message, MessageCellReactionButton srcButton){
		this.abomination=abomination;
		this.message=message;
		this.srcButton=srcButton;
		wm=abomination.getParentActivity().getWindowManager();

		totalReactions=srcButton.getReaction().count;
	}

	public void show(){
		RecyclerListView chatListView=abomination.getChatListView();
		Activity context=abomination.getParentActivity();
		windowView=new KeyInterceptingFrameLayout(context);
		windowView.setFitsSystemWindows(true);
		windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		windowView.setClipToPadding(false);
		windowView.setClipChildren(false);

		scrim=new View(context);
		scrim.setBackgroundColor(0x33000000);
		windowView.addView(scrim);

		wrap=new LinearLayout(context);
		wrap.setOrientation(LinearLayout.VERTICAL);

		HoleView hole=new HoleView(context);
		LinearLayout.LayoutParams holeLP=new LinearLayout.LayoutParams(srcButton.getWidth(), srcButton.getHeight());
		holeLP.leftMargin=AndroidUtilities.dp(5);
		holeLP.gravity=Gravity.LEFT;
		wrap.addView(hole, holeLP);

		FrameLayout.LayoutParams wrapLP=LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
		int[] loc={0, 0};
		srcButton.getLocationOnScreen(loc);
		boolean upwards=loc[1]+srcButton.getHeight()/2>AndroidUtilities.displaySize.y/2;
		if(upwards){
			wrapLP.height=loc[1]-AndroidUtilities.statusBarHeight;
		}else{
			wrapLP.topMargin=loc[1]-AndroidUtilities.statusBarHeight;
		}

		wrap.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				wrap.getViewTreeObserver().removeOnPreDrawListener(this);

				if(upwards){
					wrap.setTranslationY(loc[1]/*-AndroidUtilities.statusBarHeight*/+AndroidUtilities.dp(2f)-(shadowView.getHeight()+hole.getHeight()));
				}
				int maxX=windowView.getWidth()-windowView.getPaddingLeft()-windowView.getPaddingRight()-wrap.getWidth();
				int x=loc[0]-AndroidUtilities.dp(5)-windowView.getPaddingLeft();
				if(x>maxX){
					hole.setTranslationX(x-maxX);
					x=maxX;
				}
				wrap.setTranslationX(x);

				return true;
			}
		});

		windowView.addView(wrap, wrapLP);

		shadowView=new FrameLayout(context);
		shadowView.setBackground(new PopupMenuShadowDrawable(abomination.getResourceProvider()));
		wrap.addView(shadowView, upwards ? 0 : 1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

		if(Build.VERSION.SDK_INT<Build.VERSION_CODES.LOLLIPOP){
			clippingView=new RoundedFrameLayout(context);
		}else{
			clippingView=new LayoutIgnoringFrameLayout(context);
			clippingView.setOutlineProvider(new ViewOutlineProvider(){
				@Override
				public void getOutline(View view, Outline outline){
					outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(6));
				}
			});
			clippingView.setClipToOutline(true);
		}

		shadowView.addView(clippingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

		list=new RecyclerListView(context);
		list.setLayoutManager(new LinearLayoutManager(context));
		list.setAdapter(adapter=new ReactionsViewRecyclerAdapter());
		list.setOnItemClickListener(adapter);
		clippingView.addView(list, LayoutHelper.createFrame(220, LayoutHelper.WRAP_CONTENT));

		WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
		lp.width=lp.height=WindowManager.LayoutParams.MATCH_PARENT;
		lp.type=WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
		lp.flags=WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)
			lp.flags|=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
		lp.format=PixelFormat.TRANSLUCENT;
		lp.token=chatListView.getWindowToken();
		wm.addView(windowView, lp);
		isShowing=true;

		View pagedownButton=abomination.getPagedownButton();
		View mentiondownButton=abomination.getMentiondownButton();
		chatListView.stopScroll();
		((GridLayoutManagerFixed)chatListView.getLayoutManager()).setCanScrollVertically(false);
		if (scrimAnimatorSet != null) {
			scrimAnimatorSet.cancel();
		}
		scrimAnimatorSet = new AnimatorSet();
		ArrayList<Animator> animators = new ArrayList<>();
		if (pagedownButton.getTag() != null) {
			animators.add(ObjectAnimator.ofFloat(pagedownButton, View.ALPHA, 0));
		}
		if (mentiondownButton.getTag() != null) {
			animators.add(ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 0));
		}
		scrimAnimatorSet.playTogether(animators);
		scrimAnimatorSet.setDuration(150);
		scrimAnimatorSet.start();
		abomination.hideHints(false);
		UndoView topUndoView=abomination.getTopUndoView();
		if (topUndoView != null) {
			topUndoView.hide(true, 1);
		}
		UndoView undoView=abomination.getUndoView();
		if (undoView != null) {
			undoView.hide(true, 1);
		}
		ChatActivityEnterView chatActivityEnterView=abomination.getChatActivityEnterView();
		if (chatActivityEnterView != null) {
			chatActivityEnterView.getEditField().setAllowDrawCursor(false);
		}

		shadowView.setScaleX(.5f);
		shadowView.setScaleY(.5f);
		shadowView.setAlpha(0f);
		scrim.setAlpha(0f);
		shadowView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				shadowView.getViewTreeObserver().removeOnPreDrawListener(this);

				SpringAnimation anim=new SpringAnimation(shadowView, new FloatPropertyCompat<Object>(""){
					@Override
					public float getValue(Object object){
						return 0;
					}

					@Override
					public void setValue(Object object, float value){
						shadowView.setScaleX(value/2f+.5f);
						shadowView.setScaleY(value/2f+.5f);
						shadowView.setAlpha(Math.min(1f, value));
						scrim.setAlpha(Math.min(1f, value));
					}
				}, 1f);
				anim.setSpring(new SpringForce().setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY).setStiffness(500f).setFinalPosition(1f));
				anim.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
				anim.addEndListener(new DynamicAnimation.OnAnimationEndListener(){
					@Override
					public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity){
						showAnim=null;
					}
				});
				showAnim=anim;
				anim.start();

				return true;
			}
		});
	}

	public void dismiss(){
		if(!isShowing)
			return;
		if(showAnim!=null)
			showAnim.cancel();
		if(onDismissAction!=null)
			onDismissAction.run();
		afterDismiss();
		isShowing=false;

		AnimatorSet set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofFloat(scrim, View.ALPHA, 0f),
				ObjectAnimator.ofFloat(shadowView, View.TRANSLATION_Y, AndroidUtilities.dp(-15)),
				ObjectAnimator.ofFloat(shadowView, View.ALPHA, 0f)
		);
		set.setDuration(200);
		set.setInterpolator(CubicBezierInterpolator.DEFAULT);
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				srcButton.invalidate();
				srcButton.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
					@Override
					public boolean onPreDraw(){
						srcButton.getViewTreeObserver().removeOnPreDrawListener(this);
						wm.removeView(windowView);
						return true;
					}
				});
			}
		});
		set.start();
	}

	private void afterDismiss(){
		abomination.getConnectionsManager().cancelRequestsForGuid(classGuid);
		SizeNotifierFrameLayout contentView=(SizeNotifierFrameLayout)abomination.getFragmentView();
		RecyclerListView chatListView=abomination.getChatListView();
		View pagedownButton=abomination.getPagedownButton();
		View mentiondownButton=abomination.getMentiondownButton();

		if (scrimAnimatorSet != null) {
			scrimAnimatorSet.cancel();
			scrimAnimatorSet = null;
		}
		((GridLayoutManagerFixed)chatListView.getLayoutManager()).setCanScrollVertically(true);
		scrimAnimatorSet = new AnimatorSet();
		ArrayList<Animator> animators = new ArrayList<>();
		if (pagedownButton.getTag() != null) {
			animators.add(ObjectAnimator.ofFloat(pagedownButton, View.ALPHA, 1.0f));
		}
		if (mentiondownButton.getTag() != null) {
			animators.add(ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 1.0f));
		}
		scrimAnimatorSet.playTogether(animators);
		scrimAnimatorSet.setDuration(220);
		scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				contentView.invalidate();
				chatListView.invalidate();
			}
		});
		scrimAnimatorSet.start();
		ChatActivityEnterView chatActivityEnterView=abomination.getChatActivityEnterView();
		if (chatActivityEnterView != null) {
			chatActivityEnterView.getEditField().setAllowDrawCursor(true);
		}
	}

	private boolean onViewKeyEvent(View v, KeyEvent ev){
		if(ev.getKeyCode()==KeyEvent.KEYCODE_BACK){
			if(ev.getAction()==KeyEvent.ACTION_DOWN && isShowing){
				dismiss();
			}
			return true;
		}
		return false;
	}

	private void openUserProfile(long id){
		Bundle args = new Bundle();
		args.putLong("user_id", id);
		ProfileActivity fragment = new ProfileActivity(args);
		abomination.presentFragment(fragment);
	}

	private void loadReactions(int count){
		loading=true;
		TLRPC.TL_messages_getMessageReactionsList req=new TLRPC.TL_messages_getMessageReactionsList();
		req.peer=MessagesController.getInputPeer(abomination.getCurrentChat());
		req.id=message.getId();
		req.reaction=srcButton.getReaction().reaction;
		req.flags|=1;
		if(offset!=null){
			req.offset=offset;
			req.flags|=2;
		}
		req.limit=count;
		int token=abomination.getConnectionsManager().sendRequest(req, (res, err)->{
			AndroidUtilities.runOnUIThread(()->{
				loading=false;
				if(res instanceof TLRPC.TL_messages_messageReactionsList){
					TLRPC.TL_messages_messageReactionsList r=(TLRPC.TL_messages_messageReactionsList) res;
					abomination.getMessagesController().putUsers(r.users, false);
					offset=r.next_offset;
					int prevSize=reactions.size();
					reactions.addAll(r.reactions);
					if(reactions.size()>totalReactions){
						adapter.notifyItemRangeInserted(reactions.size(), reactions.size()-totalReactions);
					}
					adapter.notifyItemRangeChanged(prevSize, r.reactions.size());
					if(offset==null && totalReactions<reactions.size()){
						adapter.notifyItemRangeRemoved(reactions.size(), totalReactions-reactions.size());
						totalReactions=reactions.size();
					}
				}
			});
		});
		abomination.getConnectionsManager().bindRequestToGuid(token, classGuid);
	}

	public void setOnDismissAction(Runnable onDismissAction){
		this.onDismissAction=onDismissAction;
	}

	private class KeyInterceptingFrameLayout extends FrameLayout{
		public KeyInterceptingFrameLayout(@NonNull Context context){
			super(context);
		}

		@Override
		public boolean dispatchKeyEvent(KeyEvent event){
			return onViewKeyEvent(this, event);
		}

		@Override
		public boolean onTouchEvent(MotionEvent ev){
			if(ev.getAction()==MotionEvent.ACTION_DOWN){
				if(isShowing)
					dismiss();
			}
			return true;
		}

		@Override
		public WindowInsets dispatchApplyWindowInsets(WindowInsets insets){
			if(Build.VERSION.SDK_INT<Build.VERSION_CODES.LOLLIPOP)
				return insets;
			setPadding(insets.getStableInsetLeft(), insets.getStableInsetTop(), insets.getStableInsetRight(), insets.getStableInsetBottom());
			LayoutParams lp=(LayoutParams) scrim.getLayoutParams();
			lp.leftMargin=-getPaddingLeft();
			lp.rightMargin=-getPaddingRight();
			lp.topMargin=-getPaddingTop();
			lp.bottomMargin=-getPaddingBottom();
			return insets.consumeStableInsets();
		}
	}

	private class ReactionsViewRecyclerAdapter extends RecyclerListView.SelectionAdapter implements RecyclerListView.OnItemClickListener{
		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return switch(viewType){
				case 0 -> new ReactionUserViewHolder(parent);
				case 1 -> new LoadingReactionViewHolder();
				default -> throw new IllegalArgumentException();
			};
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position){
			if(position<reactions.size()){
				((ReactionUserViewHolder) holder).bind(reactions.get(position));
			}else{
				((LoadingReactionViewHolder) holder).bind();
				if(!loading){
					loadReactions(50);
				}
			}
		}

		@Override
		public int getItemCount(){
			return totalReactions;
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder){
			return holder instanceof ReactionUserViewHolder;
		}

		@Override
		public int getItemViewType(int position){
			return position>=reactions.size() ? 1 : 0;
		}

		@Override
		public void onItemClick(View view, int position){
			openUserProfile(reactions.get(position).user_id);
			dismiss();
		}
	}

	private class ReactionUserViewHolder extends RecyclerView.ViewHolder{
		private BackupImageView photo, icon;
		private TextView name;
		private AvatarDrawable avatarDrawable=new AvatarDrawable();

		public ReactionUserViewHolder(ViewGroup list){
			super(abomination.getParentActivity().getLayoutInflater().inflate(R.layout.reaction_list_cell, list, false));
			photo=itemView.findViewById(R.id.photo);
			icon=itemView.findViewById(R.id.icon);
			name=itemView.findViewById(R.id.name);

			name.setTextColor(abomination.getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
			photo.setRoundRadius(AndroidUtilities.dp(17));
		}

		public void bind(TLRPC.TL_messageUserReaction reaction){
			if(reaction.reaction==null){
				icon.setVisibility(View.GONE);
			}else{
				TLRPC.TL_availableReaction aReaction=abomination.getMediaDataController().getReaction(reaction.reaction);
				if(aReaction!=null){
					ReactionUtils.loadWebpIntoImageView(aReaction.static_icon, aReaction, icon);
					icon.setVisibility(View.VISIBLE);
				}else{
					icon.setVisibility(View.GONE);
				}
			}
			TLRPC.User user=abomination.getMessagesController().getUser(reaction.user_id);
			name.setText(ContactsController.formatName(user.first_name, user.last_name));
			avatarDrawable.setInfo(user);
			ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
			photo.setImage(imageLocation, "50_50", avatarDrawable, user);
		}
	}

	public class LoadingReactionViewHolder extends RecyclerView.ViewHolder{
		public LoadingReactionViewHolder(){
			super(new FlickerLoadingView(abomination.getParentActivity(), abomination.getResourceProvider()));

			FlickerLoadingView view=(FlickerLoadingView) itemView;
			view.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
			view.setViewType(FlickerLoadingView.REACTION_CELL_TYPE);
			view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48)));
		}

		public void bind(){
			((FlickerLoadingView)itemView).setItemsCount(getAdapterPosition()%2);
		}
	}

	// Since there's no layer, this would put a hole through the entire window so the button beneath isn't shaded
	private static class HoleView extends View{
		private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
		private RectF rect=new RectF();

		public HoleView(Context context){
			super(context);
			paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		}

		@Override
		protected void onDraw(Canvas canvas){
			rect.set(0, 0, getWidth(), getHeight());
			canvas.drawRoundRect(rect, rect.height()/2f, rect.height()/2f, paint);
		}
	}
}
