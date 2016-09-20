/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_BYTEBUFFER_H_
#define WEBRTC_BASE_BYTEBUFFER_H_

#include <string>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/buffer.h"
#include "webrtc/base/constructormagic.h"

namespace rtc {

class ByteBuffer {
 public:
  enum ByteOrder {
    ORDER_NETWORK = 0,  // Default, use network byte order (big endian).
    ORDER_HOST,         // Use the native order of the host.
  };

  explicit ByteBuffer(ByteOrder byte_order) : byte_order_(byte_order) {}

  ByteOrder Order() const { return byte_order_; }

 private:
  ByteOrder byte_order_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ByteBuffer);
};

class ByteBufferWriter : public ByteBuffer {
 public:
  // |byte_order| defines order of bytes in the buffer.
  ByteBufferWriter();
  explicit ByteBufferWriter(ByteOrder byte_order);
  ByteBufferWriter(const char* bytes, size_t len);
  ByteBufferWriter(const char* bytes, size_t len, ByteOrder byte_order);

  ~ByteBufferWriter();

  const char* Data() const { return bytes_ + start_; }
  size_t Length() const { return end_ - start_; }
  size_t Capacity() const { return size_ - start_; }

  // Write value to the buffer. Resizes the buffer when it is
  // neccessary.
  void WriteUInt8(uint8_t val);
  void WriteUInt16(uint16_t val);
  void WriteUInt24(uint32_t val);
  void WriteUInt32(uint32_t val);
  void WriteUInt64(uint64_t val);
  void WriteUVarint(uint64_t val);
  void WriteString(const std::string& val);
  void WriteBytes(const char* val, size_t len);

  // Reserves the given number of bytes and returns a char* that can be written
  // into. Useful for functions that require a char* buffer and not a
  // ByteBufferWriter.
  char* ReserveWriteBuffer(size_t len);

  // Resize the buffer to the specified |size|.
  void Resize(size_t size);

  // Clears the contents of the buffer. After this, Length() will be 0.
  void Clear();

 private:
  void Construct(const char* bytes, size_t size);

  char* bytes_;
  size_t size_;
  size_t start_;
  size_t end_;

  // There are sensible ways to define these, but they aren't needed in our code
  // base.
  RTC_DISALLOW_COPY_AND_ASSIGN(ByteBufferWriter);
};

// The ByteBufferReader references the passed data, i.e. the pointer must be
// valid during the lifetime of the reader.
class ByteBufferReader : public ByteBuffer {
 public:
  ByteBufferReader(const char* bytes, size_t len);
  ByteBufferReader(const char* bytes, size_t len, ByteOrder byte_order);

  // Initializes buffer from a zero-terminated string.
  explicit ByteBufferReader(const char* bytes);

  explicit ByteBufferReader(const Buffer& buf);

  explicit ByteBufferReader(const ByteBufferWriter& buf);

  // Returns start of unprocessed data.
  const char* Data() const { return bytes_ + start_; }
  // Returns number of unprocessed bytes.
  size_t Length() const { return end_ - start_; }

  // Read a next value from the buffer. Return false if there isn't
  // enough data left for the specified type.
  bool ReadUInt8(uint8_t* val);
  bool ReadUInt16(uint16_t* val);
  bool ReadUInt24(uint32_t* val);
  bool ReadUInt32(uint32_t* val);
  bool ReadUInt64(uint64_t* val);
  bool ReadUVarint(uint64_t* val);
  bool ReadBytes(char* val, size_t len);

  // Appends next |len| bytes from the buffer to |val|. Returns false
  // if there is less than |len| bytes left.
  bool ReadString(std::string* val, size_t len);

  // Moves current position |size| bytes forward. Returns false if
  // there is less than |size| bytes left in the buffer. Consume doesn't
  // permanently remove data, so remembered read positions are still valid
  // after this call.
  bool Consume(size_t size);

 private:
  void Construct(const char* bytes, size_t size);

  const char* bytes_;
  size_t size_;
  size_t start_;
  size_t end_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ByteBufferReader);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_BYTEBUFFER_H_
