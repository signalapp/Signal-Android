/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/platform_file.h"

#if defined(WEBRTC_WIN)
#include <io.h>
#else
#include <unistd.h>
#endif

namespace rtc {

#if defined(WEBRTC_WIN)
const PlatformFile kInvalidPlatformFileValue = INVALID_HANDLE_VALUE;

FILE* FdopenPlatformFileForWriting(PlatformFile file) {
  if (file == kInvalidPlatformFileValue)
    return NULL;
  int fd = _open_osfhandle(reinterpret_cast<intptr_t>(file), 0);
  if (fd < 0)
    return NULL;

  return _fdopen(fd, "w");
}

bool ClosePlatformFile(PlatformFile file) {
  return CloseHandle(file) != 0;
}
#else
const PlatformFile kInvalidPlatformFileValue = -1;

FILE* FdopenPlatformFileForWriting(PlatformFile file) {
  return fdopen(file, "w");
}

bool ClosePlatformFile(PlatformFile file) {
  return close(file);
}
#endif

}  // namespace rtc
