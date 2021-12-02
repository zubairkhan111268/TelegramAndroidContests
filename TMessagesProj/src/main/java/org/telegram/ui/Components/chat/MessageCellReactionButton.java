package org.telegram.ui.Components.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.util.FloatProperty;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;

import java.util.List;

import androidx.annotation.NonNull;

public class MessageCellReactionButton extends FrameLayout{

	private BackupImageView icon;
	private NumberTextView counter;
	private Drawable serviceBg, inBubbleSelector;
	private ShapeDrawable inBubbleBg;
	private Theme.ResourcesProvider resProvider;
	private float selectedness=0f;
	private boolean selected;
	private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
	private RectF rectF=new RectF();
	private Animator currentSelectionAnim;
	private TLRPC.TL_reactionCount reactions;
	private AvatarsImageView avatarsView;
	private Animator currentTransitionAnim;

	private static final Property<MessageCellReactionButton, Float> SELECTEDNESS_PROPERTY;

	static{
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
			SELECTEDNESS_PROPERTY=new FloatProperty<>("selectedness"){
				@Override
				public void setValue(MessageCellReactionButton object, float value){
					object.selectedness=value;
					object.invalidate();
				}

				@Override
				public Float get(MessageCellReactionButton object){
					return object.selectedness;
				}
			};
		}else{
			SELECTEDNESS_PROPERTY=new Property<>(Float.class, "selectedness"){
				@Override
				public Float get(MessageCellReactionButton object){
					return object.selectedness;
				}

				@Override
				public void set(MessageCellReactionButton object, Float value){
					object.selectedness=value;
					object.invalidate();
				}
			};
		}
	}

	public MessageCellReactionButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider){
		super(context);
		setWillNotDraw(false);

		setPadding(AndroidUtilities.dp(7), 0, 0, 0);

		icon=new BackupImageView(context);
		addView(icon, LayoutHelper.createFrame(19, 19, Gravity.LEFT | Gravity.CENTER_VERTICAL));

		counter=new NumberTextView(context);
		counter.setWrapContent(true);
		counter.setTextSize(12);
		counter.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
		addView(counter, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 4+19, 0, 9, 0));
		resProvider=resourcesProvider;
		paint.setStyle(Paint.Style.STROKE);

	}

	public void setForegroundColor(int color){
		counter.setTextColor(color);
		paint.setColor(color);
	}

	public void setBackgroundType(boolean insideBubble, boolean isOut){
		if(insideBubble){
			int color=getColor(isOut ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText);
			if(inBubbleBg==null){
				float r=AndroidUtilities.dp(13);
				inBubbleBg=new ShapeDrawable(new RoundRectShape(new float[]{r, r, r, r, r, r, r, r}, null, null));
				inBubbleBg.setAlpha(25);
				inBubbleSelector=Theme.createRadSelectorDrawable(color & 0x19ffffff, Math.round(r), Math.round(r));
			}else{
				Theme.setSelectorDrawableColor(inBubbleSelector, color & 0x19ffffff, false);
			}
			inBubbleBg.getPaint().setColor(color);
			setBackground(inBubbleBg);
			setForeground(inBubbleSelector);
		}else{
			if(serviceBg==null){
				// this -> reactions layout -> message cell -> message list
				// (yet another horrible workaround, please forgive me)
				serviceBg=new ServiceMessageBackgroundDrawable((View) getParent().getParent().getParent(), this, resProvider);
			}
			setBackground(serviceBg);
			setForeground(null);
		}
	}

	public void setReactions(TLRPC.TL_reactionCount reaction, List<TLRPC.User> avaUsers, boolean animated){
		boolean countChanged=reactions!=null && reactions.count!=reaction.count;
		reactions=reaction;
		counter.setNumber(reaction.count, animated);
		TLRPC.TL_availableReaction aReaction=MediaDataController.getInstance(UserConfig.selectedAccount).getReaction(reaction.reaction);
		if(aReaction!=null){
			icon.setVisibility(View.VISIBLE);
			ReactionUtils.loadWebpIntoImageView(aReaction.static_icon, aReaction, icon);
		}else{
			icon.setVisibility(INVISIBLE);
		}
		if(currentTransitionAnim!=null){
			currentTransitionAnim.cancel();
			currentTransitionAnim=null;
		}
		if(avaUsers!=null && !avaUsers.isEmpty()){
			boolean visibilityChanged=false;
			if(avatarsView==null){
				avatarsView=new AvatarsImageView(getContext(), false);
				avatarsView.setLayout(21, 13, 2);
				addView(avatarsView, LayoutHelper.createFrame(21+13+13, 21, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 4+19, 0, 3, 0));
				visibilityChanged=true;
			}else if(avatarsView.getVisibility()!=VISIBLE){
				avatarsView.setVisibility(View.VISIBLE);
				visibilityChanged=true;
			}
			if(animated && visibilityChanged)
				avatarsView.setAlpha(0f);
			for(int i=0;i<3;i++){
				avatarsView.setObject(2-i, UserConfig.selectedAccount, i<avaUsers.size() ? avaUsers.get(i) : null);
			}
			int offset=13*(3-avaUsers.size());
			LayoutParams lp=(LayoutParams)avatarsView.getLayoutParams();
			int newMargin=AndroidUtilities.dp(4+19-offset);
			if(newMargin!=lp.leftMargin){
				lp.leftMargin=newMargin;
				requestLayout();
			}
			avatarsView.forceCommitTransition(animated);
			int prevWidth=getWidth();
			if(animated){
				getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
					@Override
					public boolean onPreDraw(){
						getViewTreeObserver().removeOnPreDrawListener(this);

						AnimatorSet set=new AnimatorSet();
						set.playTogether(
								ObjectAnimator.ofFloat(avatarsView, View.ALPHA, 1f),
								ObjectAnimator.ofFloat(counter, View.ALPHA, 1f, 0f),
								ObjectAnimator.ofFloat(avatarsView, View.TRANSLATION_X, prevWidth-getWidth(), 0f),
								ObjectAnimator.ofFloat(counter, View.TRANSLATION_X, prevWidth-getWidth(), 0f)
						);
						set.setDuration(220);
						set.setInterpolator(CubicBezierInterpolator.DEFAULT);
						set.addListener(new AnimatorListenerAdapter(){
							@Override
							public void onAnimationEnd(Animator animation){
								currentTransitionAnim=null;
								counter.setVisibility(GONE);
								counter.setAlpha(1f);
								counter.setTranslationX(0f);
								avatarsView.setAlpha(1f);
								avatarsView.setTranslationX(0f);
							}
						});
						currentTransitionAnim=set;
						set.start();

						return true;
					}
				});
			}else{
				counter.setVisibility(GONE);
			}
		}else if(avatarsView!=null && avatarsView.getVisibility()==VISIBLE){
			if(counter.getVisibility()!=VISIBLE){
				if(animated){
					counter.setVisibility(VISIBLE);
					int prevWidth=getWidth();
					getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
						@Override
						public boolean onPreDraw(){
							getViewTreeObserver().removeOnPreDrawListener(this);

							AnimatorSet set=new AnimatorSet();
							set.playTogether(
									ObjectAnimator.ofFloat(avatarsView, View.ALPHA, 1f, 0f),
									ObjectAnimator.ofFloat(counter, View.ALPHA, 0f, 1f),
									ObjectAnimator.ofFloat(avatarsView, View.TRANSLATION_X, prevWidth-getWidth(), 0f),
									ObjectAnimator.ofFloat(counter, View.TRANSLATION_X, prevWidth-getWidth(), 0f)
							);
							set.setDuration(220);
							set.setInterpolator(CubicBezierInterpolator.DEFAULT);
							set.addListener(new AnimatorListenerAdapter(){
								@Override
								public void onAnimationEnd(Animator animation){
									currentTransitionAnim=null;
									avatarsView.setVisibility(GONE);
									counter.setAlpha(1f);
									counter.setTranslationX(0f);
									avatarsView.setAlpha(1f);
									avatarsView.setTranslationX(0f);
								}
							});
							currentTransitionAnim=set;
							set.start();

							return true;
						}
					});
				}else{
					avatarsView.setVisibility(GONE);
					counter.setVisibility(VISIBLE);
				}
			}
		}else if(animated && countChanged){
			int prevWidth=getWidth();
			getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
				@Override
				public boolean onPreDraw(){
					getViewTreeObserver().removeOnPreDrawListener(this);

					ObjectAnimator anim=ObjectAnimator.ofFloat(counter, View.TRANSLATION_X, prevWidth-getWidth(), 0f);
					anim.setDuration(220);
					anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
					anim.addListener(new AnimatorListenerAdapter(){
						@Override
						public void onAnimationEnd(Animator animation){
							counter.setTranslationX(0f);
							currentTransitionAnim=null;
						}
					});
					currentTransitionAnim=anim;
					anim.start();

					return true;
				}
			});
		}
	}

	public TLRPC.TL_reactionCount getReaction(){
		return reactions;
	}

	public void setSelected(boolean selected, boolean animated){
		if(selected==this.selected)
			return;
		this.selected=selected;
		if(currentSelectionAnim!=null){
			currentSelectionAnim.cancel();
			currentSelectionAnim=null;
		}
		if(animated){
			ObjectAnimator anim=ObjectAnimator.ofFloat(this, SELECTEDNESS_PROPERTY, selected ? 1f : 0f);
			anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
			anim.setDuration(200);
			anim.addListener(new AnimatorListenerAdapter(){
				@Override
				public void onAnimationEnd(Animator animation){
					currentSelectionAnim=null;
				}
			});
			currentSelectionAnim=anim;
			anim.start();
		}else{
			selectedness=selected ? 1f : 0f;
			invalidate();
		}
	}

	@Override
	public boolean isSelected(){
		return selected;
	}

	@Override
	protected void onDraw(Canvas canvas){
		super.onDraw(canvas);
		if(selectedness>0f){
			int strokeWidth=AndroidUtilities.dp(1.333f);
			float widthToDraw=selectedness<1f ? strokeWidth*selectedness : strokeWidth;
			rectF.set(0, 0, getWidth(), getHeight());
			rectF.inset(widthToDraw/2f, widthToDraw/2f);
			paint.setStrokeWidth(widthToDraw);
			canvas.drawRoundRect(rectF, rectF.height()/2f, rectF.height()/2f, paint);
		}
	}

	private int getColor(String key){
		Integer color=resProvider.getColor(key);
		return color==null ? Theme.getColor(key) : color;
	}
}
