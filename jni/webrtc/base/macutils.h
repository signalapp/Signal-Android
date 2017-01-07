/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_MACUTILS_H__
#define WEBRTC_BASE_MACUTILS_H__

#include <CoreFoundation/CoreFoundation.h>
#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include <Carbon/Carbon.h>
#endif
#include <string>

namespace rtc {

///////////////////////////////////////////////////////////////////////////////

// Note that some of these functions work for both iOS and Mac OS X.  The ones
// that are specific to Mac are #ifdef'ed as such.

bool ToUtf8(const CFStringRef str16, std::string* str8);
bool ToUtf16(const std::string& str8, CFStringRef* str16);

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
void DecodeFourChar(UInt32 fc, std::string* out);

enum MacOSVersionName {
  kMacOSUnknown,       // ???
  kMacOSOlder,         // 10.2-
  kMacOSPanther,       // 10.3
  kMacOSTiger,         // 10.4
  kMacOSLeopard,       // 10.5
  kMacOSSnowLeopard,   // 10.6
  kMacOSLion,          // 10.7
  kMacOSMountainLion,  // 10.8
  kMacOSMavericks,     // 10.9
  kMacOSNewer,         // 10.10+
};

bool GetOSVersion(int* major, int* minor, int* bugfix);
MacOSVersionName GetOSVersionName();
bool GetQuickTimeVersion(std::string* version);

// Runs the given apple script. Only supports scripts that does not
// require user interaction.
bool RunAppleScript(const std::string& script);
#endif

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_BASE_MACUTILS_H__
