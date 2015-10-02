/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/cpu_info.h"

#if defined(_WIN32)
#include <Windows.h>
#elif defined(WEBRTC_MAC)
#include <sys/sysctl.h>
#include <sys/types.h>
#else // defined(WEBRTC_LINUX) or defined(WEBRTC_ANDROID)
#include <unistd.h>
#endif

#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {

uint32_t CpuInfo::number_of_cores_ = 0;

uint32_t CpuInfo::DetectNumberOfCores() {
  if (!number_of_cores_) {
#if defined(_WIN32)
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    number_of_cores_ = static_cast<uint32_t>(si.dwNumberOfProcessors);
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1,
                 "Available number of cores:%d", number_of_cores_);

#elif defined(WEBRTC_LINUX) || defined(WEBRTC_ANDROID)
    number_of_cores_ = static_cast<uint32_t>(sysconf(_SC_NPROCESSORS_ONLN));
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1,
                 "Available number of cores:%d", number_of_cores_);

#elif defined(WEBRTC_MAC)
    int name[] = {CTL_HW, HW_AVAILCPU};
    int ncpu;
    size_t size = sizeof(ncpu);
    if (0 == sysctl(name, 2, &ncpu, &size, NULL, 0)) {
      number_of_cores_ = static_cast<uint32_t>(ncpu);
      WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1,
                   "Available number of cores:%d", number_of_cores_);
    } else {
      WEBRTC_TRACE(kTraceError, kTraceUtility, -1,
                   "Failed to get number of cores");
      number_of_cores_ = 1;
    }
#else
    WEBRTC_TRACE(kTraceWarning, kTraceUtility, -1,
                 "No function to get number of cores");
    number_of_cores_ = 1;
#endif
  }
  return number_of_cores_;
}

}  // namespace webrtc
