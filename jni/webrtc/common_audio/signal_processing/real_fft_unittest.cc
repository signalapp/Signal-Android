/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/signal_processing/include/real_fft.h"
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/typedefs.h"

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {
namespace {

// FFT order.
const int kOrder = 5;
// Lengths for real FFT's time and frequency bufffers.
// For N-point FFT, the length requirements from API are N and N+2 respectively.
const int kTimeDataLength = 1 << kOrder;
const int kFreqDataLength = (1 << kOrder) + 2;
// For complex FFT's time and freq buffer. The implementation requires
// 2*N 16-bit words.
const int kComplexFftDataLength = 2 << kOrder;
// Reference data for time signal.
const int16_t kRefData[kTimeDataLength] = {
  11739, 6848, -8688, 31980, -30295, 25242, 27085, 19410,
  -26299, 15607, -10791, 11778, -23819, 14498, -25772, 10076,
  1173, 6848, -8688, 31980, -30295, 2522, 27085, 19410,
  -2629, 5607, -3, 1178, -23819, 1498, -25772, 10076
};

class RealFFTTest : public ::testing::Test {
 protected:
   RealFFTTest() {
     WebRtcSpl_Init();
   }
};

TEST_F(RealFFTTest, CreateFailsOnBadInput) {
  RealFFT* fft = WebRtcSpl_CreateRealFFT(11);
  EXPECT_TRUE(fft == NULL);
  fft = WebRtcSpl_CreateRealFFT(-1);
  EXPECT_TRUE(fft == NULL);
}

TEST_F(RealFFTTest, RealAndComplexMatch) {
  int i = 0;
  int j = 0;
  int16_t real_fft_time[kTimeDataLength] = {0};
  int16_t real_fft_freq[kFreqDataLength] = {0};
  // One common buffer for complex FFT's time and frequency data.
  int16_t complex_fft_buff[kComplexFftDataLength] = {0};

  // Prepare the inputs to forward FFT's.
  memcpy(real_fft_time, kRefData, sizeof(kRefData));
  for (i = 0, j = 0; i < kTimeDataLength; i += 1, j += 2) {
    complex_fft_buff[j] = kRefData[i];
    complex_fft_buff[j + 1] = 0;  // Insert zero's to imaginary parts.
  };

  // Create and run real forward FFT.
  RealFFT* fft = WebRtcSpl_CreateRealFFT(kOrder);
  EXPECT_TRUE(fft != NULL);
  EXPECT_EQ(0, WebRtcSpl_RealForwardFFT(fft, real_fft_time, real_fft_freq));

  // Run complex forward FFT.
  WebRtcSpl_ComplexBitReverse(complex_fft_buff, kOrder);
  EXPECT_EQ(0, WebRtcSpl_ComplexFFT(complex_fft_buff, kOrder, 1));

  // Verify the results between complex and real forward FFT.
  for (i = 0; i < kFreqDataLength; i++) {
    EXPECT_EQ(real_fft_freq[i], complex_fft_buff[i]);
  }

  // Prepare the inputs to inverse real FFT.
  // We use whatever data in complex_fft_buff[] since we don't care
  // about data contents. Only kFreqDataLength 16-bit words are copied
  // from complex_fft_buff to real_fft_freq since remaining words (2nd half)
  // are conjugate-symmetric to the first half in theory.
  memcpy(real_fft_freq, complex_fft_buff, sizeof(real_fft_freq));

  // Run real inverse FFT.
  int real_scale = WebRtcSpl_RealInverseFFT(fft, real_fft_freq, real_fft_time);
  EXPECT_GE(real_scale, 0);

  // Run complex inverse FFT.
  WebRtcSpl_ComplexBitReverse(complex_fft_buff, kOrder);
  int complex_scale = WebRtcSpl_ComplexIFFT(complex_fft_buff, kOrder, 1);

  // Verify the results between complex and real inverse FFT.
  // They are not bit-exact, since complex IFFT doesn't produce
  // exactly conjugate-symmetric data (between first and second half).
  EXPECT_EQ(real_scale, complex_scale);
  for (i = 0, j = 0; i < kTimeDataLength; i += 1, j += 2) {
    EXPECT_LE(abs(real_fft_time[i] - complex_fft_buff[j]), 1);
  }

  WebRtcSpl_FreeRealFFT(fft);
}

}  // namespace
}  // namespace webrtc
