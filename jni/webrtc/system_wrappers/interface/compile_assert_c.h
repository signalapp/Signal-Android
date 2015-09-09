/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_INTERFACE_COMPILE_ASSERT_H_
#define WEBRTC_SYSTEM_WRAPPERS_INTERFACE_COMPILE_ASSERT_H_

// Only use this for C files. For C++, use compile_assert.h.
//
// Use this macro to verify at compile time that certain restrictions are met.
// The argument is the boolean expression to evaluate.
// Example:
//   COMPILE_ASSERT(sizeof(foo) < 128);
#define COMPILE_ASSERT(expression) switch (0) {case 0: case expression:;}

#endif  // WEBRTC_SYSTEM_WRAPPERS_INTERFACE_COMPILE_ASSERT_H_
