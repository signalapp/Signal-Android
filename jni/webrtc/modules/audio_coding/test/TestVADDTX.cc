/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/TestVADDTX.h"

#include <string>

#include "webrtc/engine_configurations.h"
#include "webrtc/modules/audio_coding/test/PCMFile.h"
#include "webrtc/modules/audio_coding/test/utility.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

#ifdef WEBRTC_CODEC_ISAC
const CodecInst kIsacWb = {103, "ISAC", 16000, 480, 1, 32000};
const CodecInst kIsacSwb = {104, "ISAC", 32000, 960, 1, 56000};
#endif

#ifdef WEBRTC_CODEC_ILBC
const CodecInst kIlbc = {102, "ILBC", 8000, 240, 1, 13300};
#endif

#ifdef WEBRTC_CODEC_OPUS
const CodecInst kOpus = {120, "opus", 48000, 960, 1, 64000};
const CodecInst kOpusStereo = {120, "opus", 48000, 960, 2, 64000};
#endif

ActivityMonitor::ActivityMonitor() {
  ResetStatistics();
}

int32_t ActivityMonitor::InFrameType(FrameType frame_type) {
  counter_[frame_type]++;
  return 0;
}

void ActivityMonitor::PrintStatistics() {
  printf("\n");
  printf("kEmptyFrame       %u\n", counter_[kEmptyFrame]);
  printf("kAudioFrameSpeech %u\n", counter_[kAudioFrameSpeech]);
  printf("kAudioFrameCN     %u\n", counter_[kAudioFrameCN]);
  printf("kVideoFrameKey    %u\n", counter_[kVideoFrameKey]);
  printf("kVideoFrameDelta  %u\n", counter_[kVideoFrameDelta]);
  printf("\n\n");
}

void ActivityMonitor::ResetStatistics() {
  memset(counter_, 0, sizeof(counter_));
}

void ActivityMonitor::GetStatistics(uint32_t* counter) {
  memcpy(counter, counter_, sizeof(counter_));
}

TestVadDtx::TestVadDtx()
    : acm_send_(AudioCodingModule::Create(0)),
      acm_receive_(AudioCodingModule::Create(1)),
      channel_(new Channel),
      monitor_(new ActivityMonitor) {
  EXPECT_EQ(0, acm_send_->RegisterTransportCallback(channel_.get()));
  channel_->RegisterReceiverACM(acm_receive_.get());
  EXPECT_EQ(0, acm_send_->RegisterVADCallback(monitor_.get()));
}

void TestVadDtx::RegisterCodec(CodecInst codec_param) {
  // Set the codec for sending and receiving.
  EXPECT_EQ(0, acm_send_->RegisterSendCodec(codec_param));
  EXPECT_EQ(0, acm_receive_->RegisterReceiveCodec(codec_param));
  channel_->SetIsStereo(codec_param.channels > 1);
}

// Encoding a file and see if the numbers that various packets occur follow
// the expectation.
void TestVadDtx::Run(std::string in_filename, int frequency, int channels,
                     std::string out_filename, bool append,
                     const int* expects) {
  monitor_->ResetStatistics();

  PCMFile in_file;
  in_file.Open(in_filename, frequency, "rb");
  in_file.ReadStereo(channels > 1);
  // Set test length to 1000 ms (100 blocks of 10 ms each).
  in_file.SetNum10MsBlocksToRead(100);
  // Fast-forward both files 500 ms (50 blocks). The first second of the file is
  // silence, but we want to keep half of that to test silence periods.
  in_file.FastForward(50);

  PCMFile out_file;
  if (append) {
    out_file.Open(out_filename, kOutputFreqHz, "ab");
  } else {
    out_file.Open(out_filename, kOutputFreqHz, "wb");
  }

  uint16_t frame_size_samples = in_file.PayloadLength10Ms();
  uint32_t time_stamp = 0x12345678;
  AudioFrame audio_frame;
  while (!in_file.EndOfFile()) {
    in_file.Read10MsData(audio_frame);
    audio_frame.timestamp_ = time_stamp;
    time_stamp += frame_size_samples;
    EXPECT_GE(acm_send_->Add10MsData(audio_frame), 0);
    bool muted;
    acm_receive_->PlayoutData10Ms(kOutputFreqHz, &audio_frame, &muted);
    ASSERT_FALSE(muted);
    out_file.Write10MsData(audio_frame);
  }

  in_file.Close();
  out_file.Close();

#ifdef PRINT_STAT
  monitor_->PrintStatistics();
#endif

  uint32_t stats[5];
  monitor_->GetStatistics(stats);
  monitor_->ResetStatistics();

  for (const auto& st : stats) {
    int i = &st - stats;  // Calculate the current position in stats.
    switch (expects[i]) {
      case 0: {
        EXPECT_EQ(0u, st) << "stats[" << i << "] error.";
        break;
      }
      case 1: {
        EXPECT_GT(st, 0u) << "stats[" << i << "] error.";
        break;
      }
    }
  }
}

// Following is the implementation of TestWebRtcVadDtx.
TestWebRtcVadDtx::TestWebRtcVadDtx()
    : vad_enabled_(false),
      dtx_enabled_(false),
      output_file_num_(0) {
}

void TestWebRtcVadDtx::Perform() {
  // Go through various test cases.
#ifdef WEBRTC_CODEC_ISAC
  // Register iSAC WB as send codec
  RegisterCodec(kIsacWb);
  RunTestCases();

  // Register iSAC SWB as send codec
  RegisterCodec(kIsacSwb);
  RunTestCases();
#endif

#ifdef WEBRTC_CODEC_ILBC
  // Register iLBC as send codec
  RegisterCodec(kIlbc);
  RunTestCases();
#endif

#ifdef WEBRTC_CODEC_OPUS
  // Register Opus as send codec
  RegisterCodec(kOpus);
  RunTestCases();
#endif
}

// Test various configurations on VAD/DTX.
void TestWebRtcVadDtx::RunTestCases() {
  // #1 DTX = OFF, VAD = OFF, VADNormal
  SetVAD(false, false, VADNormal);
  Test(true);

  // #2 DTX = ON, VAD = ON, VADAggr
  SetVAD(true, true, VADAggr);
  Test(false);

  // #3 DTX = ON, VAD = ON, VADLowBitrate
  SetVAD(true, true, VADLowBitrate);
  Test(false);

  // #4 DTX = ON, VAD = ON, VADVeryAggr
  SetVAD(true, true, VADVeryAggr);
  Test(false);

  // #5 DTX = ON, VAD = ON, VADNormal
  SetVAD(true, true, VADNormal);
  Test(false);
}

// Set the expectation and run the test.
void TestWebRtcVadDtx::Test(bool new_outfile) {
  int expects[] = {-1, 1, dtx_enabled_, 0, 0};
  if (new_outfile) {
    output_file_num_++;
  }
  std::stringstream out_filename;
  out_filename << webrtc::test::OutputPath()
               << "testWebRtcVadDtx_outFile_"
               << output_file_num_
               << ".pcm";
  Run(webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm"),
      32000, 1, out_filename.str(), !new_outfile, expects);
}

void TestWebRtcVadDtx::SetVAD(bool enable_dtx, bool enable_vad,
                              ACMVADMode vad_mode) {
  ACMVADMode mode;
  EXPECT_EQ(0, acm_send_->SetVAD(enable_dtx, enable_vad, vad_mode));
  EXPECT_EQ(0, acm_send_->VAD(&dtx_enabled_, &vad_enabled_, &mode));

  auto codec_param = acm_send_->SendCodec();
  ASSERT_TRUE(codec_param);
  if (STR_CASE_CMP(codec_param->plname, "opus") == 0) {
    // If send codec is Opus, WebRTC VAD/DTX cannot be used.
    enable_dtx = enable_vad = false;
  }

  EXPECT_EQ(dtx_enabled_ , enable_dtx); // DTX should be set as expected.

  if (dtx_enabled_) {
    EXPECT_TRUE(vad_enabled_); // WebRTC DTX cannot run without WebRTC VAD.
  } else {
    // Using no DTX should not affect setting of VAD.
    EXPECT_EQ(enable_vad, vad_enabled_);
  }
}

// Following is the implementation of TestOpusDtx.
void TestOpusDtx::Perform() {
#ifdef WEBRTC_CODEC_ISAC
  // If we set other codec than Opus, DTX cannot be switched on.
  RegisterCodec(kIsacWb);
  EXPECT_EQ(-1, acm_send_->EnableOpusDtx());
  EXPECT_EQ(0, acm_send_->DisableOpusDtx());
#endif

#ifdef WEBRTC_CODEC_OPUS
  int expects[] = {0, 1, 0, 0, 0};

  // Register Opus as send codec
  std::string out_filename = webrtc::test::OutputPath() +
      "testOpusDtx_outFile_mono.pcm";
  RegisterCodec(kOpus);
  EXPECT_EQ(0, acm_send_->DisableOpusDtx());

  Run(webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm"),
      32000, 1, out_filename, false, expects);

  EXPECT_EQ(0, acm_send_->EnableOpusDtx());
  expects[kEmptyFrame] = 1;
  Run(webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm"),
      32000, 1, out_filename, true, expects);

  // Register stereo Opus as send codec
  out_filename = webrtc::test::OutputPath() + "testOpusDtx_outFile_stereo.pcm";
  RegisterCodec(kOpusStereo);
  EXPECT_EQ(0, acm_send_->DisableOpusDtx());
  expects[kEmptyFrame] = 0;
  Run(webrtc::test::ResourcePath("audio_coding/teststereo32kHz", "pcm"),
      32000, 2, out_filename, false, expects);

  EXPECT_EQ(0, acm_send_->EnableOpusDtx());

  expects[kEmptyFrame] = 1;
  Run(webrtc::test::ResourcePath("audio_coding/teststereo32kHz", "pcm"),
      32000, 2, out_filename, true, expects);
#endif
}

}  // namespace webrtc
