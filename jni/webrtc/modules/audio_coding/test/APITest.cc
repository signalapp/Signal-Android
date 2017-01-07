/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/APITest.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <iostream>
#include <ostream>
#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/platform_thread.h"
#include "webrtc/base/timeutils.h"
#include "webrtc/common.h"
#include "webrtc/common_types.h"
#include "webrtc/engine_configurations.h"
#include "webrtc/modules/audio_coding/acm2/acm_common_defs.h"
#include "webrtc/modules/audio_coding/test/utility.h"
#include "webrtc/system_wrappers/include/event_wrapper.h"
#include "webrtc/system_wrappers/include/trace.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

#define TEST_DURATION_SEC 600
#define NUMBER_OF_SENDER_TESTS 6
#define MAX_FILE_NAME_LENGTH_BYTE 500

void APITest::Wait(uint32_t waitLengthMs) {
  if (_randomTest) {
    return;
  } else {
    EventWrapper* myEvent = EventWrapper::Create();
    myEvent->Wait(waitLengthMs);
    delete myEvent;
    return;
  }
}

APITest::APITest(const Config& config)
    : _acmA(AudioCodingModule::Create(1)),
      _acmB(AudioCodingModule::Create(2)),
      _channel_A2B(NULL),
      _channel_B2A(NULL),
      _writeToFile(true),
      _pullEventA(NULL),
      _pushEventA(NULL),
      _processEventA(NULL),
      _apiEventA(NULL),
      _pullEventB(NULL),
      _pushEventB(NULL),
      _processEventB(NULL),
      _apiEventB(NULL),
      _codecCntrA(0),
      _codecCntrB(0),
      _thereIsEncoderA(false),
      _thereIsEncoderB(false),
      _thereIsDecoderA(false),
      _thereIsDecoderB(false),
      _sendVADA(false),
      _sendDTXA(false),
      _sendVADModeA(VADNormal),
      _sendVADB(false),
      _sendDTXB(false),
      _sendVADModeB(VADNormal),
      _minDelayA(0),
      _minDelayB(0),
      _dotPositionA(0),
      _dotMoveDirectionA(1),
      _dotPositionB(39),
      _dotMoveDirectionB(-1),
      _vadCallbackA(NULL),
      _vadCallbackB(NULL),
      _apiTestRWLock(*RWLockWrapper::CreateRWLock()),
      _randomTest(false),
      _testNumA(0),
      _testNumB(1) {
  int n;
  for (n = 0; n < 32; n++) {
    _payloadUsed[n] = false;
  }

  _movingDot[40] = '\0';

  for (int n = 0; n < 40; n++) {
    _movingDot[n] = ' ';
  }
}

APITest::~APITest() {
  DELETE_POINTER(_channel_A2B);
  DELETE_POINTER(_channel_B2A);

  DELETE_POINTER(_pushEventA);
  DELETE_POINTER(_pullEventA);
  DELETE_POINTER(_processEventA);
  DELETE_POINTER(_apiEventA);

  DELETE_POINTER(_pushEventB);
  DELETE_POINTER(_pullEventB);
  DELETE_POINTER(_processEventB);
  DELETE_POINTER(_apiEventB);

  _inFileA.Close();
  _outFileA.Close();

  _inFileB.Close();
  _outFileB.Close();

  DELETE_POINTER(_vadCallbackA);
  DELETE_POINTER(_vadCallbackB);

  delete &_apiTestRWLock;
}

int16_t APITest::SetUp() {
  CodecInst dummyCodec;
  int lastPayloadType = 0;

  int16_t numCodecs = _acmA->NumberOfCodecs();
  for (uint8_t n = 0; n < numCodecs; n++) {
    AudioCodingModule::Codec(n, &dummyCodec);
    if ((STR_CASE_CMP(dummyCodec.plname, "CN") == 0)
        && (dummyCodec.plfreq == 32000)) {
      continue;
    }

    printf("Register Receive Codec %s  ", dummyCodec.plname);

    if ((n != 0) && !FixedPayloadTypeCodec(dummyCodec.plname)) {
      // Check registration with an already occupied payload type
      int currentPayloadType = dummyCodec.pltype;
      dummyCodec.pltype = 97;  //lastPayloadType;
      CHECK_ERROR(_acmB->RegisterReceiveCodec(dummyCodec));
      dummyCodec.pltype = currentPayloadType;
    }

    if ((n < numCodecs - 1) && !FixedPayloadTypeCodec(dummyCodec.plname)) {
      // test if re-registration works;
      CodecInst nextCodec;
      int currentPayloadType = dummyCodec.pltype;
      AudioCodingModule::Codec(n + 1, &nextCodec);
      dummyCodec.pltype = nextCodec.pltype;
      if (!FixedPayloadTypeCodec(nextCodec.plname)) {
        _acmB->RegisterReceiveCodec(dummyCodec);
      }
      dummyCodec.pltype = currentPayloadType;
    }

    if ((n < numCodecs - 1) && !FixedPayloadTypeCodec(dummyCodec.plname)) {
      // test if un-registration works;
      CodecInst nextCodec;
      AudioCodingModule::Codec(n + 1, &nextCodec);
      nextCodec.pltype = dummyCodec.pltype;
      if (!FixedPayloadTypeCodec(nextCodec.plname)) {
        CHECK_ERROR_MT(_acmA->RegisterReceiveCodec(nextCodec));
        CHECK_ERROR_MT(_acmA->UnregisterReceiveCodec(nextCodec.pltype));
      }
    }

    CHECK_ERROR_MT(_acmA->RegisterReceiveCodec(dummyCodec));
    printf("   side A done!");
    CHECK_ERROR_MT(_acmB->RegisterReceiveCodec(dummyCodec));
    printf("   side B done!\n");

    if (!strcmp(dummyCodec.plname, "CN")) {
      CHECK_ERROR_MT(_acmA->RegisterSendCodec(dummyCodec));
      CHECK_ERROR_MT(_acmB->RegisterSendCodec(dummyCodec));
    }
    lastPayloadType = dummyCodec.pltype;
    if ((lastPayloadType >= 96) && (lastPayloadType <= 127)) {
      _payloadUsed[lastPayloadType - 96] = true;
    }
  }
  _thereIsDecoderA = true;
  _thereIsDecoderB = true;

  // Register Send Codec
  AudioCodingModule::Codec((uint8_t) _codecCntrA, &dummyCodec);
  CHECK_ERROR_MT(_acmA->RegisterSendCodec(dummyCodec));
  _thereIsEncoderA = true;
  //
  AudioCodingModule::Codec((uint8_t) _codecCntrB, &dummyCodec);
  CHECK_ERROR_MT(_acmB->RegisterSendCodec(dummyCodec));
  _thereIsEncoderB = true;

  uint16_t frequencyHz;

  printf("\n\nAPI Test\n");
  printf("========\n");
  printf("Hit enter to accept the default values indicated in []\n\n");

  //--- Input A
  std::string file_name = webrtc::test::ResourcePath(
      "audio_coding/testfile32kHz", "pcm");
  frequencyHz = 32000;
  printf("Enter input file at side A [%s]: ", file_name.c_str());
  PCMFile::ChooseFile(&file_name, 499, &frequencyHz);
  _inFileA.Open(file_name, frequencyHz, "rb", true);

  //--- Output A
  std::string out_file_a = webrtc::test::OutputPath() + "outA.pcm";
  printf("Enter output file at side A [%s]: ", out_file_a.c_str());
  PCMFile::ChooseFile(&out_file_a, 499, &frequencyHz);
  _outFileA.Open(out_file_a, frequencyHz, "wb");

  //--- Input B
  file_name = webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
  printf("\n\nEnter input file at side B [%s]: ", file_name.c_str());
  PCMFile::ChooseFile(&file_name, 499, &frequencyHz);
  _inFileB.Open(file_name, frequencyHz, "rb", true);

  //--- Output B
  std::string out_file_b = webrtc::test::OutputPath() + "outB.pcm";
  printf("Enter output file at side B [%s]: ", out_file_b.c_str());
  PCMFile::ChooseFile(&out_file_b, 499, &frequencyHz);
  _outFileB.Open(out_file_b, frequencyHz, "wb");

  //--- Set A-to-B channel
  _channel_A2B = new Channel(2);
  CHECK_ERROR_MT(_acmA->RegisterTransportCallback(_channel_A2B));
  _channel_A2B->RegisterReceiverACM(_acmB.get());

  //--- Set B-to-A channel
  _channel_B2A = new Channel(1);
  CHECK_ERROR_MT(_acmB->RegisterTransportCallback(_channel_B2A));
  _channel_B2A->RegisterReceiverACM(_acmA.get());

  //--- EVENT TIMERS
  // A
  _pullEventA = EventTimerWrapper::Create();
  _pushEventA = EventTimerWrapper::Create();
  _processEventA = EventTimerWrapper::Create();
  _apiEventA = EventWrapper::Create();
  // B
  _pullEventB = EventTimerWrapper::Create();
  _pushEventB = EventTimerWrapper::Create();
  _processEventB = EventTimerWrapper::Create();
  _apiEventB = EventWrapper::Create();

  //--- I/O params
  // A
  _outFreqHzA = _outFileA.SamplingFrequency();
  // B
  _outFreqHzB = _outFileB.SamplingFrequency();

  //Trace::SetEncryptedTraceFile("ACMAPITestEncrypted.txt");

  char print[11];

  // Create a trace file.
  Trace::CreateTrace();
  Trace::SetTraceFile(
      (webrtc::test::OutputPath() + "acm_api_trace.txt").c_str());

  printf("\nRandom Test (y/n)?");
  EXPECT_TRUE(fgets(print, 10, stdin) != NULL);
  print[10] = '\0';
  if (strstr(print, "y") != NULL) {
    _randomTest = true;
    _verbose = false;
    _writeToFile = false;
  } else {
    _randomTest = false;
    printf("\nPrint Tests (y/n)? ");
    EXPECT_TRUE(fgets(print, 10, stdin) != NULL);
    print[10] = '\0';
    if (strstr(print, "y") == NULL) {
      EXPECT_TRUE(freopen("APITest_log.txt", "w", stdout) != 0);
      _verbose = false;
    }
  }

  _vadCallbackA = new VADCallback;
  _vadCallbackB = new VADCallback;

  return 0;
}

bool APITest::PushAudioThreadA(void* obj) {
  return static_cast<APITest*>(obj)->PushAudioRunA();
}

bool APITest::PushAudioThreadB(void* obj) {
  return static_cast<APITest*>(obj)->PushAudioRunB();
}

bool APITest::PullAudioThreadA(void* obj) {
  return static_cast<APITest*>(obj)->PullAudioRunA();
}

bool APITest::PullAudioThreadB(void* obj) {
  return static_cast<APITest*>(obj)->PullAudioRunB();
}

bool APITest::ProcessThreadA(void* obj) {
  return static_cast<APITest*>(obj)->ProcessRunA();
}

bool APITest::ProcessThreadB(void* obj) {
  return static_cast<APITest*>(obj)->ProcessRunB();
}

bool APITest::APIThreadA(void* obj) {
  return static_cast<APITest*>(obj)->APIRunA();
}

bool APITest::APIThreadB(void* obj) {
  return static_cast<APITest*>(obj)->APIRunB();
}

bool APITest::PullAudioRunA() {
  _pullEventA->Wait(100);
  AudioFrame audioFrame;
  bool muted;
  if (_acmA->PlayoutData10Ms(_outFreqHzA, &audioFrame, &muted) < 0) {
    bool thereIsDecoder;
    {
      ReadLockScoped rl(_apiTestRWLock);
      thereIsDecoder = _thereIsDecoderA;
    }
    if (thereIsDecoder) {
      fprintf(stderr, "\n>>>>>>    cannot pull audio A       <<<<<<<< \n");
    }
  } else {
    if (_writeToFile) {
      _outFileA.Write10MsData(audioFrame);
    }
  }
  return true;
}

bool APITest::PullAudioRunB() {
  _pullEventB->Wait(100);
  AudioFrame audioFrame;
  bool muted;
  if (_acmB->PlayoutData10Ms(_outFreqHzB, &audioFrame, &muted) < 0) {
    bool thereIsDecoder;
    {
      ReadLockScoped rl(_apiTestRWLock);
      thereIsDecoder = _thereIsDecoderB;
    }
    if (thereIsDecoder) {
      fprintf(stderr, "\n>>>>>>    cannot pull audio B       <<<<<<<< \n");
      fprintf(stderr, "%d %d\n", _testNumA, _testNumB);
    }
  } else {
    if (_writeToFile) {
      _outFileB.Write10MsData(audioFrame);
    }
  }
  return true;
}

bool APITest::PushAudioRunA() {
  _pushEventA->Wait(100);
  AudioFrame audioFrame;
  _inFileA.Read10MsData(audioFrame);
  if (_acmA->Add10MsData(audioFrame) < 0) {
    bool thereIsEncoder;
    {
      ReadLockScoped rl(_apiTestRWLock);
      thereIsEncoder = _thereIsEncoderA;
    }
    if (thereIsEncoder) {
      fprintf(stderr, "\n>>>>        add10MsData at A failed       <<<<\n");
    }
  }
  return true;
}

bool APITest::PushAudioRunB() {
  _pushEventB->Wait(100);
  AudioFrame audioFrame;
  _inFileB.Read10MsData(audioFrame);
  if (_acmB->Add10MsData(audioFrame) < 0) {
    bool thereIsEncoder;
    {
      ReadLockScoped rl(_apiTestRWLock);
      thereIsEncoder = _thereIsEncoderB;
    }

    if (thereIsEncoder) {
      fprintf(stderr, "\n>>>>   cannot add audio to B    <<<<");
    }
  }

  return true;
}

bool APITest::ProcessRunA() {
  _processEventA->Wait(100);
  return true;
}

bool APITest::ProcessRunB() {
  _processEventB->Wait(100);
  return true;
}

/*/
 *
 * In side A we test the APIs which are related to sender Side.
 *
/*/

void APITest::RunTest(char thread) {
  int testNum;
  {
    WriteLockScoped cs(_apiTestRWLock);
    if (thread == 'A') {
      _testNumA = (_testNumB + 1 + (rand() % 3)) % 4;
      testNum = _testNumA;

      _movingDot[_dotPositionA] = ' ';
      if (_dotPositionA == 0) {
        _dotMoveDirectionA = 1;
      }
      if (_dotPositionA == 19) {
        _dotMoveDirectionA = -1;
      }
      _dotPositionA += _dotMoveDirectionA;
      _movingDot[_dotPositionA] = (_dotMoveDirectionA > 0) ? '>' : '<';
    } else {
      _testNumB = (_testNumA + 1 + (rand() % 3)) % 4;
      testNum = _testNumB;

      _movingDot[_dotPositionB] = ' ';
      if (_dotPositionB == 20) {
        _dotMoveDirectionB = 1;
      }
      if (_dotPositionB == 39) {
        _dotMoveDirectionB = -1;
      }
      _dotPositionB += _dotMoveDirectionB;
      _movingDot[_dotPositionB] = (_dotMoveDirectionB > 0) ? '>' : '<';
    }
    //fprintf(stderr, "%c: %d \n", thread, testNum);
    //fflush(stderr);
  }
  switch (testNum) {
    case 0:
      CurrentCodec('A');
      ChangeCodec('A');
      break;
    case 1:
      if (!_randomTest) {
        fprintf(stdout, "\nTesting Delay ...\n");
      }
      TestDelay('A');
      break;
    case 2:
      TestSendVAD('A');
      break;
    case 3:
      TestRegisteration('A');
      break;
    default:
      fprintf(stderr, "Wrong Test Number\n");
      getc(stdin);
      exit(1);
  }
}

bool APITest::APIRunA() {
  _apiEventA->Wait(50);

  bool randomTest;
  {
    ReadLockScoped rl(_apiTestRWLock);
    randomTest = _randomTest;
  }
  if (randomTest) {
    RunTest('A');
  } else {
    CurrentCodec('A');
    ChangeCodec('A');
    if (_codecCntrA == 0) {
      fprintf(stdout, "\nTesting Delay ...\n");
      TestDelay('A');
    }
    // VAD TEST
    TestSendVAD('A');
    TestRegisteration('A');
  }
  return true;
}

bool APITest::APIRunB() {
  _apiEventB->Wait(50);
  bool randomTest;
  {
    ReadLockScoped rl(_apiTestRWLock);
    randomTest = _randomTest;
  }
  //_apiEventB->Wait(2000);
  if (randomTest) {
    RunTest('B');
  }

  return true;
}

void APITest::Perform() {
  SetUp();

  //--- THREADS
  // A
  // PUSH
  rtc::PlatformThread myPushAudioThreadA(PushAudioThreadA, this,
                                         "PushAudioThreadA");
  myPushAudioThreadA.Start();
  // PULL
  rtc::PlatformThread myPullAudioThreadA(PullAudioThreadA, this,
                                         "PullAudioThreadA");
  myPullAudioThreadA.Start();
  // Process
  rtc::PlatformThread myProcessThreadA(ProcessThreadA, this, "ProcessThreadA");
  myProcessThreadA.Start();
  // API
  rtc::PlatformThread myAPIThreadA(APIThreadA, this, "APIThreadA");
  myAPIThreadA.Start();
  // B
  // PUSH
  rtc::PlatformThread myPushAudioThreadB(PushAudioThreadB, this,
                                         "PushAudioThreadB");
  myPushAudioThreadB.Start();
  // PULL
  rtc::PlatformThread myPullAudioThreadB(PullAudioThreadB, this,
                                         "PullAudioThreadB");
  myPullAudioThreadB.Start();
  // Process
  rtc::PlatformThread myProcessThreadB(ProcessThreadB, this, "ProcessThreadB");
  myProcessThreadB.Start();
  // API
  rtc::PlatformThread myAPIThreadB(APIThreadB, this, "APIThreadB");
  myAPIThreadB.Start();

  //_apiEventA->StartTimer(true, 5000);
  //_apiEventB->StartTimer(true, 5000);

  _processEventA->StartTimer(true, 10);
  _processEventB->StartTimer(true, 10);

  _pullEventA->StartTimer(true, 10);
  _pullEventB->StartTimer(true, 10);

  _pushEventA->StartTimer(true, 10);
  _pushEventB->StartTimer(true, 10);

  // Keep main thread waiting for sender/receiver
  // threads to complete
  EventWrapper* completeEvent = EventWrapper::Create();
  uint64_t startTime = rtc::TimeMillis();
  uint64_t currentTime;
  // Run test in 2 minutes (120000 ms).
  do {
    {
      //ReadLockScoped rl(_apiTestRWLock);
      //fprintf(stderr, "\r%s", _movingDot);
    }
    //fflush(stderr);
    completeEvent->Wait(50);
    currentTime = rtc::TimeMillis();
  } while ((currentTime - startTime) < 120000);

  //completeEvent->Wait(0xFFFFFFFF);
  //(unsigned long)((unsigned long)TEST_DURATION_SEC * (unsigned long)1000));
  delete completeEvent;

  myPushAudioThreadA.Stop();
  myPullAudioThreadA.Stop();
  myProcessThreadA.Stop();
  myAPIThreadA.Stop();

  myPushAudioThreadB.Stop();
  myPullAudioThreadB.Stop();
  myProcessThreadB.Stop();
  myAPIThreadB.Stop();
}

void APITest::CheckVADStatus(char side) {

  bool dtxEnabled;
  bool vadEnabled;
  ACMVADMode vadMode;

  if (side == 'A') {
    _acmA->VAD(&dtxEnabled, &vadEnabled, &vadMode);
    _acmA->RegisterVADCallback(NULL);
    _vadCallbackA->Reset();
    _acmA->RegisterVADCallback(_vadCallbackA);

    if (!_randomTest) {
      if (_verbose) {
        fprintf(stdout, "DTX %3s, VAD %3s, Mode %d", dtxEnabled ? "ON" : "OFF",
                vadEnabled ? "ON" : "OFF", (int) vadMode);
        Wait(5000);
        fprintf(stdout, " => bit-rate %3.0f kbps\n", _channel_A2B->BitRate());
      } else {
        Wait(5000);
        fprintf(stdout, "DTX %3s, VAD %3s, Mode %d => bit-rate %3.0f kbps\n",
                dtxEnabled ? "ON" : "OFF", vadEnabled ? "ON" : "OFF",
                (int) vadMode, _channel_A2B->BitRate());
      }
      _vadCallbackA->PrintFrameTypes();
    }

    if (dtxEnabled != _sendDTXA) {
      fprintf(stderr, ">>>   Error Enabling DTX    <<<\n");
    }
    if ((vadEnabled != _sendVADA) && (!dtxEnabled)) {
      fprintf(stderr, ">>>   Error Enabling VAD    <<<\n");
    }
    if ((vadMode != _sendVADModeA) && vadEnabled) {
      fprintf(stderr, ">>>   Error setting VAD-mode    <<<\n");
    }
  } else {
    _acmB->VAD(&dtxEnabled, &vadEnabled, &vadMode);

    _acmB->RegisterVADCallback(NULL);
    _vadCallbackB->Reset();
    _acmB->RegisterVADCallback(_vadCallbackB);

    if (!_randomTest) {
      if (_verbose) {
        fprintf(stdout, "DTX %3s, VAD %3s, Mode %d", dtxEnabled ? "ON" : "OFF",
                vadEnabled ? "ON" : "OFF", (int) vadMode);
        Wait(5000);
        fprintf(stdout, " => bit-rate %3.0f kbps\n", _channel_B2A->BitRate());
      } else {
        Wait(5000);
        fprintf(stdout, "DTX %3s, VAD %3s, Mode %d => bit-rate %3.0f kbps\n",
                dtxEnabled ? "ON" : "OFF", vadEnabled ? "ON" : "OFF",
                (int) vadMode, _channel_B2A->BitRate());
      }
      _vadCallbackB->PrintFrameTypes();
    }

    if (dtxEnabled != _sendDTXB) {
      fprintf(stderr, ">>>   Error Enabling DTX    <<<\n");
    }
    if ((vadEnabled != _sendVADB) && (!dtxEnabled)) {
      fprintf(stderr, ">>>   Error Enabling VAD    <<<\n");
    }
    if ((vadMode != _sendVADModeB) && vadEnabled) {
      fprintf(stderr, ">>>   Error setting VAD-mode    <<<\n");
    }
  }
}

// Set Min delay, get delay, playout timestamp
void APITest::TestDelay(char side) {
  AudioCodingModule* myACM;
  Channel* myChannel;
  int32_t* myMinDelay;
  EventTimerWrapper* myEvent = EventTimerWrapper::Create();

  uint32_t inTimestamp = 0;
  double estimDelay = 0;

  double averageEstimDelay = 0;
  double averageDelay = 0;

  CircularBuffer estimDelayCB(100);
  estimDelayCB.SetArithMean(true);

  if (side == 'A') {
    myACM = _acmA.get();
    myChannel = _channel_B2A;
    myMinDelay = &_minDelayA;
  } else {
    myACM = _acmB.get();
    myChannel = _channel_A2B;
    myMinDelay = &_minDelayB;
  }

  CHECK_ERROR_MT(myACM->SetMinimumPlayoutDelay(*myMinDelay));

  inTimestamp = myChannel->LastInTimestamp();
  rtc::Optional<uint32_t> outTimestamp = myACM->PlayoutTimestamp();
  CHECK_ERROR_MT(outTimestamp ? 0 : -1);

  if (!_randomTest) {
    myEvent->StartTimer(true, 30);
    int n = 0;
    int settlePoint = 5000;
    while (n < settlePoint + 400) {
      myEvent->Wait(1000);

      inTimestamp = myChannel->LastInTimestamp();
      outTimestamp = myACM->PlayoutTimestamp();
      CHECK_ERROR_MT(outTimestamp ? 0 : -1);

      //std::cout << outTimestamp << std::endl << std::flush;
      estimDelay = (double)((uint32_t)(inTimestamp - *outTimestamp)) /
                   ((double)myACM->ReceiveFrequency() / 1000.0);

      estimDelayCB.Update(estimDelay);

      estimDelayCB.ArithMean(averageEstimDelay);
      //printf("\n %6.1f \n", estimDelay);
      //std::cout << " " << std::flush;

      if (_verbose) {
        fprintf(stdout,
                "\rExpected: %4d,    retreived: %6.1f,   measured: %6.1f",
                *myMinDelay, averageDelay, averageEstimDelay);
        std::cout << " " << std::flush;
      }
      if ((averageDelay > *myMinDelay) && (n < settlePoint)) {
        settlePoint = n;
      }
      n++;
    }
    myEvent->StopTimer();
  }

  if ((!_verbose) && (!_randomTest)) {
    fprintf(stdout, "\nExpected: %4d,    retreived: %6.1f,   measured: %6.1f",
            *myMinDelay, averageDelay, averageEstimDelay);
  }

  *myMinDelay = (rand() % 1000) + 1;

  NetworkStatistics networkStat;
  CHECK_ERROR_MT(myACM->GetNetworkStatistics(&networkStat));

  if (!_randomTest) {
    fprintf(stdout, "\n\nJitter Statistics at Side %c\n", side);
    fprintf(stdout, "--------------------------------------\n");
    fprintf(stdout, "buffer-size............. %d\n",
            networkStat.currentBufferSize);
    fprintf(stdout, "Preferred buffer-size... %d\n",
            networkStat.preferredBufferSize);
    fprintf(stdout, "Peaky jitter mode........%d\n",
            networkStat.jitterPeaksFound);
    fprintf(stdout, "packet-size rate........ %d\n",
            networkStat.currentPacketLossRate);
    fprintf(stdout, "discard rate............ %d\n",
            networkStat.currentDiscardRate);
    fprintf(stdout, "expand rate............. %d\n",
            networkStat.currentExpandRate);
    fprintf(stdout, "speech expand rate...... %d\n",
            networkStat.currentSpeechExpandRate);
    fprintf(stdout, "Preemptive rate......... %d\n",
            networkStat.currentPreemptiveRate);
    fprintf(stdout, "Accelerate rate......... %d\n",
            networkStat.currentAccelerateRate);
    fprintf(stdout, "Secondary decoded rate.. %d\n",
            networkStat.currentSecondaryDecodedRate);
    fprintf(stdout, "Clock-drift............. %d\n", networkStat.clockDriftPPM);
    fprintf(stdout, "Mean waiting time....... %d\n",
            networkStat.meanWaitingTimeMs);
    fprintf(stdout, "Median waiting time..... %d\n",
            networkStat.medianWaitingTimeMs);
    fprintf(stdout, "Min waiting time........ %d\n",
            networkStat.minWaitingTimeMs);
    fprintf(stdout, "Max waiting time........ %d\n",
            networkStat.maxWaitingTimeMs);
  }

  CHECK_ERROR_MT(myACM->SetMinimumPlayoutDelay(*myMinDelay));

  if (!_randomTest) {
    myEvent->Wait(500);
    fprintf(stdout, "\n");
    fprintf(stdout, "\n");
  }
  delete myEvent;
}

// Unregister a codec & register again.
void APITest::TestRegisteration(char sendSide) {
  AudioCodingModule* sendACM;
  AudioCodingModule* receiveACM;
  bool* thereIsDecoder;
  EventWrapper* myEvent = EventWrapper::Create();

  if (!_randomTest) {
    fprintf(stdout, "\n\n");
    fprintf(stdout,
            "---------------------------------------------------------\n");
    fprintf(stdout, "           Unregister/register Receive Codec\n");
    fprintf(stdout,
            "---------------------------------------------------------\n");
  }

  switch (sendSide) {
    case 'A': {
      sendACM = _acmA.get();
      receiveACM = _acmB.get();
      thereIsDecoder = &_thereIsDecoderB;
      break;
    }
    case 'B': {
      sendACM = _acmB.get();
      receiveACM = _acmA.get();
      thereIsDecoder = &_thereIsDecoderA;
      break;
    }
    default:
      fprintf(stderr, "Invalid sender-side in TestRegistration(%c)\n",
              sendSide);
      exit(-1);
  }

  auto myCodec = sendACM->SendCodec();
  if (!myCodec) {
    CodecInst ci;
    AudioCodingModule::Codec(_codecCntrA, &ci);
    myCodec = rtc::Optional<CodecInst>(ci);
  }

  if (!_randomTest) {
    fprintf(stdout, "Unregistering reveive codec, NO AUDIO.\n");
    fflush (stdout);
  }
  {
    WriteLockScoped wl(_apiTestRWLock);
    *thereIsDecoder = false;
  }
  //myEvent->Wait(20);
  CHECK_ERROR_MT(receiveACM->UnregisterReceiveCodec(myCodec->pltype));
  Wait(1000);

  int currentPayload = myCodec->pltype;

  if (!FixedPayloadTypeCodec(myCodec->plname)) {
    int32_t i;
    for (i = 0; i < 32; i++) {
      if (!_payloadUsed[i]) {
        if (!_randomTest) {
          fprintf(stdout,
                  "Register receive codec with new Payload, AUDIO BACK.\n");
        }
        //myCodec->pltype = i + 96;
        //CHECK_ERROR_MT(receiveACM->RegisterReceiveCodec(*myCodec));
        //CHECK_ERROR_MT(sendACM->RegisterSendCodec(*myCodec));
        //myEvent->Wait(20);
        //{
        //    WriteLockScoped wl(_apiTestRWLock);
        //    *thereIsDecoder = true;
        //}
        Wait(1000);

        if (!_randomTest) {
          fprintf(stdout, "Unregistering reveive codec, NO AUDIO.\n");
        }
        //{
        //    WriteLockScoped wl(_apiTestRWLock);
        //    *thereIsDecoder = false;
        //}
        //myEvent->Wait(20);
        //CHECK_ERROR_MT(receiveACM->UnregisterReceiveCodec(myCodec->pltype));
        Wait(1000);

        myCodec->pltype = currentPayload;
        if (!_randomTest) {
          fprintf(stdout,
                  "Register receive codec with default Payload, AUDIO BACK.\n");
          fflush (stdout);
        }
        CHECK_ERROR_MT(receiveACM->RegisterReceiveCodec(*myCodec));
        //CHECK_ERROR_MT(sendACM->RegisterSendCodec(*myCodec));
        myEvent->Wait(20);
        {
          WriteLockScoped wl(_apiTestRWLock);
          *thereIsDecoder = true;
        }
        Wait(1000);

        break;
      }
    }
    if (i == 32) {
      CHECK_ERROR_MT(receiveACM->RegisterReceiveCodec(*myCodec));
      {
        WriteLockScoped wl(_apiTestRWLock);
        *thereIsDecoder = true;
      }
    }
  } else {
    if (!_randomTest) {
      fprintf(stdout,
              "Register receive codec with fixed Payload, AUDIO BACK.\n");
      fflush (stdout);
    }
    CHECK_ERROR_MT(receiveACM->RegisterReceiveCodec(*myCodec));
    //CHECK_ERROR_MT(receiveACM->UnregisterReceiveCodec(myCodec->pltype));
    //CHECK_ERROR_MT(receiveACM->RegisterReceiveCodec(*myCodec));
    myEvent->Wait(20);
    {
      WriteLockScoped wl(_apiTestRWLock);
      *thereIsDecoder = true;
    }
  }
  delete myEvent;
  if (!_randomTest) {
    fprintf(stdout,
            "---------------------------------------------------------\n");
  }
}

void APITest::TestSendVAD(char side) {
  if (_randomTest) {
    return;
  }

  bool* vad;
  bool* dtx;
  ACMVADMode* mode;
  Channel* myChannel;
  AudioCodingModule* myACM;

  CodecInst myCodec;
  if (!_randomTest) {
    fprintf(stdout, "\n\n");
    fprintf(stdout, "-----------------------------------------------\n");
    fprintf(stdout, "                Test VAD API\n");
    fprintf(stdout, "-----------------------------------------------\n");
  }

  if (side == 'A') {
    AudioCodingModule::Codec(_codecCntrA, &myCodec);
    vad = &_sendVADA;
    dtx = &_sendDTXA;
    mode = &_sendVADModeA;
    myChannel = _channel_A2B;
    myACM = _acmA.get();
  } else {
    AudioCodingModule::Codec(_codecCntrB, &myCodec);
    vad = &_sendVADB;
    dtx = &_sendDTXB;
    mode = &_sendVADModeB;
    myChannel = _channel_B2A;
    myACM = _acmB.get();
  }

  CheckVADStatus(side);
  if (!_randomTest) {
    fprintf(stdout, "\n\n");
  }

  switch (*mode) {
    case VADNormal:
      *vad = true;
      *dtx = true;
      *mode = VADAggr;
      break;
    case VADLowBitrate:
      *vad = true;
      *dtx = true;
      *mode = VADVeryAggr;
      break;
    case VADAggr:
      *vad = true;
      *dtx = true;
      *mode = VADLowBitrate;
      break;
    case VADVeryAggr:
      *vad = false;
      *dtx = false;
      *mode = VADNormal;
      break;
    default:
      *mode = VADNormal;
  }

  *dtx = (myCodec.plfreq == 32000) ? false : *dtx;

  CHECK_ERROR_MT(myACM->SetVAD(*dtx, *vad, *mode));
  myChannel->ResetStats();

  CheckVADStatus(side);
  if (!_randomTest) {
    fprintf(stdout, "\n");
    fprintf(stdout, "-----------------------------------------------\n");
  }

  // Fault Test
  CHECK_PROTECTED_MT(myACM->SetVAD(false, true, (ACMVADMode) - 1));
  CHECK_PROTECTED_MT(myACM->SetVAD(false, true, (ACMVADMode) 4));

}

void APITest::CurrentCodec(char side) {
  auto myCodec = (side == 'A' ? _acmA : _acmB)->SendCodec();

  if (!_randomTest) {
    fprintf(stdout, "\n\n");
    fprintf(stdout, "Send codec in Side A\n");
    fprintf(stdout, "----------------------------\n");
    fprintf(stdout, "Name................. %s\n", myCodec->plname);
    fprintf(stdout, "Sampling Frequency... %d\n", myCodec->plfreq);
    fprintf(stdout, "Rate................. %d\n", myCodec->rate);
    fprintf(stdout, "Payload-type......... %d\n", myCodec->pltype);
    fprintf(stdout, "Packet-size.......... %d\n", myCodec->pacsize);
  }

  Wait(100);
}

void APITest::ChangeCodec(char side) {
  CodecInst myCodec;
  AudioCodingModule* myACM;
  uint8_t* codecCntr;
  bool* thereIsEncoder;
  bool* vad;
  bool* dtx;
  ACMVADMode* mode;
  Channel* myChannel;
  // Reset and Wait
  if (!_randomTest) {
    fprintf(stdout, "Reset Encoder Side A \n");
  }
  if (side == 'A') {
    myACM = _acmA.get();
    codecCntr = &_codecCntrA;
    {
      WriteLockScoped wl(_apiTestRWLock);
      thereIsEncoder = &_thereIsEncoderA;
    }
    vad = &_sendVADA;
    dtx = &_sendDTXA;
    mode = &_sendVADModeA;
    myChannel = _channel_A2B;
  } else {
    myACM = _acmB.get();
    codecCntr = &_codecCntrB;
    {
      WriteLockScoped wl(_apiTestRWLock);
      thereIsEncoder = &_thereIsEncoderB;
    }
    vad = &_sendVADB;
    dtx = &_sendDTXB;
    mode = &_sendVADModeB;
    myChannel = _channel_B2A;
  }

  Wait(100);

  // Register the next codec
  do {
    *codecCntr =
        (*codecCntr < AudioCodingModule::NumberOfCodecs() - 1) ?
            (*codecCntr + 1) : 0;

    if (*codecCntr == 0) {
      //printf("Initialize Sender Side A \n");
      {
        WriteLockScoped wl(_apiTestRWLock);
        *thereIsEncoder = false;
      }
      // After Initialization CN is lost, re-register them
      if (AudioCodingModule::Codec("CN", &myCodec, 8000, 1) >= 0) {
        CHECK_ERROR_MT(myACM->RegisterSendCodec(myCodec));
      }
      if (AudioCodingModule::Codec("CN", &myCodec, 16000, 1) >= 0) {
        CHECK_ERROR_MT(myACM->RegisterSendCodec(myCodec));
      }
      // VAD & DTX are disabled after initialization
      *vad = false;
      *dtx = false;
      _writeToFile = false;
    }

    AudioCodingModule::Codec(*codecCntr, &myCodec);
  } while (!STR_CASE_CMP(myCodec.plname, "CN")
      || !STR_CASE_CMP(myCodec.plname, "telephone-event")
      || !STR_CASE_CMP(myCodec.plname, "RED"));

  if (!_randomTest) {
    fprintf(stdout,"\n=====================================================\n");
    fprintf(stdout, "      Registering New Codec %s, %d kHz, %d kbps\n",
            myCodec.plname, myCodec.plfreq / 1000, myCodec.rate / 1000);
  }
  //std::cout<< std::flush;

  // NO DTX for supe-wideband codec at this point
  if (myCodec.plfreq == 32000) {
    *dtx = false;
    CHECK_ERROR_MT(myACM->SetVAD(*dtx, *vad, *mode));

  }

  CHECK_ERROR_MT(myACM->RegisterSendCodec(myCodec));
  myChannel->ResetStats();
  {
    WriteLockScoped wl(_apiTestRWLock);
    *thereIsEncoder = true;
  }
  Wait(500);
}

}  // namespace webrtc
