/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "TwoWayCommunication.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

#ifdef WIN32
#include <Windows.h>
#endif

#include "gtest/gtest.h"
#include "webrtc/engine_configurations.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/main/test/PCMFile.h"
#include "webrtc/modules/audio_coding/main/test/utility.h"
#include "webrtc/system_wrappers/interface/trace.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

#define MAX_FILE_NAME_LENGTH_BYTE 500

TwoWayCommunication::TwoWayCommunication(int testMode)
    : _acmA(AudioCodingModule::Create(1)),
      _acmB(AudioCodingModule::Create(2)),
      _acmRefA(AudioCodingModule::Create(3)),
      _acmRefB(AudioCodingModule::Create(4)),
      _testMode(testMode) {}

TwoWayCommunication::~TwoWayCommunication() {
  delete _channel_A2B;
  delete _channel_B2A;
  delete _channelRef_A2B;
  delete _channelRef_B2A;
#ifdef WEBRTC_DTMF_DETECTION
  if (_dtmfDetectorA != NULL) {
    delete _dtmfDetectorA;
  }
  if (_dtmfDetectorB != NULL) {
    delete _dtmfDetectorB;
  }
#endif
  _inFileA.Close();
  _inFileB.Close();
  _outFileA.Close();
  _outFileB.Close();
  _outFileRefA.Close();
  _outFileRefB.Close();
}

void TwoWayCommunication::ChooseCodec(uint8_t* codecID_A,
                                      uint8_t* codecID_B) {
  scoped_ptr<AudioCodingModule> tmpACM(AudioCodingModule::Create(0));
  uint8_t noCodec = tmpACM->NumberOfCodecs();
  CodecInst codecInst;
  printf("List of Supported Codecs\n");
  printf("========================\n");
  for (uint8_t codecCntr = 0; codecCntr < noCodec; codecCntr++) {
    EXPECT_EQ(tmpACM->Codec(codecCntr, &codecInst), 0);
    printf("%d- %s\n", codecCntr, codecInst.plname);
  }
  printf("\nChoose a send codec for side A [0]: ");
  char myStr[15] = "";
  EXPECT_TRUE(fgets(myStr, 10, stdin) != NULL);
  *codecID_A = (uint8_t) atoi(myStr);

  printf("\nChoose a send codec for side B [0]: ");
  EXPECT_TRUE(fgets(myStr, 10, stdin) != NULL);
  *codecID_B = (uint8_t) atoi(myStr);

  printf("\n");
}

void TwoWayCommunication::SetUp() {
  uint8_t codecID_A;
  uint8_t codecID_B;

  ChooseCodec(&codecID_A, &codecID_B);
  CodecInst codecInst_A;
  CodecInst codecInst_B;
  CodecInst dummyCodec;
  EXPECT_EQ(0, _acmA->Codec(codecID_A, &codecInst_A));
  EXPECT_EQ(0, _acmB->Codec(codecID_B, &codecInst_B));
  EXPECT_EQ(0, _acmA->Codec(6, &dummyCodec));

  //--- Set A codecs
  EXPECT_EQ(0, _acmA->RegisterSendCodec(codecInst_A));
  EXPECT_EQ(0, _acmA->RegisterReceiveCodec(codecInst_B));
#ifdef WEBRTC_DTMF_DETECTION
  _dtmfDetectorA = new(DTMFDetector);
  EXPECT_GT(_acmA->RegisterIncomingMessagesCallback(_dtmfDetectorA, ACMUSA),
            -1);
#endif
  //--- Set ref-A codecs
  EXPECT_EQ(0, _acmRefA->RegisterSendCodec(codecInst_A));
  EXPECT_EQ(0, _acmRefA->RegisterReceiveCodec(codecInst_B));

  //--- Set B codecs
  EXPECT_EQ(0, _acmB->RegisterSendCodec(codecInst_B));
  EXPECT_EQ(0, _acmB->RegisterReceiveCodec(codecInst_A));
#ifdef WEBRTC_DTMF_DETECTION
  _dtmfDetectorB = new(DTMFDetector);
  EXPECT_GT(_acmB->RegisterIncomingMessagesCallback(_dtmfDetectorB, ACMUSA),
            -1);
#endif

  //--- Set ref-B codecs
  EXPECT_EQ(0, _acmRefB->RegisterSendCodec(codecInst_B));
  EXPECT_EQ(0, _acmRefB->RegisterReceiveCodec(codecInst_A));

  uint16_t frequencyHz;

  //--- Input A
  std::string in_file_name = webrtc::test::ResourcePath(
      "audio_coding/testfile32kHz", "pcm");
  frequencyHz = 32000;
  printf("Enter input file at side A [%s]: ", in_file_name.c_str());
  PCMFile::ChooseFile(&in_file_name, 499, &frequencyHz);
  _inFileA.Open(in_file_name, frequencyHz, "rb");

  //--- Output A
  std::string out_file_a = webrtc::test::OutputPath() + "outA.pcm";
  printf("Output file at side A: %s\n", out_file_a.c_str());
  printf("Sampling frequency (in Hz) of the above file: %u\n", frequencyHz);
  _outFileA.Open(out_file_a, frequencyHz, "wb");
  std::string ref_file_name = webrtc::test::OutputPath() + "ref_outA.pcm";
  _outFileRefA.Open(ref_file_name, frequencyHz, "wb");

  //--- Input B
  in_file_name = webrtc::test::ResourcePath("audio_coding/testfile32kHz",
                                            "pcm");
  frequencyHz = 32000;
  printf("\n\nEnter input file at side B [%s]: ", in_file_name.c_str());
  PCMFile::ChooseFile(&in_file_name, 499, &frequencyHz);
  _inFileB.Open(in_file_name, frequencyHz, "rb");

  //--- Output B
  std::string out_file_b = webrtc::test::OutputPath() + "outB.pcm";
  printf("Output file at side B: %s\n", out_file_b.c_str());
  printf("Sampling frequency (in Hz) of the above file: %u\n", frequencyHz);
  _outFileB.Open(out_file_b, frequencyHz, "wb");
  ref_file_name = webrtc::test::OutputPath() + "ref_outB.pcm";
  _outFileRefB.Open(ref_file_name, frequencyHz, "wb");

  //--- Set A-to-B channel
  _channel_A2B = new Channel;
  _acmA->RegisterTransportCallback(_channel_A2B);
  _channel_A2B->RegisterReceiverACM(_acmB.get());
  //--- Do the same for the reference
  _channelRef_A2B = new Channel;
  _acmRefA->RegisterTransportCallback(_channelRef_A2B);
  _channelRef_A2B->RegisterReceiverACM(_acmRefB.get());

  //--- Set B-to-A channel
  _channel_B2A = new Channel;
  _acmB->RegisterTransportCallback(_channel_B2A);
  _channel_B2A->RegisterReceiverACM(_acmA.get());
  //--- Do the same for reference
  _channelRef_B2A = new Channel;
  _acmRefB->RegisterTransportCallback(_channelRef_B2A);
  _channelRef_B2A->RegisterReceiverACM(_acmRefA.get());

  // The clicks will be more obvious when we
  // are in FAX mode.
  EXPECT_EQ(_acmB->SetPlayoutMode(fax), 0);
  EXPECT_EQ(_acmRefB->SetPlayoutMode(fax), 0);
}

void TwoWayCommunication::SetUpAutotest() {
  CodecInst codecInst_A;
  CodecInst codecInst_B;
  CodecInst dummyCodec;

  EXPECT_EQ(0, _acmA->Codec("ISAC", &codecInst_A, 16000, 1));
  EXPECT_EQ(0, _acmB->Codec("L16", &codecInst_B, 8000, 1));
  EXPECT_EQ(0, _acmA->Codec(6, &dummyCodec));

  //--- Set A codecs
  EXPECT_EQ(0, _acmA->RegisterSendCodec(codecInst_A));
  EXPECT_EQ(0, _acmA->RegisterReceiveCodec(codecInst_B));
#ifdef WEBRTC_DTMF_DETECTION
  _dtmfDetectorA = new(DTMFDetector);
  EXPECT_EQ(0, _acmA->RegisterIncomingMessagesCallback(_dtmfDetectorA, ACMUSA));
#endif

  //--- Set ref-A codecs
  EXPECT_GT(_acmRefA->RegisterSendCodec(codecInst_A), -1);
  EXPECT_GT(_acmRefA->RegisterReceiveCodec(codecInst_B), -1);

  //--- Set B codecs
  EXPECT_GT(_acmB->RegisterSendCodec(codecInst_B), -1);
  EXPECT_GT(_acmB->RegisterReceiveCodec(codecInst_A), -1);
#ifdef WEBRTC_DTMF_DETECTION
  _dtmfDetectorB = new(DTMFDetector);
  EXPECT_EQ(0, _acmB->RegisterIncomingMessagesCallback(_dtmfDetectorB, ACMUSA));
#endif

  //--- Set ref-B codecs
  EXPECT_EQ(0, _acmRefB->RegisterSendCodec(codecInst_B));
  EXPECT_EQ(0, _acmRefB->RegisterReceiveCodec(codecInst_A));

  uint16_t frequencyHz;

  //--- Input A and B
  std::string in_file_name = webrtc::test::ResourcePath(
      "audio_coding/testfile32kHz", "pcm");
  frequencyHz = 16000;
  _inFileA.Open(in_file_name, frequencyHz, "rb");
  _inFileB.Open(in_file_name, frequencyHz, "rb");

  //--- Output A
  std::string output_file_a = webrtc::test::OutputPath() + "outAutotestA.pcm";
  frequencyHz = 16000;
  _outFileA.Open(output_file_a, frequencyHz, "wb");
  std::string output_ref_file_a = webrtc::test::OutputPath()
      + "ref_outAutotestA.pcm";
  _outFileRefA.Open(output_ref_file_a, frequencyHz, "wb");

  //--- Output B
  std::string output_file_b = webrtc::test::OutputPath() + "outAutotestB.pcm";
  frequencyHz = 16000;
  _outFileB.Open(output_file_b, frequencyHz, "wb");
  std::string output_ref_file_b = webrtc::test::OutputPath()
      + "ref_outAutotestB.pcm";
  _outFileRefB.Open(output_ref_file_b, frequencyHz, "wb");

  //--- Set A-to-B channel
  _channel_A2B = new Channel;
  _acmA->RegisterTransportCallback(_channel_A2B);
  _channel_A2B->RegisterReceiverACM(_acmB.get());
  //--- Do the same for the reference
  _channelRef_A2B = new Channel;
  _acmRefA->RegisterTransportCallback(_channelRef_A2B);
  _channelRef_A2B->RegisterReceiverACM(_acmRefB.get());

  //--- Set B-to-A channel
  _channel_B2A = new Channel;
  _acmB->RegisterTransportCallback(_channel_B2A);
  _channel_B2A->RegisterReceiverACM(_acmA.get());
  //--- Do the same for reference
  _channelRef_B2A = new Channel;
  _acmRefB->RegisterTransportCallback(_channelRef_B2A);
  _channelRef_B2A->RegisterReceiverACM(_acmRefA.get());

  // The clicks will be more obvious when we
  // are in FAX mode.
  EXPECT_EQ(0, _acmB->SetPlayoutMode(fax));
  EXPECT_EQ(0, _acmRefB->SetPlayoutMode(fax));
}

void TwoWayCommunication::Perform() {
  if (_testMode == 0) {
    SetUpAutotest();
  } else {
    SetUp();
  }
  unsigned int msecPassed = 0;
  unsigned int secPassed = 0;

  int32_t outFreqHzA = _outFileA.SamplingFrequency();
  int32_t outFreqHzB = _outFileB.SamplingFrequency();

  AudioFrame audioFrame;

  CodecInst codecInst_B;
  CodecInst dummy;

  EXPECT_EQ(0, _acmB->SendCodec(&codecInst_B));

  // In the following loop we tests that the code can handle misuse of the APIs.
  // In the middle of a session with data flowing between two sides, called A
  // and B, APIs will be called, like ResetEncoder(), and the code should
  // continue to run, and be able to recover.
  bool expect_error_add = false;
  bool expect_error_process = false;
  while (!_inFileA.EndOfFile() && !_inFileB.EndOfFile()) {
    msecPassed += 10;
    EXPECT_GT(_inFileA.Read10MsData(audioFrame), 0);
    EXPECT_EQ(0, _acmA->Add10MsData(audioFrame));
    EXPECT_EQ(0, _acmRefA->Add10MsData(audioFrame));

    EXPECT_GT(_inFileB.Read10MsData(audioFrame), 0);

    // Expect call to pass except for the time when no send codec is registered.
    if (!expect_error_add) {
      EXPECT_EQ(0, _acmB->Add10MsData(audioFrame));
    } else {
      EXPECT_EQ(-1, _acmB->Add10MsData(audioFrame));
    }
    // Expect to pass except for the time when there either is no send codec
    // registered, or no receive codec.
    if (!expect_error_process) {
      EXPECT_GT(_acmB->Process(), -1);
    } else {
      EXPECT_EQ(_acmB->Process(), -1);
    }
    EXPECT_EQ(0, _acmRefB->Add10MsData(audioFrame));
    EXPECT_GT(_acmA->Process(), -1);
    EXPECT_GT(_acmRefA->Process(), -1);
    EXPECT_GT(_acmRefB->Process(), -1);
    EXPECT_EQ(0, _acmA->PlayoutData10Ms(outFreqHzA, &audioFrame));
    _outFileA.Write10MsData(audioFrame);
    EXPECT_EQ(0, _acmRefA->PlayoutData10Ms(outFreqHzA, &audioFrame));
    _outFileRefA.Write10MsData(audioFrame);
    EXPECT_EQ(0, _acmB->PlayoutData10Ms(outFreqHzB, &audioFrame));
    _outFileB.Write10MsData(audioFrame);
    EXPECT_EQ(0, _acmRefB->PlayoutData10Ms(outFreqHzB, &audioFrame));
    _outFileRefB.Write10MsData(audioFrame);

    // Update time counters each time a second of data has passed.
    if (msecPassed >= 1000) {
      msecPassed = 0;
      secPassed++;
    }
    // Call RestEncoder for ACM on side A, and InitializeSender for ACM on
    // side B.
    if (((secPassed % 5) == 4) && (msecPassed == 0)) {
      EXPECT_EQ(0, _acmA->ResetEncoder());
      EXPECT_EQ(0, _acmB->InitializeSender());
      expect_error_add = true;
      expect_error_process = true;
    }
    // Re-register send codec on side B.
    if (((secPassed % 5) == 4) && (msecPassed >= 990)) {
      EXPECT_EQ(0, _acmB->RegisterSendCodec(codecInst_B));
      EXPECT_EQ(0, _acmB->SendCodec(&dummy));
      expect_error_add = false;
      expect_error_process = false;
    }
    // Reset decoder on side B, and initialize receiver on side A.
    if (((secPassed % 7) == 6) && (msecPassed == 0)) {
      EXPECT_EQ(0, _acmB->ResetDecoder());
      EXPECT_EQ(0, _acmA->InitializeReceiver());
    }
    // Re-register codec on side A.
    if (((secPassed % 7) == 6) && (msecPassed >= 990)) {
      EXPECT_EQ(0, _acmA->RegisterReceiveCodec(codecInst_B));
    }
  }
}

}  // namespace webrtc
