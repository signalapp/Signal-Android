/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_TWOWAYCOMMUNICATION_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_TWOWAYCOMMUNICATION_H_

#include "webrtc/modules/audio_coding/main/interface/audio_coding_module.h"
#include "webrtc/modules/audio_coding/main/test/ACMTest.h"
#include "webrtc/modules/audio_coding/main/test/Channel.h"
#include "webrtc/modules/audio_coding/main/test/PCMFile.h"
#include "webrtc/modules/audio_coding/main/test/utility.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

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

  scoped_ptr<AudioCodingModule> _acmA;
  scoped_ptr<AudioCodingModule> _acmB;

  scoped_ptr<AudioCodingModule> _acmRefA;
  scoped_ptr<AudioCodingModule> _acmRefB;

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

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_TWOWAYCOMMUNICATION_H_
