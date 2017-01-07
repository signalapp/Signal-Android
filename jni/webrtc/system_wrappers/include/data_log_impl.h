/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains the helper classes for the DataLog APIs. See data_log.h
// for the APIs.
//
// These classes are helper classes used for logging data for offline
// processing. Data logged with these classes can conveniently be parsed and
// processed with e.g. Matlab.
#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_DATA_LOG_IMPL_H_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_DATA_LOG_IMPL_H_

#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include "webrtc/base/platform_thread.h"
#include "webrtc/typedefs.h"

namespace webrtc {

class CriticalSectionWrapper;
class EventWrapper;
class LogTable;
class RWLockWrapper;

// All container classes need to implement a ToString-function to be
// writable to file. Enforce this via the Container interface.
class Container {
 public:
  virtual ~Container() {}

  virtual void ToString(std::string* container_string) const = 0;
};

template<class T>
class ValueContainer : public Container {
 public:
  explicit ValueContainer(T data) : data_(data) {}

  virtual void ToString(std::string* container_string) const {
    *container_string = "";
    std::stringstream ss;
    ss << data_ << ",";
    ss >> *container_string;
  }

 private:
  T   data_;
};

template<class T>
class MultiValueContainer : public Container {
 public:
  MultiValueContainer(const T* data, int length)
    : data_(data, data + length) {
  }

  virtual void ToString(std::string* container_string) const {
    *container_string = "";
    std::stringstream ss;
    for (size_t i = 0; i < data_.size(); ++i)
      ss << data_[i] << ",";
    *container_string += ss.str();
  }

 private:
  std::vector<T>  data_;
};

class DataLogImpl {
 public:
  ~DataLogImpl();

  // The implementation of the CreateLog() method declared in data_log.h.
  // See data_log.h for a description.
  static int CreateLog();

  // The implementation of the StaticInstance() method declared in data_log.h.
  // See data_log.h for a description.
  static DataLogImpl* StaticInstance();

  // The implementation of the ReturnLog() method declared in data_log.h. See
  // data_log.h for a description.
  static void ReturnLog();

  // The implementation of the AddTable() method declared in data_log.h. See
  // data_log.h for a description.
  int AddTable(const std::string& table_name);

  // The implementation of the AddColumn() method declared in data_log.h. See
  // data_log.h for a description.
  int AddColumn(const std::string& table_name,
                const std::string& column_name,
                int multi_value_length);

  // Inserts a Container into a table with name table_name at the column
  // with name column_name.
  // column_name is treated in a case sensitive way.
  int InsertCell(const std::string& table_name,
                 const std::string& column_name,
                 const Container* value_container);

  // The implementation of the NextRow() method declared in data_log.h. See
  // data_log.h for a description.
  int NextRow(const std::string& table_name);

 private:
  DataLogImpl();

  // Initializes the DataLogImpl object, allocates and starts the
  // thread file_writer_thread_.
  int Init();

  // Write all complete rows in every table to file.
  // This function should only be called by the file_writer_thread_ if that
  // thread is running to avoid race conditions.
  void Flush();

  // Run() is called by the thread file_writer_thread_.
  static bool Run(void* obj);

  // This function writes data to file. Note, it blocks if there is no data
  // that should be written to file availble. Flush is the non-blocking
  // version of this function.
  void Process();

  // Stops the continuous calling of Process().
  void StopThread();

  // Collection of tables indexed by the table name as std::string.
  typedef std::map<std::string, LogTable*> TableMap;
  typedef std::unique_ptr<CriticalSectionWrapper> CritSectScopedPtr;

  static CritSectScopedPtr  crit_sect_;
  static DataLogImpl*       instance_;
  int                       counter_;
  TableMap                  tables_;
  EventWrapper*             flush_event_;
  // This is a unique_ptr so that we don't have to create threads in the no-op
  // impl.
  std::unique_ptr<rtc::PlatformThread> file_writer_thread_;
  RWLockWrapper*            tables_lock_;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_DATA_LOG_IMPL_H_
