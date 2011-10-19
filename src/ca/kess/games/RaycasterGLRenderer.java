package ca.kess.games;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;
import android.opengl.GLUtils;


public class RaycasterGLRenderer implements Renderer {
    
    //The map
    private int[][] mMap = {
            { 1, 1, 1, 1, 1, 1, 1, 1, },
            { 1, 0, 0, 0, 0, 0, 0, 1, },
            { 1, 0, 0, 0, 0, 0, 0, 1, },
            { 1, 0, 0, 0, 0, 0, 0, 1, },
            { 1, 0, 0, 0, 0, 0, 1, 1, },
            { 1, 0, 0, 1, 1, 0, 0, 1, },
            { 1, 0, 0, 0, 0, 1, 0, 1, },
            { 1, 1, 1, 1, 1, 1, 1, 1, }
    };
    
    private int mScreenWidth, mScreenHeight;
    
    private double mPlayerX, mPlayerY, mPlayerVelocityX, mPlayerVelocityY;
    private double mPlayerAngle, mPlayerFOV;
    private double mPlayerRadius;
    
    private double mDistanceToClipPlane;
    private double mPlayerAngleDelta;
    
    private final int mTileWidth = 64;
    private final int mTileHeight = 64;
    private final int mTileDepth = 64;
    
    private final int mPlayerHeight = mTileHeight / 2;
    
    
    private int[] mTextures = new int[2];


    /// GL INFO FOR WALLS
    private CollisionInfo[] mWalls;
    private float[] mWallCoords;
    private float[] mWallColors;
    private float[] mWallTextureCoords;
    
    private FloatBuffer mWallVertexBuffer;
    private FloatBuffer mWallColorBuffer;
    private FloatBuffer mWallTextureBuffer;

    /// GL INFO FOR FLOORS
    private float[] mFloorCoords;
    private float[] mFloorColors;
    private float[] mFloorTextureCoords;
    
    private FloatBuffer mFloorVertexBuffer;
    private FloatBuffer mFloorColorBuffer;
    private FloatBuffer mFloorTextureBuffer;
    
    private int mNumFloorPixels;
    
    
    
    private Context mContext;
    
    public RaycasterGLRenderer(Context context) {
        mContext = context;
    }

    private int loadTexture(GL10 gl, Bitmap bitmap) {
        int[] textureID = new int[1];
        gl.glGenTextures(1, textureID, 0);
        
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureID[0]);
        // Create Nearest Filtered Texture
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
        
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
        
        return textureID[0];
    }
    
    public void loadTextures(GL10 gl) {
        
        Bitmap wallTexture = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.wall);
        Bitmap floorTexture = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.wall_red);
        mTextures[0] = loadTexture(gl, wallTexture);
        mTextures[1] = loadTexture(gl, floorTexture);
    }
    
    @Override
    public void onDrawFrame(GL10 gl) {
        updateGame();
        mNumFloorPixels = 0;
        //Calculate wall coords
        for(int w=0; w < mScreenWidth; ++w) {
            double wallHeight = (mTileDepth / mWalls[w].Distance) * mDistanceToClipPlane;
            mWallCoords[4 * w + 0] = w;
            mWallCoords[4 * w + 1] = (float) ((mScreenHeight - wallHeight) / 2.0);
            mWallCoords[4 * w + 2] = w;
            mWallCoords[4 * w + 3] = (float) ((mScreenHeight + wallHeight) / 2.0);
            
            float intensity = (float) (1.0f - (mWalls[w].Distance/700.0f));
            
            mWallColors[8 * w + 0] = intensity;
            mWallColors[8 * w + 1] = intensity;
            mWallColors[8 * w + 2] = intensity;
            mWallColors[8 * w + 3] = 1.0f;
            
            mWallColors[8 * w + 4] = intensity;
            mWallColors[8 * w + 5] = intensity;
            mWallColors[8 * w + 6] = intensity;
            mWallColors[8 * w + 7] = 1.0f;
            
            mWallTextureCoords[4 * w + 0] = (float) mWalls[w].CollisionPosition;
            mWallTextureCoords[4 * w + 1] = 1.0f;
            mWallTextureCoords[4 * w + 2] = (float) mWalls[w].CollisionPosition;
            mWallTextureCoords[4 * w + 3] = 0.0f;
            
            int midPointRow = mScreenHeight / 2;
            for(int h = (int)mWallCoords[4 * w + 1]; h >= 0; --h) {
                
                //Figure out the straight line distance to the point
                float straightDistance = (float) ((mDistanceToClipPlane * mPlayerHeight) / (midPointRow - h));
                straightDistance /= Math.cos(mWalls[w].Angle - mPlayerAngle);
                //Figure out where on the floor this point lies
                float texX = (float) (mPlayerX + straightDistance * Math.cos(mWalls[w].Angle)) / mTileHeight;
                float texY = (float) (mPlayerY + straightDistance * Math.sin(mWalls[w].Angle)) / mTileWidth;
                
                mFloorCoords[2 * mNumFloorPixels + 0] = w;
                mFloorCoords[2 * mNumFloorPixels + 1] = h;
                
                mFloorTextureCoords[2 * mNumFloorPixels + 0] =  texX;
                mFloorTextureCoords[2 * mNumFloorPixels + 1] =  texY;
                        
                ++mNumFloorPixels;
            }
        }
        
        //Put wall coords into vertex buffer
        mWallVertexBuffer.clear();
        mWallVertexBuffer.position(0);
        mWallVertexBuffer.put(mWallCoords);
        mWallVertexBuffer.position(0);
        
        mWallColorBuffer.clear();
        mWallColorBuffer.position(0);
        mWallColorBuffer.put(mWallColors);
        mWallColorBuffer.position(0);
        
        mWallTextureBuffer.clear();
        mWallTextureBuffer.position(0);
        mWallTextureBuffer.put(mWallTextureCoords);
        mWallTextureBuffer.position(0);
        
        //Draw buffer.
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

        gl.glColor4f(0.0f, 1.0f, 0.0f, 1.0f);
        
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, mWallVertexBuffer);
        
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, mWallColorBuffer);
        
        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextures[0]);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mWallTextureBuffer);
        
        //Draw ze walls!
        gl.glDrawArrays(GL10.GL_LINES, 0, mWallCoords.length / 2);
        
        
        gl.glBindTexture(GL10.GL_TEXTURE_2D, 0);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        //gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        
        mFloorVertexBuffer.clear();
        mFloorVertexBuffer.position(0);
        mFloorVertexBuffer.put(mFloorCoords);
        mFloorVertexBuffer.position(0);
        
        mFloorTextureBuffer.clear();
        mFloorTextureBuffer.position(0);
        mFloorTextureBuffer.put(mFloorTextureCoords);
        mFloorTextureBuffer.position(0);
       
        
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextures[1]);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mFloorTextureBuffer);
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, mFloorVertexBuffer);

        gl.glDrawArrays(GL10.GL_POINTS, 0, mNumFloorPixels);
        
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        //gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.mScreenWidth = width;
        this.mScreenHeight = height;

        mScreenWidth = 320;
        mScreenHeight = 240;
        
        gl.glLineWidth((width / mScreenWidth) * 2.0f);
        gl.glPointSize((width / mScreenWidth) * 2.0f);
        mWalls = new CollisionInfo[width];
        for(int i=0; i<mScreenWidth; ++i) {
            mWalls[i] = new CollisionInfo();
        }

        mWallCoords = new float[4 * mScreenWidth]; // four floats per coord (x1, y1) (x2, y2)
        mWallColors = new float[8 * mScreenWidth]; // eight colors per wall (r1, g1, b1, a1) (r2, g2, b2, a2)
        mWallTextureCoords = new float[4 * mScreenWidth]; // four floats per coord

        mFloorCoords = new float[ 2 * mScreenWidth * mScreenHeight];  //There are potentially number of pixels points for floors.
        mFloorColors = new float[ 2 * mScreenWidth * mScreenHeight];
        mFloorTextureCoords = new float[ 2 * mScreenWidth * mScreenHeight];
        
        mWallVertexBuffer  = ByteBuffer.allocateDirect(mScreenWidth * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mWallColorBuffer   = ByteBuffer.allocateDirect(mScreenWidth * 4 * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mWallTextureBuffer = ByteBuffer.allocateDirect(mScreenWidth * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        
        mFloorVertexBuffer  = ByteBuffer.allocateDirect(mScreenWidth * mScreenHeight * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mFloorColorBuffer   = ByteBuffer.allocateDirect(mScreenWidth * mScreenHeight * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mFloorTextureBuffer = ByteBuffer.allocateDirect(mScreenWidth * mScreenHeight * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        
        mDistanceToClipPlane = (mScreenWidth / 2) / Math.tan(mPlayerFOV/2);
        mPlayerAngleDelta = mPlayerFOV / mScreenWidth;
        
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        GLU.gluOrtho2D(gl, 0, mScreenWidth, 0, mScreenHeight);
        
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();
        
        

        
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        initPlayer();
        
        gl.glClearColor(1.0f, 0.5f, 0.5f, 1.0f);
        gl.glDisable(GL10.GL_DEPTH_TEST);
        gl.glEnable(GL10.GL_TEXTURE_2D);
        
        gl.glPixelStorei(GL10.GL_UNPACK_ALIGNMENT, 1);

        loadTextures(gl);

    }
    
    private void initPlayer() {
        mPlayerX = mTileWidth * 2.5;
        mPlayerY = mTileHeight * 2.5;
        
        mPlayerVelocityX = 0;
        mPlayerVelocityY = 0;
        
        mPlayerAngle = 0.0;
        
        mPlayerFOV = Math.PI / 3.0;
        
        mPlayerRadius = 6.0;
        

        
    }

    private void updateGame() {
        double currentAngle = normalize(mPlayerAngle - mPlayerFOV / 2);
        for(int w = 0; w < mScreenWidth; ++w) {
            GetDistanceToWall(mWalls[w], mPlayerX, mPlayerY, currentAngle);
            mWalls[w].Distance *= Math.cos(mPlayerAngle - currentAngle); //Normalize, to prevent fish-eye. stupid fish
            mWalls[w].Angle = currentAngle;
            currentAngle = normalize(currentAngle + mPlayerAngleDelta);
        }
    }
    
    private double normalize(double theta) {
        while(theta < 0) {
            theta += 2 * Math.PI;
        }
        while(theta > 2 * Math.PI) {
            theta -= 2 * Math.PI;
        }
        return theta;
    }
    
    private void GetDistanceToWall(CollisionInfo ret, double x, double y, double angle) {
        double horizontalDistance = 0.0, verticalDistance = 0.0;
        
        double dX, dY;
        double aX, aY;
        int cX, cY;
        //Horizontal
        if(angle >= 0 && angle <= Math.PI) { //ray is facing down
            aY = Math.floor(y / mTileHeight)  * mTileHeight + mTileHeight;
            dY = mTileHeight;
        } else { //ray is facing up
            aY = Math.floor(y / mTileHeight)  * mTileHeight - 1;
            dY = -mTileHeight;
        }
        aX = x + (aY - y) / Math.tan(angle);
        dX = dY / Math.tan(angle);
        
        for(;;) {
            cX = (int) Math.floor(aX / mTileWidth);
            cY = (int) Math.floor(aY / mTileHeight);
            if(cX < 0 || cX >= mMap.length || cY < 0 || cY >= mMap.length) {
                break;
            }
            //canvas.drawCircle((float)aX, (float)aY, 4.0f, paint);
            
            if(mMap[cY][cX] > 0) {
                break;
            }
            aX += dX;
            aY += dY;
        }
        horizontalDistance = dist(x, y, aX, aY);
        double horizontalPosition = aX % mTileWidth;

        //Vertical
        if(angle >= Math.PI * 0.5 && angle < Math.PI * 1.5) { //ray is facing left
            aX = Math.floor(x / mTileWidth)  * mTileWidth - 1;
            dX = -mTileWidth;
        } else { //ray is facing right
            aX = Math.floor(x / mTileWidth)  * mTileWidth + mTileWidth;
            dX = mTileWidth;
        }
        
        aY = y + (aX - x) * Math.tan(angle);
        dY = dX * Math.tan(angle);
        
        for(;;) {
            cX = (int) Math.floor(aX / mTileWidth);
            cY = (int) Math.floor(aY / mTileHeight);
            if(cX < 0 || cX >= mMap.length || cY < 0 || cY >= mMap.length) {
                break;
            }
            //canvas.drawCircle((float)aX, (float)aY, 4.0f, paint);
            
            if(mMap[cY][cX] > 0) {
                break;
            }
            aX += dX;
            aY += dY;
        }
        verticalDistance = dist(x, y, aX, aY);
        double verticalPosition = aY % mTileHeight;
        
        if(verticalDistance < horizontalDistance) {
            ret.Distance = verticalDistance;
            ret.CollisionPosition = verticalPosition / mTileHeight;
        } else {
            ret.Distance = horizontalDistance;
            ret.CollisionPosition = horizontalPosition / mTileWidth;
        }
        
    }
    private double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow((x2 - x1), 2.0) + Math.pow((y2 - y1), 2.0));
    }

    public void AddPlayerAngle(double d) {
        mPlayerAngle = normalize(mPlayerAngle +d);
    }

    public void MoveForward(double d) {
        mPlayerX += d * Math.cos(mPlayerAngle);
        mPlayerY += d * Math.sin(mPlayerAngle);
        
    }
    
}

