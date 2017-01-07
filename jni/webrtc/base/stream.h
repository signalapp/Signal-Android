/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_STREAM_H_
#define WEBRTC_BASE_STREAM_H_

#include <memory>
#include <stdio.h>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/buffer.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/messagehandler.h"
#include "webrtc/base/messagequeue.h"
#include "webrtc/base/sigslot.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// StreamInterface is a generic asynchronous stream interface, supporting read,
// write, and close operations, and asynchronous signalling of state changes.
// The interface is designed with file, memory, and socket implementations in
// mind.  Some implementations offer extended operations, such as seeking.
///////////////////////////////////////////////////////////////////////////////

// The following enumerations are declared outside of the StreamInterface
// class for brevity in use.

// The SS_OPENING state indicates that the stream will signal open or closed
// in the future.
enum StreamState { SS_CLOSED, SS_OPENING, SS_OPEN };

// Stream read/write methods return this value to indicate various success
// and failure conditions described below.
enum StreamResult { SR_ERROR, SR_SUCCESS, SR_BLOCK, SR_EOS };

// StreamEvents are used to asynchronously signal state transitionss.  The flags
// may be combined.
//  SE_OPEN: The stream has transitioned to the SS_OPEN state
//  SE_CLOSE: The stream has transitioned to the SS_CLOSED state
//  SE_READ: Data is available, so Read is likely to not return SR_BLOCK
//  SE_WRITE: Data can be written, so Write is likely to not return SR_BLOCK
enum StreamEvent { SE_OPEN = 1, SE_READ = 2, SE_WRITE = 4, SE_CLOSE = 8 };

class Thread;

struct StreamEventData : public MessageData {
  int events, error;
  StreamEventData(int ev, int er) : events(ev), error(er) { }
};

class StreamInterface : public MessageHandler {
 public:
  enum {
    MSG_POST_EVENT = 0xF1F1, MSG_MAX = MSG_POST_EVENT
  };

  ~StreamInterface() override;

  virtual StreamState GetState() const = 0;

  // Read attempts to fill buffer of size buffer_len.  Write attempts to send
  // data_len bytes stored in data.  The variables read and write are set only
  // on SR_SUCCESS (see below).  Likewise, error is only set on SR_ERROR.
  // Read and Write return a value indicating:
  //  SR_ERROR: an error occurred, which is returned in a non-null error
  //    argument.  Interpretation of the error requires knowledge of the
  //    stream's concrete type, which limits its usefulness.
  //  SR_SUCCESS: some number of bytes were successfully written, which is
  //    returned in a non-null read/write argument.
  //  SR_BLOCK: the stream is in non-blocking mode, and the operation would
  //    block, or the stream is in SS_OPENING state.
  //  SR_EOS: the end-of-stream has been reached, or the stream is in the
  //    SS_CLOSED state.
  virtual StreamResult Read(void* buffer, size_t buffer_len,
                            size_t* read, int* error) = 0;
  virtual StreamResult Write(const void* data, size_t data_len,
                             size_t* written, int* error) = 0;
  // Attempt to transition to the SS_CLOSED state.  SE_CLOSE will not be
  // signalled as a result of this call.
  virtual void Close() = 0;

  // Streams may signal one or more StreamEvents to indicate state changes.
  // The first argument identifies the stream on which the state change occured.
  // The second argument is a bit-wise combination of StreamEvents.
  // If SE_CLOSE is signalled, then the third argument is the associated error
  // code.  Otherwise, the value is undefined.
  // Note: Not all streams will support asynchronous event signalling.  However,
  // SS_OPENING and SR_BLOCK returned from stream member functions imply that
  // certain events will be raised in the future.
  sigslot::signal3<StreamInterface*, int, int> SignalEvent;

  // Like calling SignalEvent, but posts a message to the specified thread,
  // which will call SignalEvent.  This helps unroll the stack and prevent
  // re-entrancy.
  void PostEvent(Thread* t, int events, int err);
  // Like the aforementioned method, but posts to the current thread.
  void PostEvent(int events, int err);

  //
  // OPTIONAL OPERATIONS
  //
  // Not all implementations will support the following operations.  In general,
  // a stream will only support an operation if it reasonably efficient to do
  // so.  For example, while a socket could buffer incoming data to support
  // seeking, it will not do so.  Instead, a buffering stream adapter should
  // be used.
  //
  // Even though several of these operations are related, you should
  // always use whichever operation is most relevant.  For example, you may
  // be tempted to use GetSize() and GetPosition() to deduce the result of
  // GetAvailable().  However, a stream which is read-once may support the
  // latter operation but not the former.
  //

  // The following four methods are used to avoid copying data multiple times.

  // GetReadData returns a pointer to a buffer which is owned by the stream.
  // The buffer contains data_len bytes.  NULL is returned if no data is
  // available, or if the method fails.  If the caller processes the data, it
  // must call ConsumeReadData with the number of processed bytes.  GetReadData
  // does not require a matching call to ConsumeReadData if the data is not
  // processed.  Read and ConsumeReadData invalidate the buffer returned by
  // GetReadData.
  virtual const void* GetReadData(size_t* data_len);
  virtual void ConsumeReadData(size_t used) {}

  // GetWriteBuffer returns a pointer to a buffer which is owned by the stream.
  // The buffer has a capacity of buf_len bytes.  NULL is returned if there is
  // no buffer available, or if the method fails.  The call may write data to
  // the buffer, and then call ConsumeWriteBuffer with the number of bytes
  // written.  GetWriteBuffer does not require a matching call to
  // ConsumeWriteData if no data is written.  Write, ForceWrite, and
  // ConsumeWriteData invalidate the buffer returned by GetWriteBuffer.
  // TODO: Allow the caller to specify a minimum buffer size.  If the specified
  // amount of buffer is not yet available, return NULL and Signal SE_WRITE
  // when it is available.  If the requested amount is too large, return an
  // error.
  virtual void* GetWriteBuffer(size_t* buf_len);
  virtual void ConsumeWriteBuffer(size_t used) {}

  // Write data_len bytes found in data, circumventing any throttling which
  // would could cause SR_BLOCK to be returned.  Returns true if all the data
  // was written.  Otherwise, the method is unsupported, or an unrecoverable
  // error occurred, and the error value is set.  This method should be used
  // sparingly to write critical data which should not be throttled.  A stream
  // which cannot circumvent its blocking constraints should not implement this
  // method.
  // NOTE: This interface is being considered experimentally at the moment.  It
  // would be used by JUDP and BandwidthStream as a way to circumvent certain
  // soft limits in writing.
  //virtual bool ForceWrite(const void* data, size_t data_len, int* error) {
  //  if (error) *error = -1;
  //  return false;
  //}

  // Seek to a byte offset from the beginning of the stream.  Returns false if
  // the stream does not support seeking, or cannot seek to the specified
  // position.
  virtual bool SetPosition(size_t position);

  // Get the byte offset of the current position from the start of the stream.
  // Returns false if the position is not known.
  virtual bool GetPosition(size_t* position) const;

  // Get the byte length of the entire stream.  Returns false if the length
  // is not known.
  virtual bool GetSize(size_t* size) const;

  // Return the number of Read()-able bytes remaining before end-of-stream.
  // Returns false if not known.
  virtual bool GetAvailable(size_t* size) const;

  // Return the number of Write()-able bytes remaining before end-of-stream.
  // Returns false if not known.
  virtual bool GetWriteRemaining(size_t* size) const;

  // Return true if flush is successful.
  virtual bool Flush();

  // Communicates the amount of data which will be written to the stream.  The
  // stream may choose to preallocate memory to accomodate this data.  The
  // stream may return false to indicate that there is not enough room (ie,
  // Write will return SR_EOS/SR_ERROR at some point).  Note that calling this
  // function should not affect the existing state of data in the stream.
  virtual bool ReserveSize(size_t size);

  //
  // CONVENIENCE METHODS
  //
  // These methods are implemented in terms of other methods, for convenience.
  //

  // Seek to the start of the stream.
  inline bool Rewind() { return SetPosition(0); }

  // WriteAll is a helper function which repeatedly calls Write until all the
  // data is written, or something other than SR_SUCCESS is returned.  Note that
  // unlike Write, the argument 'written' is always set, and may be non-zero
  // on results other than SR_SUCCESS.  The remaining arguments have the
  // same semantics as Write.
  StreamResult WriteAll(const void* data, size_t data_len,
                        size_t* written, int* error);

  // Similar to ReadAll.  Calls Read until buffer_len bytes have been read, or
  // until a non-SR_SUCCESS result is returned.  'read' is always set.
  StreamResult ReadAll(void* buffer, size_t buffer_len,
                       size_t* read, int* error);

  // ReadLine is a helper function which repeatedly calls Read until it hits
  // the end-of-line character, or something other than SR_SUCCESS.
  // TODO: this is too inefficient to keep here.  Break this out into a buffered
  // readline object or adapter
  StreamResult ReadLine(std::string* line);

 protected:
  StreamInterface();

  // MessageHandler Interface
  void OnMessage(Message* msg) override;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(StreamInterface);
};

///////////////////////////////////////////////////////////////////////////////
// StreamAdapterInterface is a convenient base-class for adapting a stream.
// By default, all operations are pass-through.  Override the methods that you
// require adaptation.  Streams should really be upgraded to reference-counted.
// In the meantime, use the owned flag to indicate whether the adapter should
// own the adapted stream.
///////////////////////////////////////////////////////////////////////////////

class StreamAdapterInterface : public StreamInterface,
                               public sigslot::has_slots<> {
 public:
  explicit StreamAdapterInterface(StreamInterface* stream, bool owned = true);

  // Core Stream Interface
  StreamState GetState() const override;
  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;
  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;
  void Close() override;

  // Optional Stream Interface
  /*  Note: Many stream adapters were implemented prior to this Read/Write
      interface.  Therefore, a simple pass through of data in those cases may
      be broken.  At a later time, we should do a once-over pass of all
      adapters, and make them compliant with these interfaces, after which this
      code can be uncommented.
  virtual const void* GetReadData(size_t* data_len) {
    return stream_->GetReadData(data_len);
  }
  virtual void ConsumeReadData(size_t used) {
    stream_->ConsumeReadData(used);
  }

  virtual void* GetWriteBuffer(size_t* buf_len) {
    return stream_->GetWriteBuffer(buf_len);
  }
  virtual void ConsumeWriteBuffer(size_t used) {
    stream_->ConsumeWriteBuffer(used);
  }
  */

  /*  Note: This interface is currently undergoing evaluation.
  virtual bool ForceWrite(const void* data, size_t data_len, int* error) {
    return stream_->ForceWrite(data, data_len, error);
  }
  */

  bool SetPosition(size_t position) override;
  bool GetPosition(size_t* position) const override;
  bool GetSize(size_t* size) const override;
  bool GetAvailable(size_t* size) const override;
  bool GetWriteRemaining(size_t* size) const override;
  bool ReserveSize(size_t size) override;
  bool Flush() override;

  void Attach(StreamInterface* stream, bool owned = true);
  StreamInterface* Detach();

 protected:
  ~StreamAdapterInterface() override;

  // Note that the adapter presents itself as the origin of the stream events,
  // since users of the adapter may not recognize the adapted object.
  virtual void OnEvent(StreamInterface* stream, int events, int err);
  StreamInterface* stream() { return stream_; }

 private:
  StreamInterface* stream_;
  bool owned_;
  RTC_DISALLOW_COPY_AND_ASSIGN(StreamAdapterInterface);
};

///////////////////////////////////////////////////////////////////////////////
// StreamTap is a non-modifying, pass-through adapter, which copies all data
// in either direction to the tap.  Note that errors or blocking on writing to
// the tap will prevent further tap writes from occurring.
///////////////////////////////////////////////////////////////////////////////

class StreamTap : public StreamAdapterInterface {
 public:
  explicit StreamTap(StreamInterface* stream, StreamInterface* tap);
  ~StreamTap() override;

  void AttachTap(StreamInterface* tap);
  StreamInterface* DetachTap();
  StreamResult GetTapResult(int* error);

  // StreamAdapterInterface Interface
  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;
  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;

 private:
  std::unique_ptr<StreamInterface> tap_;
  StreamResult tap_result_;
  int tap_error_;
  RTC_DISALLOW_COPY_AND_ASSIGN(StreamTap);
};

///////////////////////////////////////////////////////////////////////////////
// NullStream gives errors on read, and silently discards all written data.
///////////////////////////////////////////////////////////////////////////////

class NullStream : public StreamInterface {
 public:
  NullStream();
  ~NullStream() override;

  // StreamInterface Interface
  StreamState GetState() const override;
  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;
  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;
  void Close() override;
};

///////////////////////////////////////////////////////////////////////////////
// FileStream is a simple implementation of a StreamInterface, which does not
// support asynchronous notification.
///////////////////////////////////////////////////////////////////////////////

class FileStream : public StreamInterface {
 public:
  FileStream();
  ~FileStream() override;

  // The semantics of filename and mode are the same as stdio's fopen
  virtual bool Open(const std::string& filename, const char* mode, int* error);
  virtual bool OpenShare(const std::string& filename, const char* mode,
                         int shflag, int* error);

  // By default, reads and writes are buffered for efficiency.  Disabling
  // buffering causes writes to block until the bytes on disk are updated.
  virtual bool DisableBuffering();

  StreamState GetState() const override;
  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;
  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;
  void Close() override;
  bool SetPosition(size_t position) override;
  bool GetPosition(size_t* position) const override;
  bool GetSize(size_t* size) const override;
  bool GetAvailable(size_t* size) const override;
  bool ReserveSize(size_t size) override;

  bool Flush() override;

#if defined(WEBRTC_POSIX) && !defined(__native_client__)
  // Tries to aquire an exclusive lock on the file.
  // Use OpenShare(...) on win32 to get similar functionality.
  bool TryLock();
  bool Unlock();
#endif

  // Note: Deprecated in favor of Filesystem::GetFileSize().
  static bool GetSize(const std::string& filename, size_t* size);

 protected:
  virtual void DoClose();

  FILE* file_;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(FileStream);
};

///////////////////////////////////////////////////////////////////////////////
// MemoryStream is a simple implementation of a StreamInterface over in-memory
// data.  Data is read and written at the current seek position.  Reads return
// end-of-stream when they reach the end of data.  Writes actually extend the
// end of data mark.
///////////////////////////////////////////////////////////////////////////////

class MemoryStreamBase : public StreamInterface {
 public:
  StreamState GetState() const override;
  StreamResult Read(void* buffer,
                    size_t bytes,
                    size_t* bytes_read,
                    int* error) override;
  StreamResult Write(const void* buffer,
                     size_t bytes,
                     size_t* bytes_written,
                     int* error) override;
  void Close() override;
  bool SetPosition(size_t position) override;
  bool GetPosition(size_t* position) const override;
  bool GetSize(size_t* size) const override;
  bool GetAvailable(size_t* size) const override;
  bool ReserveSize(size_t size) override;

  char* GetBuffer() { return buffer_; }
  const char* GetBuffer() const { return buffer_; }

 protected:
  MemoryStreamBase();

  virtual StreamResult DoReserve(size_t size, int* error);

  // Invariant: 0 <= seek_position <= data_length_ <= buffer_length_
  char* buffer_;
  size_t buffer_length_;
  size_t data_length_;
  size_t seek_position_;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(MemoryStreamBase);
};

// MemoryStream dynamically resizes to accomodate written data.

class MemoryStream : public MemoryStreamBase {
 public:
  MemoryStream();
  explicit MemoryStream(const char* data);  // Calls SetData(data, strlen(data))
  MemoryStream(const void* data, size_t length);  // Calls SetData(data, length)
  ~MemoryStream() override;

  void SetData(const void* data, size_t length);

 protected:
  StreamResult DoReserve(size_t size, int* error) override;
  // Memory Streams are aligned for efficiency.
  static const int kAlignment = 16;
  char* buffer_alloc_;
};

// ExternalMemoryStream adapts an external memory buffer, so writes which would
// extend past the end of the buffer will return end-of-stream.

class ExternalMemoryStream : public MemoryStreamBase {
 public:
  ExternalMemoryStream();
  ExternalMemoryStream(void* data, size_t length);
  ~ExternalMemoryStream() override;

  void SetData(void* data, size_t length);
};

// FifoBuffer allows for efficient, thread-safe buffering of data between
// writer and reader. As the data can wrap around the end of the buffer,
// MemoryStreamBase can't help us here.

class FifoBuffer : public StreamInterface {
 public:
  // Creates a FIFO buffer with the specified capacity.
  explicit FifoBuffer(size_t length);
  // Creates a FIFO buffer with the specified capacity and owner
  FifoBuffer(size_t length, Thread* owner);
  ~FifoBuffer() override;
  // Gets the amount of data currently readable from the buffer.
  bool GetBuffered(size_t* data_len) const;
  // Resizes the buffer to the specified capacity. Fails if data_length_ > size
  bool SetCapacity(size_t length);

  // Read into |buffer| with an offset from the current read position, offset
  // is specified in number of bytes.
  // This method doesn't adjust read position nor the number of available
  // bytes, user has to call ConsumeReadData() to do this.
  StreamResult ReadOffset(void* buffer, size_t bytes, size_t offset,
                          size_t* bytes_read);

  // Write |buffer| with an offset from the current write position, offset is
  // specified in number of bytes.
  // This method doesn't adjust the number of buffered bytes, user has to call
  // ConsumeWriteBuffer() to do this.
  StreamResult WriteOffset(const void* buffer, size_t bytes, size_t offset,
                           size_t* bytes_written);

  // StreamInterface methods
  StreamState GetState() const override;
  StreamResult Read(void* buffer,
                    size_t bytes,
                    size_t* bytes_read,
                    int* error) override;
  StreamResult Write(const void* buffer,
                     size_t bytes,
                     size_t* bytes_written,
                     int* error) override;
  void Close() override;
  const void* GetReadData(size_t* data_len) override;
  void ConsumeReadData(size_t used) override;
  void* GetWriteBuffer(size_t* buf_len) override;
  void ConsumeWriteBuffer(size_t used) override;
  bool GetWriteRemaining(size_t* size) const override;

 private:
  // Helper method that implements ReadOffset. Caller must acquire a lock
  // when calling this method.
  StreamResult ReadOffsetLocked(void* buffer, size_t bytes, size_t offset,
                                size_t* bytes_read)
      EXCLUSIVE_LOCKS_REQUIRED(crit_);

  // Helper method that implements WriteOffset. Caller must acquire a lock
  // when calling this method.
  StreamResult WriteOffsetLocked(const void* buffer, size_t bytes,
                                 size_t offset, size_t* bytes_written)
      EXCLUSIVE_LOCKS_REQUIRED(crit_);

  // keeps the opened/closed state of the stream
  StreamState state_ GUARDED_BY(crit_);
  // the allocated buffer
  std::unique_ptr<char[]> buffer_ GUARDED_BY(crit_);
  // size of the allocated buffer
  size_t buffer_length_ GUARDED_BY(crit_);
  // amount of readable data in the buffer
  size_t data_length_ GUARDED_BY(crit_);
  // offset to the readable data
  size_t read_position_ GUARDED_BY(crit_);
  // stream callbacks are dispatched on this thread
  Thread* owner_;
  // object lock
  CriticalSection crit_;
  RTC_DISALLOW_COPY_AND_ASSIGN(FifoBuffer);
};

///////////////////////////////////////////////////////////////////////////////

class LoggingAdapter : public StreamAdapterInterface {
 public:
  LoggingAdapter(StreamInterface* stream, LoggingSeverity level,
                 const std::string& label, bool hex_mode = false);

  void set_label(const std::string& label);

  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;
  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;
  void Close() override;

 protected:
  void OnEvent(StreamInterface* stream, int events, int err) override;

 private:
  LoggingSeverity level_;
  std::string label_;
  bool hex_mode_;
  LogMultilineState lms_;

  RTC_DISALLOW_COPY_AND_ASSIGN(LoggingAdapter);
};

///////////////////////////////////////////////////////////////////////////////
// StringStream - Reads/Writes to an external std::string
///////////////////////////////////////////////////////////////////////////////

class StringStream : public StreamInterface {
 public:
  explicit StringStream(std::string* str);
  explicit StringStream(const std::string& str);

  StreamState GetState() const override;
  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;
  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;
  void Close() override;
  bool SetPosition(size_t position) override;
  bool GetPosition(size_t* position) const override;
  bool GetSize(size_t* size) const override;
  bool GetAvailable(size_t* size) const override;
  bool ReserveSize(size_t size) override;

 private:
  std::string& str_;
  size_t read_pos_;
  bool read_only_;
};

///////////////////////////////////////////////////////////////////////////////
// StreamReference - A reference counting stream adapter
///////////////////////////////////////////////////////////////////////////////

// Keep in mind that the streams and adapters defined in this file are
// not thread-safe, so this has limited uses.

// A StreamRefCount holds the reference count and a pointer to the
// wrapped stream. It deletes the wrapped stream when there are no
// more references. We can then have multiple StreamReference
// instances pointing to one StreamRefCount, all wrapping the same
// stream.

class StreamReference : public StreamAdapterInterface {
  class StreamRefCount;
 public:
  // Constructor for the first reference to a stream
  // Note: get more references through NewReference(). Use this
  // constructor only once on a given stream.
  explicit StreamReference(StreamInterface* stream);
  StreamInterface* GetStream() { return stream(); }
  StreamInterface* NewReference();
  ~StreamReference() override;

 private:
  class StreamRefCount {
   public:
    explicit StreamRefCount(StreamInterface* stream)
        : stream_(stream), ref_count_(1) {
    }
    void AddReference() {
      CritScope lock(&cs_);
      ++ref_count_;
    }
    void Release() {
      int ref_count;
      {  // Atomic ops would have been a better fit here.
        CritScope lock(&cs_);
        ref_count = --ref_count_;
      }
      if (ref_count == 0) {
        delete stream_;
        delete this;
      }
    }
   private:
    StreamInterface* stream_;
    int ref_count_;
    CriticalSection cs_;
    RTC_DISALLOW_COPY_AND_ASSIGN(StreamRefCount);
  };

  // Constructor for adding references
  explicit StreamReference(StreamRefCount* stream_ref_count,
                           StreamInterface* stream);

  StreamRefCount* stream_ref_count_;
  RTC_DISALLOW_COPY_AND_ASSIGN(StreamReference);
};

///////////////////////////////////////////////////////////////////////////////

// Flow attempts to move bytes from source to sink via buffer of size
// buffer_len.  The function returns SR_SUCCESS when source reaches
// end-of-stream (returns SR_EOS), and all the data has been written successful
// to sink.  Alternately, if source returns SR_BLOCK or SR_ERROR, or if sink
// returns SR_BLOCK, SR_ERROR, or SR_EOS, then the function immediately returns
// with the unexpected StreamResult value.
// data_len is the length of the valid data in buffer. in case of error
// this is the data that read from source but can't move to destination.
// as a pass in parameter, it indicates data in buffer that should move to sink
StreamResult Flow(StreamInterface* source,
                  char* buffer, size_t buffer_len,
                  StreamInterface* sink, size_t* data_len = NULL);

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_BASE_STREAM_H_
