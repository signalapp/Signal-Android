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

#include <string>
#include <list>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/interface/neteq.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_external_decoder_pcm16b.h"
#include "webrtc/modules/audio_coding/neteq/tools/input_audio_file.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_generator.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/test/testsupport/gtest_disable.h"

namespace webrtc {

using ::testing::_;

// This test encodes a few packets of PCM16b 32 kHz data and inserts it into two
// different NetEq instances. The first instance uses the internal version of
// the decoder object, while the second one uses an externally created decoder
// object (ExternalPcm16B wrapped in MockExternalPcm16B, both defined above).
// The test verifies that the output from both instances match.
class NetEqExternalDecoderTest : public ::testing::Test {
 protected:
  static const int kTimeStepMs = 10;
  static const int kMaxBlockSize = 480;  // 10 ms @ 48 kHz.
  static const uint8_t kPayloadType = 95;
  static const int kSampleRateHz = 32000;

  NetEqExternalDecoderTest()
      : sample_rate_hz_(kSampleRateHz),
        samples_per_ms_(sample_rate_hz_ / 1000),
        frame_size_ms_(10),
        frame_size_samples_(frame_size_ms_ * samples_per_ms_),
        output_size_samples_(frame_size_ms_ * samples_per_ms_),
        external_decoder_(new MockExternalPcm16B(kDecoderPCM16Bswb32kHz)),
        rtp_generator_(samples_per_ms_),
        payload_size_bytes_(0),
        last_send_time_(0),
        last_arrival_time_(0) {
    NetEq::Config config;
    config.sample_rate_hz = sample_rate_hz_;
    neteq_external_ = NetEq::Create(config);
    neteq_ = NetEq::Create(config);
    input_ = new int16_t[frame_size_samples_];
    encoded_ = new uint8_t[2 * frame_size_samples_];
  }

  ~NetEqExternalDecoderTest() {
    delete neteq_external_;
    delete neteq_;
    // We will now delete the decoder ourselves, so expecting Die to be called.
    EXPECT_CALL(*external_decoder_, Die()).Times(1);
    delete external_decoder_;
    delete [] input_;
    delete [] encoded_;
  }

  virtual void SetUp() {
    const std::string file_name =
        webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
    input_file_.reset(new test::InputAudioFile(file_name));
    assert(sample_rate_hz_ == 32000);
    NetEqDecoder decoder = kDecoderPCM16Bswb32kHz;
    EXPECT_CALL(*external_decoder_, Init());
    // NetEq is not allowed to delete the external decoder (hence Times(0)).
    EXPECT_CALL(*external_decoder_, Die()).Times(0);
    ASSERT_EQ(NetEq::kOK,
              neteq_external_->RegisterExternalDecoder(external_decoder_,
                                                       decoder,
                                                       kPayloadType));
    ASSERT_EQ(NetEq::kOK,
              neteq_->RegisterPayloadType(decoder, kPayloadType));
  }

  virtual void TearDown() {}

  int GetNewPackets() {
    if (!input_file_->Read(frame_size_samples_, input_)) {
      return -1;
    }
    payload_size_bytes_ = WebRtcPcm16b_Encode(input_, frame_size_samples_,
                                             encoded_);
    if (frame_size_samples_ * 2 != payload_size_bytes_) {
      return -1;
    }
    int next_send_time = rtp_generator_.GetRtpHeader(kPayloadType,
                                                     frame_size_samples_,
                                                     &rtp_header_);
    return next_send_time;
  }

  void VerifyOutput(size_t num_samples) {
    for (size_t i = 0; i < num_samples; ++i) {
      ASSERT_EQ(output_[i], output_external_[i]) <<
          "Diff in sample " << i << ".";
    }
  }

  virtual int GetArrivalTime(int send_time) {
    int arrival_time = last_arrival_time_ + (send_time - last_send_time_);
    last_send_time_ = send_time;
    last_arrival_time_ = arrival_time;
    return arrival_time;
  }

  virtual bool Lost() { return false; }

  void RunTest(int num_loops) {
    // Get next input packets (mono and multi-channel).
    int next_send_time;
    int next_arrival_time;
    do {
      next_send_time = GetNewPackets();
      ASSERT_NE(-1, next_send_time);
      next_arrival_time = GetArrivalTime(next_send_time);
    } while (Lost());  // If lost, immediately read the next packet.

    EXPECT_CALL(*external_decoder_, Decode(_, payload_size_bytes_, _, _))
        .Times(num_loops);

    int time_now = 0;
    for (int k = 0; k < num_loops; ++k) {
      while (time_now >= next_arrival_time) {
        // Insert packet in regular instance.
        ASSERT_EQ(NetEq::kOK,
                  neteq_->InsertPacket(rtp_header_, encoded_,
                                       payload_size_bytes_,
                                       next_arrival_time));
        // Insert packet in external decoder instance.
        EXPECT_CALL(*external_decoder_,
                    IncomingPacket(_, payload_size_bytes_,
                                   rtp_header_.header.sequenceNumber,
                                   rtp_header_.header.timestamp,
                                   next_arrival_time));
        ASSERT_EQ(NetEq::kOK,
                  neteq_external_->InsertPacket(rtp_header_, encoded_,
                                                payload_size_bytes_,
                                                next_arrival_time));
        // Get next input packet.
        do {
          next_send_time = GetNewPackets();
          ASSERT_NE(-1, next_send_time);
          next_arrival_time = GetArrivalTime(next_send_time);
        } while (Lost());  // If lost, immediately read the next packet.
      }
      NetEqOutputType output_type;
      // Get audio from regular instance.
      int samples_per_channel;
      int num_channels;
      EXPECT_EQ(NetEq::kOK,
                neteq_->GetAudio(kMaxBlockSize, output_,
                                 &samples_per_channel, &num_channels,
                                 &output_type));
      EXPECT_EQ(1, num_channels);
      EXPECT_EQ(output_size_samples_, samples_per_channel);
      // Get audio from external decoder instance.
      ASSERT_EQ(NetEq::kOK,
                neteq_external_->GetAudio(kMaxBlockSize, output_external_,
                                          &samples_per_channel, &num_channels,
                                          &output_type));
      EXPECT_EQ(1, num_channels);
      EXPECT_EQ(output_size_samples_, samples_per_channel);
      std::ostringstream ss;
      ss << "Lap number " << k << ".";
      SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
      // Compare mono and multi-channel.
      ASSERT_NO_FATAL_FAILURE(VerifyOutput(output_size_samples_));

      time_now += kTimeStepMs;
    }
  }

  const int sample_rate_hz_;
  const int samples_per_ms_;
  const int frame_size_ms_;
  const int frame_size_samples_;
  const int output_size_samples_;
  NetEq* neteq_external_;
  NetEq* neteq_;
  MockExternalPcm16B* external_decoder_;
  test::RtpGenerator rtp_generator_;
  int16_t* input_;
  uint8_t* encoded_;
  int16_t output_[kMaxBlockSize];
  int16_t output_external_[kMaxBlockSize];
  WebRtcRTPHeader rtp_header_;
  int payload_size_bytes_;
  int last_send_time_;
  int last_arrival_time_;
  scoped_ptr<test::InputAudioFile> input_file_;
};

TEST_F(NetEqExternalDecoderTest, RunTest) {
  RunTest(100);  // Run 100 laps @ 10 ms each in the test loop.
}

}  // namespace webrtc
