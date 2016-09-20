/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_TEST_TESTSTEREO_H_
#define WEBRTC_MODULES_AUDIO_CODING_TEST_TESTSTEREO_H_

#include <math.h>

#include <memory>

#include "webrtc/modules/audio_coding/test/ACMTest.h"
#include "webrtc/modules/audio_coding/test/Channel.h"
#include "webrtc/modules/audio_coding/test/PCMFile.h"

#define PCMA_AND_PCMU

namespace webrtc {

enum StereoMonoMode {
  kNotSet,
  kMono,
  kStereo
};

class TestPackStereo : public AudioPacketizationCallback {
 public:
  TestPackStereo();
  ~TestPackStereo();

  void RegisterReceiverACM(AudioCodingModule* acm);

  int32_t SendData(const FrameType frame_type,
                   const uint8_t payload_type,
                   const uint32_t timestamp,
                   const uint8_t* payload_data,
                   const size_t payload_size,
                   const RTPFragmentationHeader* fragmentation) override;

  uint16_t payload_size();
  uint32_t timestamp_diff();
  void reset_payload_size();
  void set_codec_mode(StereoMonoMode mode);
  void set_lost_packet(bool lost);

 private:
  AudioCodingModule* receiver_acm_;
  int16_t seq_no_;
  uint32_t timestamp_diff_;
  uint32_t last_in_timestamp_;
  uint64_t total_bytes_;
  int payload_size_;
  StereoMonoMode codec_mode_;
  // Simulate packet losses
  bool lost_packet_;
};

class TestStereo : public ACMTest {
 public:
  explicit TestStereo(int test_mode);
  ~TestStereo();

  void Perform() override;

 private:
  // The default value of '-1' indicates that the registration is based only on
  // codec name and a sampling frequncy matching is not required. This is useful
  // for codecs which support several sampling frequency.
  void RegisterSendCodec(char side, char* codec_name, int32_t samp_freq_hz,
                         int rate, int pack_size, int channels,
                         int payload_type);

  void Run(TestPackStereo* channel, int in_channels, int out_channels,
           int percent_loss = 0);
  void OpenOutFile(int16_t test_number);
  void DisplaySendReceiveCodec();

  int test_mode_;

  std::unique_ptr<AudioCodingModule> acm_a_;
  std::unique_ptr<AudioCodingModule> acm_b_;

  TestPackStereo* channel_a2b_;

  PCMFile* in_file_stereo_;
  PCMFile* in_file_mono_;
  PCMFile out_file_;
  int16_t test_cntr_;
  uint16_t pack_size_samp_;
  uint16_t pack_size_bytes_;
  int counter_;
  char* send_codec_name_;

  // Payload types for stereo codecs and CNG
#ifdef WEBRTC_CODEC_G722
  int g722_pltype_;
#endif
  int l16_8khz_pltype_;
  int l16_16khz_pltype_;
  int l16_32khz_pltype_;
#ifdef PCMA_AND_PCMU
  int pcma_pltype_;
  int pcmu_pltype_;
#endif
#ifdef WEBRTC_CODEC_OPUS
  int opus_pltype_;
#endif
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_TEST_TESTSTEREO_H_
