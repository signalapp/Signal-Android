/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <time.h>
#include <algorithm>
#include <memory>
#include "webrtc/base/asyncsocket.h"
#include "webrtc/base/common.h"
#include "webrtc/base/diskcache.h"
#include "webrtc/base/httpclient.h"
#include "webrtc/base/httpcommon-inl.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/socketstream.h"
#include "webrtc/base/stringencode.h"
#include "webrtc/base/stringutils.h"
#include "webrtc/base/thread.h"

namespace rtc {

//////////////////////////////////////////////////////////////////////
// Helpers
//////////////////////////////////////////////////////////////////////

namespace {

const size_t kCacheHeader = 0;
const size_t kCacheBody = 1;

// Convert decimal string to integer
bool HttpStringToUInt(const std::string& str, size_t* val) {
  ASSERT(NULL != val);
  char* eos = NULL;
  *val = strtoul(str.c_str(), &eos, 10);
  return (*eos == '\0');
}

bool HttpShouldCache(const HttpTransaction& t) {
  bool verb_allows_cache = (t.request.verb == HV_GET)
                           || (t.request.verb == HV_HEAD);
  bool is_range_response = t.response.hasHeader(HH_CONTENT_RANGE, NULL);
  bool has_expires = t.response.hasHeader(HH_EXPIRES, NULL);
  bool request_allows_cache =
    has_expires || (std::string::npos != t.request.path.find('?'));
  bool response_allows_cache =
    has_expires || HttpCodeIsCacheable(t.response.scode);

  bool may_cache = verb_allows_cache
                   && request_allows_cache
                   && response_allows_cache
                   && !is_range_response;

  std::string value;
  if (t.response.hasHeader(HH_CACHE_CONTROL, &value)) {
    HttpAttributeList directives;
    HttpParseAttributes(value.data(), value.size(), directives);
    // Response Directives Summary:
    // public - always cacheable
    // private - do not cache in a shared cache
    // no-cache - may cache, but must revalidate whether fresh or stale
    // no-store - sensitive information, do not cache or store in any way
    // max-age - supplants Expires for staleness
    // s-maxage - use as max-age for shared caches, ignore otherwise
    // must-revalidate - may cache, but must revalidate after stale
    // proxy-revalidate - shared cache must revalidate
    if (HttpHasAttribute(directives, "no-store", NULL)) {
      may_cache = false;
    } else if (HttpHasAttribute(directives, "public", NULL)) {
      may_cache = true;
    }
  }
  return may_cache;
}

enum HttpCacheState {
  HCS_FRESH,  // In cache, may use
  HCS_STALE,  // In cache, must revalidate
  HCS_NONE    // Not in cache
};

HttpCacheState HttpGetCacheState(const HttpTransaction& t) {
  // Temporaries
  std::string s_temp;
  time_t u_temp;

  // Current time
  time_t now = time(0);

  HttpAttributeList cache_control;
  if (t.response.hasHeader(HH_CACHE_CONTROL, &s_temp)) {
    HttpParseAttributes(s_temp.data(), s_temp.size(), cache_control);
  }

  // Compute age of cache document
  time_t date;
  if (!t.response.hasHeader(HH_DATE, &s_temp)
      || !HttpDateToSeconds(s_temp, &date))
    return HCS_NONE;

  // TODO: Timestamp when cache request sent and response received?
  time_t request_time = date;
  time_t response_time = date;

  time_t apparent_age = 0;
  if (response_time > date) {
    apparent_age = response_time - date;
  }

  time_t corrected_received_age = apparent_age;
  size_t i_temp;
  if (t.response.hasHeader(HH_AGE, &s_temp)
      && HttpStringToUInt(s_temp, (&i_temp))) {
    u_temp = static_cast<time_t>(i_temp);
    corrected_received_age = std::max(apparent_age, u_temp);
  }

  time_t response_delay = response_time - request_time;
  time_t corrected_initial_age = corrected_received_age + response_delay;
  time_t resident_time = now - response_time;
  time_t current_age = corrected_initial_age + resident_time;

  // Compute lifetime of document
  time_t lifetime;
  if (HttpHasAttribute(cache_control, "max-age", &s_temp)) {
    lifetime = atoi(s_temp.c_str());
  } else if (t.response.hasHeader(HH_EXPIRES, &s_temp)
             && HttpDateToSeconds(s_temp, &u_temp)) {
    lifetime = u_temp - date;
  } else if (t.response.hasHeader(HH_LAST_MODIFIED, &s_temp)
             && HttpDateToSeconds(s_temp, &u_temp)) {
    // TODO: Issue warning 113 if age > 24 hours
    lifetime = static_cast<size_t>(now - u_temp) / 10;
  } else {
    return HCS_STALE;
  }

  return (lifetime > current_age) ? HCS_FRESH : HCS_STALE;
}

enum HttpValidatorStrength {
  HVS_NONE,
  HVS_WEAK,
  HVS_STRONG
};

HttpValidatorStrength
HttpRequestValidatorLevel(const HttpRequestData& request) {
  if (HV_GET != request.verb)
    return HVS_STRONG;
  return request.hasHeader(HH_RANGE, NULL) ? HVS_STRONG : HVS_WEAK;
}

HttpValidatorStrength
HttpResponseValidatorLevel(const HttpResponseData& response) {
  std::string value;
  if (response.hasHeader(HH_ETAG, &value)) {
    bool is_weak = (strnicmp(value.c_str(), "W/", 2) == 0);
    return is_weak ? HVS_WEAK : HVS_STRONG;
  }
  if (response.hasHeader(HH_LAST_MODIFIED, &value)) {
    time_t last_modified, date;
    if (HttpDateToSeconds(value, &last_modified)
        && response.hasHeader(HH_DATE, &value)
        && HttpDateToSeconds(value, &date)
        && (last_modified + 60 < date)) {
      return HVS_STRONG;
    }
    return HVS_WEAK;
  }
  return HVS_NONE;
}

std::string GetCacheID(const HttpRequestData& request) {
  std::string id, url;
  id.append(ToString(request.verb));
  id.append("_");
  request.getAbsoluteUri(&url);
  id.append(url);
  return id;
}

}  // anonymous namespace

//////////////////////////////////////////////////////////////////////
// Public Helpers
//////////////////////////////////////////////////////////////////////

bool HttpWriteCacheHeaders(const HttpResponseData* response,
                           StreamInterface* output, size_t* size) {
  size_t length = 0;
  // Write all unknown and end-to-end headers to a cache file
  for (HttpData::const_iterator it = response->begin();
       it != response->end(); ++it) {
    HttpHeader header;
    if (FromString(header, it->first) && !HttpHeaderIsEndToEnd(header))
      continue;
    length += it->first.length() + 2 + it->second.length() + 2;
    if (!output)
      continue;
    std::string formatted_header(it->first);
    formatted_header.append(": ");
    formatted_header.append(it->second);
    formatted_header.append("\r\n");
    StreamResult result = output->WriteAll(formatted_header.data(),
                                           formatted_header.length(),
                                           NULL, NULL);
    if (SR_SUCCESS != result) {
      return false;
    }
  }
  if (output && (SR_SUCCESS != output->WriteAll("\r\n", 2, NULL, NULL))) {
    return false;
  }
  length += 2;
  if (size)
    *size = length;
  return true;
}

bool HttpReadCacheHeaders(StreamInterface* input, HttpResponseData* response,
                          HttpData::HeaderCombine combine) {
  while (true) {
    std::string formatted_header;
    StreamResult result = input->ReadLine(&formatted_header);
    if ((SR_EOS == result) || (1 == formatted_header.size())) {
      break;
    }
    if (SR_SUCCESS != result) {
      return false;
    }
    size_t end_of_name = formatted_header.find(':');
    if (std::string::npos == end_of_name) {
      LOG_F(LS_WARNING) << "Malformed cache header";
      continue;
    }
    size_t start_of_value = end_of_name + 1;
    size_t end_of_value = formatted_header.length();
    while ((start_of_value < end_of_value)
           && isspace(formatted_header[start_of_value]))
      ++start_of_value;
    while ((start_of_value < end_of_value)
           && isspace(formatted_header[end_of_value-1]))
     --end_of_value;
    size_t value_length = end_of_value - start_of_value;

    std::string name(formatted_header.substr(0, end_of_name));
    std::string value(formatted_header.substr(start_of_value, value_length));
    response->changeHeader(name, value, combine);
  }
  return true;
}

//////////////////////////////////////////////////////////////////////
// HttpClient
//////////////////////////////////////////////////////////////////////

const size_t kDefaultRetries = 1;
const size_t kMaxRedirects = 5;

HttpClient::HttpClient(const std::string& agent, StreamPool* pool,
                       HttpTransaction* transaction)
    : agent_(agent), pool_(pool),
      transaction_(transaction), free_transaction_(false),
      retries_(kDefaultRetries), attempt_(0), redirects_(0),
      redirect_action_(REDIRECT_DEFAULT),
      uri_form_(URI_DEFAULT), cache_(NULL), cache_state_(CS_READY),
      resolver_(NULL) {
  base_.notify(this);
  if (NULL == transaction_) {
    free_transaction_ = true;
    transaction_ = new HttpTransaction;
  }
}

HttpClient::~HttpClient() {
  base_.notify(NULL);
  base_.abort(HE_SHUTDOWN);
  if (resolver_) {
    resolver_->Destroy(false);
  }
  release();
  if (free_transaction_)
    delete transaction_;
}

void HttpClient::reset() {
  server_.Clear();
  request().clear(true);
  response().clear(true);
  context_.reset();
  redirects_ = 0;
  base_.abort(HE_OPERATION_CANCELLED);
}

void HttpClient::OnResolveResult(AsyncResolverInterface* resolver) {
  if (resolver != resolver_) {
    return;
  }
  int error = resolver_->GetError();
  server_ = resolver_->address();
  resolver_->Destroy(false);
  resolver_ = NULL;
  if (error != 0) {
    LOG(LS_ERROR) << "Error " << error << " resolving name: "
                  << server_;
    onHttpComplete(HM_CONNECT, HE_CONNECT_FAILED);
  } else {
    connect();
  }
}

void HttpClient::StartDNSLookup() {
  resolver_ = new AsyncResolver();
  resolver_->SignalDone.connect(this, &HttpClient::OnResolveResult);
  resolver_->Start(server_);
}

void HttpClient::set_server(const SocketAddress& address) {
  server_ = address;
  // Setting 'Host' here allows it to be overridden before starting the request,
  // if necessary.
  request().setHeader(HH_HOST, HttpAddress(server_, false), true);
}

StreamInterface* HttpClient::GetDocumentStream() {
  return base_.GetDocumentStream();
}

void HttpClient::start() {
  if (base_.mode() != HM_NONE) {
    // call reset() to abort an in-progress request
    ASSERT(false);
    return;
  }

  ASSERT(!IsCacheActive());

  if (request().hasHeader(HH_TRANSFER_ENCODING, NULL)) {
    // Exact size must be known on the client.  Instead of using chunked
    // encoding, wrap data with auto-caching file or memory stream.
    ASSERT(false);
    return;
  }

  attempt_ = 0;

  // If no content has been specified, using length of 0.
  request().setHeader(HH_CONTENT_LENGTH, "0", false);

  if (!agent_.empty()) {
    request().setHeader(HH_USER_AGENT, agent_, false);
  }

  UriForm uri_form = uri_form_;
  if (PROXY_HTTPS == proxy_.type) {
    // Proxies require absolute form
    uri_form = URI_ABSOLUTE;
    request().version = HVER_1_0;
    request().setHeader(HH_PROXY_CONNECTION, "Keep-Alive", false);
  } else {
    request().setHeader(HH_CONNECTION, "Keep-Alive", false);
  }

  if (URI_ABSOLUTE == uri_form) {
    // Convert to absolute uri form
    std::string url;
    if (request().getAbsoluteUri(&url)) {
      request().path = url;
    } else {
      LOG(LS_WARNING) << "Couldn't obtain absolute uri";
    }
  } else if (URI_RELATIVE == uri_form) {
    // Convert to relative uri form
    std::string host, path;
    if (request().getRelativeUri(&host, &path)) {
      request().setHeader(HH_HOST, host);
      request().path = path;
    } else {
      LOG(LS_WARNING) << "Couldn't obtain relative uri";
    }
  }

  if ((NULL != cache_) && CheckCache()) {
    return;
  }

  connect();
}

void HttpClient::connect() {
  int stream_err;
  if (server_.IsUnresolvedIP()) {
    StartDNSLookup();
    return;
  }
  StreamInterface* stream = pool_->RequestConnectedStream(server_, &stream_err);
  if (stream == NULL) {
    ASSERT(0 != stream_err);
    LOG(LS_ERROR) << "RequestConnectedStream error: " << stream_err;
    onHttpComplete(HM_CONNECT, HE_CONNECT_FAILED);
  } else {
    base_.attach(stream);
    if (stream->GetState() == SS_OPEN) {
      base_.send(&transaction_->request);
    }
  }
}

void HttpClient::prepare_get(const std::string& url) {
  reset();
  Url<char> purl(url);
  set_server(SocketAddress(purl.host(), purl.port()));
  request().verb = HV_GET;
  request().path = purl.full_path();
}

void HttpClient::prepare_post(const std::string& url,
                              const std::string& content_type,
                              StreamInterface* request_doc) {
  reset();
  Url<char> purl(url);
  set_server(SocketAddress(purl.host(), purl.port()));
  request().verb = HV_POST;
  request().path = purl.full_path();
  request().setContent(content_type, request_doc);
}

void HttpClient::release() {
  if (StreamInterface* stream = base_.detach()) {
    pool_->ReturnConnectedStream(stream);
  }
}

bool HttpClient::ShouldRedirect(std::string* location) const {
  // TODO: Unittest redirection.
  if ((REDIRECT_NEVER == redirect_action_)
      || !HttpCodeIsRedirection(response().scode)
      || !response().hasHeader(HH_LOCATION, location)
      || (redirects_ >= kMaxRedirects))
    return false;
  return (REDIRECT_ALWAYS == redirect_action_)
         || (HC_SEE_OTHER == response().scode)
         || (HV_HEAD == request().verb)
         || (HV_GET == request().verb);
}

bool HttpClient::BeginCacheFile() {
  ASSERT(NULL != cache_);
  ASSERT(CS_READY == cache_state_);

  std::string id = GetCacheID(request());
  CacheLock lock(cache_, id, true);
  if (!lock.IsLocked()) {
    LOG_F(LS_WARNING) << "Couldn't lock cache";
    return false;
  }

  if (HE_NONE != WriteCacheHeaders(id)) {
    return false;
  }

  std::unique_ptr<StreamInterface> stream(
      cache_->WriteResource(id, kCacheBody));
  if (!stream) {
    LOG_F(LS_ERROR) << "Couldn't open body cache";
    return false;
  }
  lock.Commit();

  // Let's secretly replace the response document with Folgers Crystals,
  // er, StreamTap, so that we can mirror the data to our cache.
  StreamInterface* output = response().document.release();
  if (!output) {
    output = new NullStream;
  }
  StreamTap* tap = new StreamTap(output, stream.release());
  response().document.reset(tap);
  return true;
}

HttpError HttpClient::WriteCacheHeaders(const std::string& id) {
  std::unique_ptr<StreamInterface> stream(
      cache_->WriteResource(id, kCacheHeader));
  if (!stream) {
    LOG_F(LS_ERROR) << "Couldn't open header cache";
    return HE_CACHE;
  }

  if (!HttpWriteCacheHeaders(&transaction_->response, stream.get(), NULL)) {
    LOG_F(LS_ERROR) << "Couldn't write header cache";
    return HE_CACHE;
  }

  return HE_NONE;
}

void HttpClient::CompleteCacheFile() {
  // Restore previous response document
  StreamTap* tap = static_cast<StreamTap*>(response().document.release());
  response().document.reset(tap->Detach());

  int error;
  StreamResult result = tap->GetTapResult(&error);

  // Delete the tap and cache stream (which completes cache unlock)
  delete tap;

  if (SR_SUCCESS != result) {
    LOG(LS_ERROR) << "Cache file error: " << error;
    cache_->DeleteResource(GetCacheID(request()));
  }
}

bool HttpClient::CheckCache() {
  ASSERT(NULL != cache_);
  ASSERT(CS_READY == cache_state_);

  std::string id = GetCacheID(request());
  if (!cache_->HasResource(id)) {
    // No cache file available
    return false;
  }

  HttpError error = ReadCacheHeaders(id, true);

  if (HE_NONE == error) {
    switch (HttpGetCacheState(*transaction_)) {
    case HCS_FRESH:
      // Cache content is good, read from cache
      break;
    case HCS_STALE:
      // Cache content may be acceptable.  Issue a validation request.
      if (PrepareValidate()) {
        return false;
      }
      // Couldn't validate, fall through.
      FALLTHROUGH();
    case HCS_NONE:
      // Cache content is not useable.  Issue a regular request.
      response().clear(false);
      return false;
    }
  }

  if (HE_NONE == error) {
    error = ReadCacheBody(id);
    cache_state_ = CS_READY;
  }

  if (HE_CACHE == error) {
    LOG_F(LS_WARNING) << "Cache failure, continuing with normal request";
    response().clear(false);
    return false;
  }

  SignalHttpClientComplete(this, error);
  return true;
}

HttpError HttpClient::ReadCacheHeaders(const std::string& id, bool override) {
  std::unique_ptr<StreamInterface> stream(
      cache_->ReadResource(id, kCacheHeader));
  if (!stream) {
    return HE_CACHE;
  }

  HttpData::HeaderCombine combine =
    override ? HttpData::HC_REPLACE : HttpData::HC_AUTO;

  if (!HttpReadCacheHeaders(stream.get(), &transaction_->response, combine)) {
    LOG_F(LS_ERROR) << "Error reading cache headers";
    return HE_CACHE;
  }

  response().scode = HC_OK;
  return HE_NONE;
}

HttpError HttpClient::ReadCacheBody(const std::string& id) {
  cache_state_ = CS_READING;

  HttpError error = HE_NONE;

  size_t data_size;
  std::unique_ptr<StreamInterface> stream(cache_->ReadResource(id, kCacheBody));
  if (!stream || !stream->GetAvailable(&data_size)) {
    LOG_F(LS_ERROR) << "Unavailable cache body";
    error = HE_CACHE;
  } else {
    error = OnHeaderAvailable(false, false, data_size);
  }

  if ((HE_NONE == error)
      && (HV_HEAD != request().verb)
      && response().document) {
    // Allocate on heap to not explode the stack.
    const int array_size = 1024 * 64;
    std::unique_ptr<char[]> buffer(new char[array_size]);
    StreamResult result = Flow(stream.get(), buffer.get(), array_size,
                               response().document.get());
    if (SR_SUCCESS != result) {
      error = HE_STREAM;
    }
  }

  return error;
}

bool HttpClient::PrepareValidate() {
  ASSERT(CS_READY == cache_state_);
  // At this point, request() contains the pending request, and response()
  // contains the cached response headers.  Reformat the request to validate
  // the cached content.
  HttpValidatorStrength vs_required = HttpRequestValidatorLevel(request());
  HttpValidatorStrength vs_available = HttpResponseValidatorLevel(response());
  if (vs_available < vs_required) {
    return false;
  }
  std::string value;
  if (response().hasHeader(HH_ETAG, &value)) {
    request().addHeader(HH_IF_NONE_MATCH, value);
  }
  if (response().hasHeader(HH_LAST_MODIFIED, &value)) {
    request().addHeader(HH_IF_MODIFIED_SINCE, value);
  }
  response().clear(false);
  cache_state_ = CS_VALIDATING;
  return true;
}

HttpError HttpClient::CompleteValidate() {
  ASSERT(CS_VALIDATING == cache_state_);

  std::string id = GetCacheID(request());

  // Merge cached headers with new headers
  HttpError error = ReadCacheHeaders(id, false);
  if (HE_NONE != error) {
    // Rewrite merged headers to cache
    CacheLock lock(cache_, id);
    error = WriteCacheHeaders(id);
  }
  if (HE_NONE != error) {
    error = ReadCacheBody(id);
  }
  return error;
}

HttpError HttpClient::OnHeaderAvailable(bool ignore_data, bool chunked,
                                        size_t data_size) {
  // If we are ignoring the data, this is an intermediate header.
  // TODO: don't signal intermediate headers.  Instead, do all header-dependent
  // processing now, and either set up the next request, or fail outright.
  // TODO: by default, only write response documents with a success code.
  SignalHeaderAvailable(this, !ignore_data, ignore_data ? 0 : data_size);
  if (!ignore_data && !chunked && (data_size != SIZE_UNKNOWN)
      && response().document) {
    // Attempt to pre-allocate space for the downloaded data.
    if (!response().document->ReserveSize(data_size)) {
      return HE_OVERFLOW;
    }
  }
  return HE_NONE;
}

//
// HttpBase Implementation
//

HttpError HttpClient::onHttpHeaderComplete(bool chunked, size_t& data_size) {
  if (CS_VALIDATING == cache_state_) {
    if (HC_NOT_MODIFIED == response().scode) {
      return CompleteValidate();
    }
    // Should we remove conditional headers from request?
    cache_state_ = CS_READY;
    cache_->DeleteResource(GetCacheID(request()));
    // Continue processing response as normal
  }

  ASSERT(!IsCacheActive());
  if ((request().verb == HV_HEAD) || !HttpCodeHasBody(response().scode)) {
    // HEAD requests and certain response codes contain no body
    data_size = 0;
  }
  if (ShouldRedirect(NULL)
      || ((HC_PROXY_AUTHENTICATION_REQUIRED == response().scode)
          && (PROXY_HTTPS == proxy_.type))) {
    // We're going to issue another request, so ignore the incoming data.
    base_.set_ignore_data(true);
  }

  HttpError error = OnHeaderAvailable(base_.ignore_data(), chunked, data_size);
  if (HE_NONE != error) {
    return error;
  }

  if ((NULL != cache_)
      && !base_.ignore_data()
      && HttpShouldCache(*transaction_)) {
    if (BeginCacheFile()) {
      cache_state_ = CS_WRITING;
    }
  }
  return HE_NONE;
}

void HttpClient::onHttpComplete(HttpMode mode, HttpError err) {
  if (((HE_DISCONNECTED == err) || (HE_CONNECT_FAILED == err)
       || (HE_SOCKET_ERROR == err))
      && (HC_INTERNAL_SERVER_ERROR == response().scode)
      && (attempt_ < retries_)) {
    // If the response code has not changed from the default, then we haven't
    // received anything meaningful from the server, so we are eligible for a
    // retry.
    ++attempt_;
    if (request().document && !request().document->Rewind()) {
      // Unable to replay the request document.
      err = HE_STREAM;
    } else {
      release();
      connect();
      return;
    }
  } else if (err != HE_NONE) {
    // fall through
  } else if (mode == HM_CONNECT) {
    base_.send(&transaction_->request);
    return;
  } else if ((mode == HM_SEND) || HttpCodeIsInformational(response().scode)) {
    // If you're interested in informational headers, catch
    // SignalHeaderAvailable.
    base_.recv(&transaction_->response);
    return;
  } else {
    if (!HttpShouldKeepAlive(response())) {
      LOG(LS_VERBOSE) << "HttpClient: closing socket";
      base_.stream()->Close();
    }
    std::string location;
    if (ShouldRedirect(&location)) {
      Url<char> purl(location);
      set_server(SocketAddress(purl.host(), purl.port()));
      request().path = purl.full_path();
      if (response().scode == HC_SEE_OTHER) {
        request().verb = HV_GET;
        request().clearHeader(HH_CONTENT_TYPE);
        request().clearHeader(HH_CONTENT_LENGTH);
        request().document.reset();
      } else if (request().document && !request().document->Rewind()) {
        // Unable to replay the request document.
        ASSERT(REDIRECT_ALWAYS == redirect_action_);
        err = HE_STREAM;
      }
      if (err == HE_NONE) {
        ++redirects_;
        context_.reset();
        response().clear(false);
        release();
        start();
        return;
      }
    } else if ((HC_PROXY_AUTHENTICATION_REQUIRED == response().scode)
               && (PROXY_HTTPS == proxy_.type)) {
      std::string authorization, auth_method;
      HttpData::const_iterator begin = response().begin(HH_PROXY_AUTHENTICATE);
      HttpData::const_iterator end = response().end(HH_PROXY_AUTHENTICATE);
      for (HttpData::const_iterator it = begin; it != end; ++it) {
        HttpAuthContext *context = context_.get();
        HttpAuthResult res = HttpAuthenticate(
          it->second.data(), it->second.size(),
          proxy_.address,
          ToString(request().verb), request().path,
          proxy_.username, proxy_.password,
          context, authorization, auth_method);
        context_.reset(context);
        if (res == HAR_RESPONSE) {
          request().setHeader(HH_PROXY_AUTHORIZATION, authorization);
          if (request().document && !request().document->Rewind()) {
            err = HE_STREAM;
          } else {
            // Explicitly do not reset the HttpAuthContext
            response().clear(false);
            // TODO: Reuse socket when authenticating?
            release();
            start();
            return;
          }
        } else if (res == HAR_IGNORE) {
          LOG(INFO) << "Ignoring Proxy-Authenticate: " << auth_method;
          continue;
        } else {
          break;
        }
      }
    }
  }
  if (CS_WRITING == cache_state_) {
    CompleteCacheFile();
    cache_state_ = CS_READY;
  } else if (CS_READING == cache_state_) {
    cache_state_ = CS_READY;
  }
  release();
  SignalHttpClientComplete(this, err);
}

void HttpClient::onHttpClosed(HttpError err) {
  // This shouldn't occur, since we return the stream to the pool upon command
  // completion.
  ASSERT(false);
}

//////////////////////////////////////////////////////////////////////
// HttpClientDefault
//////////////////////////////////////////////////////////////////////

HttpClientDefault::HttpClientDefault(SocketFactory* factory,
                                     const std::string& agent,
                                     HttpTransaction* transaction)
    : ReuseSocketPool(factory ? factory : Thread::Current()->socketserver()),
      HttpClient(agent, NULL, transaction) {
  set_pool(this);
}

//////////////////////////////////////////////////////////////////////

}  // namespace rtc
