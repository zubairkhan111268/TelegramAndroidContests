package org.telegram.ui.Components.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
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
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
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
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.MessageSeenView;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SponsoredMessageInfoView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChatMessagePopupMenu implements ReactionChooserView.OnReactionClickListener{
	private static final String TAG="ChatMessagePopupMenu";

	private MessageObject selectedObject;
	private MessageObject.GroupedMessages selectedObjectGroup;
	private ChatActivity fragment;
	private int type;
	private boolean single;
	private boolean allowChatActions, allowUnpin, allowPin, allowEdit;

	private AnimatorSet scrimAnimatorSet;
	private FrameLayout windowView;
	private LinearLayout menuWrapper;
	private ArrayList<ActionBarMenuSubItem> scrimPopupWindowItems;
	private PopupMenuListener listener;
	private WindowManager wm;
	private boolean isShowing;
	private SpringAnimation showAnim;
	private MessageSeenView messageSeenView;
	private LayoutIgnoringFrameLayout clippingView;
	private FrameLayout menuView;
	private ScrollView menuScroller;
	private LinearLayout menuContent;
	private LinearLayout seenContent;
	private View scrim, whiteBg;
	private ViewPagerFixed reactionsPager;
	private float touchslop;
	private float swipeBackDownX;
	private int swipeBackPointerID;
	private boolean swipingBack;
	private int initialMenuViewBottom, initialClippingViewBottom;
	private boolean animatingSeenView;
	private VelocityTracker swipeVelocityTracker;
	private HorizontalScrollView tabScroller;
	private LinearLayout tabBar;
	private int currentTab;
	private boolean scrollingPagerFromTab;
	private ReactionChooserView reactionChooserView;

	private int maxListHeight;

	private final int classGuid=ConnectionsManager.generateClassGuid();
	private HashMap<String, ReactionTypeLoadState> reactionsByType=new HashMap<>();
	private ReactionTypeLoadState allReactions=new ReactionTypeLoadState();
	private ArrayList<ChatMessagePopupMenu.Option> options = new ArrayList<>();
	private boolean showTabs;

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
		touchslop=ViewConfiguration.get(getParentActivity()).getScaledTouchSlop();
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

	private View makeDivider(){
		Theme.ResourcesProvider themeDelegate=fragment.getResourceProvider();
		View divider=new View(getParentActivity());
		divider.setBackground(new LayerDrawable(new Drawable[]{
				new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)),
				Theme.getThemedDrawable(getParentActivity(), R.drawable.greydivider, getThemedColor(Theme.key_windowBackgroundGrayShadow))
		}));
		return divider;
	}

	public void show(View v, float x, float y){
		Theme.ResourcesProvider themeDelegate=fragment.getResourceProvider();
		SizeNotifierFrameLayout contentView=(SizeNotifierFrameLayout)fragment.getFragmentView();
		RecyclerListView chatListView=fragment.getChatListView();
		View pagedownButton=fragment.getPagedownButton();
		View mentiondownButton=fragment.getMentiondownButton();

		windowView=new KeyInterceptingFrameLayout(getParentActivity());
		windowView.setFitsSystemWindows(true);
		windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		windowView.setClipToPadding(false);
		menuWrapper=new LinearLayout(getParentActivity());
		menuWrapper.setOrientation(LinearLayout.VERTICAL);
		menuView=new FrameLayout(getParentActivity());
		menuScroller=new ScrollView(getParentActivity());
		menuContent=new LinearLayout(getParentActivity());
		menuContent.setOrientation(LinearLayout.VERTICAL);

		if(Build.VERSION.SDK_INT<Build.VERSION_CODES.LOLLIPOP){
			clippingView=new RoundedFrameLayout(getParentActivity());
		}else{
			clippingView=new LayoutIgnoringFrameLayout(getParentActivity());
			clippingView.setOutlineProvider(new ViewOutlineProvider(){
				@Override
				public void getOutline(View view, Outline outline){
					outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(6));
				}
			});
			clippingView.setClipToOutline(true);
		}
		menuScroller.addView(menuContent);
		menuContent.setPivotX(0);
		menuContent.setPivotY(0);
		clippingView.addView(menuScroller, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
		menuView.addView(clippingView);

		TLRPC.ChatFull chatFull=fragment.getCurrentChatInfo();
		if(((fragment.getCurrentUser()!=null && fragment.getCurrentEncryptedChat()==null) || (chatFull!=null && chatFull.available_reactions!=null && !chatFull.available_reactions.isEmpty())) &&
				selectedObject.messageOwner.action==null && !selectedObject.scheduled && !selectedObject.isSponsored()){
			List<TLRPC.TL_availableReaction> availableReactions;
			if(fragment.getCurrentUser()!=null)
				availableReactions=fragment.getMediaDataController().getAvailableReactions();
			else
				availableReactions=chatFull.available_reactions.stream().map(fragment.getMediaDataController()::getReaction).collect(Collectors.toList());
			reactionChooserView=new ReactionChooserView(getParentActivity(), themeDelegate, availableReactions);
			reactionChooserView.setOnReactionClickListener(this);
			FrameLayout reactionsWrapper=new FrameLayout(getParentActivity());

			menuWrapper.addView(reactionsWrapper, LayoutHelper.createLinear(276, 71, 0, 0, 0, -17));
			reactionsWrapper.addView(reactionChooserView, LayoutHelper.createFrame(Math.min(276, Math.max(110, 10+9+39*availableReactions.size())), 71, Gravity.RIGHT));
		}

		menuWrapper.addView(menuView, LayoutHelper.createLinear(220+10, LayoutHelper.WRAP_CONTENT));
		windowView.addView(menuWrapper, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

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
			menuContent.addView(makeDivider(), 0, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
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
		lp.flags=WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)
			lp.flags|=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
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
		menuWrapper.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				menuWrapper.getViewTreeObserver().removeOnPreDrawListener(this);
				int reactionChooserTargetLeft=reactionChooserView!=null ? reactionChooserView.getLeft() : 0;
				int reactionChooserStartLeft=reactionChooserView!=null ? (reactionChooserView.getRight()-AndroidUtilities.dp(54)) : 0;
				if(reactionChooserView!=null){
					reactionChooserView.setLeft(reactionChooserStartLeft);
					reactionChooserView.setIgnoreLayout(true);
				}
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
						if(reactionChooserView!=null){
							reactionChooserView.setLeft(Math.round(reactionChooserTargetLeft+(reactionChooserStartLeft-reactionChooserTargetLeft)*(1f-value)));
						}
					}
				}, 1f);
				anim.setSpring(new SpringForce().setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY).setStiffness(500f).setFinalPosition(1f));
				anim.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
				anim.addEndListener(new DynamicAnimation.OnAnimationEndListener(){
					@Override
					public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity){
						showAnim=null;
						if(reactionChooserView!=null)
							reactionChooserView.setIgnoreLayout(false);
					}
				});
				showAnim=anim;
				anim.start();

				return true;
			}
		});
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
		fragment.getConnectionsManager().cancelRequestsForGuid(classGuid);
		SizeNotifierFrameLayout contentView=(SizeNotifierFrameLayout)fragment.getFragmentView();
		RecyclerListView chatListView=fragment.getChatListView();
		View pagedownButton=fragment.getPagedownButton();
		View mentiondownButton=fragment.getMentiondownButton();

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
		SizeNotifierFrameLayout contentView=(SizeNotifierFrameLayout)fragment.getFragmentView();

		if (messageSeenView.users.isEmpty()) {
			return;
		}

		if(messageSeenView.users.size()==1){
			openUserProfile(messageSeenView.users.get(0).id);
			dismiss();
			return;
		}

		if(!selectedObject.hasReactions()){ // only "seen"
			allReactions.total=messageSeenView.users.size();
			for(TLRPC.User user:messageSeenView.users){
				TLRPC.TL_messageUserReaction reaction=new TLRPC.TL_messageUserReaction();
				reaction.user_id=user.id;
				allReactions.items.add(reaction);
			}
		}else if(allReactions.items.isEmpty()){
			allReactions.total=selectedObject.getAllReactionsCount()+messageSeenView.seenUserIDs.size();
			if(allReactions.total>10 && selectedObject.messageOwner.reactions.results.size()>1){
				showTabs=true;
				for(TLRPC.TL_reactionCount count:selectedObject.messageOwner.reactions.results){
					ReactionTypeLoadState state=new ReactionTypeLoadState();
					state.total=count.count;
					reactionsByType.put(count.reaction, state);
				}
			}
			loadReactions(null, 100);
		}

		prepareAndAddUserListLayout();

		if(!messageSeenView.seenUserIDs.isEmpty()){
			if (SharedConfig.messageSeenHintCount > 0 && contentView.getKeyboardHeight() < AndroidUtilities.dp(20)) {
				Bulletin bulletin = BulletinFactory.of(fragment).createErrorBulletin(AndroidUtilities.replaceTags(LocaleController.getString("MessageSeenTooltipMessage", R.string.MessageSeenTooltipMessage)));
				bulletin.tag = 1;
				bulletin.setDuration(4000);
				bulletin.show();
				SharedConfig.updateMessageSeenHintCount(SharedConfig.messageSeenHintCount - 1);
			}
		}
	}

	private void prepareAndAddUserListLayout(){
		Theme.ResourcesProvider themeDelegate=fragment.getResourceProvider();

		scrim=new View(getParentActivity());
		scrim.setBackgroundColor(0x33000000);
		scrim.setPivotY(0);
		clippingView.addView(scrim, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 1));

		whiteBg=new View(getParentActivity());
		whiteBg.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
		whiteBg.setPivotY(0);
		clippingView.addView(whiteBg, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 1));

		seenContent=new LinearLayout(getParentActivity());
		seenContent.setOrientation(LinearLayout.VERTICAL);
		seenContent.setMotionEventSplittingEnabled(false);

		ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), false, false, themeDelegate);
		cell.setItemHeight(44);
		cell.setTextAndIcon(LocaleController.getString("Back", R.string.Back), R.drawable.msg_arrow_back);
		cell.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(43), 0, LocaleController.isRTL ? AndroidUtilities.dp(43) : 0, 0);
		cell.setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), 0);
		seenContent.addView(cell);
		if(showTabs){
			createTabBar();
			seenContent.addView(tabScroller, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));
			View line=new View(getParentActivity());
			line.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
			line.setAlpha(.1f);
			int lineH=AndroidUtilities.dp(.66f);
			LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, lineH);
			lp.topMargin=-lineH;
			seenContent.addView(line, lp);
		}else{
			seenContent.addView(makeDivider(), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
		}

		int[] loc={0,0};
		clippingView.getLocationInWindow(loc);
		maxListHeight=Math.min(AndroidUtilities.dp(413), windowView.getHeight()-windowView.getPaddingBottom()-loc[1]-AndroidUtilities.dp(5+8));
		if(showTabs)
			maxListHeight-=AndroidUtilities.dp(44+40);
		else
			maxListHeight-=AndroidUtilities.dp(44+8);

		reactionsPager=new SwipeBackViewPager(getParentActivity());
		reactionsPager.setAdapter(new ReactionsViewPagerAdapter());
		seenContent.addView(reactionsPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

		cell.setOnClickListener(v->{
			startBackTransition(true);
		});

		clippingView.addView(seenContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
		clippingView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				clippingView.getViewTreeObserver().removeOnPreDrawListener(this);

				scrim.setScaleY(clippingView.getHeight());
				whiteBg.setScaleY(clippingView.getHeight());
				seenContent.setTranslationX(seenContent.getWidth());
				whiteBg.setTranslationX(seenContent.getWidth());
				scrim.setAlpha(0f);

				initialClippingViewBottom=clippingView.getBottom();
				initialMenuViewBottom=menuView.getBottom();
				animateSeenViewOpening(400, false);

				return true;
			}
		});
	}

	private void createTabBar(){
		tabScroller=new HorizontalScrollView(getParentActivity());
		tabScroller.setHorizontalScrollBarEnabled(false);
		tabBar=new LinearLayout(getParentActivity());
		tabScroller.addView(tabBar);
		tabBar.setOrientation(LinearLayout.HORIZONTAL);
		tabBar.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(4), AndroidUtilities.dp(7), AndroidUtilities.dp(10));
		MessageCellReactionButton allReactions=new MessageCellReactionButton(getParentActivity(), fragment.getResourceProvider());
		allReactions.setSelected(true, false);
		allReactions.setBackgroundType(true, false);
		allReactions.setForegroundColor(getThemedColor(Theme.key_chat_inPreviewInstantText));
		allReactions.setDrawableAndCount(Theme.getThemedDrawable(getParentActivity(), R.drawable.msg_reactions_filled, getThemedColor(Theme.key_chat_inPreviewInstantText)), selectedObject.getAllReactionsCount());
		allReactions.setTag(0);
		allReactions.setOnClickListener(this::onTabClick);
		tabBar.addView(allReactions, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26, 3, 0, 3, 0));
		int i=1;
		for(TLRPC.TL_reactionCount reaction:selectedObject.messageOwner.reactions.results){
			MessageCellReactionButton btn=new MessageCellReactionButton(getParentActivity(), fragment.getResourceProvider());
			btn.setBackgroundType(true, false);
			btn.setForegroundColor(getThemedColor(Theme.key_chat_inPreviewInstantText));
			btn.setReactions(reaction, null, false);
			btn.setTag(i);
			btn.setOnClickListener(this::onTabClick);
			tabBar.addView(btn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26, 3, 0, 3, 0));
			i++;
		}
	}

	private void startBackTransition(boolean animate){
		scrim.setVisibility(View.VISIBLE);
		whiteBg.setVisibility(View.VISIBLE);
		menuScroller.setVisibility(View.VISIBLE);
		clippingView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				clippingView.getViewTreeObserver().removeOnPreDrawListener(this);
				initialClippingViewBottom=clippingView.getBottom();
				initialMenuViewBottom=menuView.getBottom();
				int heightDiff=seenContent.getHeight()-menuScroller.getHeight();
				if(heightDiff>0){
					initialMenuViewBottom-=heightDiff;
					initialClippingViewBottom-=heightDiff;
				}
				menuView.setBottom(initialMenuViewBottom+heightDiff);
				clippingView.setBottom(initialClippingViewBottom+heightDiff);
				if(animate){
					animateSeenViewClosing(300);
				}
				return true;
			}
		});
	}

	private void endBackTransition(){
		clippingView.setIgnoreLayout(false);
		scrim.setAlpha(1f);
		seenContent.setTranslationX(0f);
		whiteBg.setTranslationX(0f);
		menuContent.setTranslationX(AndroidUtilities.dp(-50));
		menuContent.setScaleX(.9f);
		menuContent.setScaleY(.9f);
		// menuView.bottom & clippingView.bottom will get reset during the next layout pass
		scrim.setVisibility(View.GONE);
		whiteBg.setVisibility(View.GONE);
		menuScroller.setVisibility(View.GONE);
		if(reactionChooserView!=null)
			reactionChooserView.setVisibility(View.INVISIBLE);
	}

	private void animateSeenViewOpening(long duration, boolean reopening){
		if(animatingSeenView)
			return;
		int heightDiff=seenContent.getHeight()-menuScroller.getHeight();
		if(heightDiff>0 && !reopening){
			menuView.setBottom(initialMenuViewBottom=initialMenuViewBottom-heightDiff);
			clippingView.setBottom(initialClippingViewBottom=initialClippingViewBottom-heightDiff);
		}
		animatingSeenView=true;
		clippingView.setIgnoreLayout(true);

		AnimatorSet set=new AnimatorSet();
		ArrayList<Animator> anims=new ArrayList<>();
		anims.add(ObjectAnimator.ofFloat(scrim, View.ALPHA, 1f));
		anims.add(ObjectAnimator.ofFloat(seenContent, View.TRANSLATION_X, 0f));
		anims.add(ObjectAnimator.ofFloat(whiteBg, View.TRANSLATION_X, 0f));
		anims.add(ObjectAnimator.ofFloat(menuContent, View.TRANSLATION_X, AndroidUtilities.dp(-50)));
		anims.add(ObjectAnimator.ofFloat(menuContent, View.SCALE_X, .9f));
		anims.add(ObjectAnimator.ofFloat(menuContent, View.SCALE_Y, .9f));
		anims.add(ObjectAnimator.ofInt(menuView, "bottom", initialMenuViewBottom+heightDiff));
		anims.add(ObjectAnimator.ofInt(clippingView, "bottom", initialClippingViewBottom+heightDiff));
		for(Animator a:anims)
			a.setDuration(duration);
		if(reactionChooserView!=null)
			anims.add(ObjectAnimator.ofFloat(reactionChooserView, View.ALPHA, 0f).setDuration(150));
		set.playTogether(anims);
//		set.setDuration(duration);
		set.setInterpolator(new CubicBezierInterpolator(.09, .13, 0, 1));
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				animatingSeenView=false;
				endBackTransition();
			}

			@Override
			public void onAnimationStart(Animator animation){
			}
		});
		set.start();
	}

	private void animateSeenViewClosing(long duration){
		if(animatingSeenView)
			return;
		animatingSeenView=true;
		clippingView.setIgnoreLayout(true);
		if(reactionChooserView!=null)
			reactionChooserView.setVisibility(View.VISIBLE);
		AnimatorSet set=new AnimatorSet();
		ArrayList<Animator> anims=new ArrayList<>();
		anims.add(ObjectAnimator.ofFloat(scrim, View.ALPHA, 0f));
		anims.add(ObjectAnimator.ofFloat(seenContent, View.TRANSLATION_X, seenContent.getWidth()));
		anims.add(ObjectAnimator.ofFloat(whiteBg, View.TRANSLATION_X, seenContent.getWidth()));
		anims.add(ObjectAnimator.ofFloat(menuContent, View.TRANSLATION_X, 0f));
		anims.add(ObjectAnimator.ofFloat(menuContent, View.SCALE_X, 1f));
		anims.add(ObjectAnimator.ofFloat(menuContent, View.SCALE_Y, 1f));
		anims.add(ObjectAnimator.ofInt(menuView, "bottom", initialMenuViewBottom));
		anims.add(ObjectAnimator.ofInt(clippingView, "bottom", initialClippingViewBottom));
		if(reactionChooserView!=null){
			int reactionChooserTargetLeft=reactionChooserView.getLeft();
			int reactionChooserStartLeft=reactionChooserView.getRight()-AndroidUtilities.dp(54);
			anims.add(ObjectAnimator.ofInt(reactionChooserView, "left", reactionChooserStartLeft, reactionChooserTargetLeft));
			anims.add(ObjectAnimator.ofFloat(reactionChooserView, View.ALPHA, 1f));
			anims.add(ObjectAnimator.ofFloat(reactionChooserView, "pivotX", AndroidUtilities.dp(27), reactionChooserView.getWidth()-AndroidUtilities.dp(27)));
			anims.add(ObjectAnimator.ofFloat(reactionChooserView, View.SCALE_X, .5f, 1f));
			anims.add(ObjectAnimator.ofFloat(reactionChooserView, View.SCALE_Y, .5f, 1f));
		}
		set.playTogether(anims);
		set.setDuration(duration);
		set.setInterpolator(new CubicBezierInterpolator(.09, .13, 0, 1));
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				clippingView.setIgnoreLayout(false);
				clippingView.removeView(scrim);
				scrim=null;
				clippingView.removeView(whiteBg);
				whiteBg=null;
				clippingView.removeView(seenContent);
				seenContent=null;
				reactionsPager=null;
				animatingSeenView=false;
			}

			@Override
			public void onAnimationStart(Animator animation){
			}
		});
		set.start();
	}

	private void loadReactions(String type, int count){
		ReactionTypeLoadState state=type==null ? allReactions : reactionsByType.get(type);
		if(state.loading)
			return;
		state.loading=true;
		TLRPC.TL_messages_getMessageReactionsList req=new TLRPC.TL_messages_getMessageReactionsList();
		req.peer=MessagesController.getInputPeer(fragment.getCurrentChat());
		req.id=selectedObject.getId();
		if(type!=null){
			req.reaction=type;
			req.flags|=1;
		}
		if(state.offset!=null){
			req.offset=state.offset;
			req.flags|=2;
		}
		req.limit=count;
		int token=fragment.getConnectionsManager().sendRequest(req, (res, err)->{
			AndroidUtilities.runOnUIThread(()->{
				state.loading=false;
				if(res instanceof TLRPC.TL_messages_messageReactionsList){
					TLRPC.TL_messages_messageReactionsList reactions=(TLRPC.TL_messages_messageReactionsList) res;
					fragment.getMessagesController().putUsers(reactions.users, false);
					boolean first=false;
					if(state.offset==null){
						first=true;
						state.items.clear();
					}
					state.offset=reactions.next_offset;
					int prevCount=state.items.size();
					state.items.addAll(reactions.reactions);
					int addedCount=reactions.reactions.size();
					int removedCount=0;
					if(reactions.next_offset==null){
						state.fullyLoaded=true;
						if(type==null && !messageSeenView.seenUserIDs.isEmpty()){
							Set<Long> reactedUserIDs=reactions.reactions.stream().map(r->r.user_id).collect(Collectors.toSet());
							for(Long id:messageSeenView.seenUserIDs){
								if(reactedUserIDs.contains(id)){
									state.total--;
									removedCount++;
									continue;
								}
								TLRPC.TL_messageUserReaction reaction=new TLRPC.TL_messageUserReaction();
								reaction.user_id=id;
								allReactions.items.add(reaction);
								addedCount++;
							}
						}else{
							state.total=state.items.size();
						}
					}
					HashSet<String> affectedTypes=null;
					if(first && type==null && !reactionsByType.isEmpty()){
						affectedTypes=new HashSet<>();
						for(TLRPC.TL_messageUserReaction reaction:reactions.reactions){
							ReactionTypeLoadState typeState=reactionsByType.get(reaction.reaction);
							if(typeState!=null && typeState.offset==null){
								typeState.items.add(reaction);
								affectedTypes.add(reaction.reaction);
							}
						}
					}
					if(reactionsPager!=null){
						for(int i=0;i<reactionsPager.getChildCount();i++){
							RecyclerListView list=(RecyclerListView) reactionsPager.getChildAt(i);
							String adapterType=((ReactionsViewRecyclerAdapter)list.getAdapter()).type;
							if(Objects.equals(adapterType, type)){
								list.getAdapter().notifyItemRangeChanged(prevCount, addedCount);
								if(removedCount>0)
									list.getAdapter().notifyItemRangeRemoved(list.getAdapter().getItemCount()-removedCount, removedCount);
							}else if(affectedTypes!=null && adapterType!=null && affectedTypes.contains(adapterType)){
								list.getAdapter().notifyDataSetChanged();
							}
						}
					}
				}
			});
		});
		fragment.getConnectionsManager().bindRequestToGuid(token, classGuid);
	}

	private Activity getParentActivity(){
		return fragment.getParentActivity();
	}

	private int getThemedColor(String key){
		return fragment.getThemedColor(key);
	}

	private void openUserProfile(long id){
		Bundle args = new Bundle();
		args.putLong("user_id", id);
		ProfileActivity fragment = new ProfileActivity(args);
		ChatMessagePopupMenu.this.fragment.presentFragment(fragment);
	}

	private void onTabClick(View tab){
		int i=(Integer)tab.getTag();
		if(i==currentTab)
			return;
		scrollingPagerFromTab=true;
		reactionsPager.setPositionAnimated(i);
		setSelectedTab(i);
//		reactionsPager.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
//			@Override
//			public boolean onPreDraw(){
//				reactionsPager.getViewTreeObserver().removeOnPreDrawListener(this);
//				clippingView.setIgnoreLayout(true);
//				return true;
//			}
//		});
	}

	private void setSelectedTab(int tab){
		((MessageCellReactionButton)tabBar.getChildAt(currentTab)).setSelected(false, true);
		MessageCellReactionButton newTab=(MessageCellReactionButton)tabBar.getChildAt(tab);
		newTab.setSelected(true, true);
		currentTab=tab;
		if(newTab.getLeft()<tabScroller.getScrollX()){
			if(tab==0)
				tabScroller.smoothScrollTo(0, 0);
			else
				tabScroller.smoothScrollTo(newTab.getLeft()-AndroidUtilities.dp(6), 0);
		}else if(newTab.getRight()>tabScroller.getScrollX()+tabScroller.getWidth()){
			if(tab==tabBar.getChildCount())
				tabScroller.smoothScrollTo(tabBar.getWidth()-tabScroller.getWidth(), 0);
			else
				tabScroller.smoothScrollTo(newTab.getRight()-tabScroller.getWidth()+AndroidUtilities.dp(6), 0);
		}
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
		if(menuView!=null)
			menuView.invalidate();
		if(whiteBg!=null)
			whiteBg.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
	}

	@Override
	public void onReactionClick(String reaction, View view){
		listener.onReactionSelected(reaction, view);
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
		void onReactionSelected(String reaction, View button);
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
		public boolean dispatchTouchEvent(MotionEvent ev){
			if(animatingSeenView)
				return false;
			return super.dispatchTouchEvent(ev);
		}

		@Override
		public boolean onTouchEvent(MotionEvent ev){
			if(ev.getAction()==MotionEvent.ACTION_DOWN){
				if(isShowing)
					dismiss();
			}
			return true;
		}

		@Override
		public WindowInsets dispatchApplyWindowInsets(WindowInsets insets){
			if(Build.VERSION.SDK_INT<Build.VERSION_CODES.LOLLIPOP)
				return insets;
			setPadding(insets.getStableInsetLeft(), insets.getStableInsetTop(), insets.getStableInsetRight(), insets.getStableInsetBottom());
			return insets.consumeStableInsets();
		}
	}

	private class SwipeBackViewPager extends ViewPagerFixed{

		public SwipeBackViewPager(@NonNull Context context){
			super(context);
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent ev){
			// super calls onTouchEvent()
			return swipingBack || super.onInterceptTouchEvent(ev);
		}

		@Override
		public boolean onTouchEvent(MotionEvent ev){
			if(ev==null) // what the actual fuck?! called with null from super.requestDisallowInterceptTouchEvent
				return super.onTouchEvent(ev);
			boolean res=super.onTouchEvent(ev);
			if(!animatingSeenView && (!startedTracking || getScrollProgress()==0f) && getCurrentPosition()==0){
				if(ev.getAction()==MotionEvent.ACTION_DOWN){
					swipeBackPointerID=ev.getPointerId(0);
					swipeBackDownX=ev.getX();
				}else if(ev.getAction()==MotionEvent.ACTION_MOVE){
					if(ev.getPointerId(0)==swipeBackPointerID){
						if(!swipingBack){
							if(ev.getX()-swipeBackDownX>=touchslop){
								swipingBack=true;
								swipeBackDownX=ev.getX();
								startBackTransition(false);
								swipeVelocityTracker=VelocityTracker.obtain();
								swipeVelocityTracker.addMovement(ev);
								getParent().requestDisallowInterceptTouchEvent(true);
							}
						}else{
							swipeVelocityTracker.addMovement(ev);
							float progress=(ev.getX()+seenContent.getTranslationX()-swipeBackDownX)/seenContent.getWidth();
							progress=Math.max(0f, Math.min(1f, progress));
							if(progress==0f){
								endBackTransition();
								swipingBack=false;
								swipeVelocityTracker.recycle();
								swipeVelocityTracker=null;
								return res;
							}
							scrim.setAlpha(1f-progress);
							seenContent.setTranslationX(progress*seenContent.getWidth());
							whiteBg.setTranslationX(progress*seenContent.getWidth());
							menuContent.setTranslationX(AndroidUtilities.dp(-50)*(1f-progress));
							float scale=.9f+(.1f*progress);
							menuContent.setScaleX(scale);
							menuContent.setScaleY(scale);
							int heightDiff=seenContent.getHeight()-menuScroller.getHeight();
							menuView.setBottom(initialMenuViewBottom+Math.round(heightDiff*(1f-progress)));
							clippingView.setBottom(initialClippingViewBottom+Math.round(heightDiff*(1f-progress)));
						}
						return swipingBack || res;
					}
				}else if(ev.getAction()==MotionEvent.ACTION_UP || ev.getAction()==MotionEvent.ACTION_CANCEL){
					if(swipingBack){
						swipeVelocityTracker.computeCurrentVelocity(1000);
						float xv=swipeVelocityTracker.getXVelocity(), yv=swipeVelocityTracker.getYVelocity();
						swipeVelocityTracker.recycle();
						swipeVelocityTracker=null;
						if((xv>AndroidUtilities.dp(1000) && Math.abs(xv)>Math.abs(yv)) || (ev.getX()+seenContent.getTranslationX()-swipeBackDownX)>seenContent.getWidth()/3f){
							animateSeenViewClosing(200);
						}else{
							animateSeenViewOpening(250, true);
						}
					}
					swipingBack=false;
				}
			}
			return res;
		}

		@Override
		protected void onScrolled(float progress){
			super.onScrolled(progress);
			if(viewPages[1]!=null){
				int currentHeight=viewPages[0].getHeight();
				int newHeight=viewPages[1].getHeight();
				int heightDiff=newHeight-currentHeight;
				if(heightDiff!=0){
					clippingView.setBottom(clippingView.getTop()+getTop()+currentHeight+Math.round(heightDiff*progress));
					menuView.setBottom(menuView.getTop()+getTop()+currentHeight+Math.round(heightDiff*progress)+menuView.getPaddingBottom()+menuView.getPaddingTop());
				}
			}
			if(!scrollingPagerFromTab){
				if(progress>0.6f && currentTab!=nextPosition){
					setSelectedTab(nextPosition);
				}else if(progress<0.4f && currentTab!=currentPosition){
					setSelectedTab(currentPosition);
				}
			}
		}

		@Override
		protected void doneAnimating(){
			scrollingPagerFromTab=false;
		}
	}

	private class ReactionsViewPagerAdapter extends ViewPagerFixed.Adapter{

		@Override
		public int getItemCount(){
			return reactionsByType.size()+1;
		}

		@Override
		public View createView(int viewType){
			RecyclerListView list=new RecyclerListView(getParentActivity());
			ReactionsViewRecyclerAdapter adapter=new ReactionsViewRecyclerAdapter();
			list.setAdapter(adapter);
			list.setOnItemClickListener(adapter);
			list.setLayoutManager(new LinearLayoutManager(getParentActivity()));
			list.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200));
			return list;
		}

		@Override
		public void bindView(View view, int position, int viewType){
			RecyclerListView list=(RecyclerListView) view;
			ReactionsViewRecyclerAdapter adapter=(ReactionsViewRecyclerAdapter) list.getAdapter();
			if(position==0)
				adapter.setReactions(allReactions, null);
			else{
				String type=selectedObject.messageOwner.reactions.results.get(position-1).reaction;
				adapter.setReactions(reactionsByType.get(type), type);
			}
			list.getLayoutParams().height=Math.min(maxListHeight, AndroidUtilities.dp(adapter.getItemCount()*48));
		}
	}

	private class ReactionsViewRecyclerAdapter extends RecyclerListView.SelectionAdapter implements RecyclerListView.OnItemClickListener{
		private ReactionTypeLoadState reactions=null;
		private String type;

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return switch(viewType){
				case 0 -> new ReactionUserViewHolder(parent);
				case 1 -> new LoadingReactionViewHolder();
				default -> throw new IllegalArgumentException();
			};
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position){
			if(position<reactions.items.size()){
				((ReactionUserViewHolder) holder).bind(reactions.items.get(position));
			}else{
				((LoadingReactionViewHolder) holder).bind();
				if(!reactions.loading){
					loadReactions(type, type==null ? 100 : 50);
				}
			}
		}

		@Override
		public int getItemCount(){
			return reactions==null ? 0 : reactions.total;
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder){
			return holder instanceof ReactionUserViewHolder;
		}

		@Override
		public int getItemViewType(int position){
			return position>=reactions.items.size() ? 1 : 0;
		}

		@SuppressLint("NotifyDataSetChanged")
		public void setReactions(ReactionTypeLoadState list, String type){
			reactions=list;
			this.type=type;
			notifyDataSetChanged();
		}

		@Override
		public void onItemClick(View view, int position){
			openUserProfile(reactions.items.get(position).user_id);
			dismiss();
		}
	}

	private class ReactionUserViewHolder extends RecyclerView.ViewHolder{
		private BackupImageView photo, icon;
		private TextView name;
		private AvatarDrawable avatarDrawable=new AvatarDrawable();

		public ReactionUserViewHolder(ViewGroup list){
			super(getParentActivity().getLayoutInflater().inflate(R.layout.reaction_list_cell, list, false));
			photo=itemView.findViewById(R.id.photo);
			icon=itemView.findViewById(R.id.icon);
			name=itemView.findViewById(R.id.name);

			name.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
			photo.setRoundRadius(AndroidUtilities.dp(17));
		}

		public void bind(TLRPC.TL_messageUserReaction reaction){
			if(reaction.reaction==null){
				icon.setVisibility(View.GONE);
			}else{
				TLRPC.TL_availableReaction aReaction=fragment.getMediaDataController().getReaction(reaction.reaction);
				if(aReaction!=null){
					ReactionUtils.loadWebpIntoImageView(aReaction.static_icon, aReaction, icon);
					icon.setVisibility(View.VISIBLE);
				}else{
					icon.setVisibility(View.GONE);
				}
			}
			TLRPC.User user=fragment.getMessagesController().getUser(reaction.user_id);
			name.setText(ContactsController.formatName(user.first_name, user.last_name));
			avatarDrawable.setInfo(user);
			ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
			photo.setImage(imageLocation, "50_50", avatarDrawable, user);
		}
	}

	public class LoadingReactionViewHolder extends RecyclerView.ViewHolder{
		public LoadingReactionViewHolder(){
			super(new FlickerLoadingView(getParentActivity(), fragment.getResourceProvider()));

			FlickerLoadingView view=(FlickerLoadingView) itemView;
			view.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
			view.setViewType(FlickerLoadingView.REACTION_CELL_TYPE);
			view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48)));
		}

		public void bind(){
			((FlickerLoadingView)itemView).setItemsCount(getAdapterPosition()%2);
		}
	}

	private static class ReactionTypeLoadState{
		public boolean loading;
		public boolean fullyLoaded;
		public String offset;
		public int total;
		public ArrayList<TLRPC.TL_messageUserReaction> items=new ArrayList<>();
	}
}
