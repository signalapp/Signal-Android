/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_QUALITY_TEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_QUALITY_TEST_H_

#include <fstream>
#include <memory>
#include <gflags/gflags.h>
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/modules/audio_coding/neteq/tools/audio_sink.h"
#include "webrtc/modules/audio_coding/neteq/tools/input_audio_file.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_generator.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/typedefs.h"

using google::RegisterFlagValidator;

namespace webrtc {
namespace test {

class LossModel {
 public:
  virtual ~LossModel() {};
  virtual bool Lost() = 0;
};

class NoLoss : public LossModel {
 public:
  bool Lost() override;
};

class UniformLoss : public LossModel {
 public:
  UniformLoss(double loss_rate);
  bool Lost() override;
  void set_loss_rate(double loss_rate) { loss_rate_ = loss_rate; }

 private:
  double loss_rate_;
};

class GilbertElliotLoss : public LossModel {
 public:
  GilbertElliotLoss(double prob_trans_11, double prob_trans_01);
  bool Lost() override;

 private:
  // Prob. of losing current packet, when previous packet is lost.
  double prob_trans_11_;
  // Prob. of losing current packet, when previous packet is not lost.
  double prob_trans_01_;
  bool lost_last_;
  std::unique_ptr<UniformLoss> uniform_loss_model_;
};

class NetEqQualityTest : public ::testing::Test {
 protected:
  NetEqQualityTest(int block_duration_ms,
                   int in_sampling_khz,
                   int out_sampling_khz,
                   NetEqDecoder decoder_type);
  virtual ~NetEqQualityTest();

  void SetUp() override;

  // EncodeBlock(...) does the following:
  // 1. encodes a block of audio, saved in |in_data| and has a length of
  // |block_size_samples| (samples per channel),
  // 2. save the bit stream to |payload| of |max_bytes| bytes in size,
  // 3. returns the length of the payload (in bytes),
  virtual int EncodeBlock(int16_t* in_data, size_t block_size_samples,
                          rtc::Buffer* payload, size_t max_bytes) = 0;

  // PacketLost(...) determines weather a packet sent at an indicated time gets
  // lost or not.
  bool PacketLost();

  // DecodeBlock() decodes a block of audio using the payload stored in
  // |payload_| with the length of |payload_size_bytes_| (bytes). The decoded
  // audio is to be stored in |out_data_|.
  int DecodeBlock();

  // Transmit() uses |rtp_generator_| to generate a packet and passes it to
  // |neteq_|.
  int Transmit();

  // Runs encoding / transmitting / decoding.
  void Simulate();

  // Write to log file. Usage Log() << ...
  std::ofstream& Log();

  NetEqDecoder decoder_type_;
  const size_t channels_;

 private:
  int decoded_time_ms_;
  int decodable_time_ms_;
  double drift_factor_;
  int packet_loss_rate_;
  const int block_duration_ms_;
  const int in_sampling_khz_;
  const int out_sampling_khz_;

  // Number of samples per channel in a frame.
  const size_t in_size_samples_;

  size_t payload_size_bytes_;
  size_t max_payload_bytes_;

  std::unique_ptr<InputAudioFile> in_file_;
  std::unique_ptr<AudioSink> output_;
  std::ofstream log_file_;

  std::unique_ptr<RtpGenerator> rtp_generator_;
  std::unique_ptr<NetEq> neteq_;
  std::unique_ptr<LossModel> loss_model_;

  std::unique_ptr<int16_t[]> in_data_;
  rtc::Buffer payload_;
  AudioFrame out_frame_;
  WebRtcRTPHeader rtp_header_;

  size_t total_payload_size_bytes_;
};

}  // namespace test
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_QUALITY_TEST_H_
