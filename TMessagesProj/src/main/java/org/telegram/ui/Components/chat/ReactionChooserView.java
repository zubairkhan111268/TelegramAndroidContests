package org.telegram.ui.Components.chat;

import android.content.Context;
import android.graphics.Outline;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.List;
import java.util.Random;

import androidx.annotation.NonNull;

public class ReactionChooserView extends LayoutIgnoringFrameLayout implements ImageReceiver.ImageReceiverDelegate{

	private List<TLRPC.TL_availableReaction> availableReactions;
	private HorizontalScrollView scrollView;
	private LinearLayout imagesLayout;
	private Random rand=new Random();

	public ReactionChooserView(@NonNull Context context, Theme.ResourcesProvider theme, List<TLRPC.TL_availableReaction> availableReactions){
		super(context);
		this.availableReactions=availableReactions;
		setBackground(new ReactionChooserBackgroundDrawable(theme));

		scrollView=new HorizontalScrollView(context);
		scrollView.setHorizontalScrollBarEnabled(false);
		scrollView.setFadingEdgeLength(AndroidUtilities.dp(15));
		scrollView.setHorizontalFadingEdgeEnabled(true);
		scrollView.setFillViewport(true);
		scrollView.getViewTreeObserver().addOnScrollChangedListener(this::onScrollChanged);
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
			scrollView.setOutlineProvider(new ViewOutlineProvider(){
				@Override
				public void getOutline(View view, Outline outline){
					outline.setRoundRect(0, 0, getWidth()-AndroidUtilities.dp(10), view.getHeight(), view.getHeight()/2f);
				}
			});
			scrollView.setClipToOutline(true);
		}
		addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP, 5, 5, 5, 0));
		imagesLayout=new LinearLayout(context);
		imagesLayout.setOrientation(LinearLayout.HORIZONTAL);
		imagesLayout.setGravity(Gravity.CENTER);
		imagesLayout.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(2), 0);
		scrollView.addView(imagesLayout);

		for(TLRPC.TL_availableReaction reaction:availableReactions){
			BackupImageView img=new BackupImageView(context);
			img.setOnClickListener(this::onViewClick);
			img.getImageReceiver().setDelegate(this);
			ReactionUtils.loadAnimationIntoImageView(reaction.select_animation, reaction, img);
			imagesLayout.addView(img, LayoutHelper.createLinear(34, 34, 0, 0, 5, 0));
		}
		postDelayed(this::maybeRestartDrawables, 500);
		getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				getViewTreeObserver().removeOnPreDrawListener(this);
				onScrollChanged();
				return true;
			}
		});
	}

	private void onViewClick(View v){
		RLottieDrawable drawable=((BackupImageView)v).getImageReceiver().getLottieAnimation();
	}

	@Override
	public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache){

	}

	@Override
	public void onAnimationReady(ImageReceiver imageReceiver){
		RLottieDrawable drawable=imageReceiver.getLottieAnimation();
		drawable.setAutoRepeat(3);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh){
		super.onSizeChanged(w, h, oldw, oldh);
		onScrollChanged();
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)
			scrollView.invalidateOutline();
	}

	private void maybeRestartDrawables(){
		int scrollX=scrollView.getScrollX(), scrollW=scrollView.getWidth();
		for(int i=0;i<imagesLayout.getChildCount();i++){
			BackupImageView img=(BackupImageView) imagesLayout.getChildAt(i);
			if(img.getRight()<scrollX || img.getLeft()>scrollX+scrollW)
				continue;
			RLottieDrawable drawable=img.getImageReceiver().getLottieAnimation();
			if(drawable==null || drawable.isRunning())
				continue;
			if(rand.nextInt(4)==2)
				drawable.restart();
		}
		postDelayed(this::maybeRestartDrawables, 500);
	}

	private void onScrollChanged(){
		int scrollX=scrollView.getScrollX(), scrollW=getWidth()-AndroidUtilities.dp(10);
		for(int i=0;i<imagesLayout.getChildCount();i++){
			BackupImageView img=(BackupImageView) imagesLayout.getChildAt(i);
			if(img.getRight()<scrollX || img.getLeft()>scrollX+scrollW)
				continue;

			if(img.getLeft()<scrollX){
				float part=(scrollX-img.getLeft())/(float)img.getWidth();
				float scale=0.5f+0.5f*(1f-part);
				img.setPivotX(img.getWidth());
				img.setScaleX(scale);
				img.setScaleY(scale);
			}else if(img.getRight()>scrollX+scrollW){
				float part=(img.getRight()-(scrollX+scrollW))/(float)img.getWidth();
				float scale=0.5f+0.5f*(1f-part);
				img.setPivotX(0);
				img.setScaleX(scale);
				img.setScaleY(scale);
			}else{
				img.setScaleX(1f);
				img.setScaleY(1f);
			}
		}
	}
}
