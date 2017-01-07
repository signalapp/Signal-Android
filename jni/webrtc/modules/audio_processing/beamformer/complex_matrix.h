/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_COMPLEX_MATRIX_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_COMPLEX_MATRIX_H_

#include <complex>

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_processing/beamformer/matrix.h"

namespace webrtc {

using std::complex;

// An extension of Matrix for operations that only work on a complex type.
template <typename T>
class ComplexMatrix : public Matrix<complex<T> > {
 public:
  ComplexMatrix() : Matrix<complex<T> >() {}

  ComplexMatrix(size_t num_rows, size_t num_columns)
      : Matrix<complex<T> >(num_rows, num_columns) {}

  ComplexMatrix(const complex<T>* data, size_t num_rows, size_t num_columns)
      : Matrix<complex<T> >(data, num_rows, num_columns) {}

  // Complex Matrix operations.
  ComplexMatrix& PointwiseConjugate() {
    complex<T>* const data = this->data();
    size_t size = this->num_rows() * this->num_columns();
    for (size_t i = 0; i < size; ++i) {
      data[i] = conj(data[i]);
    }

    return *this;
  }

  ComplexMatrix& PointwiseConjugate(const ComplexMatrix& operand) {
    this->CopyFrom(operand);
    return PointwiseConjugate();
  }

  ComplexMatrix& ConjugateTranspose() {
    this->CopyDataToScratch();
    size_t num_rows = this->num_rows();
    this->SetNumRows(this->num_columns());
    this->SetNumColumns(num_rows);
    this->Resize();
    return ConjugateTranspose(this->scratch_elements());
  }

  ComplexMatrix& ConjugateTranspose(const ComplexMatrix& operand) {
    RTC_CHECK_EQ(operand.num_rows(), this->num_columns());
    RTC_CHECK_EQ(operand.num_columns(), this->num_rows());
    return ConjugateTranspose(operand.elements());
  }

  ComplexMatrix& ZeroImag() {
    complex<T>* const data = this->data();
    size_t size = this->num_rows() * this->num_columns();
    for (size_t i = 0; i < size; ++i) {
      data[i] = complex<T>(data[i].real(), 0);
    }

    return *this;
  }

  ComplexMatrix& ZeroImag(const ComplexMatrix& operand) {
    this->CopyFrom(operand);
    return ZeroImag();
  }

 private:
  ComplexMatrix& ConjugateTranspose(const complex<T>* const* src) {
    complex<T>* const* elements = this->elements();
    for (size_t i = 0; i < this->num_rows(); ++i) {
      for (size_t j = 0; j < this->num_columns(); ++j) {
        elements[i][j] = conj(src[j][i]);
      }
    }

    return *this;
  }
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_COMPLEX_MATRIX_H_
