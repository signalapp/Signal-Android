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

#include <map>
#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/system_wrappers/interface/data_log_c.h"
#include "webrtc/system_wrappers/source/data_log_c_helpers_unittest.h"

using ::webrtc::DataLog;

// A class for storing the values expected from a log table column when
// verifying a log table file.
struct ExpectedValues {
 public:
  ExpectedValues()
    : values(),
      multi_value_length(1) {
  }

  ExpectedValues(std::vector<std::string> expected_values,
                 int expected_multi_value_length)
    : values(expected_values),
      multi_value_length(expected_multi_value_length) {
  }

  std::vector<std::string> values;
  int multi_value_length;
};

typedef std::map<std::string, ExpectedValues> ExpectedValuesMap;

// A static class used for parsing and verifying data log files.
class DataLogParser {
 public:
  // Verifies that the log table stored in the file "log_file" corresponds to
  // the cells and columns specified in "columns".
  static int VerifyTable(FILE* log_file, const ExpectedValuesMap& columns) {
    int row = 0;
    char line_buffer[kMaxLineLength];
    char* ret = fgets(line_buffer, kMaxLineLength, log_file);
    EXPECT_FALSE(ret == NULL);
    if (ret == NULL)
      return -1;

    std::string line(line_buffer, kMaxLineLength);
    VerifyHeader(line, columns);
    while (fgets(line_buffer, kMaxLineLength, log_file) != NULL) {
      line = std::string(line_buffer, kMaxLineLength);
      size_t line_position = 0;

      for (ExpectedValuesMap::const_iterator it = columns.begin();
           it != columns.end(); ++it) {
        std::string str = ParseElement(line, &line_position,
                                       it->second.multi_value_length);
        EXPECT_EQ(str, it->second.values[row]);
        if (str != it->second.values[row])
          return -1;
      }
      ++row;
    }
    return 0;
  }

  // Verifies the table header stored in "line" to correspond with the header
  // specified in "columns".
  static int VerifyHeader(const std::string& line,
                          const ExpectedValuesMap& columns) {
    size_t line_position = 0;
    for (ExpectedValuesMap::const_iterator it = columns.begin();
         it != columns.end(); ++it) {
      std::string str = ParseElement(line, &line_position,
                                     it->second.multi_value_length);
      EXPECT_EQ(str, it->first);
      if (str != it->first)
        return -1;
    }
    return 0;
  }

  // Parses out and returns one element from the string "line", which contains
  // one line read from a log table file. An element can either be a column
  // header or a cell of a row.
  static std::string ParseElement(const std::string& line,
                                  size_t* line_position,
                                  int multi_value_length) {
    std::string parsed_cell;
    parsed_cell = "";
    for (int i = 0; i < multi_value_length; ++i) {
      size_t next_separator = line.find(',', *line_position);
      EXPECT_NE(next_separator, std::string::npos);
      if (next_separator == std::string::npos)
        break;
      parsed_cell += line.substr(*line_position,
                                 next_separator - *line_position + 1);
      *line_position = next_separator + 1;
    }
    return parsed_cell;
  }

  // This constant defines the maximum line length the DataLogParser can
  // parse.
  enum { kMaxLineLength = 100 };
};

TEST(TestDataLog, CreateReturnTest) {
  for (int i = 0; i < 10; ++i)
    ASSERT_EQ(DataLog::CreateLog(), 0);
  ASSERT_EQ(DataLog::AddTable(DataLog::Combine("a proper table", 1)), 0);
  for (int i = 0; i < 10; ++i)
    DataLog::ReturnLog();
  ASSERT_LT(DataLog::AddTable(DataLog::Combine("table failure", 1)), 0);
}

TEST(TestDataLog, VerifyCombineMethod) {
  EXPECT_EQ(std::string("a proper table_1"),
            DataLog::Combine("a proper table", 1));
}

TEST(TestDataLog, VerifySingleTable) {
  DataLog::CreateLog();
  DataLog::AddTable(DataLog::Combine("table", 1));
  DataLog::AddColumn(DataLog::Combine("table", 1), "arrival", 1);
  DataLog::AddColumn(DataLog::Combine("table", 1), "timestamp", 1);
  DataLog::AddColumn(DataLog::Combine("table", 1), "size", 5);
  uint32_t sizes[5] = {1400, 1500, 1600, 1700, 1800};
  for (int i = 0; i < 10; ++i) {
    DataLog::InsertCell(DataLog::Combine("table", 1), "arrival",
                        static_cast<double>(i));
    DataLog::InsertCell(DataLog::Combine("table", 1), "timestamp",
                        static_cast<int64_t>(4354 + i));
    DataLog::InsertCell(DataLog::Combine("table", 1), "size", sizes, 5);
    DataLog::NextRow(DataLog::Combine("table", 1));
  }
  DataLog::ReturnLog();
  // Verify file
  FILE* table = fopen("table_1.txt", "r");
  ASSERT_FALSE(table == NULL);
  // Read the column names and verify with the expected columns.
  // Note that the columns are written to file in alphabetical order.
  // Data expected from parsing the file
  const int kNumberOfRows = 10;
  std::string string_arrival[kNumberOfRows] = {
    "0,", "1,", "2,", "3,", "4,",
    "5,", "6,", "7,", "8,", "9,"
  };
  std::string string_timestamp[kNumberOfRows] = {
    "4354,", "4355,", "4356,", "4357,",
    "4358,", "4359,", "4360,", "4361,",
    "4362,", "4363,"
  };
  std::string string_sizes = "1400,1500,1600,1700,1800,";
  ExpectedValuesMap expected;
  expected["arrival,"] = ExpectedValues(
                           std::vector<std::string>(string_arrival,
                                                    string_arrival +
                                                    kNumberOfRows),
                           1);
  expected["size[5],,,,,"] = ExpectedValues(
                               std::vector<std::string>(10, string_sizes), 5);
  expected["timestamp,"] = ExpectedValues(
                             std::vector<std::string>(string_timestamp,
                                                      string_timestamp +
                                                      kNumberOfRows),
                             1);
  ASSERT_EQ(DataLogParser::VerifyTable(table, expected), 0);
  fclose(table);
}

TEST(TestDataLog, VerifyMultipleTables) {
  DataLog::CreateLog();
  DataLog::AddTable(DataLog::Combine("table", 2));
  DataLog::AddTable(DataLog::Combine("table", 3));
  DataLog::AddColumn(DataLog::Combine("table", 2), "arrival", 1);
  DataLog::AddColumn(DataLog::Combine("table", 2), "timestamp", 1);
  DataLog::AddColumn(DataLog::Combine("table", 2), "size", 1);
  DataLog::AddTable(DataLog::Combine("table", 4));
  DataLog::AddColumn(DataLog::Combine("table", 3), "timestamp", 1);
  DataLog::AddColumn(DataLog::Combine("table", 3), "arrival", 1);
  DataLog::AddColumn(DataLog::Combine("table", 4), "size", 1);
  for (int32_t i = 0; i < 10; ++i) {
    DataLog::InsertCell(DataLog::Combine("table", 2), "arrival",
                        static_cast<int32_t>(i));
    DataLog::InsertCell(DataLog::Combine("table", 2), "timestamp",
                        static_cast<int32_t>(4354 + i));
    DataLog::InsertCell(DataLog::Combine("table", 2), "size",
                        static_cast<int32_t>(1200 + 10 * i));
    DataLog::InsertCell(DataLog::Combine("table", 3), "timestamp",
                        static_cast<int32_t>(4354 + i));
    DataLog::InsertCell(DataLog::Combine("table", 3), "arrival",
                        static_cast<int32_t>(i));
    DataLog::InsertCell(DataLog::Combine("table", 4), "size",
                        static_cast<int32_t>(1200 + 10 * i));
    DataLog::NextRow(DataLog::Combine("table", 4));
    DataLog::NextRow(DataLog::Combine("table", 2));
    DataLog::NextRow(DataLog::Combine("table", 3));
  }
  DataLog::ReturnLog();

  // Data expected from parsing the file
  const int kNumberOfRows = 10;
  std::string string_arrival[kNumberOfRows] = {
    "0,", "1,", "2,", "3,", "4,",
    "5,", "6,", "7,", "8,", "9,"
  };
  std::string string_timestamp[kNumberOfRows] = {
    "4354,", "4355,", "4356,", "4357,",
    "4358,", "4359,", "4360,", "4361,",
    "4362,", "4363,"
  };
  std::string string_size[kNumberOfRows] = {
    "1200,", "1210,", "1220,", "1230,",
    "1240,", "1250,", "1260,", "1270,",
    "1280,", "1290,"
  };

  // Verify table 2
  {
    FILE* table = fopen("table_2.txt", "r");
    ASSERT_FALSE(table == NULL);
    ExpectedValuesMap expected;
    expected["arrival,"] = ExpectedValues(
                             std::vector<std::string>(string_arrival,
                                                      string_arrival +
                                                      kNumberOfRows),
                             1);
    expected["size,"] = ExpectedValues(
                          std::vector<std::string>(string_size,
                                                   string_size + kNumberOfRows),
                          1);
    expected["timestamp,"] = ExpectedValues(
                               std::vector<std::string>(string_timestamp,
                                                        string_timestamp +
                                                        kNumberOfRows),
                               1);
    ASSERT_EQ(DataLogParser::VerifyTable(table, expected), 0);
    fclose(table);
  }

  // Verify table 3
  {
    FILE* table = fopen("table_3.txt", "r");
    ASSERT_FALSE(table == NULL);
    ExpectedValuesMap expected;
    expected["arrival,"] = ExpectedValues(
                             std::vector<std::string>(string_arrival,
                                                      string_arrival +
                                                      kNumberOfRows),
                             1);
    expected["timestamp,"] = ExpectedValues(
                               std::vector<std::string>(string_timestamp,
                                                        string_timestamp +
                                                        kNumberOfRows),
                               1);
    ASSERT_EQ(DataLogParser::VerifyTable(table, expected), 0);
    fclose(table);
  }

  // Verify table 4
  {
    FILE* table = fopen("table_4.txt", "r");
    ASSERT_FALSE(table == NULL);
    ExpectedValuesMap expected;
    expected["size,"] = ExpectedValues(
                          std::vector<std::string>(string_size,
                                                   string_size +
                                                   kNumberOfRows),
                          1);
    ASSERT_EQ(DataLogParser::VerifyTable(table, expected), 0);
    fclose(table);
  }
}

TEST(TestDataLogCWrapper, VerifyCWrapper) {
  // Simply call all C wrapper log functions through the C helper unittests.
  // Main purpose is to make sure that the linkage is correct.

  EXPECT_EQ(0, WebRtcDataLogCHelper_TestCreateLog());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestCombine());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestAddTable());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestAddColumn());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertCell_int());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertArray_int());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestNextRow());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertCell_float());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertArray_float());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestNextRow());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertCell_double());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertArray_double());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestNextRow());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertCell_int32());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertArray_int32());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestNextRow());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertCell_uint32());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertArray_uint32());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestNextRow());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertCell_int64());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestInsertArray_int64());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestNextRow());
  EXPECT_EQ(0, WebRtcDataLogCHelper_TestReturnLog());
}
