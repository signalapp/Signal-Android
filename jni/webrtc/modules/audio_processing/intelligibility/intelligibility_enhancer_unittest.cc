/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <math.h>
#include <stdlib.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/array_view.h"
#include "webrtc/base/arraysize.h"
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"
#include "webrtc/modules/audio_processing/intelligibility/intelligibility_enhancer.h"
#include "webrtc/modules/audio_processing/noise_suppression_impl.h"
#include "webrtc/modules/audio_processing/test/audio_buffer_tools.h"
#include "webrtc/modules/audio_processing/test/bitexactness_tools.h"

namespace webrtc {

namespace {

// Target output for ERB create test. Generated with matlab.
const float kTestCenterFreqs[] = {
    14.5213f, 29.735f,  45.6781f, 62.3884f, 79.9058f, 98.2691f, 117.521f,
    137.708f, 158.879f, 181.084f, 204.378f, 228.816f, 254.459f, 281.371f,
    309.618f, 339.273f, 370.411f, 403.115f, 437.469f, 473.564f, 511.497f,
    551.371f, 593.293f, 637.386f, 683.77f,  732.581f, 783.96f,  838.06f,
    895.046f, 955.09f,  1018.38f, 1085.13f, 1155.54f, 1229.85f, 1308.32f,
    1391.22f, 1478.83f, 1571.5f,  1669.55f, 1773.37f, 1883.37f, 2000.f};
const float kTestFilterBank[][33] = {
    {0.2f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.2f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.2f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.2f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.2f, 0.25f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,  0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.25f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.25f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.25f, 0.25f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,   0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,   0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.25f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f,   0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.25f, 0.142857f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f,   0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f,   0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.25f, 0.285714f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f,   0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f,   0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.f, 0.285714f, 0.142857f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.f, 0.285714f, 0.285714f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.f, 0.f, 0.285714f, 0.142857f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.f, 0.f, 0.285714f, 0.285714f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.285714f, 0.142857f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.285714f, 0.285714f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.285714f, 0.142857f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f, 0.f, 0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.285714f, 0.285714f, 0.157895f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f, 0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.285714f, 0.210526f, 0.117647f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.285714f, 0.315789f, 0.176471f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.315789f, 0.352941f, 0.142857f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f},
    {0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.352941f, 0.285714f,
     0.157895f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,
     0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f},
    {0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.285714f,
     0.210526f, 0.111111f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,       0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f, 0.f,       0.f,       0.f,       0.f,       0.f, 0.f, 0.f, 0.f,
     0.f, 0.285714f, 0.315789f, 0.222222f, 0.111111f, 0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,       0.f,       0.f,       0.f,       0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,       0.f,       0.f,       0.f,       0.f},
    {0.f, 0.f, 0.f,       0.f,       0.f,       0.f,       0.f, 0.f, 0.f,
     0.f, 0.f, 0.315789f, 0.333333f, 0.222222f, 0.111111f, 0.f, 0.f, 0.f,
     0.f, 0.f, 0.f,       0.f,       0.f,       0.f,       0.f, 0.f, 0.f,
     0.f, 0.f, 0.f,       0.f,       0.f,       0.f},
    {0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,       0.f, 0.f,
     0.f, 0.f, 0.f, 0.333333f, 0.333333f, 0.222222f, 0.111111f, 0.f, 0.f,
     0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,       0.f, 0.f,
     0.f, 0.f, 0.f, 0.f,       0.f,       0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.333333f, 0.333333f, 0.222222f, 0.111111f, 0.f,
     0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f,       0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.333333f, 0.333333f, 0.222222f, 0.111111f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,
     0.f,       0.f, 0.f, 0.f, 0.f, 0.f, 0.333333f, 0.333333f, 0.222222f,
     0.108108f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,
     0.f,       0.f, 0.f, 0.f, 0.f, 0.f},
    {0.f,       0.f,       0.f,        0.f, 0.f, 0.f, 0.f, 0.f,       0.f,
     0.f,       0.f,       0.f,        0.f, 0.f, 0.f, 0.f, 0.333333f, 0.333333f,
     0.243243f, 0.153846f, 0.0833333f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,
     0.f,       0.f,       0.f,        0.f, 0.f, 0.f},
    {0.f,       0.f,       0.f,       0.f,        0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,       0.f,       0.f,       0.f,        0.f, 0.f, 0.f, 0.f, 0.333333f,
     0.324324f, 0.230769f, 0.166667f, 0.0909091f, 0.f, 0.f, 0.f, 0.f, 0.f,
     0.f,       0.f,       0.f,       0.f,        0.f, 0.f},
    {0.f,       0.f,       0.f,   0.f,       0.f,        0.f, 0.f, 0.f, 0.f,
     0.f,       0.f,       0.f,   0.f,       0.f,        0.f, 0.f, 0.f, 0.f,
     0.324324f, 0.307692f, 0.25f, 0.181818f, 0.0833333f, 0.f, 0.f, 0.f, 0.f,
     0.f,       0.f,       0.f,   0.f,       0.f,        0.f},
    {0.f,       0.f,   0.f,       0.f,        0.f, 0.f,       0.f,
     0.f,       0.f,   0.f,       0.f,        0.f, 0.f,       0.f,
     0.f,       0.f,   0.f,       0.f,        0.f, 0.307692f, 0.333333f,
     0.363636f, 0.25f, 0.151515f, 0.0793651f, 0.f, 0.f,       0.f,
     0.f,       0.f,   0.f,       0.f,        0.f},
    {0.f,       0.f,       0.f,        0.f,       0.f,       0.f,
     0.f,       0.f,       0.f,        0.f,       0.f,       0.f,
     0.f,       0.f,       0.f,        0.f,       0.f,       0.f,
     0.f,       0.f,       0.166667f,  0.363636f, 0.333333f, 0.242424f,
     0.190476f, 0.133333f, 0.0689655f, 0.f,       0.f,       0.f,
     0.f,       0.f,       0.f},
    {0.f,        0.f, 0.f, 0.f, 0.f,       0.f,      0.f,       0.f,  0.f,
     0.f,        0.f, 0.f, 0.f, 0.f,       0.f,      0.f,       0.f,  0.f,
     0.f,        0.f, 0.f, 0.f, 0.333333f, 0.30303f, 0.253968f, 0.2f, 0.137931f,
     0.0714286f, 0.f, 0.f, 0.f, 0.f,       0.f},
    {0.f,    0.f,        0.f,      0.f,      0.f,       0.f,       0.f,
     0.f,    0.f,        0.f,      0.f,      0.f,       0.f,       0.f,
     0.f,    0.f,        0.f,      0.f,      0.f,       0.f,       0.f,
     0.f,    0.f,        0.30303f, 0.31746f, 0.333333f, 0.275862f, 0.214286f,
     0.125f, 0.0655738f, 0.f,      0.f,      0.f},
    {0.f,   0.f,       0.f,       0.f,        0.f,       0.f,       0.f,
     0.f,   0.f,       0.f,       0.f,        0.f,       0.f,       0.f,
     0.f,   0.f,       0.f,       0.f,        0.f,       0.f,       0.f,
     0.f,   0.f,       0.f,       0.15873f,   0.333333f, 0.344828f, 0.357143f,
     0.25f, 0.196721f, 0.137931f, 0.0816327f, 0.f},
    {0.f,     0.f,       0.f,       0.f,       0.f, 0.f,       0.f,
     0.f,     0.f,       0.f,       0.f,       0.f, 0.f,       0.f,
     0.f,     0.f,       0.f,       0.f,       0.f, 0.f,       0.f,
     0.f,     0.f,       0.f,       0.f,       0.f, 0.172414f, 0.357143f,
     0.3125f, 0.245902f, 0.172414f, 0.102041f, 0.f},
    {0.f, 0.f,     0.f,       0.f,       0.f,       0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,     0.f,       0.f,       0.f,       0.f, 0.f, 0.f, 0.f,
     0.f, 0.f,     0.f,       0.f,       0.f,       0.f, 0.f, 0.f, 0.f,
     0.f, 0.3125f, 0.327869f, 0.344828f, 0.204082f, 0.f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,       0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.163934f, 0.344828f, 0.408163f, 0.5f},
    {0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,       0.f,
     0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.204082f, 0.5f}};
static_assert(arraysize(kTestCenterFreqs) == arraysize(kTestFilterBank),
              "Test filterbank badly initialized.");

// Target output for gain solving test. Generated with matlab.
const size_t kTestStartFreq = 12;  // Lowest integral frequency for ERBs.
const float kTestZeroVar = 1.f;
const float kTestNonZeroVarLambdaTop[] = {
    1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 0.f, 0.f,
    0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
    0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f};
static_assert(arraysize(kTestCenterFreqs) ==
                  arraysize(kTestNonZeroVarLambdaTop),
              "Power test data badly initialized.");
const float kMaxTestError = 0.005f;

// Enhancer initialization parameters.
const int kSamples = 1000;
const int kSampleRate = 4000;
const int kNumChannels = 1;
const int kFragmentSize = kSampleRate / 100;
const size_t kNumNoiseBins = 129;

// Number of frames to process in the bitexactness tests.
const size_t kNumFramesToProcess = 1000;

int IntelligibilityEnhancerSampleRate(int sample_rate_hz) {
  return (sample_rate_hz > AudioProcessing::kSampleRate16kHz
              ? AudioProcessing::kSampleRate16kHz
              : sample_rate_hz);
}

// Process one frame of data and produce the output.
void ProcessOneFrame(int sample_rate_hz,
                     AudioBuffer* render_audio_buffer,
                     AudioBuffer* capture_audio_buffer,
                     NoiseSuppressionImpl* noise_suppressor,
                     IntelligibilityEnhancer* intelligibility_enhancer) {
  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    render_audio_buffer->SplitIntoFrequencyBands();
    capture_audio_buffer->SplitIntoFrequencyBands();
  }

  intelligibility_enhancer->ProcessRenderAudio(
      render_audio_buffer->split_channels_f(kBand0To8kHz),
      IntelligibilityEnhancerSampleRate(sample_rate_hz),
      render_audio_buffer->num_channels());

  noise_suppressor->AnalyzeCaptureAudio(capture_audio_buffer);
  noise_suppressor->ProcessCaptureAudio(capture_audio_buffer);

  intelligibility_enhancer->SetCaptureNoiseEstimate(
      noise_suppressor->NoiseEstimate(), 0);

  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    render_audio_buffer->MergeFrequencyBands();
  }
}

// Processes a specified amount of frames, verifies the results and reports
// any errors.
void RunBitexactnessTest(int sample_rate_hz,
                         size_t num_channels,
                         rtc::ArrayView<const float> output_reference) {
  const StreamConfig render_config(sample_rate_hz, num_channels, false);
  AudioBuffer render_buffer(
      render_config.num_frames(), render_config.num_channels(),
      render_config.num_frames(), render_config.num_channels(),
      render_config.num_frames());
  test::InputAudioFile render_file(
      test::GetApmRenderTestVectorFileName(sample_rate_hz));
  std::vector<float> render_input(render_buffer.num_frames() *
                                  render_buffer.num_channels());

  const StreamConfig capture_config(sample_rate_hz, num_channels, false);
  AudioBuffer capture_buffer(
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames());
  test::InputAudioFile capture_file(
      test::GetApmCaptureTestVectorFileName(sample_rate_hz));
  std::vector<float> capture_input(render_buffer.num_frames() *
                                   capture_buffer.num_channels());

  rtc::CriticalSection crit_capture;
  NoiseSuppressionImpl noise_suppressor(&crit_capture);
  noise_suppressor.Initialize(capture_config.num_channels(), sample_rate_hz);
  noise_suppressor.Enable(true);

  IntelligibilityEnhancer intelligibility_enhancer(
      IntelligibilityEnhancerSampleRate(sample_rate_hz),
      render_config.num_channels(), NoiseSuppressionImpl::num_noise_bins());

  for (size_t frame_no = 0u; frame_no < kNumFramesToProcess; ++frame_no) {
    ReadFloatSamplesFromStereoFile(render_buffer.num_frames(),
                                   render_buffer.num_channels(), &render_file,
                                   render_input);
    ReadFloatSamplesFromStereoFile(capture_buffer.num_frames(),
                                   capture_buffer.num_channels(), &capture_file,
                                   capture_input);

    test::CopyVectorToAudioBuffer(render_config, render_input, &render_buffer);
    test::CopyVectorToAudioBuffer(capture_config, capture_input,
                                  &capture_buffer);

    ProcessOneFrame(sample_rate_hz, &render_buffer, &capture_buffer,
                    &noise_suppressor, &intelligibility_enhancer);
  }

  // Extract and verify the test results.
  std::vector<float> render_output;
  test::ExtractVectorFromAudioBuffer(render_config, &render_buffer,
                                     &render_output);

  const float kElementErrorBound = 1.f / static_cast<float>(1 << 15);

  // Compare the output with the reference. Only the first values of the output
  // from last frame processed are compared in order not having to specify all
  // preceeding frames as testvectors. As the algorithm being tested has a
  // memory, testing only the last frame implicitly also tests the preceeding
  // frames.
  EXPECT_TRUE(test::VerifyDeinterleavedArray(
      render_buffer.num_frames(), render_config.num_channels(),
      output_reference, render_output, kElementErrorBound));
}

float float_rand() {
  return std::rand() * 2.f / RAND_MAX - 1;
}

}  // namespace

class IntelligibilityEnhancerTest : public ::testing::Test {
 protected:
  IntelligibilityEnhancerTest()
      : clear_data_(kSamples), noise_data_(kSamples), orig_data_(kSamples) {
    std::srand(1);
    enh_.reset(
        new IntelligibilityEnhancer(kSampleRate, kNumChannels, kNumNoiseBins));
  }

  bool CheckUpdate() {
    enh_.reset(
        new IntelligibilityEnhancer(kSampleRate, kNumChannels, kNumNoiseBins));
    float* clear_cursor = clear_data_.data();
    float* noise_cursor = noise_data_.data();
    for (int i = 0; i < kSamples; i += kFragmentSize) {
      enh_->ProcessRenderAudio(&clear_cursor, kSampleRate, kNumChannels);
      clear_cursor += kFragmentSize;
      noise_cursor += kFragmentSize;
    }
    for (int i = 0; i < kSamples; i++) {
      if (std::fabs(clear_data_[i] - orig_data_[i]) > kMaxTestError) {
        return true;
      }
    }
    return false;
  }

  std::unique_ptr<IntelligibilityEnhancer> enh_;
  std::vector<float> clear_data_;
  std::vector<float> noise_data_;
  std::vector<float> orig_data_;
};

// For each class of generated data, tests that render stream is updated when
// it should be.
TEST_F(IntelligibilityEnhancerTest, TestRenderUpdate) {
  std::fill(noise_data_.begin(), noise_data_.end(), 0.f);
  std::fill(orig_data_.begin(), orig_data_.end(), 0.f);
  std::fill(clear_data_.begin(), clear_data_.end(), 0.f);
  EXPECT_FALSE(CheckUpdate());
  std::generate(noise_data_.begin(), noise_data_.end(), float_rand);
  EXPECT_FALSE(CheckUpdate());
  std::generate(clear_data_.begin(), clear_data_.end(), float_rand);
  orig_data_ = clear_data_;
  EXPECT_TRUE(CheckUpdate());
}

// Tests ERB bank creation, comparing against matlab output.
TEST_F(IntelligibilityEnhancerTest, TestErbCreation) {
  ASSERT_EQ(arraysize(kTestCenterFreqs), enh_->bank_size_);
  for (size_t i = 0; i < enh_->bank_size_; ++i) {
    EXPECT_NEAR(kTestCenterFreqs[i], enh_->center_freqs_[i], kMaxTestError);
    ASSERT_EQ(arraysize(kTestFilterBank[0]), enh_->freqs_);
    for (size_t j = 0; j < enh_->freqs_; ++j) {
      EXPECT_NEAR(kTestFilterBank[i][j], enh_->render_filter_bank_[i][j],
                  kMaxTestError);
    }
  }
}

// Tests analytic solution for optimal gains, comparing
// against matlab output.
TEST_F(IntelligibilityEnhancerTest, TestSolveForGains) {
  ASSERT_EQ(kTestStartFreq, enh_->start_freq_);
  std::vector<float> sols(enh_->bank_size_);
  float lambda = -0.001f;
  for (size_t i = 0; i < enh_->bank_size_; i++) {
    enh_->filtered_clear_pow_[i] = 0.f;
    enh_->filtered_noise_pow_[i] = 0.f;
  }
  enh_->SolveForGainsGivenLambda(lambda, enh_->start_freq_, sols.data());
  for (size_t i = 0; i < enh_->bank_size_; i++) {
    EXPECT_NEAR(kTestZeroVar, sols[i], kMaxTestError);
  }
  for (size_t i = 0; i < enh_->bank_size_; i++) {
    enh_->filtered_clear_pow_[i] = static_cast<float>(i + 1);
    enh_->filtered_noise_pow_[i] = static_cast<float>(enh_->bank_size_ - i);
  }
  enh_->SolveForGainsGivenLambda(lambda, enh_->start_freq_, sols.data());
  for (size_t i = 0; i < enh_->bank_size_; i++) {
    EXPECT_NEAR(kTestNonZeroVarLambdaTop[i], sols[i], kMaxTestError);
  }
  lambda = -1.f;
  enh_->SolveForGainsGivenLambda(lambda, enh_->start_freq_, sols.data());
  for (size_t i = 0; i < enh_->bank_size_; i++) {
    EXPECT_NEAR(kTestNonZeroVarLambdaTop[i], sols[i], kMaxTestError);
  }
}

TEST_F(IntelligibilityEnhancerTest, TestNoiseGainHasExpectedResult) {
  const int kGainDB = 6;
  const float kGainFactor = std::pow(10.f, kGainDB / 20.f);
  const float kTolerance = 0.007f;
  std::vector<float> noise(kNumNoiseBins);
  std::vector<float> noise_psd(kNumNoiseBins);
  std::generate(noise.begin(), noise.end(), float_rand);
  for (size_t i = 0; i < kNumNoiseBins; ++i) {
    noise_psd[i] = kGainFactor * kGainFactor * noise[i] * noise[i];
  }
  float* clear_cursor = clear_data_.data();
  for (size_t i = 0; i < kNumFramesToProcess; ++i) {
    enh_->SetCaptureNoiseEstimate(noise, kGainDB);
    enh_->ProcessRenderAudio(&clear_cursor, kSampleRate, kNumChannels);
  }
  const std::vector<float>& estimated_psd =
      enh_->noise_power_estimator_.power();
  for (size_t i = 0; i < kNumNoiseBins; ++i) {
    EXPECT_LT(std::abs(estimated_psd[i] - noise_psd[i]) / noise_psd[i],
              kTolerance);
  }
}

TEST(IntelligibilityEnhancerBitExactnessTest, DISABLED_Mono8kHz) {
  const float kOutputReference[] = {-0.001892f, -0.003296f, -0.001953f};

  RunBitexactnessTest(AudioProcessing::kSampleRate8kHz, 1, kOutputReference);
}

TEST(IntelligibilityEnhancerBitExactnessTest, DISABLED_Mono16kHz) {
  const float kOutputReference[] = {-0.000977f, -0.003296f, -0.002441f};

  RunBitexactnessTest(AudioProcessing::kSampleRate16kHz, 1, kOutputReference);
}

TEST(IntelligibilityEnhancerBitExactnessTest, DISABLED_Mono32kHz) {
  const float kOutputReference[] = {0.003021f, -0.011780f, -0.008209f};

  RunBitexactnessTest(AudioProcessing::kSampleRate32kHz, 1, kOutputReference);
}

TEST(IntelligibilityEnhancerBitExactnessTest, DISABLED_Mono48kHz) {
  const float kOutputReference[] = {-0.027696f, -0.026253f, -0.018001f};

  RunBitexactnessTest(AudioProcessing::kSampleRate48kHz, 1, kOutputReference);
}

TEST(IntelligibilityEnhancerBitExactnessTest, DISABLED_Stereo8kHz) {
  const float kOutputReference[] = {0.021454f,  0.035919f, 0.026428f,
                                    -0.000641f, 0.000366f, 0.000641f};

  RunBitexactnessTest(AudioProcessing::kSampleRate8kHz, 2, kOutputReference);
}

TEST(IntelligibilityEnhancerBitExactnessTest, DISABLED_Stereo16kHz) {
  const float kOutputReference[] = {0.021362f,  0.035736f,  0.023895f,
                                    -0.001404f, -0.001465f, 0.000549f};

  RunBitexactnessTest(AudioProcessing::kSampleRate16kHz, 2, kOutputReference);
}

TEST(IntelligibilityEnhancerBitExactnessTest, DISABLED_Stereo32kHz) {
  const float kOutputReference[] = {0.030641f,  0.027406f,  0.028321f,
                                    -0.001343f, -0.004578f, 0.000977f};

  RunBitexactnessTest(AudioProcessing::kSampleRate32kHz, 2, kOutputReference);
}

TEST(IntelligibilityEnhancerBitExactnessTest, DISABLED_Stereo48kHz) {
  const float kOutputReference[] = {-0.009276f, -0.001601f, -0.008255f,
                                    -0.012975f, -0.015940f, -0.017820f};

  RunBitexactnessTest(AudioProcessing::kSampleRate48kHz, 2, kOutputReference);
}

}  // namespace webrtc
