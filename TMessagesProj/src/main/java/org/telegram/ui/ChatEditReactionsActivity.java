package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MergeRecyclerAdapter;
import org.telegram.ui.Adapters.SingleViewRecyclerAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeparatorBackgroundDrawable;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChatEditReactionsActivity extends BaseFragment{

	private TLRPC.ChatFull chat;

	private MergeRecyclerAdapter adapter;
	private RecyclerListView list;

	private TextCheckCell enableCell;
	private TextInfoPrivacyCell explainCell;
	private HeaderCell headerCell;
	private SingleViewRecyclerAdapter headerAdapter;
	private ReactionsAdapter reactionsAdapter;
	private List<TLRPC.TL_availableReaction> reactions;

	private ArrayList<String> selectedReactions=new ArrayList<>();
	private boolean enabled;

	public ChatEditReactionsActivity(Bundle args){
		super(args);
	}

	@Override
	public View createView(Context context){
		reactions=getMediaDataController().getAvailableReactions();

		actionBar.setBackButtonImage(R.drawable.ic_ab_back);
		actionBar.setTitle(LocaleController.getString("Reactions", R.string.Reactions));
		if (AndroidUtilities.isTablet()) {
			actionBar.setOccupyStatusBar(false);
		}
		actionBar.setAllowOverlayTitle(true);
		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					finishFragment();
				}
			}
		});

		fragmentView = new FrameLayout(context);
		fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
		FrameLayout frameLayout = (FrameLayout) fragmentView;

		list=new RecyclerListView(context);
		list.setVerticalScrollBarEnabled(false);
		((DefaultItemAnimator) list.getItemAnimator()).setDelayAnimations(false);
		list.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
		frameLayout.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
		adapter=new MergeRecyclerAdapter();
		list.setAdapter(adapter);
		list.setOnItemClickListener(this::onListItemClick);

		enableCell = new TextCheckCell(context);
		enableCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
		enableCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
		enableCell.setHeight(56);
		enableCell.setTextAndCheck(LocaleController.getString("EnableReactions", R.string.EnableReactions), false, false);
		enableCell.setDrawCheckRipple(true);
		adapter.addAdapter(new SingleViewRecyclerAdapter(enableCell, true));

		explainCell=new TextInfoPrivacyCell(context);
		adapter.addAdapter(new SingleViewRecyclerAdapter(explainCell, false));

		headerCell=new HeaderCell(context);
		headerCell.setText(LocaleController.getString("AvailableReactions", R.string.AvailableReactions));
		headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
		headerAdapter=new SingleViewRecyclerAdapter(headerCell, false);

		reactionsAdapter=new ReactionsAdapter();

		if(chat!=null)
			updateInfo();
		return fragmentView;
	}

	@Override
	public void onFragmentDestroy(){
		if(!enabled)
			selectedReactions.clear();
		if(chat!=null && (chat.available_reactions.size()!=selectedReactions.size() || !chat.available_reactions.containsAll(selectedReactions))){
			TLRPC.ChatFull nChat=getMessagesController().getChatFull(chat.id);
			nChat.available_reactions=selectedReactions;
			getMessagesStorage().updateChatInfo(nChat, false);
			getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, nChat, 0, false, false);

			TLRPC.TL_messages_setChatAvailableReactions req=new TLRPC.TL_messages_setChatAvailableReactions();
			req.peer=getMessagesController().getInputPeer(-chat.id);
			req.available_reactions=new ArrayList<>(selectedReactions);
			getConnectionsManager().sendRequest(req, (res, err)->{
				if(res instanceof TLRPC.Updates){
					getMessagesController().processUpdates((TLRPC.Updates) res, false);
				}
			});
		}
		super.onFragmentDestroy();
	}

	public void setInfo(TLRPC.ChatFull chat){
		this.chat=chat;
		updateInfo();
	}

	private void onListItemClick(View view, int pos, float x, float y){
		int itemsStart=adapter.getPositionForAdapter(reactionsAdapter);
		if(pos==0){
			setEnableChecked(!enabled, true);

			explainCell.setBackgroundDrawable(enabled ? Theme.getThemedDrawable(getParentActivity(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow) : null);
			if(enabled){
				if(selectedReactions.isEmpty()){
					for(TLRPC.TL_availableReaction reaction:reactions){
						selectedReactions.add(reaction.reaction);
					}
				}
				adapter.addAdapter(headerAdapter);
				adapter.addAdapter(reactionsAdapter);
			}else{
				adapter.removeAdapter(headerAdapter);
				adapter.removeAdapter(reactionsAdapter);
			}
		}else if(enabled && pos>=itemsStart){
			String reaction=reactions.get(pos-itemsStart).reaction;
			ReactionViewHolder holder=(ReactionViewHolder) list.getChildViewHolder(view);
			if(selectedReactions.contains(reaction)){
				selectedReactions.remove(reaction);
				holder.toggle.setChecked(false, true);
			}else{
				selectedReactions.add(reaction);
				holder.toggle.setChecked(true, true);
			}
		}
	}

	private void updateInfo(){
		if(fragmentView==null)
			return;

		boolean wasEnabled=enabled;
		setEnableChecked(chat.available_reactions!=null && !chat.available_reactions.isEmpty(), false);
		explainCell.setText(ChatObject.isChannelAndNotMegaGroup(chat.id, currentAccount) ? LocaleController.getString("EnableReactionsChannel", R.string.EnableReactionsChannel) : LocaleController.getString("EnableReactionsGroup", R.string.EnableReactionsGroup));
		explainCell.setBackgroundDrawable(enabled ? Theme.getThemedDrawable(getParentActivity(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow) : null);

		if(enabled==wasEnabled)
			return;

		selectedReactions.addAll(chat.available_reactions);

		if(enabled){
			adapter.addAdapter(headerAdapter);
			adapter.addAdapter(reactionsAdapter);
		}else{
			adapter.removeAdapter(headerAdapter);
			adapter.removeAdapter(reactionsAdapter);
		}
	}

	private void setEnableChecked(boolean checked, boolean animated){
		enabled=checked;
		if(animated)
			enableCell.setBackgroundColorAnimated(!enableCell.isChecked(), Theme.getColor(checked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
		enableCell.setChecked(checked);
		if(!animated)
			enableCell.setBackgroundColor(Theme.getColor(checked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
	}

	private class ReactionsAdapter extends RecyclerListView.SelectionAdapter{

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return new ReactionViewHolder();
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position){
			((ReactionViewHolder)holder).bind(reactions.get(position), position==reactions.size()-1);
		}

		@Override
		public int getItemCount(){
			return reactions.size();
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder){
			return true;
		}

		@Override
		public int getItemViewType(int position){
			return R.id.toggle;
		}
	}

	private class ReactionViewHolder extends RecyclerView.ViewHolder{

		private BackupImageView image;
		private TextView text;
		private Switch toggle;
		private SeparatorBackgroundDrawable bg;

		public ReactionViewHolder(){
			super(getParentActivity().getLayoutInflater().inflate(R.layout.image_switch_cell, list, false));
			image=itemView.findViewById(R.id.image);
			text=itemView.findViewById(R.id.title);
			toggle=itemView.findViewById(R.id.toggle);

			image.setLayerNum(1);
			text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
			itemView.setBackground(bg=new SeparatorBackgroundDrawable(Theme.key_windowBackgroundWhite, 70));
			toggle.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
		}

		public void bind(TLRPC.TL_availableReaction reaction, boolean last){
			text.setText(reaction.title);
			toggle.setChecked(selectedReactions.contains(reaction.reaction), false);

			TLRPC.Document document=reaction.static_icon;
			TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
			SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
			if (svgThumb != null) {
				if (thumb != null) {
					image.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", svgThumb, reaction);
				} else {
					image.setImage(ImageLocation.getForDocument(document), null, "webp", svgThumb, reaction);
				}
			} else {
				image.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", null, reaction);
			}

			bg.setDrawSeparator(!last);
		}
	}
}
