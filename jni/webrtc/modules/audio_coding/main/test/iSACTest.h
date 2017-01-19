/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_ISACTEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_ISACTEST_H_

#include <string.h>

#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/main/interface/audio_coding_module.h"
#include "webrtc/modules/audio_coding/main/test/ACMTest.h"
#include "webrtc/modules/audio_coding/main/test/Channel.h"
#include "webrtc/modules/audio_coding/main/test/PCMFile.h"
#include "webrtc/modules/audio_coding/main/test/utility.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

#define MAX_FILE_NAME_LENGTH_BYTE 500
#define NO_OF_CLIENTS             15

namespace webrtc {

struct ACMTestISACConfig {
  int32_t currentRateBitPerSec;
  int16_t currentFrameSizeMsec;
  uint32_t maxRateBitPerSec;
  int16_t maxPayloadSizeByte;
  int16_t encodingMode;
  uint32_t initRateBitPerSec;
  int16_t initFrameSizeInMsec;
  bool enforceFrameSize;
};

class ISACTest : public ACMTest {
 public:
  explicit ISACTest(int testMode);
  ~ISACTest();

  void Perform();
 private:
  void Setup();

  void Run10ms();

  void EncodeDecode(int testNr, ACMTestISACConfig& wbISACConfig,
                    ACMTestISACConfig& swbISACConfig);

  void SwitchingSamplingRate(int testNr, int maxSampRateChange);

  scoped_ptr<AudioCodingModule> _acmA;
  scoped_ptr<AudioCodingModule> _acmB;

  scoped_ptr<Channel> _channel_A2B;
  scoped_ptr<Channel> _channel_B2A;

  PCMFile _inFileA;
  PCMFile _inFileB;

  PCMFile _outFileA;
  PCMFile _outFileB;

  uint8_t _idISAC16kHz;
  uint8_t _idISAC32kHz;
  CodecInst _paramISAC16kHz;
  CodecInst _paramISAC32kHz;

  std::string file_name_swb_;

  ACMTestTimer _myTimer;
  int _testMode;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_ISACTEST_H_
