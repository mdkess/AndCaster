package ca.kess.games;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

public class RaycasterGLActivity extends Activity {

    private GLSurfaceView mGLSurfaceView;
    private RaycasterGLRenderer mRenderer;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mGLSurfaceView = new GLSurfaceView(this);
        mRenderer = new RaycasterGLRenderer();
        mGLSurfaceView.setRenderer(mRenderer);
        //mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        
        setContentView(mGLSurfaceView);
    }

    float lastX;
    float lastY;
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
        } else if(event.getAction() == MotionEvent.ACTION_MOVE) {
            Log.v("touch", event.getX() + " " + event.getY());
            float deltaX = event.getX() - lastX;
            float deltaY = event.getY() - lastY;
            mRenderer.AddPlayerAngle(deltaX * 0.0001);
            mRenderer.MoveForward(-deltaY * 0.01);
        }
        return super.onTouchEvent(event);
        
    }

}