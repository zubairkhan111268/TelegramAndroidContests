package org.telegram.ui.Components.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.MessageSeenView;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SponsoredMessageInfoView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManagerFixed;

public class ChatMessagePopupMenu{
	private MessageObject selectedObject;
	private MessageObject.GroupedMessages selectedObjectGroup;
	private ChatActivity fragment;
	private int type;
	private boolean single;
	private boolean allowChatActions, allowUnpin, allowPin, allowEdit;

	private AnimatorSet scrimAnimatorSet;
	private FrameLayout windowView;
	private LinearLayout menuWrapper;
	private ActionBarPopupWindow scrimPopupWindow;
	private ActionBarPopupWindow mesageSeenUsersPopupWindow;
	private int scrimPopupX, scrimPopupY;
	private ArrayList<ActionBarMenuSubItem> scrimPopupWindowItems;
	private PopupMenuListener listener;
	private WindowManager wm;
	private boolean isShowing;
	private SpringAnimation showAnim;
	private MessageSeenView messageSeenView;

	private ArrayList<ChatMessagePopupMenu.Option> options = new ArrayList<>();

	private ActionBarMenuSubItem menuDeleteItem;
	private Runnable updateDeleteItemRunnable = new Runnable() {
		@Override
		public void run() {
			if (selectedObject == null || menuDeleteItem == null) {
				return;
			}
			int remaining = Math.max(0, selectedObject.messageOwner.ttl_period - (fragment.getConnectionsManager().getCurrentTime() - selectedObject.messageOwner.date));
			String ramainingStr;
			if (remaining < 24 * 60 * 60) {
				ramainingStr = AndroidUtilities.formatDuration(remaining, false);
			} else {
				ramainingStr = LocaleController.formatPluralString("Days", Math.round(remaining / (24 * 60 * 60.0f)));
			}
			menuDeleteItem.setSubtext(LocaleController.formatString("AutoDeleteIn", R.string.AutoDeleteIn, ramainingStr));
			AndroidUtilities.runOnUIThread(updateDeleteItemRunnable, 1000);
		}
	};

	public ChatMessagePopupMenu(ChatActivity abomination, MessageObject msg, MessageObject.GroupedMessages msgGroup, int type, boolean single, boolean allowChatActions, boolean allowUnpin, boolean allowPin, boolean allowEdit){
		fragment=abomination;
		selectedObject=msg;
		selectedObjectGroup=msgGroup;
		this.type=type;
		this.single=single;
		this.allowChatActions=allowChatActions;
		this.allowUnpin=allowUnpin;
		this.allowPin=allowPin;
		this.allowEdit=allowEdit;

		wm=(WindowManager)getParentActivity().getSystemService(Context.WINDOW_SERVICE);
	}

	public void setListener(PopupMenuListener listener){
		this.listener=listener;
	}

	public boolean populateItems(){

		TLRPC.Chat currentChat=fragment.getCurrentChat();
		TLRPC.EncryptedChat currentEncryptedChat=fragment.getCurrentEncryptedChat();
		TLRPC.User currentUser=fragment.getCurrentUser();
		int chatMode=fragment.getChatMode();

		if ((type>=0) || ((type==-1) && single && (selectedObject.isSending() || selectedObject.isEditing()) && (fragment.getCurrentEncryptedChat()==null))) {
//			selectedObject = message;
//			selectedObjectGroup = groupedMessages;

			if (type == -1) {
				if (selectedObject.type == 0 || selectedObject.isAnimatedEmoji() || MessageObject.getMessageCaption(selectedObject, selectedObjectGroup) != null) {
					options.add(ChatMessagePopupMenu.Option.COPY);
				}
				options.add(ChatMessagePopupMenu.Option.CANCEL_SENDING);
			} else if (type == 0) {
				options.add(ChatMessagePopupMenu.Option.RETRY);
				options.add(ChatMessagePopupMenu.Option.DELETE);
			} else if (type == 1) {
				if (currentChat != null) {
					if (allowChatActions) {
						options.add(ChatMessagePopupMenu.Option.REPLY);
					}
					if (!fragment.isThreadChat() && chatMode != ChatActivity.MODE_SCHEDULED && selectedObject.hasReplies() && currentChat.megagroup && selectedObject.canViewThread()) {
						options.add(ChatMessagePopupMenu.Option.VIEW_THREAD);
					}
					if (allowUnpin) {
						options.add(ChatMessagePopupMenu.Option.UNPIN);
					} else if (allowPin) {
						options.add(ChatMessagePopupMenu.Option.PIN);
					}
					if (selectedObject.canEditMessage(fragment.getCurrentChat())) {
						options.add(ChatMessagePopupMenu.Option.EDIT);
					}
					if (selectedObject.contentType == 0 && !selectedObject.isMediaEmptyWebpage() && selectedObject.getId() > 0 && !selectedObject.isOut() && (currentChat != null || currentUser != null && currentUser.bot)) {
						options.add(ChatMessagePopupMenu.Option.REPORT);
					}
				} else {
					if (selectedObject.getId() > 0 && allowChatActions) {
						options.add(ChatMessagePopupMenu.Option.REPLY);
					}
				}
				if (selectedObject.canDeleteMessage(chatMode == ChatActivity.MODE_SCHEDULED, currentChat) && !fragment.hasThreadMessage(selectedObject)) {
					options.add(ChatMessagePopupMenu.Option.DELETE);
				}
			} else if (type == 20) {
				options.add(ChatMessagePopupMenu.Option.RETRY);
				options.add(ChatMessagePopupMenu.Option.COPY);
				options.add(ChatMessagePopupMenu.Option.DELETE);
			} else {
				if (currentEncryptedChat == null) {
					if (chatMode == ChatActivity.MODE_SCHEDULED) {
						options.add(ChatMessagePopupMenu.Option.SCHED_SEND_NOW);
					}
					if (selectedObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
						TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) selectedObject.messageOwner.action;
						options.add(ChatMessagePopupMenu.Option.CALL_VOIP);
						if (VoIPHelper.canRateCall(call)) {
							options.add(ChatMessagePopupMenu.Option.RATE_CALL);
						}
					}
					if (allowChatActions) {
						options.add(ChatMessagePopupMenu.Option.REPLY);
					}
					if (selectedObject.type == 0 || selectedObject.isDice() || selectedObject.isAnimatedEmoji() || MessageObject.getMessageCaption(selectedObject, selectedObjectGroup) != null) {
						options.add(ChatMessagePopupMenu.Option.COPY);
					}
					if (!fragment.isThreadChat() && chatMode != ChatActivity.MODE_SCHEDULED && currentChat != null && (currentChat.has_link || selectedObject.hasReplies()) && currentChat.megagroup && selectedObject.canViewThread()) {
						options.add(ChatMessagePopupMenu.Option.VIEW_THREAD);
					}
					if (!selectedObject.isSponsored() && chatMode != ChatActivity.MODE_SCHEDULED && ChatObject.isChannel(currentChat) && selectedObject.getDialogId() != fragment.getMergeDialogId()) {
						options.add(ChatMessagePopupMenu.Option.COPY_LINK);
					}
					if (type == 2) {
						if (chatMode != ChatActivity.MODE_SCHEDULED) {
							if (selectedObject.type == MessageObject.TYPE_POLL && !selectedObject.isPollClosed()) {
								if (selectedObject.canUnvote()) {
									options.add(ChatMessagePopupMenu.Option.POLL_UNVOTE);
								}
								if (!selectedObject.isForwarded() && (
										selectedObject.isOut() && (!ChatObject.isChannel(currentChat) || currentChat.megagroup) ||
												ChatObject.isChannel(currentChat) && !currentChat.megagroup && (currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.edit_messages))) {
									options.add(ChatMessagePopupMenu.Option.POLL_STOP);
								}
							} else if (selectedObject.isMusic()) {
								options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
							} else if (selectedObject.isDocument()) {
								options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
							}
						}
					} else if (type == 3) {
						if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && MessageObject.isNewGifDocument(selectedObject.messageOwner.media.webpage.document)) {
							options.add(ChatMessagePopupMenu.Option.SAVE_GIF);
						}
					} else if (type == 4) {
						if (selectedObject.isVideo()) {
							if (!selectedObject.needDrawBluredPreview()) {
								options.add(ChatMessagePopupMenu.Option.SAVE_VIDEO_TO_GALLERY);
								options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
							}
						} else if (selectedObject.isMusic()) {
							options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
							options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
						} else if (selectedObject.getDocument() != null) {
							if (MessageObject.isNewGifDocument(selectedObject.getDocument())) {
								options.add(ChatMessagePopupMenu.Option.SAVE_GIF);
							}
							options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
							options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
						} else {
							if (!selectedObject.needDrawBluredPreview()) {
								options.add(ChatMessagePopupMenu.Option.SAVE_VIDEO_TO_GALLERY);
							}
						}
					} else if (type == 5) {
						options.add(ChatMessagePopupMenu.Option.APPLY_LANG_OR_THEME);
						options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
						options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
					} else if (type == 10) {
						options.add(ChatMessagePopupMenu.Option.APPLY_LANG_OR_THEME);
						options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
						options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
					} else if (type == 6) {
						options.add(ChatMessagePopupMenu.Option.SAVE_IMG_TO_GALLERY);
						options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
						options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
					} else if (type == 7) {
						if (selectedObject.isMask()) {
							options.add(ChatMessagePopupMenu.Option.ADD_STICKERS);
						} else {
							options.add(ChatMessagePopupMenu.Option.ADD_STICKERS);
							TLRPC.Document document = selectedObject.getDocument();
							if (!fragment.getMediaDataController().isStickerInFavorites(document)) {
								if (fragment.getMediaDataController().canAddStickerToFavorites() && MessageObject.isStickerHasSet(document)) {
									options.add(ChatMessagePopupMenu.Option.STICKER_ADD_FAVE);
								}
							} else {
								options.add(ChatMessagePopupMenu.Option.STICKER_REMOVE_FAVE);
							}
						}
					} else if (type == 8) {
						TLRPC.User user = fragment.getMessagesController().getUser(selectedObject.messageOwner.media.user_id);
						if (user != null && user.id != fragment.getUserConfig().getClientUserId() && fragment.getContactsController().contactsDict.get(user.id) == null) {
							options.add(ChatMessagePopupMenu.Option.ADD_CONTACT);
						}
						if (!TextUtils.isEmpty(selectedObject.messageOwner.media.phone_number)) {
							options.add(ChatMessagePopupMenu.Option.COPY_NUMBER);
							options.add(ChatMessagePopupMenu.Option.CALL_NUMBER);
						}
					} else if (type == 9) {
						TLRPC.Document document = selectedObject.getDocument();
						if (!fragment.getMediaDataController().isStickerInFavorites(document)) {
							if (MessageObject.isStickerHasSet(document)) {
								options.add(ChatMessagePopupMenu.Option.STICKER_ADD_FAVE);
							}
						} else {
							options.add(ChatMessagePopupMenu.Option.STICKER_REMOVE_FAVE);
						}
					}
					if (!selectedObject.isSponsored() && chatMode != ChatActivity.MODE_SCHEDULED && !selectedObject.needDrawBluredPreview() && !selectedObject.isLiveLocation() && selectedObject.type != 16) {
						options.add(ChatMessagePopupMenu.Option.FORWARD);
					}
					if (allowUnpin) {
						options.add(ChatMessagePopupMenu.Option.UNPIN);
					} else if (allowPin) {
						options.add(ChatMessagePopupMenu.Option.PIN);
					}
					if (allowEdit) {
						options.add(ChatMessagePopupMenu.Option.EDIT);
					}
					if (chatMode == ChatActivity.MODE_SCHEDULED && selectedObject.canEditMessageScheduleTime(currentChat)) {
						options.add(ChatMessagePopupMenu.Option.SCHED_RESCHEDULE);
					}
					if (chatMode != ChatActivity.MODE_SCHEDULED && selectedObject.contentType == 0 && selectedObject.getId() > 0 && !selectedObject.isOut() && (currentChat != null || currentUser != null && currentUser.bot)) {
						if (UserObject.isReplyUser(currentUser)) {
							options.add(ChatMessagePopupMenu.Option.REPORT);
						} else {
							options.add(ChatMessagePopupMenu.Option.REPORT);
						}
					}
					if (selectedObject.canDeleteMessage(chatMode == ChatActivity.MODE_SCHEDULED, currentChat) && !fragment.hasThreadMessage(selectedObject)) {
						options.add(ChatMessagePopupMenu.Option.DELETE);
					}
				} else {
					if (allowChatActions) {
						options.add(ChatMessagePopupMenu.Option.REPLY);
					}
					if (selectedObject.type == 0 || selectedObject.isAnimatedEmoji() || MessageObject.getMessageCaption(selectedObject, selectedObjectGroup) != null) {
						options.add(ChatMessagePopupMenu.Option.COPY);
					}
					if (!fragment.isThreadChat() && chatMode != ChatActivity.MODE_SCHEDULED && currentChat != null && (currentChat.has_link || selectedObject.hasReplies()) && currentChat.megagroup && selectedObject.canViewThread()) {
						options.add(ChatMessagePopupMenu.Option.VIEW_THREAD);
					}
					if (type == 4) {
						if (selectedObject.isVideo()) {
							options.add(ChatMessagePopupMenu.Option.SAVE_VIDEO_TO_GALLERY);
							options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
						} else if (selectedObject.isMusic()) {
							options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
							options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
						} else if (!selectedObject.isVideo() && selectedObject.getDocument() != null) {
							options.add(ChatMessagePopupMenu.Option.SAVE_DOWNLOAD);
							options.add(ChatMessagePopupMenu.Option.SYS_SHARE);
						} else {
							options.add(ChatMessagePopupMenu.Option.SAVE_VIDEO_TO_GALLERY);
						}
					} else if (type == 5) {
						options.add(ChatMessagePopupMenu.Option.APPLY_LANG_OR_THEME);
					} else if (type == 10) {
						options.add(ChatMessagePopupMenu.Option.APPLY_LANG_OR_THEME);
					} else if (type == 7) {
						options.add(ChatMessagePopupMenu.Option.ADD_STICKERS);
					} else if (type == 8) {
						TLRPC.User user = fragment.getMessagesController().getUser(selectedObject.messageOwner.media.user_id);
						if (user != null && user.id != fragment.getUserConfig().getClientUserId() && fragment.getContactsController().contactsDict.get(user.id) == null) {
							options.add(ChatMessagePopupMenu.Option.ADD_CONTACT);
						}
						if (!TextUtils.isEmpty(selectedObject.messageOwner.media.phone_number)) {
							options.add(ChatMessagePopupMenu.Option.COPY_NUMBER);
							options.add(ChatMessagePopupMenu.Option.CALL_NUMBER);
						}
					}
					options.add(ChatMessagePopupMenu.Option.DELETE);
				}
			}
		}
		return !options.isEmpty();
	}

	public void show(View v, float x, float y){
		Theme.ResourcesProvider themeDelegate=fragment.getResourceProvider();
		SizeNotifierFrameLayout contentView=(SizeNotifierFrameLayout)fragment.getFragmentView();
		RecyclerListView chatListView=fragment.getChatListView();
		View pagedownButton=fragment.getPagedownButton();
		View mentiondownButton=fragment.getMentiondownButton();

		windowView=new KeyInterceptingFrameLayout(getParentActivity());
		windowView.setFitsSystemWindows(true);
		windowView.setClipToPadding(false);
		menuWrapper=new LinearLayout(getParentActivity());
		menuWrapper.setOrientation(LinearLayout.VERTICAL);
		FrameLayout menuView=new FrameLayout(getParentActivity());
		ScrollView menuScroller=new ScrollView(getParentActivity());
		LinearLayout menuContent=new LinearLayout(getParentActivity());
		menuContent.setOrientation(LinearLayout.VERTICAL);

		FrameLayout clippingView;
		if(Build.VERSION.SDK_INT<Build.VERSION_CODES.LOLLIPOP){
			clippingView=new RoundedFrameLayout(getParentActivity());
		}else{
			clippingView=new FrameLayout(getParentActivity());
			clippingView.setOutlineProvider(new ViewOutlineProvider(){
				@Override
				public void getOutline(View view, Outline outline){
					outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(6));
				}
			});
			clippingView.setClipToOutline(true);
		}
		menuScroller.addView(menuContent);
		clippingView.addView(menuScroller);
		menuView.addView(clippingView);
		menuWrapper.addView(menuView, LayoutHelper.createLinear(220+10, LayoutHelper.WRAP_CONTENT));
		windowView.addView(menuWrapper, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

		Rect backgroundPaddings=new Rect(AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(5));
		menuView.setBackground(new PopupMenuShadowDrawable(themeDelegate));

		scrimPopupWindowItems = new ArrayList<>();
		for (Option option:options) {
			ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), false, false, themeDelegate);
			cell.setMinimumWidth(AndroidUtilities.dp(200));
			cell.setTextAndIcon(getOptionTitle(option), getOptionIcon(option));
			if (option == ChatMessagePopupMenu.Option.DELETE && selectedObject.messageOwner.ttl_period != 0) {
				menuDeleteItem = cell;
				updateDeleteItemRunnable.run();
				cell.setSubtextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText6));
			}
			scrimPopupWindowItems.add(cell);
			menuContent.addView(cell);
			cell.setOnClickListener(v1 -> {
				if (selectedObject == null) {
					return;
				}
				listener.onOptionSelected(option);
				fragment.setScrimView(null);
				contentView.invalidate();
				chatListView.invalidate();
				dismiss();
			});
		}

		if (selectedObject.isSponsored()) {
			ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), true, true, themeDelegate);
			cell.setTextAndIcon(LocaleController.getString("SponsoredMessageInfo", R.string.SponsoredMessageInfo), R.drawable.menu_info);
			cell.setItemHeight(56);
			cell.setTag(R.id.width_tag, 240);
			cell.setMultiline();
			scrimPopupWindowItems.add(cell);
			cell.setOnClickListener(v1 -> {
				if (fragment.getFragmentView() == null || getParentActivity() == null) {
					return;
				}
				BottomSheet.Builder builder = new BottomSheet.Builder(fragment.getFragmentView().getContext());
				builder.setCustomView(new SponsoredMessageInfoView(getParentActivity(), themeDelegate));
				builder.show();
			});

			FrameLayout sponsoredView=new FrameLayout(getParentActivity());
			sponsoredView.addView(cell);
			sponsoredView.setBackground(new PopupMenuShadowDrawable(themeDelegate));
			menuWrapper.addView(sponsoredView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 0, -4, 0, 0));
		}

		boolean showMessageSeen = fragment.getCurrentChat() != null && selectedObject.isOutOwner() && selectedObject.isSent() && !selectedObject.isEditing() &&
			!selectedObject.isSending() && !selectedObject.isSendError() && !selectedObject.isContentUnread() && !selectedObject.isUnread() &&
			(ConnectionsManager.getInstance(fragment.getCurrentAccount()).getCurrentTime() - selectedObject.messageOwner.date < 7 * 86400)  &&
			(ChatObject.isMegagroup(fragment.getCurrentChat()) || !ChatObject.isChannel(fragment.getCurrentChat())) && fragment.getCurrentChatInfo() != null && fragment.getCurrentChatInfo().participants_count < 50
			&& !(selectedObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest);

		boolean showReactionList=selectedObject.hasReactions() && fragment.getCurrentChat()!=null && !ChatObject.isChannelAndNotMegaGroup(fragment.getCurrentChat()) &&
				!(fragment.getCurrentChatInfo().linked_chat_id!=0 && selectedObject.messageOwner.from_id.channel_id==fragment.getCurrentChatInfo().linked_chat_id);

		if (showMessageSeen || showReactionList) {
			View divider=new View(getParentActivity());
			divider.setBackground(new LayerDrawable(new Drawable[]{
					new ColorDrawable(themeDelegate.getColor(Theme.key_windowBackgroundGray)),
					Theme.getThemedDrawable(getParentActivity(), R.drawable.greydivider, themeDelegate.getColor(Theme.key_windowBackgroundGrayShadow))
			}));
			menuContent.addView(divider, 0, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
			messageSeenView = new MessageSeenView(contentView.getContext(), fragment.getCurrentAccount(), selectedObject, fragment.getCurrentChat(), showMessageSeen, showReactionList);
			menuContent.addView(messageSeenView, 0, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));

			messageSeenView.setOnClickListener(this::onMessageSeenClick);
		}

		menuWrapper.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));

		int popupX = v.getLeft() + (int) x - menuWrapper.getMeasuredWidth() + backgroundPaddings.left - AndroidUtilities.dp(28);
		if (popupX < AndroidUtilities.dp(6)) {
			popupX = AndroidUtilities.dp(6);
		} else if (popupX > chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - menuWrapper.getMeasuredWidth()) {
			popupX = chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - menuWrapper.getMeasuredWidth();
		}
		if (AndroidUtilities.isTablet()) {
			int[] location = new int[2];
			contentView.getLocationInWindow(location);
			popupX += location[0];
		}
		int totalHeight = contentView.getHeight()-AndroidUtilities.statusBarHeight;
		int height = menuWrapper.getMeasuredHeight();
		int keyboardHeight = contentView.measureKeyboardHeight();
		if (keyboardHeight > AndroidUtilities.dp(20)) {
			totalHeight += keyboardHeight;
		}
		int popupY;
		int minY=fragment.isInBubbleMode() ? 0 : AndroidUtilities.statusBarHeight;
		if (height < totalHeight) {
			popupY = (int) (chatListView.getY() + v.getTop() + y);
			if (height - backgroundPaddings.top - backgroundPaddings.bottom > AndroidUtilities.dp(240)) {
				popupY += AndroidUtilities.dp(240) - height;
			}
			if (popupY < chatListView.getY() + AndroidUtilities.dp(24)) {
				popupY = (int) (chatListView.getY() + AndroidUtilities.dp(24));
			} else if (popupY > totalHeight - height - AndroidUtilities.dp(8)) {
				popupY = totalHeight - height - AndroidUtilities.dp(8);
			}
		} else {
			popupY = minY;
		}
		if(popupY<minY)
			popupY=minY;

		menuWrapper.setTranslationX(popupX);
		menuWrapper.setTranslationY(popupY-AndroidUtilities.statusBarHeight);
		WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
		lp.width=lp.height=WindowManager.LayoutParams.MATCH_PARENT;
		lp.type=WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
		lp.flags=WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
		lp.format=PixelFormat.TRANSLUCENT;
		lp.token=chatListView.getWindowToken();
		wm.addView(windowView, lp);
		isShowing=true;

		chatListView.stopScroll();
		((GridLayoutManagerFixed)chatListView.getLayoutManager()).setCanScrollVertically(false);
		fragment.setScrimView(v);
		if (v instanceof ChatMessageCell) {
			ChatMessageCell cell = (ChatMessageCell) v;
			cell.setInvalidatesParent(true);
			fragment.restartSticker(cell);
		}
		contentView.invalidate();
		chatListView.invalidate();
		if (scrimAnimatorSet != null) {
			scrimAnimatorSet.cancel();
		}
		scrimAnimatorSet = new AnimatorSet();
		ArrayList<Animator> animators = new ArrayList<>();
		animators.add(ObjectAnimator.ofInt(fragment.getScrimPaint(), AnimationProperties.PAINT_ALPHA, 0, 50));
		if (pagedownButton.getTag() != null) {
			animators.add(ObjectAnimator.ofFloat(pagedownButton, View.ALPHA, 0));
		}
		if (mentiondownButton.getTag() != null) {
			animators.add(ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 0));
		}
		scrimAnimatorSet.playTogether(animators);
		scrimAnimatorSet.setDuration(150);
		scrimAnimatorSet.start();
		fragment.hideHints(false);
		UndoView topUndoView=fragment.getTopUndoView();
		if (topUndoView != null) {
			topUndoView.hide(true, 1);
		}
		UndoView undoView=fragment.getUndoView();
		if (undoView != null) {
			undoView.hide(true, 1);
		}
		ChatActivityEnterView chatActivityEnterView=fragment.getChatActivityEnterView();
		if (chatActivityEnterView != null) {
			chatActivityEnterView.getEditField().setAllowDrawCursor(false);
		}

		menuWrapper.setScaleX(.5f);
		menuWrapper.setScaleY(.5f);
		menuWrapper.setAlpha(0f);
		SpringAnimation anim=new SpringAnimation(menuWrapper, new FloatPropertyCompat<Object>(""){
			@Override
			public float getValue(Object object){
				return 0;
			}

			@Override
			public void setValue(Object object, float value){
				menuWrapper.setScaleX(value/2f+.5f);
				menuWrapper.setScaleY(value/2f+.5f);
				menuWrapper.setAlpha(Math.min(1f, value));
			}
		}, 1f);
		anim.setSpring(new SpringForce().setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY).setStiffness(500f).setFinalPosition(1f));
		anim.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
		anim.addEndListener(new DynamicAnimation.OnAnimationEndListener(){
			@Override
			public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity){
				showAnim=null;
			}
		});
		showAnim=anim;
		anim.start();
	}

	public void setPauseNotifications(boolean p){
	}

	public void dismiss(){
		if(isShowing){
			isShowing=false;
			if(showAnim!=null){
				showAnim.cancel();
			}
			menuWrapper.animate().alpha(0).translationYBy(AndroidUtilities.dp(-15)).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).withEndAction(()->{
				wm.removeView(windowView);
			}).start();
			afterDismiss();
		}
	}

	private void afterDismiss(){
		SizeNotifierFrameLayout contentView=(SizeNotifierFrameLayout)fragment.getFragmentView();
		RecyclerListView chatListView=fragment.getChatListView();
		View pagedownButton=fragment.getPagedownButton();
		View mentiondownButton=fragment.getMentiondownButton();

		scrimPopupWindow = null;
		menuDeleteItem = null;
		scrimPopupWindowItems = null;
		if (scrimAnimatorSet != null) {
			scrimAnimatorSet.cancel();
			scrimAnimatorSet = null;
		}
		View scrimView=fragment.getScrimView();
		if (scrimView instanceof ChatMessageCell) {
			ChatMessageCell cell = (ChatMessageCell) scrimView;
			cell.setInvalidatesParent(false);
		}
		((GridLayoutManagerFixed)chatListView.getLayoutManager()).setCanScrollVertically(true);
		scrimAnimatorSet = new AnimatorSet();
		ArrayList<Animator> animators = new ArrayList<>();
		animators.add(ObjectAnimator.ofInt(fragment.getScrimPaint(), AnimationProperties.PAINT_ALPHA, 0));
		if (pagedownButton.getTag() != null) {
			animators.add(ObjectAnimator.ofFloat(pagedownButton, View.ALPHA, 1.0f));
		}
		if (mentiondownButton.getTag() != null) {
			animators.add(ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 1.0f));
		}
		scrimAnimatorSet.playTogether(animators);
		scrimAnimatorSet.setDuration(220);
		scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				fragment.setScrimView(null);
				contentView.invalidate();
				chatListView.invalidate();
			}
		});
		scrimAnimatorSet.start();
		ChatActivityEnterView chatActivityEnterView=fragment.getChatActivityEnterView();
		if (chatActivityEnterView != null) {
			chatActivityEnterView.getEditField().setAllowDrawCursor(true);
		}
		if (mesageSeenUsersPopupWindow != null) {
			mesageSeenUsersPopupWindow.dismiss();
		}
		listener.onDismissed();
	}

	public boolean isShowing(){
		return isShowing;
	}

	private boolean onViewKeyEvent(View v, KeyEvent ev){
		if(ev.getKeyCode()==KeyEvent.KEYCODE_BACK){
			if(ev.getAction()==KeyEvent.ACTION_DOWN && isShowing){
				dismiss();
			}
			return true;
		}
		return false;
	}

	private void onMessageSeenClick(View v){
		Theme.ResourcesProvider themeDelegate=fragment.getResourceProvider();
		SizeNotifierFrameLayout contentView=(SizeNotifierFrameLayout)fragment.getFragmentView();
		Rect rect = new Rect();
		if (scrimPopupWindow == null || messageSeenView.users.isEmpty()) {
			return;
		}
		int totalHeight = contentView.getHeightWithKeyboard();
		int availableHeight = totalHeight - scrimPopupY - AndroidUtilities.dp(46 + 16);

		if (SharedConfig.messageSeenHintCount > 0 && contentView.getKeyboardHeight() < AndroidUtilities.dp(20)) {
			availableHeight -= AndroidUtilities.dp(52);
			Bulletin bulletin = BulletinFactory.of(fragment).createErrorBulletin(AndroidUtilities.replaceTags(LocaleController.getString("MessageSeenTooltipMessage", R.string.MessageSeenTooltipMessage)));
			bulletin.tag = 1;
			bulletin.setDuration(4000);
			bulletin.show();
			SharedConfig.updateMessageSeenHintCount(SharedConfig.messageSeenHintCount - 1);
		} else if (contentView.getKeyboardHeight() > AndroidUtilities.dp(20)) {
			availableHeight -=contentView.getKeyboardHeight() / 3f;
		}
		View previousPopupContentView = scrimPopupWindow.getContentView();

		ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), true, true, themeDelegate);
		cell.setItemHeight(44);
		cell.setTextAndIcon(LocaleController.getString("Back", R.string.Back), R.drawable.msg_arrow_back);
		cell.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), 0, LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0);

		Drawable shadowDrawable2 = ContextCompat.getDrawable(contentView.getContext(), R.drawable.popup_fixed_alert).mutate();
		shadowDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
		FrameLayout backContainer = new FrameLayout(contentView.getContext());
		backContainer.setBackground(shadowDrawable2);

		LinearLayout linearLayout = new LinearLayout(contentView.getContext()) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(260), MeasureSpec.AT_MOST), heightMeasureSpec);
				setPivotX(getMeasuredWidth() - AndroidUtilities.dp(8));
				setPivotY(AndroidUtilities.dp(8));
			}

			@Override
			public boolean dispatchKeyEvent(KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
					if (mesageSeenUsersPopupWindow != null) {
						mesageSeenUsersPopupWindow.dismiss();
					}
				}
				return super.dispatchKeyEvent(event);
			}
		};
		linearLayout.setOnTouchListener(new View.OnTouchListener() {

			private int[] pos = new int[2];

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					if (mesageSeenUsersPopupWindow != null && mesageSeenUsersPopupWindow.isShowing()) {
						View contentView = mesageSeenUsersPopupWindow.getContentView();
						contentView.getLocationInWindow(pos);
						rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
						if (!rect.contains((int) event.getX(), (int) event.getY())) {
							mesageSeenUsersPopupWindow.dismiss();
						}
					}
				} else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
					if (mesageSeenUsersPopupWindow != null && mesageSeenUsersPopupWindow.isShowing()) {
						mesageSeenUsersPopupWindow.dismiss();
					}
				}
				return false;
			}
		});
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		RecyclerListView listView = messageSeenView.createListView();
		int listViewTotalHeight = AndroidUtilities.dp(8) + AndroidUtilities.dp(44) * listView.getAdapter().getItemCount() + AndroidUtilities.dp(16);

		backContainer.addView(cell);
		linearLayout.addView(backContainer);
		linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 320, 0, -8, 0, 0));

		if (listViewTotalHeight > availableHeight) {
			if (availableHeight > AndroidUtilities.dp(620)) {
				listView.getLayoutParams().height = AndroidUtilities.dp(620);
			} else {
				listView.getLayoutParams().height = availableHeight;
			}
		} else {
			listView.getLayoutParams().height = listViewTotalHeight;
		}

		Drawable shadowDrawable3 = ContextCompat.getDrawable(contentView.getContext(), R.drawable.popup_fixed_alert).mutate();
		shadowDrawable3.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
		listView.setBackground(shadowDrawable3);
		boolean[] backButtonPressed = new boolean[1];

		mesageSeenUsersPopupWindow = new ActionBarPopupWindow(linearLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
			@Override
			public void dismiss(boolean animated) {
				super.dismiss(animated);
				if (backButtonPressed[0]) {
					linearLayout.animate().alpha(0).scaleX(0).scaleY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(350);
					previousPopupContentView.animate().alpha(1f).scaleX(1).scaleY(1).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(350);
				} else {
					if (scrimPopupWindow != null) {
						scrimPopupWindow.dismiss();

						contentView.invalidate();
						fragment.getChatListView().invalidate();
					}
				}
				if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().tag == 1) {
					Bulletin.getVisibleBulletin().hide();
				}
				mesageSeenUsersPopupWindow = null;
			}
		};
		mesageSeenUsersPopupWindow.setOutsideTouchable(true);
		mesageSeenUsersPopupWindow.setClippingEnabled(true);
		mesageSeenUsersPopupWindow.setFocusable(true);
		mesageSeenUsersPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
		mesageSeenUsersPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
		mesageSeenUsersPopupWindow.getContentView().setFocusableInTouchMode(true);

		mesageSeenUsersPopupWindow.showAtLocation(fragment.getChatListView(), Gravity.LEFT | Gravity.TOP, scrimPopupX, scrimPopupY);
		previousPopupContentView.setPivotX(AndroidUtilities.dp(8));
		previousPopupContentView.setPivotY(AndroidUtilities.dp(8));
		previousPopupContentView.animate().alpha(0).scaleX(0f).scaleY(0f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(350);

		linearLayout.setAlpha(0f);
		linearLayout.setScaleX(0f);
		linearLayout.setScaleY(0f);
		linearLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(350);

		backContainer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mesageSeenUsersPopupWindow != null) {
					mesageSeenUsersPopupWindow.setEmptyOutAnimation(250);
					backButtonPressed[0] = true;
					mesageSeenUsersPopupWindow.dismiss(true);
				}
			}
		});

		listView.setOnItemClickListener((view1, position) -> {
			TLRPC.User user = messageSeenView.users.get(position);
			if (user == null) {
				return;
			}
			Bundle args = new Bundle();
			args.putLong("user_id", user.id);
			ProfileActivity fragment = new ProfileActivity(args);
			ChatMessagePopupMenu.this.fragment.presentFragment(fragment);
			if (mesageSeenUsersPopupWindow != null) {
				mesageSeenUsersPopupWindow.dismiss();
			}
		});
	}

	private Activity getParentActivity(){
		return fragment.getParentActivity();
	}

	private int getThemedColor(String key){
		return fragment.getThemedColor(key);
	}

	@DrawableRes
	private int getOptionIcon(Option option){
		return switch(option){
			case RETRY -> R.drawable.msg_retry;
			case DELETE -> selectedObject.messageOwner.ttl_period != 0 ? R.drawable.msg_delete_auto : R.drawable.msg_delete;
			case FORWARD -> R.drawable.msg_forward;
			case COPY, COPY_NUMBER -> R.drawable.msg_copy;
			case SAVE_VIDEO_TO_GALLERY, SAVE_IMG_TO_GALLERY -> R.drawable.msg_gallery;
			case APPLY_LANG_OR_THEME -> selectedObject.type==5 ? R.drawable.msg_language : R.drawable.msg_theme;
			case SYS_SHARE -> R.drawable.msg_shareout;
			case REPLY -> R.drawable.msg_reply;
			case ADD_STICKERS -> R.drawable.msg_sticker;
			case SAVE_DOWNLOAD -> R.drawable.msg_download;
			case SAVE_GIF -> R.drawable.msg_gif;
			case EDIT -> R.drawable.msg_edit;
			case PIN -> R.drawable.msg_pin;
			case UNPIN -> R.drawable.msg_unpin;
			case ADD_CONTACT -> R.drawable.msg_addcontact;
			case CALL_NUMBER, CALL_VOIP -> R.drawable.msg_callback;
			case RATE_CALL, STICKER_ADD_FAVE -> R.drawable.msg_fave;
			case STICKER_REMOVE_FAVE -> R.drawable.msg_unfave;
			case COPY_LINK -> R.drawable.msg_link;
			case REPORT -> UserObject.isReplyUser(fragment.getCurrentUser()) ? R.drawable.msg_block2 : R.drawable.msg_report;
			case CANCEL_SENDING -> R.drawable.msg_delete;
			case POLL_UNVOTE -> R.drawable.msg_unvote;
			case POLL_STOP -> R.drawable.msg_pollstop;
			case VIEW_THREAD -> R.drawable.msg_viewreplies;
			case VIEW_STATS -> R.drawable.msg_stats;
			case SCHED_SEND_NOW -> R.drawable.outline_send;
			case SCHED_RESCHEDULE -> R.drawable.msg_schedule;
		};
	}

	private CharSequence getOptionTitle(Option option){
		return switch(option){
			case RETRY -> LocaleController.getString("Retry", R.string.Retry);
			case DELETE -> LocaleController.getString("Delete", R.string.Delete);
			case FORWARD -> LocaleController.getString("Forward", R.string.Forward);
			case COPY, COPY_NUMBER -> LocaleController.getString("Copy", R.string.Copy);
			case SAVE_VIDEO_TO_GALLERY, SAVE_IMG_TO_GALLERY -> LocaleController.getString("SaveToGallery", R.string.SaveToGallery);
			case APPLY_LANG_OR_THEME -> type==5 ? LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile) : LocaleController.getString("ApplyThemeFile", R.string.ApplyThemeFile);
			case SYS_SHARE -> LocaleController.getString("ShareFile", R.string.ShareFile);
			case REPLY -> LocaleController.getString("Reply", R.string.Reply);
			case ADD_STICKERS -> selectedObject.isMask() ? LocaleController.getString("AddToMasks", R.string.AddToMasks) : LocaleController.getString("AddToStickers", R.string.AddToStickers);
			case SAVE_DOWNLOAD -> {
				if(selectedObject.isMusic())
					yield LocaleController.getString("SaveToMusic", R.string.SaveToMusic);
				else //if(selectedObject.isDocument())
					yield LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads);
//				else
//					yield null;
			}
			case SAVE_GIF -> LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs);
			case EDIT -> LocaleController.getString("Edit", R.string.Edit);
			case PIN -> LocaleController.getString("PinMessage", R.string.PinMessage);
			case UNPIN -> LocaleController.getString("UnpinMessage", R.string.UnpinMessage);
			case ADD_CONTACT -> LocaleController.getString("AddContactTitle", R.string.AddContactTitle);
			case CALL_NUMBER -> LocaleController.getString("Call", R.string.Call);
			case CALL_VOIP -> {
				TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) selectedObject.messageOwner.action;
				yield (call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) && !selectedObject.isOutOwner() ? LocaleController.getString("CallBack", R.string.CallBack) : LocaleController.getString("CallAgain", R.string.CallAgain);
			}
			case RATE_CALL -> LocaleController.getString("CallMessageReportProblem", R.string.CallMessageReportProblem);
			case STICKER_ADD_FAVE -> LocaleController.getString("AddToFavorites", R.string.AddToFavorites);
			case STICKER_REMOVE_FAVE -> LocaleController.getString("DeleteFromFavorites", R.string.DeleteFromFavorites);
			case COPY_LINK -> LocaleController.getString("CopyLink", R.string.CopyLink);
			case REPORT -> UserObject.isReplyUser(fragment.getCurrentUser()) ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("ReportChat", R.string.ReportChat);
			case CANCEL_SENDING -> LocaleController.getString("CancelSending", R.string.CancelSending);
			case POLL_UNVOTE -> LocaleController.getString("Unvote", R.string.Unvote);
			case POLL_STOP -> selectedObject.isQuiz() ? LocaleController.getString("StopQuiz", R.string.StopQuiz) : LocaleController.getString("StopPoll", R.string.StopPoll);
			case VIEW_THREAD -> selectedObject.hasReplies() ? LocaleController.formatPluralString("ViewReplies", selectedObject.getRepliesCount()) : LocaleController.getString("ViewThread", R.string.ViewThread);
			case VIEW_STATS -> LocaleController.getString("ViewStats", R.string.ViewStats);
			case SCHED_SEND_NOW -> LocaleController.getString("MessageScheduleSend", R.string.MessageScheduleSend);
			case SCHED_RESCHEDULE -> LocaleController.getString("MessageScheduleEditTime", R.string.MessageScheduleEditTime);
		};
	}

	public void getThemeDescriptions(List<ThemeDescription> themeDescriptions){

	}

	public void updateThemeColors(){
		if (scrimPopupWindowItems != null) {
			for (ActionBarMenuSubItem item:scrimPopupWindowItems) {
				item.setColors(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon));
				item.setSelectorColor(getThemedColor(Theme.key_dialogButtonSelector));
			}
		}
		if (scrimPopupWindow != null) {
			final View contentView = scrimPopupWindow.getContentView();
			contentView.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
			contentView.invalidate();
		}
	}

	public enum Option{
		RETRY,
		DELETE,
		FORWARD,
		COPY,
		SAVE_VIDEO_TO_GALLERY,
		APPLY_LANG_OR_THEME,
		SYS_SHARE,
		SAVE_IMG_TO_GALLERY,
		REPLY,
		ADD_STICKERS,
		SAVE_DOWNLOAD,
		SAVE_GIF,
		EDIT,
		PIN,
		UNPIN,
		ADD_CONTACT,
		COPY_NUMBER,
		CALL_NUMBER,
		CALL_VOIP,
		RATE_CALL,
		STICKER_ADD_FAVE,
		STICKER_REMOVE_FAVE,
		COPY_LINK,
		REPORT,
		CANCEL_SENDING,
		POLL_UNVOTE,
		POLL_STOP,
		VIEW_THREAD,
		VIEW_STATS,

		// scheduled message actions
		SCHED_SEND_NOW,
		SCHED_RESCHEDULE,
	}

	public interface PopupMenuListener{
		void onOptionSelected(Option option);
		void onDismissed();
	}

	private class KeyInterceptingFrameLayout extends FrameLayout{
		public KeyInterceptingFrameLayout(@NonNull Context context){
			super(context);
		}

		@Override
		public boolean dispatchKeyEvent(KeyEvent event){
			return onViewKeyEvent(this, event);
		}

		@Override
		public boolean onTouchEvent(MotionEvent ev){
			if(ev.getAction()==MotionEvent.ACTION_DOWN){
				if(isShowing)
					dismiss();
			}
			return true;
		}
	}
}
