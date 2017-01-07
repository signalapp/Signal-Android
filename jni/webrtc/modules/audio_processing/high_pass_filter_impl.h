/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_HIGH_PASS_FILTER_IMPL_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_HIGH_PASS_FILTER_IMPL_H_

#include <memory>
#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"

namespace webrtc {

class AudioBuffer;

class HighPassFilterImpl : public HighPassFilter {
 public:
  explicit HighPassFilterImpl(rtc::CriticalSection* crit);
  ~HighPassFilterImpl() override;

  // TODO(peah): Fold into ctor, once public API is removed.
  void Initialize(size_t channels, int sample_rate_hz);
  void ProcessCaptureAudio(AudioBuffer* audio);

  // HighPassFilter implementation.
  int Enable(bool enable) override;
  bool is_enabled() const override;

 private:
  class BiquadFilter;
  rtc::CriticalSection* const crit_ = nullptr;
  bool enabled_ GUARDED_BY(crit_) = false;
  std::vector<std::unique_ptr<BiquadFilter>> filters_ GUARDED_BY(crit_);
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(HighPassFilterImpl);
};
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_HIGH_PASS_FILTER_IMPL_H_
