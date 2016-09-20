/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_TEST_TWOWAYCOMMUNICATION_H_
#define WEBRTC_MODULES_AUDIO_CODING_TEST_TWOWAYCOMMUNICATION_H_

#include <memory>

#include "webrtc/modules/audio_coding/include/audio_coding_module.h"
#include "webrtc/modules/audio_coding/test/ACMTest.h"
#include "webrtc/modules/audio_coding/test/Channel.h"
#include "webrtc/modules/audio_coding/test/PCMFile.h"
#include "webrtc/modules/audio_coding/test/utility.h"

namespace webrtc {

class TwoWayCommunication : public ACMTest {
 public:
  explicit TwoWayCommunication(int testMode);
  ~TwoWayCommunication();

  void Perform();
 private:
  void ChooseCodec(uint8_t* codecID_A, uint8_t* codecID_B);
  void SetUp();
  void SetUpAutotest();

  std::unique_ptr<AudioCodingModule> _acmA;
  std::unique_ptr<AudioCodingModule> _acmB;

  std::unique_ptr<AudioCodingModule> _acmRefA;
  std::unique_ptr<AudioCodingModule> _acmRefB;

  Channel* _channel_A2B;
  Channel* _channel_B2A;

  Channel* _channelRef_A2B;
  Channel* _channelRef_B2A;

  PCMFile _inFileA;
  PCMFile _inFileB;

  PCMFile _outFileA;
  PCMFile _outFileB;

  PCMFile _outFileRefA;
  PCMFile _outFileRefB;

  int _testMode;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_TEST_TWOWAYCOMMUNICATION_H_
