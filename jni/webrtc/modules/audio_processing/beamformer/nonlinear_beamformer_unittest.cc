/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// MSVC++ requires this to be set before any other includes to get M_PI.
#define _USE_MATH_DEFINES

#include "webrtc/modules/audio_processing/beamformer/nonlinear_beamformer.h"

#include <math.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/array_view.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"
#include "webrtc/modules/audio_processing/test/audio_buffer_tools.h"
#include "webrtc/modules/audio_processing/test/bitexactness_tools.h"

namespace webrtc {
namespace {

const int kChunkSizeMs = 10;
const int kSampleRateHz = 16000;

SphericalPointf AzimuthToSphericalPoint(float azimuth_radians) {
  return SphericalPointf(azimuth_radians, 0.f, 1.f);
}

void Verify(NonlinearBeamformer* bf, float target_azimuth_radians) {
  EXPECT_TRUE(bf->IsInBeam(AzimuthToSphericalPoint(target_azimuth_radians)));
  EXPECT_TRUE(bf->IsInBeam(AzimuthToSphericalPoint(
      target_azimuth_radians - NonlinearBeamformer::kHalfBeamWidthRadians +
      0.001f)));
  EXPECT_TRUE(bf->IsInBeam(AzimuthToSphericalPoint(
      target_azimuth_radians + NonlinearBeamformer::kHalfBeamWidthRadians -
      0.001f)));
  EXPECT_FALSE(bf->IsInBeam(AzimuthToSphericalPoint(
      target_azimuth_radians - NonlinearBeamformer::kHalfBeamWidthRadians -
      0.001f)));
  EXPECT_FALSE(bf->IsInBeam(AzimuthToSphericalPoint(
      target_azimuth_radians + NonlinearBeamformer::kHalfBeamWidthRadians +
      0.001f)));
}

void AimAndVerify(NonlinearBeamformer* bf, float target_azimuth_radians) {
  bf->AimAt(AzimuthToSphericalPoint(target_azimuth_radians));
  Verify(bf, target_azimuth_radians);
}

// Bitexactness test code.
const size_t kNumFramesToProcess = 1000;

void ProcessOneFrame(int sample_rate_hz,
                     AudioBuffer* capture_audio_buffer,
                     Beamformer<float>* beamformer) {
  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    capture_audio_buffer->SplitIntoFrequencyBands();
  }

  beamformer->ProcessChunk(*capture_audio_buffer->split_data_f(),
                           capture_audio_buffer->split_data_f());
  capture_audio_buffer->set_num_channels(1);

  if (sample_rate_hz > AudioProcessing::kSampleRate16kHz) {
    capture_audio_buffer->MergeFrequencyBands();
  }
}

int BeamformerSampleRate(int sample_rate_hz) {
  return (sample_rate_hz > AudioProcessing::kSampleRate16kHz
              ? AudioProcessing::kSampleRate16kHz
              : sample_rate_hz);
}

void RunBitExactnessTest(int sample_rate_hz,
                         const std::vector<Point>& array_geometry,
                         const SphericalPointf& target_direction,
                         rtc::ArrayView<const float> output_reference) {
  NonlinearBeamformer beamformer(array_geometry, target_direction);
  beamformer.Initialize(AudioProcessing::kChunkSizeMs,
                        BeamformerSampleRate(sample_rate_hz));

  const StreamConfig capture_config(sample_rate_hz, array_geometry.size(),
                                    false);
  AudioBuffer capture_buffer(
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames(), capture_config.num_channels(),
      capture_config.num_frames());
  test::InputAudioFile capture_file(
      test::GetApmCaptureTestVectorFileName(sample_rate_hz));
  std::vector<float> capture_input(capture_config.num_frames() *
                                   capture_config.num_channels());
  for (size_t frame_no = 0u; frame_no < kNumFramesToProcess; ++frame_no) {
    ReadFloatSamplesFromStereoFile(capture_config.num_frames(),
                                   capture_config.num_channels(), &capture_file,
                                   capture_input);

    test::CopyVectorToAudioBuffer(capture_config, capture_input,
                                  &capture_buffer);

    ProcessOneFrame(sample_rate_hz, &capture_buffer, &beamformer);
  }

  // Extract and verify the test results.
  std::vector<float> capture_output;
  test::ExtractVectorFromAudioBuffer(capture_config, &capture_buffer,
                                     &capture_output);

  const float kElementErrorBound = 1.f / static_cast<float>(1 << 15);

  // Compare the output with the reference. Only the first values of the output
  // from last frame processed are compared in order not having to specify all
  // preceeding frames as testvectors. As the algorithm being tested has a
  // memory, testing only the last frame implicitly also tests the preceeding
  // frames.
  EXPECT_TRUE(test::VerifyDeinterleavedArray(
      capture_config.num_frames(), capture_config.num_channels(),
      output_reference, capture_output, kElementErrorBound));
}

// TODO(peah): Add bitexactness tests for scenarios with more than 2 input
// channels.
std::vector<Point> CreateArrayGeometry(int variant) {
  std::vector<Point> array_geometry;
  switch (variant) {
    case 1:
      array_geometry.push_back(Point(-0.025f, 0.f, 0.f));
      array_geometry.push_back(Point(0.025f, 0.f, 0.f));
      break;
    case 2:
      array_geometry.push_back(Point(-0.035f, 0.f, 0.f));
      array_geometry.push_back(Point(0.035f, 0.f, 0.f));
      break;
    case 3:
      array_geometry.push_back(Point(-0.5f, 0.f, 0.f));
      array_geometry.push_back(Point(0.5f, 0.f, 0.f));
      break;
    default:
      RTC_CHECK(false);
  }
  return array_geometry;
}

const SphericalPointf TargetDirection1(0.4f * static_cast<float>(M_PI) / 2.f,
                                       0.f,
                                       1.f);
const SphericalPointf TargetDirection2(static_cast<float>(M_PI) / 2.f,
                                       1.f,
                                       2.f);

}  // namespace

TEST(NonlinearBeamformerTest, AimingModifiesBeam) {
  std::vector<Point> array_geometry;
  array_geometry.push_back(Point(-0.025f, 0.f, 0.f));
  array_geometry.push_back(Point(0.025f, 0.f, 0.f));
  NonlinearBeamformer bf(array_geometry);
  bf.Initialize(kChunkSizeMs, kSampleRateHz);
  // The default constructor parameter sets the target angle to PI / 2.
  Verify(&bf, static_cast<float>(M_PI) / 2.f);
  AimAndVerify(&bf, static_cast<float>(M_PI) / 3.f);
  AimAndVerify(&bf, 3.f * static_cast<float>(M_PI) / 4.f);
  AimAndVerify(&bf, static_cast<float>(M_PI) / 6.f);
  AimAndVerify(&bf, static_cast<float>(M_PI));
}

TEST(NonlinearBeamformerTest, InterfAnglesTakeAmbiguityIntoAccount) {
  {
    // For linear arrays there is ambiguity.
    std::vector<Point> array_geometry;
    array_geometry.push_back(Point(-0.1f, 0.f, 0.f));
    array_geometry.push_back(Point(0.f, 0.f, 0.f));
    array_geometry.push_back(Point(0.2f, 0.f, 0.f));
    NonlinearBeamformer bf(array_geometry);
    bf.Initialize(kChunkSizeMs, kSampleRateHz);
    EXPECT_EQ(2u, bf.interf_angles_radians_.size());
    EXPECT_FLOAT_EQ(M_PI / 2.f - bf.away_radians_,
                    bf.interf_angles_radians_[0]);
    EXPECT_FLOAT_EQ(M_PI / 2.f + bf.away_radians_,
                    bf.interf_angles_radians_[1]);
    bf.AimAt(AzimuthToSphericalPoint(bf.away_radians_ / 2.f));
    EXPECT_EQ(2u, bf.interf_angles_radians_.size());
    EXPECT_FLOAT_EQ(M_PI - bf.away_radians_ / 2.f,
                    bf.interf_angles_radians_[0]);
    EXPECT_FLOAT_EQ(3.f * bf.away_radians_ / 2.f, bf.interf_angles_radians_[1]);
  }
  {
    // For planar arrays with normal in the xy-plane there is ambiguity.
    std::vector<Point> array_geometry;
    array_geometry.push_back(Point(-0.1f, 0.f, 0.f));
    array_geometry.push_back(Point(0.f, 0.f, 0.f));
    array_geometry.push_back(Point(0.2f, 0.f, 0.f));
    array_geometry.push_back(Point(0.1f, 0.f, 0.2f));
    array_geometry.push_back(Point(0.f, 0.f, -0.1f));
    NonlinearBeamformer bf(array_geometry);
    bf.Initialize(kChunkSizeMs, kSampleRateHz);
    EXPECT_EQ(2u, bf.interf_angles_radians_.size());
    EXPECT_FLOAT_EQ(M_PI / 2.f - bf.away_radians_,
                    bf.interf_angles_radians_[0]);
    EXPECT_FLOAT_EQ(M_PI / 2.f + bf.away_radians_,
                    bf.interf_angles_radians_[1]);
    bf.AimAt(AzimuthToSphericalPoint(bf.away_radians_ / 2.f));
    EXPECT_EQ(2u, bf.interf_angles_radians_.size());
    EXPECT_FLOAT_EQ(M_PI - bf.away_radians_ / 2.f,
                    bf.interf_angles_radians_[0]);
    EXPECT_FLOAT_EQ(3.f * bf.away_radians_ / 2.f, bf.interf_angles_radians_[1]);
  }
  {
    // For planar arrays with normal not in the xy-plane there is no ambiguity.
    std::vector<Point> array_geometry;
    array_geometry.push_back(Point(0.f, 0.f, 0.f));
    array_geometry.push_back(Point(0.2f, 0.f, 0.f));
    array_geometry.push_back(Point(0.f, 0.1f, -0.2f));
    NonlinearBeamformer bf(array_geometry);
    bf.Initialize(kChunkSizeMs, kSampleRateHz);
    EXPECT_EQ(2u, bf.interf_angles_radians_.size());
    EXPECT_FLOAT_EQ(M_PI / 2.f - bf.away_radians_,
                    bf.interf_angles_radians_[0]);
    EXPECT_FLOAT_EQ(M_PI / 2.f + bf.away_radians_,
                    bf.interf_angles_radians_[1]);
    bf.AimAt(AzimuthToSphericalPoint(bf.away_radians_ / 2.f));
    EXPECT_EQ(2u, bf.interf_angles_radians_.size());
    EXPECT_FLOAT_EQ(-bf.away_radians_ / 2.f, bf.interf_angles_radians_[0]);
    EXPECT_FLOAT_EQ(3.f * bf.away_radians_ / 2.f, bf.interf_angles_radians_[1]);
  }
  {
    // For arrays which are not linear or planar there is no ambiguity.
    std::vector<Point> array_geometry;
    array_geometry.push_back(Point(0.f, 0.f, 0.f));
    array_geometry.push_back(Point(0.1f, 0.f, 0.f));
    array_geometry.push_back(Point(0.f, 0.2f, 0.f));
    array_geometry.push_back(Point(0.f, 0.f, 0.3f));
    NonlinearBeamformer bf(array_geometry);
    bf.Initialize(kChunkSizeMs, kSampleRateHz);
    EXPECT_EQ(2u, bf.interf_angles_radians_.size());
    EXPECT_FLOAT_EQ(M_PI / 2.f - bf.away_radians_,
                    bf.interf_angles_radians_[0]);
    EXPECT_FLOAT_EQ(M_PI / 2.f + bf.away_radians_,
                    bf.interf_angles_radians_[1]);
    bf.AimAt(AzimuthToSphericalPoint(bf.away_radians_ / 2.f));
    EXPECT_EQ(2u, bf.interf_angles_radians_.size());
    EXPECT_FLOAT_EQ(-bf.away_radians_ / 2.f, bf.interf_angles_radians_[0]);
    EXPECT_FLOAT_EQ(3.f * bf.away_radians_ / 2.f, bf.interf_angles_radians_[1]);
  }
}

// TODO(peah): Investigate why the nonlinear_beamformer.cc causes a DCHECK in
// this setup.
TEST(BeamformerBitExactnessTest,
     DISABLED_Stereo8kHz_ArrayGeometry1_TargetDirection1) {
  const float kOutputReference[] = {0.001318f, -0.001091f, 0.000990f,
                                    0.001318f, -0.001091f, 0.000990f};

  RunBitExactnessTest(AudioProcessing::kSampleRate8kHz, CreateArrayGeometry(1),
                      TargetDirection1, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo16kHz_ArrayGeometry1_TargetDirection1) {
  const float kOutputReference[] = {0.000064f, 0.000211f, 0.000075f,
                                    0.000064f, 0.000211f, 0.000075f};

  RunBitExactnessTest(AudioProcessing::kSampleRate16kHz, CreateArrayGeometry(1),
                      TargetDirection1, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo32kHz_ArrayGeometry1_TargetDirection1) {
  const float kOutputReference[] = {0.000183f, 0.000183f, 0.000183f,
                                    0.000183f, 0.000183f, 0.000183f};

  RunBitExactnessTest(AudioProcessing::kSampleRate32kHz, CreateArrayGeometry(1),
                      TargetDirection1, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo48kHz_ArrayGeometry1_TargetDirection1) {
  const float kOutputReference[] = {0.000155f, 0.000152f, 0.000159f,
                                    0.000155f, 0.000152f, 0.000159f};

  RunBitExactnessTest(AudioProcessing::kSampleRate48kHz, CreateArrayGeometry(1),
                      TargetDirection1, kOutputReference);
}

// TODO(peah): Investigate why the nonlinear_beamformer.cc causes a DCHECK in
// this setup.
TEST(BeamformerBitExactnessTest,
     DISABLED_Stereo8kHz_ArrayGeometry1_TargetDirection2) {
  const float kOutputReference[] = {0.001144f,  -0.001026f, 0.001074f,
                                    -0.016205f, -0.007324f, -0.015656f};

  RunBitExactnessTest(AudioProcessing::kSampleRate8kHz, CreateArrayGeometry(1),
                      TargetDirection2, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo16kHz_ArrayGeometry1_TargetDirection2) {
  const float kOutputReference[] = {0.001144f, -0.001026f, 0.001074f,
                                    0.001144f, -0.001026f, 0.001074f};

  RunBitExactnessTest(AudioProcessing::kSampleRate16kHz, CreateArrayGeometry(1),
                      TargetDirection2, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo32kHz_ArrayGeometry1_TargetDirection2) {
  const float kOutputReference[] = {0.000732f, -0.000397f, 0.000610f,
                                    0.000732f, -0.000397f, 0.000610f};

  RunBitExactnessTest(AudioProcessing::kSampleRate32kHz, CreateArrayGeometry(1),
                      TargetDirection2, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo48kHz_ArrayGeometry1_TargetDirection2) {
  const float kOutputReference[] = {0.000106f, -0.000464f, 0.000188f,
                                    0.000106f, -0.000464f, 0.000188f};

  RunBitExactnessTest(AudioProcessing::kSampleRate48kHz, CreateArrayGeometry(1),
                      TargetDirection2, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo8kHz_ArrayGeometry2_TargetDirection2) {
  const float kOutputReference[] = {-0.000649f, 0.000576f, -0.000148f,
                                    -0.000649f, 0.000576f, -0.000148f};

  RunBitExactnessTest(AudioProcessing::kSampleRate8kHz, CreateArrayGeometry(2),
                      TargetDirection2, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo16kHz_ArrayGeometry2_TargetDirection2) {
  const float kOutputReference[] = {0.000808f, -0.000695f, 0.000739f,
                                    0.000808f, -0.000695f, 0.000739f};

  RunBitExactnessTest(AudioProcessing::kSampleRate16kHz, CreateArrayGeometry(2),
                      TargetDirection2, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo32kHz_ArrayGeometry2_TargetDirection2) {
  const float kOutputReference[] = {0.000580f, -0.000183f, 0.000458f,
                                    0.000580f, -0.000183f, 0.000458f};

  RunBitExactnessTest(AudioProcessing::kSampleRate32kHz, CreateArrayGeometry(2),
                      TargetDirection2, kOutputReference);
}

TEST(BeamformerBitExactnessTest,
     Stereo48kHz_ArrayGeometry2_TargetDirection2) {
  const float kOutputReference[] = {0.000075f, -0.000288f, 0.000156f,
                                    0.000075f, -0.000288f, 0.000156f};

  RunBitExactnessTest(AudioProcessing::kSampleRate48kHz, CreateArrayGeometry(2),
                      TargetDirection2, kOutputReference);
}

// TODO(peah): Investigate why the nonlinear_beamformer.cc causes a DCHECK in
// this setup.
TEST(BeamformerBitExactnessTest,
     DISABLED_Stereo16kHz_ArrayGeometry3_TargetDirection1) {
  const float kOutputReference[] = {-0.000161f, 0.000171f, -0.000096f,
                                    0.001007f,  0.000427f, 0.000977f};

  RunBitExactnessTest(AudioProcessing::kSampleRate16kHz, CreateArrayGeometry(3),
                      TargetDirection1, kOutputReference);
}

}  // namespace webrtc
