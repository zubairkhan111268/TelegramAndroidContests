package org.telegram.animcontest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class AnimationSettingsViewController{
	private static final long[] DURATION_OPTIONS={200, 300, 400, 500, 600, 700, 800, 900, 1000, 1500, 2000, 3000, 5000};

	public RecyclerListView list;
	protected Context context;
	private ActionBarPopupWindow.ActionBarPopupWindowLayout durationPopupLayout;
	private ActionBarPopupWindow durationPopupWindow;
	private ActionBarMenuSubItem[] durationItems=new ActionBarMenuSubItem[DURATION_OPTIONS.length];

	protected ArrayList<Item<?>> listItems=new ArrayList<>();
	private AnimationSettingsAdapter adapter;

	@SuppressLint("ClickableViewAccessibility")
	public AnimationSettingsViewController(Context context){
		this.context=context;

		list=new RecyclerListView(context);
		list.setLayoutManager(new LinearLayoutManager(context));
		list.setAdapter(adapter=new AnimationSettingsAdapter());
		list.setClipToPadding(false);
		list.setSectionsType(2);
		list.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		list.setOnItemClickListener(new RecyclerListView.OnItemClickListener(){
			@Override
			public void onItemClick(View view, int position){
				listItems.get(position).onClick();
			}
		});

		durationPopupLayout=new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
		for(int i=0;i<DURATION_OPTIONS.length;i++){
			ActionBarMenuSubItem item = new ActionBarMenuSubItem(context, i==0, i==DURATION_OPTIONS.length-1);
			item.setText(DURATION_OPTIONS[i]+"ms");
			item.setTag(i);
			durationPopupLayout.addView(item);
			durationItems[i]=item;
		}
		durationPopupLayout.setupRadialSelectors(Theme.getColor(Theme.key_dialogButtonSelector));
		durationPopupWindow = new ActionBarPopupWindow(durationPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
		durationPopupWindow.setAnimationEnabled(false);
		durationPopupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
		durationPopupWindow.setOutsideTouchable(true);
		durationPopupWindow.setClippingEnabled(true);
		durationPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
		durationPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
		durationPopupWindow.getContentView().setFocusableInTouchMode(true);
		durationPopupLayout.setOnTouchListener(new View.OnTouchListener() {

			private android.graphics.Rect popupRect = new android.graphics.Rect();

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					if (durationPopupWindow != null && durationPopupWindow.isShowing()) {
						v.getHitRect(popupRect);
						if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
							durationPopupWindow.dismiss();
						}
					}
				}
				return false;
			}
		});
		durationPopupLayout.setDispatchKeyEventListener(keyEvent -> {
			if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && durationPopupWindow != null && durationPopupWindow.isShowing()) {
				durationPopupWindow.dismiss();
			}
		});
	}

	public abstract String getTitle();

	public void update(){
		adapter.notifyDataSetChanged();
	}

	protected TextAndValueItem makeDurationItem(boolean separator, final LongSupplier initialValue, final LongConsumer callback){

		return new TextAndValueItem("Duration", ()->initialValue.getAsLong()+"ms", separator){
			@Override
			public boolean isClickable(){
				return true;
			}

			@Override
			public void onClick(){
				View.OnClickListener clickListener=new View.OnClickListener(){
					@Override
					public void onClick(View view){
						long opt=DURATION_OPTIONS[(Integer)view.getTag()];
						callback.accept(opt);
//						currentView.setTextAndValue("Duration", opt+"ms", separator);
						adapter.notifyDataSetChanged();
						durationPopupWindow.dismiss();
					}
				};
				for(ActionBarMenuSubItem item:durationItems)
					item.setOnClickListener(clickListener);
				durationPopupWindow.setFocusable(true);
				durationPopupWindow.showAtLocation(currentView, Gravity.RIGHT, 0, 0);
			}
		};
	}

	private class AnimationSettingsAdapter extends RecyclerListView.SelectionAdapter<SimpleViewHolder<?>>{

		@NonNull
		@Override
		public SimpleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			for(Item item:listItems)
				if(item.getViewType()==viewType)
					return new SimpleViewHolder(item.createView(context));
			throw new IllegalStateException("no items of this type");
		}

		@Override
		public void onBindViewHolder(@NonNull SimpleViewHolder<?> holder, int position){
			listItems.get(position)._bindView(position, holder.itemView);
		}

		@Override
		public int getItemViewType(int position){
			return listItems.get(position).getViewType();
		}

		@Override
		public int getItemCount(){
			return listItems.size();
		}

		@Override
		public boolean isEnabled(SimpleViewHolder<?> holder){
			return listItems.get(holder.getAdapterPosition()).isClickable();
		}
	}

	private class SimpleViewHolder<VT extends View> extends RecyclerView.ViewHolder{
		public SimpleViewHolder(VT v){
			super(v);
		}
	}

	protected abstract static class Item<VT extends View>{
		protected VT currentView;

		public abstract int getViewType();
		public abstract VT createView(Context context);
		public void onClick(){}
		public void bindView(int position, VT view){}
		public boolean isClickable(){
			return false;
		}

		public final void _bindView(int position, View view){
			currentView=(VT)view;
			bindView(position, (VT)view);
		}
	}

	protected static class TextAndValueItem extends Item<TextSettingsCell>{
		public String title;
		public Supplier<String> value;
		public boolean separator;

		public TextAndValueItem(String title, Supplier<String> value, boolean separator){
			this.title=title;
			this.value=value;
			this.separator=separator;
		}

		@Override
		public int getViewType(){
			return 1;
		}

		@Override
		public TextSettingsCell createView(Context context){
			TextSettingsCell view=new TextSettingsCell(context);
			view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
			return view;
		}

		@Override
		public void bindView(int position, TextSettingsCell view){
			view.setTextAndValue(title, value!=null ? value.get() : null, separator);
		}
	}

	protected class CardDividerItem extends Item<ShadowSectionCell>{

		@Override
		public int getViewType(){
			return 2;
		}

		@Override
		public ShadowSectionCell createView(Context context){
			return new ShadowSectionCell(context);
		}

		@Override
		public void bindView(int position, ShadowSectionCell view){
			view.setBackgroundDrawable(Theme.getThemedDrawable(view.getContext(), position==listItems.size()-1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
		}
	}

	protected class HeaderItem extends Item<HeaderCell>{
		public String title;

		public HeaderItem(String title){
			this.title=title;
		}

		@Override
		public int getViewType(){
			return 3;
		}

		@Override
		public HeaderCell createView(Context context){
			HeaderCell view=new HeaderCell(context);
			view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
			return view;
		}

		@Override
		public void bindView(int position, HeaderCell view){
			view.setText(title);
		}
	}

	protected class TallHeaderItem extends HeaderItem{

		public TallHeaderItem(String title){
			super(title);
		}

		@Override
		public HeaderCell createView(Context context){
			HeaderCell view=new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 21, 0, false);
			view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
			view.setHeight(52);
			return view;
		}

		@Override
		public int getViewType(){
			return 4;
		}
	}

	protected static class BlueTextItem extends TextAndValueItem{

		public BlueTextItem(String title, boolean separator){
			super(title, null, separator);
		}

		@Override
		public int getViewType(){
			return 5;
		}

		@Override
		public TextSettingsCell createView(Context context){
			TextSettingsCell view=super.createView(context);
			view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
			return view;
		}
	}

	protected static class AnimationTimingItem extends Item<AnimationTimingCell>{

		public Supplier<AnimationSettings.TimingParameters> timingParameters;
		public LongSupplier totalDuration;

		public AnimationTimingItem(Supplier<AnimationSettings.TimingParameters> timingParameters, LongSupplier totalDuration){
			this.timingParameters=timingParameters;
			this.totalDuration=totalDuration;
		}

		@Override
		public int getViewType(){
			return 6;
		}

		@Override
		public AnimationTimingCell createView(Context context){
			AnimationTimingCell view=new AnimationTimingCell(context);
			view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
			return view;
		}

		@Override
		public void bindView(int position, AnimationTimingCell view){
			view.setTimingParameters(timingParameters.get(), totalDuration.getAsLong());
		}
	}

	protected static class SeekBarItem extends Item<SeekBarCell>{

		public Supplier<Float> initialValue;
		public Consumer<Float> callback;

		public SeekBarItem(Supplier<Float> initialValue, Consumer<Float> callback){
			this.initialValue=initialValue;
			this.callback=callback;
		}

		@Override
		public int getViewType(){
			return 7;
		}

		@Override
		public SeekBarCell createView(Context context){
			SeekBarCell view=new SeekBarCell(context);
			view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
			return view;
		}

		@Override
		public void bindView(int position, SeekBarCell view){
			view.seekBar.setProgress(initialValue.get());
			view.callback=callback;
		}
	}
}
