/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_PACKET_BUFFER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_PACKET_BUFFER_H_

#include "webrtc/modules/audio_coding/neteq/packet_buffer.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockPacketBuffer : public PacketBuffer {
 public:
  MockPacketBuffer(size_t max_number_of_packets, const TickTimer* tick_timer)
      : PacketBuffer(max_number_of_packets, tick_timer) {}
  virtual ~MockPacketBuffer() { Die(); }
  MOCK_METHOD0(Die, void());
  MOCK_METHOD0(Flush,
      void());
  MOCK_CONST_METHOD0(Empty,
      bool());
  MOCK_METHOD1(InsertPacket,
      int(Packet* packet));
  MOCK_METHOD4(InsertPacketList,
      int(PacketList* packet_list,
          const DecoderDatabase& decoder_database,
          uint8_t* current_rtp_payload_type,
          uint8_t* current_cng_rtp_payload_type));
  MOCK_CONST_METHOD1(NextTimestamp,
      int(uint32_t* next_timestamp));
  MOCK_CONST_METHOD2(NextHigherTimestamp,
      int(uint32_t timestamp, uint32_t* next_timestamp));
  MOCK_CONST_METHOD0(NextRtpHeader,
      const RTPHeader*());
  MOCK_METHOD1(GetNextPacket,
      Packet*(size_t* discard_count));
  MOCK_METHOD0(DiscardNextPacket,
      int());
  MOCK_METHOD2(DiscardOldPackets,
      int(uint32_t timestamp_limit, uint32_t horizon_samples));
  MOCK_METHOD1(DiscardAllOldPackets,
      int(uint32_t timestamp_limit));
  MOCK_CONST_METHOD0(NumPacketsInBuffer,
      size_t());
  MOCK_METHOD1(IncrementWaitingTimes,
      void(int));
  MOCK_CONST_METHOD0(current_memory_bytes,
      int());
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_PACKET_BUFFER_H_
