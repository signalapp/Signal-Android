/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_POSIX_H_
#define WEBRTC_BASE_POSIX_H_

namespace rtc {

// Runs the given executable name as a daemon, so that it executes concurrently
// with this process. Upon completion, the daemon process will automatically be
// reaped by init(8), so an error exit status or a failure to start the
// executable are not reported. Returns true if the daemon process was forked
// successfully, else false.
bool RunAsDaemon(const char *file, const char *const argv[]);

}  // namespace rtc

#endif  // WEBRTC_BASE_POSIX_H_
