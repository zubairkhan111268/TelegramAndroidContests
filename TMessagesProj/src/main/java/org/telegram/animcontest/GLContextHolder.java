package org.telegram.animcontest;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import org.telegram.messenger.ApplicationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.*;

public class GLContextHolder{

	private static GLContextHolder instance;

	private static final int EGL_CONTEXT_CLIENT_VERSION=0x3098;
	private static final int EGL_OPENGL_ES2_BIT=4;
	/* package */ static final int TEXTURE_SIZE=256;
	private static final String TAG="AnimatedBackgroundView";

	private EGL10 egl;
	private EGLDisplay eglDisplay;
	private EGLConfig eglConfig;
	private EGLContext eglContext;
	/* package */ static final float[] vertices={
			-1, -1,
			-1, 1,
			1, -1,
			1, 1
	};
	private static final float[] vertexTexCoords={
			0, 0,
			0, 1,
			1, 0,
			1, 1
	};
	private FloatBuffer vertexBuffer, vertexTexCoordBuffer;
	/* package */ int gradientViewportSizeUniform, gradientColorsUniform, gradientPointsUniform, gradientSizeFactorUniform;
	/* package */ int displaceTextureUniform, displaceViewportSizeUniform, displaceSeedUniform, displaceAmplitudeUniform, displaceIntensityUniform;
	/* package */ int blurTextureUniform, blurRadiusUniform, blurDirectionUniform;
	/* package */ int shaderProgramGradient, shaderProgramDisplace, shaderProgramBlur;
	/* package */ int[] textures=new int[2], framebuffers=new int[2];

	private boolean initialized=false;

	public static GLContextHolder getInstance(){
		if(instance==null){
			instance=new GLContextHolder();
		}
		return instance;
	}

	private GLContextHolder(){

	}

	public void initAsync(){
		new Thread(new Runnable(){
			@Override
			public void run(){
				doInit();
			}
		}).start();
	}

	private void doInit(){
		egl=(EGL10) EGLContext.getEGL();
		eglDisplay=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
		if(eglDisplay==EGL10.EGL_NO_DISPLAY)
			throw new RuntimeException("eglGetDisplay failed "+GLUtils.getEGLErrorString(egl.eglGetError()));
		int[] version=new int[2];
		if(!egl.eglInitialize(eglDisplay, version))
			throw new RuntimeException("eglInitialize failed "+GLUtils.getEGLErrorString(egl.eglGetError()));

		eglConfig=chooseEglConfig();
		if(eglConfig==null)
			throw new RuntimeException("eglConfig not initialized");

		int[] attrib_list={EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
		eglContext=egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);

		int[] attribs={
				EGL10.EGL_WIDTH, 1,
				EGL10.EGL_HEIGHT, 1,
				EGL10.EGL_NONE
		};
		EGLSurface pbuffer=egl.eglCreatePbufferSurface(eglDisplay, eglConfig, attribs);
		if(pbuffer==EGL10.EGL_NO_SURFACE)
			throw new RuntimeException("eglCreatePbufferSurface failed "+GLUtils.getEGLErrorString(egl.eglGetError()));

		if(!egl.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext))
			throw new RuntimeException("eglMakeCurrent failed "+GLUtils.getEGLErrorString(egl.eglGetError()));

		vertexBuffer=floatArrayToBuffer(vertices);
		vertexTexCoordBuffer=floatArrayToBuffer(vertexTexCoords);

		int vertexShader=loadShader(GL_VERTEX_SHADER, readAssetFile("vertex.glsl"));

		shaderProgramGradient=makeShaderProgram("gradient_new.glsl", vertexShader);
		setupShaderAttributes(shaderProgramGradient);
		gradientViewportSizeUniform=glGetUniformLocation(shaderProgramGradient, "viewportSize");
		gradientColorsUniform=glGetUniformLocation(shaderProgramGradient, "colors");
		gradientPointsUniform=glGetUniformLocation(shaderProgramGradient, "points");
		gradientSizeFactorUniform=glGetUniformLocation(shaderProgramGradient, "sizeFactor");

		shaderProgramDisplace=makeShaderProgram("displace.glsl", vertexShader);
		setupShaderAttributes(shaderProgramDisplace);
		displaceViewportSizeUniform=glGetUniformLocation(shaderProgramDisplace, "viewportSize");
		displaceTextureUniform=glGetUniformLocation(shaderProgramDisplace, "texture");
		displaceSeedUniform=glGetUniformLocation(shaderProgramDisplace, "seed");
		displaceAmplitudeUniform=glGetUniformLocation(shaderProgramDisplace, "amplitude");
		displaceIntensityUniform=glGetUniformLocation(shaderProgramDisplace, "intensity");

		shaderProgramBlur=makeShaderProgram("blur.glsl", vertexShader);
		setupShaderAttributes(shaderProgramBlur);
		blurTextureUniform=glGetUniformLocation(shaderProgramBlur, "texture");
		blurRadiusUniform=glGetUniformLocation(shaderProgramBlur, "radius");
		blurDirectionUniform=glGetUniformLocation(shaderProgramBlur, "direction");

		glGenFramebuffers(2, framebuffers, 0);
		glGenTextures(2, textures, 0);

		glBindFramebuffer(GL_FRAMEBUFFER, framebuffers[0]);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, textures[0]);
//		glUniform1i(displaceTextureUniform, 0);
//		glUniform1i(blurTextureUniform, 0);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL_RGB, GL_UNSIGNED_BYTE, null);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[0], 0);

		glBindFramebuffer(GL_FRAMEBUFFER, framebuffers[1]);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, textures[1]);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL_RGB, GL_UNSIGNED_BYTE, null);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[1], 0);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glActiveTexture(GL_TEXTURE0);

		egl.eglMakeCurrent(EGL10.EGL_NO_DISPLAY, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
		egl.eglDestroySurface(eglDisplay, pbuffer);

		initialized=true;
	}

	public boolean makeCurrent(EGLSurface eglSurface){
		if(!initialized)
			return false;
		if(!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)){
			throw new RuntimeException("eglMakeCurrent failed "+GLUtils.getEGLErrorString(egl.eglGetError()));
		}
		return true;
	}

	public void makeCurrentNone(){
		egl.eglMakeCurrent(EGL10.EGL_NO_DISPLAY, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
	}

	public EGLSurface createEglSurface(SurfaceTexture surface){
		if(egl==null)
			throw new RuntimeException("egl not initialized");
		if(eglDisplay==null)
			throw new RuntimeException("eglDisplay not initialized");
		if(eglConfig==null)
			throw new RuntimeException("eglConfig not initialized");

		EGLSurface eglSurface;
		try{
			eglSurface=egl.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null);
		}catch(IllegalArgumentException e){
			Log.e(TAG, "eglCreateWindowSurface", e);
			return null;
		}

		if(eglSurface==null || eglSurface==EGL10.EGL_NO_SURFACE){
			int error=egl.eglGetError();
			if(error==EGL10.EGL_BAD_NATIVE_WINDOW){
				Log.e(TAG, "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
			}
			return null;
		}
		return eglSurface;
	}

	public void destroyEglSurface(EGLSurface eglSurface){
		egl.eglDestroySurface(eglDisplay, eglSurface);
	}

	public void swapBuffers(EGLSurface eglSurface){
		if (!egl.eglSwapBuffers(eglDisplay, eglSurface)) {
			throw new RuntimeException("Cannot swap buffers");
		}
	}

	private EGLConfig chooseEglConfig(){
		int[] configsCount=new int[1];
		EGLConfig[] configs=new EGLConfig[1];
		int[] configSpec=new int[]{
				EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
				EGL10.EGL_RED_SIZE, 8,
				EGL10.EGL_GREEN_SIZE, 8,
				EGL10.EGL_BLUE_SIZE, 8,
//				EGL10.EGL_ALPHA_SIZE, 8,
//				EGL10.EGL_DEPTH_SIZE, 0,
//				EGL10.EGL_STENCIL_SIZE, 0,
				EGL10.EGL_NONE
		};
		if(!egl.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)){
			throw new IllegalArgumentException("eglChooseConfig failed "+GLUtils.getEGLErrorString(egl.eglGetError()));
		}else if(configsCount[0]>0){
			return configs[0];
		}
		return null;
	}

	private FloatBuffer floatArrayToBuffer(float[] arr){
		ByteBuffer buf=ByteBuffer.allocateDirect(arr.length*4);
		buf.order(ByteOrder.nativeOrder());
		FloatBuffer fbuf=buf.asFloatBuffer();
		fbuf.put(arr);
		fbuf.position(0);
		return fbuf;
	}

	private String readAssetFile(String name){
		try(InputStream in=ApplicationLoader.applicationContext.getAssets().open("shaders/"+name)){
			BufferedReader reader=new BufferedReader(new InputStreamReader(in, "UTF-8"));
			StringBuilder sb=new StringBuilder();
			String line;
			while((line=reader.readLine())!=null){
				sb.append(line);
				sb.append('\n');
			}
			return sb.toString();
		}catch(IOException ignore){}
		return "";
	}

	private int loadShader(int type, String shaderCode) {
		int shader = glCreateShader(type);
		glShaderSource(shader, shaderCode);
		glCompileShader(shader);
		int[] compileStatus = new int[1];
		glGetShaderiv(shader, GL_COMPILE_STATUS, compileStatus, 0);
		if (compileStatus[0] == 0) {
			Log.e(TAG, glGetShaderInfoLog(shader));
			GLES20.glDeleteShader(shader);
			throw new RuntimeException("Failed to load shader");
		}
		return shader;
	}

	private void setupShaderAttributes(int shaderProgram){
		int positionAttribute=glGetAttribLocation(shaderProgram, "position");
		glEnableVertexAttribArray(positionAttribute);
		glVertexAttribPointer(positionAttribute, 2, GL_FLOAT, false, 4*2, vertexBuffer);

		int texCoordAttribute=glGetAttribLocation(shaderProgram, "texCoord");
		glEnableVertexAttribArray(texCoordAttribute);
		glVertexAttribPointer(texCoordAttribute, 2, GL_FLOAT, false, 4*2, vertexTexCoordBuffer);
	}

	private int makeShaderProgram(String fragmentShaderName, int vertexShader){
		int program=glCreateProgram();
		glAttachShader(program, vertexShader);
		glAttachShader(program, loadShader(GL_FRAGMENT_SHADER, readAssetFile(fragmentShaderName)));
		glLinkProgram(program);
		int[] linkStatus=new int[1];
		glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0);
		if(linkStatus[0]==0)
			throw new RuntimeException("Failed to link shader program");
		glUseProgram(program);
		return program;
	}
}
