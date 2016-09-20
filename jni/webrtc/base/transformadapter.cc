/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/transformadapter.h"

#include <string.h>

#include "webrtc/base/common.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////

TransformAdapter::TransformAdapter(StreamInterface * stream,
                                   TransformInterface * transform,
                                   bool direction_read)
    : StreamAdapterInterface(stream), transform_(transform),
      direction_read_(direction_read), state_(ST_PROCESSING), len_(0) {
}

TransformAdapter::~TransformAdapter() {
  TransformAdapter::Close();
  delete transform_;
}

StreamResult
TransformAdapter::Read(void * buffer, size_t buffer_len,
                       size_t * read, int * error) {
  if (!direction_read_)
    return SR_EOS;

  while (state_ != ST_ERROR) {
    if (state_ == ST_COMPLETE)
      return SR_EOS;

    // Buffer more data
    if ((state_ == ST_PROCESSING) && (len_ < sizeof(buffer_))) {
      size_t subread;
      StreamResult result = StreamAdapterInterface::Read(
                              buffer_ + len_,
                              sizeof(buffer_) - len_,
                              &subread,
                              &error_);
      if (result == SR_BLOCK) {
        return SR_BLOCK;
      } else if (result == SR_ERROR) {
        state_ = ST_ERROR;
        break;
      } else if (result == SR_EOS) {
        state_ = ST_FLUSHING;
      } else {
        len_ += subread;
      }
    }

    // Process buffered data
    size_t in_len = len_;
    size_t out_len = buffer_len;
    StreamResult result = transform_->Transform(buffer_, &in_len,
                                                buffer, &out_len,
                                                (state_ == ST_FLUSHING));
    ASSERT(result != SR_BLOCK);
    if (result == SR_EOS) {
      // Note: Don't signal SR_EOS this iteration, unless out_len is zero
      state_ = ST_COMPLETE;
    } else if (result == SR_ERROR) {
      state_ = ST_ERROR;
      error_ = -1; // TODO: propagate error
      break;
    } else if ((out_len == 0) && (state_ == ST_FLUSHING)) {
      // If there is no output AND no more input, then something is wrong
      state_ = ST_ERROR;
      error_ = -1; // TODO: better error code?
      break;
    }

    len_ -= in_len;
    if (len_ > 0)
      memmove(buffer_, buffer_ + in_len, len_);

    if (out_len == 0)
      continue;

    if (read)
      *read = out_len;
    return SR_SUCCESS;
  }

  if (error)
    *error = error_;
  return SR_ERROR;
}

StreamResult
TransformAdapter::Write(const void * data, size_t data_len,
                        size_t * written, int * error) {
  if (direction_read_)
    return SR_EOS;

  size_t bytes_written = 0;
  while (state_ != ST_ERROR) {
    if (state_ == ST_COMPLETE)
      return SR_EOS;

    if (len_ < sizeof(buffer_)) {
      // Process buffered data
      size_t in_len = data_len;
      size_t out_len = sizeof(buffer_) - len_;
      StreamResult result = transform_->Transform(data, &in_len,
                                                  buffer_ + len_, &out_len,
                                                  (state_ == ST_FLUSHING));

      ASSERT(result != SR_BLOCK);
      if (result == SR_EOS) {
        // Note: Don't signal SR_EOS this iteration, unless no data written
        state_ = ST_COMPLETE;
      } else if (result == SR_ERROR) {
        ASSERT(false); // When this happens, think about what should be done
        state_ = ST_ERROR;
        error_ = -1; // TODO: propagate error
        break;
      }

      len_ = out_len;
      bytes_written = in_len;
    }

    size_t pos = 0;
    while (pos < len_) {
      size_t subwritten;
      StreamResult result = StreamAdapterInterface::Write(buffer_ + pos,
                                                          len_ - pos,
                                                          &subwritten,
                                                          &error_);
      if (result == SR_BLOCK) {
        ASSERT(false); // TODO: we should handle this
        return SR_BLOCK;
      } else if (result == SR_ERROR) {
        state_ = ST_ERROR;
        break;
      } else if (result == SR_EOS) {
        state_ = ST_COMPLETE;
        break;
      }

      pos += subwritten;
    }

    len_ -= pos;
    if (len_ > 0)
      memmove(buffer_, buffer_ + pos, len_);

    if (bytes_written == 0)
      continue;

    if (written)
      *written = bytes_written;
    return SR_SUCCESS;
  }

  if (error)
    *error = error_;
  return SR_ERROR;
}

void
TransformAdapter::Close() {
  if (!direction_read_ && (state_ == ST_PROCESSING)) {
    state_ = ST_FLUSHING;
    do {
      Write(0, 0, NULL, NULL);
    } while (state_ == ST_FLUSHING);
  }
  state_ = ST_COMPLETE;
  StreamAdapterInterface::Close();
}

bool TransformAdapter::GetAvailable(size_t* size) const {
  return false;
}

bool TransformAdapter::ReserveSize(size_t size) {
  return true;
}

bool TransformAdapter::Rewind() {
  return false;
}

} // namespace rtc
