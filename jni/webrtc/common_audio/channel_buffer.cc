/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/channel_buffer.h"

#include "webrtc/base/checks.h"

namespace webrtc {

IFChannelBuffer::IFChannelBuffer(size_t num_frames,
                                 size_t num_channels,
                                 size_t num_bands)
    : ivalid_(true),
      ibuf_(num_frames, num_channels, num_bands),
      fvalid_(true),
      fbuf_(num_frames, num_channels, num_bands) {}

ChannelBuffer<int16_t>* IFChannelBuffer::ibuf() {
  RefreshI();
  fvalid_ = false;
  return &ibuf_;
}

ChannelBuffer<float>* IFChannelBuffer::fbuf() {
  RefreshF();
  ivalid_ = false;
  return &fbuf_;
}

const ChannelBuffer<int16_t>* IFChannelBuffer::ibuf_const() const {
  RefreshI();
  return &ibuf_;
}

const ChannelBuffer<float>* IFChannelBuffer::fbuf_const() const {
  RefreshF();
  return &fbuf_;
}

void IFChannelBuffer::RefreshF() const {
  if (!fvalid_) {
    RTC_DCHECK(ivalid_);
    const int16_t* const* int_channels = ibuf_.channels();
    float* const* float_channels = fbuf_.channels();
    for (size_t i = 0; i < ibuf_.num_channels(); ++i) {
      for (size_t j = 0; j < ibuf_.num_frames(); ++j) {
        float_channels[i][j] = int_channels[i][j];
      }
    }
    fvalid_ = true;
  }
}

void IFChannelBuffer::RefreshI() const {
  if (!ivalid_) {
    RTC_DCHECK(fvalid_);
    int16_t* const* int_channels = ibuf_.channels();
    const float* const* float_channels = fbuf_.channels();
    for (size_t i = 0; i < ibuf_.num_channels(); ++i) {
      FloatS16ToS16(float_channels[i],
                    ibuf_.num_frames(),
                    int_channels[i]);
    }
    ivalid_ = true;
  }
}

}  // namespace webrtc
