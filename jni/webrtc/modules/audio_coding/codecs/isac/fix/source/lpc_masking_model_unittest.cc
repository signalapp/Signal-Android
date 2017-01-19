/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/codecs/isac/fix/source/lpc_masking_model.h"
#include "webrtc/system_wrappers/interface/cpu_features_wrapper.h"
#include "webrtc/typedefs.h"

class LpcMaskingModelTest : public testing::Test {
 protected:
  // Pass a function pointer to the Tester function.
  void CalculateResidualEnergyTester(CalculateResidualEnergy
                                     CalculateResidualEnergyFunction) {
    const int kIntOrder = 10;
    const int32_t kInt32QDomain = 5;
    const int kIntShift = 11;
    int16_t a[kIntOrder + 1] = {32760, 122, 7, 0, -32760, -3958,
        -48, 18745, 498, 9, 23456};
    int32_t corr[kIntOrder + 1] = {11443647, -27495, 0,
        98745, -11443600, 1, 1, 498, 9, 888, 23456};
    int q_shift_residual = 0;
    int32_t residual_energy = 0;

    // Test the code path where (residual_energy >= 0x10000).
    residual_energy = CalculateResidualEnergyFunction(kIntOrder,
        kInt32QDomain, kIntShift, a, corr, &q_shift_residual);
    EXPECT_EQ(1789023310, residual_energy);
    EXPECT_EQ(2, q_shift_residual);

    // Test the code path where (residual_energy < 0x10000)
    // and ((energy & 0x8000) != 0).
    for (int i = 0; i < kIntOrder + 1; i++) {
      a[i] = 24575 >> i;
      corr[i] = i;
    }
    residual_energy = CalculateResidualEnergyFunction(kIntOrder,
        kInt32QDomain, kIntShift, a, corr, &q_shift_residual);
    EXPECT_EQ(1595279092, residual_energy);
    EXPECT_EQ(26, q_shift_residual);

    // Test the code path where (residual_energy <= 0x7fff).
    for (int i = 0; i < kIntOrder + 1; i++) {
      a[i] = 2457 >> i;
    }
    residual_energy = CalculateResidualEnergyFunction(kIntOrder,
        kInt32QDomain, kIntShift, a, corr, &q_shift_residual);
    EXPECT_EQ(2029266944, residual_energy);
    EXPECT_EQ(33, q_shift_residual);
  }
};

TEST_F(LpcMaskingModelTest, CalculateResidualEnergyTest) {
  CalculateResidualEnergyTester(WebRtcIsacfix_CalculateResidualEnergyC);
#ifdef WEBRTC_DETECT_ARM_NEON
  if ((WebRtc_GetCPUFeaturesARM() & kCPUFeatureNEON) != 0) {
    CalculateResidualEnergyTester(WebRtcIsacfix_CalculateResidualEnergyNeon);
  }
#elif defined(WEBRTC_ARCH_ARM_NEON)
  CalculateResidualEnergyTester(WebRtcIsacfix_CalculateResidualEnergyNeon);
#endif
}
