/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This is the pure C wrapper of the DataLog class.

#include "webrtc/system_wrappers/include/data_log_c.h"

#include <string>

#include "webrtc/system_wrappers/include/data_log.h"

extern "C" int WebRtcDataLog_CreateLog() {
  return webrtc::DataLog::CreateLog();
}

extern "C" void WebRtcDataLog_ReturnLog() {
  return webrtc::DataLog::ReturnLog();
}

extern "C" char* WebRtcDataLog_Combine(char* combined_name, size_t combined_len,
                                       const char* table_name, int table_id) {
  if (!table_name) return NULL;
  std::string combined = webrtc::DataLog::Combine(table_name, table_id);
  if (combined.size() >= combined_len) return NULL;
  std::copy(combined.begin(), combined.end(), combined_name);
  combined_name[combined.size()] = '\0';
  return combined_name;
}

extern "C" int WebRtcDataLog_AddTable(const char* table_name) {
  if (!table_name) return -1;
  return webrtc::DataLog::AddTable(table_name);
}

extern "C" int WebRtcDataLog_AddColumn(const char* table_name,
                                       const char* column_name,
                                       int multi_value_length) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::AddColumn(table_name, column_name,
                                    multi_value_length);
}

extern "C" int WebRtcDataLog_InsertCell_int(const char* table_name,
                                            const char* column_name,
                                            int value) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, value);
}

extern "C" int WebRtcDataLog_InsertArray_int(const char* table_name,
                                             const char* column_name,
                                             const int* values,
                                             int length) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, values, length);
}

extern "C" int WebRtcDataLog_InsertCell_float(const char* table_name,
                                              const char* column_name,
                                              float value) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, value);
}

extern "C" int WebRtcDataLog_InsertArray_float(const char* table_name,
                                               const char* column_name,
                                               const float* values,
                                               int length) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, values, length);
}

extern "C" int WebRtcDataLog_InsertCell_double(const char* table_name,
                                               const char* column_name,
                                               double value) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, value);
}

extern "C" int WebRtcDataLog_InsertArray_double(const char* table_name,
                                                const char* column_name,
                                                const double* values,
                                                int length) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, values, length);
}

extern "C" int WebRtcDataLog_InsertCell_int32(const char* table_name,
                                              const char* column_name,
                                              int32_t value) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, value);
}

extern "C" int WebRtcDataLog_InsertArray_int32(const char* table_name,
                                               const char* column_name,
                                               const int32_t* values,
                                               int length) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, values, length);
}

extern "C" int WebRtcDataLog_InsertCell_uint32(const char* table_name,
                                               const char* column_name,
                                               uint32_t value) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, value);
}

extern "C" int WebRtcDataLog_InsertArray_uint32(const char* table_name,
                                                const char* column_name,
                                                const uint32_t* values,
                                                int length) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, values, length);
}

extern "C" int WebRtcDataLog_InsertCell_int64(const char* table_name,
                                              const char* column_name,
                                              int64_t value) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, value);
}

extern "C" int WebRtcDataLog_InsertArray_int64(const char* table_name,
                                               const char* column_name,
                                               const int64_t* values,
                                               int length) {
  if (!table_name || !column_name) return -1;
  return webrtc::DataLog::InsertCell(table_name, column_name, values, length);
}

extern "C" int WebRtcDataLog_NextRow(const char* table_name) {
  if (!table_name) return -1;
  return webrtc::DataLog::NextRow(table_name);
}
