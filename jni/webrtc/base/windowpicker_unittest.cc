/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <memory>

#include "webrtc/base/gunit.h"
#include "webrtc/base/testutils.h"
#include "webrtc/base/window.h"
#include "webrtc/base/windowpicker.h"
#include "webrtc/base/windowpickerfactory.h"

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#  define DISABLE_ON_MAC(name) DISABLED_ ## name
#else
#  define DISABLE_ON_MAC(name) name
#endif

TEST(WindowPickerTest, GetWindowList) {
  MAYBE_SKIP_SCREENCAST_TEST();
  if (!rtc::WindowPickerFactory::IsSupported()) {
    LOG(LS_INFO) << "skipping test: window capturing is not supported with "
                 << "current configuration.";
  }
  std::unique_ptr<rtc::WindowPicker> picker(
      rtc::WindowPickerFactory::CreateWindowPicker());
  EXPECT_TRUE(picker->Init());
  rtc::WindowDescriptionList descriptions;
  EXPECT_TRUE(picker->GetWindowList(&descriptions));
}

// TODO(hughv) Investigate why this fails on pulse but not locally after
// upgrading to XCode 4.5.  The failure is GetDesktopList returning FALSE.
TEST(WindowPickerTest, DISABLE_ON_MAC(GetDesktopList)) {
  MAYBE_SKIP_SCREENCAST_TEST();
  if (!rtc::WindowPickerFactory::IsSupported()) {
    LOG(LS_INFO) << "skipping test: window capturing is not supported with "
                 << "current configuration.";
  }
  std::unique_ptr<rtc::WindowPicker> picker(
      rtc::WindowPickerFactory::CreateWindowPicker());
  EXPECT_TRUE(picker->Init());
  rtc::DesktopDescriptionList descriptions;
  EXPECT_TRUE(picker->GetDesktopList(&descriptions));
  if (descriptions.size() > 0) {
    int width = 0;
    int height = 0;
    EXPECT_TRUE(picker->GetDesktopDimensions(descriptions[0].id(), &width,
                                             &height));
    EXPECT_GT(width, 0);
    EXPECT_GT(height, 0);

    // Test |IsPrimaryDesktop|. Only one desktop should be a primary.
    bool found_primary = false;
    for (rtc::DesktopDescriptionList::iterator it = descriptions.begin();
         it != descriptions.end(); ++it) {
      if (it->primary()) {
        EXPECT_FALSE(found_primary);
        found_primary = true;
      }
    }
    EXPECT_TRUE(found_primary);
  }
}
