/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/data_log.h"

#include <stdio.h>

#include "testing/gtest/include/gtest/gtest.h"

using ::webrtc::DataLog;

const char* kDataLogFileName = "table_1.txt";

void PerformLogging(const std::string& table_name) {
  // Simulate normal DataTable logging behavior using this table name.
  ASSERT_EQ(0, DataLog::AddTable(table_name));
  ASSERT_EQ(0, DataLog::AddColumn(table_name, "test", 1));
  for (int i = 0; i < 10; ++i) {
    // TODO(kjellander): Check InsertCell result when the DataLog dummy is
    // fixed.
    DataLog::InsertCell(table_name, "test", static_cast<double>(i));
    ASSERT_EQ(0, DataLog::NextRow(table_name));
  }
}

// Simple test to verify DataLog is still working when the GYP variable
// enable_data_logging==0 (the default case).
TEST(TestDataLogDisabled, VerifyLoggingWorks) {
  ASSERT_EQ(0, DataLog::CreateLog());
  // Generate a table_name name and assure it's an empty string
  // (dummy behavior).
  std::string table_name = DataLog::Combine("table", 1);
  ASSERT_EQ("", table_name);
  PerformLogging(table_name);
  DataLog::ReturnLog();
}

TEST(TestDataLogDisabled, EnsureNoFileIsWritten) {
  // Remove any previous data files on disk:
  remove(kDataLogFileName);
  ASSERT_EQ(0, DataLog::CreateLog());
  // Don't use the table name we would get from Combine on a disabled DataLog.
  // Use "table_1" instead (which is what an enabled DataLog would give us).
  PerformLogging("table_1");
  DataLog::ReturnLog();
  // Verify no data log file have been written:
  ASSERT_EQ(NULL, fopen(kDataLogFileName, "r"));
}
