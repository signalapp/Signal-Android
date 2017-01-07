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
#include "webrtc/base/winfirewall.h"

#include <objbase.h>

namespace rtc {

TEST(WinFirewallTest, ReadStatus) {
  ::CoInitialize(NULL);
  WinFirewall fw;
  HRESULT hr;
  bool authorized;

  EXPECT_FALSE(fw.QueryAuthorized("bogus.exe", &authorized));
  EXPECT_TRUE(fw.Initialize(&hr));
  EXPECT_EQ(S_OK, hr);

  EXPECT_TRUE(fw.QueryAuthorized("bogus.exe", &authorized));

  // Unless we mock out INetFwMgr we can't really have an expectation either way
  // about whether we're authorized.  It will depend on the settings of the
  // machine running the test.  Same goes for AddApplication.

  fw.Shutdown();
  EXPECT_FALSE(fw.QueryAuthorized("bogus.exe", &authorized));

  ::CoUninitialize();
}

}  // namespace rtc
