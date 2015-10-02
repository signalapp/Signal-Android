/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/test/TestVADDTX.h"

#include <iostream>

#include "webrtc/common_types.h"
#include "webrtc/engine_configurations.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/modules/audio_coding/main/interface/audio_coding_module_typedefs.h"
#include "webrtc/modules/audio_coding/main/test/utility.h"
#include "webrtc/system_wrappers/interface/trace.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

TestVADDTX::TestVADDTX()
    : _acmA(AudioCodingModule::Create(0)),
      _acmB(AudioCodingModule::Create(1)),
      _channelA2B(NULL) {}

TestVADDTX::~TestVADDTX() {
  if (_channelA2B != NULL) {
    delete _channelA2B;
    _channelA2B = NULL;
  }
}

void TestVADDTX::Perform() {
  const std::string file_name = webrtc::test::ResourcePath(
      "audio_coding/testfile32kHz", "pcm");
  _inFileA.Open(file_name, 32000, "rb");

  EXPECT_EQ(0, _acmA->InitializeReceiver());
  EXPECT_EQ(0, _acmB->InitializeReceiver());

  uint8_t numEncoders = _acmA->NumberOfCodecs();
  CodecInst myCodecParam;
  for (uint8_t n = 0; n < numEncoders; n++) {
    EXPECT_EQ(0, _acmB->Codec(n, &myCodecParam));
    if (!strcmp(myCodecParam.plname, "opus")) {
      // Register Opus as mono.
      myCodecParam.channels = 1;
    }
    EXPECT_EQ(0, _acmB->RegisterReceiveCodec(myCodecParam));
  }

  // Create and connect the channel
  _channelA2B = new Channel;
  _acmA->RegisterTransportCallback(_channelA2B);
  _channelA2B->RegisterReceiverACM(_acmB.get());

  _acmA->RegisterVADCallback(&_monitor);

  int16_t testCntr = 1;

#ifdef WEBRTC_CODEC_ISAC
  // Open outputfile
  OpenOutFile(testCntr++);

  // Register iSAC WB as send codec
  char nameISAC[] = "ISAC";
  RegisterSendCodec('A', nameISAC, 16000);

  // Run the five test cased
  runTestCases();

  // Close file
  _outFileB.Close();

  // Open outputfile
  OpenOutFile(testCntr++);

  // Register iSAC SWB as send codec
  RegisterSendCodec('A', nameISAC, 32000);

  // Run the five test cased
  runTestCases();

  // Close file
  _outFileB.Close();
#endif
#ifdef WEBRTC_CODEC_ILBC
  // Open outputfile
  OpenOutFile(testCntr++);

  // Register iLBC as send codec
  char nameILBC[] = "ilbc";
  RegisterSendCodec('A', nameILBC);

  // Run the five test cased
  runTestCases();

  // Close file
  _outFileB.Close();

#endif
#ifdef WEBRTC_CODEC_OPUS
  // Open outputfile
  OpenOutFile(testCntr++);

  // Register Opus as send codec
  char nameOPUS[] = "opus";
  RegisterSendCodec('A', nameOPUS);

  // Run the five test cased
  runTestCases();

  // Close file
  _outFileB.Close();

#endif
}

void TestVADDTX::runTestCases() {
  // #1 DTX = OFF, VAD = ON, VADNormal
  SetVAD(false, true, VADNormal);
  Run();
  VerifyTest();

  // #2 DTX = OFF, VAD = ON, VADAggr
  SetVAD(false, true, VADAggr);
  Run();
  VerifyTest();

  // #3 DTX = ON, VAD = ON, VADLowBitrate
  SetVAD(true, true, VADLowBitrate);
  Run();
  VerifyTest();

  // #4 DTX = ON, VAD = ON, VADVeryAggr
  SetVAD(true, true, VADVeryAggr);
  Run();
  VerifyTest();

  // #5 DTX = ON, VAD = OFF, VADNormal
  SetVAD(true, false, VADNormal);
  Run();
  VerifyTest();
}

void TestVADDTX::runTestInternalDTX(int expected_result) {
  // #6 DTX = ON, VAD = ON, VADNormal
  SetVAD(true, true, VADNormal);
  EXPECT_EQ(expected_result, _acmA->ReplaceInternalDTXWithWebRtc(true));
  if (expected_result == 0) {
    Run();
    VerifyTest();
  }
}

void TestVADDTX::SetVAD(bool statusDTX, bool statusVAD, int16_t vadMode) {
  bool dtxEnabled, vadEnabled;
  ACMVADMode vadModeSet;

  EXPECT_EQ(0, _acmA->SetVAD(statusDTX, statusVAD, (ACMVADMode) vadMode));
  EXPECT_EQ(0, _acmA->VAD(&dtxEnabled, &vadEnabled, &vadModeSet));

  // Requested VAD/DTX settings
  _setStruct.statusDTX = statusDTX;
  _setStruct.statusVAD = statusVAD;
  _setStruct.vadMode = (ACMVADMode) vadMode;

  // VAD settings after setting VAD in ACM
  _getStruct.statusDTX = dtxEnabled;
  _getStruct.statusVAD = vadEnabled;
  _getStruct.vadMode = vadModeSet;
}

VADDTXstruct TestVADDTX::GetVAD() {
  VADDTXstruct retStruct;
  bool dtxEnabled, vadEnabled;
  ACMVADMode vadModeSet;

  EXPECT_EQ(0, _acmA->VAD(&dtxEnabled, &vadEnabled, &vadModeSet));

  retStruct.statusDTX = dtxEnabled;
  retStruct.statusVAD = vadEnabled;
  retStruct.vadMode = vadModeSet;
  return retStruct;
}

int16_t TestVADDTX::RegisterSendCodec(char side, char* codecName,
                                      int32_t samplingFreqHz,
                                      int32_t rateKbps) {
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
    return -1;
  }

  CodecInst myCodecParam;
  for (int16_t codecCntr = 0; codecCntr < myACM->NumberOfCodecs();
      codecCntr++) {
    EXPECT_EQ(0, myACM->Codec((uint8_t) codecCntr, &myCodecParam));
    if (!STR_CASE_CMP(myCodecParam.plname, codecName)) {
      if ((samplingFreqHz == -1) || (myCodecParam.plfreq == samplingFreqHz)) {
        if ((rateKbps == -1) || (myCodecParam.rate == rateKbps)) {
          break;
        }
      }
    }
  }

  // We only allow VAD/DTX when sending mono.
  myCodecParam.channels = 1;
  EXPECT_EQ(0, myACM->RegisterSendCodec(myCodecParam));

  // initialization was succesful
  return 0;
}

void TestVADDTX::Run() {
  AudioFrame audioFrame;

  uint16_t SamplesIn10MsecA = _inFileA.PayloadLength10Ms();
  uint32_t timestampA = 1;
  int32_t outFreqHzB = _outFileB.SamplingFrequency();

  while (!_inFileA.EndOfFile()) {
    _inFileA.Read10MsData(audioFrame);
    audioFrame.timestamp_ = timestampA;
    timestampA += SamplesIn10MsecA;
    EXPECT_EQ(0, _acmA->Add10MsData(audioFrame));
    EXPECT_GT(_acmA->Process(), -1);
    EXPECT_EQ(0, _acmB->PlayoutData10Ms(outFreqHzB, &audioFrame));
    _outFileB.Write10MsData(audioFrame.data_, audioFrame.samples_per_channel_);
  }
#ifdef PRINT_STAT
  _monitor.PrintStatistics();
#endif
  _inFileA.Rewind();
  _monitor.GetStatistics(_statCounter);
  _monitor.ResetStatistics();
}

void TestVADDTX::OpenOutFile(int16_t test_number) {
  std::string file_name;
  std::stringstream file_stream;
  file_stream << webrtc::test::OutputPath();
  file_stream << "testVADDTX_outFile_";
  file_stream << test_number << ".pcm";
  file_name = file_stream.str();
  _outFileB.Open(file_name, 16000, "wb");
}

int16_t TestVADDTX::VerifyTest() {
  // Verify empty frame result
  uint8_t statusEF = 0;
  uint8_t vadPattern = 0;
  uint8_t emptyFramePattern[6];
  CodecInst myCodecParam;
  _acmA->SendCodec(&myCodecParam);
  bool dtxInUse = true;
  bool isReplaced = false;
  if ((STR_CASE_CMP(myCodecParam.plname, "G729") == 0)
      || (STR_CASE_CMP(myCodecParam.plname, "G723") == 0)
      || (STR_CASE_CMP(myCodecParam.plname, "AMR") == 0)
      || (STR_CASE_CMP(myCodecParam.plname, "AMR-wb") == 0)
      || (STR_CASE_CMP(myCodecParam.plname, "speex") == 0)) {
    _acmA->IsInternalDTXReplacedWithWebRtc(&isReplaced);
    if (!isReplaced) {
      dtxInUse = false;
    }
  } else if (STR_CASE_CMP(myCodecParam.plname, "opus") == 0) {
    if (_getStruct.statusDTX != false) {
      // DTX status doesn't match expected.
      vadPattern |= 4;
    } else if (_getStruct.statusVAD != false) {
      // Mismatch in VAD setting.
      vadPattern |= 2;
    } else {
      _setStruct.statusDTX = false;
      _setStruct.statusVAD = false;
    }
  }

  // Check for error in VAD/DTX settings
  if (_getStruct.statusDTX != _setStruct.statusDTX) {
    // DTX status doesn't match expected
    vadPattern |= 4;
  }
  if (_getStruct.statusDTX) {
    if ((!_getStruct.statusVAD && dtxInUse)
        || (!dtxInUse && (_getStruct.statusVAD != _setStruct.statusVAD))) {
      // Missmatch in VAD setting
      vadPattern |= 2;
    }
  } else {
    if (_getStruct.statusVAD != _setStruct.statusVAD) {
      // VAD status doesn't match expected
      vadPattern |= 2;
    }
  }
  if (_getStruct.vadMode != _setStruct.vadMode) {
    // VAD Mode doesn't match expected
    vadPattern |= 1;
  }

  // Set expected empty frame pattern
  int ii;
  for (ii = 0; ii < 6; ii++) {
    emptyFramePattern[ii] = 0;
  }
  // 0 - "kNoEncoding", not important to check.
  //      Codecs with packetsize != 80 samples will get this output.
  // 1 - "kActiveNormalEncoded", expect to receive some frames with this label .
  // 2 - "kPassiveNormalEncoded".
  // 3 - "kPassiveDTXNB".
  // 4 - "kPassiveDTXWB".
  // 5 - "kPassiveDTXSWB".
  emptyFramePattern[0] = 1;
  emptyFramePattern[1] = 1;
  emptyFramePattern[2] = (((!_getStruct.statusDTX && _getStruct.statusVAD)
      || (!dtxInUse && _getStruct.statusDTX)));
  emptyFramePattern[3] = ((_getStruct.statusDTX && dtxInUse
      && (_acmA->SendFrequency() == 8000)));
  emptyFramePattern[4] = ((_getStruct.statusDTX && dtxInUse
      && (_acmA->SendFrequency() == 16000)));
  emptyFramePattern[5] = ((_getStruct.statusDTX && dtxInUse
      && (_acmA->SendFrequency() == 32000)));

  // Check pattern 1-5 (skip 0)
  for (int ii = 1; ii < 6; ii++) {
    if (emptyFramePattern[ii]) {
      statusEF |= (_statCounter[ii] == 0);
    } else {
      statusEF |= (_statCounter[ii] > 0);
    }
  }
  EXPECT_EQ(0, statusEF);
  EXPECT_EQ(0, vadPattern);

  return 0;
}

ActivityMonitor::ActivityMonitor() {
  _counter[0] = _counter[1] = _counter[2] = _counter[3] = _counter[4] =
      _counter[5] = 0;
}

ActivityMonitor::~ActivityMonitor() {
}

int32_t ActivityMonitor::InFrameType(int16_t frameType) {
  _counter[frameType]++;
  return 0;
}

void ActivityMonitor::PrintStatistics() {
  printf("\n");
  printf("kActiveNormalEncoded  kPassiveNormalEncoded  kPassiveDTXWB  ");
  printf("kPassiveDTXNB kPassiveDTXSWB kFrameEmpty\n");
  printf("%19u", _counter[1]);
  printf("%22u", _counter[2]);
  printf("%14u", _counter[3]);
  printf("%14u", _counter[4]);
  printf("%14u", _counter[5]);
  printf("%11u", _counter[0]);
  printf("\n\n");
}

void ActivityMonitor::ResetStatistics() {
  _counter[0] = _counter[1] = _counter[2] = _counter[3] = _counter[4] =
      _counter[5] = 0;
}

void ActivityMonitor::GetStatistics(uint32_t* getCounter) {
  for (int ii = 0; ii < 6; ii++) {
    getCounter[ii] = _counter[ii];
  }
}

}  // namespace webrtc
