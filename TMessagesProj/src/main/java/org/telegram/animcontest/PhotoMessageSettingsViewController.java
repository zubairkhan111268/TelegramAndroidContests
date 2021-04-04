package org.telegram.animcontest;

import android.content.Context;

import java.util.function.Supplier;

public class PhotoMessageSettingsViewController extends AnimationSettingsViewController{

	private String title;
	private Supplier<AnimationSettings.BubbleMessageAnimationParameters> params;

	public PhotoMessageSettingsViewController(Context context, String title, Supplier<AnimationSettings.BubbleMessageAnimationParameters> params){
		super(context);
		this.title=title;
		this.params=params;

		listItems.add(makeDurationItem(false, () -> params.get().duration, d -> params.get().duration=d));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("X Position"));
		listItems.add(new AnimationTimingItem(() -> params.get().xPositionTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Y Position"));
		listItems.add(new AnimationTimingItem(() -> params.get().yPositionTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Photo shape & size"));
		listItems.add(new AnimationTimingItem(() -> params.get().bubbleShapeTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Icon transition"));
		listItems.add(new AnimationTimingItem(() -> params.get().scaleTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Sheet disappears"));
		listItems.add(new AnimationTimingItem(() -> params.get().colorChangeTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Time appears"));
		listItems.add(new AnimationTimingItem(() -> params.get().timeAppearTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());
	}

	@Override
	public String getTitle(){
		return title;
	}
}
