package org.telegram.ui.Components.voip;


import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AvatarWavesDrawable extends Drawable{

	float amplitude;
	float animateToAmplitude;
	float animateAmplitudeDiff;
	float wavesEnter = 1f;
	boolean showWaves;

	private BlobDrawable blobDrawable;
	private BlobDrawable blobDrawable2;

	private boolean hasCustomColor;
	private int isMuted;
	private float progressToMuted = 0;

	public AvatarWavesDrawable(int minRadiusInner, int maxRadiusInner, int minRadiusOuter, int maxRadiusOuter) {
		blobDrawable = new BlobDrawable(6);
		blobDrawable2 = new BlobDrawable(8);
		blobDrawable.minRadius = minRadiusInner;
		blobDrawable.maxRadius = maxRadiusInner;
		blobDrawable2.minRadius = minRadiusOuter;
		blobDrawable2.maxRadius = maxRadiusOuter;
		blobDrawable.generateBlob();
		blobDrawable2.generateBlob();
		blobDrawable.paint.setColor(0x24FFFFFF);
		blobDrawable2.paint.setColor(0x14FFFFFF);
	}

	public void update() {
		if (animateToAmplitude != amplitude) {
			amplitude += animateAmplitudeDiff * 16;
			if (animateAmplitudeDiff > 0) {
				if (amplitude > animateToAmplitude) {
					amplitude = animateToAmplitude;
				}
			} else {
				if (amplitude < animateToAmplitude) {
					amplitude = animateToAmplitude;
				}
			}
		}

		if (showWaves && wavesEnter != 1f) {
			wavesEnter += 16 / 350f;
			if (wavesEnter > 1f) {
				wavesEnter = 1f;
			}
		} else if (!showWaves && wavesEnter != 0) {
			wavesEnter -= 16 / 350f;
			if (wavesEnter < 0f) {
				wavesEnter = 0f;
			}
		}
	}

	public void draw(Canvas canvas, float cx, float cy) {
		if (SharedConfig.getLiteMode().enabled()) {
			return;
		}
		float scaleBlob = 1f + 0.1f * amplitude;
		if (showWaves || wavesEnter != 0) {
			canvas.save();
			float wavesEnter = CubicBezierInterpolator.DEFAULT.getInterpolation(this.wavesEnter);

			canvas.scale(scaleBlob * wavesEnter, scaleBlob * wavesEnter, cx, cy);

			if (!hasCustomColor) {
				if (isMuted != 1 && progressToMuted != 1f) {
					progressToMuted += 16 / 150f;
					if (progressToMuted > 1f) {
						progressToMuted = 1f;
					}
				} else if (isMuted == 1 && progressToMuted != 0f) {
					progressToMuted -= 16 / 150f;
					if (progressToMuted < 0f) {
						progressToMuted = 0f;
					}
				}
			}

			blobDrawable.update(amplitude, 1f);
			blobDrawable.draw(cx, cy, canvas, blobDrawable.paint);

			blobDrawable2.update(amplitude, 1f);
			blobDrawable2.draw(cx, cy, canvas, blobDrawable.paint);
			canvas.restore();
		}

		if (wavesEnter != 0 && (!blobDrawable.isCircle() || !blobDrawable2.isCircle() || blobDrawable.maxRadius!=blobDrawable.minRadius)) {
			invalidateSelf();
		}
	}

	public float getAvatarScale() {
		float scaleAvatar = 1f + 0.1f * amplitude;
		float wavesEnter = CubicBezierInterpolator.EASE_OUT.getInterpolation(this.wavesEnter);
		return scaleAvatar * wavesEnter + 1f * (1f - wavesEnter);
	}

	public void setShowWaves(boolean show) {
		if (showWaves != show) {
			invalidateSelf();
		}
		showWaves = show;
	}

	public void setAmplitude(double value) {
		float amplitude = (float) value / 80f;
		if (!showWaves) {
			amplitude = 0;
		}
		if (amplitude > 1f) {
			amplitude = 1f;
		} else if (amplitude < 0) {
			amplitude = 0;
		}
		animateToAmplitude = amplitude;
		animateAmplitudeDiff = (animateToAmplitude - this.amplitude) / 200;
	}

	public void setColor(int color) {
		hasCustomColor = true;
		blobDrawable.paint.setColor(color);
	}

	public void setMuted(int status, boolean animated) {
		this.isMuted = status;
		if (!animated) {
			progressToMuted = isMuted != 1 ? 1f : 0f;
		}
	}

	@Override
	public void draw(@NonNull Canvas canvas){
		update();
		Rect bounds=getBounds();
		draw(canvas, bounds.centerX(), bounds.centerY());
	}

	@Override
	public void setAlpha(int alpha){

	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter){

	}

	@Override
	public int getOpacity(){
		return PixelFormat.TRANSLUCENT;
	}

	@Keep
	public float getMinRadiusInner(){
		return blobDrawable.minRadius;
	}

	@Keep
	public float getMinRadiusOuter(){
		return blobDrawable2.minRadius;
	}

	@Keep
	public float getMaxRadiusInner(){
		return blobDrawable.maxRadius;
	}

	@Keep
	public float getMaxRadiusOuter(){
		return blobDrawable2.maxRadius;
	}

	@Keep
	public void setMinRadiusInner(float r){
		blobDrawable.minRadius=r;
		invalidateSelf();
	}

	@Keep
	public void setMinRadiusOuter(float r){
		blobDrawable2.minRadius=r;
		invalidateSelf();
	}

	@Keep
	public void setMaxRadiusInner(float r){
		blobDrawable.maxRadius=r;
		invalidateSelf();
	}

	@Keep
	public void setMaxRadiusOuter(float r){
		blobDrawable2.maxRadius=r;
		invalidateSelf();
	}
}
