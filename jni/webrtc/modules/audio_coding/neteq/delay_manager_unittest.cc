/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for DelayManager class.

#include "webrtc/modules/audio_coding/neteq/delay_manager.h"

#include <math.h>

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_delay_peak_detector.h"

namespace webrtc {

using ::testing::Return;
using ::testing::_;

class DelayManagerTest : public ::testing::Test {
 protected:
  static const int kMaxNumberOfPackets = 240;
  static const int kTimeStepMs = 10;
  static const int kFs = 8000;
  static const int kFrameSizeMs = 20;
  static const int kTsIncrement = kFrameSizeMs * kFs / 1000;

  DelayManagerTest();
  virtual void SetUp();
  virtual void TearDown();
  void SetPacketAudioLength(int lengt_ms);
  void InsertNextPacket();
  void IncreaseTime(int inc_ms);

  DelayManager* dm_;
  TickTimer tick_timer_;
  MockDelayPeakDetector detector_;
  uint16_t seq_no_;
  uint32_t ts_;
};

DelayManagerTest::DelayManagerTest()
    : dm_(NULL), detector_(&tick_timer_), seq_no_(0x1234), ts_(0x12345678) {}

void DelayManagerTest::SetUp() {
  EXPECT_CALL(detector_, Reset())
            .Times(1);
  dm_ = new DelayManager(kMaxNumberOfPackets, &detector_, &tick_timer_);
}

void DelayManagerTest::SetPacketAudioLength(int lengt_ms) {
  EXPECT_CALL(detector_, SetPacketAudioLength(lengt_ms));
  dm_->SetPacketAudioLength(lengt_ms);
}

void DelayManagerTest::InsertNextPacket() {
  EXPECT_EQ(0, dm_->Update(seq_no_, ts_, kFs));
  seq_no_ += 1;
  ts_ += kTsIncrement;
}

void DelayManagerTest::IncreaseTime(int inc_ms) {
  for (int t = 0; t < inc_ms; t += kTimeStepMs) {
    tick_timer_.Increment();
  }
}
void DelayManagerTest::TearDown() {
  EXPECT_CALL(detector_, Die());
  delete dm_;
}

TEST_F(DelayManagerTest, CreateAndDestroy) {
  // Nothing to do here. The test fixture creates and destroys the DelayManager
  // object.
}

TEST_F(DelayManagerTest, VectorInitialization) {
  const DelayManager::IATVector& vec = dm_->iat_vector();
  double sum = 0.0;
  for (size_t i = 0; i < vec.size(); i++) {
    EXPECT_NEAR(ldexp(pow(0.5, static_cast<int>(i + 1)), 30), vec[i], 65537);
    // Tolerance 65537 in Q30 corresponds to a delta of approximately 0.00006.
    sum += vec[i];
  }
  EXPECT_EQ(1 << 30, static_cast<int>(sum));  // Should be 1 in Q30.
}

TEST_F(DelayManagerTest, SetPacketAudioLength) {
  const int kLengthMs = 30;
  // Expect DelayManager to pass on the new length to the detector object.
  EXPECT_CALL(detector_, SetPacketAudioLength(kLengthMs))
      .Times(1);
  EXPECT_EQ(0, dm_->SetPacketAudioLength(kLengthMs));
  EXPECT_EQ(-1, dm_->SetPacketAudioLength(-1));  // Illegal parameter value.
}

TEST_F(DelayManagerTest, PeakFound) {
  // Expect DelayManager to pass on the question to the detector.
  // Call twice, and let the detector return true the first time and false the
  // second time.
  EXPECT_CALL(detector_, peak_found())
      .WillOnce(Return(true))
      .WillOnce(Return(false));
  EXPECT_TRUE(dm_->PeakFound());
  EXPECT_FALSE(dm_->PeakFound());
}

TEST_F(DelayManagerTest, UpdateNormal) {
  SetPacketAudioLength(kFrameSizeMs);
  // First packet arrival.
  InsertNextPacket();
  // Advance time by one frame size.
  IncreaseTime(kFrameSizeMs);
  // Second packet arrival.
  // Expect detector update method to be called once with inter-arrival time
  // equal to 1 packet, and (base) target level equal to 1 as well.
  // Return false to indicate no peaks found.
  EXPECT_CALL(detector_, Update(1, 1))
      .WillOnce(Return(false));
  InsertNextPacket();
  EXPECT_EQ(1 << 8, dm_->TargetLevel());  // In Q8.
  EXPECT_EQ(1, dm_->base_target_level());
  int lower, higher;
  dm_->BufferLimits(&lower, &higher);
  // Expect |lower| to be 75% of target level, and |higher| to be target level,
  // but also at least 20 ms higher than |lower|, which is the limiting case
  // here.
  EXPECT_EQ((1 << 8) * 3 / 4, lower);
  EXPECT_EQ(lower + (20 << 8) / kFrameSizeMs, higher);
}

TEST_F(DelayManagerTest, UpdateLongInterArrivalTime) {
  SetPacketAudioLength(kFrameSizeMs);
  // First packet arrival.
  InsertNextPacket();
  // Advance time by two frame size.
  IncreaseTime(2 * kFrameSizeMs);
  // Second packet arrival.
  // Expect detector update method to be called once with inter-arrival time
  // equal to 1 packet, and (base) target level equal to 1 as well.
  // Return false to indicate no peaks found.
  EXPECT_CALL(detector_, Update(2, 2))
      .WillOnce(Return(false));
  InsertNextPacket();
  EXPECT_EQ(2 << 8, dm_->TargetLevel());  // In Q8.
  EXPECT_EQ(2, dm_->base_target_level());
  int lower, higher;
  dm_->BufferLimits(&lower, &higher);
  // Expect |lower| to be 75% of target level, and |higher| to be target level,
  // but also at least 20 ms higher than |lower|, which is the limiting case
  // here.
  EXPECT_EQ((2 << 8) * 3 / 4, lower);
  EXPECT_EQ(lower + (20 << 8) / kFrameSizeMs, higher);
}

TEST_F(DelayManagerTest, UpdatePeakFound) {
  SetPacketAudioLength(kFrameSizeMs);
  // First packet arrival.
  InsertNextPacket();
  // Advance time by one frame size.
  IncreaseTime(kFrameSizeMs);
  // Second packet arrival.
  // Expect detector update method to be called once with inter-arrival time
  // equal to 1 packet, and (base) target level equal to 1 as well.
  // Return true to indicate that peaks are found. Let the peak height be 5.
  EXPECT_CALL(detector_, Update(1, 1))
      .WillOnce(Return(true));
  EXPECT_CALL(detector_, MaxPeakHeight())
      .WillOnce(Return(5));
  InsertNextPacket();
  EXPECT_EQ(5 << 8, dm_->TargetLevel());
  EXPECT_EQ(1, dm_->base_target_level());  // Base target level is w/o peaks.
  int lower, higher;
  dm_->BufferLimits(&lower, &higher);
  // Expect |lower| to be 75% of target level, and |higher| to be target level.
  EXPECT_EQ((5 << 8) * 3 / 4, lower);
  EXPECT_EQ(5 << 8, higher);
}

TEST_F(DelayManagerTest, TargetDelay) {
  SetPacketAudioLength(kFrameSizeMs);
  // First packet arrival.
  InsertNextPacket();
  // Advance time by one frame size.
  IncreaseTime(kFrameSizeMs);
  // Second packet arrival.
  // Expect detector update method to be called once with inter-arrival time
  // equal to 1 packet, and (base) target level equal to 1 as well.
  // Return false to indicate no peaks found.
  EXPECT_CALL(detector_, Update(1, 1))
      .WillOnce(Return(false));
  InsertNextPacket();
  const int kExpectedTarget = 1;
  EXPECT_EQ(kExpectedTarget << 8, dm_->TargetLevel());  // In Q8.
  EXPECT_EQ(1, dm_->base_target_level());
  int lower, higher;
  dm_->BufferLimits(&lower, &higher);
  // Expect |lower| to be 75% of base target level, and |higher| to be
  // lower + 20 ms headroom.
  EXPECT_EQ((1 << 8) * 3 / 4, lower);
  EXPECT_EQ(lower + (20 << 8) / kFrameSizeMs, higher);
}

TEST_F(DelayManagerTest, MaxAndRequiredDelay) {
  const int kExpectedTarget = 5;
  const int kTimeIncrement = kExpectedTarget * kFrameSizeMs;
  SetPacketAudioLength(kFrameSizeMs);
  // First packet arrival.
  InsertNextPacket();
  // Second packet arrival.
  // Expect detector update method to be called once with inter-arrival time
  // equal to |kExpectedTarget| packet. Return true to indicate peaks found.
  EXPECT_CALL(detector_, Update(kExpectedTarget, _))
      .WillRepeatedly(Return(true));
  EXPECT_CALL(detector_, MaxPeakHeight())
      .WillRepeatedly(Return(kExpectedTarget));
  IncreaseTime(kTimeIncrement);
  InsertNextPacket();

  // No limit is set.
  EXPECT_EQ(kExpectedTarget << 8, dm_->TargetLevel());

  int kMaxDelayPackets = kExpectedTarget - 2;
  int kMaxDelayMs = kMaxDelayPackets * kFrameSizeMs;
  EXPECT_TRUE(dm_->SetMaximumDelay(kMaxDelayMs));
  IncreaseTime(kTimeIncrement);
  InsertNextPacket();
  EXPECT_EQ(kExpectedTarget * kFrameSizeMs, dm_->least_required_delay_ms());
  EXPECT_EQ(kMaxDelayPackets << 8, dm_->TargetLevel());

  // Target level at least should be one packet.
  EXPECT_FALSE(dm_->SetMaximumDelay(kFrameSizeMs - 1));
}

TEST_F(DelayManagerTest, MinAndRequiredDelay) {
  const int kExpectedTarget = 5;
  const int kTimeIncrement = kExpectedTarget * kFrameSizeMs;
  SetPacketAudioLength(kFrameSizeMs);
  // First packet arrival.
  InsertNextPacket();
  // Second packet arrival.
  // Expect detector update method to be called once with inter-arrival time
  // equal to |kExpectedTarget| packet. Return true to indicate peaks found.
  EXPECT_CALL(detector_, Update(kExpectedTarget, _))
      .WillRepeatedly(Return(true));
  EXPECT_CALL(detector_, MaxPeakHeight())
      .WillRepeatedly(Return(kExpectedTarget));
  IncreaseTime(kTimeIncrement);
  InsertNextPacket();

  // No limit is applied.
  EXPECT_EQ(kExpectedTarget << 8, dm_->TargetLevel());

  int kMinDelayPackets = kExpectedTarget + 2;
  int kMinDelayMs = kMinDelayPackets * kFrameSizeMs;
  dm_->SetMinimumDelay(kMinDelayMs);
  IncreaseTime(kTimeIncrement);
  InsertNextPacket();
  EXPECT_EQ(kExpectedTarget * kFrameSizeMs, dm_->least_required_delay_ms());
  EXPECT_EQ(kMinDelayPackets << 8, dm_->TargetLevel());
}

TEST_F(DelayManagerTest, Failures) {
  // Wrong sample rate.
  EXPECT_EQ(-1, dm_->Update(0, 0, -1));
  // Wrong packet size.
  EXPECT_EQ(-1, dm_->SetPacketAudioLength(0));
  EXPECT_EQ(-1, dm_->SetPacketAudioLength(-1));

  // Minimum delay higher than a maximum delay is not accepted.
  EXPECT_TRUE(dm_->SetMaximumDelay(10));
  EXPECT_FALSE(dm_->SetMinimumDelay(20));

  // Maximum delay less than minimum delay is not accepted.
  EXPECT_TRUE(dm_->SetMaximumDelay(100));
  EXPECT_TRUE(dm_->SetMinimumDelay(80));
  EXPECT_FALSE(dm_->SetMaximumDelay(60));
}

}  // namespace webrtc
