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

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;

/**
 * A {@link MeteringPointFactory} for creating a {@link MeteringPoint} by {@link TextureView} and
 * (x,y).
 *
 * <p>SurfaceTexture in TextureView could be cropped, scaled or rotated by
 * {@link TextureView#getTransform(Matrix)}. This factory translates the (x, y) into the sensor
 * crop region normalized (x,y) by this transform. {@link SurfaceTexture#getTransformMatrix} is
 * also used during the translation. No lens facing information is required because
 * {@link SurfaceTexture#getTransformMatrix} contains the necessary transformation corresponding
 * to the lens face of current camera ouput.
 */
public class TextureViewMeteringPointFactory extends MeteringPointFactory {
    private final TextureView mTextureView;

    public TextureViewMeteringPointFactory(@NonNull TextureView textureView) {
        mTextureView = textureView;
    }

    /**
     * Translates a (x,y) from TextureView.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected PointF convertPoint(float x, float y) {
        Matrix transform = new Matrix();
        mTextureView.getTransform(transform);

        // applying reverse of TextureView#getTransform
        Matrix inverse = new Matrix();
        transform.invert(inverse);
        float[] pt = new float[]{x, y};
        inverse.mapPoints(pt);

        // get SurfaceTexture#getTransformMatrix
        float[] surfaceTextureMat = new float[16];
        mTextureView.getSurfaceTexture().getTransformMatrix(surfaceTextureMat);

        // convert SurfaceTexture#getTransformMatrix(4x4 column major 3D matrix) to
        // android.graphics.Matrix(3x3 row major 2D matrix)
        Matrix surfaceTextureTransform = glMatrixToGraphicsMatrix(surfaceTextureMat);

        float[] pt2 = new float[2];
        // convert to texture coordinates first.
        pt2[0] = pt[0] / mTextureView.getWidth();
        pt2[1] = (mTextureView.getHeight() - pt[1]) / mTextureView.getHeight();
        surfaceTextureTransform.mapPoints(pt2);

        return new PointF(pt2[0], pt2[1]);
    }

    private Matrix glMatrixToGraphicsMatrix(float[] glMatrix) {
        float[] convert = new float[9];
        convert[0] = glMatrix[0];
        convert[1] = glMatrix[4];
        convert[2] = glMatrix[12];
        convert[3] = glMatrix[1];
        convert[4] = glMatrix[5];
        convert[5] = glMatrix[13];
        convert[6] = glMatrix[3];
        convert[7] = glMatrix[7];
        convert[8] = glMatrix[15];
        Matrix graphicsMatrix = new Matrix();
        graphicsMatrix.setValues(convert);
        return graphicsMatrix;
    }
}
