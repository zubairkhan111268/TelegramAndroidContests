package org.telegram.animcontest;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import org.telegram.ui.Components.CubicBezierInterpolator;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.*;

public class AnimatedBackgroundView extends FrameLayout implements TextureView.SurfaceTextureListener, SensorEventListener{

	private static final String TAG="AnimatedBackgroundView";

	private TextureView textureView;

	private EGLSurface eglSurface;

	private SurfaceTexture surface;
	private int width, height;

	private float[] colorsForGL=new float[12];
	private float[] points=new float[8];
	private static final float[] POINT_POSITIONS_X={.349f, .816f, .643f, .173f, .349f};
	private static final float[] POINT_POSITIONS_Y={.244f, .084f, .751f, .916f, .244f};

	private GLContextHolder contextHolder=GLContextHolder.getInstance();

	private boolean invalidated=false;
	private Choreographer.FrameCallback frameCallback=this::render;
	private Interpolator interpolator=CubicBezierInterpolator.DEFAULT, nextInterpolator=CubicBezierInterpolator.DEFAULT;

	private float currentPointsRotationOffset;
	private long animationDuration, animationStartTime;
	private boolean animating;
	private float currentAnimationOffsetAmount;

	private float[] rollBuffer = new float[9], pitchBuffer = new float[9];
	private int bufferOffset;
	private WindowManager wm;
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private boolean accelerometerEnabled;
	private float additionalPointRotationOffset=0f, pointsTransX=0f, pointsTransY=0f;

	public boolean doNotDestroy=false;

	public AnimatedBackgroundView(Context context){
		super(context);
		textureView=new TextureView(context);
		textureView.setSurfaceTextureListener(this);

		addView(textureView, new LayoutParams(256, 256, Gravity.CENTER));

		wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	}

	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height){
		this.width=width;
		this.height=height;

		this.surface=surface;

		eglSurface=contextHolder.createEglSurface(surface);
		contextHolder.makeCurrent(eglSurface);

		render(System.nanoTime());
	}


	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height){
		this.width=width;
		this.height=height;
		Choreographer.getInstance().postFrameCallback(frameCallback);
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){
		if(doNotDestroy)
			return false;
		contextHolder.makeCurrentNone();
		contextHolder.destroyEglSurface(eglSurface);
		eglSurface=null;
		return true;
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface){

	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh){
		super.onSizeChanged(w, h, oldw, oldh);
//		Log.d(TAG, "onSizeChanged() called with: w = ["+w+"], h = ["+h+"], oldw = ["+oldw+"], oldh = ["+oldh+"]");
		textureView.setScaleX(w/256f);
		textureView.setScaleY(h/256f);
	}

	private void render(long frameTimeNanos){
//		Log.d(TAG, "render() called with: frameTimeNanos = ["+frameTimeNanos+"]");
		if(eglSurface==null || !contextHolder.makeCurrent(eglSurface))
			return;
		invalidated=false;

		float animationProgress=0f;
		if(animating){
			animationProgress=Math.max(0f, (frameTimeNanos-animationStartTime)/(float)animationDuration);
			if(animationProgress>=1f){
				animating=false;
				animationProgress=0f;
				currentPointsRotationOffset=clampOffset(currentPointsRotationOffset+currentAnimationOffsetAmount);
			}else{
				scheduleRedraw();
			}
			animationProgress=interpolator.getInterpolation(animationProgress);
		}

		// Render gradient into texture0
		glBindFramebuffer(GL_FRAMEBUFFER, contextHolder.framebuffers[0]);
		glUseProgram(contextHolder.shaderProgramGradient);
		glViewport(0, 0, GLContextHolder.TEXTURE_SIZE, GLContextHolder.TEXTURE_SIZE);
		glUniform2f(contextHolder.gradientViewportSizeUniform, GLContextHolder.TEXTURE_SIZE, GLContextHolder.TEXTURE_SIZE);
		float off=currentPointsRotationOffset;
		for(int i=0;i<4;i++){
			int color=AnimationSettings.backgroundColors[i];
			colorsForGL[i*3]=((color & 0x00FF0000) >> 16)/255f;
			colorsForGL[i*3+1]=((color & 0x0000FF00) >> 8)/255f;
			colorsForGL[i*3+2]=((color & 0x000000FF))/255f;

			float o=clampOffset(off+animationProgress*currentAnimationOffsetAmount+additionalPointRotationOffset);
			points[i*2]=getPointX(o)+pointsTransX;
			points[i*2+1]=1f-(getPointY(o)+pointsTransY);
			off+=.25f;
		}
		glUniform3fv(contextHolder.gradientColorsUniform, 4, colorsForGL, 0);
		glUniform2fv(contextHolder.gradientPointsUniform, 4, points, 0);
		glUniform1f(contextHolder.gradientSizeFactorUniform, AnimationSettings.backgroundGradientIntensity*2f+1f);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, GLContextHolder.vertices.length/2);

		// Blur vertically, texture0 -> texture1
		glBindFramebuffer(GL_FRAMEBUFFER, contextHolder.framebuffers[1]);
		glUseProgram(contextHolder.shaderProgramBlur);
		glUniform1f(contextHolder.blurRadiusUniform, Math.round(AnimationSettings.backgroundBlurRadius*10));
		glUniform2f(contextHolder.blurDirectionUniform, 0f, 1f);
		glUniform1i(contextHolder.blurTextureUniform, 0);

		glViewport(0, 0, GLContextHolder.TEXTURE_SIZE, GLContextHolder.TEXTURE_SIZE);
		glUniform2f(contextHolder.gradientViewportSizeUniform, GLContextHolder.TEXTURE_SIZE, GLContextHolder.TEXTURE_SIZE);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, GLContextHolder.vertices.length/2);

		// Blur horizontally, texture1 -> texture0
		glBindFramebuffer(GL_FRAMEBUFFER, contextHolder.framebuffers[0]);
		glUniform2f(contextHolder.blurDirectionUniform, 1f, 0f);
		glUniform1i(contextHolder.blurTextureUniform, 1);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, GLContextHolder.vertices.length/2);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glViewport(0, 0, width, height);
		glUseProgram(contextHolder.shaderProgramDisplace);
		glUniform1i(contextHolder.displaceTextureUniform, 0);
		glUniform2f(contextHolder.displaceViewportSizeUniform, width, height);
		glUniform1f(contextHolder.displaceSeedUniform, AnimationSettings.backgroundDisplaceSeed*100f);
		glUniform1f(contextHolder.displaceAmplitudeUniform, AnimationSettings.backgroundDisplaceAmplitude*.4f);
		glUniform1f(contextHolder.displaceIntensityUniform, AnimationSettings.backgroundDisplaceIntensity);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, GLContextHolder.vertices.length/2);


		contextHolder.swapBuffers(eglSurface);

//		Log.i(TAG, "init to first draw "+(System.currentTimeMillis()-t));
	}

	private float clampOffset(float offset){
		offset=offset%1f;
		return offset>0f ? offset : 1f+offset;
	}

	private float getPointX(float offset){
		offset*=POINT_POSITIONS_X.length-1;
		int a=(int) Math.floor(offset), b=(int) Math.ceil(offset);
		offset-=a;
		return POINT_POSITIONS_X[a]*(1f-offset)+POINT_POSITIONS_X[b]*offset;
	}

	private float getPointY(float offset){
		offset*=POINT_POSITIONS_Y.length-1;
		int a=(int) Math.floor(offset), b=(int) Math.ceil(offset);
		offset-=a;
		return POINT_POSITIONS_Y[a]*(1f-offset)+POINT_POSITIONS_Y[b]*offset;
	}

	public void setInterpolator(Interpolator interpolator){
		this.nextInterpolator=interpolator;
	}

	public void animateColorRotation(float offsetAmount, long startDelay, long duration){
		if(animating){
			float animationProgress=interpolator.getInterpolation(Math.min(1f, Math.max(0f, (System.nanoTime()-animationStartTime)/(float)animationDuration)));
			currentPointsRotationOffset=clampOffset(currentPointsRotationOffset+animationProgress*currentAnimationOffsetAmount);
		}
		interpolator=nextInterpolator;
		animationDuration=duration*1000000L;
		animationStartTime=System.nanoTime()+startDelay*1000000L;
		currentAnimationOffsetAmount=offsetAmount;
		animating=true;
		scheduleRedraw();
	}

	void scheduleRedraw(){
		if(invalidated)
			return;
		invalidated=true;
		Choreographer.getInstance().postFrameCallback(frameCallback);
	}

	public void onPause(){
		if(accelerometer==null || !accelerometerEnabled)
			return;
		sensorManager.unregisterListener(this);
		accelerometerEnabled=false;
	}

	public void onResume(){
		if(accelerometer==null || accelerometerEnabled)
			return;
		sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
		accelerometerEnabled=true;
	}

	@Override
	public void onSensorChanged(SensorEvent event){
		int rotation = wm.getDefaultDisplay().getRotation();

		float x = event.values[0] / SensorManager.GRAVITY_EARTH;
		float y = event.values[1] / SensorManager.GRAVITY_EARTH;
		float z = event.values[2] / SensorManager.GRAVITY_EARTH;


		float pitch=(float)(Math.atan2(x, Math.sqrt(y*y+z*z))/Math.PI*2.0);
		float roll=(float)(Math.atan2(y, Math.sqrt(x*x+z*z))/Math.PI*2.0);

		switch (rotation) {
			case Surface.ROTATION_0:
				break;
			case Surface.ROTATION_90: {
				float tmp = pitch;
				pitch = roll;
				roll = tmp;
				break;
			}
			case Surface.ROTATION_180:
				roll = -roll;
				pitch = -pitch;
				break;
			case Surface.ROTATION_270: {
				float tmp = -pitch;
				pitch = roll;
				roll = tmp;
				break;
			}
		}
		rollBuffer[bufferOffset] = roll;
		pitchBuffer[bufferOffset] = pitch;
		bufferOffset = (bufferOffset + 1) % rollBuffer.length;
		roll = pitch = 0;
		for (int i = 0; i < rollBuffer.length; i++) {
			roll += rollBuffer[i];
			pitch += pitchBuffer[i];
		}
		roll /= rollBuffer.length;
		pitch /= rollBuffer.length;
		if (roll > 1f) {
			roll = 2f - roll;
		} else if (roll < -1f) {
			roll = -2f - roll;
		}

		additionalPointRotationOffset=roll*.25f+pitch*.25f;
		pointsTransX=pitch*0.1f;
		pointsTransY=roll*0.1f;
		scheduleRedraw();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int i){

	}
}
