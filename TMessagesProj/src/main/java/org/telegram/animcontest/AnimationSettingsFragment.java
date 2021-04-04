package org.telegram.animcontest;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.telegram.animcontest.viewpager2.widget.ViewPager2;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class AnimationSettingsFragment extends BaseFragment {

	private static final int IMPORT_RESULT=719;
	private static final int EXPORT_RESULT=576;

	private Paint backgroundPaint = new Paint();
	private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;

	private AnimationSettingsViewController[] viewControllers;
	private ViewPager2 viewPager;

	private BackgroundSettingsViewController bgController;

	public AnimationSettingsFragment() {
		super();
	}

	@Override
	public View createView(Context context) {
		viewControllers=new AnimationSettingsViewController[]{
				bgController=new BackgroundSettingsViewController(this),
				new TextMessageSettingsViewController(getParentActivity(), "Short Text", () -> AnimationSettings.shortTextMessageParams, "Text scale"),
				new TextMessageSettingsViewController(getParentActivity(), "Long Text", () -> AnimationSettings.longTextMessageParams, "Text scale"),
				new TextMessageSettingsViewController(getParentActivity(), "Link", () -> AnimationSettings.linkPreviewMessageParams, "Text scale"),
				new TextMessageSettingsViewController(getParentActivity(), "Voice", () -> AnimationSettings.voiceMessageParams, "Icon transition"),
				new PhotoMessageSettingsViewController(getParentActivity(), "Photo", () -> AnimationSettings.photoMessageParams),
				new StickerMessageSettingsViewController(getParentActivity(), "Emoji", () -> AnimationSettings.emojiMessageParams, "Input hint reappears"),
				new StickerMessageSettingsViewController(getParentActivity(), "Sticker", () -> AnimationSettings.stickerMessageParams, "Sticker reappears in panel"),
		};

		actionBar.setBackButtonImage(R.drawable.ic_ab_back);
		actionBar.setTitle("Animation Settings");
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
				}else if(id==1){
					startExportChooser();
				}else if(id==2){
					startImportChooser();
				}else if(id==3){
					new AlertDialog.Builder(getParentActivity())
							.setTitle("Restore to Default")
							.setMessage("Are you sure?")
							.setPositiveButton("Yes", new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface dialogInterface, int i){
									AnimationSettings.setDefaults();
									AnimationSettings.save();
									for(AnimationSettingsViewController vc:viewControllers)
										vc.update();
								}
							})
							.setNegativeButton("No", null)
							.show();
				}
			}
		});
		ActionBarMenu menu=actionBar.createMenu();
		ActionBarMenuItem item=menu.addItem(0, R.drawable.ic_ab_other);
		item.addSubItem(1, "Export Parameters");
		item.addSubItem(2, "Import Parameters");
		item.addSubItem(3, "Restore to Default").setTextColor(Theme.getColor(Theme.key_dialogTextRed));
		hasOwnBackground = true;
		FrameLayout frameLayout;
		fragmentView = frameLayout = new FrameLayout(context){
			private boolean globalIgnoreLayout;
			@Override
			protected void dispatchDraw(Canvas canvas) {
				super.dispatchDraw(canvas);
				if (parentLayout != null) {
					parentLayout.drawHeaderShadow(canvas, actionBar.getMeasuredHeight() + (int) actionBar.getTranslationY());
				}
			}

			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				int widthSize = MeasureSpec.getSize(widthMeasureSpec);
				int heightSize = MeasureSpec.getSize(heightMeasureSpec);

				setMeasuredDimension(widthSize, heightSize);

				measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
				int actionBarHeight = actionBar.getMeasuredHeight();
				globalIgnoreLayout = true;
				for(AnimationSettingsViewController viewController : viewControllers){
					if(viewController==null){
						continue;
					}
					if(viewController.list!=null){
						viewController.list.setPadding(0, actionBarHeight, 0, AndroidUtilities.dp(4));
					}
				}
				globalIgnoreLayout = false;

				int childCount = getChildCount();
				for (int i = 0; i < childCount; i++) {
					View child = getChildAt(i);
					if (child == null || child.getVisibility() == GONE || child == actionBar) {
						continue;
					}
					measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
				}
			}

			@Override
			public void requestLayout() {
				if (globalIgnoreLayout) {
					return;
				}
				super.requestLayout();
			}

			@Override
			protected void onDraw(Canvas canvas) {
				backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
				canvas.drawRect(0, actionBar.getMeasuredHeight() + actionBar.getTranslationY(), getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
			}
		};

		RecyclerView.OnScrollListener scrollListener=new RecyclerView.OnScrollListener() {

			@Override
			public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
				if (newState != RecyclerView.SCROLL_STATE_DRAGGING) {
					int scrollY = (int) -actionBar.getTranslationY();
					int actionBarHeight = ActionBar.getCurrentActionBarHeight();
					if (scrollY != 0 && scrollY != actionBarHeight) {
						if (scrollY < actionBarHeight / 2) {
							viewControllers[viewPager.getCurrentItem()].list.smoothScrollBy(0, -scrollY);
						} else {
							viewControllers[viewPager.getCurrentItem()].list.smoothScrollBy(0, actionBarHeight - scrollY);
						}
					}
				}
			}

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				if (recyclerView == viewControllers[viewPager.getCurrentItem()].list) {
					float currentTranslation = actionBar.getTranslationY();
					float newTranslation = currentTranslation - dy;
					if (newTranslation < -ActionBar.getCurrentActionBarHeight()) {
						newTranslation = -ActionBar.getCurrentActionBarHeight();
					} else if (newTranslation > 0) {
						newTranslation = 0;
					}
					if (newTranslation != currentTranslation) {
						setScrollY(newTranslation);
					}
				}
			}
		};
		for(int i=0;i<viewControllers.length;i++){
			viewControllers[i].list.setOnScrollListener(scrollListener);
		}

		viewPager=new ViewPager2(context);
		viewPager.setAdapter(new TabPagerAdapter());

		frameLayout.setWillNotDraw(false);
		frameLayout.addView(viewPager);
		frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
		scrollSlidingTextTabStrip=new ScrollSlidingTextTabStrip(context);
		int i=0;
		for(AnimationSettingsViewController controller:viewControllers){
			scrollSlidingTextTabStrip.addTextTab(i, controller.getTitle());
			i++;
		}
		scrollSlidingTextTabStrip.finishAddingTabs();
		viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback(){
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels){
				scrollSlidingTextTabStrip.selectTabAtPositionWithoutBullshit(position, positionOffset);
			}

			@Override
			public void onPageSelected(int position){
				int scrollY = (int) -actionBar.getTranslationY();
				RecyclerView rv=viewControllers[position].list;
				if((rv.getChildCount()==0 || (rv.getChildAdapterPosition(rv.getChildAt(0))==0 && rv.getChildAt(0).getY()>actionBar.getTranslationY())) && scrollY>0){
					ObjectAnimator oa=ObjectAnimator.ofFloat(actionBar, "translationY", 0).setDuration(150);
					oa.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
						@Override
						public void onAnimationUpdate(ValueAnimator valueAnimator){
							fragmentView.invalidate();
						}
					});
					oa.start();
				}
			}

			@Override
			public void onPageScrollStateChanged(int state){
				if(state==ViewPager2.SCROLL_STATE_IDLE){
					scrollSlidingTextTabStrip.selectTabWithId(viewPager.getCurrentItem(), 1f);
				}
			}
		});
		scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate(){
			@Override
			public void onPageSelected(int page, boolean forward){
				viewPager.setCurrentItem(page, true);
			}

			@Override
			public void onPageScrolled(float progress){

			}
		});

		actionBar.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));

		return fragmentView;
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onPause(){
		AnimationSettings.save();
		super.onPause();
	}

	@Override
	public void onFragmentDestroy(){
		bgController.onFragmentDestroy();
		super.onFragmentDestroy();
	}

	@Override
	public boolean isSwipeBackEnabled(MotionEvent event) {
		return viewPager.getCurrentItem()==0;
	}

	private void setScrollY(float value) {
		actionBar.setTranslationY(value);
		for (AnimationSettingsViewController controller:viewControllers) {
			controller.list.setPinnedSectionOffsetY((int) value);
		}
		fragmentView.invalidate();
	}

	@Override
	public ArrayList<ThemeDescription> getThemeDescriptions() {
		ArrayList<ThemeDescription> arrayList = new ArrayList<>();

		arrayList.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_windowBackgroundGray));
		arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
		arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
		arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
		arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

		arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabActiveText));
		arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabUnactiveText));
		arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabLine));
		arrayList.add(new ThemeDescription(null, 0, null, null, new Drawable[]{scrollSlidingTextTabStrip.getSelectorDrawable()}, null, Theme.key_actionBarTabSelector));

//        for (int a = 0; a < viewPages.length; a++) {
//            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
//            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
//            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
//
//            arrayList.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
//
//            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
//
//            arrayList.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
//
//            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
//            arrayList.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
//
//            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
//            arrayList.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
//            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
//        }

		return arrayList;
	}

	private void startExportChooser(){
		Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.setType("application/json");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.putExtra(Intent.EXTRA_TITLE, "AnimationSettings.json");
		startActivityForResult(intent, EXPORT_RESULT);
	}

	private void startImportChooser(){
		Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/json");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		startActivityForResult(intent, IMPORT_RESULT);
	}

	@Override
	public void onActivityResultFragment(int requestCode, int resultCode, Intent data){
		if(resultCode!=Activity.RESULT_OK)
			return;
		if(requestCode==EXPORT_RESULT){
			try(OutputStream out=getParentActivity().getContentResolver().openOutputStream(data.getData())){
				AnimationSettings.write(out);

				String name;
				try(Cursor c=getParentActivity().getContentResolver().query(data.getData(), new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)){
					c.moveToFirst();
					name=c.getString(0);
				}
				Toast.makeText(getParentActivity(), "Saved as "+name, Toast.LENGTH_LONG).show();
			}catch(IOException x){
				new AlertDialog.Builder(getParentActivity())
						.setTitle("Error")
						.setMessage(x.getLocalizedMessage())
						.setPositiveButton("OK", null)
						.show();
			}
		}else if(requestCode==IMPORT_RESULT){
			try(InputStream in=getParentActivity().getContentResolver().openInputStream(data.getData())){
				AnimationSettings.read(in);

				AnimationSettings.save();
				for(AnimationSettingsViewController vc:viewControllers)
					vc.update();

				String name;
				try(Cursor c=getParentActivity().getContentResolver().query(data.getData(), new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)){
					c.moveToFirst();
					name=c.getString(0);
				}
				Toast.makeText(getParentActivity(), "Loaded from "+name, Toast.LENGTH_LONG).show();
			}catch(IOException|JSONException x){
				new AlertDialog.Builder(getParentActivity())
						.setTitle("Error")
						.setMessage(x.getLocalizedMessage())
						.setPositiveButton("OK", null)
						.show();
			}
		}
	}

	private class TabPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return new RecyclerView.ViewHolder(viewControllers[viewType].list){
			};
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position){

		}

		@Override
		public int getItemCount(){
			return viewControllers.length;
		}

		@Override
		public int getItemViewType(int position){
			return position;
		}
	}
}
