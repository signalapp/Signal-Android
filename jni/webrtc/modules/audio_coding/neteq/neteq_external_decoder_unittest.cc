/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Test to verify correct operation for externally created decoders.

#include <memory>

#include "testing/gmock/include/gmock/gmock.h"
#include "webrtc/modules/audio_coding/codecs/builtin_audio_decoder_factory.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_external_decoder_pcm16b.h"
#include "webrtc/modules/audio_coding/neteq/tools/input_audio_file.h"
#include "webrtc/modules/audio_coding/neteq/tools/neteq_external_decoder_test.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_generator.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

using ::testing::_;
using ::testing::Return;

class NetEqExternalDecoderUnitTest : public test::NetEqExternalDecoderTest {
 protected:
  static const int kFrameSizeMs = 10;  // Frame size of Pcm16B.

  NetEqExternalDecoderUnitTest(NetEqDecoder codec,
                               int sample_rate_hz,
                               MockExternalPcm16B* decoder)
      : NetEqExternalDecoderTest(codec, sample_rate_hz, decoder),
        external_decoder_(decoder),
        samples_per_ms_(sample_rate_hz / 1000),
        frame_size_samples_(kFrameSizeMs * samples_per_ms_),
        rtp_generator_(new test::RtpGenerator(samples_per_ms_)),
        input_(new int16_t[frame_size_samples_]),
        // Payload should be no larger than input.
        encoded_(new uint8_t[2 * frame_size_samples_]),
        payload_size_bytes_(0),
        last_send_time_(0),
        last_arrival_time_(0) {
    // NetEq is not allowed to delete the external decoder (hence Times(0)).
    EXPECT_CALL(*external_decoder_, Die()).Times(0);
    Init();

    const std::string file_name =
        webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
    input_file_.reset(new test::InputAudioFile(file_name));
  }

  virtual ~NetEqExternalDecoderUnitTest() {
    delete [] input_;
    delete [] encoded_;
    // ~NetEqExternalDecoderTest() will delete |external_decoder_|, so expecting
    // Die() to be called.
    EXPECT_CALL(*external_decoder_, Die()).Times(1);
  }

  // Method to draw kFrameSizeMs audio and verify the output.
  // Use gTest methods. e.g. ASSERT_EQ() inside to trigger errors.
  virtual void GetAndVerifyOutput() = 0;

  // Method to get the number of calls to the Decode() method of the external
  // decoder.
  virtual int NumExpectedDecodeCalls(int num_loops) = 0;

  // Method to generate packets and return the send time of the packet.
  int GetNewPacket() {
    if (!input_file_->Read(frame_size_samples_, input_)) {
      return -1;
    }
    payload_size_bytes_ = WebRtcPcm16b_Encode(input_, frame_size_samples_,
                                              encoded_);

    int next_send_time = rtp_generator_->GetRtpHeader(
        kPayloadType, frame_size_samples_, &rtp_header_);
    return next_send_time;
  }

  // Method to decide packet losses.
  virtual bool Lost() { return false; }

  // Method to calculate packet arrival time.
  int GetArrivalTime(int send_time) {
    int arrival_time = last_arrival_time_ + (send_time - last_send_time_);
    last_send_time_ = send_time;
    last_arrival_time_ = arrival_time;
    return arrival_time;
  }

  void RunTest(int num_loops) {
    // Get next input packets (mono and multi-channel).
    uint32_t next_send_time;
    uint32_t next_arrival_time;
    do {
      next_send_time = GetNewPacket();
      next_arrival_time = GetArrivalTime(next_send_time);
    } while (Lost());  // If lost, immediately read the next packet.

    EXPECT_CALL(
        *external_decoder_,
        DecodeInternal(_, payload_size_bytes_, 1000 * samples_per_ms_, _, _))
        .Times(NumExpectedDecodeCalls(num_loops));

    uint32_t time_now = 0;
    for (int k = 0; k < num_loops; ++k) {
      while (time_now >= next_arrival_time) {
        InsertPacket(rtp_header_, rtc::ArrayView<const uint8_t>(
                                      encoded_, payload_size_bytes_),
                     next_arrival_time);
        // Get next input packet.
        do {
          next_send_time = GetNewPacket();
          next_arrival_time = GetArrivalTime(next_send_time);
        } while (Lost());  // If lost, immediately read the next packet.
      }

      std::ostringstream ss;
      ss << "Lap number " << k << ".";
      SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
      // Compare mono and multi-channel.
      ASSERT_NO_FATAL_FAILURE(GetAndVerifyOutput());

      time_now += kOutputLengthMs;
    }
  }

  void InsertPacket(WebRtcRTPHeader rtp_header,
                    rtc::ArrayView<const uint8_t> payload,
                    uint32_t receive_timestamp) override {
    EXPECT_CALL(
        *external_decoder_,
        IncomingPacket(_, payload.size(), rtp_header.header.sequenceNumber,
                       rtp_header.header.timestamp, receive_timestamp));
    NetEqExternalDecoderTest::InsertPacket(rtp_header, payload,
                                           receive_timestamp);
  }

  MockExternalPcm16B* external_decoder() { return external_decoder_.get(); }

  void ResetRtpGenerator(test::RtpGenerator* rtp_generator) {
    rtp_generator_.reset(rtp_generator);
  }

  int samples_per_ms() const { return samples_per_ms_; }
 private:
  std::unique_ptr<MockExternalPcm16B> external_decoder_;
  int samples_per_ms_;
  size_t frame_size_samples_;
  std::unique_ptr<test::RtpGenerator> rtp_generator_;
  int16_t* input_;
  uint8_t* encoded_;
  size_t payload_size_bytes_;
  uint32_t last_send_time_;
  uint32_t last_arrival_time_;
  std::unique_ptr<test::InputAudioFile> input_file_;
  WebRtcRTPHeader rtp_header_;
};

// This test encodes a few packets of PCM16b 32 kHz data and inserts it into two
// different NetEq instances. The first instance uses the internal version of
// the decoder object, while the second one uses an externally created decoder
// object (ExternalPcm16B wrapped in MockExternalPcm16B, both defined above).
// The test verifies that the output from both instances match.
class NetEqExternalVsInternalDecoderTest : public NetEqExternalDecoderUnitTest,
                                           public ::testing::Test {
 protected:
  static const size_t kMaxBlockSize = 480;  // 10 ms @ 48 kHz.

  NetEqExternalVsInternalDecoderTest()
      : NetEqExternalDecoderUnitTest(NetEqDecoder::kDecoderPCM16Bswb32kHz,
                                     32000,
                                     new MockExternalPcm16B(32000)),
        sample_rate_hz_(32000) {
    NetEq::Config config;
    config.sample_rate_hz = sample_rate_hz_;
    neteq_internal_.reset(
        NetEq::Create(config, CreateBuiltinAudioDecoderFactory()));
  }

  void SetUp() override {
    ASSERT_EQ(NetEq::kOK, neteq_internal_->RegisterPayloadType(
                              NetEqDecoder::kDecoderPCM16Bswb32kHz,
                              "pcm16-swb32", kPayloadType));
  }

  void GetAndVerifyOutput() override {
    // Get audio from internal decoder instance.
    bool muted;
    EXPECT_EQ(NetEq::kOK, neteq_internal_->GetAudio(&output_internal_, &muted));
    ASSERT_FALSE(muted);
    EXPECT_EQ(1u, output_internal_.num_channels_);
    EXPECT_EQ(static_cast<size_t>(kOutputLengthMs * sample_rate_hz_ / 1000),
              output_internal_.samples_per_channel_);

    // Get audio from external decoder instance.
    GetOutputAudio(&output_);

    for (size_t i = 0; i < output_.samples_per_channel_; ++i) {
      ASSERT_EQ(output_.data_[i], output_internal_.data_[i])
          << "Diff in sample " << i << ".";
    }
  }

  void InsertPacket(WebRtcRTPHeader rtp_header,
                    rtc::ArrayView<const uint8_t> payload,
                    uint32_t receive_timestamp) override {
    // Insert packet in internal decoder.
    ASSERT_EQ(NetEq::kOK, neteq_internal_->InsertPacket(rtp_header, payload,
                                                        receive_timestamp));

    // Insert packet in external decoder instance.
    NetEqExternalDecoderUnitTest::InsertPacket(rtp_header, payload,
                                               receive_timestamp);
  }

  int NumExpectedDecodeCalls(int num_loops) override { return num_loops; }

 private:
  int sample_rate_hz_;
  std::unique_ptr<NetEq> neteq_internal_;
  AudioFrame output_internal_;
  AudioFrame output_;
};

TEST_F(NetEqExternalVsInternalDecoderTest, RunTest) {
  RunTest(100);  // Run 100 laps @ 10 ms each in the test loop.
}

class LargeTimestampJumpTest : public NetEqExternalDecoderUnitTest,
                               public ::testing::Test {
 protected:
  static const size_t kMaxBlockSize = 480;  // 10 ms @ 48 kHz.

  enum TestStates {
    kInitialPhase,
    kNormalPhase,
    kExpandPhase,
    kFadedExpandPhase,
    kRecovered
  };

  LargeTimestampJumpTest()
      : NetEqExternalDecoderUnitTest(NetEqDecoder::kDecoderPCM16B,
                                     8000,
                                     new MockExternalPcm16B(8000)),
        test_state_(kInitialPhase) {
    EXPECT_CALL(*external_decoder(), HasDecodePlc())
        .WillRepeatedly(Return(false));
  }

  virtual void UpdateState(AudioFrame::SpeechType output_type) {
    switch (test_state_) {
      case kInitialPhase: {
        if (output_type == AudioFrame::kNormalSpeech) {
          test_state_ = kNormalPhase;
        }
        break;
      }
      case kNormalPhase: {
        if (output_type == AudioFrame::kPLC) {
          test_state_ = kExpandPhase;
        }
        break;
      }
      case kExpandPhase: {
        if (output_type == AudioFrame::kPLCCNG) {
          test_state_ = kFadedExpandPhase;
        } else if (output_type == AudioFrame::kNormalSpeech) {
          test_state_ = kRecovered;
        }
        break;
      }
      case kFadedExpandPhase: {
        if (output_type == AudioFrame::kNormalSpeech) {
          test_state_ = kRecovered;
        }
        break;
      }
      case kRecovered: {
        break;
      }
    }
  }

  void GetAndVerifyOutput() override {
    AudioFrame output;
    GetOutputAudio(&output);
    UpdateState(output.speech_type_);

    if (test_state_ == kExpandPhase || test_state_ == kFadedExpandPhase) {
      // Don't verify the output in this phase of the test.
      return;
    }

    ASSERT_EQ(1u, output.num_channels_);
    for (size_t i = 0; i < output.samples_per_channel_; ++i) {
      if (output.data_[i] != 0)
        return;
    }
    EXPECT_TRUE(false)
        << "Expected at least one non-zero sample in each output block.";
  }

  int NumExpectedDecodeCalls(int num_loops) override {
    // Some packets at the end of the stream won't be decoded. When the jump in
    // timestamp happens, NetEq will do Expand during one GetAudio call. In the
    // next call it will decode the packet after the jump, but the net result is
    // that the delay increased by 1 packet. In another call, a Pre-emptive
    // Expand operation is performed, leading to delay increase by 1 packet. In
    // total, the test will end with a 2-packet delay, which results in the 2
    // last packets not being decoded.
    return num_loops - 2;
  }

  TestStates test_state_;
};

TEST_F(LargeTimestampJumpTest, JumpLongerThanHalfRange) {
  // Set the timestamp series to start at 2880, increase to 7200, then jump to
  // 2869342376. The sequence numbers start at 42076 and increase by 1 for each
  // packet, also when the timestamp jumps.
  static const uint16_t kStartSeqeunceNumber = 42076;
  static const uint32_t kStartTimestamp = 2880;
  static const uint32_t kJumpFromTimestamp = 7200;
  static const uint32_t kJumpToTimestamp = 2869342376;
  static_assert(kJumpFromTimestamp < kJumpToTimestamp,
                "timestamp jump should not result in wrap");
  static_assert(
      static_cast<uint32_t>(kJumpToTimestamp - kJumpFromTimestamp) > 0x7FFFFFFF,
      "jump should be larger than half range");
  // Replace the default RTP generator with one that jumps in timestamp.
  ResetRtpGenerator(new test::TimestampJumpRtpGenerator(samples_per_ms(),
                                                        kStartSeqeunceNumber,
                                                        kStartTimestamp,
                                                        kJumpFromTimestamp,
                                                        kJumpToTimestamp));

  RunTest(130);  // Run 130 laps @ 10 ms each in the test loop.
  EXPECT_EQ(kRecovered, test_state_);
}

TEST_F(LargeTimestampJumpTest, JumpLongerThanHalfRangeAndWrap) {
  // Make a jump larger than half the 32-bit timestamp range. Set the start
  // timestamp such that the jump will result in a wrap around.
  static const uint16_t kStartSeqeunceNumber = 42076;
  // Set the jump length slightly larger than 2^31.
  static const uint32_t kStartTimestamp = 3221223116;
  static const uint32_t kJumpFromTimestamp = 3221223216;
  static const uint32_t kJumpToTimestamp = 1073744278;
  static_assert(kJumpToTimestamp < kJumpFromTimestamp,
                "timestamp jump should result in wrap");
  static_assert(
      static_cast<uint32_t>(kJumpToTimestamp - kJumpFromTimestamp) > 0x7FFFFFFF,
      "jump should be larger than half range");
  // Replace the default RTP generator with one that jumps in timestamp.
  ResetRtpGenerator(new test::TimestampJumpRtpGenerator(samples_per_ms(),
                                                        kStartSeqeunceNumber,
                                                        kStartTimestamp,
                                                        kJumpFromTimestamp,
                                                        kJumpToTimestamp));

  RunTest(130);  // Run 130 laps @ 10 ms each in the test loop.
  EXPECT_EQ(kRecovered, test_state_);
}

class ShortTimestampJumpTest : public LargeTimestampJumpTest {
 protected:
  void UpdateState(AudioFrame::SpeechType output_type) override {
    switch (test_state_) {
      case kInitialPhase: {
        if (output_type == AudioFrame::kNormalSpeech) {
          test_state_ = kNormalPhase;
        }
        break;
      }
      case kNormalPhase: {
        if (output_type == AudioFrame::kPLC) {
          test_state_ = kExpandPhase;
        }
        break;
      }
      case kExpandPhase: {
        if (output_type == AudioFrame::kNormalSpeech) {
          test_state_ = kRecovered;
        }
        break;
      }
      case kRecovered: {
        break;
      }
      default: { FAIL(); }
    }
  }

  int NumExpectedDecodeCalls(int num_loops) override {
    // Some packets won't be decoded because of the timestamp jump.
    return num_loops - 2;
  }
};

TEST_F(ShortTimestampJumpTest, JumpShorterThanHalfRange) {
  // Make a jump shorter than half the 32-bit timestamp range. Set the start
  // timestamp such that the jump will not result in a wrap around.
  static const uint16_t kStartSeqeunceNumber = 42076;
  // Set the jump length slightly smaller than 2^31.
  static const uint32_t kStartTimestamp = 4711;
  static const uint32_t kJumpFromTimestamp = 4811;
  static const uint32_t kJumpToTimestamp = 2147483747;
  static_assert(kJumpFromTimestamp < kJumpToTimestamp,
                "timestamp jump should not result in wrap");
  static_assert(
      static_cast<uint32_t>(kJumpToTimestamp - kJumpFromTimestamp) < 0x7FFFFFFF,
      "jump should be smaller than half range");
  // Replace the default RTP generator with one that jumps in timestamp.
  ResetRtpGenerator(new test::TimestampJumpRtpGenerator(samples_per_ms(),
                                                        kStartSeqeunceNumber,
                                                        kStartTimestamp,
                                                        kJumpFromTimestamp,
                                                        kJumpToTimestamp));

  RunTest(130);  // Run 130 laps @ 10 ms each in the test loop.
  EXPECT_EQ(kRecovered, test_state_);
}

TEST_F(ShortTimestampJumpTest, JumpShorterThanHalfRangeAndWrap) {
  // Make a jump shorter than half the 32-bit timestamp range. Set the start
  // timestamp such that the jump will result in a wrap around.
  static const uint16_t kStartSeqeunceNumber = 42076;
  // Set the jump length slightly smaller than 2^31.
  static const uint32_t kStartTimestamp = 3221227827;
  static const uint32_t kJumpFromTimestamp = 3221227927;
  static const uint32_t kJumpToTimestamp = 1073739567;
  static_assert(kJumpToTimestamp < kJumpFromTimestamp,
                "timestamp jump should result in wrap");
  static_assert(
      static_cast<uint32_t>(kJumpToTimestamp - kJumpFromTimestamp) < 0x7FFFFFFF,
      "jump should be smaller than half range");
  // Replace the default RTP generator with one that jumps in timestamp.
  ResetRtpGenerator(new test::TimestampJumpRtpGenerator(samples_per_ms(),
                                                        kStartSeqeunceNumber,
                                                        kStartTimestamp,
                                                        kJumpFromTimestamp,
                                                        kJumpToTimestamp));

  RunTest(130);  // Run 130 laps @ 10 ms each in the test loop.
  EXPECT_EQ(kRecovered, test_state_);
}

}  // namespace webrtc
