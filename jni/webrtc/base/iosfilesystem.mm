/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file only exists because various iOS system APIs are only
// available from Objective-C.  See unixfilesystem.cc for the only use
// (enforced by a lack of a header file).

#import <Foundation/NSPathUtilities.h>
#import <Foundation/NSProcessInfo.h>
#include <string.h>

#include "webrtc/base/common.h"
#include "webrtc/base/pathutils.h"

// Return a new[]'d |char*| copy of the UTF8 representation of |s|.
// Caller owns the returned memory and must use delete[] on it.
static char* copyString(NSString* s) {
  const char* utf8 = [s UTF8String];
  size_t len = strlen(utf8) + 1;
  char* copy = new char[len];
  // This uses a new[] + strcpy (instead of strdup) because the
  // receiver expects to be able to delete[] the returned pointer
  // (instead of free()ing it).
  strcpy(copy, utf8);
  return copy;
}

// Return a (leaked) copy of a directory name suitable for application data.
char* IOSDataDirectory() {
  NSArray* paths = NSSearchPathForDirectoriesInDomains(
      NSApplicationSupportDirectory, NSUserDomainMask, YES);
  ASSERT([paths count] == 1);
  return copyString([paths objectAtIndex:0]);
}

// Return a (leaked) copy of a directory name suitable for use as a $TEMP.
char* IOSTempDirectory() {
  return copyString(NSTemporaryDirectory());
}

// Return the binary's path.
void IOSAppName(rtc::Pathname* path) {
  NSProcessInfo *pInfo = [NSProcessInfo processInfo];
  NSString* argv0 = [[pInfo arguments] objectAtIndex:0];
  path->SetPathname([argv0 UTF8String]);
}
