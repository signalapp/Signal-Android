/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TASK_QUEUE_POSIX_H_
#define WEBRTC_BASE_TASK_QUEUE_POSIX_H_

#include <pthread.h>

namespace rtc {

class TaskQueue;

namespace internal {

class AutoSetCurrentQueuePtr {
 public:
  explicit AutoSetCurrentQueuePtr(TaskQueue* q);
  ~AutoSetCurrentQueuePtr();

 private:
  TaskQueue* const prev_;
};

pthread_key_t GetQueuePtrTls();

}  // namespace internal
}  // namespace rtc

#endif  // WEBRTC_BASE_TASK_QUEUE_POSIX_H_
