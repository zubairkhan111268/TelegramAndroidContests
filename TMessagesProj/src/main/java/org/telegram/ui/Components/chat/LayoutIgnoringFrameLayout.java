package org.telegram.ui.Components.chat;

import android.animation.LayoutTransition;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

// a terrible terrible dirty hack, I'm so going to hell for this
// needed to prevent layout from happening while top/left/right/bottom are being animated
public class LayoutIgnoringFrameLayout extends FrameLayout{
	private boolean ignoreLayout;
	private LayoutTransition layoutTransition;

	public LayoutIgnoringFrameLayout(@NonNull Context context){
		super(context);
		setLayoutTransition(layoutTransition=new MyLayoutTransition());
	}

	public void setIgnoreLayout(boolean ignoreLayout){
		if(this.ignoreLayout!=ignoreLayout){
			this.ignoreLayout=ignoreLayout;
			if(!ignoreLayout){
				// needed to clear an internal flag in ViewGroup
				for(LayoutTransition.TransitionListener listener:layoutTransition.getTransitionListeners()){
					listener.endTransition(layoutTransition, this, null, LayoutTransition.CHANGING);
				}
			}
		}
	}

	@Override
	public void requestLayout(){
		if(ignoreLayout)
			return;
		super.requestLayout();
	}

	private class MyLayoutTransition extends LayoutTransition{
		public MyLayoutTransition(){
			disableTransitionType(APPEARING);
			disableTransitionType(CHANGE_APPEARING);
			disableTransitionType(CHANGE_DISAPPEARING);
			disableTransitionType(CHANGING);
			disableTransitionType(DISAPPEARING);
		}

		@Override
		public boolean isChangingLayout(){
			return ignoreLayout;
		}

		@Override
		public void addChild(ViewGroup parent, View child){
		}

		@Override
		public void showChild(ViewGroup parent, View child){
		}

		@Override
		public void showChild(ViewGroup parent, View child, int oldVisibility){
		}

		@Override
		public void removeChild(ViewGroup parent, View child){
		}

		@Override
		public void hideChild(ViewGroup parent, View child){
		}

		@Override
		public void hideChild(ViewGroup parent, View child, int newVisibility){
		}
	}
}
