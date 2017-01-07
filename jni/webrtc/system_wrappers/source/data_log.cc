/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/include/data_log.h"

#include <assert.h>

#include <algorithm>
#include <list>

#include "webrtc/system_wrappers/include/critical_section_wrapper.h"
#include "webrtc/system_wrappers/include/event_wrapper.h"
#include "webrtc/system_wrappers/include/file_wrapper.h"
#include "webrtc/system_wrappers/include/rw_lock_wrapper.h"

namespace webrtc {

DataLogImpl::CritSectScopedPtr DataLogImpl::crit_sect_(
  CriticalSectionWrapper::CreateCriticalSection());

DataLogImpl* DataLogImpl::instance_ = NULL;

// A Row contains cells, which are indexed by the column names as std::string.
// The string index is treated in a case sensitive way.
class Row {
 public:
  Row();
  ~Row();

  // Inserts a Container into the cell of the column specified with
  // column_name.
  // column_name is treated in a case sensitive way.
  int InsertCell(const std::string& column_name,
                 const Container* value_container);

  // Converts the value at the column specified by column_name to a string
  // stored in value_string.
  // column_name is treated in a case sensitive way.
  void ToString(const std::string& column_name, std::string* value_string);

 private:
  // Collection of containers indexed by column name as std::string
  typedef std::map<std::string, const Container*> CellMap;

  CellMap                   cells_;
  CriticalSectionWrapper*   cells_lock_;
};

// A LogTable contains multiple rows, where only the latest row is active for
// editing. The rows are defined by the ColumnMap, which contains the name of
// each column and the length of the column (1 for one-value-columns and greater
// than 1 for multi-value-columns).
class LogTable {
 public:
  LogTable();
  ~LogTable();

  // Adds the column with name column_name to the table. The column will be a
  // multi-value-column if multi_value_length is greater than 1.
  // column_name is treated in a case sensitive way.
  int AddColumn(const std::string& column_name, int multi_value_length);

  // Buffers the current row while it is waiting to be written to file,
  // which is done by a call to Flush(). A new row is available when the
  // function returns
  void NextRow();

  // Inserts a Container into the cell of the column specified with
  // column_name.
  // column_name is treated in a case sensitive way.
  int InsertCell(const std::string& column_name,
                 const Container* value_container);

  // Creates a log file, named as specified in the string file_name, to
  // where the table will be written when calling Flush().
  int CreateLogFile(const std::string& file_name);

  // Write all complete rows to file.
  // May not be called by two threads simultaneously (doing so may result in
  // a race condition). Will be called by the file_writer_thread_ when that
  // thread is running.
  void Flush();

 private:
  // Collection of multi_value_lengths indexed by column name as std::string
  typedef std::map<std::string, int> ColumnMap;
  typedef std::list<Row*> RowList;

  ColumnMap               columns_;
  RowList                 rows_[2];
  RowList*                rows_history_;
  RowList*                rows_flush_;
  Row*                    current_row_;
  FileWrapper*            file_;
  bool                    write_header_;
  CriticalSectionWrapper* table_lock_;
};

Row::Row()
  : cells_(),
    cells_lock_(CriticalSectionWrapper::CreateCriticalSection()) {
}

Row::~Row() {
  for (CellMap::iterator it = cells_.begin(); it != cells_.end();) {
    delete it->second;
    // For maps all iterators (except the erased) are valid after an erase
    cells_.erase(it++);
  }
  delete cells_lock_;
}

int Row::InsertCell(const std::string& column_name,
                    const Container* value_container) {
  CriticalSectionScoped synchronize(cells_lock_);
  assert(cells_.count(column_name) == 0);
  if (cells_.count(column_name) > 0)
    return -1;
  cells_[column_name] = value_container;
  return 0;
}

void Row::ToString(const std::string& column_name,
                   std::string* value_string) {
  CriticalSectionScoped synchronize(cells_lock_);
  const Container* container = cells_[column_name];
  if (container == NULL) {
    *value_string = "NaN,";
    return;
  }
  container->ToString(value_string);
}

LogTable::LogTable()
  : columns_(),
    rows_(),
    rows_history_(&rows_[0]),
    rows_flush_(&rows_[1]),
    current_row_(new Row),
    file_(FileWrapper::Create()),
    write_header_(true),
    table_lock_(CriticalSectionWrapper::CreateCriticalSection()) {
}

LogTable::~LogTable() {
  for (RowList::iterator row_it = rows_history_->begin();
       row_it != rows_history_->end();) {
    delete *row_it;
    row_it = rows_history_->erase(row_it);
  }
  for (ColumnMap::iterator col_it = columns_.begin();
       col_it != columns_.end();) {
    // For maps all iterators (except the erased) are valid after an erase
    columns_.erase(col_it++);
  }
  if (file_ != NULL) {
    file_->Flush();
    file_->CloseFile();
    delete file_;
  }
  delete current_row_;
  delete table_lock_;
}

int LogTable::AddColumn(const std::string& column_name,
                        int multi_value_length) {
  assert(multi_value_length > 0);
  if (!write_header_) {
    // It's not allowed to add new columns after the header
    // has been written.
    assert(false);
    return -1;
  } else {
    CriticalSectionScoped synchronize(table_lock_);
    if (write_header_)
      columns_[column_name] = multi_value_length;
    else
      return -1;
  }
  return 0;
}

void LogTable::NextRow() {
  CriticalSectionScoped sync_rows(table_lock_);
  rows_history_->push_back(current_row_);
  current_row_ = new Row;
}

int LogTable::InsertCell(const std::string& column_name,
                         const Container* value_container) {
  CriticalSectionScoped synchronize(table_lock_);
  assert(columns_.count(column_name) > 0);
  if (columns_.count(column_name) == 0)
    return -1;
  return current_row_->InsertCell(column_name, value_container);
}

int LogTable::CreateLogFile(const std::string& file_name) {
  if (file_name.length() == 0)
    return -1;
  if (file_->is_open())
    return -1;
  // Open with read/write permissions
  return file_->OpenFile(file_name.c_str(), false) ? 0 : -1;
}

void LogTable::Flush() {
  ColumnMap::iterator column_it;
  bool commit_header = false;
  if (write_header_) {
    CriticalSectionScoped synchronize(table_lock_);
    if (write_header_) {
      commit_header = true;
      write_header_ = false;
    }
  }
  if (commit_header) {
    for (column_it = columns_.begin();
         column_it != columns_.end(); ++column_it) {
      if (column_it->second > 1) {
        file_->WriteText("%s[%u],", column_it->first.c_str(),
                         column_it->second);
        for (int i = 1; i < column_it->second; ++i)
          file_->WriteText(",");
      } else {
        file_->WriteText("%s,", column_it->first.c_str());
      }
    }
    if (columns_.size() > 0)
      file_->WriteText("\n");
  }

  // Swap the list used for flushing with the list containing the row history
  // and clear the history. We also create a local pointer to the new
  // list used for flushing to avoid race conditions if another thread
  // calls this function while we are writing.
  // We don't want to block the list while we're writing to file.
  {
    CriticalSectionScoped synchronize(table_lock_);
    RowList* tmp = rows_flush_;
    rows_flush_ = rows_history_;
    rows_history_ = tmp;
    rows_history_->clear();
  }

  // Write all complete rows to file and delete them
  for (RowList::iterator row_it = rows_flush_->begin();
       row_it != rows_flush_->end();) {
    for (column_it = columns_.begin();
         column_it != columns_.end(); ++column_it) {
      std::string row_string;
      (*row_it)->ToString(column_it->first, &row_string);
      file_->WriteText("%s", row_string.c_str());
    }
    if (columns_.size() > 0)
      file_->WriteText("\n");
    delete *row_it;
    row_it = rows_flush_->erase(row_it);
  }
}

int DataLog::CreateLog() {
  return DataLogImpl::CreateLog();
}

void DataLog::ReturnLog() {
  return DataLogImpl::ReturnLog();
}

std::string DataLog::Combine(const std::string& table_name, int table_id) {
  std::stringstream ss;
  std::string combined_id = table_name;
  std::string number_suffix;
  ss << "_" << table_id;
  ss >> number_suffix;
  combined_id += number_suffix;
  std::transform(combined_id.begin(), combined_id.end(), combined_id.begin(),
                 ::tolower);
  return combined_id;
}

int DataLog::AddTable(const std::string& table_name) {
  DataLogImpl* data_log = DataLogImpl::StaticInstance();
  if (data_log == NULL)
    return -1;
  return data_log->AddTable(table_name);
}

int DataLog::AddColumn(const std::string& table_name,
                       const std::string& column_name,
                       int multi_value_length) {
  DataLogImpl* data_log = DataLogImpl::StaticInstance();
  if (data_log == NULL)
    return -1;
  return data_log->DataLogImpl::StaticInstance()->AddColumn(table_name,
                                                            column_name,
                                                            multi_value_length);
}

int DataLog::NextRow(const std::string& table_name) {
  DataLogImpl* data_log = DataLogImpl::StaticInstance();
  if (data_log == NULL)
    return -1;
  return data_log->DataLogImpl::StaticInstance()->NextRow(table_name);
}

DataLogImpl::DataLogImpl()
    : counter_(1),
      tables_(),
      flush_event_(EventWrapper::Create()),
      file_writer_thread_(
          new rtc::PlatformThread(DataLogImpl::Run, instance_, "DataLog")),
      tables_lock_(RWLockWrapper::CreateRWLock()) {}

DataLogImpl::~DataLogImpl() {
  StopThread();
  Flush();  // Write any remaining rows
  delete flush_event_;
  for (TableMap::iterator it = tables_.begin(); it != tables_.end();) {
    delete static_cast<LogTable*>(it->second);
    // For maps all iterators (except the erased) are valid after an erase
    tables_.erase(it++);
  }
  delete tables_lock_;
}

int DataLogImpl::CreateLog() {
  CriticalSectionScoped synchronize(crit_sect_.get());
  if (instance_ == NULL) {
    instance_ = new DataLogImpl();
    return instance_->Init();
  } else {
    ++instance_->counter_;
  }
  return 0;
}

int DataLogImpl::Init() {
  file_writer_thread_->Start();
  file_writer_thread_->SetPriority(rtc::kHighestPriority);
  return 0;
}

DataLogImpl* DataLogImpl::StaticInstance() {
  return instance_;
}

void DataLogImpl::ReturnLog() {
  CriticalSectionScoped synchronize(crit_sect_.get());
  if (instance_ && instance_->counter_ > 1) {
    --instance_->counter_;
    return;
  }
  delete instance_;
  instance_ = NULL;
}

int DataLogImpl::AddTable(const std::string& table_name) {
  WriteLockScoped synchronize(*tables_lock_);
  // Make sure we don't add a table which already exists
  if (tables_.count(table_name) > 0)
    return -1;
  tables_[table_name] = new LogTable();
  if (tables_[table_name]->CreateLogFile(table_name + ".txt") == -1)
    return -1;
  return 0;
}

int DataLogImpl::AddColumn(const std::string& table_name,
                           const std::string& column_name,
                           int multi_value_length) {
  ReadLockScoped synchronize(*tables_lock_);
  if (tables_.count(table_name) == 0)
    return -1;
  return tables_[table_name]->AddColumn(column_name, multi_value_length);
}

int DataLogImpl::InsertCell(const std::string& table_name,
                            const std::string& column_name,
                            const Container* value_container) {
  ReadLockScoped synchronize(*tables_lock_);
  assert(tables_.count(table_name) > 0);
  if (tables_.count(table_name) == 0)
    return -1;
  return tables_[table_name]->InsertCell(column_name, value_container);
}

int DataLogImpl::NextRow(const std::string& table_name) {
  ReadLockScoped synchronize(*tables_lock_);
  if (tables_.count(table_name) == 0)
    return -1;
  tables_[table_name]->NextRow();
  // Signal a complete row
  flush_event_->Set();
  return 0;
}

void DataLogImpl::Flush() {
  ReadLockScoped synchronize(*tables_lock_);
  for (TableMap::iterator it = tables_.begin(); it != tables_.end(); ++it) {
    it->second->Flush();
  }
}

bool DataLogImpl::Run(void* obj) {
  static_cast<DataLogImpl*>(obj)->Process();
  return true;
}

void DataLogImpl::Process() {
  // Wait for a row to be complete
  flush_event_->Wait(WEBRTC_EVENT_INFINITE);
  Flush();
}

void DataLogImpl::StopThread() {
  flush_event_->Set();
  file_writer_thread_->Stop();
}

}  // namespace webrtc
