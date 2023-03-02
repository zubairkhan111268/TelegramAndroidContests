package org.telegram.ui.Components.voip;

import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RoundRectOutlineProvider extends ViewOutlineProvider{
	private Rect rect=new Rect();
	private float radius;
	private boolean useViewBounds;

	@Override
	public void getOutline(View view, Outline outline){
		if(useViewBounds)
			outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
		else
			outline.setRoundRect(rect, radius);
	}

	@Keep
	public void setLeft(int left){
		rect.left=left;
	}

	@Keep
	public void setTop(int top){
		rect.top=top;
	}

	@Keep
	public void setRight(int right){
		rect.right=right;
	}

	@Keep
	public void setBottom(int bottom){
		rect.bottom=bottom;
	}

	@Keep
	public void setRadius(float radius){
		this.radius=radius;
	}

	public void setUseViewBounds(boolean useViewBounds){
		this.useViewBounds=useViewBounds;
	}
}
