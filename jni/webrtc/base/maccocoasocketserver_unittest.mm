/*
 *  Copyright 2009 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/maccocoasocketserver.h"

namespace rtc {

class WakeThread : public Thread {
 public:
  WakeThread(SocketServer* ss) : ss_(ss) {
  }
  virtual ~WakeThread() {
    Stop();
  }
  void Run() {
    ss_->WakeUp();
  }
 private:
  SocketServer* ss_;
};

// Test that MacCocoaSocketServer::Wait works as expected.
TEST(MacCocoaSocketServer, TestWait) {
  MacCocoaSocketServer server;
  uint32_t start = Time();
  server.Wait(1000, true);
  EXPECT_GE(TimeSince(start), 1000);
}

// Test that MacCocoaSocketServer::Wakeup works as expected.
TEST(MacCocoaSocketServer, TestWakeup) {
  MacCFSocketServer server;
  WakeThread thread(&server);
  uint32_t start = Time();
  thread.Start();
  server.Wait(10000, true);
  EXPECT_LT(TimeSince(start), 10000);
}

} // namespace rtc
