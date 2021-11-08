package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MergeRecyclerAdapter;
import org.telegram.ui.Adapters.SingleViewRecyclerAdapter;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.Cells.SmallerGroupCreateUserCell;

import java.util.List;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SendAsChannelChooser extends FrameLayout{
	private Theme.ResourcesProvider resourcesProvider;
	private FrameLayout content;
	private Paint scrimPaint=new Paint();
	private RecyclerListView listView;
	private MergeRecyclerAdapter adapter;
	private List<TLRPC.Peer> peers;
	private TLRPC.Peer chosenPeer;
	private Runnable dismissCallback, preDismissCallback;
	private OnPeerChosenListener peerChosenListener;
	private boolean dismissing;
	private Animator currentAnim;

	public SendAsChannelChooser(@NonNull Context context, Theme.ResourcesProvider resourcesProvider){
		super(context);
		this.resourcesProvider=resourcesProvider;

		content=new FrameLayout(context){
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
				super.onMeasure(widthMeasureSpec, Math.min(MeasureSpec.getSize(heightMeasureSpec), AndroidUtilities.dp(428)) | MeasureSpec.AT_MOST);
			}
		};
		Drawable shadowDrawable = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert).mutate();
		shadowDrawable.setColorFilter(new PorterDuffColorFilter(resourcesProvider.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
		content.setBackground(shadowDrawable);
		addView(content, LayoutHelper.createFrame(272, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

		HeaderCell header=new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 15, 15, false, resourcesProvider);
		header.setHeight(30);
		header.setText(LocaleController.getString("SendMessageAs", R.string.SendMessageAs));
		//content.addView(header);

		listView=new RecyclerListView(context, resourcesProvider);
		listView.setLayoutManager(new LinearLayoutManager(context));
		adapter=new MergeRecyclerAdapter();
		adapter.addAdapter(new SingleViewRecyclerAdapter(header));
		adapter.addAdapter(new PeersAdapter());
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this::onItemClick);
//		listView.setTopBottomSelectorRadius(6);
		listView.setDrawSelectorBehind(true);
		listView.setSelectorDrawableColor(resourcesProvider.getColor(Theme.key_dialogButtonSelector));
		listView.addItemDecoration(new RecyclerView.ItemDecoration(){
			@Override
			public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
				if(view==header){
					outRect.bottom=AndroidUtilities.dp(9);
				}
			}
		});
		listView.setPadding(0, 0, 0, AndroidUtilities.dp(6));
		listView.setClipToPadding(false);
		content.addView(listView);

		scrimPaint.setColor(0);
		setWillNotDraw(false);
		setClipToPadding(false);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh){
		setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
		super.onSizeChanged(w, h, oldw, oldh);
	}

	@Override
	protected void onDraw(Canvas canvas){
		canvas.drawRect(0, Math.min(0, -getY()), getWidth(), getHeight()-AndroidUtilities.dp(1), scrimPaint);
		super.onDraw(canvas);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event){
		if(super.onTouchEvent(event))
			return true;
		if(event.getAction()==MotionEvent.ACTION_DOWN){
			dismiss(null);
		}
		return true;
	}

	public void setPeersAndChoice(List<TLRPC.Peer> peers, TLRPC.Peer chosenPeer){
		this.peers=peers;
		this.chosenPeer=chosenPeer;
		adapter.notifyDataSetChanged();
	}

	private void onItemClick(View view, int position){
		position-=1;
		if(position<0)
			return;
		long chosenID=MessageObject.getPeerId(chosenPeer);
		TLRPC.Peer newPeer=peers.get(position);
		long newID=MessageObject.getPeerId(newPeer);
		if(chosenID==newID)
			return;
		for(int i=0;i<listView.getChildCount();i++){
			RecyclerView.ViewHolder holder=listView.getChildViewHolder(listView.getChildAt(i));
			if(holder instanceof PeerViewHolder){
				PeerViewHolder pvh=(PeerViewHolder) holder;
				if(pvh.did==chosenID){
					((SmallerGroupCreateUserCell)pvh.itemView).setChecked(false, true);
				}else if(pvh.did==newID){
					((SmallerGroupCreateUserCell)pvh.itemView).setChecked(true, true);
				}
			}
		}
		chosenPeer=newPeer;
		peerChosenListener.onPeerChosen(newPeer);
	}

	public void setDismissCallback(Runnable dismissCallback){
		this.dismissCallback=dismissCallback;
	}

	public void setPreDismissCallback(Runnable preDismissCallback){
		this.preDismissCallback=preDismissCallback;
	}

	public void setPeerChosenListener(OnPeerChosenListener peerChosenListener){
		this.peerChosenListener=peerChosenListener;
	}

	public void dismiss(Runnable additionalCallback){
		if(dismissing)
			return;
		preDismissCallback.run();
		if(currentAnim!=null)
			currentAnim.cancel();

		AnimatorSet set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofInt(this, "scrimAlpha", 0),
				ObjectAnimator.ofFloat(content, "translationY", AndroidUtilities.dp(-5)),
				ObjectAnimator.ofFloat(content, "alpha", 0f)
		);
		set.setDuration(150);
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				dismissCallback.run();
				if(additionalCallback!=null)
					additionalCallback.run();
				currentAnim=null;
			}
		});
		set.start();
		currentAnim=set;
	}

	public void show(){
		dismissing=false;
		if(currentAnim!=null)
			currentAnim.cancel();
		getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				getViewTreeObserver().removeOnPreDrawListener(this);
				content.setPivotX(0f);
				content.setPivotY(content.getHeight());
				return true;
			}
		});
		content.setTranslationY(0);
		AnimatorSet set=new AnimatorSet();
		ObjectAnimator scaleX, scaleY;
		set.playTogether(
				ObjectAnimator.ofInt(this, "scrimAlpha", 0x33).setDuration(150),
				ObjectAnimator.ofFloat(content, "alpha", 0f, 1f).setDuration(150),
				scaleX=ObjectAnimator.ofFloat(content, "scaleX", 0.95f, 1f).setDuration(200),
				scaleY=ObjectAnimator.ofFloat(content, "scaleY", 0.95f, 1f).setDuration(200)
		);
		OvershootInterpolator interpolator=new OvershootInterpolator();
		scaleX.setInterpolator(interpolator);
		scaleY.setInterpolator(interpolator);
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				content.setScaleX(1f);
				content.setScaleY(1f);
				currentAnim=null;
			}
		});
		set.start();
		currentAnim=set;
	}

	@Keep
	public int getScrimAlpha(){
		return scrimPaint.getAlpha();
	}

	@Keep
	public void setScrimAlpha(int alpha){
		scrimPaint.setAlpha(alpha);
		invalidate();
	}

	private class PeersAdapter extends RecyclerView.Adapter<PeerViewHolder>{

		@NonNull
		@Override
		public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return new PeerViewHolder();
		}

		@Override
		public void onBindViewHolder(@NonNull PeerViewHolder holder, int position){
			holder.bind(peers.get(position));
		}

		@Override
		public int getItemCount(){
			return peers.size();
		}

		@Override
		public int getItemViewType(int position){
			return R.id.object_tag;
		}
	}

	private class PeerViewHolder extends RecyclerView.ViewHolder{
		public long did;

		public PeerViewHolder(){
			super(new SmallerGroupCreateUserCell(getContext(), 2, 0, false));
			//itemView.setBackground(Theme.getSelectorDrawable(false));
		}

		public void bind(TLRPC.Peer peer){
			SmallerGroupCreateUserCell cell=(SmallerGroupCreateUserCell) itemView;
			did = MessageObject.getPeerId(peer);
			TLObject object;
			String status;
			if (did > 0) {
				object = MessagesController.getInstance(UserConfig.selectedAccount).getUser(did);
				status = LocaleController.getString("VoipGroupPersonalAccount", R.string.VoipGroupPersonalAccount);
			} else {
				object = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did);
				TLRPC.ChatFull full=MessagesController.getInstance(UserConfig.selectedAccount).getChatFull(-did);
				if(full!=null)
					status = LocaleController.formatPluralString(ChatObject.isChannelAndNotMegaGroup((TLRPC.Chat)object) ? "Subscribers" : "Members", full.participants_count);
				else
					status=null;
			}
			cell.setObject(object, null, status, false);
			cell.setChecked(MessageObject.getPeerId(chosenPeer)==did, false);
		}
	}

	@FunctionalInterface
	public interface OnPeerChosenListener{
		void onPeerChosen(TLRPC.Peer peer);
	}
}
