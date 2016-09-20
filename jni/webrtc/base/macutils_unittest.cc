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
#include "webrtc/base/macutils.h"

TEST(MacUtilsTest, GetOsVersionName) {
  rtc::MacOSVersionName ver = rtc::GetOSVersionName();
  LOG(LS_INFO) << "GetOsVersionName " << ver;
  EXPECT_NE(rtc::kMacOSUnknown, ver);
}

TEST(MacUtilsTest, GetQuickTimeVersion) {
  std::string version;
  EXPECT_TRUE(rtc::GetQuickTimeVersion(&version));
  LOG(LS_INFO) << "GetQuickTimeVersion " << version;
}

TEST(MacUtilsTest, RunAppleScriptCompileError) {
  std::string script("set value to to 5");
  EXPECT_FALSE(rtc::RunAppleScript(script));
}

TEST(MacUtilsTest, RunAppleScriptRuntimeError) {
  std::string script("set value to 5 / 0");
  EXPECT_FALSE(rtc::RunAppleScript(script));
}

#ifdef CARBON_DEPRECATED
TEST(MacUtilsTest, DISABLED_RunAppleScriptSuccess) {
#else
TEST(MacUtilsTest, RunAppleScriptSuccess) {
#endif
  std::string script("set value to 5");
  EXPECT_TRUE(rtc::RunAppleScript(script));
}
