/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/real_fourier_openmax.h"

#include <cstdlib>

#include "dl/sp/api/omxSP.h"
#include "webrtc/base/checks.h"

namespace webrtc {

using std::complex;

namespace {

// Creates and initializes the Openmax state. Transfers ownership to caller.
OMXFFTSpec_R_F32* CreateOpenmaxState(int order) {
  RTC_CHECK_GE(order, 1);
  // The omx implementation uses this macro to check order validity.
  RTC_CHECK_LE(order, TWIDDLE_TABLE_ORDER);

  OMX_INT buffer_size;
  OMXResult r = omxSP_FFTGetBufSize_R_F32(order, &buffer_size);
  RTC_CHECK_EQ(r, OMX_Sts_NoErr);

  OMXFFTSpec_R_F32* omx_spec = malloc(buffer_size);
  RTC_DCHECK(omx_spec);

  r = omxSP_FFTInit_R_F32(omx_spec, order);
  RTC_CHECK_EQ(r, OMX_Sts_NoErr);
  return omx_spec;
}

}  // namespace

RealFourierOpenmax::RealFourierOpenmax(int fft_order)
    : order_(fft_order),
      omx_spec_(CreateOpenmaxState(order_)) {
}

RealFourierOpenmax::~RealFourierOpenmax() {
  free(omx_spec_);
}

void RealFourierOpenmax::Forward(const float* src, complex<float>* dest) const {
  // This cast is well-defined since C++11. See "Non-static data members" at:
  // http://en.cppreference.com/w/cpp/numeric/complex
  OMXResult r =
      omxSP_FFTFwd_RToCCS_F32(src, reinterpret_cast<OMX_F32*>(dest), omx_spec_);
  RTC_CHECK_EQ(r, OMX_Sts_NoErr);
}

void RealFourierOpenmax::Inverse(const complex<float>* src, float* dest) const {
  OMXResult r =
      omxSP_FFTInv_CCSToR_F32(reinterpret_cast<const OMX_F32*>(src), dest,
                              omx_spec_);
  RTC_CHECK_EQ(r, OMX_Sts_NoErr);
}

}  // namespace webrtc

