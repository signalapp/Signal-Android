/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/bytebuffer.h"

#include <assert.h>
#include <string.h>

#include <algorithm>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/byteorder.h"

namespace rtc {

static const int DEFAULT_SIZE = 4096;

ByteBufferWriter::ByteBufferWriter()
    : ByteBuffer(ORDER_NETWORK) {
  Construct(NULL, DEFAULT_SIZE);
}

ByteBufferWriter::ByteBufferWriter(ByteOrder byte_order)
    : ByteBuffer(byte_order) {
  Construct(NULL, DEFAULT_SIZE);
}

ByteBufferWriter::ByteBufferWriter(const char* bytes, size_t len)
    : ByteBuffer(ORDER_NETWORK) {
  Construct(bytes, len);
}

ByteBufferWriter::ByteBufferWriter(const char* bytes, size_t len,
                                   ByteOrder byte_order)
    : ByteBuffer(byte_order) {
  Construct(bytes, len);
}

void ByteBufferWriter::Construct(const char* bytes, size_t len) {
  start_ = 0;
  size_ = len;
  bytes_ = new char[size_];

  if (bytes) {
    end_ = len;
    memcpy(bytes_, bytes, end_);
  } else {
    end_ = 0;
  }
}

ByteBufferWriter::~ByteBufferWriter() {
  delete[] bytes_;
}

void ByteBufferWriter::WriteUInt8(uint8_t val) {
  WriteBytes(reinterpret_cast<const char*>(&val), 1);
}

void ByteBufferWriter::WriteUInt16(uint16_t val) {
  uint16_t v = (Order() == ORDER_NETWORK) ? HostToNetwork16(val) : val;
  WriteBytes(reinterpret_cast<const char*>(&v), 2);
}

void ByteBufferWriter::WriteUInt24(uint32_t val) {
  uint32_t v = (Order() == ORDER_NETWORK) ? HostToNetwork32(val) : val;
  char* start = reinterpret_cast<char*>(&v);
  if (Order() == ORDER_NETWORK || IsHostBigEndian()) {
    ++start;
  }
  WriteBytes(start, 3);
}

void ByteBufferWriter::WriteUInt32(uint32_t val) {
  uint32_t v = (Order() == ORDER_NETWORK) ? HostToNetwork32(val) : val;
  WriteBytes(reinterpret_cast<const char*>(&v), 4);
}

void ByteBufferWriter::WriteUInt64(uint64_t val) {
  uint64_t v = (Order() == ORDER_NETWORK) ? HostToNetwork64(val) : val;
  WriteBytes(reinterpret_cast<const char*>(&v), 8);
}

// Serializes an unsigned varint in the format described by
// https://developers.google.com/protocol-buffers/docs/encoding#varints
// with the caveat that integers are 64-bit, not 128-bit.
void ByteBufferWriter::WriteUVarint(uint64_t val) {
  while (val >= 0x80) {
    // Write 7 bits at a time, then set the msb to a continuation byte (msb=1).
    char byte = static_cast<char>(val) | 0x80;
    WriteBytes(&byte, 1);
    val >>= 7;
  }
  char last_byte = static_cast<char>(val);
  WriteBytes(&last_byte, 1);
}

void ByteBufferWriter::WriteString(const std::string& val) {
  WriteBytes(val.c_str(), val.size());
}

void ByteBufferWriter::WriteBytes(const char* val, size_t len) {
  memcpy(ReserveWriteBuffer(len), val, len);
}

char* ByteBufferWriter::ReserveWriteBuffer(size_t len) {
  if (Length() + len > Capacity())
    Resize(Length() + len);

  char* start = bytes_ + end_;
  end_ += len;
  return start;
}

void ByteBufferWriter::Resize(size_t size) {
  size_t len = std::min(end_ - start_, size);
  if (size <= size_) {
    // Don't reallocate, just move data backwards
    memmove(bytes_, bytes_ + start_, len);
  } else {
    // Reallocate a larger buffer.
    size_ = std::max(size, 3 * size_ / 2);
    char* new_bytes = new char[size_];
    memcpy(new_bytes, bytes_ + start_, len);
    delete [] bytes_;
    bytes_ = new_bytes;
  }
  start_ = 0;
  end_ = len;
}

void ByteBufferWriter::Clear() {
  memset(bytes_, 0, size_);
  start_ = end_ = 0;
}


ByteBufferReader::ByteBufferReader(const char* bytes, size_t len)
    : ByteBuffer(ORDER_NETWORK) {
  Construct(bytes, len);
}

ByteBufferReader::ByteBufferReader(const char* bytes, size_t len,
                                   ByteOrder byte_order)
    : ByteBuffer(byte_order) {
  Construct(bytes, len);
}

ByteBufferReader::ByteBufferReader(const char* bytes)
    : ByteBuffer(ORDER_NETWORK) {
  Construct(bytes, strlen(bytes));
}

ByteBufferReader::ByteBufferReader(const Buffer& buf)
    : ByteBuffer(ORDER_NETWORK) {
  Construct(buf.data<char>(), buf.size());
}

ByteBufferReader::ByteBufferReader(const ByteBufferWriter& buf)
    : ByteBuffer(buf.Order()) {
  Construct(buf.Data(), buf.Length());
}

void ByteBufferReader::Construct(const char* bytes, size_t len) {
  bytes_ = bytes;
  size_ = len;
  start_ = 0;
  end_ = len;
}

bool ByteBufferReader::ReadUInt8(uint8_t* val) {
  if (!val) return false;

  return ReadBytes(reinterpret_cast<char*>(val), 1);
}

bool ByteBufferReader::ReadUInt16(uint16_t* val) {
  if (!val) return false;

  uint16_t v;
  if (!ReadBytes(reinterpret_cast<char*>(&v), 2)) {
    return false;
  } else {
    *val = (Order() == ORDER_NETWORK) ? NetworkToHost16(v) : v;
    return true;
  }
}

bool ByteBufferReader::ReadUInt24(uint32_t* val) {
  if (!val) return false;

  uint32_t v = 0;
  char* read_into = reinterpret_cast<char*>(&v);
  if (Order() == ORDER_NETWORK || IsHostBigEndian()) {
    ++read_into;
  }

  if (!ReadBytes(read_into, 3)) {
    return false;
  } else {
    *val = (Order() == ORDER_NETWORK) ? NetworkToHost32(v) : v;
    return true;
  }
}

bool ByteBufferReader::ReadUInt32(uint32_t* val) {
  if (!val) return false;

  uint32_t v;
  if (!ReadBytes(reinterpret_cast<char*>(&v), 4)) {
    return false;
  } else {
    *val = (Order() == ORDER_NETWORK) ? NetworkToHost32(v) : v;
    return true;
  }
}

bool ByteBufferReader::ReadUInt64(uint64_t* val) {
  if (!val) return false;

  uint64_t v;
  if (!ReadBytes(reinterpret_cast<char*>(&v), 8)) {
    return false;
  } else {
    *val = (Order() == ORDER_NETWORK) ? NetworkToHost64(v) : v;
    return true;
  }
}

bool ByteBufferReader::ReadUVarint(uint64_t* val) {
  if (!val) {
    return false;
  }
  // Integers are deserialized 7 bits at a time, with each byte having a
  // continuation byte (msb=1) if there are more bytes to be read.
  uint64_t v = 0;
  for (int i = 0; i < 64; i += 7) {
    char byte;
    if (!ReadBytes(&byte, 1)) {
      return false;
    }
    // Read the first 7 bits of the byte, then offset by bits read so far.
    v |= (static_cast<uint64_t>(byte) & 0x7F) << i;
    // True if the msb is not a continuation byte.
    if (static_cast<uint64_t>(byte) < 0x80) {
      *val = v;
      return true;
    }
  }
  return false;
}

bool ByteBufferReader::ReadString(std::string* val, size_t len) {
  if (!val) return false;

  if (len > Length()) {
    return false;
  } else {
    val->append(bytes_ + start_, len);
    start_ += len;
    return true;
  }
}

bool ByteBufferReader::ReadBytes(char* val, size_t len) {
  if (len > Length()) {
    return false;
  } else {
    memcpy(val, bytes_ + start_, len);
    start_ += len;
    return true;
  }
}

bool ByteBufferReader::Consume(size_t size) {
  if (size > Length())
    return false;
  start_ += size;
  return true;
}

}  // namespace rtc
