/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TRANSFORMADAPTER_H__
#define WEBRTC_BASE_TRANSFORMADAPTER_H__

#include "webrtc/base/stream.h"

namespace rtc {
///////////////////////////////////////////////////////////////////////////////

class TransformInterface {
public:
  virtual ~TransformInterface() { }

  // Transform should convert the in_len bytes of input into the out_len-sized
  // output buffer.  If flush is true, there will be no more data following
  // input.
  // After the transformation, in_len contains the number of bytes consumed, and
  // out_len contains the number of bytes ready in output.
  // Note: Transform should not return SR_BLOCK, as there is no asynchronous
  // notification available.
  virtual StreamResult Transform(const void * input, size_t * in_len,
                                 void * output, size_t * out_len,
                                 bool flush) = 0;
};

///////////////////////////////////////////////////////////////////////////////

// TransformAdapter causes all data passed through to be transformed by the
// supplied TransformInterface object, which may apply compression, encryption,
// etc.

class TransformAdapter : public StreamAdapterInterface {
public:
  // Note that the transformation is unidirectional, in the direction specified
  // by the constructor.  Operations in the opposite direction result in SR_EOS.
  TransformAdapter(StreamInterface * stream,
                   TransformInterface * transform,
                   bool direction_read);
  ~TransformAdapter() override;

  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;
  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;
  void Close() override;

  // Apriori, we can't tell what the transformation does to the stream length.
  bool GetAvailable(size_t* size) const override;
  bool ReserveSize(size_t size) override;

  // Transformations might not be restartable
  virtual bool Rewind();

private:
  enum State { ST_PROCESSING, ST_FLUSHING, ST_COMPLETE, ST_ERROR };
  enum { BUFFER_SIZE = 1024 };

  TransformInterface * transform_;
  bool direction_read_;
  State state_;
  int error_;

  char buffer_[BUFFER_SIZE];
  size_t len_;
};

///////////////////////////////////////////////////////////////////////////////

} // namespace rtc

#endif // WEBRTC_BASE_TRANSFORMADAPTER_H__
