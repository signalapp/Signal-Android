/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_MATRIX_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_MATRIX_H_

#include <algorithm>
#include <cstring>
#include <string>
#include <vector>

#include "webrtc/base/checks.h"
#include "webrtc/base/constructormagic.h"

namespace {

// Wrappers to get around the compiler warning resulting from the fact that
// there's no std::sqrt overload for ints. We cast all non-complex types to
// a double for the sqrt method.
template <typename T>
T sqrt_wrapper(T x) {
  return sqrt(static_cast<double>(x));
}

template <typename S>
std::complex<S> sqrt_wrapper(std::complex<S> x) {
  return sqrt(x);
}
} // namespace

namespace webrtc {

// Matrix is a class for doing standard matrix operations on 2 dimensional
// matrices of any size. Results of matrix operations are stored in the
// calling object. Function overloads exist for both in-place (the calling
// object is used as both an operand and the result) and out-of-place (all
// operands are passed in as parameters) operations. If operand dimensions
// mismatch, the program crashes. Out-of-place operations change the size of
// the calling object, if necessary, before operating.
//
// 'In-place' operations that inherently change the size of the matrix (eg.
// Transpose, Multiply on different-sized matrices) must make temporary copies
// (|scratch_elements_| and |scratch_data_|) of existing data to complete the
// operations.
//
// The data is stored contiguously. Data can be accessed internally as a flat
// array, |data_|, or as an array of row pointers, |elements_|, but is
// available to users only as an array of row pointers through |elements()|.
// Memory for storage is allocated when a matrix is resized only if the new
// size overflows capacity. Memory needed temporarily for any operations is
// similarly resized only if the new size overflows capacity.
//
// If you pass in storage through the ctor, that storage is copied into the
// matrix. TODO(claguna): albeit tricky, allow for data to be referenced
// instead of copied, and owned by the user.
template <typename T>
class Matrix {
 public:
  Matrix() : num_rows_(0), num_columns_(0) {}

  // Allocates space for the elements and initializes all values to zero.
  Matrix(size_t num_rows, size_t num_columns)
      : num_rows_(num_rows), num_columns_(num_columns) {
    Resize();
    scratch_data_.resize(num_rows_ * num_columns_);
    scratch_elements_.resize(num_rows_);
  }

  // Copies |data| into the new Matrix.
  Matrix(const T* data, size_t num_rows, size_t num_columns)
      : num_rows_(0), num_columns_(0) {
    CopyFrom(data, num_rows, num_columns);
    scratch_data_.resize(num_rows_ * num_columns_);
    scratch_elements_.resize(num_rows_);
  }

  virtual ~Matrix() {}

  // Deep copy an existing matrix.
  void CopyFrom(const Matrix& other) {
    CopyFrom(&other.data_[0], other.num_rows_, other.num_columns_);
  }

  // Copy |data| into the Matrix. The current data is lost.
  void CopyFrom(const T* const data, size_t num_rows, size_t num_columns) {
    Resize(num_rows, num_columns);
    memcpy(&data_[0], data, num_rows_ * num_columns_ * sizeof(data_[0]));
  }

  Matrix& CopyFromColumn(const T* const* src,
                         size_t column_index,
                         size_t num_rows) {
    Resize(1, num_rows);
    for (size_t i = 0; i < num_columns_; ++i) {
      data_[i] = src[i][column_index];
    }

    return *this;
  }

  void Resize(size_t num_rows, size_t num_columns) {
    if (num_rows != num_rows_ || num_columns != num_columns_) {
      num_rows_ = num_rows;
      num_columns_ = num_columns;
      Resize();
    }
  }

  // Accessors and mutators.
  size_t num_rows() const { return num_rows_; }
  size_t num_columns() const { return num_columns_; }
  T* const* elements() { return &elements_[0]; }
  const T* const* elements() const { return &elements_[0]; }

  T Trace() {
    RTC_CHECK_EQ(num_rows_, num_columns_);

    T trace = 0;
    for (size_t i = 0; i < num_rows_; ++i) {
      trace += elements_[i][i];
    }
    return trace;
  }

  // Matrix Operations. Returns *this to support method chaining.
  Matrix& Transpose() {
    CopyDataToScratch();
    Resize(num_columns_, num_rows_);
    return Transpose(scratch_elements());
  }

  Matrix& Transpose(const Matrix& operand) {
    RTC_CHECK_EQ(operand.num_rows_, num_columns_);
    RTC_CHECK_EQ(operand.num_columns_, num_rows_);

    return Transpose(operand.elements());
  }

  template <typename S>
  Matrix& Scale(const S& scalar) {
    for (size_t i = 0; i < data_.size(); ++i) {
      data_[i] *= scalar;
    }

    return *this;
  }

  template <typename S>
  Matrix& Scale(const Matrix& operand, const S& scalar) {
    CopyFrom(operand);
    return Scale(scalar);
  }

  Matrix& Add(const Matrix& operand) {
    RTC_CHECK_EQ(num_rows_, operand.num_rows_);
    RTC_CHECK_EQ(num_columns_, operand.num_columns_);

    for (size_t i = 0; i < data_.size(); ++i) {
      data_[i] += operand.data_[i];
    }

    return *this;
  }

  Matrix& Add(const Matrix& lhs, const Matrix& rhs) {
    CopyFrom(lhs);
    return Add(rhs);
  }

  Matrix& Subtract(const Matrix& operand) {
    RTC_CHECK_EQ(num_rows_, operand.num_rows_);
    RTC_CHECK_EQ(num_columns_, operand.num_columns_);

    for (size_t i = 0; i < data_.size(); ++i) {
      data_[i] -= operand.data_[i];
    }

    return *this;
  }

  Matrix& Subtract(const Matrix& lhs, const Matrix& rhs) {
    CopyFrom(lhs);
    return Subtract(rhs);
  }

  Matrix& PointwiseMultiply(const Matrix& operand) {
    RTC_CHECK_EQ(num_rows_, operand.num_rows_);
    RTC_CHECK_EQ(num_columns_, operand.num_columns_);

    for (size_t i = 0; i < data_.size(); ++i) {
      data_[i] *= operand.data_[i];
    }

    return *this;
  }

  Matrix& PointwiseMultiply(const Matrix& lhs, const Matrix& rhs) {
    CopyFrom(lhs);
    return PointwiseMultiply(rhs);
  }

  Matrix& PointwiseDivide(const Matrix& operand) {
    RTC_CHECK_EQ(num_rows_, operand.num_rows_);
    RTC_CHECK_EQ(num_columns_, operand.num_columns_);

    for (size_t i = 0; i < data_.size(); ++i) {
      data_[i] /= operand.data_[i];
    }

    return *this;
  }

  Matrix& PointwiseDivide(const Matrix& lhs, const Matrix& rhs) {
    CopyFrom(lhs);
    return PointwiseDivide(rhs);
  }

  Matrix& PointwiseSquareRoot() {
    for (size_t i = 0; i < data_.size(); ++i) {
      data_[i] = sqrt_wrapper(data_[i]);
    }

    return *this;
  }

  Matrix& PointwiseSquareRoot(const Matrix& operand) {
    CopyFrom(operand);
    return PointwiseSquareRoot();
  }

  Matrix& PointwiseAbsoluteValue() {
    for (size_t i = 0; i < data_.size(); ++i) {
      data_[i] = abs(data_[i]);
    }

    return *this;
  }

  Matrix& PointwiseAbsoluteValue(const Matrix& operand) {
    CopyFrom(operand);
    return PointwiseAbsoluteValue();
  }

  Matrix& PointwiseSquare() {
    for (size_t i = 0; i < data_.size(); ++i) {
      data_[i] *= data_[i];
    }

    return *this;
  }

  Matrix& PointwiseSquare(const Matrix& operand) {
    CopyFrom(operand);
    return PointwiseSquare();
  }

  Matrix& Multiply(const Matrix& lhs, const Matrix& rhs) {
    RTC_CHECK_EQ(lhs.num_columns_, rhs.num_rows_);
    RTC_CHECK_EQ(num_rows_, lhs.num_rows_);
    RTC_CHECK_EQ(num_columns_, rhs.num_columns_);

    return Multiply(lhs.elements(), rhs.num_rows_, rhs.elements());
  }

  Matrix& Multiply(const Matrix& rhs) {
    RTC_CHECK_EQ(num_columns_, rhs.num_rows_);

    CopyDataToScratch();
    Resize(num_rows_, rhs.num_columns_);
    return Multiply(scratch_elements(), rhs.num_rows_, rhs.elements());
  }

  std::string ToString() const {
    std::ostringstream ss;
    ss << std::endl << "Matrix" << std::endl;

    for (size_t i = 0; i < num_rows_; ++i) {
      for (size_t j = 0; j < num_columns_; ++j) {
        ss << elements_[i][j] << " ";
      }
      ss << std::endl;
    }
    ss << std::endl;

    return ss.str();
  }

 protected:
  void SetNumRows(const size_t num_rows) { num_rows_ = num_rows; }
  void SetNumColumns(const size_t num_columns) { num_columns_ = num_columns; }
  T* data() { return &data_[0]; }
  const T* data() const { return &data_[0]; }
  const T* const* scratch_elements() const { return &scratch_elements_[0]; }

  // Resize the matrix. If an increase in capacity is required, the current
  // data is lost.
  void Resize() {
    size_t size = num_rows_ * num_columns_;
    data_.resize(size);
    elements_.resize(num_rows_);

    for (size_t i = 0; i < num_rows_; ++i) {
      elements_[i] = &data_[i * num_columns_];
    }
  }

  // Copies data_ into scratch_data_ and updates scratch_elements_ accordingly.
  void CopyDataToScratch() {
    scratch_data_ = data_;
    scratch_elements_.resize(num_rows_);

    for (size_t i = 0; i < num_rows_; ++i) {
      scratch_elements_[i] = &scratch_data_[i * num_columns_];
    }
  }

 private:
  size_t num_rows_;
  size_t num_columns_;
  std::vector<T> data_;
  std::vector<T*> elements_;

  // Stores temporary copies of |data_| and |elements_| for in-place operations
  // where referring to original data is necessary.
  std::vector<T> scratch_data_;
  std::vector<T*> scratch_elements_;

  // Helpers for Transpose and Multiply operations that unify in-place and
  // out-of-place solutions.
  Matrix& Transpose(const T* const* src) {
    for (size_t i = 0; i < num_rows_; ++i) {
      for (size_t j = 0; j < num_columns_; ++j) {
        elements_[i][j] = src[j][i];
      }
    }

    return *this;
  }

  Matrix& Multiply(const T* const* lhs,
                   size_t num_rows_rhs,
                   const T* const* rhs) {
    for (size_t row = 0; row < num_rows_; ++row) {
      for (size_t col = 0; col < num_columns_; ++col) {
        T cur_element = 0;
        for (size_t i = 0; i < num_rows_rhs; ++i) {
          cur_element += lhs[row][i] * rhs[i][col];
        }

        elements_[row][col] = cur_element;
      }
    }

    return *this;
  }

  RTC_DISALLOW_COPY_AND_ASSIGN(Matrix);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_MATRIX_H_
