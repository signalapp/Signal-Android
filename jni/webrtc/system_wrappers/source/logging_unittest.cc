/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/include/logging.h"

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/arraysize.h"
#include "webrtc/base/event.h"
#include "webrtc/system_wrappers/include/trace.h"

namespace webrtc {
namespace {
const char kTestLogString[] = "Incredibly important test message!(?)";
const int kTestLevel = kTraceWarning;

class LoggingTestCallback : public TraceCallback {
 public:
  LoggingTestCallback(rtc::Event* event) : event_(event) {}

 private:
  void Print(TraceLevel level, const char* msg, int length) override {
    if (static_cast<size_t>(length) < arraysize(kTestLogString) ||
        level != kTestLevel) {
      return;
    }

    std::string msg_str(msg, length);
    if (msg_str.find(kTestLogString) != std::string::npos)
      event_->Set();
  }

  rtc::Event* const event_;
};

}  // namespace

TEST(LoggingTest, LogStream) {
  Trace::CreateTrace();

  rtc::Event event(false, false);
  LoggingTestCallback callback(&event);
  Trace::SetTraceCallback(&callback);

  LOG(LS_WARNING) << kTestLogString;
  EXPECT_TRUE(event.Wait(2000));

  Trace::SetTraceCallback(nullptr);
  Trace::ReturnTrace();
}
}  // namespace webrtc
