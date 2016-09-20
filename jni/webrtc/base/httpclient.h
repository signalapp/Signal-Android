/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_HTTPCLIENT_H__
#define WEBRTC_BASE_HTTPCLIENT_H__

#include <memory>

#include "webrtc/base/common.h"
#include "webrtc/base/httpbase.h"
#include "webrtc/base/nethelpers.h"
#include "webrtc/base/proxyinfo.h"
#include "webrtc/base/sigslot.h"
#include "webrtc/base/socketaddress.h"
#include "webrtc/base/socketpool.h"

namespace rtc {

//////////////////////////////////////////////////////////////////////
// Client-specific http utilities
//////////////////////////////////////////////////////////////////////

// Write cache-relevant response headers to output stream.  If size is non-null,
// it contains the length of the output in bytes.  output may be null if only
// the length is desired.
bool HttpWriteCacheHeaders(const HttpResponseData* response,
                           StreamInterface* output, size_t* size);
// Read cached headers from a stream, and them merge them into the response
// object using the specified combine operation.
bool HttpReadCacheHeaders(StreamInterface* input,
                          HttpResponseData* response,
                          HttpData::HeaderCombine combine);

//////////////////////////////////////////////////////////////////////
// HttpClient
// Implements an HTTP 1.1 client.
//////////////////////////////////////////////////////////////////////

class DiskCache;
class HttpClient;
class IPNetPool;

class SignalThread;
// What to do:  Define STRICT_HTTP_ERROR=1 in your makefile.  Use HttpError in
// your code (HttpErrorType should only be used for code that is shared
// with groups which have not yet migrated).
#if STRICT_HTTP_ERROR
typedef HttpError HttpErrorType;
#else  // !STRICT_HTTP_ERROR
typedef int HttpErrorType;
#endif  // !STRICT_HTTP_ERROR

class HttpClient : private IHttpNotify, public sigslot::has_slots<> {
public:
  // If HttpRequestData and HttpResponseData objects are provided, they must
  // be freed by the caller.  Otherwise, an internal object is allocated.
  HttpClient(const std::string& agent, StreamPool* pool,
             HttpTransaction* transaction = NULL);
  ~HttpClient() override;

  void set_pool(StreamPool* pool) { pool_ = pool; }

  void set_agent(const std::string& agent) { agent_ = agent; }
  const std::string& agent() const { return agent_; }

  void set_proxy(const ProxyInfo& proxy) { proxy_ = proxy; }
  const ProxyInfo& proxy() const { return proxy_; }

  // Request retries occur when the connection closes before the beginning of
  // an http response is received.  In these cases, the http server may have
  // timed out the keepalive connection before it received our request.  Note
  // that if a request document cannot be rewound, no retry is made.  The
  // default is 1.
  void set_request_retries(size_t retries) { retries_ = retries; }
  size_t request_retries() const { return retries_; }

  enum RedirectAction { REDIRECT_DEFAULT, REDIRECT_ALWAYS, REDIRECT_NEVER };
  void set_redirect_action(RedirectAction action) { redirect_action_ = action; }
  RedirectAction redirect_action() const { return redirect_action_; }

  enum UriForm { URI_DEFAULT, URI_ABSOLUTE, URI_RELATIVE };
  void set_uri_form(UriForm form) { uri_form_ = form; }
  UriForm uri_form() const { return uri_form_; }

  void set_cache(DiskCache* cache) { ASSERT(!IsCacheActive()); cache_ = cache; }
  bool cache_enabled() const { return (NULL != cache_); }

  // reset clears the server, request, and response structures.  It will also
  // abort an active request.
  void reset();

  void set_server(const SocketAddress& address);
  const SocketAddress& server() const { return server_; }

  // Note: in order for HttpClient to retry a POST in response to
  // an authentication challenge, a redirect response, or socket disconnection,
  // the request document must support 'replaying' by calling Rewind() on it.
  HttpTransaction* transaction() { return transaction_; }
  const HttpTransaction* transaction() const { return transaction_; }
  HttpRequestData& request() { return transaction_->request; }
  const HttpRequestData& request() const { return transaction_->request; }
  HttpResponseData& response() { return transaction_->response; }
  const HttpResponseData& response() const { return transaction_->response; }

  // convenience methods
  void prepare_get(const std::string& url);
  void prepare_post(const std::string& url, const std::string& content_type,
                    StreamInterface* request_doc);

  // Convert HttpClient to a pull-based I/O model.
  StreamInterface* GetDocumentStream();

  // After you finish setting up your request, call start.
  void start();

  // Signalled when the header has finished downloading, before the document
  // content is processed.  You may change the response document in response
  // to this signal.  The second parameter indicates whether this is an
  // intermediate (false) or final (true) header.  An intermediate header is
  // one that generates another request, such as a redirect or authentication
  // challenge.  The third parameter indicates the length of the response
  // document, or else SIZE_UNKNOWN.  Note: Do NOT abort the request in response
  // to this signal.
  sigslot::signal3<HttpClient*,bool,size_t> SignalHeaderAvailable;
  // Signalled when the current request finishes.  On success, err is 0.
  sigslot::signal2<HttpClient*,HttpErrorType> SignalHttpClientComplete;

protected:
  void connect();
  void release();

  bool ShouldRedirect(std::string* location) const;

  bool BeginCacheFile();
  HttpError WriteCacheHeaders(const std::string& id);
  void CompleteCacheFile();

  bool CheckCache();
  HttpError ReadCacheHeaders(const std::string& id, bool override);
  HttpError ReadCacheBody(const std::string& id);

  bool PrepareValidate();
  HttpError CompleteValidate();

  HttpError OnHeaderAvailable(bool ignore_data, bool chunked, size_t data_size);

  void StartDNSLookup();
  void OnResolveResult(AsyncResolverInterface* resolver);

  // IHttpNotify Interface
  HttpError onHttpHeaderComplete(bool chunked, size_t& data_size) override;
  void onHttpComplete(HttpMode mode, HttpError err) override;
  void onHttpClosed(HttpError err) override;

private:
  enum CacheState { CS_READY, CS_WRITING, CS_READING, CS_VALIDATING };
  bool IsCacheActive() const { return (cache_state_ > CS_READY); }

  std::string agent_;
  StreamPool* pool_;
  HttpBase base_;
  SocketAddress server_;
  ProxyInfo proxy_;
  HttpTransaction* transaction_;
  bool free_transaction_;
  size_t retries_, attempt_, redirects_;
  RedirectAction redirect_action_;
  UriForm uri_form_;
  std::unique_ptr<HttpAuthContext> context_;
  DiskCache* cache_;
  CacheState cache_state_;
  AsyncResolverInterface* resolver_;
};

//////////////////////////////////////////////////////////////////////
// HttpClientDefault - Default implementation of HttpClient
//////////////////////////////////////////////////////////////////////

class HttpClientDefault : public ReuseSocketPool, public HttpClient {
public:
  HttpClientDefault(SocketFactory* factory, const std::string& agent,
                    HttpTransaction* transaction = NULL);
};

//////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif // WEBRTC_BASE_HTTPCLIENT_H__
