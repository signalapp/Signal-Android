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
#include "webrtc/base/x11windowpicker.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/testutils.h"
#include "webrtc/base/windowpicker.h"

#if !defined(WEBRTC_LINUX) || defined(WEBRTC_ANDROID)
#error Only for Linux
#endif

namespace rtc {

TEST(X11WindowPickerTest, TestGetWindowList) {
  MAYBE_SKIP_SCREENCAST_TEST();
  X11WindowPicker window_picker;
  WindowDescriptionList descriptions;
  window_picker.Init();
  window_picker.GetWindowList(&descriptions);
}

TEST(X11WindowPickerTest, TestGetDesktopList) {
  MAYBE_SKIP_SCREENCAST_TEST();
  X11WindowPicker window_picker;
  DesktopDescriptionList descriptions;
  EXPECT_TRUE(window_picker.Init());
  EXPECT_TRUE(window_picker.GetDesktopList(&descriptions));
  EXPECT_TRUE(descriptions.size() > 0);
}

}  // namespace rtc
