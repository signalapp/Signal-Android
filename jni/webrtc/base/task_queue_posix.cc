/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/task_queue_posix.h"

#include "webrtc/base/checks.h"
#include "webrtc/base/task_queue.h"

namespace rtc {
namespace internal {
pthread_key_t g_queue_ptr_tls = 0;

void InitializeTls() {
  RTC_CHECK(pthread_key_create(&g_queue_ptr_tls, nullptr) == 0);
}

pthread_key_t GetQueuePtrTls() {
  static pthread_once_t init_once = PTHREAD_ONCE_INIT;
  RTC_CHECK(pthread_once(&init_once, &InitializeTls) == 0);
  return g_queue_ptr_tls;
}

AutoSetCurrentQueuePtr::AutoSetCurrentQueuePtr(TaskQueue* q)
    : prev_(TaskQueue::Current()) {
  pthread_setspecific(GetQueuePtrTls(), q);
}

AutoSetCurrentQueuePtr::~AutoSetCurrentQueuePtr() {
  pthread_setspecific(GetQueuePtrTls(), prev_);
}

}  // namespace internal
}  // namespace rtc
