/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/TestStereo.h"

#include <assert.h>

#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_types.h"
#include "webrtc/engine_configurations.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "webrtc/modules/audio_coding/test/utility.h"
#include "webrtc/system_wrappers/include/trace.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

// Class for simulating packet handling
TestPackStereo::TestPackStereo()
    : receiver_acm_(NULL),
      seq_no_(0),
      timestamp_diff_(0),
      last_in_timestamp_(0),
      total_bytes_(0),
      payload_size_(0),
      codec_mode_(kNotSet),
      lost_packet_(false) {
}

TestPackStereo::~TestPackStereo() {
}

void TestPackStereo::RegisterReceiverACM(AudioCodingModule* acm) {
  receiver_acm_ = acm;
  return;
}

int32_t TestPackStereo::SendData(const FrameType frame_type,
                                 const uint8_t payload_type,
                                 const uint32_t timestamp,
                                 const uint8_t* payload_data,
                                 const size_t payload_size,
                                 const RTPFragmentationHeader* fragmentation) {
  WebRtcRTPHeader rtp_info;
  int32_t status = 0;

  rtp_info.header.markerBit = false;
  rtp_info.header.ssrc = 0;
  rtp_info.header.sequenceNumber = seq_no_++;
  rtp_info.header.payloadType = payload_type;
  rtp_info.header.timestamp = timestamp;
  if (frame_type == kEmptyFrame) {
    // Skip this frame
    return 0;
  }

  if (lost_packet_ == false) {
    if (frame_type != kAudioFrameCN) {
      rtp_info.type.Audio.isCNG = false;
      rtp_info.type.Audio.channel = static_cast<int>(codec_mode_);
    } else {
      rtp_info.type.Audio.isCNG = true;
      rtp_info.type.Audio.channel = static_cast<int>(kMono);
    }
    status = receiver_acm_->IncomingPacket(payload_data, payload_size,
                                           rtp_info);

    if (frame_type != kAudioFrameCN) {
      payload_size_ = static_cast<int>(payload_size);
    } else {
      payload_size_ = -1;
    }

    timestamp_diff_ = timestamp - last_in_timestamp_;
    last_in_timestamp_ = timestamp;
    total_bytes_ += payload_size;
  }
  return status;
}

uint16_t TestPackStereo::payload_size() {
  return static_cast<uint16_t>(payload_size_);
}

uint32_t TestPackStereo::timestamp_diff() {
  return timestamp_diff_;
}

void TestPackStereo::reset_payload_size() {
  payload_size_ = 0;
}

void TestPackStereo::set_codec_mode(enum StereoMonoMode mode) {
  codec_mode_ = mode;
}

void TestPackStereo::set_lost_packet(bool lost) {
  lost_packet_ = lost;
}

TestStereo::TestStereo(int test_mode)
    : acm_a_(AudioCodingModule::Create(0)),
      acm_b_(AudioCodingModule::Create(1)),
      channel_a2b_(NULL),
      test_cntr_(0),
      pack_size_samp_(0),
      pack_size_bytes_(0),
      counter_(0)
#ifdef WEBRTC_CODEC_G722
      , g722_pltype_(0)
#endif
      , l16_8khz_pltype_(-1)
      , l16_16khz_pltype_(-1)
      , l16_32khz_pltype_(-1)
#ifdef PCMA_AND_PCMU
      , pcma_pltype_(-1)
      , pcmu_pltype_(-1)
#endif
#ifdef WEBRTC_CODEC_OPUS
      , opus_pltype_(-1)
#endif
      {
  // test_mode = 0 for silent test (auto test)
  test_mode_ = test_mode;
}

TestStereo::~TestStereo() {
  if (channel_a2b_ != NULL) {
    delete channel_a2b_;
    channel_a2b_ = NULL;
  }
}

void TestStereo::Perform() {
  uint16_t frequency_hz;
  int audio_channels;
  int codec_channels;
  bool dtx;
  bool vad;
  ACMVADMode vad_mode;

  // Open both mono and stereo test files in 32 kHz.
  const std::string file_name_stereo = webrtc::test::ResourcePath(
      "audio_coding/teststereo32kHz", "pcm");
  const std::string file_name_mono = webrtc::test::ResourcePath(
      "audio_coding/testfile32kHz", "pcm");
  frequency_hz = 32000;
  in_file_stereo_ = new PCMFile();
  in_file_mono_ = new PCMFile();
  in_file_stereo_->Open(file_name_stereo, frequency_hz, "rb");
  in_file_stereo_->ReadStereo(true);
  in_file_mono_->Open(file_name_mono, frequency_hz, "rb");
  in_file_mono_->ReadStereo(false);

  // Create and initialize two ACMs, one for each side of a one-to-one call.
  ASSERT_TRUE((acm_a_.get() != NULL) && (acm_b_.get() != NULL));
  EXPECT_EQ(0, acm_a_->InitializeReceiver());
  EXPECT_EQ(0, acm_b_->InitializeReceiver());

  // Register all available codes as receiving codecs.
  uint8_t num_encoders = acm_a_->NumberOfCodecs();
  CodecInst my_codec_param;
  for (uint8_t n = 0; n < num_encoders; n++) {
    EXPECT_EQ(0, acm_b_->Codec(n, &my_codec_param));
    EXPECT_EQ(0, acm_b_->RegisterReceiveCodec(my_codec_param));
  }

  // Test that unregister all receive codecs works.
  for (uint8_t n = 0; n < num_encoders; n++) {
    EXPECT_EQ(0, acm_b_->Codec(n, &my_codec_param));
    EXPECT_EQ(0, acm_b_->UnregisterReceiveCodec(my_codec_param.pltype));
  }

  // Register all available codes as receiving codecs once more.
  for (uint8_t n = 0; n < num_encoders; n++) {
    EXPECT_EQ(0, acm_b_->Codec(n, &my_codec_param));
    EXPECT_EQ(0, acm_b_->RegisterReceiveCodec(my_codec_param));
  }

  // Create and connect the channel.
  channel_a2b_ = new TestPackStereo;
  EXPECT_EQ(0, acm_a_->RegisterTransportCallback(channel_a2b_));
  channel_a2b_->RegisterReceiverACM(acm_b_.get());

  // Start with setting VAD/DTX, before we know we will send stereo.
  // Continue with setting a stereo codec as send codec and verify that
  // VAD/DTX gets turned off.
  EXPECT_EQ(0, acm_a_->SetVAD(true, true, VADNormal));
  EXPECT_EQ(0, acm_a_->VAD(&dtx, &vad, &vad_mode));
  EXPECT_TRUE(dtx);
  EXPECT_TRUE(vad);
  char codec_pcma_temp[] = "PCMA";
  RegisterSendCodec('A', codec_pcma_temp, 8000, 64000, 80, 2, pcma_pltype_);
  EXPECT_EQ(0, acm_a_->VAD(&dtx, &vad, &vad_mode));
  EXPECT_FALSE(dtx);
  EXPECT_FALSE(vad);
  if (test_mode_ != 0) {
    printf("\n");
  }

  //
  // Test Stereo-To-Stereo for all codecs.
  //
  audio_channels = 2;
  codec_channels = 2;

  // All codecs are tested for all allowed sampling frequencies, rates and
  // packet sizes.
#ifdef WEBRTC_CODEC_G722
  if (test_mode_ != 0) {
    printf("===========================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-stereo\n");
  }
  channel_a2b_->set_codec_mode(kStereo);
  test_cntr_++;
  OpenOutFile(test_cntr_);
  char codec_g722[] = "G722";
  RegisterSendCodec('A', codec_g722, 16000, 64000, 160, codec_channels,
      g722_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_g722, 16000, 64000, 320, codec_channels,
      g722_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_g722, 16000, 64000, 480, codec_channels,
      g722_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_g722, 16000, 64000, 640, codec_channels,
      g722_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_g722, 16000, 64000, 800, codec_channels,
      g722_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_g722, 16000, 64000, 960, codec_channels,
      g722_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#endif
  if (test_mode_ != 0) {
    printf("===========================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-stereo\n");
  }
  channel_a2b_->set_codec_mode(kStereo);
  test_cntr_++;
  OpenOutFile(test_cntr_);
  char codec_l16[] = "L16";
  RegisterSendCodec('A', codec_l16, 8000, 128000, 80, codec_channels,
      l16_8khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_l16, 8000, 128000, 160, codec_channels,
      l16_8khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_l16, 8000, 128000, 240, codec_channels,
      l16_8khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_l16, 8000, 128000, 320, codec_channels,
      l16_8khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();

  if (test_mode_ != 0) {
    printf("===========================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-stereo\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_l16, 16000, 256000, 160, codec_channels,
      l16_16khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_l16, 16000, 256000, 320, codec_channels,
      l16_16khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_l16, 16000, 256000, 480, codec_channels,
      l16_16khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_l16, 16000, 256000, 640, codec_channels,
      l16_16khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();

  if (test_mode_ != 0) {
    printf("===========================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-stereo\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_l16, 32000, 512000, 320, codec_channels,
      l16_32khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_l16, 32000, 512000, 640, codec_channels,
      l16_32khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#ifdef PCMA_AND_PCMU
  if (test_mode_ != 0) {
    printf("===========================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-stereo\n");
  }
  channel_a2b_->set_codec_mode(kStereo);
  audio_channels = 2;
  codec_channels = 2;
  test_cntr_++;
  OpenOutFile(test_cntr_);
  char codec_pcma[] = "PCMA";
  RegisterSendCodec('A', codec_pcma, 8000, 64000, 80, codec_channels,
                    pcma_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcma, 8000, 64000, 160, codec_channels,
                    pcma_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcma, 8000, 64000, 240, codec_channels,
                    pcma_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcma, 8000, 64000, 320, codec_channels,
                    pcma_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcma, 8000, 64000, 400, codec_channels,
                    pcma_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcma, 8000, 64000, 480, codec_channels,
                    pcma_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);

  // Test that VAD/DTX cannot be turned on while sending stereo.
  EXPECT_EQ(-1, acm_a_->SetVAD(true, true, VADNormal));
  EXPECT_EQ(0, acm_a_->VAD(&dtx, &vad, &vad_mode));
  EXPECT_FALSE(dtx);
  EXPECT_FALSE(vad);
  EXPECT_EQ(0, acm_a_->SetVAD(false, false, VADNormal));
  EXPECT_EQ(0, acm_a_->VAD(&dtx, &vad, &vad_mode));
  EXPECT_FALSE(dtx);
  EXPECT_FALSE(vad);

  out_file_.Close();
  if (test_mode_ != 0) {
    printf("===========================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-stereo\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  char codec_pcmu[] = "PCMU";
  RegisterSendCodec('A', codec_pcmu, 8000, 64000, 80, codec_channels,
                    pcmu_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcmu, 8000, 64000, 160, codec_channels,
                    pcmu_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcmu, 8000, 64000, 240, codec_channels,
                    pcmu_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcmu, 8000, 64000, 320, codec_channels,
                    pcmu_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcmu, 8000, 64000, 400, codec_channels,
                    pcmu_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcmu, 8000, 64000, 480, codec_channels,
                    pcmu_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#endif
#ifdef WEBRTC_CODEC_OPUS
  if (test_mode_ != 0) {
    printf("===========================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-stereo\n");
  }
  channel_a2b_->set_codec_mode(kStereo);
  audio_channels = 2;
  codec_channels = 2;
  test_cntr_++;
  OpenOutFile(test_cntr_);

  char codec_opus[] = "opus";
  // Run Opus with 10 ms frame size.
  RegisterSendCodec('A', codec_opus, 48000, 64000, 480, codec_channels,
      opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  // Run Opus with 20 ms frame size.
  RegisterSendCodec('A', codec_opus, 48000, 64000, 480*2, codec_channels,
      opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  // Run Opus with 40 ms frame size.
  RegisterSendCodec('A', codec_opus, 48000, 64000, 480*4, codec_channels,
      opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  // Run Opus with 60 ms frame size.
  RegisterSendCodec('A', codec_opus, 48000, 64000, 480*6, codec_channels,
      opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  // Run Opus with 20 ms frame size and different bitrates.
  RegisterSendCodec('A', codec_opus, 48000, 40000, 960, codec_channels,
      opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_opus, 48000, 510000, 960, codec_channels,
      opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#endif
  //
  // Test Mono-To-Stereo for all codecs.
  //
  audio_channels = 1;
  codec_channels = 2;

#ifdef WEBRTC_CODEC_G722
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Mono-to-stereo\n");
  }
  test_cntr_++;
  channel_a2b_->set_codec_mode(kStereo);
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_g722, 16000, 64000, 160, codec_channels,
      g722_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#endif
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Mono-to-stereo\n");
  }
  test_cntr_++;
  channel_a2b_->set_codec_mode(kStereo);
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_l16, 8000, 128000, 80, codec_channels,
      l16_8khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Mono-to-stereo\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_l16, 16000, 256000, 160, codec_channels,
      l16_16khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Mono-to-stereo\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_l16, 32000, 512000, 320, codec_channels,
      l16_32khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#ifdef PCMA_AND_PCMU
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Mono-to-stereo\n");
  }
  test_cntr_++;
  channel_a2b_->set_codec_mode(kStereo);
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_pcmu, 8000, 64000, 80, codec_channels,
                    pcmu_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcma, 8000, 64000, 80, codec_channels,
                    pcma_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#endif
#ifdef WEBRTC_CODEC_OPUS
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Mono-to-stereo\n");
  }

  // Keep encode and decode in stereo.
  test_cntr_++;
  channel_a2b_->set_codec_mode(kStereo);
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_opus, 48000, 64000, 960, codec_channels,
      opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);

  // Encode in mono, decode in stereo mode.
  RegisterSendCodec('A', codec_opus, 48000, 64000, 960, 1, opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#endif

  //
  // Test Stereo-To-Mono for all codecs.
  //
  audio_channels = 2;
  codec_channels = 1;
  channel_a2b_->set_codec_mode(kMono);

#ifdef WEBRTC_CODEC_G722
  // Run stereo audio and mono codec.
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-mono\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_g722, 16000, 64000, 160, codec_channels,
      g722_pltype_);

  // Make sure it is possible to set VAD/CNG, now that we are sending mono
  // again.
  EXPECT_EQ(0, acm_a_->SetVAD(true, true, VADNormal));
  EXPECT_EQ(0, acm_a_->VAD(&dtx, &vad, &vad_mode));
  EXPECT_TRUE(dtx);
  EXPECT_TRUE(vad);
  EXPECT_EQ(0, acm_a_->SetVAD(false, false, VADNormal));
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#endif
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-mono\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_l16, 8000, 128000, 80, codec_channels,
      l16_8khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-mono\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_l16, 16000, 256000, 160, codec_channels,
      l16_16khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
  if (test_mode_ != 0) {
    printf("==============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-mono\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_l16, 32000, 512000, 320, codec_channels,
      l16_32khz_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#ifdef PCMA_AND_PCMU
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-mono\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  RegisterSendCodec('A', codec_pcmu, 8000, 64000, 80, codec_channels,
                    pcmu_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  RegisterSendCodec('A', codec_pcma, 8000, 64000, 80, codec_channels,
                    pcma_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
#endif
#ifdef WEBRTC_CODEC_OPUS
  if (test_mode_ != 0) {
    printf("===============================================================\n");
    printf("Test number: %d\n", test_cntr_ + 1);
    printf("Test type: Stereo-to-mono\n");
  }
  test_cntr_++;
  OpenOutFile(test_cntr_);
  // Encode and decode in mono.
  RegisterSendCodec('A', codec_opus, 48000, 32000, 960, codec_channels,
      opus_pltype_);
  CodecInst opus_codec_param;
  for (uint8_t n = 0; n < num_encoders; n++) {
    EXPECT_EQ(0, acm_b_->Codec(n, &opus_codec_param));
    if (!strcmp(opus_codec_param.plname, "opus")) {
      opus_codec_param.channels = 1;
      EXPECT_EQ(0, acm_b_->RegisterReceiveCodec(opus_codec_param));
      break;
    }
  }
  Run(channel_a2b_, audio_channels, codec_channels);

  // Encode in stereo, decode in mono.
  RegisterSendCodec('A', codec_opus, 48000, 32000, 960, 2, opus_pltype_);
  Run(channel_a2b_, audio_channels, codec_channels);

  out_file_.Close();

  // Test switching between decoding mono and stereo for Opus.

  // Decode in mono.
  test_cntr_++;
  OpenOutFile(test_cntr_);
  if (test_mode_ != 0) {
    // Print out codec and settings
    printf("Test number: %d\nCodec: Opus Freq: 48000 Rate :32000 PackSize: 960"
        " Decode: mono\n", test_cntr_);
  }
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();
  // Decode in stereo.
  test_cntr_++;
  OpenOutFile(test_cntr_);
  if (test_mode_ != 0) {
    // Print out codec and settings
    printf("Test number: %d\nCodec: Opus Freq: 48000 Rate :32000 PackSize: 960"
        " Decode: stereo\n", test_cntr_);
  }
  opus_codec_param.channels = 2;
  EXPECT_EQ(0, acm_b_->RegisterReceiveCodec(opus_codec_param));
  Run(channel_a2b_, audio_channels, 2);
  out_file_.Close();
  // Decode in mono.
  test_cntr_++;
  OpenOutFile(test_cntr_);
  if (test_mode_ != 0) {
    // Print out codec and settings
    printf("Test number: %d\nCodec: Opus Freq: 48000 Rate :32000 PackSize: 960"
        " Decode: mono\n", test_cntr_);
  }
  opus_codec_param.channels = 1;
  EXPECT_EQ(0, acm_b_->RegisterReceiveCodec(opus_codec_param));
  Run(channel_a2b_, audio_channels, codec_channels);
  out_file_.Close();

#endif

  // Print out which codecs were tested, and which were not, in the run.
  if (test_mode_ != 0) {
    printf("\nThe following codecs was INCLUDED in the test:\n");
#ifdef WEBRTC_CODEC_G722
    printf("   G.722\n");
#endif
    printf("   PCM16\n");
    printf("   G.711\n");
#ifdef WEBRTC_CODEC_OPUS
    printf("   Opus\n");
#endif
    printf("\nTo complete the test, listen to the %d number of output "
           "files.\n",
           test_cntr_);
  }

  // Delete the file pointers.
  delete in_file_stereo_;
  delete in_file_mono_;
}

// Register Codec to use in the test
//
// Input:   side             - which ACM to use, 'A' or 'B'
//          codec_name       - name to use when register the codec
//          sampling_freq_hz - sampling frequency in Herz
//          rate             - bitrate in bytes
//          pack_size        - packet size in samples
//          channels         - number of channels; 1 for mono, 2 for stereo
//          payload_type     - payload type for the codec
void TestStereo::RegisterSendCodec(char side, char* codec_name,
                                   int32_t sampling_freq_hz, int rate,
                                   int pack_size, int channels,
                                   int payload_type) {
  if (test_mode_ != 0) {
    // Print out codec and settings
    printf("Codec: %s Freq: %d Rate: %d PackSize: %d\n", codec_name,
           sampling_freq_hz, rate, pack_size);
  }

  // Store packet size in samples, used to validate the received packet
  pack_size_samp_ = pack_size;

  // Store the expected packet size in bytes, used to validate the received
  // packet. Add 0.875 to always round up to a whole byte.
  pack_size_bytes_ = (uint16_t)(static_cast<float>(pack_size * rate) /
                                    static_cast<float>(sampling_freq_hz * 8) +
                                0.875);

  // Set pointer to the ACM where to register the codec
  AudioCodingModule* my_acm = NULL;
  switch (side) {
    case 'A': {
      my_acm = acm_a_.get();
      break;
    }
    case 'B': {
      my_acm = acm_b_.get();
      break;
    }
    default:
      break;
  }
  ASSERT_TRUE(my_acm != NULL);

  CodecInst my_codec_param;
  // Get all codec parameters before registering
  EXPECT_GT(AudioCodingModule::Codec(codec_name, &my_codec_param,
                                     sampling_freq_hz, channels), -1);
  my_codec_param.rate = rate;
  my_codec_param.pacsize = pack_size;
  EXPECT_EQ(0, my_acm->RegisterSendCodec(my_codec_param));

  send_codec_name_ = codec_name;
}

void TestStereo::Run(TestPackStereo* channel, int in_channels, int out_channels,
                     int percent_loss) {
  AudioFrame audio_frame;

  int32_t out_freq_hz_b = out_file_.SamplingFrequency();
  uint16_t rec_size;
  uint32_t time_stamp_diff;
  channel->reset_payload_size();
  int error_count = 0;
  int variable_bytes = 0;
  int variable_packets = 0;
  // Set test length to 500 ms (50 blocks of 10 ms each).
  in_file_mono_->SetNum10MsBlocksToRead(50);
  in_file_stereo_->SetNum10MsBlocksToRead(50);
  // Fast-forward 1 second (100 blocks) since the files start with silence.
  in_file_stereo_->FastForward(100);
  in_file_mono_->FastForward(100);

  while (1) {
    // Simulate packet loss by setting |packet_loss_| to "true" in
    // |percent_loss| percent of the loops.
    if (percent_loss > 0) {
      if (counter_ == floor((100 / percent_loss) + 0.5)) {
        counter_ = 0;
        channel->set_lost_packet(true);
      } else {
        channel->set_lost_packet(false);
      }
      counter_++;
    }

    // Add 10 msec to ACM
    if (in_channels == 1) {
      if (in_file_mono_->EndOfFile()) {
        break;
      }
      in_file_mono_->Read10MsData(audio_frame);
    } else {
      if (in_file_stereo_->EndOfFile()) {
        break;
      }
      in_file_stereo_->Read10MsData(audio_frame);
    }
    EXPECT_GE(acm_a_->Add10MsData(audio_frame), 0);

    // Verify that the received packet size matches the settings.
    rec_size = channel->payload_size();
    if ((0 < rec_size) & (rec_size < 65535)) {
      if (strcmp(send_codec_name_, "opus") == 0) {
        // Opus is a variable rate codec, hence calculate the average packet
        // size, and later make sure the average is in the right range.
        variable_bytes += rec_size;
        variable_packets++;
      } else {
        // For fixed rate codecs, check that packet size is correct.
        if ((rec_size != pack_size_bytes_ * out_channels)
            && (pack_size_bytes_ < 65535)) {
          error_count++;
        }
      }
      // Verify that the timestamp is updated with expected length
      time_stamp_diff = channel->timestamp_diff();
      if ((counter_ > 10) && (time_stamp_diff != pack_size_samp_)) {
        error_count++;
      }
    }

    // Run received side of ACM
    bool muted;
    EXPECT_EQ(0, acm_b_->PlayoutData10Ms(out_freq_hz_b, &audio_frame, &muted));
    ASSERT_FALSE(muted);

    // Write output speech to file
    out_file_.Write10MsData(
        audio_frame.data_,
        audio_frame.samples_per_channel_ * audio_frame.num_channels_);
  }

  EXPECT_EQ(0, error_count);

  // Check that packet size is in the right range for variable rate codecs,
  // such as Opus.
  if (variable_packets > 0) {
    variable_bytes /= variable_packets;
    EXPECT_NEAR(variable_bytes, pack_size_bytes_, 18);
  }

  if (in_file_mono_->EndOfFile()) {
    in_file_mono_->Rewind();
  }
  if (in_file_stereo_->EndOfFile()) {
    in_file_stereo_->Rewind();
  }
  // Reset in case we ended with a lost packet
  channel->set_lost_packet(false);
}

void TestStereo::OpenOutFile(int16_t test_number) {
  std::string file_name;
  std::stringstream file_stream;
  file_stream << webrtc::test::OutputPath() << "teststereo_out_" << test_number
      << ".pcm";
  file_name = file_stream.str();
  out_file_.Open(file_name, 32000, "wb");
}

void TestStereo::DisplaySendReceiveCodec() {
  auto send_codec = acm_a_->SendCodec();
  if (test_mode_ != 0) {
    ASSERT_TRUE(send_codec);
    printf("%s -> ", send_codec->plname);
  }
  CodecInst receive_codec;
  acm_b_->ReceiveCodec(&receive_codec);
  if (test_mode_ != 0) {
    printf("%s\n", receive_codec.plname);
  }
}

}  // namespace webrtc
