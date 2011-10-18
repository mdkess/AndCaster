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
    
    
    private int mTextures[];


    private CollisionInfo[] mWalls;
    private float[] mWallCoords;
    private float[] mWallColors;
    private float[] mTextureCoords;
    
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mColorBuffer;
    private FloatBuffer mTextureBuffer;
    
    private Context mContext;
    
    public RaycasterGLRenderer(Context context) {
        mContext = context;
    }

    public void loadTextures(GL10 gl) {
        mTextures = new int[1];
        
        gl.glGenTextures(1, mTextures, 0);
        
        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextures[0]);
        // Create Nearest Filtered Texture
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);

        Bitmap texture = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.wall);
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture, 0);
    }
    
    @Override
    public void onDrawFrame(GL10 gl) {
        updateGame();
        
        //Calculate wall coords
        for(int w=0; w < mWalls.length; ++w) {
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
            
            mTextureCoords[4 * w + 0] = (float) mWalls[w].CollisionPosition;
            mTextureCoords[4 * w + 1] = 1.0f;
            mTextureCoords[4 * w + 2] = (float) mWalls[w].CollisionPosition;
            mTextureCoords[4 * w + 3] = 0.0f;
        }
        
        //Put wall coords into vertex buffer
        mVertexBuffer.clear();
        mVertexBuffer.position(0);
        mVertexBuffer.put(mWallCoords);
        mVertexBuffer.position(0);
        
        mColorBuffer.clear();
        mColorBuffer.position(0);
        mColorBuffer.put(mWallColors);
        mColorBuffer.position(0);
        
        mTextureBuffer.clear();
        mTextureBuffer.position(0);
        mTextureBuffer.put(mTextureCoords);
        mTextureBuffer.position(0);
        
        //Draw buffer.
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

        gl.glColor4f(0.0f, 1.0f, 0.0f, 1.0f);
        
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, mVertexBuffer);
        
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, mColorBuffer);
        
        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextures[0]);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTextureBuffer);
        
        gl.glDrawArrays(GL10.GL_LINES, 0, mWallCoords.length / 2);
    
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.mScreenWidth = width;
        this.mScreenHeight = height;

        mWalls = new CollisionInfo[width];
        for(int i=0; i<width; ++i) {
            mWalls[i] = new CollisionInfo();
        }

        mWallCoords = new float[4 * width]; // four floats per coord (x1, y1) (x2, y2)
        mWallColors = new float[8 * width]; // eight colors per wall (r1, g1, b1, a1) (r2, g2, b2, a2)
        mTextureCoords = new float[4 * width]; // four floats per coord

        mVertexBuffer  = ByteBuffer.allocateDirect(width * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mColorBuffer   = ByteBuffer.allocateDirect(width * 4 * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTextureBuffer = ByteBuffer.allocateDirect(width * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        
        mDistanceToClipPlane = (width / 2) / Math.tan(mPlayerFOV/2);
        mPlayerAngleDelta = mPlayerFOV / width;
        
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        GLU.gluOrtho2D(gl, 0, width, 0, height);
        
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

