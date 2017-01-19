/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// TODO(ajm): Make this a comprehensive test.

extern "C" {
#include "webrtc/modules/audio_processing/utility/ring_buffer.h"
}

#include <stdlib.h>
#include <time.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

namespace webrtc {

struct FreeBufferDeleter {
  inline void operator()(void* ptr) const {
    WebRtc_FreeBuffer(ptr);
  }
};
typedef scoped_ptr<RingBuffer, FreeBufferDeleter> scoped_ring_buffer;

static void AssertElementEq(int expected, int actual) {
  ASSERT_EQ(expected, actual);
}

static int SetIncrementingData(int* data, int num_elements,
                               int starting_value) {
  for (int i = 0; i < num_elements; i++) {
    data[i] = starting_value++;
  }
  return starting_value;
}

static int CheckIncrementingData(int* data, int num_elements,
                                 int starting_value) {
  for (int i = 0; i < num_elements; i++) {
    AssertElementEq(starting_value++, data[i]);
  }
  return starting_value;
}

// We use ASSERTs in this test to avoid obscuring the seed in the case of a
// failure.
static void RandomStressTest(int** data_ptr) {
  const int kNumTests = 10;
  const int kNumOps = 1000;
  const int kMaxBufferSize = 1000;

  unsigned int seed = time(NULL);
  printf("seed=%u\n", seed);
  srand(seed);
  for (int i = 0; i < kNumTests; i++) {
    const int buffer_size = std::max(rand() % kMaxBufferSize, 1);
    scoped_ptr<int[]> write_data(new int[buffer_size]);
    scoped_ptr<int[]> read_data(new int[buffer_size]);
    scoped_ring_buffer buffer(WebRtc_CreateBuffer(buffer_size, sizeof(int)));
    ASSERT_TRUE(buffer.get() != NULL);
    ASSERT_EQ(0, WebRtc_InitBuffer(buffer.get()));
    int buffer_consumed = 0;
    int write_element = 0;
    int read_element = 0;
    for (int j = 0; j < kNumOps; j++) {
      const bool write = rand() % 2 == 0 ? true : false;
      const int num_elements = rand() % buffer_size;
      if (write) {
        const int buffer_available = buffer_size - buffer_consumed;
        ASSERT_EQ(static_cast<size_t>(buffer_available),
                  WebRtc_available_write(buffer.get()));
        const int expected_elements = std::min(num_elements, buffer_available);
        write_element = SetIncrementingData(write_data.get(), expected_elements,
                                     write_element);
        ASSERT_EQ(static_cast<size_t>(expected_elements),
                  WebRtc_WriteBuffer(buffer.get(), write_data.get(),
                                     num_elements));
        buffer_consumed = std::min(buffer_consumed + expected_elements,
                                   buffer_size);
      } else {
        const int expected_elements = std::min(num_elements,
                                               buffer_consumed);
        ASSERT_EQ(static_cast<size_t>(buffer_consumed),
                  WebRtc_available_read(buffer.get()));
        ASSERT_EQ(static_cast<size_t>(expected_elements),
                  WebRtc_ReadBuffer(buffer.get(),
                                    reinterpret_cast<void**>(data_ptr),
                                    read_data.get(),
                                    num_elements));
        int* check_ptr = read_data.get();
        if (data_ptr) {
          check_ptr = *data_ptr;
        }
        read_element = CheckIncrementingData(check_ptr, expected_elements,
                                             read_element);
        buffer_consumed = std::max(buffer_consumed - expected_elements, 0);
      }
    }
  }
}

TEST(RingBufferTest, RandomStressTest) {
  int* data_ptr = NULL;
  RandomStressTest(&data_ptr);
}

TEST(RingBufferTest, RandomStressTestWithNullPtr) {
  RandomStressTest(NULL);
}

TEST(RingBufferTest, PassingNulltoReadBufferForcesMemcpy) {
  const size_t kDataSize = 2;
  int write_data[kDataSize];
  int read_data[kDataSize];
  int* data_ptr;

  scoped_ring_buffer buffer(WebRtc_CreateBuffer(kDataSize, sizeof(int)));
  ASSERT_TRUE(buffer.get() != NULL);
  ASSERT_EQ(0, WebRtc_InitBuffer(buffer.get()));

  SetIncrementingData(write_data, kDataSize, 0);
  EXPECT_EQ(kDataSize, WebRtc_WriteBuffer(buffer.get(), write_data, kDataSize));
  SetIncrementingData(read_data, kDataSize, kDataSize);
  EXPECT_EQ(kDataSize, WebRtc_ReadBuffer(buffer.get(),
      reinterpret_cast<void**>(&data_ptr), read_data, kDataSize));
  // Copying was not necessary, so |read_data| has not been updated.
  CheckIncrementingData(data_ptr, kDataSize, 0);
  CheckIncrementingData(read_data, kDataSize, kDataSize);

  EXPECT_EQ(kDataSize, WebRtc_WriteBuffer(buffer.get(), write_data, kDataSize));
  EXPECT_EQ(kDataSize, WebRtc_ReadBuffer(buffer.get(), NULL, read_data,
                                         kDataSize));
  // Passing NULL forces a memcpy, so |read_data| is now updated.
  CheckIncrementingData(read_data, kDataSize, 0);
}

TEST(RingBufferTest, CreateHandlesErrors) {
  EXPECT_TRUE(WebRtc_CreateBuffer(0, 1) == NULL);
  EXPECT_TRUE(WebRtc_CreateBuffer(1, 0) == NULL);
  RingBuffer* buffer = WebRtc_CreateBuffer(1, 1);
  EXPECT_TRUE(buffer != NULL);
  WebRtc_FreeBuffer(buffer);
}

}  // namespace webrtc
