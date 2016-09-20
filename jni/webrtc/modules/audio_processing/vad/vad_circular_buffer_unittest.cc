/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/vad/vad_circular_buffer.h"

#include <stdio.h>

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

static const int kWidthThreshold = 7;
static const double kValThreshold = 1.0;
static const int kLongBuffSize = 100;
static const int kShortBuffSize = 10;

static void InsertSequentially(int k, VadCircularBuffer* circular_buffer) {
  double mean_val;
  for (int n = 1; n <= k; n++) {
    EXPECT_TRUE(!circular_buffer->is_full());
    circular_buffer->Insert(n);
    mean_val = circular_buffer->Mean();
    EXPECT_EQ((n + 1.0) / 2., mean_val);
  }
}

static void Insert(double value,
                   int num_insertion,
                   VadCircularBuffer* circular_buffer) {
  for (int n = 0; n < num_insertion; n++)
    circular_buffer->Insert(value);
}

static void InsertZeros(int num_zeros, VadCircularBuffer* circular_buffer) {
  Insert(0.0, num_zeros, circular_buffer);
}

TEST(VadCircularBufferTest, GeneralTest) {
  std::unique_ptr<VadCircularBuffer> circular_buffer(
      VadCircularBuffer::Create(kShortBuffSize));
  double mean_val;

  // Mean should return zero if nothing is inserted.
  mean_val = circular_buffer->Mean();
  EXPECT_DOUBLE_EQ(0.0, mean_val);
  InsertSequentially(kShortBuffSize, circular_buffer.get());

  // Should be full.
  EXPECT_TRUE(circular_buffer->is_full());
  // Correct update after being full.
  for (int n = 1; n < kShortBuffSize; n++) {
    circular_buffer->Insert(n);
    mean_val = circular_buffer->Mean();
    EXPECT_DOUBLE_EQ((kShortBuffSize + 1.) / 2., mean_val);
    EXPECT_TRUE(circular_buffer->is_full());
  }

  // Check reset. This should be like starting fresh.
  circular_buffer->Reset();
  mean_val = circular_buffer->Mean();
  EXPECT_DOUBLE_EQ(0, mean_val);
  InsertSequentially(kShortBuffSize, circular_buffer.get());
  EXPECT_TRUE(circular_buffer->is_full());
}

TEST(VadCircularBufferTest, TransientsRemoval) {
  std::unique_ptr<VadCircularBuffer> circular_buffer(
      VadCircularBuffer::Create(kLongBuffSize));
  // Let the first transient be in wrap-around.
  InsertZeros(kLongBuffSize - kWidthThreshold / 2, circular_buffer.get());

  double push_val = kValThreshold;
  double mean_val;
  for (int k = kWidthThreshold; k >= 1; k--) {
    Insert(push_val, k, circular_buffer.get());
    circular_buffer->Insert(0);
    mean_val = circular_buffer->Mean();
    EXPECT_DOUBLE_EQ(k * push_val / kLongBuffSize, mean_val);
    circular_buffer->RemoveTransient(kWidthThreshold, kValThreshold);
    mean_val = circular_buffer->Mean();
    EXPECT_DOUBLE_EQ(0, mean_val);
  }
}

TEST(VadCircularBufferTest, TransientDetection) {
  std::unique_ptr<VadCircularBuffer> circular_buffer(
      VadCircularBuffer::Create(kLongBuffSize));
  // Let the first transient be in wrap-around.
  int num_insertion = kLongBuffSize - kWidthThreshold / 2;
  InsertZeros(num_insertion, circular_buffer.get());

  double push_val = 2;
  // This is longer than a transient and shouldn't be removed.
  int num_non_zero_elements = kWidthThreshold + 1;
  Insert(push_val, num_non_zero_elements, circular_buffer.get());

  double mean_val = circular_buffer->Mean();
  EXPECT_DOUBLE_EQ(num_non_zero_elements * push_val / kLongBuffSize, mean_val);
  circular_buffer->Insert(0);
  EXPECT_EQ(0,
            circular_buffer->RemoveTransient(kWidthThreshold, kValThreshold));
  mean_val = circular_buffer->Mean();
  EXPECT_DOUBLE_EQ(num_non_zero_elements * push_val / kLongBuffSize, mean_val);

  // A transient right after a non-transient, should be removed and mean is
  // not changed.
  num_insertion = 3;
  Insert(push_val, num_insertion, circular_buffer.get());
  circular_buffer->Insert(0);
  EXPECT_EQ(0,
            circular_buffer->RemoveTransient(kWidthThreshold, kValThreshold));
  mean_val = circular_buffer->Mean();
  EXPECT_DOUBLE_EQ(num_non_zero_elements * push_val / kLongBuffSize, mean_val);

  // Last input is larger than threshold, although the sequence is short but
  // it shouldn't be considered transient.
  Insert(push_val, num_insertion, circular_buffer.get());
  num_non_zero_elements += num_insertion;
  EXPECT_EQ(0,
            circular_buffer->RemoveTransient(kWidthThreshold, kValThreshold));
  mean_val = circular_buffer->Mean();
  EXPECT_DOUBLE_EQ(num_non_zero_elements * push_val / kLongBuffSize, mean_val);
}

}  // namespace webrtc
