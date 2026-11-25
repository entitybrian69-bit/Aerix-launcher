package net.kdt.pojavlaunch.render;

import static net.kdt.pojavlaunch.CallbackBridge.windowHeight;
import static net.kdt.pojavlaunch.CallbackBridge.windowWidth;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;

public class TextureViewSurfaceProvider implements SurfaceProvider {
    private TextureView mTextureView;
    private SurfaceCallback mCallback;
    private Surface mActiveSurface;

    @Override
    public View create(Context context, SurfaceCallback callback) {
        mCallback = callback;
        mTextureView = new TextureView(context);
        mTextureView.setOpaque(true);
        mTextureView.setAlpha(1.0f);
        mTextureView.setSurfaceTextureListener(new CallbackAdapter());
        return mTextureView;
    }

    @Override
    public void updateSize() {
        if (mTextureView == null || windowWidth <= 0 || windowHeight <= 0) return;
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if(surfaceTexture != null) {
            surfaceTexture.setDefaultBufferSize(windowWidth, windowHeight);
            Tools.runOnUiThread(() -> {
                if (mActiveSurface != null && mActiveSurface.isValid()) {
                    mCallback.onSurfaceResized();
                }
            });
        }
    }

    private class CallbackAdapter implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            if(windowWidth > 0 && windowHeight > 0)
                surfaceTexture.setDefaultBufferSize(windowWidth, windowHeight);
            if (mActiveSurface != null) {
                mCallback.onSurfaceDestroyed();
                mActiveSurface.release();
                mActiveSurface = null;
            }
            Surface surface = new Surface(surfaceTexture);
            mActiveSurface = surface;
            if (surface.isValid()) {
                mCallback.onSurfaceAvailable(surface);
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            mCallback.onSurfaceDestroyed();
            if (mActiveSurface != null) {
                mActiveSurface.release();
                mActiveSurface = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            if (mActiveSurface != null && mActiveSurface.isValid()) {
                mCallback.onSurfaceResized();
            }
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    }
}
