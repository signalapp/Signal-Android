/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_LINUXFDWALK_H_
#define WEBRTC_BASE_LINUXFDWALK_H_

#ifdef __cplusplus
extern "C" {
#endif

// Linux port of SunOS's fdwalk(3) call. It loops over all open file descriptors
// and calls func on each one. Additionally, it is safe to use from the child
// of a fork that hasn't exec'ed yet, so you can use it to close all open file
// descriptors prior to exec'ing a daemon.
// The return value is 0 if successful, or else -1 and errno is set. The
// possible errors include any error that can be returned by opendir(),
// readdir(), or closedir(), plus EBADF if there are problems parsing the
// contents of /proc/self/fd.
// The file descriptors that are enumerated will not include the file descriptor
// used for the enumeration itself.
int fdwalk(void (*func)(void *, int), void *opaque);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // WEBRTC_BASE_LINUXFDWALK_H_
