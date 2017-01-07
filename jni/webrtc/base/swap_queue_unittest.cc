/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/swap_queue.h"

#include <vector>

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

namespace {

// Test parameter for the basic sample based SwapQueue Tests.
const size_t kChunkSize = 3;

// Queue item verification function for the vector test.
bool LengthVerifierFunction(const std::vector<int>& v) {
  return v.size() == kChunkSize;
}

// Queue item verifier for the vector test.
class LengthVerifierFunctor {
 public:
  explicit LengthVerifierFunctor(size_t length) : length_(length) {}

  bool operator()(const std::vector<int>& v) const {
    return v.size() == length_;
  }

 private:
  size_t length_;
};

}  // anonymous namespace

TEST(SwapQueueTest, BasicOperation) {
  std::vector<int> i(kChunkSize, 0);
  SwapQueue<std::vector<int>> queue(2, i);

  EXPECT_TRUE(queue.Insert(&i));
  EXPECT_EQ(i.size(), kChunkSize);
  EXPECT_TRUE(queue.Insert(&i));
  EXPECT_EQ(i.size(), kChunkSize);
  EXPECT_TRUE(queue.Remove(&i));
  EXPECT_EQ(i.size(), kChunkSize);
  EXPECT_TRUE(queue.Remove(&i));
  EXPECT_EQ(i.size(), kChunkSize);
}

TEST(SwapQueueTest, FullQueue) {
  SwapQueue<int> queue(2);

  // Fill the queue.
  int i = 0;
  EXPECT_TRUE(queue.Insert(&i));
  i = 1;
  EXPECT_TRUE(queue.Insert(&i));

  // Ensure that the value is not swapped when doing an Insert
  // on a full queue.
  i = 2;
  EXPECT_FALSE(queue.Insert(&i));
  EXPECT_EQ(i, 2);

  // Ensure that the Insert didn't overwrite anything in the queue.
  EXPECT_TRUE(queue.Remove(&i));
  EXPECT_EQ(i, 0);
  EXPECT_TRUE(queue.Remove(&i));
  EXPECT_EQ(i, 1);
}

TEST(SwapQueueTest, EmptyQueue) {
  SwapQueue<int> queue(2);
  int i = 0;
  EXPECT_FALSE(queue.Remove(&i));
  EXPECT_TRUE(queue.Insert(&i));
  EXPECT_TRUE(queue.Remove(&i));
  EXPECT_FALSE(queue.Remove(&i));
}

TEST(SwapQueueTest, Clear) {
  SwapQueue<int> queue(2);
  int i = 0;

  // Fill the queue.
  EXPECT_TRUE(queue.Insert(&i));
  EXPECT_TRUE(queue.Insert(&i));

  // Ensure full queue.
  EXPECT_FALSE(queue.Insert(&i));

  // Empty the queue.
  queue.Clear();

  // Ensure that the queue is empty
  EXPECT_FALSE(queue.Remove(&i));

  // Ensure that the queue is no longer full.
  EXPECT_TRUE(queue.Insert(&i));
}

TEST(SwapQueueTest, SuccessfulItemVerifyFunction) {
  std::vector<int> template_element(kChunkSize);
  SwapQueue<std::vector<int>,
            SwapQueueItemVerifier<std::vector<int>, LengthVerifierFunction>>
      queue(2, template_element);
  std::vector<int> valid_chunk(kChunkSize, 0);

  EXPECT_TRUE(queue.Insert(&valid_chunk));
  EXPECT_EQ(valid_chunk.size(), kChunkSize);
  EXPECT_TRUE(queue.Remove(&valid_chunk));
  EXPECT_EQ(valid_chunk.size(), kChunkSize);
}

TEST(SwapQueueTest, SuccessfulItemVerifyFunctor) {
  std::vector<int> template_element(kChunkSize);
  LengthVerifierFunctor verifier(kChunkSize);
  SwapQueue<std::vector<int>, LengthVerifierFunctor> queue(2, template_element,
                                                           verifier);
  std::vector<int> valid_chunk(kChunkSize, 0);

  EXPECT_TRUE(queue.Insert(&valid_chunk));
  EXPECT_EQ(valid_chunk.size(), kChunkSize);
  EXPECT_TRUE(queue.Remove(&valid_chunk));
  EXPECT_EQ(valid_chunk.size(), kChunkSize);
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST(SwapQueueTest, UnsuccessfulItemVerifyFunctor) {
  // Queue item verifier for the test.
  auto minus_2_verifier = [](const int& i) { return i > -2; };
  SwapQueue<int, decltype(minus_2_verifier)> queue(2, minus_2_verifier);

  int valid_value = 1;
  int invalid_value = -4;
  EXPECT_TRUE(queue.Insert(&valid_value));
  EXPECT_TRUE(queue.Remove(&valid_value));
  bool result;
  EXPECT_DEATH(result = queue.Insert(&invalid_value), "");
}

TEST(SwapQueueTest, UnSuccessfulItemVerifyInsert) {
  std::vector<int> template_element(kChunkSize);
  SwapQueue<std::vector<int>,
            SwapQueueItemVerifier<std::vector<int>, &LengthVerifierFunction>>
      queue(2, template_element);
  std::vector<int> invalid_chunk(kChunkSize - 1, 0);
  bool result;
  EXPECT_DEATH(result = queue.Insert(&invalid_chunk), "");
}

TEST(SwapQueueTest, UnSuccessfulItemVerifyRemove) {
  std::vector<int> template_element(kChunkSize);
  SwapQueue<std::vector<int>,
            SwapQueueItemVerifier<std::vector<int>, &LengthVerifierFunction>>
      queue(2, template_element);
  std::vector<int> invalid_chunk(kChunkSize - 1, 0);
  std::vector<int> valid_chunk(kChunkSize, 0);
  EXPECT_TRUE(queue.Insert(&valid_chunk));
  EXPECT_EQ(valid_chunk.size(), kChunkSize);
  bool result;
  EXPECT_DEATH(result = queue.Remove(&invalid_chunk), "");
}
#endif

TEST(SwapQueueTest, VectorContentTest) {
  const size_t kQueueSize = 10;
  const size_t kFrameLength = 160;
  const size_t kDataLength = kQueueSize * kFrameLength;
  std::vector<int16_t> buffer_reader(kFrameLength, 0);
  std::vector<int16_t> buffer_writer(kFrameLength, 0);
  SwapQueue<std::vector<int16_t>> queue(kQueueSize,
                                        std::vector<int16_t>(kFrameLength));
  std::vector<int16_t> samples(kDataLength);

  for (size_t k = 0; k < kDataLength; k++) {
    samples[k] = k % 9;
  }

  for (size_t k = 0; k < kQueueSize; k++) {
    buffer_writer.clear();
    buffer_writer.insert(buffer_writer.end(), &samples[0] + k * kFrameLength,
                         &samples[0] + (k + 1) * kFrameLength);

    EXPECT_TRUE(queue.Insert(&buffer_writer));
  }

  for (size_t k = 0; k < kQueueSize; k++) {
    EXPECT_TRUE(queue.Remove(&buffer_reader));

    for (size_t j = 0; j < buffer_reader.size(); j++) {
      EXPECT_EQ(buffer_reader[j], samples[k * kFrameLength + j]);
    }
  }
}

TEST(SwapQueueTest, ZeroSlotQueue) {
  SwapQueue<int> queue(0);
  int i = 42;
  EXPECT_FALSE(queue.Insert(&i));
  EXPECT_FALSE(queue.Remove(&i));
  EXPECT_EQ(i, 42);
}

TEST(SwapQueueTest, OneSlotQueue) {
  SwapQueue<int> queue(1);
  int i = 42;
  EXPECT_TRUE(queue.Insert(&i));
  i = 43;
  EXPECT_FALSE(queue.Insert(&i));
  EXPECT_EQ(i, 43);
  EXPECT_TRUE(queue.Remove(&i));
  EXPECT_EQ(i, 42);
  EXPECT_FALSE(queue.Remove(&i));
}

}  // namespace webrtc
