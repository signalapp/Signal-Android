/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */


#ifndef WEBRTC_BASE_HTTPBASE_H__
#define WEBRTC_BASE_HTTPBASE_H__

#include "webrtc/base/httpcommon.h"

namespace rtc {

class StreamInterface;

///////////////////////////////////////////////////////////////////////////////
// HttpParser - Parses an HTTP stream provided via Process and end_of_input, and
// generates events for:
//  Structural Elements: Leader, Headers, Document Data
//  Events: End of Headers, End of Document, Errors
///////////////////////////////////////////////////////////////////////////////

class HttpParser {
public:
  enum ProcessResult { PR_CONTINUE, PR_BLOCK, PR_COMPLETE };
  HttpParser();
  virtual ~HttpParser();
  
  void reset();
  ProcessResult Process(const char* buffer, size_t len, size_t* processed,
                        HttpError* error);
  bool is_valid_end_of_input() const;
  void complete(HttpError err);
  
  size_t GetDataRemaining() const { return data_size_; }

protected:
  ProcessResult ProcessLine(const char* line, size_t len, HttpError* error);

  // HttpParser Interface
  virtual ProcessResult ProcessLeader(const char* line, size_t len,
                                      HttpError* error) = 0;
  virtual ProcessResult ProcessHeader(const char* name, size_t nlen,
                                      const char* value, size_t vlen,
                                      HttpError* error) = 0;
  virtual ProcessResult ProcessHeaderComplete(bool chunked, size_t& data_size,
                                              HttpError* error) = 0;
  virtual ProcessResult ProcessData(const char* data, size_t len, size_t& read,
                                    HttpError* error) = 0;
  virtual void OnComplete(HttpError err) = 0;
  
private:
  enum State {
    ST_LEADER, ST_HEADERS,
    ST_CHUNKSIZE, ST_CHUNKTERM, ST_TRAILERS,
    ST_DATA, ST_COMPLETE
  } state_;
  bool chunked_;
  size_t data_size_;
};

///////////////////////////////////////////////////////////////////////////////
// IHttpNotify
///////////////////////////////////////////////////////////////////////////////

enum HttpMode { HM_NONE, HM_CONNECT, HM_RECV, HM_SEND };

class IHttpNotify {
public:
  virtual ~IHttpNotify() {}
  virtual HttpError onHttpHeaderComplete(bool chunked, size_t& data_size) = 0;
  virtual void onHttpComplete(HttpMode mode, HttpError err) = 0;
  virtual void onHttpClosed(HttpError err) = 0;
};

///////////////////////////////////////////////////////////////////////////////
// HttpBase - Provides a state machine for implementing HTTP-based components.
// Attach HttpBase to a StreamInterface which represents a bidirectional HTTP
// stream, and then call send() or recv() to initiate sending or receiving one
// side of an HTTP transaction.  By default, HttpBase operates as an I/O pump,
// moving data from the HTTP stream to the HttpData object and vice versa.
// However, it can also operate in stream mode, in which case the user of the
// stream interface drives I/O via calls to Read().
///////////////////////////////////////////////////////////////////////////////

class HttpBase
: private HttpParser,
  public sigslot::has_slots<>
{
public:
  HttpBase();
  ~HttpBase() override;

  void notify(IHttpNotify* notify) { notify_ = notify; }
  bool attach(StreamInterface* stream);
  StreamInterface* stream() { return http_stream_; }
  StreamInterface* detach();
  bool isConnected() const;

  void send(HttpData* data);
  void recv(HttpData* data);
  void abort(HttpError err);

  HttpMode mode() const { return mode_; }

  void set_ignore_data(bool ignore) { ignore_data_ = ignore; }
  bool ignore_data() const { return ignore_data_; }

  // Obtaining this stream puts HttpBase into stream mode until the stream
  // is closed.  HttpBase can only expose one open stream interface at a time.
  // Further calls will return NULL.
  StreamInterface* GetDocumentStream();

protected:
  // Do cleanup when the http stream closes (error may be 0 for a clean
  // shutdown), and return the error code to signal.
  HttpError HandleStreamClose(int error);

  // DoReceiveLoop acts as a data pump, pulling data from the http stream,
  // pushing it through the HttpParser, and then populating the HttpData object
  // based on the callbacks from the parser.  One of the most interesting
  // callbacks is ProcessData, which provides the actual http document body.
  // This data is then written to the HttpData::document.  As a result, data
  // flows from the network to the document, with some incidental protocol
  // parsing in between.
  // Ideally, we would pass in the document* to DoReceiveLoop, to more easily
  // support GetDocumentStream().  However, since the HttpParser is callback
  // driven, we are forced to store the pointer somewhere until the callback
  // is triggered.
  // Returns true if the received document has finished, and
  // HttpParser::complete should be called.
  bool DoReceiveLoop(HttpError* err);

  void read_and_process_data();
  void flush_data();
  bool queue_headers();
  void do_complete(HttpError err = HE_NONE);

  void OnHttpStreamEvent(StreamInterface* stream, int events, int error);
  void OnDocumentEvent(StreamInterface* stream, int events, int error);

  // HttpParser Interface
  ProcessResult ProcessLeader(const char* line,
                              size_t len,
                              HttpError* error) override;
  ProcessResult ProcessHeader(const char* name,
                              size_t nlen,
                              const char* value,
                              size_t vlen,
                              HttpError* error) override;
  ProcessResult ProcessHeaderComplete(bool chunked,
                                      size_t& data_size,
                                      HttpError* error) override;
  ProcessResult ProcessData(const char* data,
                            size_t len,
                            size_t& read,
                            HttpError* error) override;
  void OnComplete(HttpError err) override;

private:
  class DocumentStream;
  friend class DocumentStream;

  enum { kBufferSize = 32 * 1024 };

  HttpMode mode_;
  HttpData* data_;
  IHttpNotify* notify_;
  StreamInterface* http_stream_;
  DocumentStream* doc_stream_;
  char buffer_[kBufferSize];
  size_t len_;

  bool ignore_data_, chunk_data_;
  HttpData::const_iterator header_;
};

///////////////////////////////////////////////////////////////////////////////

} // namespace rtc

#endif // WEBRTC_BASE_HTTPBASE_H__
