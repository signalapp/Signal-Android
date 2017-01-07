/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef _WEBRTC_BASE_CRYPTSTRING_H_
#define _WEBRTC_BASE_CRYPTSTRING_H_

#include <string.h>

#include <memory>
#include <string>
#include <vector>

#include "webrtc/base/linked_ptr.h"

namespace rtc {

class CryptStringImpl {
public:
  virtual ~CryptStringImpl() {}
  virtual size_t GetLength() const = 0;
  virtual void CopyTo(char * dest, bool nullterminate) const = 0;
  virtual std::string UrlEncode() const = 0;
  virtual CryptStringImpl * Copy() const = 0;
  virtual void CopyRawTo(std::vector<unsigned char> * dest) const = 0;
};

class EmptyCryptStringImpl : public CryptStringImpl {
public:
  ~EmptyCryptStringImpl() override {}
  size_t GetLength() const override;
  void CopyTo(char* dest, bool nullterminate) const override;
  std::string UrlEncode() const override;
  CryptStringImpl* Copy() const override;
  void CopyRawTo(std::vector<unsigned char>* dest) const override;
};

class CryptString {
 public:
  CryptString();
  size_t GetLength() const { return impl_->GetLength(); }
  void CopyTo(char * dest, bool nullterminate) const { impl_->CopyTo(dest, nullterminate); }
  CryptString(const CryptString& other);
  explicit CryptString(const CryptStringImpl& impl);
  ~CryptString();
  CryptString & operator=(const CryptString & other) {
    if (this != &other) {
      impl_.reset(other.impl_->Copy());
    }
    return *this;
  }
  void Clear() { impl_.reset(new EmptyCryptStringImpl()); }
  std::string UrlEncode() const { return impl_->UrlEncode(); }
  void CopyRawTo(std::vector<unsigned char> * dest) const {
    return impl_->CopyRawTo(dest);
  }

 private:
  std::unique_ptr<const CryptStringImpl> impl_;
};


// Used for constructing strings where a password is involved and we
// need to ensure that we zero memory afterwards
class FormatCryptString {
public:
  FormatCryptString() {
    storage_ = new char[32];
    capacity_ = 32;
    length_ = 0;
    storage_[0] = 0;
  }
  
  void Append(const std::string & text) {
    Append(text.data(), text.length());
  }

  void Append(const char * data, size_t length) {
    EnsureStorage(length_ + length + 1);
    memcpy(storage_ + length_, data, length);
    length_ += length;
    storage_[length_] = '\0';
  }
  
  void Append(const CryptString * password) {
    size_t len = password->GetLength();
    EnsureStorage(length_ + len + 1);
    password->CopyTo(storage_ + length_, true);
    length_ += len;
  }

  size_t GetLength() {
    return length_;
  }

  const char * GetData() {
    return storage_;
  }


  // Ensures storage of at least n bytes
  void EnsureStorage(size_t n) {
    if (capacity_ >= n) {
      return;
    }

    size_t old_capacity = capacity_;
    char * old_storage = storage_;

    for (;;) {
      capacity_ *= 2;
      if (capacity_ >= n)
        break;
    }

    storage_ = new char[capacity_];

    if (old_capacity) {
      memcpy(storage_, old_storage, length_);
    
      // zero memory in a way that an optimizer won't optimize it out
      old_storage[0] = 0;
      for (size_t i = 1; i < old_capacity; i++) {
        old_storage[i] = old_storage[i - 1];
      }
      delete[] old_storage;
    }
  }  

  ~FormatCryptString() {
    if (capacity_) {
      storage_[0] = 0;
      for (size_t i = 1; i < capacity_; i++) {
        storage_[i] = storage_[i - 1];
      }
    }
    delete[] storage_;
  }
private:
  char * storage_;
  size_t capacity_;
  size_t length_;
};

class InsecureCryptStringImpl : public CryptStringImpl {
 public:
  std::string& password() { return password_; }
  const std::string& password() const { return password_; }

  ~InsecureCryptStringImpl() override = default;
  size_t GetLength() const override;
  void CopyTo(char* dest, bool nullterminate) const override;
  std::string UrlEncode() const override;
  CryptStringImpl* Copy() const override;
  void CopyRawTo(std::vector<unsigned char>* dest) const override;

 private:
  std::string password_;
};

}

#endif  // _WEBRTC_BASE_CRYPTSTRING_H_
