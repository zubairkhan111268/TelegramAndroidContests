package org.telegram.animcontest;

import android.content.Context;

import java.util.function.Supplier;

public class StickerMessageSettingsViewController extends AnimationSettingsViewController{

	private String title;
	private Supplier<AnimationSettings.StickerMessageAnimationParameters> params;

	public StickerMessageSettingsViewController(Context context, String title, Supplier<AnimationSettings.StickerMessageAnimationParameters> params, String reappearTitle){
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

		listItems.add(new HeaderItem("Scale"));
		listItems.add(new AnimationTimingItem(() -> params.get().scaleTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Placeholder crossfade"));
		listItems.add(new AnimationTimingItem(() -> params.get().placeholderCrossfadeTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem("Time appears"));
		listItems.add(new AnimationTimingItem(() -> params.get().timeAppearTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());

		listItems.add(new HeaderItem(reappearTitle));
		listItems.add(new AnimationTimingItem(() -> params.get().reappearTiming, () -> params.get().duration));
		listItems.add(new CardDividerItem());
	}

	@Override
	public String getTitle(){
		return title;
	}
}
