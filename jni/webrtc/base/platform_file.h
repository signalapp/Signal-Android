/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_PLATFORM_FILE_H_
#define WEBRTC_BASE_PLATFORM_FILE_H_

#include <stdio.h>

#if defined(WEBRTC_WIN)
#include <windows.h>
#endif

namespace rtc {

#if defined(WEBRTC_WIN)
typedef HANDLE PlatformFile;
#elif defined(WEBRTC_POSIX)
typedef int PlatformFile;
#else
#error Unsupported platform
#endif

extern const PlatformFile kInvalidPlatformFileValue;

// Associates a standard FILE stream with an existing PlatformFile.
// Note that after this function has returned a valid FILE stream,
// the PlatformFile should no longer be used.
FILE* FdopenPlatformFileForWriting(PlatformFile file);

// Closes a PlatformFile.
// Don't use ClosePlatformFile to close a file opened with FdopenPlatformFile.
// Use fclose instead.
bool ClosePlatformFile(PlatformFile file);

}  // namespace rtc

#endif  // WEBRTC_BASE_PLATFORM_FILE_H_
