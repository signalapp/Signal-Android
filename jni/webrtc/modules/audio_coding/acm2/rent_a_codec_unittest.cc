/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/arraysize.h"
#include "webrtc/modules/audio_coding/codecs/mock/mock_audio_encoder.h"
#include "webrtc/modules/audio_coding/acm2/rent_a_codec.h"

namespace webrtc {
namespace acm2 {

using ::testing::Return;

namespace {

const int kDataLengthSamples = 80;
const int kPacketSizeSamples = 2 * kDataLengthSamples;
const int16_t kZeroData[kDataLengthSamples] = {0};
const CodecInst kDefaultCodecInst = {0, "pcmu", 8000, kPacketSizeSamples,
                                     1, 64000};
const int kCngPt = 13;

class Marker final {
 public:
  MOCK_METHOD1(Mark, void(std::string desc));
};

}  // namespace

class RentACodecTestF : public ::testing::Test {
 protected:
  void CreateCodec() {
    auto speech_encoder = rent_a_codec_.RentEncoder(kDefaultCodecInst);
    ASSERT_TRUE(speech_encoder);
    RentACodec::StackParameters param;
    param.use_cng = true;
    param.speech_encoder = std::move(speech_encoder);
    encoder_ = rent_a_codec_.RentEncoderStack(&param);
  }

  void EncodeAndVerify(size_t expected_out_length,
                       uint32_t expected_timestamp,
                       int expected_payload_type,
                       int expected_send_even_if_empty) {
    rtc::Buffer out;
    AudioEncoder::EncodedInfo encoded_info;
    encoded_info =
        encoder_->Encode(timestamp_, kZeroData, &out);
    timestamp_ += kDataLengthSamples;
    EXPECT_TRUE(encoded_info.redundant.empty());
    EXPECT_EQ(expected_out_length, encoded_info.encoded_bytes);
    EXPECT_EQ(expected_timestamp, encoded_info.encoded_timestamp);
    if (expected_payload_type >= 0)
      EXPECT_EQ(expected_payload_type, encoded_info.payload_type);
    if (expected_send_even_if_empty >= 0)
      EXPECT_EQ(static_cast<bool>(expected_send_even_if_empty),
                encoded_info.send_even_if_empty);
  }

  RentACodec rent_a_codec_;
  std::unique_ptr<AudioEncoder> encoder_;
  uint32_t timestamp_ = 0;
};

// This test verifies that CNG frames are delivered as expected. Since the frame
// size is set to 20 ms, we expect the first encode call to produce no output
// (which is signaled as 0 bytes output of type kNoEncoding). The next encode
// call should produce one SID frame of 9 bytes. The third call should not
// result in any output (just like the first one). The fourth and final encode
// call should produce an "empty frame", which is like no output, but with
// AudioEncoder::EncodedInfo::send_even_if_empty set to true. (The reason to
// produce an empty frame is to drive sending of DTMF packets in the RTP/RTCP
// module.)
TEST_F(RentACodecTestF, VerifyCngFrames) {
  CreateCodec();
  uint32_t expected_timestamp = timestamp_;
  // Verify no frame.
  {
    SCOPED_TRACE("First encoding");
    EncodeAndVerify(0, expected_timestamp, -1, -1);
  }

  // Verify SID frame delivered.
  {
    SCOPED_TRACE("Second encoding");
    EncodeAndVerify(9, expected_timestamp, kCngPt, 1);
  }

  // Verify no frame.
  {
    SCOPED_TRACE("Third encoding");
    EncodeAndVerify(0, expected_timestamp, -1, -1);
  }

  // Verify NoEncoding.
  expected_timestamp += 2 * kDataLengthSamples;
  {
    SCOPED_TRACE("Fourth encoding");
    EncodeAndVerify(0, expected_timestamp, kCngPt, 1);
  }
}

TEST(RentACodecTest, ExternalEncoder) {
  const int kSampleRateHz = 8000;
  auto* external_encoder = new MockAudioEncoder;
  EXPECT_CALL(*external_encoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateHz));
  EXPECT_CALL(*external_encoder, NumChannels()).WillRepeatedly(Return(1));
  EXPECT_CALL(*external_encoder, SetFec(false)).WillRepeatedly(Return(true));

  RentACodec rac;
  RentACodec::StackParameters param;
  param.speech_encoder = std::unique_ptr<AudioEncoder>(external_encoder);
  std::unique_ptr<AudioEncoder> encoder_stack = rac.RentEncoderStack(&param);
  EXPECT_EQ(external_encoder, encoder_stack.get());
  const int kPacketSizeSamples = kSampleRateHz / 100;
  int16_t audio[kPacketSizeSamples] = {0};
  rtc::Buffer encoded;
  AudioEncoder::EncodedInfo info;

  Marker marker;
  {
    ::testing::InSequence s;
    info.encoded_timestamp = 0;
    EXPECT_CALL(
        *external_encoder,
        EncodeImpl(0, rtc::ArrayView<const int16_t>(audio), &encoded))
        .WillOnce(Return(info));
    EXPECT_CALL(marker, Mark("A"));
    EXPECT_CALL(marker, Mark("B"));
    EXPECT_CALL(*external_encoder, Die());
    EXPECT_CALL(marker, Mark("C"));
  }

  info = encoder_stack->Encode(0, audio, &encoded);
  EXPECT_EQ(0u, info.encoded_timestamp);
  marker.Mark("A");

  // Change to internal encoder.
  CodecInst codec_inst = kDefaultCodecInst;
  codec_inst.pacsize = kPacketSizeSamples;
  param.speech_encoder = rac.RentEncoder(codec_inst);
  ASSERT_TRUE(param.speech_encoder);
  AudioEncoder* enc = param.speech_encoder.get();
  std::unique_ptr<AudioEncoder> stack = rac.RentEncoderStack(&param);
  EXPECT_EQ(enc, stack.get());

  // Don't expect any more calls to the external encoder.
  info = stack->Encode(1, audio, &encoded);
  marker.Mark("B");
  encoder_stack.reset();
  marker.Mark("C");
}

// Verify that the speech encoder's Reset method is called when CNG or RED
// (or both) are switched on, but not when they're switched off.
void TestCngAndRedResetSpeechEncoder(bool use_cng, bool use_red) {
  auto make_enc = [] {
    auto speech_encoder =
        std::unique_ptr<MockAudioEncoder>(new MockAudioEncoder);
    EXPECT_CALL(*speech_encoder, NumChannels()).WillRepeatedly(Return(1));
    EXPECT_CALL(*speech_encoder, Max10MsFramesInAPacket())
        .WillRepeatedly(Return(2));
    EXPECT_CALL(*speech_encoder, SampleRateHz()).WillRepeatedly(Return(8000));
    EXPECT_CALL(*speech_encoder, SetFec(false)).WillRepeatedly(Return(true));
    return speech_encoder;
  };
  auto speech_encoder1 = make_enc();
  auto speech_encoder2 = make_enc();
  Marker marker;
  {
    ::testing::InSequence s;
    EXPECT_CALL(marker, Mark("disabled"));
    EXPECT_CALL(*speech_encoder1, Die());
    EXPECT_CALL(marker, Mark("enabled"));
    if (use_cng || use_red)
      EXPECT_CALL(*speech_encoder2, Reset());
    EXPECT_CALL(*speech_encoder2, Die());
  }

  RentACodec::StackParameters param1, param2;
  param1.speech_encoder = std::move(speech_encoder1);
  param2.speech_encoder = std::move(speech_encoder2);
  param2.use_cng = use_cng;
  param2.use_red = use_red;
  marker.Mark("disabled");
  RentACodec rac;
  rac.RentEncoderStack(&param1);
  marker.Mark("enabled");
  rac.RentEncoderStack(&param2);
}

TEST(RentACodecTest, CngResetsSpeechEncoder) {
  TestCngAndRedResetSpeechEncoder(true, false);
}

TEST(RentACodecTest, RedResetsSpeechEncoder) {
  TestCngAndRedResetSpeechEncoder(false, true);
}

TEST(RentACodecTest, CngAndRedResetsSpeechEncoder) {
  TestCngAndRedResetSpeechEncoder(true, true);
}

TEST(RentACodecTest, NoCngAndRedNoSpeechEncoderReset) {
  TestCngAndRedResetSpeechEncoder(false, false);
}

TEST(RentACodecTest, RentEncoderError) {
  const CodecInst codec_inst = {
      0, "Robert'); DROP TABLE Students;", 8000, 160, 1, 64000};
  RentACodec rent_a_codec;
  EXPECT_FALSE(rent_a_codec.RentEncoder(codec_inst));
}

TEST(RentACodecTest, RentEncoderStackWithoutSpeechEncoder) {
  RentACodec::StackParameters sp;
  EXPECT_EQ(nullptr, sp.speech_encoder);
  EXPECT_EQ(nullptr, RentACodec().RentEncoderStack(&sp));
}

}  // namespace acm2
}  // namespace webrtc
