/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// A fake TaskRunner for use in unit tests.

#ifndef WEBRTC_BASE_FAKETASKRUNNER_H_
#define WEBRTC_BASE_FAKETASKRUNNER_H_

#include "webrtc/base/taskparent.h"
#include "webrtc/base/taskrunner.h"

namespace rtc {

class FakeTaskRunner : public TaskRunner {
 public:
  FakeTaskRunner() : current_time_(0) {}
  virtual ~FakeTaskRunner() {}

  virtual void WakeTasks() { RunTasks(); }

  virtual int64_t CurrentTime() {
    // Implement if needed.
    return current_time_++;
  }

  int64_t current_time_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_FAKETASKRUNNER_H_
