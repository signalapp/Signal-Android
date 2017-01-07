/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>
#include "webrtc/base/linux.h"
#include "webrtc/base/fileutils.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/gunit.h"

namespace rtc {

// These tests running on ARM are fairly specific to the output of the tegra2
// ARM processor, and so may fail on other ARM-based systems.
TEST(ProcCpuInfo, GetProcInfo) {
  ProcCpuInfo proc_info;
  EXPECT_TRUE(proc_info.LoadFromSystem());

  int out_cpus = 0;
  EXPECT_TRUE(proc_info.GetNumCpus(&out_cpus));
  LOG(LS_INFO) << "GetNumCpus: " << out_cpus;
  EXPECT_GT(out_cpus, 0);

  int out_cpus_phys = 0;
  EXPECT_TRUE(proc_info.GetNumPhysicalCpus(&out_cpus_phys));
  LOG(LS_INFO) << "GetNumPhysicalCpus: " << out_cpus_phys;
  EXPECT_GT(out_cpus_phys, 0);
  EXPECT_LE(out_cpus_phys, out_cpus);

  int out_family = 0;
  EXPECT_TRUE(proc_info.GetCpuFamily(&out_family));
  LOG(LS_INFO) << "cpu family: " << out_family;
  EXPECT_GE(out_family, 4);

#if defined(__arm__)
  std::string out_processor;
  EXPECT_TRUE(proc_info.GetSectionStringValue(0, "Processor", &out_processor));
  LOG(LS_INFO) << "Processor: " << out_processor;
  EXPECT_NE(std::string::npos, out_processor.find("ARM"));

  // Most other info, such as model, stepping, vendor, etc.
  // is missing on ARM systems.
#else
  int out_model = 0;
  EXPECT_TRUE(proc_info.GetSectionIntValue(0, "model", &out_model));
  LOG(LS_INFO) << "model: " << out_model;

  int out_stepping = 0;
  EXPECT_TRUE(proc_info.GetSectionIntValue(0, "stepping", &out_stepping));
  LOG(LS_INFO) << "stepping: " << out_stepping;

  int out_processor = 0;
  EXPECT_TRUE(proc_info.GetSectionIntValue(0, "processor", &out_processor));
  LOG(LS_INFO) << "processor: " << out_processor;
  EXPECT_EQ(0, out_processor);

  std::string out_str;
  EXPECT_TRUE(proc_info.GetSectionStringValue(0, "vendor_id", &out_str));
  LOG(LS_INFO) << "vendor_id: " << out_str;
  EXPECT_FALSE(out_str.empty());
#endif
}

TEST(ConfigParser, ParseConfig) {
  ConfigParser parser;
  MemoryStream *test_stream = new MemoryStream(
      "Key1: Value1\n"
      "Key2\t: Value2\n"
      "Key3:Value3\n"
      "\n"
      "Key1:Value1\n");
  ConfigParser::MapVector key_val_pairs;
  parser.Attach(test_stream);
  EXPECT_EQ(true, parser.Parse(&key_val_pairs));
  EXPECT_EQ(2U, key_val_pairs.size());
  EXPECT_EQ("Value1", key_val_pairs[0]["Key1"]);
  EXPECT_EQ("Value2", key_val_pairs[0]["Key2"]);
  EXPECT_EQ("Value3", key_val_pairs[0]["Key3"]);
  EXPECT_EQ("Value1", key_val_pairs[1]["Key1"]);
  key_val_pairs.clear();
  EXPECT_EQ(true, parser.Open("/proc/cpuinfo"));
  EXPECT_EQ(true, parser.Parse(&key_val_pairs));
}

TEST(ReadLinuxUname, ReturnsSomething) {
  std::string str = ReadLinuxUname();
  EXPECT_FALSE(str.empty());
}

}  // namespace rtc
