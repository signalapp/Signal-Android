/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SYSTEMINFO_H__
#define WEBRTC_BASE_SYSTEMINFO_H__

#include <string>

#include "webrtc/base/basictypes.h"

namespace rtc {

class SystemInfo {
 public:
  enum Architecture {
    SI_ARCH_UNKNOWN = -1,
    SI_ARCH_X86 = 0,
    SI_ARCH_X64 = 1,
    SI_ARCH_ARM = 2
  };

  SystemInfo();

  // The number of CPU Threads in the system.
  static int GetMaxCpus();
  // The number of CPU Threads currently available to this process.
  static int GetCurCpus();
  // Identity of the CPUs.
  Architecture GetCpuArchitecture();
  std::string GetCpuVendor();
  // Total amount of physical memory, in bytes.
  int64_t GetMemorySize();
  // The model name of the machine, e.g. "MacBookAir1,1"
  std::string GetMachineModel();

 private:
  static int logical_cpus_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_SYSTEMINFO_H__
