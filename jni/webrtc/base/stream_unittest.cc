/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/fileutils.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/stream.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// TestStream
///////////////////////////////////////////////////////////////////////////////

class TestStream : public StreamInterface {
 public:
  TestStream() : pos_(0) { }

  virtual StreamState GetState() const { return SS_OPEN; }
  virtual StreamResult Read(void* buffer, size_t buffer_len,
                            size_t* read, int* error) {
    unsigned char* uc_buffer = static_cast<unsigned char*>(buffer);
    for (size_t i = 0; i < buffer_len; ++i) {
      uc_buffer[i] = static_cast<unsigned char>(pos_++);
    }
    if (read)
      *read = buffer_len;
    return SR_SUCCESS;
  }
  virtual StreamResult Write(const void* data, size_t data_len,
                             size_t* written, int* error) {
    if (error)
      *error = -1;
    return SR_ERROR;
  }
  virtual void Close() { }
  virtual bool SetPosition(size_t position) {
    pos_ = position;
    return true;
  }
  virtual bool GetPosition(size_t* position) const {
    if (position) *position = pos_;
    return true;
  }
  virtual bool GetSize(size_t* size) const {
    return false;
  }
  virtual bool GetAvailable(size_t* size) const {
    return false;
  }

 private:
  size_t pos_;
};

bool VerifyTestBuffer(unsigned char* buffer, size_t len,
                      unsigned char value) {
  bool passed = true;
  for (size_t i = 0; i < len; ++i) {
    if (buffer[i] != value++) {
      passed = false;
      break;
    }
  }
  // Ensure that we don't pass again without re-writing
  memset(buffer, 0, len);
  return passed;
}

void SeekTest(StreamInterface* stream, const unsigned char value) {
  size_t bytes;
  unsigned char buffer[13] = { 0 };
  const size_t kBufSize = sizeof(buffer);

  EXPECT_EQ(stream->Read(buffer, kBufSize, &bytes, NULL), SR_SUCCESS);
  EXPECT_EQ(bytes, kBufSize);
  EXPECT_TRUE(VerifyTestBuffer(buffer, kBufSize, value));
  EXPECT_TRUE(stream->GetPosition(&bytes));
  EXPECT_EQ(13U, bytes);

  EXPECT_TRUE(stream->SetPosition(7));

  EXPECT_EQ(stream->Read(buffer, kBufSize, &bytes, NULL), SR_SUCCESS);
  EXPECT_EQ(bytes, kBufSize);
  EXPECT_TRUE(VerifyTestBuffer(buffer, kBufSize, value + 7));
  EXPECT_TRUE(stream->GetPosition(&bytes));
  EXPECT_EQ(20U, bytes);
}

TEST(FifoBufferTest, TestAll) {
  const size_t kSize = 16;
  const char in[kSize * 2 + 1] = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
  char out[kSize * 2];
  void* p;
  const void* q;
  size_t bytes;
  FifoBuffer buf(kSize);
  StreamInterface* stream = &buf;

  // Test assumptions about base state
  EXPECT_EQ(SS_OPEN, stream->GetState());
  EXPECT_EQ(SR_BLOCK, stream->Read(out, kSize, &bytes, NULL));
  EXPECT_TRUE(NULL != stream->GetReadData(&bytes));
  EXPECT_EQ((size_t)0, bytes);
  stream->ConsumeReadData(0);
  EXPECT_TRUE(NULL != stream->GetWriteBuffer(&bytes));
  EXPECT_EQ(kSize, bytes);
  stream->ConsumeWriteBuffer(0);

  // Try a full write
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);

  // Try a write that should block
  EXPECT_EQ(SR_BLOCK, stream->Write(in, kSize, &bytes, NULL));

  // Try a full read
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize));

  // Try a read that should block
  EXPECT_EQ(SR_BLOCK, stream->Read(out, kSize, &bytes, NULL));

  // Try a too-big write
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize * 2, &bytes, NULL));
  EXPECT_EQ(bytes, kSize);

  // Try a too-big read
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize * 2, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize));

  // Try some small writes and reads
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize / 2));
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize / 2));
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize / 2));

  // Try wraparound reads and writes in the following pattern
  // WWWWWWWWWWWW.... 0123456789AB....
  // RRRRRRRRXXXX.... ........89AB....
  // WWWW....XXXXWWWW 4567....89AB0123
  // XXXX....RRRRXXXX 4567........0123
  // XXXXWWWWWWWWXXXX 4567012345670123
  // RRRRXXXXXXXXRRRR ....01234567....
  // ....RRRRRRRR.... ................
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize * 3 / 4, &bytes, NULL));
  EXPECT_EQ(kSize * 3 / 4, bytes);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize / 2));
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 4, &bytes, NULL));
  EXPECT_EQ(kSize / 4 , bytes);
  EXPECT_EQ(0, memcmp(in + kSize / 2, out, kSize / 4));
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2 , bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize / 2));
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 2, &bytes, NULL));
  EXPECT_EQ(kSize / 2 , bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize / 2));

  // Use GetWriteBuffer to reset the read_position for the next tests
  stream->GetWriteBuffer(&bytes);
  stream->ConsumeWriteBuffer(0);

  // Try using GetReadData to do a full read
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize, &bytes, NULL));
  q = stream->GetReadData(&bytes);
  EXPECT_TRUE(NULL != q);
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(q, in, kSize));
  stream->ConsumeReadData(kSize);
  EXPECT_EQ(SR_BLOCK, stream->Read(out, kSize, &bytes, NULL));

  // Try using GetReadData to do some small reads
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize, &bytes, NULL));
  q = stream->GetReadData(&bytes);
  EXPECT_TRUE(NULL != q);
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(q, in, kSize / 2));
  stream->ConsumeReadData(kSize / 2);
  q = stream->GetReadData(&bytes);
  EXPECT_TRUE(NULL != q);
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(q, in + kSize / 2, kSize / 2));
  stream->ConsumeReadData(kSize / 2);
  EXPECT_EQ(SR_BLOCK, stream->Read(out, kSize, &bytes, NULL));

  // Try using GetReadData in a wraparound case
  // WWWWWWWWWWWWWWWW 0123456789ABCDEF
  // RRRRRRRRRRRRXXXX ............CDEF
  // WWWWWWWW....XXXX 01234567....CDEF
  // ............RRRR 01234567........
  // RRRRRRRR........ ................
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize, &bytes, NULL));
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize * 3 / 4, &bytes, NULL));
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize / 2, &bytes, NULL));
  q = stream->GetReadData(&bytes);
  EXPECT_TRUE(NULL != q);
  EXPECT_EQ(kSize / 4, bytes);
  EXPECT_EQ(0, memcmp(q, in + kSize * 3 / 4, kSize / 4));
  stream->ConsumeReadData(kSize / 4);
  q = stream->GetReadData(&bytes);
  EXPECT_TRUE(NULL != q);
  EXPECT_EQ(kSize / 2, bytes);
  EXPECT_EQ(0, memcmp(q, in, kSize / 2));
  stream->ConsumeReadData(kSize / 2);

  // Use GetWriteBuffer to reset the read_position for the next tests
  stream->GetWriteBuffer(&bytes);
  stream->ConsumeWriteBuffer(0);

  // Try using GetWriteBuffer to do a full write
  p = stream->GetWriteBuffer(&bytes);
  EXPECT_TRUE(NULL != p);
  EXPECT_EQ(kSize, bytes);
  memcpy(p, in, kSize);
  stream->ConsumeWriteBuffer(kSize);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize));

  // Try using GetWriteBuffer to do some small writes
  p = stream->GetWriteBuffer(&bytes);
  EXPECT_TRUE(NULL != p);
  EXPECT_EQ(kSize, bytes);
  memcpy(p, in, kSize / 2);
  stream->ConsumeWriteBuffer(kSize / 2);
  p = stream->GetWriteBuffer(&bytes);
  EXPECT_TRUE(NULL != p);
  EXPECT_EQ(kSize / 2, bytes);
  memcpy(p, in + kSize / 2, kSize / 2);
  stream->ConsumeWriteBuffer(kSize / 2);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize));

  // Try using GetWriteBuffer in a wraparound case
  // WWWWWWWWWWWW.... 0123456789AB....
  // RRRRRRRRXXXX.... ........89AB....
  // ........XXXXWWWW ........89AB0123
  // WWWW....XXXXXXXX 4567....89AB0123
  // RRRR....RRRRRRRR ................
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize * 3 / 4, &bytes, NULL));
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 2, &bytes, NULL));
  p = stream->GetWriteBuffer(&bytes);
  EXPECT_TRUE(NULL != p);
  EXPECT_EQ(kSize / 4, bytes);
  memcpy(p, in, kSize / 4);
  stream->ConsumeWriteBuffer(kSize / 4);
  p = stream->GetWriteBuffer(&bytes);
  EXPECT_TRUE(NULL != p);
  EXPECT_EQ(kSize / 2, bytes);
  memcpy(p, in + kSize / 4, kSize / 4);
  stream->ConsumeWriteBuffer(kSize / 4);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize * 3 / 4, &bytes, NULL));
  EXPECT_EQ(kSize * 3 / 4, bytes);
  EXPECT_EQ(0, memcmp(in + kSize / 2, out, kSize / 4));
  EXPECT_EQ(0, memcmp(in, out + kSize / 4, kSize / 4));

  // Check that the stream is now empty
  EXPECT_EQ(SR_BLOCK, stream->Read(out, kSize, &bytes, NULL));

  // Try growing the buffer
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);
  EXPECT_TRUE(buf.SetCapacity(kSize * 2));
  EXPECT_EQ(SR_SUCCESS, stream->Write(in + kSize, kSize, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize * 2, &bytes, NULL));
  EXPECT_EQ(kSize * 2, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize * 2));

  // Try shrinking the buffer
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);
  EXPECT_TRUE(buf.SetCapacity(kSize));
  EXPECT_EQ(SR_BLOCK, stream->Write(in, kSize, &bytes, NULL));
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize, &bytes, NULL));
  EXPECT_EQ(kSize, bytes);
  EXPECT_EQ(0, memcmp(in, out, kSize));

  // Write to the stream, close it, read the remaining bytes
  EXPECT_EQ(SR_SUCCESS, stream->Write(in, kSize / 2, &bytes, NULL));
  stream->Close();
  EXPECT_EQ(SS_CLOSED, stream->GetState());
  EXPECT_EQ(SR_EOS, stream->Write(in, kSize / 2, &bytes, NULL));
  EXPECT_EQ(SR_SUCCESS, stream->Read(out, kSize / 2, &bytes, NULL));
  EXPECT_EQ(0, memcmp(in, out, kSize / 2));
  EXPECT_EQ(SR_EOS, stream->Read(out, kSize / 2, &bytes, NULL));
}

TEST(FifoBufferTest, FullBufferCheck) {
  FifoBuffer buff(10);
  buff.ConsumeWriteBuffer(10);

  size_t free;
  EXPECT_TRUE(buff.GetWriteBuffer(&free) != NULL);
  EXPECT_EQ(0U, free);
}

TEST(FifoBufferTest, WriteOffsetAndReadOffset) {
  const size_t kSize = 16;
  const char in[kSize * 2 + 1] = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
  char out[kSize * 2];
  FifoBuffer buf(kSize);

  // Write 14 bytes.
  EXPECT_EQ(SR_SUCCESS, buf.Write(in, 14, NULL, NULL));

  // Make sure data is in |buf|.
  size_t buffered;
  EXPECT_TRUE(buf.GetBuffered(&buffered));
  EXPECT_EQ(14u, buffered);

  // Read 10 bytes.
  buf.ConsumeReadData(10);

  // There should be now 12 bytes of available space.
  size_t remaining;
  EXPECT_TRUE(buf.GetWriteRemaining(&remaining));
  EXPECT_EQ(12u, remaining);

  // Write at offset 12, this should fail.
  EXPECT_EQ(SR_BLOCK, buf.WriteOffset(in, 10, 12, NULL));

  // Write 8 bytes at offset 4, this wraps around the buffer.
  EXPECT_EQ(SR_SUCCESS, buf.WriteOffset(in, 8, 4, NULL));

  // Number of available space remains the same until we call
  // ConsumeWriteBuffer().
  EXPECT_TRUE(buf.GetWriteRemaining(&remaining));
  EXPECT_EQ(12u, remaining);
  buf.ConsumeWriteBuffer(12);

  // There's 4 bytes bypassed and 4 bytes no read so skip them and verify the
  // 8 bytes written.
  size_t read;
  EXPECT_EQ(SR_SUCCESS, buf.ReadOffset(out, 8, 8, &read));
  EXPECT_EQ(8u, read);
  EXPECT_EQ(0, memcmp(out, in, 8));

  // There should still be 16 bytes available for reading.
  EXPECT_TRUE(buf.GetBuffered(&buffered));
  EXPECT_EQ(16u, buffered);

  // Read at offset 16, this should fail since we don't have that much data.
  EXPECT_EQ(SR_BLOCK, buf.ReadOffset(out, 10, 16, NULL));
}

}  // namespace rtc
