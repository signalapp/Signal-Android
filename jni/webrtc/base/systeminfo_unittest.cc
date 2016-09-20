/*
 *  Copyright 2009 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/stringutils.h"
#include "webrtc/base/systeminfo.h"

#if defined(CPU_X86) || defined(CPU_ARM)
TEST(SystemInfoTest, CpuVendorNonEmpty) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CpuVendor: " << info.GetCpuVendor();
  EXPECT_FALSE(info.GetCpuVendor().empty());
}

// Tests Vendor identification is Intel or AMD.
// See Also http://en.wikipedia.org/wiki/CPUID
TEST(SystemInfoTest, CpuVendorIntelAMDARM) {
  rtc::SystemInfo info;
#if defined(CPU_X86)
  EXPECT_TRUE(rtc::string_match(info.GetCpuVendor().c_str(),
                                      "GenuineIntel") ||
              rtc::string_match(info.GetCpuVendor().c_str(),
                                      "AuthenticAMD"));
#elif defined(CPU_ARM)
  EXPECT_TRUE(rtc::string_match(info.GetCpuVendor().c_str(), "ARM"));
#endif
}
#endif  // defined(CPU_X86) || defined(CPU_ARM)

// Tests CpuArchitecture matches expectations.
TEST(SystemInfoTest, GetCpuArchitecture) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CpuArchitecture: " << info.GetCpuArchitecture();
  rtc::SystemInfo::Architecture architecture = info.GetCpuArchitecture();
#if defined(CPU_X86) || defined(CPU_ARM)
  if (sizeof(intptr_t) == 8) {
    EXPECT_EQ(rtc::SystemInfo::SI_ARCH_X64, architecture);
  } else if (sizeof(intptr_t) == 4) {
#if defined(CPU_ARM)
    EXPECT_EQ(rtc::SystemInfo::SI_ARCH_ARM, architecture);
#else
    EXPECT_EQ(rtc::SystemInfo::SI_ARCH_X86, architecture);
#endif
  }
#endif
}

// Tests MachineModel is set.  On Mac test machine model is known.
TEST(SystemInfoTest, MachineModelKnown) {
  rtc::SystemInfo info;
  EXPECT_FALSE(info.GetMachineModel().empty());
  const char *machine_model = info.GetMachineModel().c_str();
  LOG(LS_INFO) << "MachineModel: " << machine_model;
  bool known = true;
#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
  // Full list as of May 2012.  Update when new OSX based models are added.
  known = rtc::string_match(machine_model, "MacBookPro*") ||
          rtc::string_match(machine_model, "MacBookAir*") ||
          rtc::string_match(machine_model, "MacBook*") ||
          rtc::string_match(machine_model, "MacPro*") ||
          rtc::string_match(machine_model, "Macmini*") ||
          rtc::string_match(machine_model, "iMac*") ||
          rtc::string_match(machine_model, "Xserve*");
#elif !defined(WEBRTC_IOS)
  // All other machines return Not available.
  known = rtc::string_match(info.GetMachineModel().c_str(),
                                  "Not available");
#endif
  if (!known) {
    LOG(LS_WARNING) << "Machine Model Unknown: " << machine_model;
  }
}

// Tests physical memory size.
TEST(SystemInfoTest, MemorySize) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "MemorySize: " << info.GetMemorySize();
  EXPECT_GT(info.GetMemorySize(), -1);
}

// Tests number of logical cpus available to the system.
TEST(SystemInfoTest, MaxCpus) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "MaxCpus: " << info.GetMaxCpus();
  EXPECT_GT(info.GetMaxCpus(), 0);
}

// Tests number of logical cpus available to the process.
TEST(SystemInfoTest, CurCpus) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CurCpus: " << info.GetCurCpus();
  EXPECT_GT(info.GetCurCpus(), 0);
  EXPECT_LE(info.GetCurCpus(), info.GetMaxCpus());
}

#ifdef CPU_X86
// CPU family/model/stepping is only available on X86. The following tests
// that they are set when running on x86 CPUs. Valid Family/Model/Stepping
// values are non-zero on known CPUs.

// Tests Intel CPU Family identification.
TEST(SystemInfoTest, CpuFamily) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CpuFamily: " << info.GetCpuFamily();
  EXPECT_GT(info.GetCpuFamily(), 0);
}

// Tests Intel CPU Model identification.
TEST(SystemInfoTest, CpuModel) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CpuModel: " << info.GetCpuModel();
  EXPECT_GT(info.GetCpuModel(), 0);
}

// Tests Intel CPU Stepping identification.
TEST(SystemInfoTest, CpuStepping) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CpuStepping: " << info.GetCpuStepping();
  EXPECT_GT(info.GetCpuStepping(), 0);
}
#else  // CPU_X86
// If not running on x86 CPU the following tests expect the functions to
// return 0.
TEST(SystemInfoTest, CpuFamily) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CpuFamily: " << info.GetCpuFamily();
  EXPECT_EQ(0, info.GetCpuFamily());
}

// Tests Intel CPU Model identification.
TEST(SystemInfoTest, CpuModel) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CpuModel: " << info.GetCpuModel();
  EXPECT_EQ(0, info.GetCpuModel());
}

// Tests Intel CPU Stepping identification.
TEST(SystemInfoTest, CpuStepping) {
  rtc::SystemInfo info;
  LOG(LS_INFO) << "CpuStepping: " << info.GetCpuStepping();
  EXPECT_EQ(0, info.GetCpuStepping());
}
#endif  // CPU_X86
