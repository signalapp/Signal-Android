/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thoughtcrime.securesms.mediasend.camerax;

import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;
import androidx.core.content.ContextCompat;

/**
 * The SurfaceView implementation for {@link PreviewView}.
 */
@RequiresApi(21)
final class SurfaceViewImplementation implements PreviewView.Implementation {

    private static final String TAG = "SurfaceViewPreviewView";

    // Synthetic Accessor
    @SuppressWarnings("WeakerAccess")
    TransformableSurfaceView mSurfaceView;

    // Synthetic Accessor
    @SuppressWarnings("WeakerAccess")
    final SurfaceRequestCallback mSurfaceRequestCallback =
            new SurfaceRequestCallback();

    private Preview.SurfaceProvider mSurfaceProvider =
            new Preview.SurfaceProvider() {
                @Override
                public void onSurfaceRequested(@NonNull SurfaceRequest surfaceRequest) {
                    mSurfaceView.post(
                            () -> mSurfaceRequestCallback.setSurfaceRequest(surfaceRequest));
                }
            };

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(@NonNull FrameLayout parent) {
        mSurfaceView = new TransformableSurfaceView(parent.getContext());
        mSurfaceView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        parent.addView(mSurfaceView);
        mSurfaceView.getHolder().addCallback(mSurfaceRequestCallback);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Preview.SurfaceProvider getSurfaceProvider() {
        return mSurfaceProvider;
    }

    @Override
    public void onDisplayChanged() {

    }

    /**
     * The {@link SurfaceHolder.Callback} on mSurfaceView.
     *
     * <p> SurfaceView creates Surface on its own before we can do anything. This class makes
     * sure only the Surface with correct size will be returned to Preview.
     */
    class SurfaceRequestCallback implements SurfaceHolder.Callback {

        // Target Surface size. Only complete the SurfaceRequest when the size of the Surface
        // matches this value.
        // Guarded by UI thread.
        @Nullable
        private Size mTargetSize;

        // SurfaceRequest to set when the target size is met.
        // Guarded by UI thread.
        @Nullable
        private SurfaceRequest mSurfaceRequest;

        // The cached size of the current Surface.
        // Guarded by UI thread.
        @Nullable
        private Size mCurrentSurfaceSize;

        /**
         * Sets the completer and the size. The completer will only be set if the current size of
         * the Surface matches the target size.
         */
        @UiThread
        void setSurfaceRequest(@NonNull SurfaceRequest surfaceRequest) {
            cancelPreviousRequest();
            mSurfaceRequest = surfaceRequest;
            Size targetSize = surfaceRequest.getResolution();
            mTargetSize = targetSize;
            if (!tryToComplete()) {
                // The current size is incorrect. Wait for it to change.
                Log.d(TAG, "Wait for new Surface creation.");
                mSurfaceView.getHolder().setFixedSize(targetSize.getWidth(),
                        targetSize.getHeight());
            }
        }

        /**
         * Sets the completer if size matches.
         *
         * @return true if the completer is set.
         */
        @UiThread
        private boolean tryToComplete() {
            Surface surface = mSurfaceView.getHolder().getSurface();
            if (mSurfaceRequest != null && mTargetSize != null && mTargetSize.equals(
                    mCurrentSurfaceSize)) {
                Log.d(TAG, "Surface set on Preview.");
                mSurfaceRequest.provideSurface(surface,
                        ContextCompat.getMainExecutor(mSurfaceView.getContext()),
                        (result) -> Log.d(TAG, "Safe to release surface."));
                mSurfaceRequest = null;
                mTargetSize = null;
                return true;
            }
            return false;
        }

        @UiThread
        private void cancelPreviousRequest() {
            if (mSurfaceRequest != null) {
                Log.d(TAG, "Request canceled: " + mSurfaceRequest);
                mSurfaceRequest.willNotProvideSurface();
                mSurfaceRequest = null;
            }
            mTargetSize = null;
        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "Surface created.");
            // No-op. Handling surfaceChanged() is enough because it's always called afterwards.
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
            Log.d(TAG, "Surface changed. Size: " + width + "x" + height);
            mCurrentSurfaceSize = new Size(width, height);
            tryToComplete();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "Surface destroyed.");
            mCurrentSurfaceSize = null;
            cancelPreviousRequest();
        }
    }
}
