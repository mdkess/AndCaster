package ca.kess.games;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;


public class RaycasterGLRenderer implements Renderer {
    
    //The map
    int[][] map = {
            { 1, 1, 1, 1, 1, 1, 1, 1, },
            { 1, 0, 0, 0, 0, 0, 0, 1, },
            { 1, 0, 0, 0, 0, 0, 0, 1, },
            { 1, 0, 0, 0, 0, 0, 0, 1, },
            { 1, 0, 0, 0, 0, 0, 1, 1, },
            { 1, 0, 0, 1, 1, 0, 0, 1, },
            { 1, 0, 0, 0, 0, 1, 0, 1, },
            { 1, 1, 1, 1, 1, 1, 1, 1, }
    };
    
    int width, height;
    
    double pX, pY, pVX, pVY;
    double pA, pFOV;
    double pR;
    
    double distanceToPlane;
    double angleDelta;
    
    final int tileWidth = 64;
    final int tileHeight = 64;
    final int tileSize = 64;
    
    
    


    private CollisionInfo[] mWalls;
    private float[] mWallCoords;
    private float[] mWallColors;
    
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mColorBuffer;
    
    @Override
    public void onDrawFrame(GL10 gl) {
        updateGame();
        
        //Calculate wall coords
        for(int w=0; w < mWalls.length; ++w) {
            double wallHeight = (tileSize / mWalls[w].Distance) * distanceToPlane;
            mWallCoords[4 * w + 0] = w;
            mWallCoords[4 * w + 1] = (float) ((height - wallHeight) / 2.0);
            mWallCoords[4 * w + 2] = w;
            mWallCoords[4 * w + 3] = (float) ((height + wallHeight) / 2.0);
            
            float intensity = (float) (1.0f - (mWalls[w].Distance/512.0f));
            
            mWallColors[8 * w + 0] = intensity;
            mWallColors[8 * w + 1] = intensity;
            mWallColors[8 * w + 2] = intensity;
            mWallColors[8 * w + 3] = 1.0f;
            
            mWallColors[8 * w + 4] = intensity;
            mWallColors[8 * w + 5] = intensity;
            mWallColors[8 * w + 6] = intensity;
            mWallColors[8 * w + 7] = 1.0f;
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
        
        //Draw buffer.
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

        gl.glColor4f(0.0f, 1.0f, 0.0f, 1.0f);
        {
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
            gl.glVertexPointer(2, GL10.GL_FLOAT, 0, mVertexBuffer);
            gl.glColorPointer(4, GL10.GL_FLOAT, 0, mColorBuffer);
            gl.glDrawArrays(GL10.GL_LINES, 0, mWallCoords.length / 2);
        }
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.width = width;
        this.height = height;

        mWalls = new CollisionInfo[width];
        for(int i=0; i<width; ++i) {
            mWalls[i] = new CollisionInfo();
        }

        mWallCoords = new float[4 * width]; // four floats per coord (x1, y1) (x2, y2)
        mWallColors = new float[8 * width]; // eight colors per wall (r1, g1, b1, a1) (r2, g2, b2, a2)

        mVertexBuffer = ByteBuffer.allocateDirect(width * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mColorBuffer = ByteBuffer.allocateDirect(width * 4 * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
        
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        GLU.gluOrtho2D(gl, 0, width, 0, height);
        
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();
        
        
        distanceToPlane = (width / 2) / Math.tan(pFOV/2);
        angleDelta = pFOV / width;
        
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        initPlayer();
        
        gl.glClearColor(1.0f, 0.5f, 0.5f, 1.0f);
        gl.glDisable(GL10.GL_DEPTH_TEST);
        

        

    }
    
    private void initPlayer() {
        pX = tileWidth * 2.5;
        pY = tileHeight * 2.5;
        
        pVX = 0;
        pVY = 0;
        
        pA = 0.0;
        
        pFOV = Math.PI / 3.0;
        
        pR = 6.0;
        

        
    }

    private void updateGame() {
        double currentAngle = normalize(pA - pFOV / 2);
        for(int w = 0; w < width; ++w) {
            double dist = GetDistanceToWall(pX, pY, currentAngle) * Math.cos(pA - currentAngle);
            mWalls[w].Distance = dist;
            currentAngle = normalize(currentAngle + angleDelta);
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
    
    private double GetDistanceToWall(double x, double y, double angle) {
        double horizontalDistance = 0.0, verticalDistance = 0.0;
        
        double dX, dY;
        double aX, aY;
        int cX, cY;
        //Horizontal
        if(angle >= 0 && angle <= Math.PI) { //ray is facing down
            aY = Math.floor(y / tileHeight)  * tileHeight + tileHeight;
            dY = tileHeight;
        } else { //ray is facing up
            aY = Math.floor(y / tileHeight)  * tileHeight - 1;
            dY = -tileHeight;
        }
        aX = x + (aY - y) / Math.tan(angle);
        dX = dY / Math.tan(angle);
        
        for(;;) {
            cX = (int) Math.floor(aX / tileWidth);
            cY = (int) Math.floor(aY / tileHeight);
            if(cX < 0 || cX >= map.length || cY < 0 || cY >= map.length) {
                break;
            }
            //canvas.drawCircle((float)aX, (float)aY, 4.0f, paint);
            
            if(map[cY][cX] > 0) {
                break;
            }
            aX += dX;
            aY += dY;
        }
        horizontalDistance = dist(x, y, aX, aY);

        //Vertical
        if(angle >= Math.PI * 0.5 && angle < Math.PI * 1.5) { //ray is facing left
            aX = Math.floor(x / tileWidth)  * tileWidth - 1;
            dX = -tileWidth;
        } else { //ray is facing right
            aX = Math.floor(x / tileWidth)  * tileWidth + tileWidth;
            dX = tileWidth;
        }
        
        aY = y + (aX - x) * Math.tan(angle);
        dY = dX * Math.tan(angle);
        
        for(;;) {
            cX = (int) Math.floor(aX / tileWidth);
            cY = (int) Math.floor(aY / tileHeight);
            if(cX < 0 || cX >= map.length || cY < 0 || cY >= map.length) {
                break;
            }
            //canvas.drawCircle((float)aX, (float)aY, 4.0f, paint);
            
            if(map[cY][cX] > 0) {
                break;
            }
            aX += dX;
            aY += dY;
        }
        verticalDistance = dist(x, y, aX, aY);
        
        return Math.min(horizontalDistance, verticalDistance);
    }
    private double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow((x2 - x1), 2.0) + Math.pow((y2 - y1), 2.0));
    }

    public void AddPlayerAngle(double d) {
        pA = normalize(pA +d);
    }

    public void MoveForward(double d) {
        pX += d * Math.cos(pA);
        pY += d * Math.sin(pA);
        
    }
    
}
