/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/copyonwritebuffer.h"

namespace rtc {

CopyOnWriteBuffer::CopyOnWriteBuffer() {
  RTC_DCHECK(IsConsistent());
}

CopyOnWriteBuffer::CopyOnWriteBuffer(const CopyOnWriteBuffer& buf)
    : buffer_(buf.buffer_) {
}

CopyOnWriteBuffer::CopyOnWriteBuffer(CopyOnWriteBuffer&& buf) {
  // TODO(jbauch): use std::move once scoped_refptr supports it (issue 5556)
  std::swap(buffer_, buf.buffer_);
}

CopyOnWriteBuffer::CopyOnWriteBuffer(size_t size)
    : buffer_(size > 0 ? new RefCountedObject<Buffer>(size) : nullptr) {
  RTC_DCHECK(IsConsistent());
}

CopyOnWriteBuffer::CopyOnWriteBuffer(size_t size, size_t capacity)
    : buffer_(size > 0 || capacity > 0
          ? new RefCountedObject<Buffer>(size, capacity)
          : nullptr) {
  RTC_DCHECK(IsConsistent());
}

CopyOnWriteBuffer::~CopyOnWriteBuffer() = default;

}  // namespace rtc
