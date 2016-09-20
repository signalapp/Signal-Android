/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <algorithm>

#include "webrtc/base/gunit.h"
#include "webrtc/base/httpbase.h"
#include "webrtc/base/testutils.h"

namespace rtc {

const char* const kHttpResponse =
  "HTTP/1.1 200\r\n"
  "Connection: Keep-Alive\r\n"
  "Content-Type: text/plain\r\n"
  "Proxy-Authorization: 42\r\n"
  "Transfer-Encoding: chunked\r\n"
  "\r\n"
  "00000008\r\n"
  "Goodbye!\r\n"
  "0\r\n\r\n";

const char* const kHttpEmptyResponse =
  "HTTP/1.1 200\r\n"
  "Connection: Keep-Alive\r\n"
  "Content-Length: 0\r\n"
  "Proxy-Authorization: 42\r\n"
  "\r\n";

const char* const kHttpResponsePrefix =
  "HTTP/1.1 200\r\n"
  "Connection: Keep-Alive\r\n"
  "Content-Type: text/plain\r\n"
  "Proxy-Authorization: 42\r\n"
  "Transfer-Encoding: chunked\r\n"
  "\r\n"
  "8\r\n"
  "Goodbye!\r\n";

class HttpBaseTest : public testing::Test, public IHttpNotify {
public:
  enum EventType { E_HEADER_COMPLETE, E_COMPLETE, E_CLOSED };
  struct Event {
    EventType event;
    bool chunked;
    size_t data_size;
    HttpMode mode;
    HttpError err;
  };
  HttpBaseTest() : mem(NULL), obtain_stream(false), http_stream(NULL) { }

  virtual void SetUp() { }
  virtual void TearDown() {
    delete http_stream;
    // Avoid an ASSERT, in case a test doesn't clean up properly
    base.abort(HE_NONE);
  }

  virtual HttpError onHttpHeaderComplete(bool chunked, size_t& data_size) {
    LOG_F(LS_VERBOSE) << "chunked: " << chunked << " size: " << data_size;
    Event e = { E_HEADER_COMPLETE, chunked, data_size, HM_NONE, HE_NONE};
    events.push_back(e);
    if (obtain_stream) {
      ObtainDocumentStream();
    }
    return HE_NONE;
  }
  virtual void onHttpComplete(HttpMode mode, HttpError err) {
    LOG_F(LS_VERBOSE) << "mode: " << mode << " err: " << err;
    Event e = { E_COMPLETE, false, 0, mode, err };
    events.push_back(e);
  }
  virtual void onHttpClosed(HttpError err) {
    LOG_F(LS_VERBOSE) << "err: " << err;
    Event e = { E_CLOSED, false, 0, HM_NONE, err };
    events.push_back(e);
  }

  void SetupSource(const char* response);

  void VerifyHeaderComplete(size_t event_count, bool empty_doc);
  void VerifyDocumentContents(const char* expected_data,
                              size_t expected_length = SIZE_UNKNOWN);

  void ObtainDocumentStream();
  void VerifyDocumentStreamIsOpening();
  void VerifyDocumentStreamOpenEvent();
  void ReadDocumentStreamData(const char* expected_data);
  void VerifyDocumentStreamIsEOS();

  void SetupDocument(const char* response);
  void VerifySourceContents(const char* expected_data,
                            size_t expected_length = SIZE_UNKNOWN);

  void VerifyTransferComplete(HttpMode mode, HttpError error);

  HttpBase base;
  MemoryStream* mem;
  HttpResponseData data;

  // The source of http data, and source events
  testing::StreamSource src;
  std::vector<Event> events;

  // Document stream, and stream events
  bool obtain_stream;
  StreamInterface* http_stream;
  testing::StreamSink sink;
};

void HttpBaseTest::SetupSource(const char* http_data) {
  LOG_F(LS_VERBOSE) << "Enter";

  src.SetState(SS_OPENING);
  src.QueueString(http_data);

  base.notify(this);
  base.attach(&src);
  EXPECT_TRUE(events.empty());

  src.SetState(SS_OPEN);
  ASSERT_EQ(1U, events.size());
  EXPECT_EQ(E_COMPLETE, events[0].event);
  EXPECT_EQ(HM_CONNECT, events[0].mode);
  EXPECT_EQ(HE_NONE, events[0].err);
  events.clear();

  mem = new MemoryStream;
  data.document.reset(mem);
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::VerifyHeaderComplete(size_t event_count, bool empty_doc) {
  LOG_F(LS_VERBOSE) << "Enter";

  ASSERT_EQ(event_count, events.size());
  EXPECT_EQ(E_HEADER_COMPLETE, events[0].event);

  std::string header;
  EXPECT_EQ(HVER_1_1, data.version);
  EXPECT_EQ(static_cast<uint32_t>(HC_OK), data.scode);
  EXPECT_TRUE(data.hasHeader(HH_PROXY_AUTHORIZATION, &header));
  EXPECT_EQ("42", header);
  EXPECT_TRUE(data.hasHeader(HH_CONNECTION, &header));
  EXPECT_EQ("Keep-Alive", header);

  if (empty_doc) {
    EXPECT_FALSE(events[0].chunked);
    EXPECT_EQ(0U, events[0].data_size);

    EXPECT_TRUE(data.hasHeader(HH_CONTENT_LENGTH, &header));
    EXPECT_EQ("0", header);
  } else {
    EXPECT_TRUE(events[0].chunked);
    EXPECT_EQ(SIZE_UNKNOWN, events[0].data_size);

    EXPECT_TRUE(data.hasHeader(HH_CONTENT_TYPE, &header));
    EXPECT_EQ("text/plain", header);
    EXPECT_TRUE(data.hasHeader(HH_TRANSFER_ENCODING, &header));
    EXPECT_EQ("chunked", header);
  }
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::VerifyDocumentContents(const char* expected_data,
                                          size_t expected_length) {
  LOG_F(LS_VERBOSE) << "Enter";

  if (SIZE_UNKNOWN == expected_length) {
    expected_length = strlen(expected_data);
  }
  EXPECT_EQ(mem, data.document.get());

  size_t length;
  mem->GetSize(&length);
  EXPECT_EQ(expected_length, length);
  EXPECT_TRUE(0 == memcmp(expected_data, mem->GetBuffer(), length));
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::ObtainDocumentStream() {
  LOG_F(LS_VERBOSE) << "Enter";
  EXPECT_FALSE(http_stream);
  http_stream = base.GetDocumentStream();
  ASSERT_TRUE(NULL != http_stream);
  sink.Monitor(http_stream);
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::VerifyDocumentStreamIsOpening() {
  LOG_F(LS_VERBOSE) << "Enter";
  ASSERT_TRUE(NULL != http_stream);
  EXPECT_EQ(0, sink.Events(http_stream));
  EXPECT_EQ(SS_OPENING, http_stream->GetState());

  size_t read = 0;
  char buffer[5] = { 0 };
  EXPECT_EQ(SR_BLOCK, http_stream->Read(buffer, sizeof(buffer), &read, NULL));
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::VerifyDocumentStreamOpenEvent() {
  LOG_F(LS_VERBOSE) << "Enter";

  ASSERT_TRUE(NULL != http_stream);
  EXPECT_EQ(SE_OPEN | SE_READ, sink.Events(http_stream));
  EXPECT_EQ(SS_OPEN, http_stream->GetState());

  // HTTP headers haven't arrived yet
  EXPECT_EQ(0U, events.size());
  EXPECT_EQ(static_cast<uint32_t>(HC_INTERNAL_SERVER_ERROR), data.scode);
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::ReadDocumentStreamData(const char* expected_data) {
  LOG_F(LS_VERBOSE) << "Enter";

  ASSERT_TRUE(NULL != http_stream);
  EXPECT_EQ(SS_OPEN, http_stream->GetState());

  // Pump the HTTP I/O using Read, and verify the results.
  size_t verified_length = 0;
  const size_t expected_length = strlen(expected_data);
  while (verified_length < expected_length) {
    size_t read = 0;
    char buffer[5] = { 0 };
    size_t amt_to_read =
        std::min(expected_length - verified_length, sizeof(buffer));
    EXPECT_EQ(SR_SUCCESS, http_stream->Read(buffer, amt_to_read, &read, NULL));
    EXPECT_EQ(amt_to_read, read);
    EXPECT_TRUE(0 == memcmp(expected_data + verified_length, buffer, read));
    verified_length += read;
  }
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::VerifyDocumentStreamIsEOS() {
  LOG_F(LS_VERBOSE) << "Enter";

  ASSERT_TRUE(NULL != http_stream);
  size_t read = 0;
  char buffer[5] = { 0 };
  EXPECT_EQ(SR_EOS, http_stream->Read(buffer, sizeof(buffer), &read, NULL));
  EXPECT_EQ(SS_CLOSED, http_stream->GetState());

  // When EOS is caused by Read, we don't expect SE_CLOSE
  EXPECT_EQ(0, sink.Events(http_stream));
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::SetupDocument(const char* document_data) {
  LOG_F(LS_VERBOSE) << "Enter";
  src.SetState(SS_OPEN);

  base.notify(this);
  base.attach(&src);
  EXPECT_TRUE(events.empty());

  if (document_data) {
    // Note: we could just call data.set_success("text/plain", mem), but that
    // won't allow us to use the chunked transfer encoding.
    mem = new MemoryStream(document_data);
    data.document.reset(mem);
    data.setHeader(HH_CONTENT_TYPE, "text/plain");
    data.setHeader(HH_TRANSFER_ENCODING, "chunked");
  } else {
    data.setHeader(HH_CONTENT_LENGTH, "0");
  }
  data.scode = HC_OK;
  data.setHeader(HH_PROXY_AUTHORIZATION, "42");
  data.setHeader(HH_CONNECTION, "Keep-Alive");
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::VerifySourceContents(const char* expected_data,
                                        size_t expected_length) {
  LOG_F(LS_VERBOSE) << "Enter";
  if (SIZE_UNKNOWN == expected_length) {
    expected_length = strlen(expected_data);
  }
  std::string contents = src.ReadData();
  EXPECT_EQ(expected_length, contents.length());
  EXPECT_TRUE(0 == memcmp(expected_data, contents.data(), expected_length));
  LOG_F(LS_VERBOSE) << "Exit";
}

void HttpBaseTest::VerifyTransferComplete(HttpMode mode, HttpError error) {
  LOG_F(LS_VERBOSE) << "Enter";
  // Verify that http operation has completed
  ASSERT_TRUE(events.size() > 0);
  size_t last_event = events.size() - 1;
  EXPECT_EQ(E_COMPLETE, events[last_event].event);
  EXPECT_EQ(mode, events[last_event].mode);
  EXPECT_EQ(error, events[last_event].err);
  LOG_F(LS_VERBOSE) << "Exit";
}

//
// Tests
//

TEST_F(HttpBaseTest, SupportsSend) {
  // Queue response document
  SetupDocument("Goodbye!");

  // Begin send
  base.send(&data);

  // Send completed successfully
  VerifyTransferComplete(HM_SEND, HE_NONE);
  VerifySourceContents(kHttpResponse);
}

TEST_F(HttpBaseTest, SupportsSendNoDocument) {
  // Queue response document
  SetupDocument(NULL);

  // Begin send
  base.send(&data);

  // Send completed successfully
  VerifyTransferComplete(HM_SEND, HE_NONE);
  VerifySourceContents(kHttpEmptyResponse);
}

TEST_F(HttpBaseTest, SignalsCompleteOnInterruptedSend) {
  // This test is attempting to expose a bug that occurs when a particular
  // base objects is used for receiving, and then used for sending.  In
  // particular, the HttpParser state is different after receiving.  Simulate
  // that here.
  SetupSource(kHttpResponse);
  base.recv(&data);
  VerifyTransferComplete(HM_RECV, HE_NONE);

  src.Clear();
  data.clear(true);
  events.clear();
  base.detach();

  // Queue response document
  SetupDocument("Goodbye!");

  // Prevent entire response from being sent
  const size_t kInterruptedLength = strlen(kHttpResponse) - 1;
  src.SetWriteBlock(kInterruptedLength);

  // Begin send
  base.send(&data);

  // Document is mostly complete, but no completion signal yet.
  EXPECT_TRUE(events.empty());
  VerifySourceContents(kHttpResponse, kInterruptedLength);

  src.SetState(SS_CLOSED);

  // Send completed with disconnect error, and no additional data.
  VerifyTransferComplete(HM_SEND, HE_DISCONNECTED);
  EXPECT_TRUE(src.ReadData().empty());
}

TEST_F(HttpBaseTest, SupportsReceiveViaDocumentPush) {
  // Queue response document
  SetupSource(kHttpResponse);

  // Begin receive
  base.recv(&data);

  // Document completed successfully
  VerifyHeaderComplete(2, false);
  VerifyTransferComplete(HM_RECV, HE_NONE);
  VerifyDocumentContents("Goodbye!");
}

TEST_F(HttpBaseTest, SupportsReceiveViaStreamPull) {
  // Switch to pull mode
  ObtainDocumentStream();
  VerifyDocumentStreamIsOpening();

  // Queue response document
  SetupSource(kHttpResponse);
  VerifyDocumentStreamIsOpening();

  // Begin receive
  base.recv(&data);

  // Pull document data
  VerifyDocumentStreamOpenEvent();
  ReadDocumentStreamData("Goodbye!");
  VerifyDocumentStreamIsEOS();

  // Document completed successfully
  VerifyHeaderComplete(2, false);
  VerifyTransferComplete(HM_RECV, HE_NONE);
  VerifyDocumentContents("");
}

TEST_F(HttpBaseTest, DISABLED_AllowsCloseStreamBeforeDocumentIsComplete) {

  // TODO: Remove extra logging once test failure is understood
  LoggingSeverity old_sev = rtc::LogMessage::GetLogToDebug();
  rtc::LogMessage::LogToDebug(LS_VERBOSE);


  // Switch to pull mode
  ObtainDocumentStream();
  VerifyDocumentStreamIsOpening();

  // Queue response document
  SetupSource(kHttpResponse);
  VerifyDocumentStreamIsOpening();

  // Begin receive
  base.recv(&data);

  // Pull some of the data
  VerifyDocumentStreamOpenEvent();
  ReadDocumentStreamData("Goodb");

  // We've seen the header by now
  VerifyHeaderComplete(1, false);

  // Close the pull stream, this will transition back to push I/O.
  http_stream->Close();
  Thread::Current()->ProcessMessages(0);

  // Remainder of document completed successfully
  VerifyTransferComplete(HM_RECV, HE_NONE);
  VerifyDocumentContents("ye!");

  rtc::LogMessage::LogToDebug(old_sev);
}

TEST_F(HttpBaseTest, AllowsGetDocumentStreamInResponseToHttpHeader) {
  // Queue response document
  SetupSource(kHttpResponse);

  // Switch to pull mode in response to header arrival
  obtain_stream = true;

  // Begin receive
  base.recv(&data);

  // We've already seen the header, but not data has arrived
  VerifyHeaderComplete(1, false);
  VerifyDocumentContents("");

  // Pull the document data
  ReadDocumentStreamData("Goodbye!");
  VerifyDocumentStreamIsEOS();

  // Document completed successfully
  VerifyTransferComplete(HM_RECV, HE_NONE);
  VerifyDocumentContents("");
}

TEST_F(HttpBaseTest, AllowsGetDocumentStreamWithEmptyDocumentBody) {
  // Queue empty response document
  SetupSource(kHttpEmptyResponse);

  // Switch to pull mode in response to header arrival
  obtain_stream = true;

  // Begin receive
  base.recv(&data);

  // We've already seen the header, but not data has arrived
  VerifyHeaderComplete(1, true);
  VerifyDocumentContents("");

  // The document is still open, until we attempt to read
  ASSERT_TRUE(NULL != http_stream);
  EXPECT_EQ(SS_OPEN, http_stream->GetState());

  // Attempt to read data, and discover EOS
  VerifyDocumentStreamIsEOS();

  // Document completed successfully
  VerifyTransferComplete(HM_RECV, HE_NONE);
  VerifyDocumentContents("");
}

TEST_F(HttpBaseTest, SignalsDocumentStreamCloseOnUnexpectedClose) {
  // Switch to pull mode
  ObtainDocumentStream();
  VerifyDocumentStreamIsOpening();

  // Queue response document
  SetupSource(kHttpResponsePrefix);
  VerifyDocumentStreamIsOpening();

  // Begin receive
  base.recv(&data);

  // Pull document data
  VerifyDocumentStreamOpenEvent();
  ReadDocumentStreamData("Goodbye!");

  // Simulate unexpected close
  src.SetState(SS_CLOSED);

  // Observe error event on document stream
  EXPECT_EQ(testing::SSE_ERROR, sink.Events(http_stream));

  // Future reads give an error
  int error = 0;
  char buffer[5] = { 0 };
  EXPECT_EQ(SR_ERROR, http_stream->Read(buffer, sizeof(buffer), NULL, &error));
  EXPECT_EQ(HE_DISCONNECTED, error);

  // Document completed with error
  VerifyHeaderComplete(2, false);
  VerifyTransferComplete(HM_RECV, HE_DISCONNECTED);
  VerifyDocumentContents("");
}

} // namespace rtc
