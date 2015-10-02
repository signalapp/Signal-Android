/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/data_log_c_helpers_unittest.h"

#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include "webrtc/system_wrappers/interface/data_log_c.h"

enum { kTestArrayLen = 4 };
static const char kTableName[] = "c_wrapper_table";
static const char kColumnName1[] = "Scalar";
static const char kColumnName2[] = "Vector";

int WebRtcDataLogCHelper_TestCreateLog() {
  return WebRtcDataLog_CreateLog();
}

int WebRtcDataLogCHelper_TestReturnLog() {
  WebRtcDataLog_ReturnLog();
  return 0;
}

int WebRtcDataLogCHelper_TestCombine() {
  const int kOutLen = strlen(kTableName) + 4;  // Room for "_17" + '\0'
  char* combined_name = malloc(kOutLen * sizeof(char));
  char* out_ptr = WebRtcDataLog_Combine(combined_name, kOutLen, kTableName, 17);
  int return_code = 0;
  if (!out_ptr) {
    return_code = -1;
  }
  if (strcmp(combined_name, "c_wrapper_table_17") != 0) {
    return_code = -2;
  }
  free(combined_name);
  return return_code;
}

int WebRtcDataLogCHelper_TestAddTable() {
  return WebRtcDataLog_AddTable(kTableName);
}

int WebRtcDataLogCHelper_TestAddColumn() {
  if (WebRtcDataLog_AddColumn(kTableName, kColumnName1, 1) != 0) {
    return -1;
  }
  if (WebRtcDataLog_AddColumn(kTableName, kColumnName2, kTestArrayLen) != 0) {
    return -2;
  }
  return 0;
}

int WebRtcDataLogCHelper_TestNextRow() {
  return WebRtcDataLog_NextRow(kTableName);
}

int WebRtcDataLogCHelper_TestInsertCell_int() {
  return WebRtcDataLog_InsertCell_int(kTableName, kColumnName1, 17);
}

int WebRtcDataLogCHelper_TestInsertArray_int() {
  int values[kTestArrayLen] = {1, 2, 3, 4};
  return WebRtcDataLog_InsertArray_int(kTableName, kColumnName2, values,
                                       kTestArrayLen);
}

int WebRtcDataLogCHelper_TestInsertCell_float() {
  return WebRtcDataLog_InsertCell_float(kTableName, kColumnName1, 17.0f);
}

int WebRtcDataLogCHelper_TestInsertArray_float() {
  float values[kTestArrayLen] = {1.0f, 2.0f, 3.0f, 4.0f};
  return WebRtcDataLog_InsertArray_float(kTableName, kColumnName2, values,
                                         kTestArrayLen);
}

int WebRtcDataLogCHelper_TestInsertCell_double() {
  return WebRtcDataLog_InsertCell_int(kTableName, kColumnName1, 17.0);
}

int WebRtcDataLogCHelper_TestInsertArray_double() {
  double values[kTestArrayLen] = {1.0, 2.0, 3.0, 4.0};
  return WebRtcDataLog_InsertArray_double(kTableName, kColumnName2, values,
                                          kTestArrayLen);
}

int WebRtcDataLogCHelper_TestInsertCell_int32() {
  return WebRtcDataLog_InsertCell_int32(kTableName, kColumnName1, 17);
}

int WebRtcDataLogCHelper_TestInsertArray_int32() {
  int32_t values[kTestArrayLen] = {1, 2, 3, 4};
  return WebRtcDataLog_InsertArray_int32(kTableName, kColumnName2, values,
                                         kTestArrayLen);
}

int WebRtcDataLogCHelper_TestInsertCell_uint32() {
  return WebRtcDataLog_InsertCell_uint32(kTableName, kColumnName1, 17);
}

int WebRtcDataLogCHelper_TestInsertArray_uint32() {
  uint32_t values[kTestArrayLen] = {1, 2, 3, 4};
  return WebRtcDataLog_InsertArray_uint32(kTableName, kColumnName2, values,
                                          kTestArrayLen);
}

int WebRtcDataLogCHelper_TestInsertCell_int64() {
  return WebRtcDataLog_InsertCell_int64(kTableName, kColumnName1, 17);
}

int WebRtcDataLogCHelper_TestInsertArray_int64() {
  int64_t values[kTestArrayLen] = {1, 2, 3, 4};
  return WebRtcDataLog_InsertArray_int64(kTableName, kColumnName2, values,
                                         kTestArrayLen);
}
