/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_PAYLOAD_SPLITTER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_PAYLOAD_SPLITTER_H_

#include "webrtc/modules/audio_coding/neteq/payload_splitter.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockPayloadSplitter : public PayloadSplitter {
 public:
  MOCK_METHOD1(SplitRed,
      int(PacketList* packet_list));
  MOCK_METHOD2(SplitFec,
      int(PacketList* packet_list, DecoderDatabase* decoder_database));
  MOCK_METHOD2(CheckRedPayloads,
      int(PacketList* packet_list, const DecoderDatabase& decoder_database));
  MOCK_METHOD2(SplitAudio,
      int(PacketList* packet_list, const DecoderDatabase& decoder_database));
  MOCK_METHOD4(SplitBySamples,
      void(const Packet* packet, size_t bytes_per_ms,
           uint32_t timestamps_per_ms, PacketList* new_packets));
  MOCK_METHOD4(SplitByFrames,
      int(const Packet* packet, size_t bytes_per_frame,
          uint32_t timestamps_per_frame, PacketList* new_packets));
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_PAYLOAD_SPLITTER_H_
