/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Helper function for using Cocoa with Posix threads. This header should be
// included from C/C++ files that want to use some Cocoa functionality without
// using the .mm extension (mostly for files that are compiled on multiple
// platforms).

#ifndef WEBRTC_BASE_MACCOCOATHREADHELPER_H__
#define WEBRTC_BASE_MACCOCOATHREADHELPER_H__

namespace rtc {

// Cocoa must be "put into multithreading mode" before Cocoa functionality can
// be used on POSIX threads. This function does that.
void InitCocoaMultiThreading();

}  // namespace rtc

#endif  // WEBRTC_BASE_MACCOCOATHREADHELPER_H__
