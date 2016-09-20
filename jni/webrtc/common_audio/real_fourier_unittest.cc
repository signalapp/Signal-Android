/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/real_fourier.h"

#include <stdlib.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_audio/real_fourier_openmax.h"
#include "webrtc/common_audio/real_fourier_ooura.h"

namespace webrtc {

using std::complex;

TEST(RealFourierStaticsTest, AllocatorAlignment) {
  {
    RealFourier::fft_real_scoper real;
    real = RealFourier::AllocRealBuffer(3);
    ASSERT_TRUE(real.get() != nullptr);
    uintptr_t ptr_value = reinterpret_cast<uintptr_t>(real.get());
    EXPECT_EQ(0u, ptr_value % RealFourier::kFftBufferAlignment);
  }
  {
    RealFourier::fft_cplx_scoper cplx;
    cplx = RealFourier::AllocCplxBuffer(3);
    ASSERT_TRUE(cplx.get() != nullptr);
    uintptr_t ptr_value = reinterpret_cast<uintptr_t>(cplx.get());
    EXPECT_EQ(0u, ptr_value % RealFourier::kFftBufferAlignment);
  }
}

TEST(RealFourierStaticsTest, OrderComputation) {
  EXPECT_EQ(4, RealFourier::FftOrder(13));
  EXPECT_EQ(5, RealFourier::FftOrder(32));
  EXPECT_EQ(1, RealFourier::FftOrder(2));
  EXPECT_EQ(0, RealFourier::FftOrder(1));
}

TEST(RealFourierStaticsTest, ComplexLengthComputation) {
  EXPECT_EQ(2U, RealFourier::ComplexLength(1));
  EXPECT_EQ(3U, RealFourier::ComplexLength(2));
  EXPECT_EQ(5U, RealFourier::ComplexLength(3));
  EXPECT_EQ(9U, RealFourier::ComplexLength(4));
  EXPECT_EQ(17U, RealFourier::ComplexLength(5));
  EXPECT_EQ(65U, RealFourier::ComplexLength(7));
}

template <typename T>
class RealFourierTest : public ::testing::Test {
 protected:
  RealFourierTest()
      : rf_(2),
        real_buffer_(RealFourier::AllocRealBuffer(4)),
        cplx_buffer_(RealFourier::AllocCplxBuffer(3)) {}

  ~RealFourierTest() {
  }

  T rf_;
  const RealFourier::fft_real_scoper real_buffer_;
  const RealFourier::fft_cplx_scoper cplx_buffer_;
};

using FftTypes = ::testing::Types<
#if defined(RTC_USE_OPENMAX_DL)
    RealFourierOpenmax,
#endif
    RealFourierOoura>;
TYPED_TEST_CASE(RealFourierTest, FftTypes);

TYPED_TEST(RealFourierTest, SimpleForwardTransform) {
  this->real_buffer_[0] = 1.0f;
  this->real_buffer_[1] = 2.0f;
  this->real_buffer_[2] = 3.0f;
  this->real_buffer_[3] = 4.0f;

  this->rf_.Forward(this->real_buffer_.get(), this->cplx_buffer_.get());

  EXPECT_NEAR(this->cplx_buffer_[0].real(), 10.0f, 1e-8f);
  EXPECT_NEAR(this->cplx_buffer_[0].imag(), 0.0f, 1e-8f);
  EXPECT_NEAR(this->cplx_buffer_[1].real(), -2.0f, 1e-8f);
  EXPECT_NEAR(this->cplx_buffer_[1].imag(), 2.0f, 1e-8f);
  EXPECT_NEAR(this->cplx_buffer_[2].real(), -2.0f, 1e-8f);
  EXPECT_NEAR(this->cplx_buffer_[2].imag(), 0.0f, 1e-8f);
}

TYPED_TEST(RealFourierTest, SimpleBackwardTransform) {
  this->cplx_buffer_[0] = complex<float>(10.0f, 0.0f);
  this->cplx_buffer_[1] = complex<float>(-2.0f, 2.0f);
  this->cplx_buffer_[2] = complex<float>(-2.0f, 0.0f);

  this->rf_.Inverse(this->cplx_buffer_.get(), this->real_buffer_.get());

  EXPECT_NEAR(this->real_buffer_[0], 1.0f, 1e-8f);
  EXPECT_NEAR(this->real_buffer_[1], 2.0f, 1e-8f);
  EXPECT_NEAR(this->real_buffer_[2], 3.0f, 1e-8f);
  EXPECT_NEAR(this->real_buffer_[3], 4.0f, 1e-8f);
}

}  // namespace webrtc

