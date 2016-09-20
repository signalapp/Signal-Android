/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_REAL_FOURIER_OPENMAX_H_
#define WEBRTC_COMMON_AUDIO_REAL_FOURIER_OPENMAX_H_

#include <complex>

#include "webrtc/common_audio/real_fourier.h"

namespace webrtc {

class RealFourierOpenmax : public RealFourier {
 public:
  explicit RealFourierOpenmax(int fft_order);
  ~RealFourierOpenmax() override;

  void Forward(const float* src, std::complex<float>* dest) const override;
  void Inverse(const std::complex<float>* src, float* dest) const override;

  int order() const override {
    return order_;
  }

 private:
  // Basically a forward declare of OMXFFTSpec_R_F32. To get rid of the
  // dependency on openmax.
  typedef void OMXFFTSpec_R_F32_;
  const int order_;

  OMXFFTSpec_R_F32_* const omx_spec_;
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_AUDIO_REAL_FOURIER_OPENMAX_H_

