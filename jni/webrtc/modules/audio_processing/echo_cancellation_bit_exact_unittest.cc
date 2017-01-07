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
#include "webrtc/modules/audio_processing/echo_cancellation_impl.h"
#include "webrtc/modules/audio_processing/test/audio_buffer_tools.h"
#include "webrtc/modules/audio_processing/test/bitexactness_tools.h"

namespace webrtc {
namespace {

const int kNumFramesToProcess = 100;

void SetupComponent(int sample_rate_hz,
                    EchoCancellation::SuppressionLevel suppression_level,
                    bool drift_compensation_enabled,
                    EchoCancellationImpl* echo_canceller) {
  echo_canceller->Initialize(sample_rate_hz, 1, 1, 1);
  EchoCancellation* ec = static_cast<EchoCancellation*>(echo_canceller);
  ec->Enable(true);
  ec->set_suppression_level(suppression_level);
  ec->enable_drift_compensation(drift_compensation_enabled);

  Config config;
  config.Set<DelayAgnostic>(new DelayAgnostic(true));
  config.Set<ExtendedFilter>(new ExtendedFilter(true));
  echo_canceller->SetExtraOptions(config);
}

void ProcessOneFrame(int sample_rate_hz,
                     int stream_delay_ms,
                     bool drift_compensation_enabled,
                     int stream_drift_samples,
                     AudioBuffer* render_audio_buffer,
                     AudioBuffer* capture_audio_buffer,
                     EchoCancellationImpl* echo_canceller) {
  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    render_audio_buffer->SplitIntoFrequencyBands();
    capture_audio_buffer->SplitIntoFrequencyBands();
  }

  echo_canceller->ProcessRenderAudio(render_audio_buffer);

  if (drift_compensation_enabled) {
    static_cast<EchoCancellation*>(echo_canceller)
        ->set_stream_drift_samples(stream_drift_samples);
  }

  echo_canceller->ProcessCaptureAudio(capture_audio_buffer, stream_delay_ms);

  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    capture_audio_buffer->MergeFrequencyBands();
  }
}

void RunBitexactnessTest(int sample_rate_hz,
                         size_t num_channels,
                         int stream_delay_ms,
                         bool drift_compensation_enabled,
                         int stream_drift_samples,
                         EchoCancellation::SuppressionLevel suppression_level,
                         bool stream_has_echo_reference,
                         const rtc::ArrayView<const float>& output_reference) {
  rtc::CriticalSection crit_render;
  rtc::CriticalSection crit_capture;
  EchoCancellationImpl echo_canceller(&crit_render, &crit_capture);
  SetupComponent(sample_rate_hz, suppression_level, drift_compensation_enabled,
                 &echo_canceller);

  const int samples_per_channel = rtc::CheckedDivExact(sample_rate_hz, 100);
  const StreamConfig render_config(sample_rate_hz, num_channels, false);
  AudioBuffer render_buffer(
      render_config.num_frames(), render_config.num_channels(),
      render_config.num_frames(), 1, render_config.num_frames());
  test::InputAudioFile render_file(
      test::GetApmRenderTestVectorFileName(sample_rate_hz));
  std::vector<float> render_input(samples_per_channel * num_channels);

  const StreamConfig capture_config(sample_rate_hz, num_channels, false);
  AudioBuffer capture_buffer(
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames(), 1, capture_config.num_frames());
  test::InputAudioFile capture_file(
      test::GetApmCaptureTestVectorFileName(sample_rate_hz));
  std::vector<float> capture_input(samples_per_channel * num_channels);

  for (int frame_no = 0; frame_no < kNumFramesToProcess; ++frame_no) {
    ReadFloatSamplesFromStereoFile(samples_per_channel, num_channels,
                                   &render_file, render_input);
    ReadFloatSamplesFromStereoFile(samples_per_channel, num_channels,
                                   &capture_file, capture_input);

    test::CopyVectorToAudioBuffer(render_config, render_input, &render_buffer);
    test::CopyVectorToAudioBuffer(capture_config, capture_input,
                                  &capture_buffer);

    ProcessOneFrame(sample_rate_hz, stream_delay_ms, drift_compensation_enabled,
                    stream_drift_samples, &render_buffer, &capture_buffer,
                    &echo_canceller);
  }

  // Extract and verify the test results.
  std::vector<float> capture_output;
  test::ExtractVectorFromAudioBuffer(capture_config, &capture_buffer,
                                     &capture_output);

  EXPECT_EQ(stream_has_echo_reference,
            static_cast<EchoCancellation*>(&echo_canceller)->stream_has_echo());

  // Compare the output with the reference. Only the first values of the output
  // from last frame processed are compared in order not having to specify all
  // preceeding frames as testvectors. As the algorithm being tested has a
  // memory, testing only the last frame implicitly also tests the preceeding
  // frames.
  const float kElementErrorBound = 1.0f / 32768.0f;
  EXPECT_TRUE(test::VerifyDeinterleavedArray(
      capture_config.num_frames(), capture_config.num_channels(),
      output_reference, capture_output, kElementErrorBound));
}

const bool kStreamHasEchoReference = false;

}  // namespace

// TODO(peah): Activate all these tests for ARM and ARM64 once the issue on the
// Chromium ARM and ARM64 boths have been identified. This is tracked in the
// issue https://bugs.chromium.org/p/webrtc/issues/detail?id=5711.

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono8kHz_HighLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono8kHz_HighLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.006622f, -0.002747f, 0.001587f};
  RunBitexactnessTest(8000, 1, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono16kHz_HighLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono16kHz_HighLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.006561f, -0.004608f, -0.002899f};
  RunBitexactnessTest(16000, 1, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono32kHz_HighLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono32kHz_HighLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.010162f, -0.009155f, -0.008301f};
  RunBitexactnessTest(32000, 1, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono48kHz_HighLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono48kHz_HighLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.009554f, -0.009857f, -0.009868f};
  RunBitexactnessTest(48000, 1, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono16kHz_LowLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono16kHz_LowLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.006561f, -0.004608f, -0.002899f};
  RunBitexactnessTest(16000, 1, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kLowSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono16kHz_ModerateLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono16kHz_ModerateLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.006561f, -0.004608f, -0.002899f};
  RunBitexactnessTest(16000, 1, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kModerateSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono16kHz_HighLevel_NoDrift_StreamDelay10) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono16kHz_HighLevel_NoDrift_StreamDelay10) {
#endif
  const float kOutputReference[] = {-0.006561f, -0.004608f, -0.002899f};
  RunBitexactnessTest(16000, 1, 10, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono16kHz_HighLevel_NoDrift_StreamDelay20) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono16kHz_HighLevel_NoDrift_StreamDelay20) {
#endif
  const float kOutputReference[] = {-0.006561f, -0.004608f, -0.002899f};
  RunBitexactnessTest(16000, 1, 20, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono16kHz_HighLevel_Drift0_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono16kHz_HighLevel_Drift0_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.006561f, -0.004608f, -0.002899f};
  RunBitexactnessTest(16000, 1, 0, true, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Mono16kHz_HighLevel_Drift5_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Mono16kHz_HighLevel_Drift5_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.006561f, -0.004608f, -0.002899f};
  RunBitexactnessTest(16000, 1, 0, true, 5,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Stereo8kHz_HighLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Stereo8kHz_HighLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.027359f, -0.015823f, -0.028488f,
                                    -0.027359f, -0.015823f, -0.028488f};
  RunBitexactnessTest(8000, 2, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Stereo16kHz_HighLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Stereo16kHz_HighLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.027298f, -0.015900f, -0.028107f,
                                    -0.027298f, -0.015900f, -0.028107f};
  RunBitexactnessTest(16000, 2, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Stereo32kHz_HighLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Stereo32kHz_HighLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {0.004547f, -0.004456f, -0.000946f,
                                    0.004547f, -0.004456f, -0.000946f};
  RunBitexactnessTest(32000, 2, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

#if !(defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM) || \
      defined(WEBRTC_ANDROID))
TEST(EchoCancellationBitExactnessTest,
     Stereo48kHz_HighLevel_NoDrift_StreamDelay0) {
#else
TEST(EchoCancellationBitExactnessTest,
     DISABLED_Stereo48kHz_HighLevel_NoDrift_StreamDelay0) {
#endif
  const float kOutputReference[] = {-0.003500f, -0.001894f, -0.003176f,
                                    -0.003500f, -0.001894f, -0.003176f};
  RunBitexactnessTest(48000, 2, 0, false, 0,
                      EchoCancellation::SuppressionLevel::kHighSuppression,
                      kStreamHasEchoReference, kOutputReference);
}

}  // namespace webrtc
