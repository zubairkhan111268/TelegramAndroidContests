package org.telegram.ui;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MediaCalendarDeleteHistoryActivity extends BaseFragment {

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint selectionPaint=new Paint(Paint.ANTI_ALIAS_FLAG);

    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;


    int startFromYear;
    int startFromMonth;
    int monthCount;

    CalendarAdapter adapter;
    Callback callback;


    SparseArray<SparseArray<PeriodDay>> messagesByYearMonth= new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;
    private FrameLayout bottomOverlayChat;
    private TextView bottomOverlayChatText, bottomOverlayChatTextRed;
    private boolean actionModeActive;
    private TextView actionModeTitle;
    private View blurredView;
    private View previewMenu;
    private LocalDate previewDay, selectedDayFrom, selectedDayTo;
    private ChatActivity parentChatFragment;
    private boolean finishSelfAfterPreview;
    private long dayFromAnimStartTime, dayToAnimStartTime;
    private ArgbEvaluator colorEvaluator=new ArgbEvaluator();
    private boolean deselecting;
    private LocalDate deselectDayFrom, deselectDayTo;

    public MediaCalendarDeleteHistoryActivity(Bundle args, ChatActivity parentChatFragment, int selectedDate) {
        super(args);
        this.parentChatFragment=parentChatFragment;

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        FrameLayout contentWrap=new FrameLayout(context);
        LinearLayout actionBarWrap=new LinearLayout(context);
        actionBarWrap.setOrientation(LinearLayout.VERTICAL);
        contentWrap.addView(actionBarWrap);
        contentView = new FrameLayout(context);
        createActionBar(context);
        actionBarWrap.addView(actionBar);
        actionBarWrap.addView(contentView);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        adapter = new CalendarAdapter();
        adapter.setHasStableIds(true);
        listView.setAdapter(adapter);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });
        listView.setItemAnimator(new DefaultItemAnimator());

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, 48));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if(actionBar.isActionModeShowed())
                        dismissActionMode();
                    else
                        finishFragment();
                }
            }
        });

        fragmentView = contentWrap;

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }


        loadNext();
        updateColors();
        activeTextPaint.setColor(Color.WHITE);


        bottomOverlayChat = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        bottomOverlayChat.setWillNotDraw(false);
        bottomOverlayChat.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        bottomOverlayChat.setOnClickListener(this::onBottomButtonClick);
        bottomOverlayChat.setForeground(new InsetDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2), 0, Theme.chat_composeShadowDrawable.getIntrinsicHeight(), 0, 0));
        contentView.addView(bottomOverlayChat, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        bottomOverlayChatText = new TextView(context);
        bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayChatText.setText(LocaleController.getString("SelectDays", R.string.SelectDays));
        bottomOverlayChatText.setAllCaps(true);
        bottomOverlayChat.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        bottomOverlayChatTextRed = new TextView(context);
        bottomOverlayChatTextRed.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatTextRed.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatTextRed.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        bottomOverlayChatTextRed.setText(LocaleController.getString("ClearHistory", R.string.ClearHistory));
        bottomOverlayChatTextRed.setAllCaps(true);
        bottomOverlayChatTextRed.setVisibility(View.GONE);
        bottomOverlayChat.addView(bottomOverlayChatTextRed, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        blurredView = new View(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };
        blurredView.setVisibility(View.GONE);
        contentWrap.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
//        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
//        if (photosVideosTypeFilter == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
//            req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
//        } else if (photosVideosTypeFilter == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
//            req.filter = new TLRPC.TL_inputMessagesFilterVideo();
//        } else {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
//        }
//        req.filter=new TLRPC.TL_inputMessagesFilterEmpty();

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMonth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMonth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    periodDay.count=period.count;
                    periodDay.minMsgID=period.min_msg_id;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }

                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        // what THE FUCK was this meant to achieve?
//        int listMinMonth = Integer.MAX_VALUE;
//        for (int i = 0; i < listView.getChildCount(); i++) {
//            View child = listView.getChildAt(i);
//            if (child instanceof MonthView) {
//                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
//                if (currentMonth < listMinMonth) {
//                    listMinMonth = currentMonth;
//                }
//            }
//        };
//        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
//        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
//        if (min1 + 3 >= min2) {
//            loadNext();
//        }
		if(listView.getChildCount()>0){
            int pos=listView.getChildAdapterPosition(listView.getChildAt(listView.getChildCount()-1));
            if(pos==monthCount-1){
                loadNext();
            }
        }
    }

    private boolean onDayTap(int day, int month, int year){
        if(actionModeActive){
            selectDay(LocalDate.of(year, month+1, day));
        }
        return false;
    }

    private boolean onDayLongTap(int day, int month, int year){
    	if(getParentActivity()==null)
    	    return false;
        if(!actionModeActive){
        	previewDay=LocalDate.of(year, month+1, day);
        	if(previewDay.isAfter(LocalDate.now()))
        	    return false;

            Bundle args = new Bundle();
            if (DialogObject.isUserDialog(dialogId)) {
                args.putLong("user_id", dialogId);
            } else {
                long did = dialogId;
                TLRPC.Chat chat = getMessagesController().getChat(-did);
                if (chat != null && chat.migrated_to != null) {
                    args.putLong("migrated_to", did);
                    did = -chat.migrated_to.channel_id;
                }
                args.putLong("chat_id", -did);
            }
//            args.putInt("message_id", period.minMsgID);
            args.putInt("date_offset", (int)previewDay.atStartOfDay(ZoneId.systemDefault()).toInstant().getEpochSecond());
            prepareBlurBitmap();
            ChatActivity fragment=new ChatActivity(args);
            presentFragmentAsPreview(fragment);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams) fragment.getFragmentView().getLayoutParams();
            lp.topMargin=AndroidUtilities.dp(30);//AndroidUtilities.statusBarHeight;

			parentLayout.setDrawPreviewDrawables(false);
			if(previewMenu==null){
                ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout=new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, null);
                popupLayout.setMinimumWidth(AndroidUtilities.dp(200));
                Rect backgroundPaddings=new Rect();
                Drawable shadowDrawable=getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
                shadowDrawable.getPadding(backgroundPaddings);
                popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

                ActionBarMenuSubItem cell=new ActionBarMenuSubItem(getParentActivity(), true, false);
                cell.setMinimumWidth(AndroidUtilities.dp(200));
                cell.setTextAndIcon(LocaleController.getString("JumpToDate", R.string.JumpToDate), R.drawable.msg_message);
                cell.setOnClickListener(this::onPreviewJumpToDateClick);
                popupLayout.addView(cell);
                cell=new ActionBarMenuSubItem(getParentActivity(), true, false);
                cell.setMinimumWidth(AndroidUtilities.dp(200));
                cell.setTextAndIcon(LocaleController.getString("SelectThisDay", R.string.SelectThisDay), R.drawable.msg_select);
                cell.setOnClickListener(this::onPreviewSelectDayClick);
                popupLayout.addView(cell);
                cell=new ActionBarMenuSubItem(getParentActivity(), true, false);
                cell.setMinimumWidth(AndroidUtilities.dp(200));
                cell.setTextAndIcon(LocaleController.getString("ClearHistory", R.string.ClearHistory), R.drawable.msg_delete);
                cell.setOnClickListener(this::onPreviewClearHistoryClick);
                popupLayout.addView(cell);

                FrameLayout popupWrap=new FrameLayout(getParentActivity());
                popupWrap.setOnTouchListener(this::onPopupWrapTouch);
                popupWrap.setPadding(0, 0, 0, AndroidUtilities.dp(18));
                popupWrap.addView(popupLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT));

                previewMenu=popupWrap;
            }

            parentLayout.addView(previewMenu, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            previewMenu.measure(View.MeasureSpec.AT_MOST | AndroidUtilities.displaySize.x, View.MeasureSpec.UNSPECIFIED);
            lp.bottomMargin=previewMenu.getMeasuredHeight();
            previewMenu.setAlpha(0);
            parentLayout.touchablePreviewMode=true;

            return true;
        }
        return false;
    }

    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        int w = (int) (fragmentView.getMeasuredWidth() / 6.0f);
        int h = (int) (fragmentView.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        fragmentView.draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onTransitionAnimationProgress(boolean isOpen, float progress) {
        if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            if (isOpen) {
                blurredView.setAlpha(1.0f - progress);
                previewMenu.setAlpha(1f-progress);
                previewMenu.setTranslationY(AndroidUtilities.dp(5)*progress);
            } else {
                blurredView.setAlpha(progress);
                previewMenu.setAlpha(progress);
                previewMenu.setTranslationY(AndroidUtilities.dp(5)*(1f-progress));
            }
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
            parentLayout.setDrawPreviewDrawables(true);
            parentLayout.removeView(previewMenu);
            parentLayout.touchablePreviewMode=false;
            if(finishSelfAfterPreview){
                parentLayout.post(this::finishFragment);
            }
        }
    }

    @Override
    public boolean onBackPressed(){
        if(actionModeActive){
            dismissActionMode();
            return false;
        }
        return super.onBackPressed();
    }

    private void onBottomButtonClick(View v){
        if(!actionModeActive){
            startActionMode();
        }else{
            confirmAndClearHistoryInRange(selectedDayFrom, selectedDayTo);
        }
        updateBottomButton();
    }

    private void onPreviewJumpToDateClick(View v){
    	finishSelfAfterPreview=true;
        finishPreviewFragment();
        parentChatFragment.jumpToDate((int)previewDay.atStartOfDay(ZoneId.systemDefault()).toInstant().getEpochSecond());
    }

    private void onPreviewSelectDayClick(View v){
        finishPreviewFragment();
        selectDay(previewDay);
    }

    private void onPreviewClearHistoryClick(View v){
        finishPreviewFragment();
        confirmAndClearHistoryInRange(previewDay, null);
    }

    private boolean onPopupWrapTouch(View view, MotionEvent ev){
        if(ev.getAction()==MotionEvent.ACTION_DOWN){
            finishPreviewFragment();
        }
        return true;
    }

    private void startActionMode(){
        if(!actionBar.actionModeIsExist(null)){
            ActionBarMenu actionMode=actionBar.createActionMode();
            actionModeTitle=new TextView(actionMode.getContext());
            actionModeTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            actionModeTitle.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
            actionModeTitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            actionMode.addView(actionModeTitle, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        }
        actionModeTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, actionBar.getTitleTextView().getPaint().getTextSize());
        actionModeTitle.setText(LocaleController.getString("SelectDaysTitle", R.string.SelectDaysTitle));
        actionBar.showActionMode();
        actionModeActive=true;
    }

    private void dismissActionMode(){
        actionBar.hideActionMode();
        actionModeActive=false;
        updateBottomButton();
        if(selectedDayFrom!=null){
            deselecting=true;
            deselectDayFrom=selectedDayFrom;
            deselectDayTo=selectedDayTo;
            selectedDayFrom=selectedDayTo=null;
            dayFromAnimStartTime=dayToAnimStartTime=SystemClock.uptimeMillis();
        }
    }

    private void updateBottomButton(){
        if(actionModeActive){
            bottomOverlayChatText.animate().alpha(0f).setDuration(180).withEndAction(()->bottomOverlayChatText.setVisibility(View.GONE)).start();
            if(bottomOverlayChatTextRed.getVisibility()!=View.VISIBLE)
                bottomOverlayChatTextRed.setAlpha(0f);
            bottomOverlayChatTextRed.setVisibility(View.VISIBLE);
            bottomOverlayChatTextRed.animate().alpha(selectedDayFrom==null ? .5f : 1f).setDuration(180).start();
            bottomOverlayChat.setEnabled(selectedDayFrom!=null);
        }else{
            bottomOverlayChatTextRed.animate().alpha(0f).setDuration(180).withEndAction(()->bottomOverlayChatTextRed.setVisibility(View.GONE)).start();
            bottomOverlayChatText.setVisibility(View.VISIBLE);
            bottomOverlayChatText.setAlpha(0f);
            bottomOverlayChatText.animate().alpha(1f).setDuration(180).start();
            bottomOverlayChat.setEnabled(true);
        }
    }

    private void selectDay(LocalDate day){
        if(day.isAfter(LocalDate.now()))
            return;
        deselecting=false;
        if(!actionModeActive){
            startActionMode();
        }
        if(selectedDayFrom!=null && selectedDayTo!=null){
            selectedDayFrom=day;
            selectedDayTo=null;
            dayFromAnimStartTime=SystemClock.uptimeMillis();
            dayToAnimStartTime=0;
        }else if(selectedDayFrom==null){
            selectedDayFrom=day;
            dayFromAnimStartTime=SystemClock.uptimeMillis();
        }else{
            if(selectedDayFrom.isEqual(day)){
                return;
            }else if(day.isAfter(selectedDayFrom)){
                selectedDayTo=day;
                dayToAnimStartTime=SystemClock.uptimeMillis();
            }else{
                selectedDayTo=selectedDayFrom;
                selectedDayFrom=day;
                dayToAnimStartTime=dayFromAnimStartTime;
                dayFromAnimStartTime=SystemClock.uptimeMillis();
            }
        }
        for(int i=0;i<listView.getChildCount();i++){
            listView.getChildAt(i).invalidate();
        }
        updateBottomButton();
        int numDays;
        if(selectedDayTo==null){
            numDays=1;
        }else{
            numDays=(int)selectedDayFrom.until(selectedDayTo, ChronoUnit.DAYS)+1;
        }
        actionModeTitle.setText(LocaleController.formatPluralString("Days", numDays));
    }

    private void confirmAndClearHistoryInRange(LocalDate from, LocalDate to){
        int numDays;
        if(to==null || from.isEqual(to)){
            numDays=1;
        }else{
            numDays=(int)from.until(to, ChronoUnit.DAYS)+1;
        }
        TLRPC.User user=getMessagesController().getUser(dialogId);

        final boolean[] deleteForAll={false};
        FrameLayout frameLayout = new FrameLayout(getParentActivity());
        CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
        cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        cell.setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", false, false);
        cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
        frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        cell.setOnClickListener(v -> {
            CheckBoxCell cell1 = (CheckBoxCell) v;
            deleteForAll[0] = !deleteForAll[0];
            cell1.setChecked(deleteForAll[0], true);
        });

        AlertDialog.Builder builder=new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString("DeleteMessagesRangeTitle", R.string.DeleteMessagesRangeTitle))
                .setView(frameLayout)
                .setCustomViewOffset(9);
        if(numDays==1){
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DeleteMessagesRangeTextOneDay", R.string.DeleteMessagesRangeTextOneDay,
                    LocaleController.formatDateChat(from.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()))));
        }else{
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DeleteMessagesRangeText", R.string.DeleteMessagesRangeText, LocaleController.formatPluralString("SelectedDays", numDays))));
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString("Delete", R.string.Delete), new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i){
                        doClearHistoryInRange(from, to==null ? from : to, deleteForAll[0]);
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }

    private void doClearHistoryInRange(LocalDate from, LocalDate to, boolean revoke){
        dismissActionMode();
        getMessagesController().deleteDialog(dialogId, revoke, (int)from.atStartOfDay(ZoneId.systemDefault()).toEpochSecond(), (int)to.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()+3600*23+3599);
//        listView.postDelayed(()->{
            int selectedFrom=from.getYear()*100+from.getMonthValue()-1;
            int selectedTo=to.getYear()*100+to.getMonthValue()-1;
            int selectedFromFull=selectedFrom*100+from.getDayOfMonth()-1;
            int selectedToFull=selectedTo*100+to.getDayOfMonth()-1;
            for(int i=0; i<messagesByYearMonth.size(); i++){
                int key=messagesByYearMonth.keyAt(i);
                if(key>=selectedFrom || key<=selectedTo){
                    SparseArray<PeriodDay> month=messagesByYearMonth.get(key);
                    List<Integer> keysToRemove=new ArrayList<>();
                    for(int j=0; j<month.size(); j++){
                        int mkey=month.keyAt(j);
                        int day=mkey+key*100;
                        if(day>=selectedFromFull && day<=selectedToFull){
                            keysToRemove.add(mkey);
                        }
                    }
                    for(int mkey : keysToRemove){
                        month.remove(mkey);
                    }
                    adapter.notifyItemChanged(adapterPositionForYearAndMonth(key/100, key%100));
                }
            }
//            adapter.notifyDataSetChanged();
//        }, 200);
    }

    private int adapterPositionForYearAndMonth(int year, int month){
        return (year-startFromYear)*12+month-startFromMonth;
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMonth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthView extends FrameLayout implements GestureDetector.OnGestureListener{

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

        SparseArray<PeriodDay> animatedFromMessagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> animatedFromImagesByDays = new SparseArray<>();

        boolean attached;
        float animationProgress = 1f;

        private GestureDetector gestureDetector;
        private RectF tmpRect=new RectF();

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));

            gestureDetector=new GestureDetector(context, this);
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
//            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

//            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }
//            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime= (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
			return gestureDetector.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            LocalDate selectedDayFrom, selectedDayTo;
            if(deselecting){
                selectedDayFrom=deselectDayFrom;
                selectedDayTo=deselectDayTo;
            }else{
                selectedDayFrom=MediaCalendarDeleteHistoryActivity.this.selectedDayFrom;
                selectedDayTo=MediaCalendarDeleteHistoryActivity.this.selectedDayTo;
            }

            int selectedFrom=selectedDayFrom==null ? 0 : selectedDayFrom.getYear()*10000+selectedDayFrom.getMonthValue()*100+selectedDayFrom.getDayOfMonth();
            int selectedTo=selectedDayTo==null ? 0 : selectedDayTo.getYear()*10000+selectedDayTo.getMonthValue()*100+selectedDayTo.getDayOfMonth();
            float globalSelectAnimProgress=1f;
            if(selectedDayTo!=null || selectedDayFrom!=null){
                globalSelectAnimProgress=(float)(SystemClock.uptimeMillis()-Math.max(dayFromAnimStartTime, dayToAnimStartTime))/180f;
                if(globalSelectAnimProgress>1f){
                    globalSelectAnimProgress=deselecting ? 0f : 1f;
//                    if(deselecting){
//                        deselecting=false;
//                        deselectDayFrom=null;
//                        deselectDayTo=null;
//                    }
                }else{
                    if(deselecting)
                        globalSelectAnimProgress=CubicBezierInterpolator.EASE_OUT.getInterpolation(1f-globalSelectAnimProgress);
                    else
                        globalSelectAnimProgress=CubicBezierInterpolator.EASE_OUT.getInterpolation(globalSelectAnimProgress);
                    invalidate();
                }
            }

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);

            if(selectedDayTo!=null && selectedDayFrom!=null){
                int monthStartDay=currentYear*10000+(currentMonthInYear+1)*100+1;
                int monthEndDay=currentYear*10000+(currentMonthInYear+1)*100+daysInMonth;
                if((selectedTo>=monthStartDay && selectedTo<=monthEndDay) || // end of the range is within this month
                        (selectedFrom>=monthStartDay && selectedFrom<=monthEndDay) || // start of the range is within this month
                        (selectedFrom<monthStartDay && selectedTo>monthEndDay)){ // this entire month is within the range
                    int numWeeks=(int)Math.ceil((daysInMonth+startDayOfWeek)/7.0);
                    int weekStartDay=monthStartDay;
                    for(int i=0;i<numWeeks;i++){
                        int colStart=selectedFrom-weekStartDay;
                        int colEnd=selectedTo-weekStartDay;
                        if(i==0){
                            colStart+=startDayOfWeek;
                            colEnd+=startDayOfWeek;
                        }
                        if((colStart>=0 || colEnd>=0) && (colStart<=6 || colEnd<=6)){
                            colStart=Math.min(Math.max(i==0 ? startDayOfWeek : 0, colStart), 6);
                            colEnd=Math.min(Math.max(0, colEnd), i==numWeeks-1 && (startDayOfWeek+daysInMonth)%7>0 ? (startDayOfWeek+daysInMonth)%7-1 : 6);
                            float cx1 = xStep * colStart + xStep / 2f;
                            float cy = yStep * i + yStep / 2f + AndroidUtilities.dp(44);
                            float cx2 = xStep * colEnd + xStep / 2f;
                            float size=AndroidUtilities.dp(22);
                            tmpRect.set(cx1-size, cy-size, cx2+size, cy+size);

                            selectionPaint.setStyle(Paint.Style.FILL);
                            selectionPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                            selectionPaint.setAlpha(Math.round(selectionPaint.getAlpha()*.16f*globalSelectAnimProgress));
                            canvas.drawRoundRect(tmpRect, size, size, selectionPaint);
                        }
                        if(i==0)
                            weekStartDay+=(7-startDayOfWeek);
                        else
                            weekStartDay+=7;
                    }
                }
            }

            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);
                int day=currentYear*10000+(currentMonthInYear+1)*100+(i+1);

                boolean isSelected=(selectedDayFrom!=null && selectedFrom==day) || (selectedDayTo!=null && selectedTo==day);
                boolean isInSelectedRange=selectedDayFrom!=null && selectedDayTo!=null && day>=selectedFrom && day<=selectedTo;
                float selectAnimProgress=globalSelectAnimProgress;

                if(isSelected){
                    long startTime=selectedFrom==day ? dayFromAnimStartTime : dayToAnimStartTime;
                    selectAnimProgress=(float)(SystemClock.uptimeMillis()-startTime)/180f;
                    if(selectAnimProgress>1f){
                        selectAnimProgress=deselecting ? 0f : 1f;
                    }else if(deselecting){
                        selectAnimProgress=CubicBezierInterpolator.EASE_OUT.getInterpolation(1f-selectAnimProgress);
                    }else{
                        selectAnimProgress=CubicBezierInterpolator.EASE_OUT.getInterpolation(selectAnimProgress);
                    }
                    selectionPaint.setStyle(Paint.Style.STROKE);

                    selectionPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    selectionPaint.setStrokeWidth(AndroidUtilities.dp(4));
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(15)+AndroidUtilities.dp(4)*selectAnimProgress, selectionPaint);

                    selectionPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                    selectionPaint.setStrokeWidth(AndroidUtilities.dp(2));
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(17)+AndroidUtilities.dp(4)*selectAnimProgress, selectionPaint);

                    if(imagesByDays==null || imagesByDays.get(i)==null){
                        selectionPaint.setStyle(Paint.Style.FILL);
                        selectionPaint.setColor(selectAnimProgress<1f ?
                                (Integer)colorEvaluator.evaluate(selectAnimProgress, Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_checkboxSquareBackground))
                                : Theme.getColor(Theme.key_checkboxSquareBackground));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(22)-AndroidUtilities.dp(4)*selectAnimProgress, selectionPaint);
                    }
                }

                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                    float alpha = 1f;
                    if(isInSelectedRange || isSelected){
                        float scale=(36f+(8f*(1f-selectAnimProgress)))/44f;
                        canvas.save();
                        canvas.scale(scale, scale, cx, cy);
                    }
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !messagesByDays.get(i).wasDrawn) {
                            messagesByDays.get(i).enterAlpha = 0f;
                            messagesByDays.get(i).startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (messagesByDays.get(i).startEnterDelay > 0) {
                            messagesByDays.get(i).startEnterDelay -= 16;
                            if (messagesByDays.get(i).startEnterDelay < 0) {
                                messagesByDays.get(i).startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                            messagesByDays.get(i).enterAlpha += 16 / 220f;
                            if (messagesByDays.get(i).enterAlpha > 1f) {
                                messagesByDays.get(i).enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = messagesByDays.get(i).enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s,cx, cy);
                        }
                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        imagesByDays.get(i).setImageCoords(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, AndroidUtilities.dp(44), AndroidUtilities.dp(44));
                        imagesByDays.get(i).draw(canvas);
                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
                        }
                    }
                    if(isInSelectedRange || isSelected){
                        canvas.restore();
                    }
                    activeTextPaint.setColor(0xffffffff);
                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }

                } else {
                    if(isSelected){
                        activeTextPaint.setColor(Theme.getColor(Theme.key_checkboxSquareCheck));
                    	if(selectAnimProgress<1f){
                    	    int oldAlpha=textPaint.getAlpha();
                    	    textPaint.setAlpha(Math.round(oldAlpha*(1f-selectAnimProgress)));
                            canvas.drawText(Integer.toString(i+1), cx, cy+AndroidUtilities.dp(5), textPaint);
                            textPaint.setAlpha(oldAlpha);
                            activeTextPaint.setAlpha(Math.round(activeTextPaint.getAlpha()*selectAnimProgress));
                        }
                        canvas.drawText(Integer.toString(i+1), cx, cy+AndroidUtilities.dp(5), activeTextPaint);
                    }else if(isInSelectedRange && selectAnimProgress<1f){
                        activeTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        canvas.drawText(Integer.toString(i+1), cx, cy+AndroidUtilities.dp(5), textPaint);
                        activeTextPaint.setAlpha(Math.round(activeTextPaint.getAlpha()*selectAnimProgress));
                        canvas.drawText(Integer.toString(i+1), cx, cy+AndroidUtilities.dp(5), activeTextPaint);
                    }else{
                        if(isInSelectedRange)
                            activeTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        canvas.drawText(Integer.toString(i+1), cx, cy+AndroidUtilities.dp(5), isInSelectedRange ? activeTextPaint : textPaint);
                    }
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }

        private int getDayForPoint(float x, float y){
            y-=AndroidUtilities.dp(44); // title height
            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            int cellX=(int)Math.floor(x/xStep);
            int cellY=(int)Math.floor(y/yStep);
            int rowCount=(int)Math.ceil((daysInMonth+startDayOfWeek)/7.0);
            int lastRowDayCount=(daysInMonth+startDayOfWeek)%7;
            if(lastRowDayCount==0)
                lastRowDayCount=7;
            // Check that the cell is within the valid area
            if(cellX<0 || cellX>6 || cellY<0 || cellY>rowCount-1)
                return -1;
            // Further check that the cell is a valid day if it's in the first or last row
            if((cellY==0 && cellX<startDayOfWeek) || (cellY==rowCount-1 && cellX>=lastRowDayCount))
                return -1;
            return cellX+cellY*7-startDayOfWeek+1;
        }

        @Override
        public boolean onDown(MotionEvent motionEvent){
            return getDayForPoint(motionEvent.getX(), motionEvent.getY())!=-1;
        }

        @Override
        public void onShowPress(MotionEvent motionEvent){

        }

        @Override
        public boolean onSingleTapUp(MotionEvent motionEvent){
            int day=getDayForPoint(motionEvent.getX(), motionEvent.getY());
            if(onDayTap(day, currentMonthInYear, currentYear)){
                playSoundEffect(SoundEffectConstants.CLICK);
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1){
            return false;
        }

        @Override
        public void onLongPress(MotionEvent motionEvent){
            int day=getDayForPoint(motionEvent.getX(), motionEvent.getY());
            if(day!=-1 && onDayLongTap(day, currentMonthInYear, currentYear)){
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }

        @Override
        public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1){
            return false;
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onDateSelected(int messageId, int startOffset);
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;

        int minMsgID;
        int count;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                updateColors();
            }
        };
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(bottomOverlayChat, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow));
        themeDescriptions.add(new ThemeDescription(bottomOverlayChat, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground));
        themeDescriptions.add(new ThemeDescription(bottomOverlayChatText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));

        return themeDescriptions;
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }
}
