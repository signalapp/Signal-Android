/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/array_view.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"
#include "webrtc/modules/audio_processing/level_estimator_impl.h"
#include "webrtc/modules/audio_processing/test/audio_buffer_tools.h"
#include "webrtc/modules/audio_processing/test/bitexactness_tools.h"

namespace webrtc {
namespace {

const int kNumFramesToProcess = 1000;

// Processes a specified amount of frames, verifies the results and reports
// any errors.
void RunBitexactnessTest(int sample_rate_hz,
                         size_t num_channels,
                         int rms_reference) {
  rtc::CriticalSection crit_capture;
  LevelEstimatorImpl level_estimator(&crit_capture);
  level_estimator.Initialize();
  level_estimator.Enable(true);

  int samples_per_channel = rtc::CheckedDivExact(sample_rate_hz, 100);
  StreamConfig capture_config(sample_rate_hz, num_channels, false);
  AudioBuffer capture_buffer(
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames());

  test::InputAudioFile capture_file(
      test::GetApmCaptureTestVectorFileName(sample_rate_hz));
  std::vector<float> capture_input(samples_per_channel * num_channels);
  for (size_t frame_no = 0; frame_no < kNumFramesToProcess; ++frame_no) {
    ReadFloatSamplesFromStereoFile(samples_per_channel, num_channels,
                                   &capture_file, capture_input);

    test::CopyVectorToAudioBuffer(capture_config, capture_input,
                                  &capture_buffer);

    level_estimator.ProcessStream(&capture_buffer);
  }

  // Extract test results.
  int rms = level_estimator.RMS();

  // Compare the output to the reference.
  EXPECT_EQ(rms_reference, rms);
}

}  // namespace

TEST(LevelEstimatorBitExactnessTest, Mono8kHz) {
  const int kRmsReference = 31;

  RunBitexactnessTest(8000, 1, kRmsReference);
}

TEST(LevelEstimatorBitExactnessTest, Mono16kHz) {
  const int kRmsReference = 31;

  RunBitexactnessTest(16000, 1, kRmsReference);
}

TEST(LevelEstimatorBitExactnessTest, Mono32kHz) {
  const int kRmsReference = 31;

  RunBitexactnessTest(32000, 1, kRmsReference);
}

TEST(LevelEstimatorBitExactnessTest, Mono48kHz) {
  const int kRmsReference = 31;

  RunBitexactnessTest(48000, 1, kRmsReference);
}

TEST(LevelEstimatorBitExactnessTest, Stereo16kHz) {
  const int kRmsReference = 30;

  RunBitexactnessTest(16000, 2, kRmsReference);
}

}  // namespace webrtc
