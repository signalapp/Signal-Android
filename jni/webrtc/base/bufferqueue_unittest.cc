/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/bufferqueue.h"
#include "webrtc/base/gunit.h"

namespace rtc {

TEST(BufferQueueTest, TestAll) {
  const size_t kSize = 16;
  const char in[kSize * 2 + 1] = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
  char out[kSize * 2];
  size_t bytes;
  BufferQueue queue1(1, kSize);
  BufferQueue queue2(2, kSize);

  // The queue is initially empty.
  EXPECT_EQ(0u, queue1.size());
  EXPECT_FALSE(queue1.ReadFront(out, kSize, &bytes));

  // A write should succeed.
  EXPECT_TRUE(queue1.WriteBack(in, kSize, &bytes));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(1u, queue1.size());

  // The queue is full now (only one buffer allowed).
  EXPECT_FALSE(queue1.WriteBack(in, kSize, &bytes));
  EXPECT_EQ(1u, queue1.size());

  // Reading previously written buffer.
  EXPECT_TRUE(queue1.ReadFront(out, kSize, &bytes));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize));

  // The queue is empty again now.
  EXPECT_FALSE(queue1.ReadFront(out, kSize, &bytes));
  EXPECT_EQ(0u, queue1.size());

  // Reading only returns available data.
  EXPECT_TRUE(queue1.WriteBack(in, kSize, &bytes));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(1u, queue1.size());
  EXPECT_TRUE(queue1.ReadFront(out, kSize * 2, &bytes));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize));
  EXPECT_EQ(0u, queue1.size());

  // Reading maintains buffer boundaries.
  EXPECT_TRUE(queue2.WriteBack(in, kSize / 2, &bytes));
  EXPECT_EQ(1u, queue2.size());
  EXPECT_TRUE(queue2.WriteBack(in + kSize / 2, kSize / 2, &bytes));
  EXPECT_EQ(2u, queue2.size());
  EXPECT_TRUE(queue2.ReadFront(out, kSize, &bytes));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize / 2));
  EXPECT_EQ(1u, queue2.size());
  EXPECT_TRUE(queue2.ReadFront(out, kSize, &bytes));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(in + kSize / 2, out, kSize / 2));
  EXPECT_EQ(0u, queue2.size());

  // Reading truncates buffers.
  EXPECT_TRUE(queue2.WriteBack(in, kSize / 2, &bytes));
  EXPECT_EQ(1u, queue2.size());
  EXPECT_TRUE(queue2.WriteBack(in + kSize / 2, kSize / 2, &bytes));
  EXPECT_EQ(2u, queue2.size());
  // Read first packet partially in too-small buffer.
  EXPECT_TRUE(queue2.ReadFront(out, kSize / 4, &bytes));
  EXPECT_EQ(kSize / 4, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize / 4));
  EXPECT_EQ(1u, queue2.size());
  // Remainder of first packet is truncated, reading starts with next packet.
  EXPECT_TRUE(queue2.ReadFront(out, kSize, &bytes));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(in + kSize / 2, out, kSize / 2));
  EXPECT_EQ(0u, queue2.size());
}

}  // namespace rtc
