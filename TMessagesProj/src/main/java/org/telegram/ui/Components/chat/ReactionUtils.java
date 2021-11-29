package org.telegram.ui.Components.chat;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
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
}
