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
#include "webrtc/modules/audio_processing/voice_detection_impl.h"
#include "webrtc/modules/audio_processing/test/audio_buffer_tools.h"
#include "webrtc/modules/audio_processing/test/bitexactness_tools.h"

namespace webrtc {
namespace {

const int kNumFramesToProcess = 1000;

// Process one frame of data and produce the output.
void ProcessOneFrame(int sample_rate_hz,
                     AudioBuffer* audio_buffer,
                     VoiceDetectionImpl* voice_detection) {
  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    audio_buffer->SplitIntoFrequencyBands();
  }

  voice_detection->ProcessCaptureAudio(audio_buffer);
}

// Processes a specified amount of frames, verifies the results and reports
// any errors.
void RunBitexactnessTest(int sample_rate_hz,
                         size_t num_channels,
                         int frame_size_ms_reference,
                         bool stream_has_voice_reference,
                         VoiceDetection::Likelihood likelihood_reference) {
  rtc::CriticalSection crit_capture;
  VoiceDetectionImpl voice_detection(&crit_capture);
  voice_detection.Initialize(sample_rate_hz > 16000 ? 16000 : sample_rate_hz);
  voice_detection.Enable(true);

  int samples_per_channel = rtc::CheckedDivExact(sample_rate_hz, 100);
  const StreamConfig capture_config(sample_rate_hz, num_channels, false);
  AudioBuffer capture_buffer(
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames());
  test::InputAudioFile capture_file(
      test::GetApmCaptureTestVectorFileName(sample_rate_hz));
  std::vector<float> capture_input(samples_per_channel * num_channels);
  for (int frame_no = 0; frame_no < kNumFramesToProcess; ++frame_no) {
    ReadFloatSamplesFromStereoFile(samples_per_channel, num_channels,
                                   &capture_file, capture_input);

    test::CopyVectorToAudioBuffer(capture_config, capture_input,
                                  &capture_buffer);

    ProcessOneFrame(sample_rate_hz, &capture_buffer, &voice_detection);
  }

  int frame_size_ms = voice_detection.frame_size_ms();
  bool stream_has_voice = voice_detection.stream_has_voice();
  VoiceDetection::Likelihood likelihood = voice_detection.likelihood();

  // Compare the outputs to the references.
  EXPECT_EQ(frame_size_ms_reference, frame_size_ms);
  EXPECT_EQ(stream_has_voice_reference, stream_has_voice);
  EXPECT_EQ(likelihood_reference, likelihood);
}

const int kFrameSizeMsReference = 10;
const bool kStreamHasVoiceReference = true;
const VoiceDetection::Likelihood kLikelihoodReference =
    VoiceDetection::kLowLikelihood;

}  // namespace

TEST(VoiceDetectionBitExactnessTest, Mono8kHz) {
  RunBitexactnessTest(8000, 1, kFrameSizeMsReference, kStreamHasVoiceReference,
                      kLikelihoodReference);
}

TEST(VoiceDetectionBitExactnessTest, Mono16kHz) {
  RunBitexactnessTest(16000, 1, kFrameSizeMsReference, kStreamHasVoiceReference,
                      kLikelihoodReference);
}

TEST(VoiceDetectionBitExactnessTest, Mono32kHz) {
  RunBitexactnessTest(32000, 1, kFrameSizeMsReference, kStreamHasVoiceReference,
                      kLikelihoodReference);
}

TEST(VoiceDetectionBitExactnessTest, Mono48kHz) {
  RunBitexactnessTest(48000, 1, kFrameSizeMsReference, kStreamHasVoiceReference,
                      kLikelihoodReference);
}

TEST(VoiceDetectionBitExactnessTest, Stereo8kHz) {
  RunBitexactnessTest(8000, 2, kFrameSizeMsReference, kStreamHasVoiceReference,
                      kLikelihoodReference);
}

TEST(VoiceDetectionBitExactnessTest, Stereo16kHz) {
  RunBitexactnessTest(16000, 2, kFrameSizeMsReference, kStreamHasVoiceReference,
                      kLikelihoodReference);
}

TEST(VoiceDetectionBitExactnessTest, Stereo32kHz) {
  RunBitexactnessTest(32000, 2, kFrameSizeMsReference, kStreamHasVoiceReference,
                      kLikelihoodReference);
}

TEST(VoiceDetectionBitExactnessTest, Stereo48kHz) {
  RunBitexactnessTest(48000, 2, kFrameSizeMsReference, kStreamHasVoiceReference,
                      kLikelihoodReference);
}

}  // namespace webrtc
