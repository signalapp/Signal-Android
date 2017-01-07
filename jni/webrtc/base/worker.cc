/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/worker.h"

#include "webrtc/base/common.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/thread.h"

namespace rtc {

enum {
  MSG_HAVEWORK = 0,
};

Worker::Worker() : worker_thread_(NULL) {}

Worker::~Worker() {
  // We need to already be stopped before being destroyed. We cannot call
  // StopWork() from here because the subclass's data has already been
  // destructed, so OnStop() cannot be called.
  ASSERT(!worker_thread_);
}

bool Worker::StartWork() {
  rtc::Thread *me = rtc::Thread::Current();
  if (worker_thread_) {
    if (worker_thread_ == me) {
      // Already working on this thread, so nothing to do.
      return true;
    } else {
      LOG(LS_ERROR) << "Automatically switching threads is not supported";
      ASSERT(false);
      return false;
    }
  }
  worker_thread_ = me;
  OnStart();
  return true;
}

bool Worker::StopWork() {
  if (!worker_thread_) {
    // Already not working, so nothing to do.
    return true;
  } else if (worker_thread_ != rtc::Thread::Current()) {
    LOG(LS_ERROR) << "Stopping from a different thread is not supported";
    ASSERT(false);
    return false;
  }
  OnStop();
  worker_thread_->Clear(this, MSG_HAVEWORK);
  worker_thread_ = NULL;
  return true;
}

void Worker::HaveWork() {
  ASSERT(worker_thread_ != NULL);
  worker_thread_->Post(RTC_FROM_HERE, this, MSG_HAVEWORK);
}

void Worker::OnMessage(rtc::Message *msg) {
  ASSERT(msg->message_id == MSG_HAVEWORK);
  ASSERT(worker_thread_ == rtc::Thread::Current());
  OnHaveWork();
}

}  // namespace rtc
