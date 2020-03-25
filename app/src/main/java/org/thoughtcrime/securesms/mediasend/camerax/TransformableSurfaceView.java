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
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * A subclass of {@link SurfaceView} that supports translation and scaling transformations.
 */
// Begin Signal Custom Code Block
@RequiresApi(21)
// End Signal Custom Code Block
final class TransformableSurfaceView extends SurfaceView {

    private RectF mOverriddenLayoutRect;

    TransformableSurfaceView(@NonNull Context context) {
        super(context);
    }

    TransformableSurfaceView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    TransformableSurfaceView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    TransformableSurfaceView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mOverriddenLayoutRect == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            setMeasuredDimension((int) mOverriddenLayoutRect.width(),
                    (int) mOverriddenLayoutRect.height());
        }
    }

    /**
     * Sets the transform to associate with this surface view. Only translation and scaling are
     * supported. If a rotated transformation is passed in, an exception is thrown.
     *
     * @param transform The transform to apply to the content of this view.
     */
    void setTransform(final Matrix transform) {
        if (hasRotation(transform)) {
            throw new IllegalArgumentException("TransformableSurfaceView does not support "
                    + "rotation transformations.");
        }

        final RectF rect = new RectF(getLeft(), getTop(), getRight(), getBottom());
        transform.mapRect(rect);
        overrideLayout(rect);
    }

    private boolean hasRotation(final Matrix matrix) {
        final float[] values = new float[9];
        matrix.getValues(values);

        /*
          A translation matrix can be represented as:
          (1  0  transX)
          (0  1  transX)
          (0  0  1)

          A rotation Matrix of ψ degrees can be represented as:
          (cosψ  -sinψ  0)
          (sinψ  cosψ   0)
          (0     0      1)

          A scale matrix can be represented as:
          (scaleX  0       0)
          (0       scaleY  0)
          (0       0       0)

          Meaning a transformed matrix can be represented as:
          (scaleX * cosψ    -scaleX * sinψ    transX)
          (scaleY * sinψ    scaleY * cosψ     transY)
          (0                0                 1)

          Using the following 2 equalities:
          scaleX * cosψ = matrix[0][0]
          -scaleX * sinψ = matrix[0][1]

          The following is deduced:
          -tanψ = matrix[0][1] / matrix[0][0]

          Or:
          ψ = -arctan(matrix[0][1] / matrix[0][0])
         */
        final double angle = -Math.atan2(values[Matrix.MSKEW_X], values[Matrix.MSCALE_X]);

        return Math.round(angle * (180 / Math.PI)) != 0;
    }

    private void overrideLayout(final RectF overriddenLayoutRect) {
        mOverriddenLayoutRect = overriddenLayoutRect;
        setX(overriddenLayoutRect.left);
        setY(overriddenLayoutRect.top);
        requestLayout();
    }
}
