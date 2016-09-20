/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#define _USE_MATH_DEFINES

#include "webrtc/modules/audio_processing/beamformer/covariance_matrix_generator.h"

#include <cmath>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/beamformer/matrix_test_helpers.h"

namespace webrtc {

using std::complex;

TEST(CovarianceMatrixGeneratorTest, TestUniformCovarianceMatrix2Mics) {
  const float kWaveNumber = 0.5775f;
  const int kNumberMics = 2;
  const float kMicSpacing = 0.05f;
  const float kTolerance = 0.0001f;
  std::vector<Point> geometry;
  float first_mic = (kNumberMics - 1) * kMicSpacing / 2.f;
  for (int i = 0; i < kNumberMics; ++i) {
    geometry.push_back(Point(i * kMicSpacing - first_mic, 0.f, 0.f));
  }
  ComplexMatrix<float> actual_covariance_matrix(kNumberMics, kNumberMics);
  CovarianceMatrixGenerator::UniformCovarianceMatrix(kWaveNumber,
                                                     geometry,
                                                     &actual_covariance_matrix);

  complex<float>* const* actual_els = actual_covariance_matrix.elements();

  EXPECT_NEAR(actual_els[0][0].real(), 1.f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].real(), 0.9998f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].real(), 0.9998f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].real(), 1.f, kTolerance);

  EXPECT_NEAR(actual_els[0][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].imag(), 0.f, kTolerance);
}

TEST(CovarianceMatrixGeneratorTest, TestUniformCovarianceMatrix3Mics) {
  const float kWaveNumber = 10.3861f;
  const int kNumberMics = 3;
  const float kMicSpacing = 0.04f;
  const float kTolerance = 0.0001f;
  std::vector<Point> geometry;
  float first_mic = (kNumberMics - 1) * kMicSpacing / 2.f;
  for (int i = 0; i < kNumberMics; ++i) {
    geometry.push_back(Point(i * kMicSpacing - first_mic, 0.f, 0.f));
  }
  ComplexMatrix<float> actual_covariance_matrix(kNumberMics, kNumberMics);
  CovarianceMatrixGenerator::UniformCovarianceMatrix(kWaveNumber,
                                                     geometry,
                                                     &actual_covariance_matrix);

  complex<float>* const* actual_els = actual_covariance_matrix.elements();

  EXPECT_NEAR(actual_els[0][0].real(), 1.f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].real(), 0.9573f, kTolerance);
  EXPECT_NEAR(actual_els[0][2].real(), 0.8347f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].real(), 0.9573f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].real(), 1.f, kTolerance);
  EXPECT_NEAR(actual_els[1][2].real(), 0.9573f, kTolerance);
  EXPECT_NEAR(actual_els[2][0].real(), 0.8347f, kTolerance);
  EXPECT_NEAR(actual_els[2][1].real(), 0.9573f, kTolerance);
  EXPECT_NEAR(actual_els[2][2].real(), 1.f, kTolerance);

  EXPECT_NEAR(actual_els[0][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[0][2].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][2].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[2][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[2][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[2][2].imag(), 0.f, kTolerance);
}

TEST(CovarianceMatrixGeneratorTest, TestUniformCovarianceMatrix3DArray) {
  const float kWaveNumber = 1.2345f;
  const int kNumberMics = 4;
  const float kTolerance = 0.0001f;
  std::vector<Point> geometry;
  geometry.push_back(Point(-0.025f, -0.05f, -0.075f));
  geometry.push_back(Point(0.075f, -0.05f, -0.075f));
  geometry.push_back(Point(-0.025f, 0.15f, -0.075f));
  geometry.push_back(Point(-0.025f, -0.05f, 0.225f));
  ComplexMatrix<float> actual_covariance_matrix(kNumberMics, kNumberMics);
  CovarianceMatrixGenerator::UniformCovarianceMatrix(kWaveNumber,
                                                     geometry,
                                                     &actual_covariance_matrix);

  complex<float>* const* actual_els = actual_covariance_matrix.elements();

  EXPECT_NEAR(actual_els[0][0].real(), 1.f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].real(), 0.9962f, kTolerance);
  EXPECT_NEAR(actual_els[0][2].real(), 0.9848f, kTolerance);
  EXPECT_NEAR(actual_els[0][3].real(), 0.9660f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].real(), 0.9962f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].real(), 1.f, kTolerance);
  EXPECT_NEAR(actual_els[1][2].real(), 0.9810f, kTolerance);
  EXPECT_NEAR(actual_els[1][3].real(), 0.9623f, kTolerance);
  EXPECT_NEAR(actual_els[2][0].real(), 0.9848f, kTolerance);
  EXPECT_NEAR(actual_els[2][1].real(), 0.9810f, kTolerance);
  EXPECT_NEAR(actual_els[2][2].real(), 1.f, kTolerance);
  EXPECT_NEAR(actual_els[2][3].real(), 0.9511f, kTolerance);
  EXPECT_NEAR(actual_els[3][0].real(), 0.9660f, kTolerance);
  EXPECT_NEAR(actual_els[3][1].real(), 0.9623f, kTolerance);
  EXPECT_NEAR(actual_els[3][2].real(), 0.9511f, kTolerance);
  EXPECT_NEAR(actual_els[3][3].real(), 1.f, kTolerance);

  EXPECT_NEAR(actual_els[0][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[0][2].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[0][3].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][2].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][3].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[2][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[2][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[2][2].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[2][3].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[3][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[3][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[3][2].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[3][3].imag(), 0.f, kTolerance);
}

TEST(CovarianceMatrixGeneratorTest, TestAngledCovarianceMatrix2Mics) {
  const float kSpeedOfSound = 340;
  const float kAngle = static_cast<float>(M_PI) / 4.f;
  const float kFrequencyBin = 6;
  const float kFftSize = 512;
  const int kNumberFrequencyBins = 257;
  const int kSampleRate = 16000;
  const int kNumberMics = 2;
  const float kMicSpacing = 0.04f;
  const float kTolerance = 0.0001f;
  std::vector<Point> geometry;
  float first_mic = (kNumberMics - 1) * kMicSpacing / 2.f;
  for (int i = 0; i < kNumberMics; ++i) {
    geometry.push_back(Point(i * kMicSpacing - first_mic, 0.f, 0.f));
  }
  ComplexMatrix<float> actual_covariance_matrix(kNumberMics, kNumberMics);
  CovarianceMatrixGenerator::AngledCovarianceMatrix(kSpeedOfSound,
                                                    kAngle,
                                                    kFrequencyBin,
                                                    kFftSize,
                                                    kNumberFrequencyBins,
                                                    kSampleRate,
                                                    geometry,
                                                    &actual_covariance_matrix);

  complex<float>* const* actual_els = actual_covariance_matrix.elements();

  EXPECT_NEAR(actual_els[0][0].real(), 0.5f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].real(), 0.4976f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].real(), 0.4976f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].real(), 0.5f, kTolerance);

  EXPECT_NEAR(actual_els[0][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].imag(), 0.0489f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].imag(), -0.0489f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].imag(), 0.f, kTolerance);
}

TEST(CovarianceMatrixGeneratorTest, TestAngledCovarianceMatrix3Mics) {
  const float kSpeedOfSound = 340;
  const float kAngle = static_cast<float>(M_PI) / 4.f;
  const float kFrequencyBin = 9;
  const float kFftSize = 512;
  const int kNumberFrequencyBins = 257;
  const int kSampleRate = 42000;
  const int kNumberMics = 3;
  const float kMicSpacing = 0.05f;
  const float kTolerance = 0.0001f;
  std::vector<Point> geometry;
  float first_mic = (kNumberMics - 1) * kMicSpacing / 2.f;
  for (int i = 0; i < kNumberMics; ++i) {
    geometry.push_back(Point(i * kMicSpacing - first_mic, 0.f, 0.f));
  }
  ComplexMatrix<float> actual_covariance_matrix(kNumberMics, kNumberMics);
  CovarianceMatrixGenerator::AngledCovarianceMatrix(kSpeedOfSound,
                                                    kAngle,
                                                    kFrequencyBin,
                                                    kFftSize,
                                                    kNumberFrequencyBins,
                                                    kSampleRate,
                                                    geometry,
                                                    &actual_covariance_matrix);

  complex<float>* const* actual_els = actual_covariance_matrix.elements();

  EXPECT_NEAR(actual_els[0][0].real(), 0.3333f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].real(), 0.2953f, kTolerance);
  EXPECT_NEAR(actual_els[0][2].real(), 0.1899f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].real(), 0.2953f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].real(), 0.3333f, kTolerance);
  EXPECT_NEAR(actual_els[1][2].real(), 0.2953f, kTolerance);
  EXPECT_NEAR(actual_els[2][0].real(), 0.1899f, kTolerance);
  EXPECT_NEAR(actual_els[2][1].real(), 0.2953f, kTolerance);
  EXPECT_NEAR(actual_els[2][2].real(), 0.3333f, kTolerance);

  EXPECT_NEAR(actual_els[0][0].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[0][1].imag(), 0.1546f, kTolerance);
  EXPECT_NEAR(actual_els[0][2].imag(), 0.274f, kTolerance);
  EXPECT_NEAR(actual_els[1][0].imag(), -0.1546f, kTolerance);
  EXPECT_NEAR(actual_els[1][1].imag(), 0.f, kTolerance);
  EXPECT_NEAR(actual_els[1][2].imag(), 0.1546f, kTolerance);
  EXPECT_NEAR(actual_els[2][0].imag(), -0.274f, kTolerance);
  EXPECT_NEAR(actual_els[2][1].imag(), -0.1546f, kTolerance);
  EXPECT_NEAR(actual_els[2][2].imag(), 0.f, kTolerance);
}

// PhaseAlignmentMasks is tested by AngledCovarianceMatrix and by
// InitBeamformerWeights in BeamformerUnittest.

}  // namespace webrtc
