/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/systeminfo.h"

#if defined(WEBRTC_WIN)
#include <winsock2.h>
#include <windows.h>
#ifndef EXCLUDE_D3D9
#include <d3d9.h>
#endif
#include <intrin.h>  // for __cpuid()
#elif defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include <ApplicationServices/ApplicationServices.h>
#include <CoreServices/CoreServices.h>
#elif defined(WEBRTC_LINUX)
#include <unistd.h>
#endif
#if defined(WEBRTC_MAC)
#include <sys/sysctl.h>
#endif

#include "webrtc/base/common.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/stringutils.h"

namespace rtc {

// See Also: http://msdn.microsoft.com/en-us/library/ms683194(v=vs.85).aspx
#if !defined(WEBRTC_WIN)
// TODO(fbarchard): Use gcc 4.4 provided cpuid intrinsic
// 32 bit fpic requires ebx be preserved
#if (defined(__pic__) || defined(__APPLE__)) && defined(__i386__)
static inline void __cpuid(int cpu_info[4], int info_type) {
  __asm__ volatile (  // NOLINT
    "mov %%ebx, %%edi\n"
    "cpuid\n"
    "xchg %%edi, %%ebx\n"
    : "=a"(cpu_info[0]), "=D"(cpu_info[1]), "=c"(cpu_info[2]), "=d"(cpu_info[3])
    : "a"(info_type)
  );  // NOLINT
}
#elif defined(__i386__) || defined(__x86_64__)
static inline void __cpuid(int cpu_info[4], int info_type) {
  __asm__ volatile (  // NOLINT
    "cpuid\n"
    : "=a"(cpu_info[0]), "=b"(cpu_info[1]), "=c"(cpu_info[2]), "=d"(cpu_info[3])
    : "a"(info_type)
  );  // NOLINT
}
#endif
#endif  // WEBRTC_WIN

static int DetectNumberOfCores() {
  // We fall back on assuming a single core in case of errors.
  int number_of_cores = 1;

#if defined(WEBRTC_WIN)
  SYSTEM_INFO si;
  GetSystemInfo(&si);
  number_of_cores = static_cast<int>(si.dwNumberOfProcessors);
#elif defined(WEBRTC_LINUX) || defined(WEBRTC_ANDROID)
  number_of_cores = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
#elif defined(WEBRTC_MAC)
  int name[] = {CTL_HW, HW_AVAILCPU};
  size_t size = sizeof(number_of_cores);
  if (0 != sysctl(name, 2, &number_of_cores, &size, NULL, 0)) {
    LOG(LS_ERROR) << "Failed to get number of cores";
    number_of_cores = 1;
  }
#else
  LOG(LS_ERROR) << "No function to get number of cores";
#endif

  LOG(LS_INFO) << "Available number of cores: " << number_of_cores;

  return number_of_cores;
}

// Statically cache the number of system cores available since if the process
// is running in a sandbox, we may only be able to read the value once (before
// the sandbox is initialized) and not thereafter.
// For more information see crbug.com/176522.
int SystemInfo::logical_cpus_ = 0;

SystemInfo::SystemInfo() {
}

// Return the number of cpu threads available to the system.
// static
int SystemInfo::GetMaxCpus() {
  if (!logical_cpus_)
    logical_cpus_ = DetectNumberOfCores();
  return logical_cpus_;
}

// Return the number of cpus available to the process.  Since affinity can be
// changed on the fly, do not cache this value.
// Can be affected by heat.
int SystemInfo::GetCurCpus() {
  int cur_cpus = 0;
#if defined(WEBRTC_WIN)
  DWORD_PTR process_mask = 0;
  DWORD_PTR system_mask = 0;
  ::GetProcessAffinityMask(::GetCurrentProcess(), &process_mask, &system_mask);
  for (size_t i = 0; i < sizeof(DWORD_PTR) * 8; ++i) {
    if (process_mask & 1)
      ++cur_cpus;
    process_mask >>= 1;
  }
#elif defined(WEBRTC_MAC)
  uint32_t sysctl_value;
  size_t length = sizeof(sysctl_value);
  int error = sysctlbyname("hw.ncpu", &sysctl_value, &length, NULL, 0);
  cur_cpus = !error ? static_cast<int>(sysctl_value) : 1;
#else
  // Linux, Solaris, WEBRTC_ANDROID
  cur_cpus = GetMaxCpus();
#endif
  return cur_cpus;
}

// Return the type of this CPU.
SystemInfo::Architecture SystemInfo::GetCpuArchitecture() {
#if defined(__arm__) || defined(_M_ARM)
  return SI_ARCH_ARM;
#elif defined(__x86_64__) || defined(_M_X64)
  return SI_ARCH_X64;
#elif defined(__i386__) || defined(_M_IX86)
  return SI_ARCH_X86;
#else
  return SI_ARCH_UNKNOWN;
#endif
}

// Returns the vendor string from the cpu, e.g. "GenuineIntel", "AuthenticAMD".
// See "Intel Processor Identification and the CPUID Instruction"
// (Intel document number: 241618)
std::string SystemInfo::GetCpuVendor() {
#if defined(CPU_X86)
  int cpu_info[4];
  __cpuid(cpu_info, 0);
  cpu_info[0] = cpu_info[1];  // Reorder output
  cpu_info[1] = cpu_info[3];
  // cpu_info[2] = cpu_info[2];  // Avoid -Werror=self-assign
  cpu_info[3] = 0;
  return std::string(reinterpret_cast<char*>(&cpu_info[0]));
#elif defined(CPU_ARM)
  return "ARM";
#else
  return "Undefined";
#endif
}

// Returns the amount of installed physical memory in Bytes.  Cacheable.
// Returns -1 on error.
int64_t SystemInfo::GetMemorySize() {
  int64_t memory = -1;

#if defined(WEBRTC_WIN)
  MEMORYSTATUSEX status = {0};
  status.dwLength = sizeof(status);

  if (GlobalMemoryStatusEx(&status)) {
    memory = status.ullTotalPhys;
  } else {
    LOG_GLE(LS_WARNING) << "GlobalMemoryStatusEx failed.";
  }

#elif defined(WEBRTC_MAC)
  size_t len = sizeof(memory);
  int error = sysctlbyname("hw.memsize", &memory, &len, NULL, 0);
  if (error || memory == 0)
    memory = -1;
#elif defined(WEBRTC_LINUX)
  memory = static_cast<int64_t>(sysconf(_SC_PHYS_PAGES)) *
           static_cast<int64_t>(sysconf(_SC_PAGESIZE));
  if (memory < 0) {
    LOG(LS_WARNING) << "sysconf(_SC_PHYS_PAGES) failed."
                    << "sysconf(_SC_PHYS_PAGES) " << sysconf(_SC_PHYS_PAGES)
                    << "sysconf(_SC_PAGESIZE) " << sysconf(_SC_PAGESIZE);
    memory = -1;
  }
#endif

  return memory;
}

// Return the name of the machine model we are currently running on.
// This is a human readable string that consists of the name and version
// number of the hardware, i.e 'MacBookAir1,1'. Returns an empty string if
// model can not be determined.
std::string SystemInfo::GetMachineModel() {
#if defined(WEBRTC_MAC)
  char buffer[128];
  size_t length = sizeof(buffer);
  int error = sysctlbyname("hw.model", buffer, &length, NULL, 0);
  if (!error)
    return std::string(buffer, length - 1);
  return std::string();
#else
  return "Not available";
#endif
}

}  // namespace rtc
