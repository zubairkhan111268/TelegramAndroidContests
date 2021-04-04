package org.telegram.animcontest;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

public class BackgroundPreviewFragment extends BaseFragment{

	private AnimatedBackgroundView bgView;

	@Override
	public View createView(Context context){
		actionBar.setBackButtonImage(R.drawable.ic_ab_back);
		actionBar.setTitle("Background Preview");
		if (AndroidUtilities.isTablet()) {
			actionBar.setOccupyStatusBar(false);
		}
		actionBar.setExtraHeight(AndroidUtilities.dp(44));
		actionBar.setAllowOverlayTitle(false);
		actionBar.setAddToContainer(false);
		actionBar.setClipContent(true);
		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					finishFragment();
				}
			}
		});

		ScrollSlidingTextTabStrip tabs=new ScrollSlidingTextTabStrip(context);
		tabs.addTextTab(0, "Send Message");
		tabs.addTextTab(1, "Open Chat");
		tabs.addTextTab(2, "Jump to Message");
		tabs.finishAddingTabs();
		actionBar.addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));

		LinearLayout linearLayout;
		fragmentView = linearLayout= new LinearLayout(context){
			@Override
			protected void dispatchDraw(Canvas canvas) {
				super.dispatchDraw(canvas);
				if (parentLayout != null) {
					parentLayout.drawHeaderShadow(canvas, actionBar.getMeasuredHeight() + (int) actionBar.getTranslationY());
				}
				Theme.chat_composeShadowDrawable.setBounds(0, getHeight()-AndroidUtilities.dp(48)-Theme.chat_composeShadowDrawable.getIntrinsicHeight(), getWidth(), getHeight()-AndroidUtilities.dp(48));
				Theme.chat_composeShadowDrawable.draw(canvas);
			}
		};
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.addView(actionBar);

		bgView=new AnimatedBackgroundView(context);
		linearLayout.addView(bgView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

		TextView animateBtn=new TextView(context);
		animateBtn.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_dialogBackground), Theme.getColor(Theme.key_listSelector)));
		animateBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
		animateBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		animateBtn.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
		animateBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
		animateBtn.setGravity(Gravity.CENTER);
		animateBtn.setAllCaps(true);
		animateBtn.setText("Animate");
		animateBtn.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View view){
				AnimationSettings.TimingParameters timing;
				long duration;
				float offset;
				switch(tabs.getCurrentTabId()){
					case 0:
						timing=AnimationSettings.backgroundSendMessageTiming;
						duration=AnimationSettings.backgroundSendMessageDuration;
						offset=-.125f;
						break;
					case 1:
						timing=AnimationSettings.backgroundOpenChatTiming;
						duration=AnimationSettings.backgroundOpenChatDuration;
						offset=-.25f;
						break;
					case 2:
						timing=AnimationSettings.backgroundJumpToMessageTiming;
						duration=AnimationSettings.backgroundJumpToMessageDuration;
						offset=.25f;
						break;
					default:
						return;
				}
				bgView.setInterpolator(timing.getInterpolator());
				bgView.animateColorRotation(offset, timing.scaledStartDelay(duration), timing.scaledDuration(duration));
			}
		});
		linearLayout.addView(animateBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

		return linearLayout;
	}

	@Override
	public void onResume(){
		super.onResume();
		bgView.onResume();
	}

	@Override
	public void onPause(){
		super.onPause();
		bgView.onPause();
	}
}
