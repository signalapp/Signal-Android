/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_video/include/i420_buffer_pool.h"

namespace webrtc {

TEST(TestI420BufferPool, SimpleFrameReuse) {
  I420BufferPool pool;
  rtc::scoped_refptr<VideoFrameBuffer> buffer = pool.CreateBuffer(16, 16);
  EXPECT_EQ(16, buffer->width());
  EXPECT_EQ(16, buffer->height());
  // Extract non-refcounted pointers for testing.
  const uint8_t* y_ptr = buffer->DataY();
  const uint8_t* u_ptr = buffer->DataU();
  const uint8_t* v_ptr = buffer->DataV();
  // Release buffer so that it is returned to the pool.
  buffer = nullptr;
  // Check that the memory is resued.
  buffer = pool.CreateBuffer(16, 16);
  EXPECT_EQ(y_ptr, buffer->DataY());
  EXPECT_EQ(u_ptr, buffer->DataU());
  EXPECT_EQ(v_ptr, buffer->DataV());
  EXPECT_EQ(16, buffer->width());
  EXPECT_EQ(16, buffer->height());
}

TEST(TestI420BufferPool, FailToReuse) {
  I420BufferPool pool;
  rtc::scoped_refptr<VideoFrameBuffer> buffer = pool.CreateBuffer(16, 16);
  // Extract non-refcounted pointers for testing.
  const uint8_t* u_ptr = buffer->DataU();
  const uint8_t* v_ptr = buffer->DataV();
  // Release buffer so that it is returned to the pool.
  buffer = nullptr;
  // Check that the pool doesn't try to reuse buffers of incorrect size.
  buffer = pool.CreateBuffer(32, 16);
  EXPECT_EQ(32, buffer->width());
  EXPECT_EQ(16, buffer->height());
  EXPECT_NE(u_ptr, buffer->DataU());
  EXPECT_NE(v_ptr, buffer->DataV());
}

TEST(TestI420BufferPool, FrameValidAfterPoolDestruction) {
  rtc::scoped_refptr<VideoFrameBuffer> buffer;
  {
    I420BufferPool pool;
    buffer = pool.CreateBuffer(16, 16);
  }
  EXPECT_EQ(16, buffer->width());
  EXPECT_EQ(16, buffer->height());
  // Try to trigger use-after-free errors by writing to y-plane.
  memset(buffer->MutableDataY(), 0xA5, 16 * buffer->StrideY());
}

}  // namespace webrtc
