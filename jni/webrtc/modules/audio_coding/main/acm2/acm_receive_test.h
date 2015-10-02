/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_RECEIVE_TEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_RECEIVE_TEST_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/system_wrappers/interface/clock.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

namespace webrtc {
class AudioCodingModule;
struct CodecInst;

namespace test {
class AudioSink;
class PacketSource;

class AcmReceiveTest {
 public:
  AcmReceiveTest(PacketSource* packet_source,
                 AudioSink* audio_sink,
                 int output_freq_hz);
  virtual ~AcmReceiveTest() {}

  // Registers the codecs with default parameters from ACM.
  void RegisterDefaultCodecs();

  // Registers codecs with payload types matching the pre-encoded NetEq test
  // files.
  void RegisterNetEqTestCodecs();

  // Runs the test and returns true if successful.
  void Run();

 private:
  SimulatedClock clock_;
  scoped_ptr<AudioCodingModule> acm_;
  PacketSource* packet_source_;
  AudioSink* audio_sink_;
  const int output_freq_hz_;

  DISALLOW_COPY_AND_ASSIGN(AcmReceiveTest);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_RECEIVE_TEST_H_
