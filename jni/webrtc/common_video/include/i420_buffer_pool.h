/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_VIDEO_INCLUDE_I420_BUFFER_POOL_H_
#define WEBRTC_COMMON_VIDEO_INCLUDE_I420_BUFFER_POOL_H_

#include <list>

#include "webrtc/base/thread_checker.h"
#include "webrtc/common_video/include/video_frame_buffer.h"

namespace webrtc {

// Simple buffer pool to avoid unnecessary allocations of I420Buffer objects.
// The pool manages the memory of the I420Buffer returned from CreateBuffer.
// When the I420Buffer is destructed, the memory is returned to the pool for use
// by subsequent calls to CreateBuffer. If the resolution passed to CreateBuffer
// changes, old buffers will be purged from the pool.
class I420BufferPool {
 public:
  I420BufferPool() : I420BufferPool(false) {}
  explicit I420BufferPool(bool zero_initialize);

  // Returns a buffer from the pool, or creates a new buffer if no suitable
  // buffer exists in the pool.
  rtc::scoped_refptr<I420Buffer> CreateBuffer(int width, int height);
  // Clears buffers_ and detaches the thread checker so that it can be reused
  // later from another thread.
  void Release();

 private:
  // Explicitly use a RefCountedObject to get access to HasOneRef,
  // needed by the pool to check exclusive access.
  using PooledI420Buffer = rtc::RefCountedObject<I420Buffer>;

  rtc::ThreadChecker thread_checker_;
  std::list<rtc::scoped_refptr<PooledI420Buffer>> buffers_;
  // If true, newly allocated buffers are zero-initialized. Note that recycled
  // buffers are not zero'd before reuse. This is required of buffers used by
  // FFmpeg according to http://crbug.com/390941, which only requires it for the
  // initial allocation (as shown by FFmpeg's own buffer allocation code). It
  // has to do with "Use-of-uninitialized-value" on "Linux_msan_chrome".
  bool zero_initialize_;
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_VIDEO_INCLUDE_I420_BUFFER_POOL_H_
