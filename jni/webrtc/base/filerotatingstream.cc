/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/filerotatingstream.h"

#include <algorithm>
#include <iostream>
#include <string>

#include "webrtc/base/checks.h"
#include "webrtc/base/fileutils.h"
#include "webrtc/base/pathutils.h"

// Note: We use std::cerr for logging in the write paths of this stream to avoid
// infinite loops when logging.

namespace rtc {

FileRotatingStream::FileRotatingStream(const std::string& dir_path,
                                       const std::string& file_prefix)
    : FileRotatingStream(dir_path, file_prefix, 0, 0, kRead) {
}

FileRotatingStream::FileRotatingStream(const std::string& dir_path,
                                       const std::string& file_prefix,
                                       size_t max_file_size,
                                       size_t num_files)
    : FileRotatingStream(dir_path,
                         file_prefix,
                         max_file_size,
                         num_files,
                         kWrite) {
  RTC_DCHECK_GT(max_file_size, 0u);
  RTC_DCHECK_GT(num_files, 1u);
}

FileRotatingStream::FileRotatingStream(const std::string& dir_path,
                                       const std::string& file_prefix,
                                       size_t max_file_size,
                                       size_t num_files,
                                       Mode mode)
    : dir_path_(dir_path),
      file_prefix_(file_prefix),
      mode_(mode),
      file_stream_(nullptr),
      max_file_size_(max_file_size),
      current_file_index_(0),
      rotation_index_(0),
      current_bytes_written_(0),
      disable_buffering_(false) {
  RTC_DCHECK(Filesystem::IsFolder(dir_path));
  switch (mode) {
    case kWrite: {
      file_names_.clear();
      for (size_t i = 0; i < num_files; ++i) {
        file_names_.push_back(GetFilePath(i, num_files));
      }
      rotation_index_ = num_files - 1;
      break;
    }
    case kRead: {
      file_names_ = GetFilesWithPrefix();
      std::sort(file_names_.begin(), file_names_.end());
      if (file_names_.size() > 0) {
        // |file_names_| is sorted newest first, so read from the end.
        current_file_index_ = file_names_.size() - 1;
      }
      break;
    }
  }
}

FileRotatingStream::~FileRotatingStream() {
}

StreamState FileRotatingStream::GetState() const {
  if (mode_ == kRead && current_file_index_ < file_names_.size()) {
    return SS_OPEN;
  }
  if (!file_stream_) {
    return SS_CLOSED;
  }
  return file_stream_->GetState();
}

StreamResult FileRotatingStream::Read(void* buffer,
                                      size_t buffer_len,
                                      size_t* read,
                                      int* error) {
  RTC_DCHECK(buffer);
  if (mode_ != kRead) {
    return SR_EOS;
  }
  if (current_file_index_ >= file_names_.size()) {
    return SR_EOS;
  }
  // We will have no file stream initially, and when we are finished with the
  // previous file.
  if (!file_stream_) {
    if (!OpenCurrentFile()) {
      return SR_ERROR;
    }
  }
  int local_error = 0;
  if (!error) {
    error = &local_error;
  }
  StreamResult result = file_stream_->Read(buffer, buffer_len, read, error);
  if (result == SR_EOS || result == SR_ERROR) {
    if (result == SR_ERROR) {
      LOG(LS_ERROR) << "Failed to read from: "
                    << file_names_[current_file_index_] << "Error: " << error;
    }
    // Reached the end of the file, read next file. If there is an error return
    // the error status but allow for a next read by reading next file.
    CloseCurrentFile();
    if (current_file_index_ == 0) {
      // Just finished reading the last file, signal EOS by setting index.
      current_file_index_ = file_names_.size();
    } else {
      --current_file_index_;
    }
    if (read) {
      *read = 0;
    }
    return result == SR_EOS ? SR_SUCCESS : result;
  } else if (result == SR_SUCCESS) {
    // Succeeded, continue reading from this file.
    return SR_SUCCESS;
  } else {
    RTC_NOTREACHED();
  }
  return result;
}

StreamResult FileRotatingStream::Write(const void* data,
                                       size_t data_len,
                                       size_t* written,
                                       int* error) {
  if (mode_ != kWrite) {
    return SR_EOS;
  }
  if (!file_stream_) {
    std::cerr << "Open() must be called before Write." << std::endl;
    return SR_ERROR;
  }
  // Write as much as will fit in to the current file.
  RTC_DCHECK_LT(current_bytes_written_, max_file_size_);
  size_t remaining_bytes = max_file_size_ - current_bytes_written_;
  size_t write_length = std::min(data_len, remaining_bytes);
  size_t local_written = 0;
  if (!written) {
    written = &local_written;
  }
  StreamResult result = file_stream_->Write(data, write_length, written, error);
  current_bytes_written_ += *written;

  // If we're done with this file, rotate it out.
  if (current_bytes_written_ >= max_file_size_) {
    RTC_DCHECK_EQ(current_bytes_written_, max_file_size_);
    RotateFiles();
  }
  return result;
}

bool FileRotatingStream::Flush() {
  if (!file_stream_) {
    return false;
  }
  return file_stream_->Flush();
}

bool FileRotatingStream::GetSize(size_t* size) const {
  if (mode_ != kRead) {
    // Not possible to get accurate size on disk when writing because of
    // potential buffering.
    return false;
  }
  RTC_DCHECK(size);
  *size = 0;
  size_t total_size = 0;
  for (auto file_name : file_names_) {
    Pathname pathname(file_name);
    size_t file_size = 0;
    if (Filesystem::GetFileSize(file_name, &file_size)) {
      total_size += file_size;
    }
  }
  *size = total_size;
  return true;
}

void FileRotatingStream::Close() {
  CloseCurrentFile();
}

bool FileRotatingStream::Open() {
  switch (mode_) {
    case kRead:
      // Defer opening to when we first read since we want to return read error
      // if we fail to open next file.
      return true;
    case kWrite: {
      // Delete existing files when opening for write.
      std::vector<std::string> matching_files = GetFilesWithPrefix();
      for (auto matching_file : matching_files) {
        if (!Filesystem::DeleteFile(matching_file)) {
          std::cerr << "Failed to delete: " << matching_file << std::endl;
        }
      }
      return OpenCurrentFile();
    }
  }
  return false;
}

bool FileRotatingStream::DisableBuffering() {
  disable_buffering_ = true;
  if (!file_stream_) {
    std::cerr << "Open() must be called before DisableBuffering()."
              << std::endl;
    return false;
  }
  return file_stream_->DisableBuffering();
}

std::string FileRotatingStream::GetFilePath(size_t index) const {
  RTC_DCHECK_LT(index, file_names_.size());
  return file_names_[index];
}

bool FileRotatingStream::OpenCurrentFile() {
  CloseCurrentFile();

  // Opens the appropriate file in the appropriate mode.
  RTC_DCHECK_LT(current_file_index_, file_names_.size());
  std::string file_path = file_names_[current_file_index_];
  file_stream_.reset(new FileStream());
  const char* mode = nullptr;
  switch (mode_) {
    case kWrite:
      mode = "w+";
      // We should always we writing to the zero-th file.
      RTC_DCHECK_EQ(current_file_index_, 0u);
      break;
    case kRead:
      mode = "r";
      break;
  }
  int error = 0;
  if (!file_stream_->Open(file_path, mode, &error)) {
    std::cerr << "Failed to open: " << file_path << "Error: " << error
              << std::endl;
    file_stream_.reset();
    return false;
  }
  if (disable_buffering_) {
    file_stream_->DisableBuffering();
  }
  return true;
}

void FileRotatingStream::CloseCurrentFile() {
  if (!file_stream_) {
    return;
  }
  current_bytes_written_ = 0;
  file_stream_.reset();
}

void FileRotatingStream::RotateFiles() {
  RTC_DCHECK_EQ(mode_, kWrite);
  CloseCurrentFile();
  // Rotates the files by deleting the file at |rotation_index_|, which is the
  // oldest file and then renaming the newer files to have an incremented index.
  // See header file comments for example.
  RTC_DCHECK_LT(rotation_index_, file_names_.size());
  std::string file_to_delete = file_names_[rotation_index_];
  if (Filesystem::IsFile(file_to_delete)) {
    if (!Filesystem::DeleteFile(file_to_delete)) {
      std::cerr << "Failed to delete: " << file_to_delete << std::endl;
    }
  }
  for (auto i = rotation_index_; i > 0; --i) {
    std::string rotated_name = file_names_[i];
    std::string unrotated_name = file_names_[i - 1];
    if (Filesystem::IsFile(unrotated_name)) {
      if (!Filesystem::MoveFile(unrotated_name, rotated_name)) {
        std::cerr << "Failed to move: " << unrotated_name << " to "
                  << rotated_name << std::endl;
      }
    }
  }
  // Create a new file for 0th index.
  OpenCurrentFile();
  OnRotation();
}

std::vector<std::string> FileRotatingStream::GetFilesWithPrefix() const {
  std::vector<std::string> files;
  // Iterate over the files in the directory.
  DirectoryIterator it;
  Pathname dir_path;
  dir_path.SetFolder(dir_path_);
  if (!it.Iterate(dir_path)) {
    return files;
  }
  do {
    std::string current_name = it.Name();
    if (current_name.size() && !it.IsDirectory() &&
        current_name.compare(0, file_prefix_.size(), file_prefix_) == 0) {
      Pathname path(dir_path_, current_name);
      files.push_back(path.pathname());
    }
  } while (it.Next());
  return files;
}

std::string FileRotatingStream::GetFilePath(size_t index,
                                            size_t num_files) const {
  RTC_DCHECK_LT(index, num_files);
  std::ostringstream file_name;
  // The format will be "_%<num_digits>zu". We want to zero pad the index so
  // that it will sort nicely.
  size_t max_digits = ((num_files - 1) / 10) + 1;
  size_t num_digits = (index / 10) + 1;
  RTC_DCHECK_LE(num_digits, max_digits);
  size_t padding = max_digits - num_digits;

  file_name << file_prefix_ << "_";
  for (size_t i = 0; i < padding; ++i) {
    file_name << "0";
  }
  file_name << index;

  Pathname file_path(dir_path_, file_name.str());
  return file_path.pathname();
}

CallSessionFileRotatingStream::CallSessionFileRotatingStream(
    const std::string& dir_path)
    : FileRotatingStream(dir_path, kLogPrefix),
      max_total_log_size_(0),
      num_rotations_(0) {
}

CallSessionFileRotatingStream::CallSessionFileRotatingStream(
    const std::string& dir_path,
    size_t max_total_log_size)
    : FileRotatingStream(dir_path,
                         kLogPrefix,
                         max_total_log_size / 2,
                         GetNumRotatingLogFiles(max_total_log_size) + 1),
      max_total_log_size_(max_total_log_size),
      num_rotations_(0) {
  RTC_DCHECK_GE(max_total_log_size, 4u);
}

const char* CallSessionFileRotatingStream::kLogPrefix = "webrtc_log";
const size_t CallSessionFileRotatingStream::kRotatingLogFileDefaultSize =
    1024 * 1024;

void CallSessionFileRotatingStream::OnRotation() {
  ++num_rotations_;
  if (num_rotations_ == 1) {
    // On the first rotation adjust the max file size so subsequent files after
    // the first are smaller.
    SetMaxFileSize(GetRotatingLogSize(max_total_log_size_));
  } else if (num_rotations_ == (GetNumFiles() - 1)) {
    // On the next rotation the very first file is going to be deleted. Change
    // the rotation index so this doesn't happen.
    SetRotationIndex(GetRotationIndex() - 1);
  }
}

size_t CallSessionFileRotatingStream::GetRotatingLogSize(
    size_t max_total_log_size) {
  size_t num_rotating_log_files = GetNumRotatingLogFiles(max_total_log_size);
  size_t rotating_log_size = num_rotating_log_files > 2
                                 ? kRotatingLogFileDefaultSize
                                 : max_total_log_size / 4;
  return rotating_log_size;
}

size_t CallSessionFileRotatingStream::GetNumRotatingLogFiles(
    size_t max_total_log_size) {
  // At minimum have two rotating files. Otherwise split the available log size
  // evenly across 1MB files.
  return std::max((size_t)2,
                  (max_total_log_size / 2) / kRotatingLogFileDefaultSize);
}

}  // namespace rtc
