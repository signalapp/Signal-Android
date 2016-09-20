/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_MANAGER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_MANAGER_H_

#include "webrtc/modules/audio_coding/neteq/delay_manager.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockDelayManager : public DelayManager {
 public:
  MockDelayManager(size_t max_packets_in_buffer,
                   DelayPeakDetector* peak_detector,
                   const TickTimer* tick_timer)
      : DelayManager(max_packets_in_buffer, peak_detector, tick_timer) {}
  virtual ~MockDelayManager() { Die(); }
  MOCK_METHOD0(Die, void());
  MOCK_CONST_METHOD0(iat_vector,
      const IATVector&());
  MOCK_METHOD3(Update,
      int(uint16_t sequence_number, uint32_t timestamp, int sample_rate_hz));
  MOCK_METHOD1(CalculateTargetLevel,
      int(int iat_packets));
  MOCK_METHOD1(SetPacketAudioLength,
      int(int length_ms));
  MOCK_METHOD0(Reset,
      void());
  MOCK_CONST_METHOD0(AverageIAT,
      int());
  MOCK_CONST_METHOD0(PeakFound,
      bool());
  MOCK_METHOD1(UpdateCounters,
      void(int elapsed_time_ms));
  MOCK_METHOD0(ResetPacketIatCount,
      void());
  MOCK_CONST_METHOD2(BufferLimits,
      void(int* lower_limit, int* higher_limit));
  MOCK_CONST_METHOD0(TargetLevel,
      int());
  MOCK_METHOD1(LastDecoderType,
      void(NetEqDecoder decoder_type));
  MOCK_METHOD1(set_extra_delay_ms,
      void(int16_t delay));
  MOCK_CONST_METHOD0(base_target_level,
      int());
  MOCK_METHOD1(set_streaming_mode,
      void(bool value));
  MOCK_CONST_METHOD0(last_pack_cng_or_dtmf,
      int());
  MOCK_METHOD1(set_last_pack_cng_or_dtmf,
      void(int value));
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_MANAGER_H_
