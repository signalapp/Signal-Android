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

#include "webrtc/base/checks.h"
#include "webrtc/common_audio/real_fourier_ooura.h"
#include "webrtc/common_audio/real_fourier_openmax.h"
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"

namespace webrtc {

using std::complex;

const size_t RealFourier::kFftBufferAlignment = 32;

std::unique_ptr<RealFourier> RealFourier::Create(int fft_order) {
#if defined(RTC_USE_OPENMAX_DL)
  return std::unique_ptr<RealFourier>(new RealFourierOpenmax(fft_order));
#else
  return std::unique_ptr<RealFourier>(new RealFourierOoura(fft_order));
#endif
}

int RealFourier::FftOrder(size_t length) {
  RTC_CHECK_GT(length, 0U);
  return WebRtcSpl_GetSizeInBits(static_cast<uint32_t>(length - 1));
}

size_t RealFourier::FftLength(int order) {
  RTC_CHECK_GE(order, 0);
  return static_cast<size_t>(1 << order);
}

size_t RealFourier::ComplexLength(int order) {
  return FftLength(order) / 2 + 1;
}

RealFourier::fft_real_scoper RealFourier::AllocRealBuffer(int count) {
  return fft_real_scoper(static_cast<float*>(
      AlignedMalloc(sizeof(float) * count, kFftBufferAlignment)));
}

RealFourier::fft_cplx_scoper RealFourier::AllocCplxBuffer(int count) {
  return fft_cplx_scoper(static_cast<complex<float>*>(
      AlignedMalloc(sizeof(complex<float>) * count, kFftBufferAlignment)));
}

}  // namespace webrtc

