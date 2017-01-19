/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for PayloadSplitter class.

#include "webrtc/modules/audio_coding/neteq/payload_splitter.h"

#include <assert.h>

#include <utility>  // pair

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/packet.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

using ::testing::Return;
using ::testing::ReturnNull;

namespace webrtc {

static const int kRedPayloadType = 100;
static const int kPayloadLength = 10;
static const int kRedHeaderLength = 4;  // 4 bytes RED header.
static const uint16_t kSequenceNumber = 0;
static const uint32_t kBaseTimestamp = 0x12345678;

// RED headers (according to RFC 2198):
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |F|   block PT  |  timestamp offset         |   block length    |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
// Last RED header:
//    0 1 2 3 4 5 6 7
//   +-+-+-+-+-+-+-+-+
//   |0|   Block PT  |
//   +-+-+-+-+-+-+-+-+

// Creates a RED packet, with |num_payloads| payloads, with payload types given
// by the values in array |payload_types| (which must be of length
// |num_payloads|). Each redundant payload is |timestamp_offset| samples
// "behind" the the previous payload.
Packet* CreateRedPayload(int num_payloads,
                         uint8_t* payload_types,
                         int timestamp_offset) {
  Packet* packet = new Packet;
  packet->header.payloadType = kRedPayloadType;
  packet->header.timestamp = kBaseTimestamp;
  packet->header.sequenceNumber = kSequenceNumber;
  packet->payload_length = (kPayloadLength + 1) +
      (num_payloads - 1) * (kPayloadLength + kRedHeaderLength);
  uint8_t* payload = new uint8_t[packet->payload_length];
  uint8_t* payload_ptr = payload;
  for (int i = 0; i < num_payloads; ++i) {
    // Write the RED headers.
    if (i == num_payloads - 1) {
      // Special case for last payload.
      *payload_ptr = payload_types[i] & 0x7F;  // F = 0;
      ++payload_ptr;
      break;
    }
    *payload_ptr = payload_types[i] & 0x7F;
    // Not the last block; set F = 1.
    *payload_ptr |= 0x80;
    ++payload_ptr;
    int this_offset = (num_payloads - i - 1) * timestamp_offset;
    *payload_ptr = this_offset >> 6;
    ++payload_ptr;
    assert(kPayloadLength <= 1023);  // Max length described by 10 bits.
    *payload_ptr = ((this_offset & 0x3F) << 2) | (kPayloadLength >> 8);
    ++payload_ptr;
    *payload_ptr = kPayloadLength & 0xFF;
    ++payload_ptr;
  }
  for (int i = 0; i < num_payloads; ++i) {
    // Write |i| to all bytes in each payload.
    memset(payload_ptr, i, kPayloadLength);
    payload_ptr += kPayloadLength;
  }
  packet->payload = payload;
  return packet;
}


// A possible Opus packet that contains FEC is the following.
// The frame is 20 ms in duration.
//
// 0                   1                   2                   3
// 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |0|0|0|0|1|0|0|0|x|1|x|x|x|x|x|x|x|                             |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                             |
// |                    Compressed frame 1 (N-2 bytes)...          :
// :                                                               |
// |                                                               |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
Packet* CreateOpusFecPacket(uint8_t payload_type, int payload_length,
                            uint8_t payload_value) {
  Packet* packet = new Packet;
  packet->header.payloadType = payload_type;
  packet->header.timestamp = kBaseTimestamp;
  packet->header.sequenceNumber = kSequenceNumber;
  packet->payload_length = payload_length;
  uint8_t* payload = new uint8_t[packet->payload_length];
  payload[0] = 0x08;
  payload[1] = 0x40;
  memset(&payload[2], payload_value, payload_length - 2);
  packet->payload = payload;
  return packet;
}

// Create a packet with all payload bytes set to |payload_value|.
Packet* CreatePacket(uint8_t payload_type, int payload_length,
                     uint8_t payload_value) {
  Packet* packet = new Packet;
  packet->header.payloadType = payload_type;
  packet->header.timestamp = kBaseTimestamp;
  packet->header.sequenceNumber = kSequenceNumber;
  packet->payload_length = payload_length;
  uint8_t* payload = new uint8_t[packet->payload_length];
  memset(payload, payload_value, payload_length);
  packet->payload = payload;
  return packet;
}

// Checks that |packet| has the attributes given in the remaining parameters.
void VerifyPacket(const Packet* packet,
                  int payload_length,
                  uint8_t payload_type,
                  uint16_t sequence_number,
                  uint32_t timestamp,
                  uint8_t payload_value,
                  bool primary = true) {
  EXPECT_EQ(payload_length, packet->payload_length);
  EXPECT_EQ(payload_type, packet->header.payloadType);
  EXPECT_EQ(sequence_number, packet->header.sequenceNumber);
  EXPECT_EQ(timestamp, packet->header.timestamp);
  EXPECT_EQ(primary, packet->primary);
  ASSERT_FALSE(packet->payload == NULL);
  for (int i = 0; i < packet->payload_length; ++i) {
    EXPECT_EQ(payload_value, packet->payload[i]);
  }
}

// Start of test definitions.

TEST(PayloadSplitter, CreateAndDestroy) {
  PayloadSplitter* splitter = new PayloadSplitter;
  delete splitter;
}

// Packet A is split into A1 and A2.
TEST(RedPayloadSplitter, OnePacketTwoPayloads) {
  uint8_t payload_types[] = {0, 0};
  const int kTimestampOffset = 160;
  Packet* packet = CreateRedPayload(2, payload_types, kTimestampOffset);
  PacketList packet_list;
  packet_list.push_back(packet);
  PayloadSplitter splitter;
  EXPECT_EQ(PayloadSplitter::kOK, splitter.SplitRed(&packet_list));
  ASSERT_EQ(2u, packet_list.size());
  // Check first packet. The first in list should always be the primary payload.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[1], kSequenceNumber,
               kBaseTimestamp, 1, true);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();
  // Check second packet.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[0], kSequenceNumber,
               kBaseTimestamp - kTimestampOffset, 0, false);
  delete [] packet->payload;
  delete packet;
}

// Packets A and B are not split at all. Only the RED header in each packet is
// removed.
TEST(RedPayloadSplitter, TwoPacketsOnePayload) {
  uint8_t payload_types[] = {0};
  const int kTimestampOffset = 160;
  // Create first packet, with a single RED payload.
  Packet* packet = CreateRedPayload(1, payload_types, kTimestampOffset);
  PacketList packet_list;
  packet_list.push_back(packet);
  // Create second packet, with a single RED payload.
  packet = CreateRedPayload(1, payload_types, kTimestampOffset);
  // Manually change timestamp and sequence number of second packet.
  packet->header.timestamp += kTimestampOffset;
  packet->header.sequenceNumber++;
  packet_list.push_back(packet);
  PayloadSplitter splitter;
  EXPECT_EQ(PayloadSplitter::kOK, splitter.SplitRed(&packet_list));
  ASSERT_EQ(2u, packet_list.size());
  // Check first packet.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[0], kSequenceNumber,
               kBaseTimestamp, 0, true);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();
  // Check second packet.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[0], kSequenceNumber + 1,
               kBaseTimestamp + kTimestampOffset, 0, true);
  delete [] packet->payload;
  delete packet;
}

// Packets A and B are split into packets A1, A2, A3, B1, B2, B3, with
// attributes as follows:
//
//                  A1*   A2    A3    B1*   B2    B3
// Payload type     0     1     2     0     1     2
// Timestamp        b     b-o   b-2o  b+o   b     b-o
// Sequence number  0     0     0     1     1     1
//
// b = kBaseTimestamp, o = kTimestampOffset, * = primary.
TEST(RedPayloadSplitter, TwoPacketsThreePayloads) {
  uint8_t payload_types[] = {2, 1, 0};  // Primary is the last one.
  const int kTimestampOffset = 160;
  // Create first packet, with 3 RED payloads.
  Packet* packet = CreateRedPayload(3, payload_types, kTimestampOffset);
  PacketList packet_list;
  packet_list.push_back(packet);
  // Create first packet, with 3 RED payloads.
  packet = CreateRedPayload(3, payload_types, kTimestampOffset);
  // Manually change timestamp and sequence number of second packet.
  packet->header.timestamp += kTimestampOffset;
  packet->header.sequenceNumber++;
  packet_list.push_back(packet);
  PayloadSplitter splitter;
  EXPECT_EQ(PayloadSplitter::kOK, splitter.SplitRed(&packet_list));
  ASSERT_EQ(6u, packet_list.size());
  // Check first packet, A1.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[2], kSequenceNumber,
               kBaseTimestamp, 2, true);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();
  // Check second packet, A2.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[1], kSequenceNumber,
               kBaseTimestamp - kTimestampOffset, 1, false);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();
  // Check third packet, A3.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[0], kSequenceNumber,
               kBaseTimestamp - 2 * kTimestampOffset, 0, false);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();
  // Check fourth packet, B1.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[2], kSequenceNumber + 1,
               kBaseTimestamp + kTimestampOffset, 2, true);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();
  // Check fifth packet, B2.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[1], kSequenceNumber + 1,
               kBaseTimestamp, 1, false);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();
  // Check sixth packet, B3.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[0], kSequenceNumber + 1,
               kBaseTimestamp - kTimestampOffset, 0, false);
  delete [] packet->payload;
  delete packet;
}

// Creates a list with 4 packets with these payload types:
// 0 = CNGnb
// 1 = PCMu
// 2 = DTMF (AVT)
// 3 = iLBC
// We expect the method CheckRedPayloads to discard the iLBC packet, since it
// is a non-CNG, non-DTMF payload of another type than the first speech payload
// found in the list (which is PCMu).
TEST(RedPayloadSplitter, CheckRedPayloads) {
  PacketList packet_list;
  for (int i = 0; i <= 3; ++i) {
    // Create packet with payload type |i|, payload length 10 bytes, all 0.
    Packet* packet = CreatePacket(i, 10, 0);
    packet_list.push_back(packet);
  }

  // Use a real DecoderDatabase object here instead of a mock, since it is
  // easier to just register the payload types and let the actual implementation
  // do its job.
  DecoderDatabase decoder_database;
  decoder_database.RegisterPayload(0, kDecoderCNGnb);
  decoder_database.RegisterPayload(1, kDecoderPCMu);
  decoder_database.RegisterPayload(2, kDecoderAVT);
  decoder_database.RegisterPayload(3, kDecoderILBC);

  PayloadSplitter splitter;
  splitter.CheckRedPayloads(&packet_list, decoder_database);

  ASSERT_EQ(3u, packet_list.size());  // Should have dropped the last packet.
  // Verify packets. The loop verifies that payload types 0, 1, and 2 are in the
  // list.
  for (int i = 0; i <= 2; ++i) {
    Packet* packet = packet_list.front();
    VerifyPacket(packet, 10, i, kSequenceNumber, kBaseTimestamp, 0, true);
    delete [] packet->payload;
    delete packet;
    packet_list.pop_front();
  }
  EXPECT_TRUE(packet_list.empty());
}

// Packet A is split into A1, A2 and A3. But the length parameter is off, so
// the last payloads should be discarded.
TEST(RedPayloadSplitter, WrongPayloadLength) {
  uint8_t payload_types[] = {0, 0, 0};
  const int kTimestampOffset = 160;
  Packet* packet = CreateRedPayload(3, payload_types, kTimestampOffset);
  // Manually tamper with the payload length of the packet.
  // This is one byte too short for the second payload (out of three).
  // We expect only the first payload to be returned.
  packet->payload_length -= kPayloadLength + 1;
  PacketList packet_list;
  packet_list.push_back(packet);
  PayloadSplitter splitter;
  EXPECT_EQ(PayloadSplitter::kRedLengthMismatch,
            splitter.SplitRed(&packet_list));
  ASSERT_EQ(1u, packet_list.size());
  // Check first packet.
  packet = packet_list.front();
  VerifyPacket(packet, kPayloadLength, payload_types[0], kSequenceNumber,
               kBaseTimestamp - 2 * kTimestampOffset, 0, false);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();
}

// Test that iSAC, iSAC-swb, RED, DTMF, CNG, and "Arbitrary" payloads do not
// get split.
TEST(AudioPayloadSplitter, NonSplittable) {
  // Set up packets with different RTP payload types. The actual values do not
  // matter, since we are mocking the decoder database anyway.
  PacketList packet_list;
  for (int i = 0; i < 6; ++i) {
    // Let the payload type be |i|, and the payload value 10 * |i|.
    packet_list.push_back(CreatePacket(i, kPayloadLength, 10 * i));
  }

  MockDecoderDatabase decoder_database;
  // Tell the mock decoder database to return DecoderInfo structs with different
  // codec types.
  // Use scoped pointers to avoid having to delete them later.
  scoped_ptr<DecoderDatabase::DecoderInfo> info0(
      new DecoderDatabase::DecoderInfo(kDecoderISAC, 16000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(0))
      .WillRepeatedly(Return(info0.get()));
  scoped_ptr<DecoderDatabase::DecoderInfo> info1(
      new DecoderDatabase::DecoderInfo(kDecoderISACswb, 32000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(1))
      .WillRepeatedly(Return(info1.get()));
  scoped_ptr<DecoderDatabase::DecoderInfo> info2(
      new DecoderDatabase::DecoderInfo(kDecoderRED, 8000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(2))
      .WillRepeatedly(Return(info2.get()));
  scoped_ptr<DecoderDatabase::DecoderInfo> info3(
      new DecoderDatabase::DecoderInfo(kDecoderAVT, 8000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(3))
      .WillRepeatedly(Return(info3.get()));
  scoped_ptr<DecoderDatabase::DecoderInfo> info4(
      new DecoderDatabase::DecoderInfo(kDecoderCNGnb, 8000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(4))
      .WillRepeatedly(Return(info4.get()));
  scoped_ptr<DecoderDatabase::DecoderInfo> info5(
      new DecoderDatabase::DecoderInfo(kDecoderArbitrary, 8000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(5))
      .WillRepeatedly(Return(info5.get()));

  PayloadSplitter splitter;
  EXPECT_EQ(0, splitter.SplitAudio(&packet_list, decoder_database));
  EXPECT_EQ(6u, packet_list.size());

  // Check that all payloads are intact.
  uint8_t payload_type = 0;
  PacketList::iterator it = packet_list.begin();
  while (it != packet_list.end()) {
    VerifyPacket((*it), kPayloadLength, payload_type, kSequenceNumber,
                 kBaseTimestamp, 10 * payload_type);
    ++payload_type;
    delete [] (*it)->payload;
    delete (*it);
    it = packet_list.erase(it);
  }

  // The destructor is called when decoder_database goes out of scope.
  EXPECT_CALL(decoder_database, Die());
}

// Test unknown payload type.
TEST(AudioPayloadSplitter, UnknownPayloadType) {
  PacketList packet_list;
  static const uint8_t kPayloadType = 17;  // Just a random number.
  int kPayloadLengthBytes = 4711;  // Random number.
  packet_list.push_back(CreatePacket(kPayloadType, kPayloadLengthBytes, 0));

  MockDecoderDatabase decoder_database;
  // Tell the mock decoder database to return NULL when asked for decoder info.
  // This signals that the decoder database does not recognize the payload type.
  EXPECT_CALL(decoder_database, GetDecoderInfo(kPayloadType))
      .WillRepeatedly(ReturnNull());

  PayloadSplitter splitter;
  EXPECT_EQ(PayloadSplitter::kUnknownPayloadType,
            splitter.SplitAudio(&packet_list, decoder_database));
  EXPECT_EQ(1u, packet_list.size());


  // Delete the packets and payloads to avoid having the test leak memory.
  PacketList::iterator it = packet_list.begin();
  while (it != packet_list.end()) {
    delete [] (*it)->payload;
    delete (*it);
    it = packet_list.erase(it);
  }

  // The destructor is called when decoder_database goes out of scope.
  EXPECT_CALL(decoder_database, Die());
}

class SplitBySamplesTest : public ::testing::TestWithParam<NetEqDecoder> {
 protected:
  virtual void SetUp() {
    decoder_type_ = GetParam();
    switch (decoder_type_) {
      case kDecoderPCMu:
      case kDecoderPCMa:
        bytes_per_ms_ = 8;
        samples_per_ms_ = 8;
        break;
      case kDecoderPCMu_2ch:
      case kDecoderPCMa_2ch:
        bytes_per_ms_ = 2 * 8;
        samples_per_ms_ = 8;
        break;
      case kDecoderG722:
        bytes_per_ms_ = 8;
        samples_per_ms_ = 16;
        break;
      case kDecoderPCM16B:
        bytes_per_ms_ = 16;
        samples_per_ms_ = 8;
        break;
      case kDecoderPCM16Bwb:
        bytes_per_ms_ = 32;
        samples_per_ms_ = 16;
        break;
      case kDecoderPCM16Bswb32kHz:
        bytes_per_ms_ = 64;
        samples_per_ms_ = 32;
        break;
      case kDecoderPCM16Bswb48kHz:
        bytes_per_ms_ = 96;
        samples_per_ms_ = 48;
        break;
      case kDecoderPCM16B_2ch:
        bytes_per_ms_ = 2 * 16;
        samples_per_ms_ = 8;
        break;
      case kDecoderPCM16Bwb_2ch:
        bytes_per_ms_ = 2 * 32;
        samples_per_ms_ = 16;
        break;
      case kDecoderPCM16Bswb32kHz_2ch:
        bytes_per_ms_ = 2 * 64;
        samples_per_ms_ = 32;
        break;
      case kDecoderPCM16Bswb48kHz_2ch:
        bytes_per_ms_ = 2 * 96;
        samples_per_ms_ = 48;
        break;
      case kDecoderPCM16B_5ch:
        bytes_per_ms_ = 5 * 16;
        samples_per_ms_ = 8;
        break;
      default:
        assert(false);
        break;
    }
  }
  int bytes_per_ms_;
  int samples_per_ms_;
  NetEqDecoder decoder_type_;
};

// Test splitting sample-based payloads.
TEST_P(SplitBySamplesTest, PayloadSizes) {
  PacketList packet_list;
  static const uint8_t kPayloadType = 17;  // Just a random number.
  for (int payload_size_ms = 10; payload_size_ms <= 60; payload_size_ms += 10) {
    // The payload values are set to be the same as the payload_size, so that
    // one can distinguish from which packet the split payloads come from.
    int payload_size_bytes = payload_size_ms * bytes_per_ms_;
    packet_list.push_back(CreatePacket(kPayloadType, payload_size_bytes,
                                       payload_size_ms));
  }

  MockDecoderDatabase decoder_database;
  // Tell the mock decoder database to return DecoderInfo structs with different
  // codec types.
  // Use scoped pointers to avoid having to delete them later.
  // (Sample rate is set to 8000 Hz, but does not matter.)
  scoped_ptr<DecoderDatabase::DecoderInfo> info(
      new DecoderDatabase::DecoderInfo(decoder_type_, 8000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(kPayloadType))
      .WillRepeatedly(Return(info.get()));

  PayloadSplitter splitter;
  EXPECT_EQ(0, splitter.SplitAudio(&packet_list, decoder_database));
  // The payloads are expected to be split as follows:
  // 10 ms -> 10 ms
  // 20 ms -> 20 ms
  // 30 ms -> 30 ms
  // 40 ms -> 20 + 20 ms
  // 50 ms -> 25 + 25 ms
  // 60 ms -> 30 + 30 ms
  int expected_size_ms[] = {10, 20, 30, 20, 20, 25, 25, 30, 30};
  int expected_payload_value[] = {10, 20, 30, 40, 40, 50, 50, 60, 60};
  int expected_timestamp_offset_ms[] = {0, 0, 0, 0, 20, 0, 25, 0, 30};
  size_t expected_num_packets =
      sizeof(expected_size_ms) / sizeof(expected_size_ms[0]);
  EXPECT_EQ(expected_num_packets, packet_list.size());

  PacketList::iterator it = packet_list.begin();
  int i = 0;
  while (it != packet_list.end()) {
    int length_bytes = expected_size_ms[i] * bytes_per_ms_;
    uint32_t expected_timestamp = kBaseTimestamp +
        expected_timestamp_offset_ms[i] * samples_per_ms_;
    VerifyPacket((*it), length_bytes, kPayloadType, kSequenceNumber,
                 expected_timestamp, expected_payload_value[i]);
    delete [] (*it)->payload;
    delete (*it);
    it = packet_list.erase(it);
    ++i;
  }

  // The destructor is called when decoder_database goes out of scope.
  EXPECT_CALL(decoder_database, Die());
}

INSTANTIATE_TEST_CASE_P(
    PayloadSplitter, SplitBySamplesTest,
    ::testing::Values(kDecoderPCMu, kDecoderPCMa, kDecoderPCMu_2ch,
                      kDecoderPCMa_2ch, kDecoderG722, kDecoderPCM16B,
                      kDecoderPCM16Bwb, kDecoderPCM16Bswb32kHz,
                      kDecoderPCM16Bswb48kHz, kDecoderPCM16B_2ch,
                      kDecoderPCM16Bwb_2ch, kDecoderPCM16Bswb32kHz_2ch,
                      kDecoderPCM16Bswb48kHz_2ch, kDecoderPCM16B_5ch));


class SplitIlbcTest : public ::testing::TestWithParam<std::pair<int, int> > {
 protected:
  virtual void SetUp() {
    const std::pair<int, int> parameters = GetParam();
    num_frames_ = parameters.first;
    frame_length_ms_ = parameters.second;
    frame_length_bytes_ = (frame_length_ms_ == 20) ? 38 : 50;
  }
  size_t num_frames_;
  int frame_length_ms_;
  int frame_length_bytes_;
};

// Test splitting sample-based payloads.
TEST_P(SplitIlbcTest, NumFrames) {
  PacketList packet_list;
  static const uint8_t kPayloadType = 17;  // Just a random number.
  const int frame_length_samples = frame_length_ms_ * 8;
  int payload_length_bytes = frame_length_bytes_ * num_frames_;
  Packet* packet = CreatePacket(kPayloadType, payload_length_bytes, 0);
  // Fill payload with increasing integers {0, 1, 2, ...}.
  for (int i = 0; i < packet->payload_length; ++i) {
    packet->payload[i] = static_cast<uint8_t>(i);
  }
  packet_list.push_back(packet);

  MockDecoderDatabase decoder_database;
  // Tell the mock decoder database to return DecoderInfo structs with different
  // codec types.
  // Use scoped pointers to avoid having to delete them later.
  scoped_ptr<DecoderDatabase::DecoderInfo> info(
      new DecoderDatabase::DecoderInfo(kDecoderILBC, 8000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(kPayloadType))
      .WillRepeatedly(Return(info.get()));

  PayloadSplitter splitter;
  EXPECT_EQ(0, splitter.SplitAudio(&packet_list, decoder_database));
  EXPECT_EQ(num_frames_, packet_list.size());

  PacketList::iterator it = packet_list.begin();
  int frame_num = 0;
  uint8_t payload_value = 0;
  while (it != packet_list.end()) {
    Packet* packet = (*it);
    EXPECT_EQ(kBaseTimestamp + frame_length_samples * frame_num,
              packet->header.timestamp);
    EXPECT_EQ(frame_length_bytes_, packet->payload_length);
    EXPECT_EQ(kPayloadType, packet->header.payloadType);
    EXPECT_EQ(kSequenceNumber, packet->header.sequenceNumber);
    EXPECT_EQ(true, packet->primary);
    ASSERT_FALSE(packet->payload == NULL);
    for (int i = 0; i < packet->payload_length; ++i) {
      EXPECT_EQ(payload_value, packet->payload[i]);
      ++payload_value;
    }
    delete [] (*it)->payload;
    delete (*it);
    it = packet_list.erase(it);
    ++frame_num;
  }

  // The destructor is called when decoder_database goes out of scope.
  EXPECT_CALL(decoder_database, Die());
}

// Test 1 through 5 frames of 20 and 30 ms size.
// Also test the maximum number of frames in one packet for 20 and 30 ms.
// The maximum is defined by the largest payload length that can be uniquely
// resolved to a frame size of either 38 bytes (20 ms) or 50 bytes (30 ms).
INSTANTIATE_TEST_CASE_P(
    PayloadSplitter, SplitIlbcTest,
    ::testing::Values(std::pair<int, int>(1, 20),  // 1 frame, 20 ms.
                      std::pair<int, int>(2, 20),  // 2 frames, 20 ms.
                      std::pair<int, int>(3, 20),  // And so on.
                      std::pair<int, int>(4, 20),
                      std::pair<int, int>(5, 20),
                      std::pair<int, int>(24, 20),
                      std::pair<int, int>(1, 30),
                      std::pair<int, int>(2, 30),
                      std::pair<int, int>(3, 30),
                      std::pair<int, int>(4, 30),
                      std::pair<int, int>(5, 30),
                      std::pair<int, int>(18, 30)));

// Test too large payload size.
TEST(IlbcPayloadSplitter, TooLargePayload) {
  PacketList packet_list;
  static const uint8_t kPayloadType = 17;  // Just a random number.
  int kPayloadLengthBytes = 950;
  Packet* packet = CreatePacket(kPayloadType, kPayloadLengthBytes, 0);
  packet_list.push_back(packet);

  MockDecoderDatabase decoder_database;
  scoped_ptr<DecoderDatabase::DecoderInfo> info(
      new DecoderDatabase::DecoderInfo(kDecoderILBC, 8000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(kPayloadType))
      .WillRepeatedly(Return(info.get()));

  PayloadSplitter splitter;
  EXPECT_EQ(PayloadSplitter::kTooLargePayload,
            splitter.SplitAudio(&packet_list, decoder_database));
  EXPECT_EQ(1u, packet_list.size());

  // Delete the packets and payloads to avoid having the test leak memory.
  PacketList::iterator it = packet_list.begin();
  while (it != packet_list.end()) {
    delete [] (*it)->payload;
    delete (*it);
    it = packet_list.erase(it);
  }

  // The destructor is called when decoder_database goes out of scope.
  EXPECT_CALL(decoder_database, Die());
}

// Payload not an integer number of frames.
TEST(IlbcPayloadSplitter, UnevenPayload) {
  PacketList packet_list;
  static const uint8_t kPayloadType = 17;  // Just a random number.
  int kPayloadLengthBytes = 39;  // Not an even number of frames.
  Packet* packet = CreatePacket(kPayloadType, kPayloadLengthBytes, 0);
  packet_list.push_back(packet);

  MockDecoderDatabase decoder_database;
  scoped_ptr<DecoderDatabase::DecoderInfo> info(
      new DecoderDatabase::DecoderInfo(kDecoderILBC, 8000, NULL, false));
  EXPECT_CALL(decoder_database, GetDecoderInfo(kPayloadType))
      .WillRepeatedly(Return(info.get()));

  PayloadSplitter splitter;
  EXPECT_EQ(PayloadSplitter::kFrameSplitError,
            splitter.SplitAudio(&packet_list, decoder_database));
  EXPECT_EQ(1u, packet_list.size());

  // Delete the packets and payloads to avoid having the test leak memory.
  PacketList::iterator it = packet_list.begin();
  while (it != packet_list.end()) {
    delete [] (*it)->payload;
    delete (*it);
    it = packet_list.erase(it);
  }

  // The destructor is called when decoder_database goes out of scope.
  EXPECT_CALL(decoder_database, Die());
}

TEST(FecPayloadSplitter, MixedPayload) {
  PacketList packet_list;
  DecoderDatabase decoder_database;

  decoder_database.RegisterPayload(0, kDecoderOpus);
  decoder_database.RegisterPayload(1, kDecoderPCMu);

  Packet* packet = CreateOpusFecPacket(0, 10, 0xFF);
  packet_list.push_back(packet);

  packet = CreatePacket(0, 10, 0); // Non-FEC Opus payload.
  packet_list.push_back(packet);

  packet = CreatePacket(1, 10, 0); // Non-Opus payload.
  packet_list.push_back(packet);

  PayloadSplitter splitter;
  EXPECT_EQ(PayloadSplitter::kOK,
            splitter.SplitFec(&packet_list, &decoder_database));
  EXPECT_EQ(4u, packet_list.size());

  // Check first packet.
  packet = packet_list.front();
  EXPECT_EQ(0, packet->header.payloadType);
  EXPECT_EQ(kBaseTimestamp - 20 * 48, packet->header.timestamp);
  EXPECT_EQ(10, packet->payload_length);
  EXPECT_FALSE(packet->primary);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();

  // Check second packet.
  packet = packet_list.front();
  EXPECT_EQ(0, packet->header.payloadType);
  EXPECT_EQ(kBaseTimestamp, packet->header.timestamp);
  EXPECT_EQ(10, packet->payload_length);
  EXPECT_TRUE(packet->primary);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();

  // Check third packet.
  packet = packet_list.front();
  VerifyPacket(packet, 10, 0, kSequenceNumber, kBaseTimestamp, 0, true);
  delete [] packet->payload;
  delete packet;
  packet_list.pop_front();

  // Check fourth packet.
  packet = packet_list.front();
  VerifyPacket(packet, 10, 1, kSequenceNumber, kBaseTimestamp, 0, true);
  delete [] packet->payload;
  delete packet;
}

}  // namespace webrtc
