/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for PacketBuffer class.

#include "webrtc/modules/audio_coding/neteq/packet_buffer.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/packet.h"

using ::testing::Return;
using ::testing::_;

namespace webrtc {

// Helper class to generate packets. Packets must be deleted by the user.
class PacketGenerator {
 public:
  PacketGenerator(uint16_t seq_no, uint32_t ts, uint8_t pt, int frame_size);
  virtual ~PacketGenerator() {}
  Packet* NextPacket(int payload_size_bytes);
  void SkipPacket();

  uint16_t seq_no_;
  uint32_t ts_;
  uint8_t pt_;
  int frame_size_;
};

PacketGenerator::PacketGenerator(uint16_t seq_no, uint32_t ts, uint8_t pt,
                                 int frame_size)
    : seq_no_(seq_no),
      ts_(ts),
      pt_(pt),
      frame_size_(frame_size) {
}

Packet* PacketGenerator::NextPacket(int payload_size_bytes) {
  Packet* packet = new Packet;
  packet->header.sequenceNumber = seq_no_;
  packet->header.timestamp = ts_;
  packet->header.payloadType = pt_;
  packet->header.markerBit = false;
  packet->header.ssrc = 0x12345678;
  packet->header.numCSRCs = 0;
  packet->header.paddingLength = 0;
  packet->payload_length = payload_size_bytes;
  packet->primary = true;
  packet->payload = new uint8_t[payload_size_bytes];
  ++seq_no_;
  ts_ += frame_size_;
  return packet;
}

void PacketGenerator::SkipPacket() {
  ++seq_no_;
  ts_ += frame_size_;
}


// Start of test definitions.

TEST(PacketBuffer, CreateAndDestroy) {
  PacketBuffer* buffer = new PacketBuffer(10);  // 10 packets.
  EXPECT_TRUE(buffer->Empty());
  delete buffer;
}

TEST(PacketBuffer, InsertPacket) {
  PacketBuffer buffer(10);  // 10 packets.
  PacketGenerator gen(17u, 4711u, 0, 10);

  const int payload_len = 100;
  Packet* packet = gen.NextPacket(payload_len);

  EXPECT_EQ(0, buffer.InsertPacket(packet));
  uint32_t next_ts;
  EXPECT_EQ(PacketBuffer::kOK, buffer.NextTimestamp(&next_ts));
  EXPECT_EQ(4711u, next_ts);
  EXPECT_FALSE(buffer.Empty());
  EXPECT_EQ(1, buffer.NumPacketsInBuffer());
  const RTPHeader* hdr = buffer.NextRtpHeader();
  EXPECT_EQ(&(packet->header), hdr);  // Compare pointer addresses.

  // Do not explicitly flush buffer or delete packet to test that it is deleted
  // with the buffer. (Tested with Valgrind or similar tool.)
}

// Test to flush buffer.
TEST(PacketBuffer, FlushBuffer) {
  PacketBuffer buffer(10);  // 10 packets.
  PacketGenerator gen(0, 0, 0, 10);
  const int payload_len = 10;

  // Insert 10 small packets; should be ok.
  for (int i = 0; i < 10; ++i) {
    Packet* packet = gen.NextPacket(payload_len);
    EXPECT_EQ(PacketBuffer::kOK, buffer.InsertPacket(packet));
  }
  EXPECT_EQ(10, buffer.NumPacketsInBuffer());
  EXPECT_FALSE(buffer.Empty());

  buffer.Flush();
  // Buffer should delete the payloads itself.
  EXPECT_EQ(0, buffer.NumPacketsInBuffer());
  EXPECT_TRUE(buffer.Empty());
}

// Test to fill the buffer over the limits, and verify that it flushes.
TEST(PacketBuffer, OverfillBuffer) {
  PacketBuffer buffer(10);  // 10 packets.
  PacketGenerator gen(0, 0, 0, 10);

  // Insert 10 small packets; should be ok.
  const int payload_len = 10;
  int i;
  for (i = 0; i < 10; ++i) {
    Packet* packet = gen.NextPacket(payload_len);
    EXPECT_EQ(PacketBuffer::kOK, buffer.InsertPacket(packet));
  }
  EXPECT_EQ(10, buffer.NumPacketsInBuffer());
  uint32_t next_ts;
  EXPECT_EQ(PacketBuffer::kOK, buffer.NextTimestamp(&next_ts));
  EXPECT_EQ(0u, next_ts);  // Expect first inserted packet to be first in line.

  // Insert 11th packet; should flush the buffer and insert it after flushing.
  Packet* packet = gen.NextPacket(payload_len);
  EXPECT_EQ(PacketBuffer::kFlushed, buffer.InsertPacket(packet));
  EXPECT_EQ(1, buffer.NumPacketsInBuffer());
  EXPECT_EQ(PacketBuffer::kOK, buffer.NextTimestamp(&next_ts));
  // Expect last inserted packet to be first in line.
  EXPECT_EQ(packet->header.timestamp, next_ts);

  // Flush buffer to delete all packets.
  buffer.Flush();
}

// Test inserting a list of packets.
TEST(PacketBuffer, InsertPacketList) {
  PacketBuffer buffer(10);  // 10 packets.
  PacketGenerator gen(0, 0, 0, 10);
  PacketList list;
  const int payload_len = 10;

  // Insert 10 small packets.
  for (int i = 0; i < 10; ++i) {
    Packet* packet = gen.NextPacket(payload_len);
    list.push_back(packet);
  }

  MockDecoderDatabase decoder_database;
  EXPECT_CALL(decoder_database, IsComfortNoise(0))
      .WillRepeatedly(Return(false));
  EXPECT_CALL(decoder_database, IsDtmf(0))
      .WillRepeatedly(Return(false));
  uint8_t current_pt = 0xFF;
  uint8_t current_cng_pt = 0xFF;
  EXPECT_EQ(PacketBuffer::kOK, buffer.InsertPacketList(&list,
                                                       decoder_database,
                                                       &current_pt,
                                                       &current_cng_pt));
  EXPECT_TRUE(list.empty());  // The PacketBuffer should have depleted the list.
  EXPECT_EQ(10, buffer.NumPacketsInBuffer());
  EXPECT_EQ(0, current_pt);  // Current payload type changed to 0.
  EXPECT_EQ(0xFF, current_cng_pt);  // CNG payload type not changed.

  buffer.Flush();  // Clean up.

  EXPECT_CALL(decoder_database, Die());  // Called when object is deleted.
}

// Test inserting a list of packets. Last packet is of a different payload type.
// Expecting the buffer to flush.
// TODO(hlundin): Remove this test when legacy operation is no longer needed.
TEST(PacketBuffer, InsertPacketListChangePayloadType) {
  PacketBuffer buffer(10);  // 10 packets.
  PacketGenerator gen(0, 0, 0, 10);
  PacketList list;
  const int payload_len = 10;

  // Insert 10 small packets.
  for (int i = 0; i < 10; ++i) {
    Packet* packet = gen.NextPacket(payload_len);
    list.push_back(packet);
  }
  // Insert 11th packet of another payload type (not CNG).
  Packet* packet = gen.NextPacket(payload_len);
  packet->header.payloadType = 1;
  list.push_back(packet);


  MockDecoderDatabase decoder_database;
  EXPECT_CALL(decoder_database, IsComfortNoise(_))
      .WillRepeatedly(Return(false));
  EXPECT_CALL(decoder_database, IsDtmf(_))
      .WillRepeatedly(Return(false));
  uint8_t current_pt = 0xFF;
  uint8_t current_cng_pt = 0xFF;
  EXPECT_EQ(PacketBuffer::kFlushed, buffer.InsertPacketList(&list,
                                                            decoder_database,
                                                            &current_pt,
                                                            &current_cng_pt));
  EXPECT_TRUE(list.empty());  // The PacketBuffer should have depleted the list.
  EXPECT_EQ(1, buffer.NumPacketsInBuffer());  // Only the last packet.
  EXPECT_EQ(1, current_pt);  // Current payload type changed to 0.
  EXPECT_EQ(0xFF, current_cng_pt);  // CNG payload type not changed.

  buffer.Flush();  // Clean up.

  EXPECT_CALL(decoder_database, Die());  // Called when object is deleted.
}

// Test inserting a number of packets, and verifying correct extraction order.
// The packets inserted are as follows:
// Packet no.  Seq. no.    Primary TS    Secondary TS
// 0           0xFFFD      0xFFFFFFD7    -
// 1           0xFFFE      0xFFFFFFE1    0xFFFFFFD7
// 2           0xFFFF      0xFFFFFFEB    0xFFFFFFE1
// 3           0x0000      0xFFFFFFF5    0xFFFFFFEB
// 4           0x0001      0xFFFFFFFF    0xFFFFFFF5
// 5           0x0002      0x0000000A    0xFFFFFFFF
// 6  MISSING--0x0003------0x00000014----0x0000000A--MISSING
// 7           0x0004      0x0000001E    0x00000014
// 8           0x0005      0x00000028    0x0000001E
// 9           0x0006      0x00000032    0x00000028
TEST(PacketBuffer, ExtractOrderRedundancy) {
  PacketBuffer buffer(100);  // 100 packets.
  const uint32_t ts_increment = 10;  // Samples per packet.
  const uint16_t start_seq_no = 0xFFFF - 2;  // Wraps after 3 packets.
  const uint32_t start_ts = 0xFFFFFFFF -
      4 * ts_increment;  // Wraps after 5 packets.
  const uint8_t primary_pt = 0;
  const uint8_t secondary_pt = 1;
  PacketGenerator gen(start_seq_no, start_ts, primary_pt, ts_increment);
  // Insert secondary payloads too. (Simulating RED.)
  PacketGenerator red_gen(start_seq_no + 1, start_ts, secondary_pt,
                          ts_increment);

  // Insert 9 small packets (skip one).
  for (int i = 0; i < 10; ++i) {
    const int payload_len = 10;
    if (i == 6) {
      // Skip this packet.
      gen.SkipPacket();
      red_gen.SkipPacket();
      continue;
    }
    // Primary payload.
    Packet* packet = gen.NextPacket(payload_len);
    EXPECT_EQ(PacketBuffer::kOK, buffer.InsertPacket(packet));
    if (i >= 1) {
      // Secondary payload.
      packet = red_gen.NextPacket(payload_len);
      packet->primary = false;
      EXPECT_EQ(PacketBuffer::kOK, buffer.InsertPacket(packet));
    }
  }
  EXPECT_EQ(17, buffer.NumPacketsInBuffer());  // 9 primary + 8 secondary

  uint16_t current_seq_no = start_seq_no;
  uint32_t current_ts = start_ts;

  for (int i = 0; i < 10; ++i) {
    // Extract packets.
    int drop_count = 0;
    Packet* packet = buffer.GetNextPacket(&drop_count);
    ASSERT_FALSE(packet == NULL);
    if (i == 6) {
      // Special case for the dropped primary payload.
      // Expect secondary payload, and one step higher sequence number.
      EXPECT_EQ(current_seq_no + 1, packet->header.sequenceNumber);
      EXPECT_EQ(current_ts, packet->header.timestamp);
      EXPECT_FALSE(packet->primary);
      EXPECT_EQ(1, packet->header.payloadType);
      EXPECT_EQ(0, drop_count);
    } else {
      EXPECT_EQ(current_seq_no, packet->header.sequenceNumber);
      EXPECT_EQ(current_ts, packet->header.timestamp);
      EXPECT_TRUE(packet->primary);
      EXPECT_EQ(0, packet->header.payloadType);
      if (i == 5 || i == 9) {
        // No duplicate TS for dropped packet or for last primary payload.
        EXPECT_EQ(0, drop_count);
      } else {
        EXPECT_EQ(1, drop_count);
      }
    }
    ++current_seq_no;
    current_ts += ts_increment;
    delete [] packet->payload;
    delete packet;
  }
}

TEST(PacketBuffer, DiscardPackets) {
  PacketBuffer buffer(100);  // 100 packets.
  const uint16_t start_seq_no = 17;
  const uint32_t start_ts = 4711;
  const uint32_t ts_increment = 10;
  PacketGenerator gen(start_seq_no, start_ts, 0, ts_increment);
  PacketList list;
  const int payload_len = 10;

  // Insert 10 small packets.
  for (int i = 0; i < 10; ++i) {
    Packet* packet = gen.NextPacket(payload_len);
    buffer.InsertPacket(packet);
  }
  EXPECT_EQ(10, buffer.NumPacketsInBuffer());

  // Discard them one by one and make sure that the right packets are at the
  // front of the buffer.
  uint32_t current_ts = start_ts;
  for (int i = 0; i < 10; ++i) {
    uint32_t ts;
    EXPECT_EQ(PacketBuffer::kOK, buffer.NextTimestamp(&ts));
    EXPECT_EQ(current_ts, ts);
    EXPECT_EQ(PacketBuffer::kOK, buffer.DiscardNextPacket());
    current_ts += ts_increment;
  }
  EXPECT_TRUE(buffer.Empty());
}

TEST(PacketBuffer, Reordering) {
  PacketBuffer buffer(100);  // 100 packets.
  const uint16_t start_seq_no = 17;
  const uint32_t start_ts = 4711;
  const uint32_t ts_increment = 10;
  PacketGenerator gen(start_seq_no, start_ts, 0, ts_increment);
  const int payload_len = 10;

  // Generate 10 small packets and insert them into a PacketList. Insert every
  // odd packet to the front, and every even packet to the back, thus creating
  // a (rather strange) reordering.
  PacketList list;
  for (int i = 0; i < 10; ++i) {
    Packet* packet = gen.NextPacket(payload_len);
    if (i % 2) {
      list.push_front(packet);
    } else {
      list.push_back(packet);
    }
  }

  MockDecoderDatabase decoder_database;
  EXPECT_CALL(decoder_database, IsComfortNoise(0))
      .WillRepeatedly(Return(false));
  EXPECT_CALL(decoder_database, IsDtmf(0))
      .WillRepeatedly(Return(false));
  uint8_t current_pt = 0xFF;
  uint8_t current_cng_pt = 0xFF;

  EXPECT_EQ(PacketBuffer::kOK, buffer.InsertPacketList(&list,
                                                       decoder_database,
                                                       &current_pt,
                                                       &current_cng_pt));
  EXPECT_EQ(10, buffer.NumPacketsInBuffer());

  // Extract them and make sure that come out in the right order.
  uint32_t current_ts = start_ts;
  for (int i = 0; i < 10; ++i) {
    Packet* packet = buffer.GetNextPacket(NULL);
    ASSERT_FALSE(packet == NULL);
    EXPECT_EQ(current_ts, packet->header.timestamp);
    current_ts += ts_increment;
    delete [] packet->payload;
    delete packet;
  }
  EXPECT_TRUE(buffer.Empty());

  EXPECT_CALL(decoder_database, Die());  // Called when object is deleted.
}

TEST(PacketBuffer, Failures) {
  const uint16_t start_seq_no = 17;
  const uint32_t start_ts = 4711;
  const uint32_t ts_increment = 10;
  int payload_len = 100;
  PacketGenerator gen(start_seq_no, start_ts, 0, ts_increment);

  PacketBuffer* buffer = new PacketBuffer(100);  // 100 packets.
  Packet* packet = NULL;
  EXPECT_EQ(PacketBuffer::kInvalidPacket, buffer->InsertPacket(packet));
  packet = gen.NextPacket(payload_len);
  delete [] packet->payload;
  packet->payload = NULL;
  EXPECT_EQ(PacketBuffer::kInvalidPacket, buffer->InsertPacket(packet));
  // Packet is deleted by the PacketBuffer.

  // Buffer should still be empty. Test all empty-checks.
  uint32_t temp_ts;
  EXPECT_EQ(PacketBuffer::kBufferEmpty, buffer->NextTimestamp(&temp_ts));
  EXPECT_EQ(PacketBuffer::kBufferEmpty,
            buffer->NextHigherTimestamp(0, &temp_ts));
  EXPECT_EQ(NULL, buffer->NextRtpHeader());
  EXPECT_EQ(NULL, buffer->GetNextPacket(NULL));
  EXPECT_EQ(PacketBuffer::kBufferEmpty, buffer->DiscardNextPacket());
  EXPECT_EQ(0, buffer->DiscardOldPackets(0));  // 0 packets discarded.

  // Insert one packet to make the buffer non-empty.
  packet = gen.NextPacket(payload_len);
  EXPECT_EQ(PacketBuffer::kOK, buffer->InsertPacket(packet));
  EXPECT_EQ(PacketBuffer::kInvalidPointer, buffer->NextTimestamp(NULL));
  EXPECT_EQ(PacketBuffer::kInvalidPointer,
            buffer->NextHigherTimestamp(0, NULL));
  delete buffer;

  // Insert packet list of three packets, where the second packet has an invalid
  // payload.  Expect first packet to be inserted, and the remaining two to be
  // discarded.
  buffer = new PacketBuffer(100);  // 100 packets.
  PacketList list;
  list.push_back(gen.NextPacket(payload_len));  // Valid packet.
  packet = gen.NextPacket(payload_len);
  delete [] packet->payload;
  packet->payload = NULL;  // Invalid.
  list.push_back(packet);
  list.push_back(gen.NextPacket(payload_len));  // Valid packet.
  MockDecoderDatabase decoder_database;
  EXPECT_CALL(decoder_database, IsComfortNoise(0))
      .WillRepeatedly(Return(false));
  EXPECT_CALL(decoder_database, IsDtmf(0))
      .WillRepeatedly(Return(false));
  uint8_t current_pt = 0xFF;
  uint8_t current_cng_pt = 0xFF;
  EXPECT_EQ(PacketBuffer::kInvalidPacket,
            buffer->InsertPacketList(&list,
                                     decoder_database,
                                     &current_pt,
                                     &current_cng_pt));
  EXPECT_TRUE(list.empty());  // The PacketBuffer should have depleted the list.
  EXPECT_EQ(1, buffer->NumPacketsInBuffer());
  delete buffer;
  EXPECT_CALL(decoder_database, Die());  // Called when object is deleted.
}

// Test packet comparison function.
// The function should return true if the first packet "goes before" the second.
TEST(PacketBuffer, ComparePackets) {
  PacketGenerator gen(0, 0, 0, 10);
  Packet* a = gen.NextPacket(10);  // SN = 0, TS = 0.
  Packet* b = gen.NextPacket(10);  // SN = 1, TS = 10.
  EXPECT_FALSE(*a == *b);
  EXPECT_TRUE(*a != *b);
  EXPECT_TRUE(*a < *b);
  EXPECT_FALSE(*a > *b);
  EXPECT_TRUE(*a <= *b);
  EXPECT_FALSE(*a >= *b);

  // Testing wrap-around case; 'a' is earlier but has a larger timestamp value.
  a->header.timestamp = 0xFFFFFFFF - 10;
  EXPECT_FALSE(*a == *b);
  EXPECT_TRUE(*a != *b);
  EXPECT_TRUE(*a < *b);
  EXPECT_FALSE(*a > *b);
  EXPECT_TRUE(*a <= *b);
  EXPECT_FALSE(*a >= *b);

  // Test equal packets.
  EXPECT_TRUE(*a == *a);
  EXPECT_FALSE(*a != *a);
  EXPECT_FALSE(*a < *a);
  EXPECT_FALSE(*a > *a);
  EXPECT_TRUE(*a <= *a);
  EXPECT_TRUE(*a >= *a);

  // Test equal timestamps but different sequence numbers (0 and 1).
  a->header.timestamp = b->header.timestamp;
  EXPECT_FALSE(*a == *b);
  EXPECT_TRUE(*a != *b);
  EXPECT_TRUE(*a < *b);
  EXPECT_FALSE(*a > *b);
  EXPECT_TRUE(*a <= *b);
  EXPECT_FALSE(*a >= *b);

  // Test equal timestamps but different sequence numbers (32767 and 1).
  a->header.sequenceNumber = 0xFFFF;
  EXPECT_FALSE(*a == *b);
  EXPECT_TRUE(*a != *b);
  EXPECT_TRUE(*a < *b);
  EXPECT_FALSE(*a > *b);
  EXPECT_TRUE(*a <= *b);
  EXPECT_FALSE(*a >= *b);

  // Test equal timestamps and sequence numbers, but only 'b' is primary.
  a->header.sequenceNumber = b->header.sequenceNumber;
  a->primary = false;
  b->primary = true;
  EXPECT_FALSE(*a == *b);
  EXPECT_TRUE(*a != *b);
  EXPECT_FALSE(*a < *b);
  EXPECT_TRUE(*a > *b);
  EXPECT_FALSE(*a <= *b);
  EXPECT_TRUE(*a >= *b);

  delete [] a->payload;
  delete a;
  delete [] b->payload;
  delete b;
}

// Test the DeleteFirstPacket DeleteAllPackets methods.
TEST(PacketBuffer, DeleteAllPackets) {
  PacketGenerator gen(0, 0, 0, 10);
  PacketList list;
  const int payload_len = 10;

  // Insert 10 small packets.
  for (int i = 0; i < 10; ++i) {
    Packet* packet = gen.NextPacket(payload_len);
    list.push_back(packet);
  }
  EXPECT_TRUE(PacketBuffer::DeleteFirstPacket(&list));
  EXPECT_EQ(9u, list.size());
  PacketBuffer::DeleteAllPackets(&list);
  EXPECT_TRUE(list.empty());
  EXPECT_FALSE(PacketBuffer::DeleteFirstPacket(&list));
}

}  // namespace webrtc
