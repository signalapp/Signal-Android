/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_STRINGENCODE_H_
#define WEBRTC_BASE_STRINGENCODE_H_

#include <sstream>
#include <string>
#include <vector>

#include "webrtc/base/checks.h"

namespace rtc {

//////////////////////////////////////////////////////////////////////
// String Encoding Utilities
//////////////////////////////////////////////////////////////////////

// Convert an unsigned value to it's utf8 representation.  Returns the length
// of the encoded string, or 0 if the encoding is longer than buflen - 1.
size_t utf8_encode(char* buffer, size_t buflen, unsigned long value);
// Decode the utf8 encoded value pointed to by source.  Returns the number of
// bytes used by the encoding, or 0 if the encoding is invalid.
size_t utf8_decode(const char* source, size_t srclen, unsigned long* value);

// Escaping prefixes illegal characters with the escape character.  Compact, but
// illegal characters still appear in the string.
size_t escape(char * buffer, size_t buflen,
              const char * source, size_t srclen,
              const char * illegal, char escape);
// Note: in-place unescaping (buffer == source) is allowed.
size_t unescape(char * buffer, size_t buflen,
                const char * source, size_t srclen,
                char escape);

// Encoding replaces illegal characters with the escape character and 2 hex
// chars, so it's a little less compact than escape, but completely removes
// illegal characters.  note that hex digits should not be used as illegal
// characters.
size_t encode(char * buffer, size_t buflen,
              const char * source, size_t srclen,
              const char * illegal, char escape);
// Note: in-place decoding (buffer == source) is allowed.
size_t decode(char * buffer, size_t buflen,
              const char * source, size_t srclen,
              char escape);

// Returns a list of characters that may be unsafe for use in the name of a
// file, suitable for passing to the 'illegal' member of escape or encode.
const char* unsafe_filename_characters();

// url_encode is an encode operation with a predefined set of illegal characters
// and escape character (for use in URLs, obviously).
size_t url_encode(char * buffer, size_t buflen,
                  const char * source, size_t srclen);
// Note: in-place decoding (buffer == source) is allowed.
size_t url_decode(char * buffer, size_t buflen,
                  const char * source, size_t srclen);

// html_encode prevents data embedded in html from containing markup.
size_t html_encode(char * buffer, size_t buflen,
                   const char * source, size_t srclen);
// Note: in-place decoding (buffer == source) is allowed.
size_t html_decode(char * buffer, size_t buflen,
                   const char * source, size_t srclen);

// xml_encode makes data suitable for inside xml attributes and values.
size_t xml_encode(char * buffer, size_t buflen,
                  const char * source, size_t srclen);
// Note: in-place decoding (buffer == source) is allowed.
size_t xml_decode(char * buffer, size_t buflen,
                  const char * source, size_t srclen);

// Convert an unsigned value from 0 to 15 to the hex character equivalent...
char hex_encode(unsigned char val);
// ...and vice-versa.
bool hex_decode(char ch, unsigned char* val);

// hex_encode shows the hex representation of binary data in ascii.
size_t hex_encode(char* buffer, size_t buflen,
                  const char* source, size_t srclen);

// hex_encode, but separate each byte representation with a delimiter.
// |delimiter| == 0 means no delimiter
// If the buffer is too short, we return 0
size_t hex_encode_with_delimiter(char* buffer, size_t buflen,
                                 const char* source, size_t srclen,
                                 char delimiter);

// Helper functions for hex_encode.
std::string hex_encode(const std::string& str);
std::string hex_encode(const char* source, size_t srclen);
std::string hex_encode_with_delimiter(const char* source, size_t srclen,
                                      char delimiter);

// hex_decode converts ascii hex to binary.
size_t hex_decode(char* buffer, size_t buflen,
                  const char* source, size_t srclen);

// hex_decode, assuming that there is a delimiter between every byte
// pair.
// |delimiter| == 0 means no delimiter
// If the buffer is too short or the data is invalid, we return 0.
size_t hex_decode_with_delimiter(char* buffer, size_t buflen,
                                 const char* source, size_t srclen,
                                 char delimiter);

// Helper functions for hex_decode.
size_t hex_decode(char* buffer, size_t buflen, const std::string& source);
size_t hex_decode_with_delimiter(char* buffer, size_t buflen,
                                 const std::string& source, char delimiter);

// Apply any suitable string transform (including the ones above) to an STL
// string.  Stack-allocated temporary space is used for the transformation,
// so value and source may refer to the same string.
typedef size_t (*Transform)(char * buffer, size_t buflen,
                            const char * source, size_t srclen);
size_t transform(std::string& value, size_t maxlen, const std::string& source,
                 Transform t);

// Return the result of applying transform t to source.
std::string s_transform(const std::string& source, Transform t);

// Convenience wrappers.
inline std::string s_url_encode(const std::string& source) {
  return s_transform(source, url_encode);
}
inline std::string s_url_decode(const std::string& source) {
  return s_transform(source, url_decode);
}

// Splits the source string into multiple fields separated by delimiter,
// with duplicates of delimiter creating empty fields.
size_t split(const std::string& source, char delimiter,
             std::vector<std::string>* fields);

// Splits the source string into multiple fields separated by delimiter,
// with duplicates of delimiter ignored.  Trailing delimiter ignored.
size_t tokenize(const std::string& source, char delimiter,
                std::vector<std::string>* fields);

// Tokenize, including the empty tokens.
size_t tokenize_with_empty_tokens(const std::string& source,
                                  char delimiter,
                                  std::vector<std::string>* fields);

// Tokenize and append the tokens to fields. Return the new size of fields.
size_t tokenize_append(const std::string& source, char delimiter,
                       std::vector<std::string>* fields);

// Splits the source string into multiple fields separated by delimiter, with
// duplicates of delimiter ignored. Trailing delimiter ignored. A substring in
// between the start_mark and the end_mark is treated as a single field. Return
// the size of fields. For example, if source is "filename
// \"/Library/Application Support/media content.txt\"", delimiter is ' ', and
// the start_mark and end_mark are '"', this method returns two fields:
// "filename" and "/Library/Application Support/media content.txt".
size_t tokenize(const std::string& source, char delimiter, char start_mark,
                char end_mark, std::vector<std::string>* fields);

// Extract the first token from source as separated by delimiter, with
// duplicates of delimiter ignored. Return false if the delimiter could not be
// found, otherwise return true.
bool tokenize_first(const std::string& source,
                    const char delimiter,
                    std::string* token,
                    std::string* rest);

// Safe sprintf to std::string
//void sprintf(std::string& value, size_t maxlen, const char * format, ...)
//     PRINTF_FORMAT(3);

// Convert arbitrary values to/from a string.

template <class T>
static bool ToString(const T &t, std::string* s) {
  RTC_DCHECK(s);
  std::ostringstream oss;
  oss << std::boolalpha << t;
  *s = oss.str();
  return !oss.fail();
}

template <class T>
static bool FromString(const std::string& s, T* t) {
  RTC_DCHECK(t);
  std::istringstream iss(s);
  iss >> std::boolalpha >> *t;
  return !iss.fail();
}

// Inline versions of the string conversion routines.

template<typename T>
static inline std::string ToString(const T& val) {
  std::string str; ToString(val, &str); return str;
}

template<typename T>
static inline T FromString(const std::string& str) {
  T val; FromString(str, &val); return val;
}

template<typename T>
static inline T FromString(const T& defaultValue, const std::string& str) {
  T val(defaultValue); FromString(str, &val); return val;
}

// simple function to strip out characters which shouldn't be
// used in filenames
char make_char_safe_for_filename(char c);

//////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_BASE_STRINGENCODE_H__
