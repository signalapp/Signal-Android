/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_TEST_TESTREDFEC_H_
#define WEBRTC_MODULES_AUDIO_CODING_TEST_TESTREDFEC_H_

#include <memory>
#include <string>

#include "webrtc/modules/audio_coding/test/ACMTest.h"
#include "webrtc/modules/audio_coding/test/Channel.h"
#include "webrtc/modules/audio_coding/test/PCMFile.h"

namespace webrtc {

class Config;

class TestRedFec : public ACMTest {
 public:
  explicit TestRedFec();
  ~TestRedFec();

  void Perform();
 private:
  // The default value of '-1' indicates that the registration is based only on
  // codec name and a sampling frequency matching is not required. This is
  // useful for codecs which support several sampling frequency.
  int16_t RegisterSendCodec(char side, const char* codecName,
                            int32_t sampFreqHz = -1);
  void Run();
  void OpenOutFile(int16_t testNumber);
  int32_t SetVAD(bool enableDTX, bool enableVAD, ACMVADMode vadMode);
  std::unique_ptr<AudioCodingModule> _acmA;
  std::unique_ptr<AudioCodingModule> _acmB;

  Channel* _channelA2B;

  PCMFile _inFileA;
  PCMFile _outFileB;
  int16_t _testCntr;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_TEST_TESTREDFEC_H_
