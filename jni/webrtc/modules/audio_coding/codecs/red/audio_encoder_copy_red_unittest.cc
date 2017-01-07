/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/codecs/red/audio_encoder_copy_red.h"
#include "webrtc/modules/audio_coding/codecs/mock/mock_audio_encoder.h"

using ::testing::Return;
using ::testing::_;
using ::testing::SetArgPointee;
using ::testing::InSequence;
using ::testing::Invoke;
using ::testing::MockFunction;

namespace webrtc {

namespace {
static const size_t kMaxNumSamples = 48 * 10 * 2;  // 10 ms @ 48 kHz stereo.
}

class AudioEncoderCopyRedTest : public ::testing::Test {
 protected:
  AudioEncoderCopyRedTest()
      : mock_encoder_(new MockAudioEncoder),
        timestamp_(4711),
        sample_rate_hz_(16000),
        num_audio_samples_10ms(sample_rate_hz_ / 100),
        red_payload_type_(200) {
    AudioEncoderCopyRed::Config config;
    config.payload_type = red_payload_type_;
    config.speech_encoder = std::unique_ptr<AudioEncoder>(mock_encoder_);
    red_.reset(new AudioEncoderCopyRed(std::move(config)));
    memset(audio_, 0, sizeof(audio_));
    EXPECT_CALL(*mock_encoder_, NumChannels()).WillRepeatedly(Return(1U));
    EXPECT_CALL(*mock_encoder_, SampleRateHz())
        .WillRepeatedly(Return(sample_rate_hz_));
  }

  void TearDown() override {
    EXPECT_CALL(*mock_encoder_, Die()).Times(1);
    red_.reset();
  }

  void Encode() {
    ASSERT_TRUE(red_.get() != NULL);
    encoded_.Clear();
    encoded_info_ = red_->Encode(
        timestamp_,
        rtc::ArrayView<const int16_t>(audio_, num_audio_samples_10ms),
        &encoded_);
    timestamp_ += num_audio_samples_10ms;
  }

  MockAudioEncoder* mock_encoder_;
  std::unique_ptr<AudioEncoderCopyRed> red_;
  uint32_t timestamp_;
  int16_t audio_[kMaxNumSamples];
  const int sample_rate_hz_;
  size_t num_audio_samples_10ms;
  rtc::Buffer encoded_;
  AudioEncoder::EncodedInfo encoded_info_;
  const int red_payload_type_;
};

TEST_F(AudioEncoderCopyRedTest, CreateAndDestroy) {
}

TEST_F(AudioEncoderCopyRedTest, CheckSampleRatePropagation) {
  EXPECT_CALL(*mock_encoder_, SampleRateHz()).WillOnce(Return(17));
  EXPECT_EQ(17, red_->SampleRateHz());
}

TEST_F(AudioEncoderCopyRedTest, CheckNumChannelsPropagation) {
  EXPECT_CALL(*mock_encoder_, NumChannels()).WillOnce(Return(17U));
  EXPECT_EQ(17U, red_->NumChannels());
}

TEST_F(AudioEncoderCopyRedTest, CheckFrameSizePropagation) {
  EXPECT_CALL(*mock_encoder_, Num10MsFramesInNextPacket())
      .WillOnce(Return(17U));
  EXPECT_EQ(17U, red_->Num10MsFramesInNextPacket());
}

TEST_F(AudioEncoderCopyRedTest, CheckMaxFrameSizePropagation) {
  EXPECT_CALL(*mock_encoder_, Max10MsFramesInAPacket()).WillOnce(Return(17U));
  EXPECT_EQ(17U, red_->Max10MsFramesInAPacket());
}

TEST_F(AudioEncoderCopyRedTest, CheckSetBitratePropagation) {
  EXPECT_CALL(*mock_encoder_, SetTargetBitrate(4711));
  red_->SetTargetBitrate(4711);
}

TEST_F(AudioEncoderCopyRedTest, CheckProjectedPacketLossRatePropagation) {
  EXPECT_CALL(*mock_encoder_, SetProjectedPacketLossRate(0.5));
  red_->SetProjectedPacketLossRate(0.5);
}

// Checks that the an Encode() call is immediately propagated to the speech
// encoder.
TEST_F(AudioEncoderCopyRedTest, CheckImmediateEncode) {
  // Interleaving the EXPECT_CALL sequence with expectations on the MockFunction
  // check ensures that exactly one call to EncodeImpl happens in each
  // Encode call.
  InSequence s;
  MockFunction<void(int check_point_id)> check;
  for (int i = 1; i <= 6; ++i) {
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillRepeatedly(Return(AudioEncoder::EncodedInfo()));
    EXPECT_CALL(check, Call(i));
    Encode();
    check.Call(i);
  }
}

// Checks that no output is produced if the underlying codec doesn't emit any
// new data, even if the RED codec is loaded with a secondary encoding.
TEST_F(AudioEncoderCopyRedTest, CheckNoOutput) {
  static const size_t kEncodedSize = 17;
  {
    InSequence s;
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(kEncodedSize)))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(0)))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(kEncodedSize)));
  }

  // Start with one Encode() call that will produce output.
  Encode();
  // First call is a special case, since it does not include a secondary
  // payload.
  EXPECT_EQ(1u, encoded_info_.redundant.size());
  EXPECT_EQ(kEncodedSize, encoded_info_.encoded_bytes);

  // Next call to the speech encoder will not produce any output.
  Encode();
  EXPECT_EQ(0u, encoded_info_.encoded_bytes);

  // Final call to the speech encoder will produce output.
  Encode();
  EXPECT_EQ(2 * kEncodedSize, encoded_info_.encoded_bytes);
  ASSERT_EQ(2u, encoded_info_.redundant.size());
}

// Checks that the correct payload sizes are populated into the redundancy
// information.
TEST_F(AudioEncoderCopyRedTest, CheckPayloadSizes) {
  // Let the mock encoder return payload sizes 1, 2, 3, ..., 10 for the sequence
  // of calls.
  static const int kNumPackets = 10;
  InSequence s;
  for (int encode_size = 1; encode_size <= kNumPackets; ++encode_size) {
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(encode_size)));
  }

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  EXPECT_EQ(1u, encoded_info_.redundant.size());
  EXPECT_EQ(1u, encoded_info_.encoded_bytes);

  for (size_t i = 2; i <= kNumPackets; ++i) {
    Encode();
    ASSERT_EQ(2u, encoded_info_.redundant.size());
    EXPECT_EQ(i, encoded_info_.redundant[0].encoded_bytes);
    EXPECT_EQ(i - 1, encoded_info_.redundant[1].encoded_bytes);
    EXPECT_EQ(i + i - 1, encoded_info_.encoded_bytes);
  }
}

// Checks that the correct timestamps are returned.
TEST_F(AudioEncoderCopyRedTest, CheckTimestamps) {
  uint32_t primary_timestamp = timestamp_;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 17;
  info.encoded_timestamp = timestamp_;

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  EXPECT_EQ(primary_timestamp, encoded_info_.encoded_timestamp);

  uint32_t secondary_timestamp = primary_timestamp;
  primary_timestamp = timestamp_;
  info.encoded_timestamp = timestamp_;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  Encode();
  ASSERT_EQ(2u, encoded_info_.redundant.size());
  EXPECT_EQ(primary_timestamp, encoded_info_.redundant[0].encoded_timestamp);
  EXPECT_EQ(secondary_timestamp, encoded_info_.redundant[1].encoded_timestamp);
  EXPECT_EQ(primary_timestamp, encoded_info_.encoded_timestamp);
}

// Checks that the primary and secondary payloads are written correctly.
TEST_F(AudioEncoderCopyRedTest, CheckPayloads) {
  // Let the mock encoder write payloads with increasing values. The first
  // payload will have values 0, 1, 2, ..., kPayloadLenBytes - 1.
  static const size_t kPayloadLenBytes = 5;
  uint8_t payload[kPayloadLenBytes];
  for (uint8_t i = 0; i < kPayloadLenBytes; ++i) {
    payload[i] = i;
  }
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillRepeatedly(Invoke(MockAudioEncoder::CopyEncoding(payload)));

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  EXPECT_EQ(kPayloadLenBytes, encoded_info_.encoded_bytes);
  for (size_t i = 0; i < kPayloadLenBytes; ++i) {
    EXPECT_EQ(i, encoded_.data()[i]);
  }

  for (int j = 0; j < 5; ++j) {
    // Increment all values of the payload by 10.
    for (size_t i = 0; i < kPayloadLenBytes; ++i)
      payload[i] += 10;

    Encode();
    ASSERT_EQ(2u, encoded_info_.redundant.size());
    EXPECT_EQ(kPayloadLenBytes, encoded_info_.redundant[0].encoded_bytes);
    EXPECT_EQ(kPayloadLenBytes, encoded_info_.redundant[1].encoded_bytes);
    for (size_t i = 0; i < kPayloadLenBytes; ++i) {
      // Check primary payload.
      EXPECT_EQ((j + 1) * 10 + i, encoded_.data()[i]);
      // Check secondary payload.
      EXPECT_EQ(j * 10 + i, encoded_.data()[i + kPayloadLenBytes]);
    }
  }
}

// Checks correct propagation of payload type.
// Checks that the correct timestamps are returned.
TEST_F(AudioEncoderCopyRedTest, CheckPayloadType) {
  const int primary_payload_type = red_payload_type_ + 1;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 17;
  info.payload_type = primary_payload_type;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  ASSERT_EQ(1u, encoded_info_.redundant.size());
  EXPECT_EQ(primary_payload_type, encoded_info_.redundant[0].payload_type);
  EXPECT_EQ(red_payload_type_, encoded_info_.payload_type);

  const int secondary_payload_type = red_payload_type_ + 2;
  info.payload_type = secondary_payload_type;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  Encode();
  ASSERT_EQ(2u, encoded_info_.redundant.size());
  EXPECT_EQ(secondary_payload_type, encoded_info_.redundant[0].payload_type);
  EXPECT_EQ(primary_payload_type, encoded_info_.redundant[1].payload_type);
  EXPECT_EQ(red_payload_type_, encoded_info_.payload_type);
}

#if GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

// This test fixture tests various error conditions that makes the
// AudioEncoderCng die via CHECKs.
class AudioEncoderCopyRedDeathTest : public AudioEncoderCopyRedTest {
 protected:
  AudioEncoderCopyRedDeathTest() : AudioEncoderCopyRedTest() {}
};

TEST_F(AudioEncoderCopyRedDeathTest, WrongFrameSize) {
  num_audio_samples_10ms *= 2;  // 20 ms frame.
  EXPECT_DEATH(Encode(), "");
  num_audio_samples_10ms = 0;  // Zero samples.
  EXPECT_DEATH(Encode(), "");
}

TEST_F(AudioEncoderCopyRedDeathTest, NullSpeechEncoder) {
  AudioEncoderCopyRed* red = NULL;
  AudioEncoderCopyRed::Config config;
  config.speech_encoder = NULL;
  EXPECT_DEATH(red = new AudioEncoderCopyRed(std::move(config)),
               "Speech encoder not provided.");
  // The delete operation is needed to avoid leak reports from memcheck.
  delete red;
}

#endif  // GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

}  // namespace webrtc
