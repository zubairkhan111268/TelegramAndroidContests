package org.telegram.animcontest;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.animation.Interpolator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AnimationSettings{

	private static final String TAG="AnimationSettings";

//	static{
//		setDefaults();
//	}

	public static int[] backgroundColors;
	public static long backgroundSendMessageDuration;
	public static long backgroundOpenChatDuration;
	public static long backgroundJumpToMessageDuration;
	public static float backgroundGradientIntensity; // 1-3, def 2
	public static float backgroundDisplaceSeed; // 0-100, def 0
	public static float backgroundDisplaceAmplitude; // 0-.4, def .1
	public static float backgroundDisplaceIntensity; // 0-1, def .5
	public static float backgroundBlurRadius; // 0-10, def 2
	public static TimingParameters backgroundSendMessageTiming;
	public static TimingParameters backgroundOpenChatTiming;
	public static TimingParameters backgroundJumpToMessageTiming;
	public static BubbleMessageAnimationParameters shortTextMessageParams;
	public static BubbleMessageAnimationParameters longTextMessageParams;
	public static BubbleMessageAnimationParameters linkPreviewMessageParams;
	public static BubbleMessageAnimationParameters photoMessageParams;
	public static StickerMessageAnimationParameters emojiMessageParams;
	public static BubbleMessageAnimationParameters voiceMessageParams;
	public static StickerMessageAnimationParameters stickerMessageParams;

	public static void setDefaults(){
		backgroundColors=new int[]{0xFFFFF6BF, 0xFF76A076, 0xFFF6E477, 0xFF316B4D};
		backgroundSendMessageDuration=1000;
		backgroundOpenChatDuration=5000;
		backgroundJumpToMessageDuration=1000;
		backgroundSendMessageTiming=new TimingParameters();
		backgroundOpenChatTiming=new TimingParameters(0f, 1f, .16f, 1f);
		backgroundJumpToMessageTiming=new TimingParameters();

		backgroundGradientIntensity=2f/3f;
		backgroundDisplaceSeed=0f;
		backgroundDisplaceAmplitude=0.25f;
		backgroundDisplaceIntensity=0.5f;
		backgroundBlurRadius=.2f;

		shortTextMessageParams=new BubbleMessageAnimationParameters();
		longTextMessageParams=new BubbleMessageAnimationParameters();
		linkPreviewMessageParams=new BubbleMessageAnimationParameters();
		photoMessageParams=new BubbleMessageAnimationParameters();
		emojiMessageParams=new StickerMessageAnimationParameters();
		voiceMessageParams=new BubbleMessageAnimationParameters();
		stickerMessageParams=new StickerMessageAnimationParameters();

		voiceMessageParams.duration=700;
		voiceMessageParams.scaleTiming=new TimingParameters();

		photoMessageParams.duration=1000;
		photoMessageParams.xPositionTiming=new TimingParameters();
		photoMessageParams.timeAppearTiming=new TimingParameters(.5f, 1f);
		photoMessageParams.colorChangeTiming=new TimingParameters(0f, .5f);
		photoMessageParams.scaleTiming=new TimingParameters();
		photoMessageParams.bubbleShapeTiming=new TimingParameters();
	}

	public static void save(){
		ApplicationLoader.applicationContext.getSharedPreferences("animation_contest", Context.MODE_PRIVATE).edit().putString("settings", serialize()).apply();
	}

	public static void load(){
		try{
			SharedPreferences prefs=ApplicationLoader.applicationContext.getSharedPreferences("animation_contest", Context.MODE_PRIVATE);
			String s=prefs.getString("settings", null);
			if(s==null)
				setDefaults();
			else
				deserialize(s);
		}catch(JSONException x){
			Log.w(TAG, x);
			setDefaults();
		}
	}

	public static void write(OutputStream out) throws IOException{
		out.write(serialize().getBytes("UTF-8"));
	}

	public static void read(InputStream in) throws IOException, JSONException{
		byte[] buf=new byte[in.available()];
		new DataInputStream(in).readFully(buf);
		deserialize(new String(buf, "UTF-8"));
	}

	private static String serialize(){
		try{
			JSONObject root=new JSONObject();
			JSONArray colors=new JSONArray();
			for(int c:backgroundColors)
				colors.put(c);
			root.put("bgColors", colors);
			root.put("bgSendDur", backgroundSendMessageDuration);
			root.put("bgOpenDur", backgroundOpenChatDuration);
			root.put("bgJumpDur", backgroundJumpToMessageDuration);
			root.put("bgSend", backgroundSendMessageTiming.serialize());
			root.put("bgOpen", backgroundOpenChatTiming.serialize());
			root.put("bgJump", backgroundJumpToMessageTiming.serialize());
			root.put("bgGradIntensity", backgroundGradientIntensity);
			root.put("bgDispSeed", backgroundDisplaceSeed);
			root.put("bgDispAmpl", backgroundDisplaceAmplitude);
			root.put("bgDispIntensity", backgroundDisplaceIntensity);
			root.put("bgBlurRadius", backgroundBlurRadius);

			root.put("shortText", shortTextMessageParams.serialize());
			root.put("longText", longTextMessageParams.serialize());
			root.put("emoji", emojiMessageParams.serialize());
			root.put("sticker", stickerMessageParams.serialize());
			root.put("link", linkPreviewMessageParams.serialize());
			root.put("voice", voiceMessageParams.serialize());
			root.put("photo", photoMessageParams.serialize());

			return root.toString();
		}catch(JSONException x){
			Log.w(TAG, x);
		}
		throw new RuntimeException("impossible");
	}

	private static void deserialize(String s) throws JSONException{
		JSONObject root=new JSONObject(s);
		JSONArray colors=root.getJSONArray("bgColors");
		if(colors.length()!=4)
			throw new JSONException("wrong colors length");
		backgroundColors=new int[4];
		for(int i=0;i<4;i++){
			backgroundColors[i]=colors.getInt(i);
			if((backgroundColors[i] & 0xFF000000)!=0xFF000000)
				throw new JSONException("invalid color");
		}
		backgroundSendMessageDuration=root.getLong("bgSendDur");
		backgroundOpenChatDuration=root.getLong("bgOpenDur");
		backgroundJumpToMessageDuration=root.getLong("bgJumpDur");
		backgroundSendMessageTiming=new TimingParameters(root.getJSONObject("bgSend"));
		backgroundOpenChatTiming=new TimingParameters(root.getJSONObject("bgOpen"));
		backgroundJumpToMessageTiming=new TimingParameters(root.getJSONObject("bgJump"));
		backgroundGradientIntensity=clamp(root.getDouble("bgGradIntensity"));
		backgroundDisplaceSeed=clamp(root.getDouble("bgDispSeed"));
		backgroundDisplaceAmplitude=clamp(root.getDouble("bgDispAmpl"));
		backgroundDisplaceIntensity=clamp(root.getDouble("bgDispIntensity"));
		backgroundBlurRadius=clamp(root.getDouble("bgBlurRadius"));

		shortTextMessageParams=new BubbleMessageAnimationParameters(root.getJSONObject("shortText"));
		longTextMessageParams=new BubbleMessageAnimationParameters(root.getJSONObject("longText"));
		linkPreviewMessageParams=new BubbleMessageAnimationParameters(root.getJSONObject("link"));
		emojiMessageParams=new StickerMessageAnimationParameters(root.getJSONObject("emoji"));
		stickerMessageParams=new StickerMessageAnimationParameters(root.getJSONObject("sticker"));
		voiceMessageParams=new BubbleMessageAnimationParameters(root.getJSONObject("voice"));
		photoMessageParams=new BubbleMessageAnimationParameters(root.getJSONObject("photo"));
	}

	private static float clamp(double d){
		return Math.max(0f, Math.min(1f, (float)d));
	}

	public static class TimingParameters{
		public float startDelayFraction, endTimeFraction;
		float easingStart, easingEnd;
		private Interpolator interpolator;

		public TimingParameters(float startDelayFraction, float endTimeFraction, float easingStart, float easingEnd){
			this.startDelayFraction=startDelayFraction;
			this.endTimeFraction=endTimeFraction;
			setEasing(easingStart, easingEnd);
		}

		public TimingParameters(float startDelayFraction, float endTimeFraction){
			this.startDelayFraction=startDelayFraction;
			this.endTimeFraction=endTimeFraction;
			setEasing(.33f, 1f);
		}

		public TimingParameters(){
			startDelayFraction=0f;
			endTimeFraction=1f;
			setEasing(.33f, 1f);
		}

		public TimingParameters(JSONObject o) throws JSONException{
			startDelayFraction=(float) o.getDouble("start");
			endTimeFraction=(float)o.getDouble("end");
			setEasing((float)o.getDouble("ease0"), (float)o.getDouble("ease1"));
		}

		public Interpolator getInterpolator(){
			return interpolator;
		}

		public void setEasing(float start, float end){
			easingStart=start;
			easingEnd=end;
			interpolator=new CubicBezierInterpolator(easingStart, 0f, 1f-easingEnd, 1f);
		}

		public long scaledDuration(long duration){
			return Math.round(duration*(endTimeFraction-startDelayFraction));
		}

		public long scaledStartDelay(long duration){
			return Math.round(duration*startDelayFraction);
		}

		public JSONObject serialize() throws JSONException{
			JSONObject o=new JSONObject();
			o.put("start", startDelayFraction);
			o.put("end", endTimeFraction);
			o.put("ease0", easingStart);
			o.put("ease1", easingEnd);
			return o;
		}
	}

	public static class MessageAnimationParameters{
		public long duration=500;
		public TimingParameters xPositionTiming=new TimingParameters(0f, .5f);
		public TimingParameters yPositionTiming=new TimingParameters();
		public TimingParameters timeAppearTiming=new TimingParameters(0f, .5f);
		public TimingParameters scaleTiming=new TimingParameters(0f, .5f);

		public MessageAnimationParameters(){

		}

		public MessageAnimationParameters(JSONObject o) throws JSONException{
			duration=o.getLong("dur");
			xPositionTiming=new TimingParameters(o.getJSONObject("xPos"));
			yPositionTiming=new TimingParameters(o.getJSONObject("yPos"));
			timeAppearTiming=new TimingParameters(o.getJSONObject("timeAppear"));
			scaleTiming=new TimingParameters(o.getJSONObject("scale"));
		}

		public JSONObject serialize() throws JSONException{
			JSONObject o=new JSONObject();
			o.put("dur", duration);
			o.put("xPos", xPositionTiming.serialize());
			o.put("yPos", yPositionTiming.serialize());
			o.put("timeAppear", timeAppearTiming.serialize());
			o.put("scale", scaleTiming.serialize());
			return o;
		}
	}

	public static class BubbleMessageAnimationParameters extends MessageAnimationParameters{

		public TimingParameters colorChangeTiming=new TimingParameters(0f, .5f);
		public TimingParameters bubbleShapeTiming=new TimingParameters(0f, .5f);

		public BubbleMessageAnimationParameters(){
		}

		public BubbleMessageAnimationParameters(JSONObject o) throws JSONException{
			super(o);
			colorChangeTiming=new TimingParameters(o.getJSONObject("colorChange"));
			bubbleShapeTiming=new TimingParameters(o.getJSONObject("bubbleShape"));
		}

		@Override
		public JSONObject serialize() throws JSONException{
			JSONObject o=super.serialize();
			o.put("colorChange", colorChangeTiming.serialize());
			o.put("bubbleShape", bubbleShapeTiming.serialize());
			return o;
		}
	}

	public static class StickerMessageAnimationParameters extends MessageAnimationParameters{

		public TimingParameters placeholderCrossfadeTiming=new TimingParameters(0f, .25f);
		public TimingParameters reappearTiming=new TimingParameters();

		public StickerMessageAnimationParameters(){
			timeAppearTiming=new TimingParameters(.5f, 1f);
		}

		public StickerMessageAnimationParameters(JSONObject o) throws JSONException{
			super(o);
			placeholderCrossfadeTiming=new TimingParameters(o.getJSONObject("placeholderCrossfade"));
			reappearTiming=new TimingParameters(o.getJSONObject("reappear"));
		}

		@Override
		public JSONObject serialize() throws JSONException{
			JSONObject o=super.serialize();
			o.put("placeholderCrossfade", placeholderCrossfadeTiming.serialize());
			o.put("reappear", reappearTiming.serialize());
			return o;
		}
	}
}
