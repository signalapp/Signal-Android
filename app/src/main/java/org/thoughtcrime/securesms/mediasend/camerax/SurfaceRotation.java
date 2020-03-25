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

import android.view.Surface;

final class SurfaceRotation {
    /**
     * Get the int value degree of a rotation from the {@link Surface} constants.
     *
     * <p>Valid values for the relative rotation are {@link Surface#ROTATION_0}, {@link
     *      * Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
     */
    static int rotationDegreesFromSurfaceRotation(int rotationConstant) {
        switch (rotationConstant) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported surface rotation constant: " + rotationConstant);
        }
    }

    /** Prevents construction */
    private SurfaceRotation() {}
}
