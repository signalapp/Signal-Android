/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/iSACTest.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

#if _WIN32
#include <windows.h>
#elif WEBRTC_LINUX
#include <time.h>
#else
#include <sys/time.h>
#include <time.h>
#endif

#include "webrtc/modules/audio_coding/acm2/acm_common_defs.h"
#include "webrtc/modules/audio_coding/test/utility.h"
#include "webrtc/system_wrappers/include/event_wrapper.h"
#include "webrtc/system_wrappers/include/trace.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

void SetISACConfigDefault(ACMTestISACConfig& isacConfig) {
  isacConfig.currentRateBitPerSec = 0;
  isacConfig.currentFrameSizeMsec = 0;
  isacConfig.encodingMode = -1;
  isacConfig.initRateBitPerSec = 0;
  isacConfig.initFrameSizeInMsec = 0;
  isacConfig.enforceFrameSize = false;
  return;
}

int16_t SetISAConfig(ACMTestISACConfig& isacConfig, AudioCodingModule* acm,
                     int testMode) {

  if ((isacConfig.currentRateBitPerSec != 0)
      || (isacConfig.currentFrameSizeMsec != 0)) {
    auto sendCodec = acm->SendCodec();
    EXPECT_TRUE(sendCodec);
    if (isacConfig.currentRateBitPerSec < 0) {
      // Register iSAC in adaptive (channel-dependent) mode.
      sendCodec->rate = -1;
      EXPECT_EQ(0, acm->RegisterSendCodec(*sendCodec));
    } else {
      if (isacConfig.currentRateBitPerSec != 0) {
        sendCodec->rate = isacConfig.currentRateBitPerSec;
      }
      if (isacConfig.currentFrameSizeMsec != 0) {
        sendCodec->pacsize = isacConfig.currentFrameSizeMsec
            * (sendCodec->plfreq / 1000);
      }
      EXPECT_EQ(0, acm->RegisterSendCodec(*sendCodec));
    }
  }

  return 0;
}

ISACTest::ISACTest(int testMode)
    : _acmA(AudioCodingModule::Create(1)),
      _acmB(AudioCodingModule::Create(2)),
      _testMode(testMode) {}

ISACTest::~ISACTest() {}

void ISACTest::Setup() {
  int codecCntr;
  CodecInst codecParam;

  for (codecCntr = 0; codecCntr < AudioCodingModule::NumberOfCodecs();
      codecCntr++) {
    EXPECT_EQ(0, AudioCodingModule::Codec(codecCntr, &codecParam));
    if (!STR_CASE_CMP(codecParam.plname, "ISAC")
        && codecParam.plfreq == 16000) {
      memcpy(&_paramISAC16kHz, &codecParam, sizeof(CodecInst));
      _idISAC16kHz = codecCntr;
    }
    if (!STR_CASE_CMP(codecParam.plname, "ISAC")
        && codecParam.plfreq == 32000) {
      memcpy(&_paramISAC32kHz, &codecParam, sizeof(CodecInst));
      _idISAC32kHz = codecCntr;
    }
  }

  // Register both iSAC-wb & iSAC-swb in both sides as receiver codecs.
  EXPECT_EQ(0, _acmA->RegisterReceiveCodec(_paramISAC16kHz));
  EXPECT_EQ(0, _acmA->RegisterReceiveCodec(_paramISAC32kHz));
  EXPECT_EQ(0, _acmB->RegisterReceiveCodec(_paramISAC16kHz));
  EXPECT_EQ(0, _acmB->RegisterReceiveCodec(_paramISAC32kHz));

  //--- Set A-to-B channel
  _channel_A2B.reset(new Channel);
  EXPECT_EQ(0, _acmA->RegisterTransportCallback(_channel_A2B.get()));
  _channel_A2B->RegisterReceiverACM(_acmB.get());

  //--- Set B-to-A channel
  _channel_B2A.reset(new Channel);
  EXPECT_EQ(0, _acmB->RegisterTransportCallback(_channel_B2A.get()));
  _channel_B2A->RegisterReceiverACM(_acmA.get());

  file_name_swb_ = webrtc::test::ResourcePath("audio_coding/testfile32kHz",
                                              "pcm");

  EXPECT_EQ(0, _acmB->RegisterSendCodec(_paramISAC16kHz));
  EXPECT_EQ(0, _acmA->RegisterSendCodec(_paramISAC32kHz));

  _inFileA.Open(file_name_swb_, 32000, "rb");
  // Set test length to 500 ms (50 blocks of 10 ms each).
  _inFileA.SetNum10MsBlocksToRead(50);
  // Fast-forward 1 second (100 blocks) since the files start with silence.
  _inFileA.FastForward(100);
  std::string fileNameA = webrtc::test::OutputPath() + "testisac_a.pcm";
  std::string fileNameB = webrtc::test::OutputPath() + "testisac_b.pcm";
  _outFileA.Open(fileNameA, 32000, "wb");
  _outFileB.Open(fileNameB, 32000, "wb");

  while (!_inFileA.EndOfFile()) {
    Run10ms();
  }
  CodecInst receiveCodec;
  EXPECT_EQ(0, _acmA->ReceiveCodec(&receiveCodec));
  EXPECT_EQ(0, _acmB->ReceiveCodec(&receiveCodec));

  _inFileA.Close();
  _outFileA.Close();
  _outFileB.Close();
}

void ISACTest::Perform() {
  Setup();

  int16_t testNr = 0;
  ACMTestISACConfig wbISACConfig;
  ACMTestISACConfig swbISACConfig;

  SetISACConfigDefault(wbISACConfig);
  SetISACConfigDefault(swbISACConfig);

  wbISACConfig.currentRateBitPerSec = -1;
  swbISACConfig.currentRateBitPerSec = -1;
  testNr++;
  EncodeDecode(testNr, wbISACConfig, swbISACConfig);

  if (_testMode != 0) {
    SetISACConfigDefault(wbISACConfig);
    SetISACConfigDefault(swbISACConfig);

    wbISACConfig.currentRateBitPerSec = -1;
    swbISACConfig.currentRateBitPerSec = -1;
    wbISACConfig.initRateBitPerSec = 13000;
    wbISACConfig.initFrameSizeInMsec = 60;
    swbISACConfig.initRateBitPerSec = 20000;
    swbISACConfig.initFrameSizeInMsec = 30;
    testNr++;
    EncodeDecode(testNr, wbISACConfig, swbISACConfig);

    SetISACConfigDefault(wbISACConfig);
    SetISACConfigDefault(swbISACConfig);

    wbISACConfig.currentRateBitPerSec = 20000;
    swbISACConfig.currentRateBitPerSec = 48000;
    testNr++;
    EncodeDecode(testNr, wbISACConfig, swbISACConfig);

    wbISACConfig.currentRateBitPerSec = 16000;
    swbISACConfig.currentRateBitPerSec = 30000;
    wbISACConfig.currentFrameSizeMsec = 60;
    testNr++;
    EncodeDecode(testNr, wbISACConfig, swbISACConfig);
  }

  SetISACConfigDefault(wbISACConfig);
  SetISACConfigDefault(swbISACConfig);
  testNr++;
  EncodeDecode(testNr, wbISACConfig, swbISACConfig);

  testNr++;
  if (_testMode == 0) {
    SwitchingSamplingRate(testNr, 4);
  } else {
    SwitchingSamplingRate(testNr, 80);
  }
}

void ISACTest::Run10ms() {
  AudioFrame audioFrame;
  EXPECT_GT(_inFileA.Read10MsData(audioFrame), 0);
  EXPECT_GE(_acmA->Add10MsData(audioFrame), 0);
  EXPECT_GE(_acmB->Add10MsData(audioFrame), 0);
  bool muted;
  EXPECT_EQ(0, _acmA->PlayoutData10Ms(32000, &audioFrame, &muted));
  ASSERT_FALSE(muted);
  _outFileA.Write10MsData(audioFrame);
  EXPECT_EQ(0, _acmB->PlayoutData10Ms(32000, &audioFrame, &muted));
  ASSERT_FALSE(muted);
  _outFileB.Write10MsData(audioFrame);
}

void ISACTest::EncodeDecode(int testNr, ACMTestISACConfig& wbISACConfig,
                            ACMTestISACConfig& swbISACConfig) {
  // Files in Side A and B
  _inFileA.Open(file_name_swb_, 32000, "rb", true);
  _inFileB.Open(file_name_swb_, 32000, "rb", true);

  std::string file_name_out;
  std::stringstream file_stream_a;
  std::stringstream file_stream_b;
  file_stream_a << webrtc::test::OutputPath();
  file_stream_b << webrtc::test::OutputPath();
  file_stream_a << "out_iSACTest_A_" << testNr << ".pcm";
  file_stream_b << "out_iSACTest_B_" << testNr << ".pcm";
  file_name_out = file_stream_a.str();
  _outFileA.Open(file_name_out, 32000, "wb");
  file_name_out = file_stream_b.str();
  _outFileB.Open(file_name_out, 32000, "wb");

  EXPECT_EQ(0, _acmA->RegisterSendCodec(_paramISAC16kHz));
  EXPECT_EQ(0, _acmA->RegisterSendCodec(_paramISAC32kHz));
  EXPECT_EQ(0, _acmB->RegisterSendCodec(_paramISAC32kHz));
  EXPECT_EQ(0, _acmB->RegisterSendCodec(_paramISAC16kHz));

  // Side A is sending super-wideband, and side B is sending wideband.
  SetISAConfig(swbISACConfig, _acmA.get(), _testMode);
  SetISAConfig(wbISACConfig, _acmB.get(), _testMode);

  bool adaptiveMode = false;
  if ((swbISACConfig.currentRateBitPerSec == -1)
      || (wbISACConfig.currentRateBitPerSec == -1)) {
    adaptiveMode = true;
  }
  _myTimer.Reset();
  _channel_A2B->ResetStats();
  _channel_B2A->ResetStats();

  char currentTime[500];
  EventTimerWrapper* myEvent = EventTimerWrapper::Create();
  EXPECT_TRUE(myEvent->StartTimer(true, 10));
  while (!(_inFileA.EndOfFile() || _inFileA.Rewinded())) {
    Run10ms();
    _myTimer.Tick10ms();
    _myTimer.CurrentTimeHMS(currentTime);

    if ((adaptiveMode) && (_testMode != 0)) {
      myEvent->Wait(5000);
      EXPECT_TRUE(_acmA->SendCodec());
      EXPECT_TRUE(_acmB->SendCodec());
    }
  }

  if (_testMode != 0) {
    printf("\n\nSide A statistics\n\n");
    _channel_A2B->PrintStats(_paramISAC32kHz);

    printf("\n\nSide B statistics\n\n");
    _channel_B2A->PrintStats(_paramISAC16kHz);
  }

  _channel_A2B->ResetStats();
  _channel_B2A->ResetStats();

  _outFileA.Close();
  _outFileB.Close();
  _inFileA.Close();
  _inFileB.Close();
}

void ISACTest::SwitchingSamplingRate(int testNr, int maxSampRateChange) {
  // Files in Side A
  _inFileA.Open(file_name_swb_, 32000, "rb");
  _inFileB.Open(file_name_swb_, 32000, "rb");

  std::string file_name_out;
  std::stringstream file_stream_a;
  std::stringstream file_stream_b;
  file_stream_a << webrtc::test::OutputPath();
  file_stream_b << webrtc::test::OutputPath();
  file_stream_a << "out_iSACTest_A_" << testNr << ".pcm";
  file_stream_b << "out_iSACTest_B_" << testNr << ".pcm";
  file_name_out = file_stream_a.str();
  _outFileA.Open(file_name_out, 32000, "wb");
  file_name_out = file_stream_b.str();
  _outFileB.Open(file_name_out, 32000, "wb");

  // Start with side A sending super-wideband and side B seding wideband.
  // Toggle sending wideband/super-wideband in this test.
  EXPECT_EQ(0, _acmA->RegisterSendCodec(_paramISAC32kHz));
  EXPECT_EQ(0, _acmB->RegisterSendCodec(_paramISAC16kHz));

  int numSendCodecChanged = 0;
  _myTimer.Reset();
  char currentTime[50];
  while (numSendCodecChanged < (maxSampRateChange << 1)) {
    Run10ms();
    _myTimer.Tick10ms();
    _myTimer.CurrentTimeHMS(currentTime);
    if (_testMode == 2)
      printf("\r%s", currentTime);
    if (_inFileA.EndOfFile()) {
      if (_inFileA.SamplingFrequency() == 16000) {
        // Switch side A to send super-wideband.
        _inFileA.Close();
        _inFileA.Open(file_name_swb_, 32000, "rb");
        EXPECT_EQ(0, _acmA->RegisterSendCodec(_paramISAC32kHz));
      } else {
        // Switch side A to send wideband.
        _inFileA.Close();
        _inFileA.Open(file_name_swb_, 32000, "rb");
        EXPECT_EQ(0, _acmA->RegisterSendCodec(_paramISAC16kHz));
      }
      numSendCodecChanged++;
    }

    if (_inFileB.EndOfFile()) {
      if (_inFileB.SamplingFrequency() == 16000) {
        // Switch side B to send super-wideband.
        _inFileB.Close();
        _inFileB.Open(file_name_swb_, 32000, "rb");
        EXPECT_EQ(0, _acmB->RegisterSendCodec(_paramISAC32kHz));
      } else {
        // Switch side B to send wideband.
        _inFileB.Close();
        _inFileB.Open(file_name_swb_, 32000, "rb");
        EXPECT_EQ(0, _acmB->RegisterSendCodec(_paramISAC16kHz));
      }
      numSendCodecChanged++;
    }
  }
  _outFileA.Close();
  _outFileB.Close();
  _inFileA.Close();
  _inFileB.Close();
}

}  // namespace webrtc
