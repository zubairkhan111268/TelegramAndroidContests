package org.telegram.messenger;

import android.util.LruCache;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public class SendAsChannelCache{
	private LruCache<Long, CachedPeerList> cache=new LruCache<>(25);
	private ConnectionsManager connectionsManager;

	public SendAsChannelCache(ConnectionsManager connectionsManager){
		this.connectionsManager=connectionsManager;
	}

	public void getSendAsPeers(TLRPC.Chat chat, OnSendAsPeersLoadedCallback callback){
		long id=chat.id;
		CachedPeerList list=cache.get(id);
		if(list!=null && (System.currentTimeMillis()-list.loadedAt<5*60_000 || connectionsManager.getConnectionState()!=ConnectionsManager.ConnectionStateConnected)){
			callback.onSendAsPeersLoaded(list.peers);
			return;
		}
		TLRPC.TL_channels_getSendAs req=new TLRPC.TL_channels_getSendAs();
		req.peer=MessagesController.getInputPeer(chat);
		connectionsManager.sendRequest(req, (response, error) -> {
			if(error!=null){
				if(list!=null){
					callback.onSendAsPeersLoaded(list.peers);
				}
				return;
			}
			AndroidUtilities.runOnUIThread(()->{
				TLRPC.TL_channels_sendAsPeers peers=(TLRPC.TL_channels_sendAsPeers)response;
				connectionsManager.getMessagesController().putChats(peers.chats, false);
				connectionsManager.getMessagesController().putUsers(peers.users, false);
				cache.put(id, new CachedPeerList(peers, System.currentTimeMillis()));
				callback.onSendAsPeersLoaded(peers);
			});
		});
	}

	public void removeFromCache(long id){
		cache.remove(id);
	}

	private static class CachedPeerList{
		public TLRPC.TL_channels_sendAsPeers peers;
		public long loadedAt;

		public CachedPeerList(TLRPC.TL_channels_sendAsPeers peers, long loadedAt){
			this.peers=peers;
			this.loadedAt=loadedAt;
		}
	}

	@FunctionalInterface
	public interface OnSendAsPeersLoadedCallback{
		void onSendAsPeersLoaded(TLRPC.TL_channels_sendAsPeers peers);
	}
}
