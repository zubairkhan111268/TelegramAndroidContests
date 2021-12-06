package org.telegram.ui.Components.chat;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.RecyclerListView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class ReactionAnimationOverlay{
	protected ChatActivity abomination;
	protected ChatMessageCell cell;
	protected Runnable onDismissedAction;
	protected Runnable startAction;
	protected boolean dismissed;
	protected WindowManager wm;
	protected FrameLayout windowView;
	protected FrameLayout animationsWrap;
	protected Activity activity;
	protected RecyclerView.OnScrollListener listScrollListener=new RecyclerView.OnScrollListener(){
		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
			listScrollOffset-=dy;
			if(animationsWrap!=null){
				animationsWrap.setTranslationY(listScrollOffset);
			}
		}
	};
	private int listScrollOffset=0;

	ReactionAnimationOverlay(ChatActivity abomination, ChatMessageCell cell, Runnable startAction){
		this.abomination=abomination;
		this.cell=cell;
		this.startAction=startAction;
		activity=abomination.getParentActivity();
		wm=activity.getWindowManager();
	}

	public abstract void show();

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

	protected void showWindow(){
		RecyclerListView chatListView=abomination.getChatListView();
		chatListView.addOnScrollListener(listScrollListener);
		WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
		lp.width=lp.height=WindowManager.LayoutParams.MATCH_PARENT;
		lp.type=WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
		lp.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
		lp.format=PixelFormat.TRANSLUCENT;
		lp.token=chatListView.getWindowToken();
		wm.addView(windowView, lp);
	}
}
