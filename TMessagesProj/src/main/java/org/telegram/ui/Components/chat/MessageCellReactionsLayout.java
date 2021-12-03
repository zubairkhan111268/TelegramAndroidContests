package org.telegram.ui.Components.chat;

import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class MessageCellReactionsLayout extends ViewGroup{
	private Theme.ResourcesProvider resProvider;
	private MessageObject message;
	private int bottomRightIndentWidth;
	private Rect rect=new Rect();
	private boolean inBubble;
	private LayoutTransition layoutTransition;
	private boolean hasUnknownUsers;

	private ArrayList<MessageCellReactionButton> buttons=new ArrayList<>();

//	private Paint paint=new Paint();

	public MessageCellReactionsLayout(Context context, Theme.ResourcesProvider resProvider){
		super(context);
		this.resProvider=resProvider;
//		setBackgroundColor(0x8800ff00);
//		setWillNotDraw(false);
		setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), AndroidUtilities.dp(10));

		layoutTransition=new LayoutTransition();
		layoutTransition.setAnimateParentHierarchy(false);
		layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
		layoutTransition.setDuration(220);
		layoutTransition.setInterpolator(LayoutTransition.CHANGING, CubicBezierInterpolator.DEFAULT);

		AnimatorSet set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofFloat(null, View.SCALE_X, .3f, 1f),
				ObjectAnimator.ofFloat(null, View.SCALE_Y, .3f, 1f),
				ObjectAnimator.ofFloat(null, View.ALPHA, 0f, 1f)
		);
		layoutTransition.setAnimator(LayoutTransition.APPEARING, set);

		set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofFloat(null, View.SCALE_X, 1f, .3f),
				ObjectAnimator.ofFloat(null, View.SCALE_Y, 1f, .3f),
				ObjectAnimator.ofFloat(null, View.ALPHA, 1f, 0f)
		);
		layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, set);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
		int width=MeasureSpec.getSize(widthMeasureSpec)-getPaddingLeft()-getPaddingRight();
		int curX=getPaddingLeft();
		int gap=AndroidUtilities.dp(6);
		int rowHeight=AndroidUtilities.dp(26);
		int curY=0;

		for(int i=0;i<getChildCount();i++){
			View child=getChildAt(i);
			child.measure(width | MeasureSpec.AT_MOST, rowHeight | MeasureSpec.EXACTLY);
			if(curX+child.getMeasuredWidth()>width){
				curX=getPaddingLeft();
				curY+=rowHeight+gap;
			}
			curX+=child.getMeasuredWidth()+gap;
		}

		int height=curY+rowHeight+getPaddingTop();

		if(curX>width-bottomRightIndentWidth)
			height+=AndroidUtilities.dp(20);
		else
			height+=getPaddingBottom();
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b){
		int width=r-l-getPaddingLeft()-getPaddingRight();
		int curX=getPaddingLeft();
		int gap=AndroidUtilities.dp(6);
		int rowHeight=AndroidUtilities.dp(26);
		int curY=getPaddingTop();

//		if(r-l<getMeasuredWidth()) throw new IllegalStateException("wrong width "+(r-l)+" expected "+getMeasuredWidth());
//		if(b-t!=getMeasuredHeight()) throw new IllegalStateException("wrong height");

		// for outgoing media messages, align buttons to the right
		if(!inBubble && message.isOutOwner()){
			int rowStart=0;
			// go through all children and add up their widths
			for(int i=0; i<getChildCount(); i++){
				View child=getChildAt(i);
				if(curX+child.getMeasuredWidth()>width){
					// we have a completed row, go backwards and lay it out right to left
					curX=r-l-getPaddingRight();
					for(int j=i-1;j>=rowStart;j--){
						View child2=getChildAt(j);
						child2.layout(curX-child2.getMeasuredWidth(), curY, curX, curY+child2.getMeasuredHeight());
						curX-=child2.getMeasuredWidth()+gap;
					}
					rowStart=i;
					curX=getPaddingLeft();
					curY+=rowHeight+gap;
				}
				curX+=child.getMeasuredWidth()+gap;
			}
			// lay out the last row
			curX=r-l-getPaddingRight();
			for(int j=getChildCount()-1;j>=rowStart;j--){
				View child2=getChildAt(j);
				child2.layout(curX-child2.getMeasuredWidth(), curY, curX, curY+child2.getMeasuredHeight());
				curX-=child2.getMeasuredWidth()+gap;
			}
		}else{
			for(int i=0; i<getChildCount(); i++){
				View child=getChildAt(i);
				if(curX+child.getMeasuredWidth()>width){
					curX=getPaddingLeft();
					curY+=rowHeight+gap;
				}
				child.layout(curX, curY, curX+child.getMeasuredWidth(), curY+child.getMeasuredHeight());
				curX+=child.getMeasuredWidth()+gap;
			}
		}
	}

	public void setMessage(MessageObject message, boolean inBubble){
		boolean sameMessage=this.message!=null && this.message.getDialogId()==message.getDialogId() && this.message.getId()==message.getId();
		this.message=message;
		this.inBubble=inBubble;
		hasUnknownUsers=false;
		boolean showAvatars=true;
		if(message.messageOwner.reactions.recent_reactons!=null && !message.messageOwner.reactions.recent_reactons.isEmpty()){
			String avatarsReaction=message.messageOwner.reactions.recent_reactons.get(0).reaction;
			for(TLRPC.TL_messageUserReaction reaction:message.messageOwner.reactions.recent_reactons){
				if(!avatarsReaction.equals(reaction.reaction)){
					showAvatars=false;
					break;
				}
			}
		}else{
			showAvatars=false;
		}

		if(sameMessage){
			if(getLayoutTransition()==null)
				setLayoutTransition(layoutTransition);
			HashMap<String, MessageCellReactionButton> existingButtons=new HashMap<>(getChildCount());
			for(int i=0;i<getChildCount();i++){
				MessageCellReactionButton btn=(MessageCellReactionButton) getChildAt(i);
				existingButtons.put(btn.getReaction().reaction, btn);
			}
			boolean finalShowAvatars=showAvatars;
			List<TLRPC.TL_reactionCount> addedReactions=message.messageOwner.reactions.results.stream().filter(r->{
				MessageCellReactionButton btn=existingButtons.remove(r.reaction);
				if(btn==null)
					return true;
				btn.setReactions(r, finalShowAvatars ? makeUserListForReaction(r, message.messageOwner.reactions.recent_reactons) : null, true);
				btn.setSelected(r.chosen, true);
				return false;
			}).collect(Collectors.toList());

			if(!existingButtons.isEmpty()){
				for(MessageCellReactionButton btn:existingButtons.values()){
					recycleButton(btn);
				}
			}
			if(!addedReactions.isEmpty()){
				for(TLRPC.TL_reactionCount count : addedReactions){
					MessageCellReactionButton button=obtainButton();
					button.setReactions(count, showAvatars ? makeUserListForReaction(count, message.messageOwner.reactions.recent_reactons) : null, false);
					button.setSelected(count.chosen, false);
				}
			}
		}else{
			if(getLayoutTransition()!=null)
					setLayoutTransition(null);
			while(getChildCount()>0)
				recycleButton((MessageCellReactionButton) getChildAt(getChildCount()-1));
			if(!message.hasReactions())
				return;
			for(TLRPC.TL_reactionCount count : message.messageOwner.reactions.results){
				MessageCellReactionButton button=obtainButton();
				button.setReactions(count, showAvatars ? makeUserListForReaction(count, message.messageOwner.reactions.recent_reactons) : null, false);
				button.setSelected(count.chosen, false);
			}
		}
	}

	private List<TLRPC.User> makeUserListForReaction(TLRPC.TL_reactionCount reaction, List<TLRPC.TL_messageUserReaction> recentReactions){
		if(reaction.count>3 || recentReactions==null || recentReactions.isEmpty())
			return null;
		ArrayList<TLRPC.User> users=new ArrayList<>(reaction.count);
		for(TLRPC.TL_messageUserReaction r:recentReactions){
			if(r.reaction.equals(reaction.reaction)){
				TLRPC.User user=MessagesController.getInstance(UserConfig.selectedAccount).getUser(r.user_id);
				if(user!=null)
					users.add(user);
				else
					hasUnknownUsers=true;
				if(users.size()==reaction.count)
					break;
			}
		}
		return users.isEmpty() ? null : users;
	}

	private MessageCellReactionButton obtainButton(){
		if(!buttons.isEmpty()){
			MessageCellReactionButton btn=buttons.remove(buttons.size()-1);
			addView(btn);
			btn.setForegroundColor(getColor(inBubble ? (message.isOutOwner() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) : Theme.key_chat_serviceText));
			btn.setBackgroundType(inBubble, message.isOutOwner());
			return btn;
		}
		MessageCellReactionButton btn=new MessageCellReactionButton(getContext(), resProvider);
		addView(btn);
		btn.setOnClickListener(this::onChildClick);
		btn.setForegroundColor(getColor(inBubble ? (message.isOutOwner() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) : Theme.key_chat_serviceText));
		btn.setBackgroundType(inBubble, message.isOutOwner());
		return btn;
	}

	private void recycleButton(MessageCellReactionButton btn){
		removeView(btn);
		buttons.add(btn);
	}

	public void setBottomRightIndentWidth(int bottomRightIndentWidth){
		this.bottomRightIndentWidth=bottomRightIndentWidth;
	}

	public boolean hasUnknownUsers(){
		return hasUnknownUsers;
	}

//	@Override
//	protected void onDraw(Canvas canvas){
//		super.onDraw(canvas);
//
//		paint.setColor(0x88ff0000);
//		if(bottomRightIndentWidth>0){
//			canvas.drawRect(getWidth()-bottomRightIndentWidth, getHeight()-AndroidUtilities.dp(14), getWidth(), getHeight(), paint);
//		}
//	}

	private void onChildClick(View v){
		MessageCellReactionButton btn=(MessageCellReactionButton)v;
		btn.getReaction().chosen=!btn.getReaction().chosen;
		if(btn.getReaction().chosen)
			btn.getReaction().count++;
		else
			btn.getReaction().count--;
		((ChatMessageCell)getParent()).forceResetMessageObject();
	}

	public boolean willHandleTouchEventBecauseOfFuckedUpCustomEventDispatch(MotionEvent ev){
		for(int i=0;i<getChildCount();i++){
			View child=getChildAt(i);
			rect.set(0, 0, child.getWidth(), child.getHeight());
			rect.offset(getLeft()+child.getLeft(), getTop()+child.getTop());
			if(rect.contains((int)ev.getX(), (int)ev.getY()))
				return true;
		}
		return false;
	}

	public void invalidateButtons(){
		for(int i=0;i<getChildCount();i++)
			getChildAt(i).invalidate();
	}

	private int getColor(String key){
		Integer color=resProvider.getColor(key);
		return color==null ? Theme.getColor(key) : color;
	}
}
