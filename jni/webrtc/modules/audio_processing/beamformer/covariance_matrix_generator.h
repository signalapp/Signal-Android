/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_COVARIANCE_MATRIX_GENERATOR_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_COVARIANCE_MATRIX_GENERATOR_H_

#include "webrtc/modules/audio_processing/beamformer/complex_matrix.h"
#include "webrtc/modules/audio_processing/beamformer/array_util.h"

namespace webrtc {

// Helper class for Beamformer in charge of generating covariance matrices. For
// each function, the passed-in ComplexMatrix is expected to be of size
// |num_input_channels| x |num_input_channels|.
class CovarianceMatrixGenerator {
 public:
  // A uniform covariance matrix with a gap at the target location. WARNING:
  // The target angle is assumed to be 0.
  static void UniformCovarianceMatrix(float wave_number,
                                      const std::vector<Point>& geometry,
                                      ComplexMatrix<float>* mat);

  // The covariance matrix of a source at the given angle.
  static void AngledCovarianceMatrix(float sound_speed,
                                     float angle,
                                     size_t frequency_bin,
                                     size_t fft_size,
                                     size_t num_freq_bins,
                                     int sample_rate,
                                     const std::vector<Point>& geometry,
                                     ComplexMatrix<float>* mat);

  // Calculates phase shifts that, when applied to a multichannel signal and
  // added together, cause constructive interferernce for sources located at
  // the given angle.
  static void PhaseAlignmentMasks(size_t frequency_bin,
                                  size_t fft_size,
                                  int sample_rate,
                                  float sound_speed,
                                  const std::vector<Point>& geometry,
                                  float angle,
                                  ComplexMatrix<float>* mat);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_BF_HELPERS_H_
