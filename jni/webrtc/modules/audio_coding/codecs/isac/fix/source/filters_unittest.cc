/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "webrtc/system_wrappers/include/cpu_features_wrapper.h"
#include "webrtc/typedefs.h"

class FiltersTest : public testing::Test {
 protected:
  // Pass a function pointer to the Tester function.
  void FiltersTester(AutocorrFix WebRtcIsacfix_AutocorrFixFunction) {
    const int kOrder = 12;
    const int kBuffer = 40;
    int16_t scale = 0;
    int32_t r_buffer[kOrder + 2] = {0};

    // Test an overflow case.
    const int16_t x_buffer_0[kBuffer] = {0, 0, 3010, 22351, 21106, 16969, -2095,
        -664, 3513, -30980, 32767, -23839, 13335, 20289, -6831, 339, -17207,
        32767, 4959, 6177, 32767, 16599, -4747, 20504, 3513, -30980, 32767,
        -23839, 13335, 20289, 0, -16969, -2095, -664, 3513, 31981, 32767,
        -13839, 23336, 30281};
    const int32_t r_expected_0[kOrder + 2] = {1872498461, -224288754, 203789985,
        483400487, -208272635, 2436500, 137785322, 266600814, -208486262,
        329510080, 137949184, -161738972, -26894267, 237630192};

    WebRtcIsacfix_AutocorrFixFunction(r_buffer, x_buffer_0,
                                      kBuffer, kOrder + 1, &scale);
    for (int i = 0; i < kOrder + 2; i++) {
      EXPECT_EQ(r_expected_0[i], r_buffer[i]);
    }
    EXPECT_EQ(3, scale);

    // Test a no-overflow case.
    const int16_t x_buffer_1[kBuffer] = {0, 0, 300, 21, 206, 169, -295,
        -664, 3513, -300, 327, -29, 15, 289, -6831, 339, -107,
        37, 59, 6177, 327, 169, -4747, 204, 313, -980, 767,
        -9, 135, 289, 0, -6969, -2095, -664, 0, 1, 7,
        -39, 236, 281};
    const int32_t r_expected_1[kOrder + 2] = {176253864, 8126617, 1983287,
        -26196788, -3487363, -42839676, -24644043, 3469813, 30559879, 31905045,
        5101567, 29328896, -55787438, -13163978};

    WebRtcIsacfix_AutocorrFixFunction(r_buffer, x_buffer_1,
                                      kBuffer, kOrder + 1, &scale);
    for (int i = 0; i < kOrder + 2; i++) {
      EXPECT_EQ(r_expected_1[i], r_buffer[i]);
    }
    EXPECT_EQ(0, scale);
  }
};

TEST_F(FiltersTest, AutocorrFixTest) {
  FiltersTester(WebRtcIsacfix_AutocorrC);
#if defined(WEBRTC_HAS_NEON)
  FiltersTester(WebRtcIsacfix_AutocorrNeon);
#endif
}
