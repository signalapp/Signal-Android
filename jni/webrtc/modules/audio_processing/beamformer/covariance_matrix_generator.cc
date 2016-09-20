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

namespace webrtc {
namespace {

float BesselJ0(float x) {
#if WEBRTC_WIN
  return _j0(x);
#else
  return j0(x);
#endif
}

// Calculates the Euclidean norm for a row vector.
float Norm(const ComplexMatrix<float>& x) {
  RTC_CHECK_EQ(1u, x.num_rows());
  const size_t length = x.num_columns();
  const complex<float>* elems = x.elements()[0];
  float result = 0.f;
  for (size_t i = 0u; i < length; ++i) {
    result += std::norm(elems[i]);
  }
  return std::sqrt(result);
}

}  // namespace

void CovarianceMatrixGenerator::UniformCovarianceMatrix(
    float wave_number,
    const std::vector<Point>& geometry,
    ComplexMatrix<float>* mat) {
  RTC_CHECK_EQ(geometry.size(), mat->num_rows());
  RTC_CHECK_EQ(geometry.size(), mat->num_columns());

  complex<float>* const* mat_els = mat->elements();
  for (size_t i = 0; i < geometry.size(); ++i) {
    for (size_t j = 0; j < geometry.size(); ++j) {
      if (wave_number > 0.f) {
        mat_els[i][j] =
            BesselJ0(wave_number * Distance(geometry[i], geometry[j]));
      } else {
        mat_els[i][j] = i == j ? 1.f : 0.f;
      }
    }
  }
}

void CovarianceMatrixGenerator::AngledCovarianceMatrix(
    float sound_speed,
    float angle,
    size_t frequency_bin,
    size_t fft_size,
    size_t num_freq_bins,
    int sample_rate,
    const std::vector<Point>& geometry,
    ComplexMatrix<float>* mat) {
  RTC_CHECK_EQ(geometry.size(), mat->num_rows());
  RTC_CHECK_EQ(geometry.size(), mat->num_columns());

  ComplexMatrix<float> interf_cov_vector(1, geometry.size());
  ComplexMatrix<float> interf_cov_vector_transposed(geometry.size(), 1);
  PhaseAlignmentMasks(frequency_bin,
                      fft_size,
                      sample_rate,
                      sound_speed,
                      geometry,
                      angle,
                      &interf_cov_vector);
  interf_cov_vector.Scale(1.f / Norm(interf_cov_vector));
  interf_cov_vector_transposed.Transpose(interf_cov_vector);
  interf_cov_vector.PointwiseConjugate();
  mat->Multiply(interf_cov_vector_transposed, interf_cov_vector);
}

void CovarianceMatrixGenerator::PhaseAlignmentMasks(
    size_t frequency_bin,
    size_t fft_size,
    int sample_rate,
    float sound_speed,
    const std::vector<Point>& geometry,
    float angle,
    ComplexMatrix<float>* mat) {
  RTC_CHECK_EQ(1u, mat->num_rows());
  RTC_CHECK_EQ(geometry.size(), mat->num_columns());

  float freq_in_hertz =
      (static_cast<float>(frequency_bin) / fft_size) * sample_rate;

  complex<float>* const* mat_els = mat->elements();
  for (size_t c_ix = 0; c_ix < geometry.size(); ++c_ix) {
    float distance = std::cos(angle) * geometry[c_ix].x() +
                     std::sin(angle) * geometry[c_ix].y();
    float phase_shift = -2.f * M_PI * distance * freq_in_hertz / sound_speed;

    // Euler's formula for mat[0][c_ix] = e^(j * phase_shift).
    mat_els[0][c_ix] = complex<float>(cos(phase_shift), sin(phase_shift));
  }
}

}  // namespace webrtc
