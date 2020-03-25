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

import android.content.Context;
import android.content.res.TypedArray;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.Preview;

import org.thoughtcrime.securesms.R;

import java.util.concurrent.Executor;

/**
 * Custom View that displays camera feed for CameraX's Preview use case.
 *
 * <p> This class manages the Surface lifecycle, as well as the preview aspect ratio and
 * orientation. Internally, it uses either a {@link android.view.TextureView} or
 * {@link android.view.SurfaceView} to display the camera feed.
 */
// Begin Signal Custom Code Block
@RequiresApi(21)
// End Signal Custom Code Block
public class PreviewView extends FrameLayout {

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    Implementation mImplementation;

    private ImplementationMode mImplementationMode;

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }
        @Override
        public void onDisplayChanged(int displayId) {
            mImplementation.onDisplayChanged();
        }
    };

    public PreviewView(@NonNull Context context) {
        this(context, null);
    }

    public PreviewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreviewView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PreviewView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.PreviewView, defStyleAttr, defStyleRes);

        try {
            final int implementationModeId = attributes.getInteger(
                    R.styleable.PreviewView_implementationMode,
                    ImplementationMode.TEXTURE_VIEW.getId());
            mImplementationMode = ImplementationMode.fromId(implementationModeId);
        } finally {
            attributes.recycle();
        }
        setUp();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final DisplayManager displayManager =
                (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(mDisplayListener, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        final DisplayManager displayManager =
                (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(mDisplayListener);
        }
    }

    private void setUp() {
        removeAllViews();
        switch (mImplementationMode) {
            case SURFACE_VIEW:
                mImplementation = new SurfaceViewImplementation();
                break;
            case TEXTURE_VIEW:
                mImplementation = new TextureViewImplementation();
                break;
            default:
                throw new IllegalStateException(
                        "Unsupported implementation mode " + mImplementationMode);
        }
        mImplementation.init(this);
    }

    /**
     * Specifies the {@link ImplementationMode} to use for the preview.
     *
     * @param implementationMode <code>SURFACE_VIEW</code> if a {@link android.view.SurfaceView}
     *                           should be used to display the camera feed, or
     *                           <code>TEXTURE_VIEW</code> to use a {@link android.view.TextureView}
     */
    public void setImplementationMode(@NonNull final ImplementationMode implementationMode) {
        mImplementationMode = implementationMode;
        setUp();
    }

    /**
     * Returns the implementation mode of the {@link PreviewView}.
     *
     * @return <code>SURFACE_VIEW</code> if the {@link PreviewView} is internally using a
     * {@link android.view.SurfaceView} to display the camera feed, or <code>TEXTURE_VIEW</code>
     * if a {@link android.view.TextureView} is being used.
     */
    @NonNull
    public ImplementationMode getImplementationMode() {
        return mImplementationMode;
    }

    /**
     * Gets the {@link Preview.SurfaceProvider} to be used with
     * {@link Preview#setSurfaceProvider(Executor, Preview.SurfaceProvider)}.
     */
    @NonNull
    public Preview.SurfaceProvider getPreviewSurfaceProvider() {
        return mImplementation.getSurfaceProvider();
    }

    /**
     * Implements this interface to create PreviewView implementation.
     */
    interface Implementation {

        /**
         * Initializes the parent view with sub views.
         *
         * @param parent the containing parent {@link FrameLayout}.
         */
        void init(@NonNull FrameLayout parent);

        /**
         * Gets the {@link Preview.SurfaceProvider} to be used with {@link Preview}.
         */
        @NonNull
        Preview.SurfaceProvider getSurfaceProvider();

        /**
         *  Notifies that the display properties have changed.
         *
         *  <p>Implementation might need to adjust transform by latest display properties such as
         *  display orientation in order to show the preview correctly.
         */
        void onDisplayChanged();
    }

    /**
     * The implementation mode of a {@link PreviewView}
     *
     * <p>Specifies how the Preview surface will be implemented internally: Using a
     * {@link android.view.SurfaceView} or a {@link android.view.TextureView} (which is the default)
     * </p>
     */
    public enum ImplementationMode {
        /** Use a {@link android.view.SurfaceView} for the preview */
        SURFACE_VIEW(0),

        /** Use a {@link android.view.TextureView} for the preview */
        TEXTURE_VIEW(1);

        private final int mId;

        ImplementationMode(final int id) {
            mId = id;
        }

        public int getId() {
            return mId;
        }

        static ImplementationMode fromId(final int id) {
            for (final ImplementationMode mode : values()) {
                if (mode.mId == id) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported implementation mode " + id);
        }
    }

    /** Options for scaling the preview vis-à-vis its container {@link PreviewView}. */
    public enum ScaleType {
        /**
         * Scale the preview, maintaining the source aspect ratio, so it fills the entire
         * {@link PreviewView}, and align it to the top left corner of the view.
         * This may cause the preview to be cropped if the camera preview aspect ratio does not
         * match that of its container {@link PreviewView}.
         */
        FILL_START,
        /**
         * Scale the preview, maintaining the source aspect ratio, so it fills the entire
         * {@link PreviewView}, and center it inside the view.
         * This may cause the preview to be cropped if the camera preview aspect ratio does not
         * match that of its container {@link PreviewView}.
         */
        FILL_CENTER,
        /**
         * Scale the preview, maintaining the source aspect ratio, so it fills the entire
         * {@link PreviewView}, and align it to the bottom right corner of the view.
         * This may cause the preview to be cropped if the camera preview aspect ratio does not
         * match that of its container {@link PreviewView}.
         */
        FILL_END,
        /**
         * Scale the preview, maintaining the source aspect ratio, so it is entirely contained
         * within the {@link PreviewView}, and align it to the top left corner of the view.
         * Both dimensions of the preview will be equal or less than the corresponding dimensions
         * of its container {@link PreviewView}.
         */
        FIT_START,
        /**
         * Scale the preview, maintaining the source aspect ratio, so it is entirely contained
         * within the {@link PreviewView}, and center it inside the view.
         * Both dimensions of the preview will be equal or less than the corresponding dimensions
         * of its container {@link PreviewView}.
         */
        FIT_CENTER,
        /**
         * Scale the preview, maintaining the source aspect ratio, so it is entirely contained
         * within the {@link PreviewView}, and align it to the bottom right corner of the view.
         * Both dimensions of the preview will be equal or less than the corresponding dimensions
         * of its container {@link PreviewView}.
         */
        FIT_END
    }
}

