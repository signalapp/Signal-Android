/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/TestRedFec.h"

#include <assert.h>

#include "webrtc/common.h"
#include "webrtc/common_types.h"
#include "webrtc/engine_configurations.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "webrtc/modules/audio_coding/test/utility.h"
#include "webrtc/system_wrappers/include/trace.h"
#include "webrtc/test/testsupport/fileutils.h"

#ifdef SUPPORT_RED_WB
#undef SUPPORT_RED_WB
#endif

#ifdef SUPPORT_RED_SWB
#undef SUPPORT_RED_SWB
#endif

#ifdef SUPPORT_RED_FB
#undef SUPPORT_RED_FB
#endif

namespace webrtc {

namespace {
  const char kNameL16[] = "L16";
  const char kNamePCMU[] = "PCMU";
  const char kNameCN[] = "CN";
  const char kNameRED[] = "RED";

  // These three are only used by code #ifdeffed on WEBRTC_CODEC_G722.
#ifdef WEBRTC_CODEC_G722
  const char kNameISAC[] = "ISAC";
  const char kNameG722[] = "G722";
  const char kNameOPUS[] = "opus";
#endif
}

TestRedFec::TestRedFec()
    : _acmA(AudioCodingModule::Create(0)),
      _acmB(AudioCodingModule::Create(1)),
      _channelA2B(NULL),
      _testCntr(0) {
}

TestRedFec::~TestRedFec() {
  if (_channelA2B != NULL) {
    delete _channelA2B;
    _channelA2B = NULL;
  }
}

void TestRedFec::Perform() {
  const std::string file_name = webrtc::test::ResourcePath(
      "audio_coding/testfile32kHz", "pcm");
  _inFileA.Open(file_name, 32000, "rb");

  ASSERT_EQ(0, _acmA->InitializeReceiver());
  ASSERT_EQ(0, _acmB->InitializeReceiver());

  uint8_t numEncoders = _acmA->NumberOfCodecs();
  CodecInst myCodecParam;
  for (uint8_t n = 0; n < numEncoders; n++) {
    EXPECT_EQ(0, _acmB->Codec(n, &myCodecParam));
    // Default number of channels is 2 for opus, so we change to 1 in this test.
    if (!strcmp(myCodecParam.plname, "opus")) {
      myCodecParam.channels = 1;
    }
    EXPECT_EQ(0, _acmB->RegisterReceiveCodec(myCodecParam));
  }

  // Create and connect the channel
  _channelA2B = new Channel;
  _acmA->RegisterTransportCallback(_channelA2B);
  _channelA2B->RegisterReceiverACM(_acmB.get());

  EXPECT_EQ(0, RegisterSendCodec('A', kNameL16, 8000));
  EXPECT_EQ(0, RegisterSendCodec('A', kNameCN, 8000));
  EXPECT_EQ(0, RegisterSendCodec('A', kNameRED));
  EXPECT_EQ(0, SetVAD(true, true, VADAggr));
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());

  OpenOutFile(_testCntr);
  Run();
  _outFileB.Close();

  RegisterSendCodec('A', kNamePCMU, 8000);
  // Switch to another 8 kHz codec, RED should remain switched on.
  EXPECT_TRUE(_acmA->REDStatus());
  OpenOutFile(_testCntr);
  Run();
  _outFileB.Close();

#ifndef WEBRTC_CODEC_G722
  EXPECT_TRUE(false);
  printf("G722 needs to be activated to run this test\n");
  return;
#else
  EXPECT_EQ(0, RegisterSendCodec('A', kNameG722, 16000));
  EXPECT_EQ(0, RegisterSendCodec('A', kNameCN, 16000));

#ifdef SUPPORT_RED_WB
  // Switch codec, RED should remain.
  EXPECT_TRUE(_acmA->REDStatus());
#else
  // Switch to a 16 kHz codec, RED should have been switched off.
  EXPECT_FALSE(_acmA->REDStatus());
#endif

  OpenOutFile(_testCntr);
  EXPECT_EQ(0, SetVAD(true, true, VADAggr));
  EXPECT_EQ(0, _acmA->SetREDStatus(false));
  EXPECT_FALSE(_acmA->REDStatus());
  Run();
#ifdef SUPPORT_RED_WB
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
#endif
  Run();
  _outFileB.Close();

  RegisterSendCodec('A', kNameISAC, 16000);

#ifdef SUPPORT_RED_WB
  // Switch codec, RED should remain.
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_FALSE(_acmA->REDStatus());
#endif

  OpenOutFile(_testCntr);
  EXPECT_EQ(0, SetVAD(true, true, VADVeryAggr));
  EXPECT_EQ(0, _acmA->SetREDStatus(false));
  EXPECT_FALSE(_acmA->REDStatus());
  Run();
  _outFileB.Close();

#ifdef SUPPORT_RED_WB
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
#endif
  OpenOutFile(_testCntr);
  Run();
  _outFileB.Close();

  RegisterSendCodec('A', kNameISAC, 32000);

#if defined(SUPPORT_RED_SWB) && defined(SUPPORT_RED_WB)
  // Switch codec, RED should remain.
  EXPECT_TRUE(_acmA->REDStatus());
#else
  // Switch to a 32 kHz codec, RED should have been switched off.
  EXPECT_FALSE(_acmA->REDStatus());
#endif

  OpenOutFile(_testCntr);
  EXPECT_EQ(0, SetVAD(true, true, VADVeryAggr));
  EXPECT_EQ(0, _acmA->SetREDStatus(false));
  EXPECT_FALSE(_acmA->REDStatus());
  Run();
  _outFileB.Close();

#ifdef SUPPORT_RED_SWB
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
#endif
  OpenOutFile(_testCntr);
  Run();
  _outFileB.Close();

  RegisterSendCodec('A', kNameISAC, 32000);
  EXPECT_EQ(0, SetVAD(false, false, VADNormal));

#if defined(SUPPORT_RED_SWB) && defined(SUPPORT_RED_WB)
  OpenOutFile(_testCntr);
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
  Run();

  RegisterSendCodec('A', kNameISAC, 16000);
  EXPECT_TRUE(_acmA->REDStatus());
  Run();

  RegisterSendCodec('A', kNameISAC, 32000);
  EXPECT_TRUE(_acmA->REDStatus());
  Run();

  RegisterSendCodec('A', kNameISAC, 16000);
  EXPECT_TRUE(_acmA->REDStatus());
  Run();
  _outFileB.Close();
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
#endif

  _channelA2B->SetFECTestWithPacketLoss(true);
  // Following tests are under packet losses.

  EXPECT_EQ(0, RegisterSendCodec('A', kNameG722));
  EXPECT_EQ(0, RegisterSendCodec('A', kNameCN, 16000));

#if defined(SUPPORT_RED_WB) && defined(SUPPORT_RED_SWB)
  // Switch codec, RED should remain.
  EXPECT_TRUE(_acmA->REDStatus());
#else
  // Switch to a 16 kHz codec, RED should have been switched off.
  EXPECT_FALSE(_acmA->REDStatus());
#endif

  OpenOutFile(_testCntr);
  EXPECT_EQ(0, SetVAD(true, true, VADAggr));
  EXPECT_EQ(0, _acmA->SetREDStatus(false));
  EXPECT_FALSE(_acmA->REDStatus());
  Run();
  _outFileB.Close();

#ifdef SUPPORT_RED_WB
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
#endif
  OpenOutFile(_testCntr);
  Run();
  _outFileB.Close();

  RegisterSendCodec('A', kNameISAC, 16000);

#ifdef SUPPORT_RED_WB
  // Switch codec, RED should remain.
  EXPECT_TRUE(_acmA->REDStatus());
#else
  // Switch to a 16 kHz codec, RED should have been switched off.
  EXPECT_FALSE(_acmA->REDStatus());
#endif

  OpenOutFile(_testCntr);
  EXPECT_EQ(0, SetVAD(true, true, VADVeryAggr));
  EXPECT_EQ(0, _acmA->SetREDStatus(false));
  EXPECT_FALSE(_acmA->REDStatus());
  Run();
  _outFileB.Close();
#ifdef SUPPORT_RED_WB
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
#endif
  OpenOutFile(_testCntr);
  Run();
  _outFileB.Close();

  RegisterSendCodec('A', kNameISAC, 32000);

#if defined(SUPPORT_RED_SWB) && defined(SUPPORT_RED_WB)
  // Switch codec, RED should remain.
  EXPECT_TRUE(_acmA->REDStatus());
#else
  // Switch to a 32 kHz codec, RED should have been switched off.
  EXPECT_FALSE(_acmA->REDStatus());
#endif

  OpenOutFile(_testCntr);
  EXPECT_EQ(0, SetVAD(true, true, VADVeryAggr));
  EXPECT_EQ(0, _acmA->SetREDStatus(false));
  EXPECT_FALSE(_acmA->REDStatus());
#ifdef SUPPORT_RED_SWB
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
#endif
  OpenOutFile(_testCntr);
  Run();
  _outFileB.Close();

  RegisterSendCodec('A', kNameISAC, 32000);
  EXPECT_EQ(0, SetVAD(false, false, VADNormal));
#if defined(SUPPORT_RED_SWB) && defined(SUPPORT_RED_WB)
  OpenOutFile(_testCntr);
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
  Run();

  RegisterSendCodec('A', kNameISAC, 16000);
  EXPECT_TRUE(_acmA->REDStatus());
  Run();

  RegisterSendCodec('A', kNameISAC, 32000);
  EXPECT_TRUE(_acmA->REDStatus());
  Run();

  RegisterSendCodec('A', kNameISAC, 16000);
  EXPECT_TRUE(_acmA->REDStatus());
  Run();
  _outFileB.Close();
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
#endif

#ifndef WEBRTC_CODEC_OPUS
  EXPECT_TRUE(false);
  printf("Opus needs to be activated to run this test\n");
  return;
#endif

  RegisterSendCodec('A', kNameOPUS, 48000);

#if defined(SUPPORT_RED_FB) && defined(SUPPORT_RED_SWB) &&\
  defined(SUPPORT_RED_WB)
  // Switch to codec, RED should remain switched on.
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_FALSE(_acmA->REDStatus());
#endif

  // _channelA2B imposes 25% packet loss rate.
  EXPECT_EQ(0, _acmA->SetPacketLossRate(25));

#ifdef SUPPORT_RED_FB
  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
  // Codec FEC and RED are mutually exclusive.
  EXPECT_EQ(-1, _acmA->SetCodecFEC(true));

  EXPECT_EQ(0, _acmA->SetREDStatus(false));
  EXPECT_EQ(0, _acmA->SetCodecFEC(true));

  // Codec FEC and RED are mutually exclusive.
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
#else
  EXPECT_EQ(-1, _acmA->SetREDStatus(true));
  EXPECT_FALSE(_acmA->REDStatus());
  EXPECT_EQ(0, _acmA->SetCodecFEC(true));
#endif

  EXPECT_TRUE(_acmA->CodecFEC());
  OpenOutFile(_testCntr);
  Run();

  // Switch to L16 with RED.
  RegisterSendCodec('A', kNameL16, 8000);
  EXPECT_EQ(0, SetVAD(false, false, VADNormal));

  // L16 does not support FEC, so FEC should be turned off automatically.
  EXPECT_FALSE(_acmA->CodecFEC());

  EXPECT_EQ(0, _acmA->SetREDStatus(true));
  EXPECT_TRUE(_acmA->REDStatus());
  Run();

  // Switch to Opus again.
  RegisterSendCodec('A', kNameOPUS, 48000);
#ifdef SUPPORT_RED_FB
  // Switch to codec, RED should remain switched on.
  EXPECT_TRUE(_acmA->REDStatus());
#else
  EXPECT_FALSE(_acmA->REDStatus());
#endif
  EXPECT_EQ(0, _acmA->SetREDStatus(false));
  EXPECT_EQ(0, _acmA->SetCodecFEC(false));
  Run();

  EXPECT_EQ(0, _acmA->SetCodecFEC(true));
  _outFileB.Close();

  // Codecs does not support internal FEC, cannot enable FEC.
  RegisterSendCodec('A', kNameG722, 16000);
  EXPECT_FALSE(_acmA->REDStatus());
  EXPECT_EQ(-1, _acmA->SetCodecFEC(true));
  EXPECT_FALSE(_acmA->CodecFEC());

  RegisterSendCodec('A', kNameISAC, 16000);
  EXPECT_FALSE(_acmA->REDStatus());
  EXPECT_EQ(-1, _acmA->SetCodecFEC(true));
  EXPECT_FALSE(_acmA->CodecFEC());

  // Codecs does not support internal FEC, disable FEC does not trigger failure.
  RegisterSendCodec('A', kNameG722, 16000);
  EXPECT_FALSE(_acmA->REDStatus());
  EXPECT_EQ(0, _acmA->SetCodecFEC(false));
  EXPECT_FALSE(_acmA->CodecFEC());

  RegisterSendCodec('A', kNameISAC, 16000);
  EXPECT_FALSE(_acmA->REDStatus());
  EXPECT_EQ(0, _acmA->SetCodecFEC(false));
  EXPECT_FALSE(_acmA->CodecFEC());

#endif  // defined(WEBRTC_CODEC_G722)
}

int32_t TestRedFec::SetVAD(bool enableDTX, bool enableVAD, ACMVADMode vadMode) {
  return _acmA->SetVAD(enableDTX, enableVAD, vadMode);
}

int16_t TestRedFec::RegisterSendCodec(char side, const char* codecName,
                                      int32_t samplingFreqHz) {
  std::cout << std::flush;
  AudioCodingModule* myACM;
  switch (side) {
    case 'A': {
      myACM = _acmA.get();
      break;
    }
    case 'B': {
      myACM = _acmB.get();
      break;
    }
    default:
      return -1;
  }

  if (myACM == NULL) {
    assert(false);
    return -1;
  }
  CodecInst myCodecParam;
  EXPECT_GT(AudioCodingModule::Codec(codecName, &myCodecParam,
                                     samplingFreqHz, 1), -1);
  EXPECT_GT(myACM->RegisterSendCodec(myCodecParam), -1);

  // Initialization was successful.
  return 0;
}

void TestRedFec::Run() {
  AudioFrame audioFrame;
  int32_t outFreqHzB = _outFileB.SamplingFrequency();
  // Set test length to 500 ms (50 blocks of 10 ms each).
  _inFileA.SetNum10MsBlocksToRead(50);
  // Fast-forward 1 second (100 blocks) since the file starts with silence.
  _inFileA.FastForward(100);

  while (!_inFileA.EndOfFile()) {
    EXPECT_GT(_inFileA.Read10MsData(audioFrame), 0);
    EXPECT_GE(_acmA->Add10MsData(audioFrame), 0);
    bool muted;
    EXPECT_EQ(0, _acmB->PlayoutData10Ms(outFreqHzB, &audioFrame, &muted));
    ASSERT_FALSE(muted);
    _outFileB.Write10MsData(audioFrame.data_, audioFrame.samples_per_channel_);
  }
  _inFileA.Rewind();
}

void TestRedFec::OpenOutFile(int16_t test_number) {
  std::string file_name;
  std::stringstream file_stream;
  file_stream << webrtc::test::OutputPath();
  file_stream << "TestRedFec_outFile_";
  file_stream << test_number << ".pcm";
  file_name = file_stream.str();
  _outFileB.Open(file_name, 16000, "wb");
}

}  // namespace webrtc
