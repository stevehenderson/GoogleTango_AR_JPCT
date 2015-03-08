package com.digitalblacksmith.googletango_ar_jpct;

import com.digitalblacksmith.tango_ar_pointcloud.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.projecttango.tangoutils.Renderer;
import com.projecttango.tangoutils.renderables.PointCloud;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.threed.jpct.Camera;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Logger;
import com.threed.jpct.Object3D;
import com.threed.jpct.Primitives;
import com.threed.jpct.RGBColor;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.BitmapHelper;
import com.threed.jpct.util.MemoryHelper;


/**
 * This class implements our custom renderer. Note that the GL10 parameter passed in is unused for OpenGL ES 2.0
 * renderers -- the static class GLES20 is used instead.
 * 
 * from:
 * https://code.google.com/p/android-tes/source/browse/trunk/opengl+example/android/AndroidOpenGLESLessons/src/com/learnopengles/android/lesson2/LessonTwoRenderer.java?r=226
 */
public class OpenGL2JPCTRenderer extends Renderer implements GLSurfaceView.Renderer, ARRenderer {

	/** Used for debug logs. */
	private static final String TAG = "LessonTwoRenderer";

	/**
	 * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
	 * of being located at the center of the universe) to world space.
	 */
	private float[] mModelMatrix = new float[16];

	/**
	 * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
	 * it positions things relative to our eye.
	 */
	private float[] mViewMatrix = new float[16];

	/** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
	private float[] mProjectionMatrix = new float[16];

	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mMVPMatrix = new float[16];

	/** 
	 * Stores a copy of the model matrix specifically for the light position.
	 */
	private float[] mLightModelMatrix = new float[16];      

	

	

	/** Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when
	 *  we multiply this by our transformation matrices. */
	private final float[] mLightPosInModelSpace = new float[] {0.0f, 0.0f, 0.0f, 1.0f};

	/** Used to hold the current position of the light in world space (after transformation via model matrix). */
	private final float[] mLightPosInWorldSpace = new float[4];

	/** Used to hold the transformed position of the light in eye space (after transformation via modelview matrix) */
	private final float[] mLightPosInEyeSpace = new float[4];

	/** This is a handle to our per-vertex cube shading program. */
	private int mPerVertexProgramHandle;

	/** This is a handle to our light point program. */
	private int mPointProgramHandle;        


	private PointCloud mPointCloud;
	private int mMaxDepthPoints;

	//JPCT stuff
	private static PointCloudActivity master = null;
	
	private long time = System.currentTimeMillis();
	
	private boolean initialized = false;
	
	private FrameBuffer fb = null;
	private World world = null;
	
	/**
	 * Create a transparent alpha background color
	 * http://www.jpct.net/forum2/index.php/topic,1542.45.html
	 */
	private RGBColor back = new RGBColor(0, 0, 0, 0);

	private float touchTurn = 0;
	private float touchTurnUp = 0;

	private float xpos = -1;
	private float ypos = -1;

	private Object3D cube = null;
	private int fps = 0;
	private boolean gl2 = true;

	private Light sun = null;



	/**
	 * Cube scale
	 */
	float d = 0.1f;

	float x,y,z;
	float qx,qy,qz,qw;

	/**
	 * Set the camera position
	 */
	public void setCameraPosition(float x, float y, float z) {
		this.x=x;
		this.y=y;
		this.z=z;
	}

	/**
	 * Set the camera Euler rotation angles
	 */
	public void setCameraAngles(float x, float y, float z, float w) {
		this.qx=x;
		this.qy=y;
		this.qz=z;
		this.qw=w;
	}


	/**
	 * Initialize the model data.
	 */
	public OpenGL2JPCTRenderer(int maxDepthPoints, PointCloudActivity parent) {
	
		master = parent;
		
		mMaxDepthPoints = maxDepthPoints;

		
	}



	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) 
	{	}       

	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height) 
	{
		
		
		
		fb = new FrameBuffer(width, height); // OpenGL ES 2.0 constructor
		
		if (initialized == false) {

			world = new World();
			world.setAmbientLight(20, 20, 20);

			sun = new Light(world);
			sun.setIntensity(250, 250, 250);

			// Create a texture out of the icon...:-)
			Texture texture = master.getTextureFromIcon(R.drawable.icon);			
			TextureManager.getInstance().addTexture("texture", texture);

			cube = Primitives.getCube(10);
			cube.calcTextureWrapSpherical();
			cube.setTexture("texture");
			cube.strip();
			cube.build();

			world.addObject(cube);

			Camera cam = world.getCamera();
			cam.moveCamera(Camera.CAMERA_MOVEOUT, 50);
			cam.lookAt(cube.getTransformedCenter());

			SimpleVector sv = new SimpleVector();
			sv.set(cube.getTransformedCenter());
			sv.y -= 100;
			sv.z -= 100;
			sun.setPosition(sv);
			MemoryHelper.compact();
			
			Logger.log("Saving master Activity!");
			
		}
		
		// Set the OpenGL viewport to the same size as the surface.
		GLES20.glViewport(0, 0, width, height);

		// Create a new perspective projection matrix. The height will stay the same
		// while the width will vary as per aspect ratio.
		final float ratio = (float) width / height;
		final float near = 0.01f;
		final float far = 100.0f;

		float fov = 30; // degrees, try also 45, or different number if you like
		float top = (float) Math.tan(fov * 1.0*Math.PI / 360.0f) * near;
		float bottom = -top;
		float left = ratio * bottom;
		float right = ratio * top;

		Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
	}       

	@Override
	public void onDrawFrame(GL10 glUnused) 
	{
		fb.clear(back);
		world.renderScene(fb);
		world.draw(fb);
		fb.display();

		if (System.currentTimeMillis() - time >= 1000) {
			Logger.log(fps + "fps");
			fps = 0;
			time = System.currentTimeMillis();
		}
		fps++;
		
	}                               

	
	

	public PointCloud getPointCloud() {
		return mPointCloud;
	}

}