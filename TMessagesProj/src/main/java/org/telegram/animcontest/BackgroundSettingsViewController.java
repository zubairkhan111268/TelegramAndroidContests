package org.telegram.animcontest;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.LayoutHelper;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import androidx.recyclerview.widget.RecyclerView;

public class BackgroundSettingsViewController extends AnimationSettingsViewController{
	private AnimatedBackgroundView previewView;
	private AnimationSettingsFragment fragment;

	public BackgroundSettingsViewController(AnimationSettingsFragment fragment){
		super(fragment.getParentActivity());
		this.fragment=fragment;

		listItems.add(new TallHeaderItem("Background Preview"));
		listItems.add(new BackgroundPreviewItem());
		listItems.add(new BlueTextItem("Open Full Screen", false){
			@Override
			public boolean isClickable(){
				return true;
			}

			@Override
			public void onClick(){
				fragment.presentFragment(new BackgroundPreviewFragment());
			}
		});
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Colors"));
		listItems.add(new TextAndColorItem("Color 1", () -> AnimationSettings.backgroundColors[0], true, col -> setBackgroundGradientColor(0, col)));
		listItems.add(new TextAndColorItem("Color 2", () -> AnimationSettings.backgroundColors[1], true, col -> setBackgroundGradientColor(1, col)));
		listItems.add(new TextAndColorItem("Color 3", () -> AnimationSettings.backgroundColors[2], true, col -> setBackgroundGradientColor(2, col)));
		listItems.add(new TextAndColorItem("Color 4", () -> AnimationSettings.backgroundColors[3], false, col -> setBackgroundGradientColor(3, col)));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Gradient intensity"));
		listItems.add(new SeekBarItem(() -> AnimationSettings.backgroundGradientIntensity, v -> {
			AnimationSettings.backgroundGradientIntensity=v;
			previewView.scheduleRedraw();
		}));
		listItems.add(new HeaderItem("Displace noise seed"));
		listItems.add(new SeekBarItem(() -> AnimationSettings.backgroundDisplaceSeed, v -> {
			AnimationSettings.backgroundDisplaceSeed=v;
			previewView.scheduleRedraw();
		}));
		listItems.add(new HeaderItem("Displace amplitude"));
		listItems.add(new SeekBarItem(() -> AnimationSettings.backgroundDisplaceAmplitude, v -> {
			AnimationSettings.backgroundDisplaceAmplitude=v;
			previewView.scheduleRedraw();
		}));
		listItems.add(new HeaderItem("Displace intensity"));
		listItems.add(new SeekBarItem(() -> AnimationSettings.backgroundDisplaceIntensity, v -> {
			AnimationSettings.backgroundDisplaceIntensity=v;
			previewView.scheduleRedraw();
		}));
		listItems.add(new HeaderItem("Blur radius"));
		listItems.add(new SeekBarItem(() -> AnimationSettings.backgroundBlurRadius, v -> {
			AnimationSettings.backgroundBlurRadius=v;
			previewView.scheduleRedraw();
		}));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Send Message"));
		listItems.add(makeDurationItem(true, () -> AnimationSettings.backgroundSendMessageDuration, d -> AnimationSettings.backgroundSendMessageDuration=d));
		listItems.add(new AnimationTimingItem(() -> AnimationSettings.backgroundSendMessageTiming, () -> AnimationSettings.backgroundSendMessageDuration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Open Chat"));
		listItems.add(makeDurationItem(true, () -> AnimationSettings.backgroundOpenChatDuration, d -> AnimationSettings.backgroundOpenChatDuration=d));
		listItems.add(new AnimationTimingItem(() -> AnimationSettings.backgroundOpenChatTiming, () -> AnimationSettings.backgroundOpenChatDuration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Jump to Message"));
		listItems.add(makeDurationItem(true, () -> AnimationSettings.backgroundJumpToMessageDuration, d -> AnimationSettings.backgroundJumpToMessageDuration=d));
		listItems.add(new AnimationTimingItem(() -> AnimationSettings.backgroundJumpToMessageTiming, () -> AnimationSettings.backgroundJumpToMessageDuration));
		listItems.add(new CardDividerItem());

		previewView=new AnimatedBackgroundView(context);
		previewView.doNotDestroy=true;
	}

	@Override
	public String getTitle(){
		return "Background";
	}

	private void setBackgroundGradientColor(int index, int color){
		AnimationSettings.backgroundColors[index]=color;
		previewView.scheduleRedraw();
	}

	public void onFragmentDestroy(){
		previewView.doNotDestroy=false;
	}

	private class BackgroundPreviewItem extends Item<AnimatedBackgroundView>{

		@Override
		public int getViewType(){
			return 1000;
		}

		@Override
		public AnimatedBackgroundView createView(Context context){
			previewView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(134)));
			return previewView;
		}
	}

	protected class TextAndColorItem extends Item<TextSettingsColorCell> implements ColorPicker.ColorPickerDelegate{
		public String title;
		public IntSupplier color;
		public boolean separator;
		public IntConsumer callback;

		public TextAndColorItem(String title, IntSupplier color, boolean separator, IntConsumer callback){
			this.title=title;
			this.color=color;
			this.separator=separator;
			this.callback=callback;
		}

		@Override
		public int getViewType(){
			return 1001;
		}

		@Override
		public TextSettingsColorCell createView(Context context){
			TextSettingsColorCell view=new TextSettingsColorCell(context);
			view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
			return view;
		}

		@Override
		public void bindView(int position, TextSettingsColorCell view){
			view.setTextAndColor(title, color.getAsInt(), separator);
		}

		@Override
		public boolean isClickable(){
			return true;
		}

		@Override
		public void onClick(){
			final BottomSheet sheet=new BottomSheet(context, true);
			sheet.setApplyBottomPadding(false);
			final ColorPicker picker=new ColorPicker(context, false, this);
			picker.setColor(color.getAsInt(), 0);

			LinearLayout sheetContent=new LinearLayout(context);
			sheetContent.setOrientation(LinearLayout.VERTICAL);
			sheetContent.addView(picker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 294));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight());
			lp.topMargin=-AndroidUtilities.getShadowHeight();
			View shadow = new View(context);
			shadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
			sheetContent.addView(shadow, lp);

			LinearLayout buttonsLayout=new LinearLayout(context);
			buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
			sheetContent.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

			TextView cancelBtn=new TextView(context);
			cancelBtn.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_dialogBackground), Theme.getColor(Theme.key_listSelector)));
			cancelBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
			cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
			cancelBtn.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
			cancelBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
			cancelBtn.setGravity(Gravity.CENTER);
			cancelBtn.setAllCaps(true);
			cancelBtn.setText("Cancel");
			cancelBtn.setOnClickListener(new View.OnClickListener(){
				@Override
				public void onClick(View view){
					sheet.dismiss();
				}
			});
			buttonsLayout.addView(cancelBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

			buttonsLayout.addView(new View(context), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

			TextView applyBtn=new TextView(context);
			applyBtn.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_dialogBackground), Theme.getColor(Theme.key_listSelector)));
			applyBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
			applyBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
			applyBtn.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
			applyBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
			applyBtn.setGravity(Gravity.CENTER);
			applyBtn.setAllCaps(true);
			applyBtn.setText("Apply");
			applyBtn.setOnClickListener(new View.OnClickListener(){
				@Override
				public void onClick(View view){
					sheet.dismiss();
//					color=picker.getColor();
					int color=picker.getColor();
					currentView.setTextAndColor(title, color, separator);
					callback.accept(color);
				}
			});
			buttonsLayout.addView(applyBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

			sheet.setCustomView(sheetContent);
			sheet.show();
		}

		@Override
		public void setColor(int color, int num, boolean applyNow){

		}
	}
}
