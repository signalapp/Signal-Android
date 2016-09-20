/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_PEAK_DETECTOR_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_PEAK_DETECTOR_H_

#include "webrtc/modules/audio_coding/neteq/delay_peak_detector.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockDelayPeakDetector : public DelayPeakDetector {
 public:
  MockDelayPeakDetector(const TickTimer* tick_timer)
      : DelayPeakDetector(tick_timer) {}
  virtual ~MockDelayPeakDetector() { Die(); }
  MOCK_METHOD0(Die, void());
  MOCK_METHOD0(Reset, void());
  MOCK_METHOD1(SetPacketAudioLength, void(int length_ms));
  MOCK_METHOD0(peak_found, bool());
  MOCK_CONST_METHOD0(MaxPeakHeight, int());
  MOCK_CONST_METHOD0(MaxPeakPeriod, uint64_t());
  MOCK_METHOD2(Update, bool(int inter_arrival_time, int target_level));
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_PEAK_DETECTOR_H_
