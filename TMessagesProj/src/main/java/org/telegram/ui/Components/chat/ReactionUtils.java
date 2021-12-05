package org.telegram.ui.Components.chat;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;

public class ReactionUtils{
	public static void loadWebpIntoImageView(TLRPC.Document document, Object parentObject, BackupImageView imageView){
		TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
		SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
		if (svgThumb != null) {
			if (thumb != null) {
				imageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", svgThumb, parentObject);
			} else {
				imageView.setImage(ImageLocation.getForDocument(document), null, "webp", svgThumb, parentObject);
			}
		} else {
			imageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", null, parentObject);
		}
	}

	public static void loadWebpIntoImageReceiver(TLRPC.Document document, Object parentObject, ImageReceiver receiver){
		TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
		SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
		if (svgThumb != null) {
			if (thumb != null) {
				receiver.setImage(ImageLocation.getForDocument(thumb, document), null, svgThumb, "webp", parentObject, 1);
			} else {
				receiver.setImage(ImageLocation.getForDocument(document), null, svgThumb, "webp", parentObject, 1);
			}
		} else {
			receiver.setImage(ImageLocation.getForDocument(thumb, document), null, null, "webp", parentObject, 1);
		}
	}

	public static void loadAnimationIntoImageView(TLRPC.Document document, Object parentObject, BackupImageView imageView, int size){
		TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
		SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
		String key=size+"_"+size;
		if (svgThumb != null) {
			imageView.getImageReceiver().setImage(ImageLocation.getForDocument(document), key, svgThumb, "tgs", parentObject, 1);
		} else if (thumb != null) {
			imageView.getImageReceiver().setImage(ImageLocation.getForDocument(document), key, ImageLocation.getForDocument(thumb, document), null, "tgs", parentObject, 1);
		} else {
			imageView.getImageReceiver().setImage(ImageLocation.getForDocument(document), key, null, "tgs", parentObject, 1);
		}
	}
}
