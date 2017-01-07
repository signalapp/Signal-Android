/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/beamformer/complex_matrix.h"
#include "webrtc/modules/audio_processing/beamformer/matrix_test_helpers.h"

namespace webrtc {

TEST(ComplexMatrixTest, TestPointwiseConjugate) {
  const int kNumRows = 2;
  const int kNumCols = 4;

  const complex<float> kValuesInitial[kNumRows][kNumCols] = {
      {complex<float>(1.1f, 1.1f), complex<float>(2.2f, -2.2f),
       complex<float>(3.3f, 3.3f), complex<float>(4.4f, -4.4f)},
      {complex<float>(5.5f, 5.5f), complex<float>(6.6f, -6.6f),
       complex<float>(7.7f, 7.7f), complex<float>(8.8f, -8.8f)}};

  const complex<float> kValuesExpected[kNumRows][kNumCols] = {
      {complex<float>(1.1f, -1.1f), complex<float>(2.2f, 2.2f),
       complex<float>(3.3f, -3.3f), complex<float>(4.4f, 4.4f)},
      {complex<float>(5.5f, -5.5f), complex<float>(6.6f, 6.6f),
       complex<float>(7.7f, -7.7f), complex<float>(8.8f, 8.8f)}};

  ComplexMatrix<float> initial_mat(*kValuesInitial, kNumRows, kNumCols);
  ComplexMatrix<float> expected_result(*kValuesExpected, kNumRows, kNumCols);
  ComplexMatrix<float> actual_result(kNumRows, kNumCols);

  actual_result.PointwiseConjugate(initial_mat);
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(expected_result,
                                                        actual_result);

  initial_mat.PointwiseConjugate();
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(initial_mat,
                                                        actual_result);
}

TEST(ComplexMatrixTest, TestConjugateTranspose) {
  const int kNumInitialRows = 2;
  const int kNumInitialCols = 4;
  const int kNumResultRows = 4;
  const int kNumResultCols = 2;

  const complex<float> kValuesInitial[kNumInitialRows][kNumInitialCols] = {
      {complex<float>(1.1f, 1.1f), complex<float>(2.2f, 2.2f),
       complex<float>(3.3f, 3.3f), complex<float>(4.4f, 4.4f)},
      {complex<float>(5.5f, 5.5f), complex<float>(6.6f, 6.6f),
       complex<float>(7.7f, 7.7f), complex<float>(8.8f, 8.8f)}};

  const complex<float> kValuesExpected[kNumResultRows][kNumResultCols] = {
      {complex<float>(1.1f, -1.1f), complex<float>(5.5f, -5.5f)},
      {complex<float>(2.2f, -2.2f), complex<float>(6.6f, -6.6f)},
      {complex<float>(3.3f, -3.3f), complex<float>(7.7f, -7.7f)},
      {complex<float>(4.4f, -4.4f), complex<float>(8.8f, -8.8f)}};

  ComplexMatrix<float> initial_mat(
      *kValuesInitial, kNumInitialRows, kNumInitialCols);
  ComplexMatrix<float> expected_result(
      *kValuesExpected, kNumResultRows, kNumResultCols);
  ComplexMatrix<float> actual_result(kNumResultRows, kNumResultCols);

  actual_result.ConjugateTranspose(initial_mat);
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(expected_result,
                                                        actual_result);

  initial_mat.ConjugateTranspose();
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(initial_mat,
                                                        actual_result);
}

TEST(ComplexMatrixTest, TestZeroImag) {
  const int kNumRows = 2;
  const int kNumCols = 2;
  const complex<float> kValuesInitial[kNumRows][kNumCols] = {
      {complex<float>(1.1f, 1.1f), complex<float>(2.2f, 2.2f)},
      {complex<float>(3.3f, 3.3f), complex<float>(4.4f, 4.4f)}};
  const complex<float> kValuesExpected[kNumRows][kNumCols] = {
      {complex<float>(1.1f, 0.f), complex<float>(2.2f, 0.f)},
      {complex<float>(3.3f, 0.f), complex<float>(4.4f, 0.f)}};

  ComplexMatrix<float> initial_mat(*kValuesInitial, kNumRows, kNumCols);
  ComplexMatrix<float> expected_result(*kValuesExpected, kNumRows, kNumCols);
  ComplexMatrix<float> actual_result;

  actual_result.ZeroImag(initial_mat);
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(expected_result,
                                                        actual_result);

  initial_mat.ZeroImag();
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(initial_mat,
                                                        actual_result);
}

}  // namespace webrtc
