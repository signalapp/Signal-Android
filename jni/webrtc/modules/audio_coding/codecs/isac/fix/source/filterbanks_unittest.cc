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
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_coding/codecs/isac/fix/source/filterbank_internal.h"
#include "webrtc/modules/audio_coding/codecs/isac/fix/source/filterbank_tables.h"
#include "webrtc/modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "webrtc/system_wrappers/include/cpu_features_wrapper.h"
#include "webrtc/typedefs.h"

class FilterBanksTest : public testing::Test {
 protected:
  // Pass a function pointer to the Tester function.
  void CalculateResidualEnergyTester(AllpassFilter2FixDec16
                                     AllpassFilter2FixDec16Function) {
    const int kSamples = QLOOKAHEAD;
    const int kState = 2;
    int16_t data_ch1[kSamples] = {0};
    int16_t data_ch2[kSamples] = {0};
    int32_t state_ch1[kState] = {0};
    int32_t state_ch2[kState] = {0};
    const int32_t out_state_ch1[kState] = {-809122714, 1645972152};
    const int32_t out_state_ch2[kState] = {428019288, 1057309936};
    const int32_t out_data_ch1[kSamples] = {0, 0, 347, 10618, 16718, -7089,
        32767, 16913, 27042, 8377, -22973, -28372, -27603, -14804, 398, -25332,
        -11200, 18044, 25223, -6839, 1116, -23984, 32717, 7364};
    const int32_t out_data_ch2[kSamples] = {0, 0, 3010, 22351, 21106, 16969,
        -2095, -664, 3513, -30980, 32767, -23839, 13335, 20289, -6831, 339,
        -17207, 32767, 4959, 6177, 32767, 16599, -4747, 20504};
    int sign = 1;

    for (int i = 0; i < kSamples; i++) {
      sign *= -1;
      data_ch1[i] = sign * WEBRTC_SPL_WORD32_MAX / (i * i + 1);
      data_ch2[i] = sign * WEBRTC_SPL_WORD32_MIN / (i * i + 1);
    };

    AllpassFilter2FixDec16Function(data_ch1,
                                   data_ch2,
                                   WebRtcIsacfix_kUpperApFactorsQ15,
                                   WebRtcIsacfix_kLowerApFactorsQ15,
                                   kSamples,
                                   state_ch1,
                                   state_ch2);

    for (int i = 0; i < kSamples; i++) {
      EXPECT_EQ(out_data_ch1[i], data_ch1[i]);
      EXPECT_EQ(out_data_ch2[i], data_ch2[i]);
    }
    for (int i = 0; i < kState; i++) {
      EXPECT_EQ(out_state_ch1[i], state_ch1[i]);
      EXPECT_EQ(out_state_ch2[i], state_ch2[i]);
    }
  }
};

TEST_F(FilterBanksTest, AllpassFilter2FixDec16Test) {
  CalculateResidualEnergyTester(WebRtcIsacfix_AllpassFilter2FixDec16C);
#if defined(WEBRTC_HAS_NEON)
  CalculateResidualEnergyTester(WebRtcIsacfix_AllpassFilter2FixDec16Neon);
#endif
}

TEST_F(FilterBanksTest, HighpassFilterFixDec32Test) {
  const int kSamples = 20;
  int16_t in[kSamples];
  int32_t state[2] = {12345, 987654};
#ifdef WEBRTC_ARCH_ARM_V7
  int32_t out[kSamples] = {-1040, -1035, -22875, -1397, -27604, 20018, 7917,
    -1279, -8552, -14494, -7558, -23537, -27258, -30554, -32768, -3432, -32768,
    25215, -27536, 22436};
#else
  int32_t out[kSamples] = {-1040, -1035, -22875, -1397, -27604, 20017, 7915,
    -1280, -8554, -14496, -7561, -23541, -27263, -30560, -32768, -3441, -32768,
    25203, -27550, 22419};
#endif
  HighpassFilterFixDec32 WebRtcIsacfix_HighpassFilterFixDec32;
#if defined(MIPS_DSP_R1_LE)
  WebRtcIsacfix_HighpassFilterFixDec32 =
      WebRtcIsacfix_HighpassFilterFixDec32MIPS;
#else
  WebRtcIsacfix_HighpassFilterFixDec32 = WebRtcIsacfix_HighpassFilterFixDec32C;
#endif

  for (int i = 0; i < kSamples; i++) {
    in[i] = WEBRTC_SPL_WORD32_MAX / (i + 1);
  }

  WebRtcIsacfix_HighpassFilterFixDec32(in, kSamples,
      WebRtcIsacfix_kHPStCoeffOut1Q30, state);

  for (int i = 0; i < kSamples; i++) {
    EXPECT_EQ(out[i], in[i]);
  }
}
