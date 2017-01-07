/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/timestamp_scaler.h"

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/packet.h"

using ::testing::Return;
using ::testing::ReturnNull;
using ::testing::_;

namespace webrtc {

TEST(TimestampScaler, TestNoScaling) {
  MockDecoderDatabase db;
  // Use PCMu, because it doesn't use scaled timestamps.
  const DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderPCMu, "");
  static const uint8_t kRtpPayloadType = 0;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillRepeatedly(Return(&info));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  for (uint32_t timestamp = 0xFFFFFFFF - 5; timestamp != 5; ++timestamp) {
    // Scale to internal timestamp.
    EXPECT_EQ(timestamp, scaler.ToInternal(timestamp, kRtpPayloadType));
    // Scale back.
    EXPECT_EQ(timestamp, scaler.ToExternal(timestamp));
  }

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

TEST(TimestampScaler, TestNoScalingLargeStep) {
  MockDecoderDatabase db;
  // Use PCMu, because it doesn't use scaled timestamps.
  const DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderPCMu, "");
  static const uint8_t kRtpPayloadType = 0;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillRepeatedly(Return(&info));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  static const uint32_t kStep = 160;
  uint32_t start_timestamp = 0;
  // |external_timestamp| will be a large positive value.
  start_timestamp = start_timestamp - 5 * kStep;
  for (uint32_t timestamp = start_timestamp; timestamp != 5 * kStep;
      timestamp += kStep) {
    // Scale to internal timestamp.
    EXPECT_EQ(timestamp, scaler.ToInternal(timestamp, kRtpPayloadType));
    // Scale back.
    EXPECT_EQ(timestamp, scaler.ToExternal(timestamp));
  }

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

TEST(TimestampScaler, TestG722) {
  MockDecoderDatabase db;
  // Use G722, which has a factor 2 scaling.
  const DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderG722, "");
  static const uint8_t kRtpPayloadType = 17;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillRepeatedly(Return(&info));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  uint32_t external_timestamp = 0xFFFFFFFF - 5;
  uint32_t internal_timestamp = external_timestamp;
  for (; external_timestamp != 5; ++external_timestamp) {
    // Scale to internal timestamp.
    EXPECT_EQ(internal_timestamp,
              scaler.ToInternal(external_timestamp, kRtpPayloadType));
    // Scale back.
    EXPECT_EQ(external_timestamp, scaler.ToExternal(internal_timestamp));
    internal_timestamp += 2;
  }

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

TEST(TimestampScaler, TestG722LargeStep) {
  MockDecoderDatabase db;
  // Use G722, which has a factor 2 scaling.
  const DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderG722, "");
  static const uint8_t kRtpPayloadType = 17;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillRepeatedly(Return(&info));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  static const uint32_t kStep = 320;
  uint32_t external_timestamp = 0;
  // |external_timestamp| will be a large positive value.
  external_timestamp = external_timestamp - 5 * kStep;
  uint32_t internal_timestamp = external_timestamp;
  for (; external_timestamp != 5 * kStep; external_timestamp += kStep) {
    // Scale to internal timestamp.
    EXPECT_EQ(internal_timestamp,
              scaler.ToInternal(external_timestamp, kRtpPayloadType));
    // Scale back.
    EXPECT_EQ(external_timestamp, scaler.ToExternal(internal_timestamp));
    // Internal timestamp should be incremented with twice the step.
    internal_timestamp += 2 * kStep;
  }

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

TEST(TimestampScaler, TestG722WithCng) {
  MockDecoderDatabase db;
  // Use G722, which has a factor 2 scaling.
  const DecoderDatabase::DecoderInfo info_g722(NetEqDecoder::kDecoderG722, "");
  const DecoderDatabase::DecoderInfo info_cng(NetEqDecoder::kDecoderCNGwb, "");
  static const uint8_t kRtpPayloadTypeG722 = 17;
  static const uint8_t kRtpPayloadTypeCng = 13;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadTypeG722))
      .WillRepeatedly(Return(&info_g722));
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadTypeCng))
      .WillRepeatedly(Return(&info_cng));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  uint32_t external_timestamp = 0xFFFFFFFF - 5;
  uint32_t internal_timestamp = external_timestamp;
  bool next_is_cng = false;
  for (; external_timestamp != 5; ++external_timestamp) {
    // Alternate between G.722 and CNG every other packet.
    if (next_is_cng) {
      // Scale to internal timestamp.
      EXPECT_EQ(internal_timestamp,
                scaler.ToInternal(external_timestamp, kRtpPayloadTypeCng));
      next_is_cng = false;
    } else {
      // Scale to internal timestamp.
      EXPECT_EQ(internal_timestamp,
                scaler.ToInternal(external_timestamp, kRtpPayloadTypeG722));
      next_is_cng = true;
    }
    // Scale back.
    EXPECT_EQ(external_timestamp, scaler.ToExternal(internal_timestamp));
    internal_timestamp += 2;
  }

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

// Make sure that the method ToInternal(Packet* packet) is wired up correctly.
// Since it is simply calling the other ToInternal method, we are not doing
// as many tests here.
TEST(TimestampScaler, TestG722Packet) {
  MockDecoderDatabase db;
  // Use G722, which has a factor 2 scaling.
  const DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderG722, "");
  static const uint8_t kRtpPayloadType = 17;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillRepeatedly(Return(&info));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  uint32_t external_timestamp = 0xFFFFFFFF - 5;
  uint32_t internal_timestamp = external_timestamp;
  Packet packet;
  packet.header.payloadType = kRtpPayloadType;
  for (; external_timestamp != 5; ++external_timestamp) {
    packet.header.timestamp = external_timestamp;
    // Scale to internal timestamp.
    scaler.ToInternal(&packet);
    EXPECT_EQ(internal_timestamp, packet.header.timestamp);
    internal_timestamp += 2;
  }

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

// Make sure that the method ToInternal(PacketList* packet_list) is wired up
// correctly. Since it is simply calling the ToInternal(Packet* packet) method,
// we are not doing as many tests here.
TEST(TimestampScaler, TestG722PacketList) {
  MockDecoderDatabase db;
  // Use G722, which has a factor 2 scaling.
  const DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderG722, "");
  static const uint8_t kRtpPayloadType = 17;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillRepeatedly(Return(&info));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  uint32_t external_timestamp = 0xFFFFFFFF - 5;
  uint32_t internal_timestamp = external_timestamp;
  Packet packet1;
  packet1.header.payloadType = kRtpPayloadType;
  packet1.header.timestamp = external_timestamp;
  Packet packet2;
  packet2.header.payloadType = kRtpPayloadType;
  packet2.header.timestamp = external_timestamp + 10;
  PacketList packet_list;
  packet_list.push_back(&packet1);
  packet_list.push_back(&packet2);

  scaler.ToInternal(&packet_list);
  EXPECT_EQ(internal_timestamp, packet1.header.timestamp);
  EXPECT_EQ(internal_timestamp + 20, packet2.header.timestamp);

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

TEST(TimestampScaler, TestG722Reset) {
  MockDecoderDatabase db;
  // Use G722, which has a factor 2 scaling.
  const DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderG722, "");
  static const uint8_t kRtpPayloadType = 17;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillRepeatedly(Return(&info));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  uint32_t external_timestamp = 0xFFFFFFFF - 5;
  uint32_t internal_timestamp = external_timestamp;
  for (; external_timestamp != 5; ++external_timestamp) {
    // Scale to internal timestamp.
    EXPECT_EQ(internal_timestamp,
              scaler.ToInternal(external_timestamp, kRtpPayloadType));
    // Scale back.
    EXPECT_EQ(external_timestamp, scaler.ToExternal(internal_timestamp));
    internal_timestamp += 2;
  }
  // Reset the scaler. After this, we expect the internal and external to start
  // over at the same value again.
  scaler.Reset();
  internal_timestamp = external_timestamp;
  for (; external_timestamp != 15; ++external_timestamp) {
    // Scale to internal timestamp.
    EXPECT_EQ(internal_timestamp,
              scaler.ToInternal(external_timestamp, kRtpPayloadType));
    // Scale back.
    EXPECT_EQ(external_timestamp, scaler.ToExternal(internal_timestamp));
    internal_timestamp += 2;
  }

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

// TODO(minyue): This test becomes trivial since Opus does not need a timestamp
// scaler. Therefore, this test may be removed in future. There is no harm to
// keep it, since it can be taken as a test case for the situation of a trivial
// timestamp scaler.
TEST(TimestampScaler, TestOpusLargeStep) {
  MockDecoderDatabase db;
  const DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderOpus, "");
  static const uint8_t kRtpPayloadType = 17;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillRepeatedly(Return(&info));

  TimestampScaler scaler(db);
  // Test both sides of the timestamp wrap-around.
  static const uint32_t kStep = 960;
  uint32_t external_timestamp = 0;
  // |external_timestamp| will be a large positive value.
  external_timestamp = external_timestamp - 5 * kStep;
  uint32_t internal_timestamp = external_timestamp;
  for (; external_timestamp != 5 * kStep; external_timestamp += kStep) {
    // Scale to internal timestamp.
    EXPECT_EQ(internal_timestamp,
              scaler.ToInternal(external_timestamp, kRtpPayloadType));
    // Scale back.
    EXPECT_EQ(external_timestamp, scaler.ToExternal(internal_timestamp));
    internal_timestamp += kStep;
  }

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

TEST(TimestampScaler, Failures) {
  static const uint8_t kRtpPayloadType = 17;
  MockDecoderDatabase db;
  EXPECT_CALL(db, GetDecoderInfo(kRtpPayloadType))
      .WillOnce(ReturnNull());  // Return NULL to indicate unknown payload type.

  TimestampScaler scaler(db);
  uint32_t timestamp = 4711;  // Some number.
  EXPECT_EQ(timestamp, scaler.ToInternal(timestamp, kRtpPayloadType));

  Packet* packet = NULL;
  scaler.ToInternal(packet);  // Should not crash. That's all we can test.

  EXPECT_CALL(db, Die());  // Called when database object is deleted.
}

}  // namespace webrtc
