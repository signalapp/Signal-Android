/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_TEST_WAV_BASED_SIMULATOR_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_TEST_WAV_BASED_SIMULATOR_H_

#include <vector>

#include "webrtc/modules/audio_processing/test/audio_processing_simulator.h"

#include "webrtc/base/constructormagic.h"

namespace webrtc {
namespace test {

// Used to perform an audio processing simulation from wav files.
class WavBasedSimulator final : public AudioProcessingSimulator {
 public:
  explicit WavBasedSimulator(const SimulationSettings& settings)
      : AudioProcessingSimulator(settings) {}
  virtual ~WavBasedSimulator() {}

  // Processes the WAV input.
  void Process() override;

 private:
  enum SimulationEventType {
    kProcessStream,
    kProcessReverseStream,
  };

  void Initialize();
  bool HandleProcessStreamCall();
  bool HandleProcessReverseStreamCall();
  void PrepareProcessStreamCall();
  void PrepareReverseProcessStreamCall();
  std::vector<SimulationEventType> GetDefaultEventChain() const;

  std::vector<SimulationEventType> call_chain_;
  int last_specified_microphone_level_ = 100;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(WavBasedSimulator);
};

}  // namespace test
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_TEST_WAV_BASED_SIMULATOR_H_
