/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <complex>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/beamformer/matrix.h"
#include "webrtc/modules/audio_processing/beamformer/matrix_test_helpers.h"

namespace webrtc {

using std::complex;

TEST(MatrixTest, TestMultiplySameSize) {
  const int kNumRows = 2;
  const int kNumCols = 2;
  const float kValuesLeft[kNumRows][kNumCols] = {{1.1f, 2.2f}, {3.3f, 4.4f}};
  const float kValuesRight[kNumRows][kNumCols] = {{5.4f, 127.f},
                                                  {4600.f, -555.f}};
  const float kValuesExpected[kNumRows][kNumCols] = {{10125.94f, -1081.3f},
                                                     {20257.82f, -2022.9f}};

  Matrix<float> lh_mat(*kValuesLeft, kNumRows, kNumCols);
  Matrix<float> rh_mat(*kValuesRight, kNumRows, kNumCols);
  Matrix<float> expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<float> actual_result(kNumRows, kNumCols);

  actual_result.Multiply(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEquality(expected_result, actual_result);

  lh_mat.Multiply(rh_mat);
  MatrixTestHelpers::ValidateMatrixEquality(lh_mat, actual_result);
}

TEST(MatrixTest, TestMultiplyDifferentSize) {
  const int kNumRowsLeft = 2;
  const int kNumColsLeft = 3;
  const int kNumRowsRight = 3;
  const int kNumColsRight = 2;
  const int kValuesLeft[kNumRowsLeft][kNumColsLeft] = {{35, 466, -15},
                                                       {-3, 3422, 9}};
  const int kValuesRight[kNumRowsRight][kNumColsRight] = {
      {765, -42}, {0, 194}, {625, 66321}};
  const int kValuesExpected[kNumRowsLeft][kNumColsRight] = {{17400, -905881},
                                                            {3330, 1260883}};

  Matrix<int> lh_mat(*kValuesLeft, kNumRowsLeft, kNumColsLeft);
  Matrix<int> rh_mat(*kValuesRight, kNumRowsRight, kNumColsRight);
  Matrix<int> expected_result(*kValuesExpected, kNumRowsLeft, kNumColsRight);
  Matrix<int> actual_result(kNumRowsLeft, kNumColsRight);

  actual_result.Multiply(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEquality(expected_result, actual_result);

  lh_mat.Multiply(rh_mat);
  MatrixTestHelpers::ValidateMatrixEquality(lh_mat, actual_result);
}

TEST(MatrixTest, TestTranspose) {
  const int kNumInitialRows = 2;
  const int kNumInitialCols = 4;
  const int kNumResultRows = 4;
  const int kNumResultCols = 2;
  const float kValuesInitial[kNumInitialRows][kNumInitialCols] = {
      {1.1f, 2.2f, 3.3f, 4.4f}, {5.5f, 6.6f, 7.7f, 8.8f}};
  const float kValuesExpected[kNumResultRows][kNumResultCols] = {
      {1.1f, 5.5f}, {2.2f, 6.6f}, {3.3f, 7.7f}, {4.4f, 8.8f}};

  Matrix<float> initial_mat(*kValuesInitial, kNumInitialRows, kNumInitialCols);
  Matrix<float> expected_result(
      *kValuesExpected, kNumResultRows, kNumResultCols);
  Matrix<float> actual_result(kNumResultRows, kNumResultCols);

  actual_result.Transpose(initial_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(expected_result,
                                                 actual_result);
  initial_mat.Transpose();
  MatrixTestHelpers::ValidateMatrixEqualityFloat(initial_mat, actual_result);
}

TEST(MatrixTest, TestScale) {
  const int kNumRows = 3;
  const int kNumCols = 3;
  const int kScaleFactor = -9;
  const int kValuesInitial[kNumRows][kNumCols] = {
      {1, 20, 5000}, {-3, -29, 66}, {7654, 0, -23455}};
  const int kValuesExpected[kNumRows][kNumCols] = {
      {-9, -180, -45000}, {27, 261, -594}, {-68886, 0, 211095}};

  Matrix<int> initial_mat(*kValuesInitial, kNumRows, kNumCols);
  Matrix<int> expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<int> actual_result;

  actual_result.Scale(initial_mat, kScaleFactor);
  MatrixTestHelpers::ValidateMatrixEquality(expected_result, actual_result);

  initial_mat.Scale(kScaleFactor);
  MatrixTestHelpers::ValidateMatrixEquality(initial_mat, actual_result);
}

TEST(MatrixTest, TestPointwiseAdd) {
  const int kNumRows = 2;
  const int kNumCols = 3;
  const float kValuesLeft[kNumRows][kNumCols] = {{1.1f, 210.45f, -549.2f},
                                                 {11.876f, 586.7f, -64.35f}};
  const float kValuesRight[kNumRows][kNumCols] = {{-50.4f, 1.f, 0.5f},
                                                  {460.f, -554.2f, 4566.f}};
  const float kValuesExpected[kNumRows][kNumCols] = {
      {-49.3f, 211.45f, -548.7f}, {471.876f, 32.5f, 4501.65f}};

  Matrix<float> lh_mat(*kValuesLeft, kNumRows, kNumCols);
  Matrix<float> rh_mat(*kValuesRight, kNumRows, kNumCols);
  Matrix<float> expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<float> actual_result;

  actual_result.Add(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(expected_result,
                                                 actual_result);
  lh_mat.Add(rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(lh_mat, actual_result);
}

TEST(MatrixTest, TestPointwiseSubtract) {
  const int kNumRows = 3;
  const int kNumCols = 2;
  const float kValuesLeft[kNumRows][kNumCols] = {
      {1.1f, 210.45f}, {-549.2f, 11.876f}, {586.7f, -64.35f}};
  const float kValuesRight[kNumRows][kNumCols] = {
      {-50.4f, 1.f}, {0.5f, 460.f}, {-554.2f, 4566.f}};
  const float kValuesExpected[kNumRows][kNumCols] = {
      {51.5f, 209.45f}, {-549.7f, -448.124f}, {1140.9f, -4630.35f}};

  Matrix<float> lh_mat(*kValuesLeft, kNumRows, kNumCols);
  Matrix<float> rh_mat(*kValuesRight, kNumRows, kNumCols);
  Matrix<float> expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<float> actual_result;

  actual_result.Subtract(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(expected_result,
                                                 actual_result);

  lh_mat.Subtract(rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(lh_mat, actual_result);
}

TEST(MatrixTest, TestPointwiseMultiply) {
  const int kNumRows = 1;
  const int kNumCols = 5;
  const float kValuesLeft[kNumRows][kNumCols] = {
      {1.1f, 6.4f, 0.f, -1.f, -88.3f}};
  const float kValuesRight[kNumRows][kNumCols] = {
      {53.2f, -210.45f, -549.2f, 99.99f, -45.2f}};
  const float kValuesExpected[kNumRows][kNumCols] = {
      {58.52f, -1346.88f, 0.f, -99.99f, 3991.16f}};

  Matrix<float> lh_mat(*kValuesLeft, kNumRows, kNumCols);
  Matrix<float> rh_mat(*kValuesRight, kNumRows, kNumCols);
  Matrix<float> expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<float> actual_result;

  actual_result.PointwiseMultiply(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(expected_result,
                                                 actual_result);

  lh_mat.PointwiseMultiply(rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(lh_mat, actual_result);
}

TEST(MatrixTest, TestPointwiseDivide) {
  const int kNumRows = 5;
  const int kNumCols = 1;
  const float kValuesLeft[kNumRows][kNumCols] = {
      {1.1f}, {6.4f}, {0.f}, {-1.f}, {-88.3f}};
  const float kValuesRight[kNumRows][kNumCols] = {
      {53.2f}, {-210.45f}, {-549.2f}, {99.99f}, {-45.2f}};
  const float kValuesExpected[kNumRows][kNumCols] = {
      {0.020676691f}, {-0.03041102399f}, {0.f}, {-0.010001f}, {1.9535398f}};

  Matrix<float> lh_mat(*kValuesLeft, kNumRows, kNumCols);
  Matrix<float> rh_mat(*kValuesRight, kNumRows, kNumCols);
  Matrix<float> expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<float> actual_result;

  actual_result.PointwiseDivide(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(expected_result,
                                                 actual_result);

  lh_mat.PointwiseDivide(rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(lh_mat, actual_result);
}

TEST(MatrixTest, TestPointwiseSquareRoot) {
  const int kNumRows = 2;
  const int kNumCols = 2;
  const int kValues[kNumRows][kNumCols] = {{4, 9}, {16, 0}};
  const int kValuesExpected[kNumRows][kNumCols] = {{2, 3}, {4, 0}};

  Matrix<int> operand_mat(*kValues, kNumRows, kNumCols);
  Matrix<int> expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<int> actual_result;

  actual_result.PointwiseSquareRoot(operand_mat);
  MatrixTestHelpers::ValidateMatrixEquality(expected_result, actual_result);

  operand_mat.PointwiseSquareRoot();
  MatrixTestHelpers::ValidateMatrixEquality(operand_mat, actual_result);
}

TEST(MatrixTest, TestPointwiseSquareRootComplex) {
  const int kNumRows = 1;
  const int kNumCols = 3;
  const complex<float> kValues[kNumRows][kNumCols] = {
      {complex<float>(-4.f, 0), complex<float>(0, 9), complex<float>(3, -4)}};
  const complex<float> kValuesExpected[kNumRows][kNumCols] = {
      {complex<float>(0.f, 2.f), complex<float>(2.1213202f, 2.1213202f),
       complex<float>(2.f, -1.f)}};

  Matrix<complex<float> > operand_mat(*kValues, kNumRows, kNumCols);
  Matrix<complex<float> > expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<complex<float> > actual_result;

  actual_result.PointwiseSquareRoot(operand_mat);
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(expected_result,
                                                        actual_result);

  operand_mat.PointwiseSquareRoot();
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(operand_mat,
                                                        actual_result);
}

TEST(MatrixTest, TestPointwiseAbsoluteValue) {
  const int kNumRows = 1;
  const int kNumCols = 3;
  const complex<float> kValues[kNumRows][kNumCols] = {
      {complex<float>(-4.f, 0), complex<float>(0, 9), complex<float>(3, -4)}};
  const complex<float> kValuesExpected[kNumRows][kNumCols] = {
      {complex<float>(4.f, 0), complex<float>(9.f, 0), complex<float>(5.f, 0)}};

  Matrix<complex<float> > operand_mat(*kValues, kNumRows, kNumCols);
  Matrix<complex<float> > expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<complex<float> > actual_result;

  actual_result.PointwiseAbsoluteValue(operand_mat);
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(expected_result,
                                                        actual_result);

  operand_mat.PointwiseAbsoluteValue();
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(operand_mat,
                                                        actual_result);
}

TEST(MatrixTest, TestPointwiseSquare) {
  const int kNumRows = 1;
  const int kNumCols = 3;
  const float kValues[kNumRows][kNumCols] = {{2.4f, -4.f, 3.3f}};
  const float kValuesExpected[kNumRows][kNumCols] = {{5.76f, 16.f, 10.89f}};

  Matrix<float> operand_mat(*kValues, kNumRows, kNumCols);
  Matrix<float> expected_result(*kValuesExpected, kNumRows, kNumCols);
  Matrix<float> actual_result;

  actual_result.PointwiseSquare(operand_mat);
  MatrixTestHelpers::ValidateMatrixEqualityFloat(expected_result,
                                                 actual_result);

  operand_mat.PointwiseSquare();
  MatrixTestHelpers::ValidateMatrixEqualityFloat(operand_mat, actual_result);
}

TEST(MatrixTest, TestComplexOperations) {
  const int kNumRows = 2;
  const int kNumCols = 2;

  const complex<float> kValuesLeft[kNumRows][kNumCols] = {
      {complex<float>(1.f, 1.f), complex<float>(2.f, 2.f)},
      {complex<float>(3.f, 3.f), complex<float>(4.f, 4.f)}};

  const complex<float> kValuesRight[kNumRows][kNumCols] = {
      {complex<float>(5.f, 5.f), complex<float>(6.f, 6.f)},
      {complex<float>(7.f, 7.f), complex<float>(8.f, 8.f)}};

  const complex<float> kValuesExpectedAdd[kNumRows][kNumCols] = {
      {complex<float>(6.f, 6.f), complex<float>(8.f, 8.f)},
      {complex<float>(10.f, 10.f), complex<float>(12.f, 12.f)}};

  const complex<float> kValuesExpectedMultiply[kNumRows][kNumCols] = {
      {complex<float>(0.f, 38.f), complex<float>(0.f, 44.f)},
      {complex<float>(0.f, 86.f), complex<float>(0.f, 100.f)}};

  const complex<float> kValuesExpectedPointwiseDivide[kNumRows][kNumCols] = {
      {complex<float>(0.2f, 0.f), complex<float>(0.33333333f, 0.f)},
      {complex<float>(0.42857143f, 0.f), complex<float>(0.5f, 0.f)}};

  Matrix<complex<float> > lh_mat(*kValuesLeft, kNumRows, kNumCols);
  Matrix<complex<float> > rh_mat(*kValuesRight, kNumRows, kNumCols);
  Matrix<complex<float> > expected_result_add(
      *kValuesExpectedAdd, kNumRows, kNumCols);
  Matrix<complex<float> > expected_result_multiply(
      *kValuesExpectedMultiply, kNumRows, kNumCols);
  Matrix<complex<float> > expected_result_pointwise_divide(
      *kValuesExpectedPointwiseDivide, kNumRows, kNumCols);
  Matrix<complex<float> > actual_result_add;
  Matrix<complex<float> > actual_result_multiply(kNumRows, kNumCols);
  Matrix<complex<float> > actual_result_pointwise_divide;

  actual_result_add.Add(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(expected_result_add,
                                                        actual_result_add);

  actual_result_multiply.Multiply(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(
      expected_result_multiply, actual_result_multiply);

  actual_result_pointwise_divide.PointwiseDivide(lh_mat, rh_mat);
  MatrixTestHelpers::ValidateMatrixEqualityComplexFloat(
      expected_result_pointwise_divide, actual_result_pointwise_divide);
}

}  // namespace webrtc
