/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "webrtc/base/gunit.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/macutils.h"
#include "webrtc/base/macwindowpicker.h"
#include "webrtc/base/windowpicker.h"

#if !defined(WEBRTC_MAC) || defined(WEBRTC_IOS)
#error Only for WEBRTC_MAC && !WEBRTC_IOS
#endif

namespace rtc {

bool IsLeopardOrLater() {
  return GetOSVersionName() >= kMacOSLeopard;
}

// Test that this works on new versions and fails acceptably on old versions.
TEST(MacWindowPickerTest, TestGetWindowList) {
  MacWindowPicker picker, picker2;
  WindowDescriptionList descriptions;
  if (IsLeopardOrLater()) {
    EXPECT_TRUE(picker.Init());
    EXPECT_TRUE(picker.GetWindowList(&descriptions));
    EXPECT_TRUE(picker2.GetWindowList(&descriptions));  // Init is optional
  } else {
    EXPECT_FALSE(picker.Init());
    EXPECT_FALSE(picker.GetWindowList(&descriptions));
    EXPECT_FALSE(picker2.GetWindowList(&descriptions));
  }
}

// TODO: Add verification of the actual parsing, ie, add
// functionality to inject a fake get_window_array function which
// provide a pre-constructed list of windows.

}  // namespace rtc
