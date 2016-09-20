/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_CONSTANT_PCM_PACKET_SOURCE_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_CONSTANT_PCM_PACKET_SOURCE_H_

#include <stdio.h>
#include <string>

#include "webrtc/base/constructormagic.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet_source.h"

namespace webrtc {
namespace test {

// This class implements a packet source that delivers PCM16b encoded packets
// with a constant sample value. The payload length, constant sample value,
// sample rate, and payload type are all set in the constructor.
class ConstantPcmPacketSource : public PacketSource {
 public:
  ConstantPcmPacketSource(size_t payload_len_samples,
                          int16_t sample_value,
                          int sample_rate_hz,
                          int payload_type);

  std::unique_ptr<Packet> NextPacket() override;

 private:
  void WriteHeader(uint8_t* packet_memory);

  const size_t kHeaderLenBytes = 12;
  const size_t payload_len_samples_;
  const size_t packet_len_bytes_;
  uint8_t encoded_sample_[2];
  const int samples_per_ms_;
  double next_arrival_time_ms_;
  const int payload_type_;
  uint16_t seq_number_;
  uint32_t timestamp_;
  const uint32_t payload_ssrc_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ConstantPcmPacketSource);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_CONSTANT_PCM_PACKET_SOURCE_H_
