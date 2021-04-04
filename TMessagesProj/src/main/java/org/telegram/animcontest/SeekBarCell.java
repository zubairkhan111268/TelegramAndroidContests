package org.telegram.animcontest;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import java.util.function.Consumer;

import androidx.annotation.NonNull;

public class SeekBarCell extends FrameLayout implements SeekBarView.SeekBarViewDelegate{

	public SeekBarView seekBar;
	public Consumer<Float> callback;

	public SeekBarCell(@NonNull Context context){
		super(context);

		seekBar=new SeekBarView(context);
		seekBar.setDelegate(this);
		seekBar.setReportChanges(true);
		addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38));

		int pad=AndroidUtilities.dp(5);
		setPadding(pad, pad, pad, pad);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
		super.onMeasure(MeasureSpec.getSize(widthMeasureSpec) | MeasureSpec.EXACTLY, heightMeasureSpec);
	}

	@Override
	public void onSeekBarDrag(boolean stop, float progress){
		if(callback!=null)
			callback.accept(progress);
	}

	@Override
	public void onSeekBarPressed(boolean pressed){

	}
}
