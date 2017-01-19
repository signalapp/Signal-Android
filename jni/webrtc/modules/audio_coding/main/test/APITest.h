/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_APITEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_APITEST_H_

#include "webrtc/modules/audio_coding/main/interface/audio_coding_module.h"
#include "webrtc/modules/audio_coding/main/test/ACMTest.h"
#include "webrtc/modules/audio_coding/main/test/Channel.h"
#include "webrtc/modules/audio_coding/main/test/PCMFile.h"
#include "webrtc/modules/audio_coding/main/test/utility.h"
#include "webrtc/system_wrappers/interface/event_wrapper.h"
#include "webrtc/system_wrappers/interface/rw_lock_wrapper.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

namespace webrtc {

class Config;

enum APITESTAction {
  TEST_CHANGE_CODEC_ONLY = 0,
  DTX_TEST = 1
};

class APITest : public ACMTest {
 public:
  explicit APITest(const Config& config);
  ~APITest();

  void Perform();
 private:
  int16_t SetUp();

  static bool PushAudioThreadA(void* obj);
  static bool PullAudioThreadA(void* obj);
  static bool ProcessThreadA(void* obj);
  static bool APIThreadA(void* obj);

  static bool PushAudioThreadB(void* obj);
  static bool PullAudioThreadB(void* obj);
  static bool ProcessThreadB(void* obj);
  static bool APIThreadB(void* obj);

  void CheckVADStatus(char side);

  // Set Min delay, get delay, playout timestamp
  void TestDelay(char side);

  // Unregister a codec & register again.
  void TestRegisteration(char side);

  // Playout Mode, background noise mode.
  // Receiver Frequency, playout frequency.
  void TestPlayout(char receiveSide);

  //
  void TestSendVAD(char side);

  void CurrentCodec(char side);

  void ChangeCodec(char side);

  void Wait(uint32_t waitLengthMs);

  void RunTest(char thread);

  bool PushAudioRunA();
  bool PullAudioRunA();
  bool ProcessRunA();
  bool APIRunA();

  bool PullAudioRunB();
  bool PushAudioRunB();
  bool ProcessRunB();
  bool APIRunB();

  //--- ACMs
  scoped_ptr<AudioCodingModule> _acmA;
  scoped_ptr<AudioCodingModule> _acmB;

  //--- Channels
  Channel* _channel_A2B;
  Channel* _channel_B2A;

  //--- I/O files
  // A
  PCMFile _inFileA;
  PCMFile _outFileA;
  // B
  PCMFile _outFileB;
  PCMFile _inFileB;

  //--- I/O params
  // A
  int32_t _outFreqHzA;
  // B
  int32_t _outFreqHzB;

  // Should we write to file.
  // we might skip writing to file if we
  // run the test for a long time.
  bool _writeToFile;
  //--- Events
  // A
  EventWrapper* _pullEventA;      // pulling data from ACM
  EventWrapper* _pushEventA;      // pushing data to ACM
  EventWrapper* _processEventA;   // process
  EventWrapper* _apiEventA;       // API calls
  // B
  EventWrapper* _pullEventB;      // pulling data from ACM
  EventWrapper* _pushEventB;      // pushing data to ACM
  EventWrapper* _processEventB;   // process
  EventWrapper* _apiEventB;       // API calls

  // keep track of the codec in either side.
  uint8_t _codecCntrA;
  uint8_t _codecCntrB;

  // Is set to true if there is no encoder in either side
  bool _thereIsEncoderA;
  bool _thereIsEncoderB;
  bool _thereIsDecoderA;
  bool _thereIsDecoderB;

  bool _sendVADA;
  bool _sendDTXA;
  ACMVADMode _sendVADModeA;

  bool _sendVADB;
  bool _sendDTXB;
  ACMVADMode _sendVADModeB;

  int32_t _minDelayA;
  int32_t _minDelayB;
  bool _payloadUsed[32];

  AudioPlayoutMode _playoutModeA;
  AudioPlayoutMode _playoutModeB;

  bool _verbose;

  int _dotPositionA;
  int _dotMoveDirectionA;
  int _dotPositionB;
  int _dotMoveDirectionB;

  char _movingDot[41];

  DTMFDetector* _dtmfCallback;
  VADCallback* _vadCallbackA;
  VADCallback* _vadCallbackB;
  RWLockWrapper& _apiTestRWLock;
  bool _randomTest;
  int _testNumA;
  int _testNumB;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_APITEST_H_
