/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string.h>

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/acm2/initial_delay_manager.h"

namespace webrtc {

namespace acm2 {

namespace {

const uint8_t kAudioPayloadType = 0;
const uint8_t kCngPayloadType = 1;
const uint8_t kAvtPayloadType = 2;

const int kSamplingRateHz = 16000;
const int kInitDelayMs = 200;
const int kFrameSizeMs = 20;
const uint32_t kTimestampStep = kFrameSizeMs * kSamplingRateHz / 1000;
const int kLatePacketThreshold = 5;

void InitRtpInfo(WebRtcRTPHeader* rtp_info) {
  memset(rtp_info, 0, sizeof(*rtp_info));
  rtp_info->header.markerBit = false;
  rtp_info->header.payloadType = kAudioPayloadType;
  rtp_info->header.sequenceNumber = 1234;
  rtp_info->header.timestamp = 0xFFFFFFFD;  // Close to wrap around.
  rtp_info->header.ssrc = 0x87654321;  // Arbitrary.
  rtp_info->header.numCSRCs = 0;  // Arbitrary.
  rtp_info->header.paddingLength = 0;
  rtp_info->header.headerLength = sizeof(RTPHeader);
  rtp_info->header.payload_type_frequency = kSamplingRateHz;
  rtp_info->header.extension.absoluteSendTime = 0;
  rtp_info->header.extension.transmissionTimeOffset = 0;
  rtp_info->frameType = kAudioFrameSpeech;
}

void ForwardRtpHeader(int n,
                      WebRtcRTPHeader* rtp_info,
                      uint32_t* rtp_receive_timestamp) {
  rtp_info->header.sequenceNumber += n;
  rtp_info->header.timestamp += n * kTimestampStep;
  *rtp_receive_timestamp += n * kTimestampStep;
}

void NextRtpHeader(WebRtcRTPHeader* rtp_info,
                   uint32_t* rtp_receive_timestamp) {
  ForwardRtpHeader(1, rtp_info, rtp_receive_timestamp);
}

}  // namespace

class InitialDelayManagerTest : public ::testing::Test {
 protected:
  InitialDelayManagerTest()
      : manager_(new InitialDelayManager(kInitDelayMs, kLatePacketThreshold)),
        rtp_receive_timestamp_(1111) { }  // Arbitrary starting point.

  virtual void SetUp() {
    ASSERT_TRUE(manager_.get() != NULL);
    InitRtpInfo(&rtp_info_);
  }

  void GetNextRtpHeader(WebRtcRTPHeader* rtp_info,
                        uint32_t* rtp_receive_timestamp) const {
    memcpy(rtp_info, &rtp_info_, sizeof(*rtp_info));
    *rtp_receive_timestamp = rtp_receive_timestamp_;
    NextRtpHeader(rtp_info, rtp_receive_timestamp);
  }

  std::unique_ptr<InitialDelayManager> manager_;
  WebRtcRTPHeader rtp_info_;
  uint32_t rtp_receive_timestamp_;
};

TEST_F(InitialDelayManagerTest, Init) {
  EXPECT_TRUE(manager_->buffering());
  EXPECT_FALSE(manager_->PacketBuffered());
  manager_->DisableBuffering();
  EXPECT_FALSE(manager_->buffering());
  InitialDelayManager::SyncStream sync_stream;

  // Call before any packet inserted.
  manager_->LatePackets(0x6789ABCD, &sync_stream);  // Arbitrary but large
                                                    // receive timestamp.
  EXPECT_EQ(0, sync_stream.num_sync_packets);

  // Insert non-audio packets, a CNG and DTMF.
  rtp_info_.header.payloadType = kCngPayloadType;
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kCngPacket, false,
                                     kSamplingRateHz, &sync_stream);
  EXPECT_EQ(0, sync_stream.num_sync_packets);
  ForwardRtpHeader(5, &rtp_info_, &rtp_receive_timestamp_);
  rtp_info_.header.payloadType = kAvtPayloadType;
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAvtPacket, false,
                                     kSamplingRateHz, &sync_stream);
  // Gap in sequence numbers but no audio received, sync-stream should be empty.
  EXPECT_EQ(0, sync_stream.num_sync_packets);
  manager_->LatePackets(0x45678987, &sync_stream);  // Large arbitrary receive
                                                    // timestamp.
  // |manager_| has no estimate of timestamp-step and has not received any
  // audio packet.
  EXPECT_EQ(0, sync_stream.num_sync_packets);


  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);
  rtp_info_.header.payloadType = kAudioPayloadType;
  // First packet.
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, true,
                                     kSamplingRateHz, &sync_stream);
  EXPECT_EQ(0, sync_stream.num_sync_packets);

  // Call LatePAcket() after only one packet inserted.
  manager_->LatePackets(0x6789ABCD, &sync_stream);  // Arbitrary but large
                                                    // receive timestamp.
  EXPECT_EQ(0, sync_stream.num_sync_packets);

  // Gap in timestamp, but this packet is also flagged as "new," therefore,
  // expecting empty sync-stream.
  ForwardRtpHeader(5, &rtp_info_, &rtp_receive_timestamp_);
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, true,
                                     kSamplingRateHz, &sync_stream);
}

TEST_F(InitialDelayManagerTest, MissingPacket) {
  InitialDelayManager::SyncStream sync_stream;
  // First packet.
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, true,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Second packet.
  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, false,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Third packet, missing packets start from here.
  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);

  // First sync-packet in sync-stream is one after the above packet.
  WebRtcRTPHeader expected_rtp_info;
  uint32_t expected_receive_timestamp;
  GetNextRtpHeader(&expected_rtp_info, &expected_receive_timestamp);

  const int kNumMissingPackets = 10;
  ForwardRtpHeader(kNumMissingPackets, &rtp_info_, &rtp_receive_timestamp_);
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, false,
                                     kSamplingRateHz, &sync_stream);
  EXPECT_EQ(kNumMissingPackets - 2, sync_stream.num_sync_packets);
  EXPECT_EQ(0, memcmp(&expected_rtp_info, &sync_stream.rtp_info,
                      sizeof(expected_rtp_info)));
  EXPECT_EQ(kTimestampStep, sync_stream.timestamp_step);
  EXPECT_EQ(expected_receive_timestamp, sync_stream.receive_timestamp);
}

// There hasn't been any consecutive packets to estimate timestamp-step.
TEST_F(InitialDelayManagerTest, MissingPacketEstimateTimestamp) {
  InitialDelayManager::SyncStream sync_stream;
  // First packet.
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, true,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Second packet, missing packets start here.
  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);

  // First sync-packet in sync-stream is one after the above.
  WebRtcRTPHeader expected_rtp_info;
  uint32_t expected_receive_timestamp;
  GetNextRtpHeader(&expected_rtp_info, &expected_receive_timestamp);

  const int kNumMissingPackets = 10;
  ForwardRtpHeader(kNumMissingPackets, &rtp_info_, &rtp_receive_timestamp_);
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, false,
                                     kSamplingRateHz, &sync_stream);
  EXPECT_EQ(kNumMissingPackets - 2, sync_stream.num_sync_packets);
  EXPECT_EQ(0, memcmp(&expected_rtp_info, &sync_stream.rtp_info,
                      sizeof(expected_rtp_info)));
}

TEST_F(InitialDelayManagerTest, MissingPacketWithCng) {
  InitialDelayManager::SyncStream sync_stream;

  // First packet.
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, true,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Second packet as CNG.
  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);
  rtp_info_.header.payloadType = kCngPayloadType;
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kCngPacket, false,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Audio packet after CNG. Missing packets start from this packet.
  rtp_info_.header.payloadType = kAudioPayloadType;
  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);

  // Timestamps are increased higher than regular packet.
  const uint32_t kCngTimestampStep = 5 * kTimestampStep;
  rtp_info_.header.timestamp += kCngTimestampStep;
  rtp_receive_timestamp_ += kCngTimestampStep;

  // First sync-packet in sync-stream is the one after the above packet.
  WebRtcRTPHeader expected_rtp_info;
  uint32_t expected_receive_timestamp;
  GetNextRtpHeader(&expected_rtp_info, &expected_receive_timestamp);

  const int kNumMissingPackets = 10;
  ForwardRtpHeader(kNumMissingPackets, &rtp_info_, &rtp_receive_timestamp_);
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, false,
                                     kSamplingRateHz, &sync_stream);
  EXPECT_EQ(kNumMissingPackets - 2, sync_stream.num_sync_packets);
  EXPECT_EQ(0, memcmp(&expected_rtp_info, &sync_stream.rtp_info,
                      sizeof(expected_rtp_info)));
  EXPECT_EQ(kTimestampStep, sync_stream.timestamp_step);
  EXPECT_EQ(expected_receive_timestamp, sync_stream.receive_timestamp);
}

TEST_F(InitialDelayManagerTest, LatePacket) {
  InitialDelayManager::SyncStream sync_stream;
  // First packet.
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, true,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Second packet.
  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, false,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Timestamp increment for 10ms;
  const uint32_t kTimestampStep10Ms = kSamplingRateHz / 100;

  // 10 ms after the second packet is inserted.
  uint32_t timestamp_now = rtp_receive_timestamp_ + kTimestampStep10Ms;

  // Third packet, late packets start from this packet.
  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);

  // First sync-packet in sync-stream, which is one after the above packet.
  WebRtcRTPHeader expected_rtp_info;
  uint32_t expected_receive_timestamp;
  GetNextRtpHeader(&expected_rtp_info, &expected_receive_timestamp);

  const int kLatePacketThreshold = 5;

  int expected_num_late_packets = kLatePacketThreshold - 1;
  for (int k = 0; k < 2; ++k) {
    for (int n = 1; n < kLatePacketThreshold * kFrameSizeMs / 10; ++n) {
      manager_->LatePackets(timestamp_now, &sync_stream);
      EXPECT_EQ(0, sync_stream.num_sync_packets) <<
          "try " << k << " loop number " << n;
      timestamp_now += kTimestampStep10Ms;
    }
    manager_->LatePackets(timestamp_now, &sync_stream);

    EXPECT_EQ(expected_num_late_packets, sync_stream.num_sync_packets) <<
        "try " << k;
    EXPECT_EQ(kTimestampStep, sync_stream.timestamp_step) <<
        "try " << k;
    EXPECT_EQ(expected_receive_timestamp, sync_stream.receive_timestamp) <<
        "try " << k;
    EXPECT_EQ(0, memcmp(&expected_rtp_info, &sync_stream.rtp_info,
                        sizeof(expected_rtp_info)));

    timestamp_now += kTimestampStep10Ms;

    // |manger_| assumes the |sync_stream| obtained by LatePacket() is fully
    // injected. The last injected packet is sync-packet, therefore, there will
    // not be any gap between sync stream of this and the next iteration.
    ForwardRtpHeader(sync_stream.num_sync_packets, &expected_rtp_info,
        &expected_receive_timestamp);
    expected_num_late_packets = kLatePacketThreshold;
  }

  // Test "no-gap" for missing packet after late packet.
  // |expected_rtp_info| is the expected sync-packet if any packet is missing.
  memcpy(&rtp_info_, &expected_rtp_info, sizeof(rtp_info_));
  rtp_receive_timestamp_ = expected_receive_timestamp;

  int kNumMissingPackets = 3;  // Arbitrary.
  ForwardRtpHeader(kNumMissingPackets, &rtp_info_, &rtp_receive_timestamp_);
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, false,
                                     kSamplingRateHz, &sync_stream);

  // Note that there is one packet gap between the last sync-packet and the
  // latest inserted packet.
  EXPECT_EQ(kNumMissingPackets - 1, sync_stream.num_sync_packets);
  EXPECT_EQ(kTimestampStep, sync_stream.timestamp_step);
  EXPECT_EQ(expected_receive_timestamp, sync_stream.receive_timestamp);
  EXPECT_EQ(0, memcmp(&expected_rtp_info, &sync_stream.rtp_info,
                      sizeof(expected_rtp_info)));
}

TEST_F(InitialDelayManagerTest, NoLatePacketAfterCng) {
  InitialDelayManager::SyncStream sync_stream;

  // First packet.
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket, true,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Second packet as CNG.
  NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);
  rtp_info_.header.payloadType = kCngPayloadType;
  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kCngPacket, false,
                                     kSamplingRateHz, &sync_stream);
  ASSERT_EQ(0, sync_stream.num_sync_packets);

  // Forward the time more then |kLatePacketThreshold| packets.
  uint32_t timestamp_now = rtp_receive_timestamp_ + kTimestampStep * (3 +
      kLatePacketThreshold);

  manager_->LatePackets(timestamp_now, &sync_stream);
  EXPECT_EQ(0, sync_stream.num_sync_packets);
}

TEST_F(InitialDelayManagerTest, BufferingAudio) {
  InitialDelayManager::SyncStream sync_stream;

  // Very first packet is not counted in calculation of buffered audio.
  for (int n = 0; n < kInitDelayMs / kFrameSizeMs; ++n) {
    manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                       InitialDelayManager::kAudioPacket,
                                       n == 0, kSamplingRateHz, &sync_stream);
    EXPECT_EQ(0, sync_stream.num_sync_packets);
    EXPECT_TRUE(manager_->buffering());
    const uint32_t expected_playout_timestamp = rtp_info_.header.timestamp -
        kInitDelayMs * kSamplingRateHz / 1000;
    uint32_t actual_playout_timestamp = 0;
    EXPECT_TRUE(manager_->GetPlayoutTimestamp(&actual_playout_timestamp));
    EXPECT_EQ(expected_playout_timestamp, actual_playout_timestamp);
    NextRtpHeader(&rtp_info_, &rtp_receive_timestamp_);
  }

  manager_->UpdateLastReceivedPacket(rtp_info_, rtp_receive_timestamp_,
                                     InitialDelayManager::kAudioPacket,
                                     false, kSamplingRateHz, &sync_stream);
  EXPECT_EQ(0, sync_stream.num_sync_packets);
  EXPECT_FALSE(manager_->buffering());
}

}  // namespace acm2

}  // namespace webrtc
