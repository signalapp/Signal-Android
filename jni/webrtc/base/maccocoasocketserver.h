/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// A libjingle compatible SocketServer for OSX/iOS/Cocoa.

#ifndef WEBRTC_BASE_MACCOCOASOCKETSERVER_H_
#define WEBRTC_BASE_MACCOCOASOCKETSERVER_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/macsocketserver.h"

#ifdef __OBJC__
@class NSTimer, MacCocoaSocketServerHelperRtc;
#else
class NSTimer;
class MacCocoaSocketServerHelperRtc;
#endif

namespace rtc {

// A socketserver implementation that wraps the main cocoa
// application loop accessed through [NSApp run].
class MacCocoaSocketServer : public MacBaseSocketServer {
 public:
  explicit MacCocoaSocketServer();
  ~MacCocoaSocketServer() override;

  bool Wait(int cms, bool process_io) override;
  void WakeUp() override;

 private:
  MacCocoaSocketServerHelperRtc* helper_;
  NSTimer* timer_;  // Weak.
  // The count of how many times we're inside the NSApplication main loop.
  int run_count_;

  RTC_DISALLOW_COPY_AND_ASSIGN(MacCocoaSocketServer);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_MACCOCOASOCKETSERVER_H_
