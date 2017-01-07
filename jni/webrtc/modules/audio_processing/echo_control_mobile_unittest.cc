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
#include "webrtc/modules/audio_processing/echo_control_mobile_impl.h"
#include "webrtc/modules/audio_processing/test/audio_buffer_tools.h"
#include "webrtc/modules/audio_processing/test/bitexactness_tools.h"

namespace webrtc {
namespace {

// TODO(peah): Increase the number of frames to proces when the issue of
// non repeatable test results have been found.
const int kNumFramesToProcess = 200;

void SetupComponent(int sample_rate_hz,
                    EchoControlMobile::RoutingMode routing_mode,
                    bool comfort_noise_enabled,
                    EchoControlMobileImpl* echo_control_mobile) {
  echo_control_mobile->Initialize(
      sample_rate_hz > 16000 ? 16000 : sample_rate_hz, 1, 1);
  EchoControlMobile* ec = static_cast<EchoControlMobile*>(echo_control_mobile);
  ec->Enable(true);
  ec->set_routing_mode(routing_mode);
  ec->enable_comfort_noise(comfort_noise_enabled);
}

void ProcessOneFrame(int sample_rate_hz,
                     int stream_delay_ms,
                     AudioBuffer* render_audio_buffer,
                     AudioBuffer* capture_audio_buffer,
                     EchoControlMobileImpl* echo_control_mobile) {
  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    render_audio_buffer->SplitIntoFrequencyBands();
    capture_audio_buffer->SplitIntoFrequencyBands();
  }

  echo_control_mobile->ProcessRenderAudio(render_audio_buffer);
  echo_control_mobile->ProcessCaptureAudio(capture_audio_buffer,
                                           stream_delay_ms);

  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    capture_audio_buffer->MergeFrequencyBands();
  }
}

void RunBitexactnessTest(int sample_rate_hz,
                         size_t num_channels,
                         int stream_delay_ms,
                         EchoControlMobile::RoutingMode routing_mode,
                         bool comfort_noise_enabled,
                         const rtc::ArrayView<const float>& output_reference) {
  rtc::CriticalSection crit_render;
  rtc::CriticalSection crit_capture;
  EchoControlMobileImpl echo_control_mobile(&crit_render, &crit_capture);
  SetupComponent(sample_rate_hz, routing_mode, comfort_noise_enabled,
                 &echo_control_mobile);

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

    ProcessOneFrame(sample_rate_hz, stream_delay_ms, &render_buffer,
                    &capture_buffer, &echo_control_mobile);
  }

  // Extract and verify the test results.
  std::vector<float> capture_output;
  test::ExtractVectorFromAudioBuffer(capture_config, &capture_buffer,
                                     &capture_output);

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

}  // namespace

// TODO(peah): Renable once the integer overflow issue in aecm_core.c:932:69
// has been solved.
TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono8kHz_LoudSpeakerPhone_CngOn_StreamDelay0) {
  const float kOutputReference[] = {0.005280f, 0.002380f, -0.000427f};

  RunBitexactnessTest(8000, 1, 0,
                      EchoControlMobile::RoutingMode::kLoudSpeakerphone, true,
                      kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono16kHz_LoudSpeakerPhone_CngOn_StreamDelay0) {
  const float kOutputReference[] = {0.003601f, 0.002991f, 0.001923f};
  RunBitexactnessTest(16000, 1, 0,
                      EchoControlMobile::RoutingMode::kLoudSpeakerphone, true,
                      kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono32kHz_LoudSpeakerPhone_CngOn_StreamDelay0) {
  const float kOutputReference[] = {0.002258f, 0.002899f, 0.003906f};

  RunBitexactnessTest(32000, 1, 0,
                      EchoControlMobile::RoutingMode::kLoudSpeakerphone, true,
                      kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono48kHz_LoudSpeakerPhone_CngOn_StreamDelay0) {
  const float kOutputReference[] = {-0.000046f, 0.000041f, 0.000249f};

  RunBitexactnessTest(48000, 1, 0,
                      EchoControlMobile::RoutingMode::kLoudSpeakerphone, true,
                      kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono16kHz_LoudSpeakerPhone_CngOff_StreamDelay0) {
  const float kOutputReference[] = {0.000000f, 0.000000f, 0.000000f};

  RunBitexactnessTest(16000, 1, 0,
                      EchoControlMobile::RoutingMode::kLoudSpeakerphone, false,
                      kOutputReference);
}

// TODO(peah): Renable once the integer overflow issue in aecm_core.c:932:69
// has been solved.
TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono16kHz_LoudSpeakerPhone_CngOn_StreamDelay5) {
  const float kOutputReference[] = {0.003693f, 0.002930f, 0.001801f};

  RunBitexactnessTest(16000, 1, 5,
                      EchoControlMobile::RoutingMode::kLoudSpeakerphone, true,
                      kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     Mono16kHz_LoudSpeakerPhone_CngOn_StreamDelay10) {
  const float kOutputReference[] = {-0.002411f, -0.002716f, -0.002747f};

  RunBitexactnessTest(16000, 1, 10,
                      EchoControlMobile::RoutingMode::kLoudSpeakerphone, true,
                      kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono16kHz_QuietEarpieceOrHeadset_CngOn_StreamDelay0) {
  const float kOutputReference[] = {0.000397f, 0.000000f, -0.000305f};

  RunBitexactnessTest(16000, 1, 0,
                      EchoControlMobile::RoutingMode::kQuietEarpieceOrHeadset,
                      true, kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono16kHz_Earpiece_CngOn_StreamDelay0) {
  const float kOutputReference[] = {0.002167f, 0.001617f, 0.001038f};

  RunBitexactnessTest(16000, 1, 0, EchoControlMobile::RoutingMode::kEarpiece,
                      true, kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono16kHz_LoudEarpiece_CngOn_StreamDelay0) {
  const float kOutputReference[] = {0.003540f, 0.002899f, 0.001862f};

  RunBitexactnessTest(16000, 1, 0,
                      EchoControlMobile::RoutingMode::kLoudEarpiece, true,
                      kOutputReference);
}

TEST(EchoControlMobileBitExactnessTest,
     DISABLED_Mono16kHz_SpeakerPhone_CngOn_StreamDelay0) {
  const float kOutputReference[] = {0.003632f, 0.003052f, 0.001984f};

  RunBitexactnessTest(16000, 1, 0,
                      EchoControlMobile::RoutingMode::kSpeakerphone, true,
                      kOutputReference);
}

}  // namespace webrtc
