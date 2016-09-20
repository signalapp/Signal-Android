/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_MATRIX_TEST_HELPERS_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_MATRIX_TEST_HELPERS_H_

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/beamformer/complex_matrix.h"
#include "webrtc/modules/audio_processing/beamformer/matrix.h"

namespace {
const float kTolerance = 0.001f;
}

namespace webrtc {

using std::complex;

// Functions used in both matrix_unittest and complex_matrix_unittest.
class MatrixTestHelpers {
 public:
  template <typename T>
  static void ValidateMatrixEquality(const Matrix<T>& expected,
                                     const Matrix<T>& actual) {
    EXPECT_EQ(expected.num_rows(), actual.num_rows());
    EXPECT_EQ(expected.num_columns(), actual.num_columns());

    const T* const* expected_elements = expected.elements();
    const T* const* actual_elements = actual.elements();
    for (size_t i = 0; i < expected.num_rows(); ++i) {
      for (size_t j = 0; j < expected.num_columns(); ++j) {
        EXPECT_EQ(expected_elements[i][j], actual_elements[i][j]);
      }
    }
  }

  static void ValidateMatrixEqualityFloat(const Matrix<float>& expected,
                                          const Matrix<float>& actual) {
    EXPECT_EQ(expected.num_rows(), actual.num_rows());
    EXPECT_EQ(expected.num_columns(), actual.num_columns());

    const float* const* expected_elements = expected.elements();
    const float* const* actual_elements = actual.elements();
    for (size_t i = 0; i < expected.num_rows(); ++i) {
      for (size_t j = 0; j < expected.num_columns(); ++j) {
        EXPECT_NEAR(expected_elements[i][j], actual_elements[i][j], kTolerance);
      }
    }
  }

  static void ValidateMatrixEqualityComplexFloat(
      const Matrix<complex<float> >& expected,
      const Matrix<complex<float> >& actual) {
    EXPECT_EQ(expected.num_rows(), actual.num_rows());
    EXPECT_EQ(expected.num_columns(), actual.num_columns());

    const complex<float>* const* expected_elements = expected.elements();
    const complex<float>* const* actual_elements = actual.elements();
    for (size_t i = 0; i < expected.num_rows(); ++i) {
      for (size_t j = 0; j < expected.num_columns(); ++j) {
        EXPECT_NEAR(expected_elements[i][j].real(),
                    actual_elements[i][j].real(),
                    kTolerance);
        EXPECT_NEAR(expected_elements[i][j].imag(),
                    actual_elements[i][j].imag(),
                    kTolerance);
      }
    }
  }

  static void ValidateMatrixNearEqualityComplexFloat(
      const Matrix<complex<float> >& expected,
      const Matrix<complex<float> >& actual,
      float tolerance) {
    EXPECT_EQ(expected.num_rows(), actual.num_rows());
    EXPECT_EQ(expected.num_columns(), actual.num_columns());

    const complex<float>* const* expected_elements = expected.elements();
    const complex<float>* const* actual_elements = actual.elements();
    for (size_t i = 0; i < expected.num_rows(); ++i) {
      for (size_t j = 0; j < expected.num_columns(); ++j) {
        EXPECT_NEAR(expected_elements[i][j].real(),
                    actual_elements[i][j].real(),
                    tolerance);
        EXPECT_NEAR(expected_elements[i][j].imag(),
                    actual_elements[i][j].imag(),
                    tolerance);
      }
    }
  }
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_MATRIX_TEST_HELPERS_H_
