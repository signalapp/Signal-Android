/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/stringencode.h"

#include <stdio.h>
#include <stdlib.h>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/checks.h"
#include "webrtc/base/stringutils.h"

namespace rtc {

/////////////////////////////////////////////////////////////////////////////
// String Encoding Utilities
/////////////////////////////////////////////////////////////////////////////

size_t escape(char * buffer, size_t buflen,
              const char * source, size_t srclen,
              const char * illegal, char escape) {
  RTC_DCHECK(buffer);  // TODO(grunell): estimate output size
  if (buflen <= 0)
    return 0;

  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    char ch = source[srcpos++];
    if ((ch == escape) || ::strchr(illegal, ch)) {
      if (bufpos + 2 >= buflen)
        break;
      buffer[bufpos++] = escape;
    }
    buffer[bufpos++] = ch;
  }

  buffer[bufpos] = '\0';
  return bufpos;
}

size_t unescape(char * buffer, size_t buflen,
                const char * source, size_t srclen,
                char escape) {
  RTC_DCHECK(buffer);  // TODO(grunell): estimate output size
  if (buflen <= 0)
    return 0;

  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    char ch = source[srcpos++];
    if ((ch == escape) && (srcpos < srclen)) {
      ch = source[srcpos++];
    }
    buffer[bufpos++] = ch;
  }
  buffer[bufpos] = '\0';
  return bufpos;
}

size_t encode(char * buffer, size_t buflen,
              const char * source, size_t srclen,
              const char * illegal, char escape) {
  RTC_DCHECK(buffer);  // TODO(grunell): estimate output size
  if (buflen <= 0)
    return 0;

  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    char ch = source[srcpos++];
    if ((ch != escape) && !::strchr(illegal, ch)) {
      buffer[bufpos++] = ch;
    } else if (bufpos + 3 >= buflen) {
      break;
    } else {
      buffer[bufpos+0] = escape;
      buffer[bufpos+1] = hex_encode((static_cast<unsigned char>(ch) >> 4) & 0xF);
      buffer[bufpos+2] = hex_encode((static_cast<unsigned char>(ch)     ) & 0xF);
      bufpos += 3;
    }
  }
  buffer[bufpos] = '\0';
  return bufpos;
}

size_t decode(char * buffer, size_t buflen,
              const char * source, size_t srclen,
              char escape) {
  if (buflen <= 0)
    return 0;

  unsigned char h1, h2;
  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    char ch = source[srcpos++];
    if ((ch == escape)
        && (srcpos + 1 < srclen)
        && hex_decode(source[srcpos], &h1)
        && hex_decode(source[srcpos+1], &h2)) {
      buffer[bufpos++] = (h1 << 4) | h2;
      srcpos += 2;
    } else {
      buffer[bufpos++] = ch;
    }
  }
  buffer[bufpos] = '\0';
  return bufpos;
}

const char* unsafe_filename_characters() {
  // It might be better to have a single specification which is the union of
  // all operating systems, unless one system is overly restrictive.
#if defined(WEBRTC_WIN)
  return "\\/:*?\"<>|";
#else  // !WEBRTC_WIN
  // TODO(grunell): Should this never be reached?
  RTC_DCHECK(false);
  return "";
#endif  // !WEBRTC_WIN
}

const unsigned char URL_UNSAFE  = 0x1; // 0-33 "#$%&+,/:;<=>?@[\]^`{|} 127
const unsigned char XML_UNSAFE  = 0x2; // "&'<>
const unsigned char HTML_UNSAFE = 0x2; // "&'<>

//  ! " # $ % & ' ( ) * + , - . / 0 1 2 3 4 6 5 7 8 9 : ; < = > ?
//@ A B C D E F G H I J K L M N O P Q R S T U V W X Y Z [ \ ] ^ _
//` a b c d e f g h i j k l m n o p q r s t u v w x y z { | } ~

const unsigned char ASCII_CLASS[128] = {
  1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
  1,0,3,1,1,1,3,2,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,1,1,3,1,3,1,
  1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,
  1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,0,1,
};

size_t url_encode(char * buffer, size_t buflen,
                  const char * source, size_t srclen) {
  if (NULL == buffer)
    return srclen * 3 + 1;
  if (buflen <= 0)
    return 0;

  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    unsigned char ch = source[srcpos++];
    if ((ch < 128) && (ASCII_CLASS[ch] & URL_UNSAFE)) {
      if (bufpos + 3 >= buflen) {
        break;
      }
      buffer[bufpos+0] = '%';
      buffer[bufpos+1] = hex_encode((ch >> 4) & 0xF);
      buffer[bufpos+2] = hex_encode((ch     ) & 0xF);
      bufpos += 3;
    } else {
      buffer[bufpos++] = ch;
    }
  }
  buffer[bufpos] = '\0';
  return bufpos;
}

size_t url_decode(char * buffer, size_t buflen,
                  const char * source, size_t srclen) {
  if (NULL == buffer)
    return srclen + 1;
  if (buflen <= 0)
    return 0;

  unsigned char h1, h2;
  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    unsigned char ch = source[srcpos++];
    if (ch == '+') {
      buffer[bufpos++] = ' ';
    } else if ((ch == '%')
               && (srcpos + 1 < srclen)
               && hex_decode(source[srcpos], &h1)
               && hex_decode(source[srcpos+1], &h2))
    {
      buffer[bufpos++] = (h1 << 4) | h2;
      srcpos += 2;
    } else {
      buffer[bufpos++] = ch;
    }
  }
  buffer[bufpos] = '\0';
  return bufpos;
}

size_t utf8_decode(const char* source, size_t srclen, unsigned long* value) {
  const unsigned char* s = reinterpret_cast<const unsigned char*>(source);
  if ((s[0] & 0x80) == 0x00) {                    // Check s[0] == 0xxxxxxx
    *value = s[0];
    return 1;
  }
  if ((srclen < 2) || ((s[1] & 0xC0) != 0x80)) {  // Check s[1] != 10xxxxxx
    return 0;
  }
  // Accumulate the trailer byte values in value16, and combine it with the
  // relevant bits from s[0], once we've determined the sequence length.
  unsigned long value16 = (s[1] & 0x3F);
  if ((s[0] & 0xE0) == 0xC0) {                    // Check s[0] == 110xxxxx
    *value = ((s[0] & 0x1F) << 6) | value16;
    return 2;
  }
  if ((srclen < 3) || ((s[2] & 0xC0) != 0x80)) {  // Check s[2] != 10xxxxxx
    return 0;
  }
  value16 = (value16 << 6) | (s[2] & 0x3F);
  if ((s[0] & 0xF0) == 0xE0) {                    // Check s[0] == 1110xxxx
    *value = ((s[0] & 0x0F) << 12) | value16;
    return 3;
  }
  if ((srclen < 4) || ((s[3] & 0xC0) != 0x80)) {  // Check s[3] != 10xxxxxx
    return 0;
  }
  value16 = (value16 << 6) | (s[3] & 0x3F);
  if ((s[0] & 0xF8) == 0xF0) {                    // Check s[0] == 11110xxx
    *value = ((s[0] & 0x07) << 18) | value16;
    return 4;
  }
  return 0;
}

size_t utf8_encode(char* buffer, size_t buflen, unsigned long value) {
  if ((value <= 0x7F) && (buflen >= 1)) {
    buffer[0] = static_cast<unsigned char>(value);
    return 1;
  }
  if ((value <= 0x7FF) && (buflen >= 2)) {
    buffer[0] = 0xC0 | static_cast<unsigned char>(value >> 6);
    buffer[1] = 0x80 | static_cast<unsigned char>(value & 0x3F);
    return 2;
  }
  if ((value <= 0xFFFF) && (buflen >= 3)) {
    buffer[0] = 0xE0 | static_cast<unsigned char>(value >> 12);
    buffer[1] = 0x80 | static_cast<unsigned char>((value >> 6) & 0x3F);
    buffer[2] = 0x80 | static_cast<unsigned char>(value & 0x3F);
    return 3;
  }
  if ((value <= 0x1FFFFF) && (buflen >= 4)) {
    buffer[0] = 0xF0 | static_cast<unsigned char>(value >> 18);
    buffer[1] = 0x80 | static_cast<unsigned char>((value >> 12) & 0x3F);
    buffer[2] = 0x80 | static_cast<unsigned char>((value >> 6) & 0x3F);
    buffer[3] = 0x80 | static_cast<unsigned char>(value & 0x3F);
    return 4;
  }
  return 0;
}

size_t html_encode(char * buffer, size_t buflen,
                   const char * source, size_t srclen) {
  RTC_DCHECK(buffer);  // TODO(grunell): estimate output size
  if (buflen <= 0)
    return 0;

  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    unsigned char ch = source[srcpos];
    if (ch < 128) {
      srcpos += 1;
      if (ASCII_CLASS[ch] & HTML_UNSAFE) {
        const char * escseq = 0;
        size_t esclen = 0;
        switch (ch) {
          case '<':  escseq = "&lt;";   esclen = 4; break;
          case '>':  escseq = "&gt;";   esclen = 4; break;
          case '\'': escseq = "&#39;";  esclen = 5; break;
          case '\"': escseq = "&quot;"; esclen = 6; break;
          case '&':  escseq = "&amp;";  esclen = 5; break;
          default: RTC_DCHECK(false);
        }
        if (bufpos + esclen >= buflen) {
          break;
        }
        memcpy(buffer + bufpos, escseq, esclen);
        bufpos += esclen;
      } else {
        buffer[bufpos++] = ch;
      }
    } else {
      // Largest value is 0x1FFFFF => &#2097151;  (10 characters)
      const size_t kEscseqSize = 11;
      char escseq[kEscseqSize];
      unsigned long val;
      if (size_t vallen = utf8_decode(&source[srcpos], srclen - srcpos, &val)) {
        srcpos += vallen;
      } else {
        // Not a valid utf8 sequence, just use the raw character.
        val = static_cast<unsigned char>(source[srcpos++]);
      }
      size_t esclen = sprintfn(escseq, kEscseqSize, "&#%lu;", val);
      if (bufpos + esclen >= buflen) {
        break;
      }
      memcpy(buffer + bufpos, escseq, esclen);
      bufpos += esclen;
    }
  }
  buffer[bufpos] = '\0';
  return bufpos;
}

size_t html_decode(char * buffer, size_t buflen,
                   const char * source, size_t srclen) {
  RTC_DCHECK(buffer);  // TODO(grunell): estimate output size
  return xml_decode(buffer, buflen, source, srclen);
}

size_t xml_encode(char * buffer, size_t buflen,
                  const char * source, size_t srclen) {
  RTC_DCHECK(buffer);  // TODO(grunell): estimate output size
  if (buflen <= 0)
    return 0;

  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    unsigned char ch = source[srcpos++];
    if ((ch < 128) && (ASCII_CLASS[ch] & XML_UNSAFE)) {
      const char * escseq = 0;
      size_t esclen = 0;
      switch (ch) {
        case '<':  escseq = "&lt;";   esclen = 4; break;
        case '>':  escseq = "&gt;";   esclen = 4; break;
        case '\'': escseq = "&apos;"; esclen = 6; break;
        case '\"': escseq = "&quot;"; esclen = 6; break;
        case '&':  escseq = "&amp;";  esclen = 5; break;
        default: RTC_DCHECK(false);
      }
      if (bufpos + esclen >= buflen) {
        break;
      }
      memcpy(buffer + bufpos, escseq, esclen);
      bufpos += esclen;
    } else {
      buffer[bufpos++] = ch;
    }
  }
  buffer[bufpos] = '\0';
  return bufpos;
}

size_t xml_decode(char * buffer, size_t buflen,
                  const char * source, size_t srclen) {
  RTC_DCHECK(buffer);  // TODO(grunell): estimate output size
  if (buflen <= 0)
    return 0;

  size_t srcpos = 0, bufpos = 0;
  while ((srcpos < srclen) && (bufpos + 1 < buflen)) {
    unsigned char ch = source[srcpos++];
    if (ch != '&') {
      buffer[bufpos++] = ch;
    } else if ((srcpos + 2 < srclen)
               && (memcmp(source + srcpos, "lt;", 3) == 0)) {
      buffer[bufpos++] = '<';
      srcpos += 3;
    } else if ((srcpos + 2 < srclen)
               && (memcmp(source + srcpos, "gt;", 3) == 0)) {
      buffer[bufpos++] = '>';
      srcpos += 3;
    } else if ((srcpos + 4 < srclen)
               && (memcmp(source + srcpos, "apos;", 5) == 0)) {
      buffer[bufpos++] = '\'';
      srcpos += 5;
    } else if ((srcpos + 4 < srclen)
               && (memcmp(source + srcpos, "quot;", 5) == 0)) {
      buffer[bufpos++] = '\"';
      srcpos += 5;
    } else if ((srcpos + 3 < srclen)
               && (memcmp(source + srcpos, "amp;", 4) == 0)) {
      buffer[bufpos++] = '&';
      srcpos += 4;
    } else if ((srcpos < srclen) && (source[srcpos] == '#')) {
      int int_base = 10;
      if ((srcpos + 1 < srclen) && (source[srcpos+1] == 'x')) {
        int_base = 16;
        srcpos += 1;
      }
      char * ptr;
      // TODO(grunell): Fix hack (ptr may go past end of data)
      unsigned long val = strtoul(source + srcpos + 1, &ptr, int_base);
      if ((static_cast<size_t>(ptr - source) < srclen) && (*ptr == ';')) {
        srcpos = ptr - source + 1;
      } else {
        // Not a valid escape sequence.
        break;
      }
      if (size_t esclen = utf8_encode(buffer + bufpos, buflen - bufpos, val)) {
        bufpos += esclen;
      } else {
        // Not enough room to encode the character, or illegal character
        break;
      }
    } else {
      // Unrecognized escape sequence.
      break;
    }
  }
  buffer[bufpos] = '\0';
  return bufpos;
}

static const char HEX[] = "0123456789abcdef";

char hex_encode(unsigned char val) {
  RTC_DCHECK_LT(val, 16);
  return (val < 16) ? HEX[val] : '!';
}

bool hex_decode(char ch, unsigned char* val) {
  if ((ch >= '0') && (ch <= '9')) {
    *val = ch - '0';
  } else if ((ch >= 'A') && (ch <= 'Z')) {
    *val = (ch - 'A') + 10;
  } else if ((ch >= 'a') && (ch <= 'z')) {
    *val = (ch - 'a') + 10;
  } else {
    return false;
  }
  return true;
}

size_t hex_encode(char* buffer, size_t buflen,
                  const char* csource, size_t srclen) {
  return hex_encode_with_delimiter(buffer, buflen, csource, srclen, 0);
}

size_t hex_encode_with_delimiter(char* buffer, size_t buflen,
                                 const char* csource, size_t srclen,
                                 char delimiter) {
  RTC_DCHECK(buffer);  // TODO(grunell): estimate output size
  if (buflen == 0)
    return 0;

  // Init and check bounds.
  const unsigned char* bsource =
      reinterpret_cast<const unsigned char*>(csource);
  size_t srcpos = 0, bufpos = 0;
  size_t needed = delimiter ? (srclen * 3) : (srclen * 2 + 1);
  if (buflen < needed)
    return 0;

  while (srcpos < srclen) {
    unsigned char ch = bsource[srcpos++];
    buffer[bufpos  ] = hex_encode((ch >> 4) & 0xF);
    buffer[bufpos+1] = hex_encode((ch     ) & 0xF);
    bufpos += 2;

    // Don't write a delimiter after the last byte.
    if (delimiter && (srcpos < srclen)) {
      buffer[bufpos] = delimiter;
      ++bufpos;
    }
  }

  // Null terminate.
  buffer[bufpos] = '\0';
  return bufpos;
}

std::string hex_encode(const std::string& str) {
  return hex_encode(str.c_str(), str.size());
}

std::string hex_encode(const char* source, size_t srclen) {
  return hex_encode_with_delimiter(source, srclen, 0);
}

std::string hex_encode_with_delimiter(const char* source, size_t srclen,
                                      char delimiter) {
  const size_t kBufferSize = srclen * 3;
  char* buffer = STACK_ARRAY(char, kBufferSize);
  size_t length = hex_encode_with_delimiter(buffer, kBufferSize,
                                            source, srclen, delimiter);
  RTC_DCHECK(srclen == 0 || length > 0);
  return std::string(buffer, length);
}

size_t hex_decode(char * cbuffer, size_t buflen,
                  const char * source, size_t srclen) {
  return hex_decode_with_delimiter(cbuffer, buflen, source, srclen, 0);
}

size_t hex_decode_with_delimiter(char* cbuffer, size_t buflen,
                                 const char* source, size_t srclen,
                                 char delimiter) {
  RTC_DCHECK(cbuffer);  // TODO(grunell): estimate output size
  if (buflen == 0)
    return 0;

  // Init and bounds check.
  unsigned char* bbuffer = reinterpret_cast<unsigned char*>(cbuffer);
  size_t srcpos = 0, bufpos = 0;
  size_t needed = (delimiter) ? (srclen + 1) / 3 : srclen / 2;
  if (buflen < needed)
    return 0;

  while (srcpos < srclen) {
    if ((srclen - srcpos) < 2) {
      // This means we have an odd number of bytes.
      return 0;
    }

    unsigned char h1, h2;
    if (!hex_decode(source[srcpos], &h1) ||
        !hex_decode(source[srcpos + 1], &h2))
      return 0;

    bbuffer[bufpos++] = (h1 << 4) | h2;
    srcpos += 2;

    // Remove the delimiter if needed.
    if (delimiter && (srclen - srcpos) > 1) {
      if (source[srcpos] != delimiter)
        return 0;
      ++srcpos;
    }
  }

  return bufpos;
}

size_t hex_decode(char* buffer, size_t buflen, const std::string& source) {
  return hex_decode_with_delimiter(buffer, buflen, source, 0);
}
size_t hex_decode_with_delimiter(char* buffer, size_t buflen,
                                 const std::string& source, char delimiter) {
  return hex_decode_with_delimiter(buffer, buflen,
                                   source.c_str(), source.length(), delimiter);
}

size_t transform(std::string& value, size_t maxlen, const std::string& source,
                 Transform t) {
  char* buffer = STACK_ARRAY(char, maxlen + 1);
  size_t length = t(buffer, maxlen + 1, source.data(), source.length());
  value.assign(buffer, length);
  return length;
}

std::string s_transform(const std::string& source, Transform t) {
  // Ask transformation function to approximate the destination size (returns upper bound)
  size_t maxlen = t(NULL, 0, source.data(), source.length());
  char * buffer = STACK_ARRAY(char, maxlen);
  size_t len = t(buffer, maxlen, source.data(), source.length());
  std::string result(buffer, len);
  return result;
}

size_t tokenize(const std::string& source, char delimiter,
                std::vector<std::string>* fields) {
  fields->clear();
  size_t last = 0;
  for (size_t i = 0; i < source.length(); ++i) {
    if (source[i] == delimiter) {
      if (i != last) {
        fields->push_back(source.substr(last, i - last));
      }
      last = i + 1;
    }
  }
  if (last != source.length()) {
    fields->push_back(source.substr(last, source.length() - last));
  }
  return fields->size();
}

size_t tokenize_with_empty_tokens(const std::string& source,
                                  char delimiter,
                                  std::vector<std::string>* fields) {
  fields->clear();
  size_t last = 0;
  for (size_t i = 0; i < source.length(); ++i) {
    if (source[i] == delimiter) {
      fields->push_back(source.substr(last, i - last));
      last = i + 1;
    }
  }
  fields->push_back(source.substr(last, source.length() - last));
  return fields->size();
}

size_t tokenize_append(const std::string& source, char delimiter,
                       std::vector<std::string>* fields) {
  if (!fields) return 0;

  std::vector<std::string> new_fields;
  tokenize(source, delimiter, &new_fields);
  fields->insert(fields->end(), new_fields.begin(), new_fields.end());
  return fields->size();
}

size_t tokenize(const std::string& source, char delimiter, char start_mark,
                char end_mark, std::vector<std::string>* fields) {
  if (!fields) return 0;
  fields->clear();

  std::string remain_source = source;
  while (!remain_source.empty()) {
    size_t start_pos = remain_source.find(start_mark);
    if (std::string::npos == start_pos) break;
    std::string pre_mark;
    if (start_pos > 0) {
      pre_mark = remain_source.substr(0, start_pos - 1);
    }

    ++start_pos;
    size_t end_pos = remain_source.find(end_mark, start_pos);
    if (std::string::npos == end_pos) break;

    // We have found the matching marks. First tokenize the pre-mask. Then add
    // the marked part as a single field. Finally, loop back for the post-mark.
    tokenize_append(pre_mark, delimiter, fields);
    fields->push_back(remain_source.substr(start_pos, end_pos - start_pos));
    remain_source = remain_source.substr(end_pos + 1);
  }

  return tokenize_append(remain_source, delimiter, fields);
}

bool tokenize_first(const std::string& source,
                    const char delimiter,
                    std::string* token,
                    std::string* rest) {
  // Find the first delimiter
  size_t left_pos = source.find(delimiter);
  if (left_pos == std::string::npos) {
    return false;
  }

  // Look for additional occurrances of delimiter.
  size_t right_pos = left_pos + 1;
  while (source[right_pos] == delimiter) {
    right_pos++;
  }

  *token = source.substr(0, left_pos);
  *rest = source.substr(right_pos);
  return true;
}

size_t split(const std::string& source, char delimiter,
             std::vector<std::string>* fields) {
  RTC_DCHECK(fields);
  fields->clear();
  size_t last = 0;
  for (size_t i = 0; i < source.length(); ++i) {
    if (source[i] == delimiter) {
      fields->push_back(source.substr(last, i - last));
      last = i + 1;
    }
  }
  fields->push_back(source.substr(last, source.length() - last));
  return fields->size();
}

char make_char_safe_for_filename(char c) {
  if (c < 32)
    return '_';

  switch (c) {
    case '<':
    case '>':
    case ':':
    case '"':
    case '/':
    case '\\':
    case '|':
    case '*':
    case '?':
      return '_';

    default:
      return c;
  }
}

/*
void sprintf(std::string& value, size_t maxlen, const char * format, ...) {
  char * buffer = STACK_ARRAY(char, maxlen + 1);
  va_list args;
  va_start(args, format);
  value.assign(buffer, vsprintfn(buffer, maxlen + 1, format, args));
  va_end(args);
}
*/

/////////////////////////////////////////////////////////////////////////////

}  // namespace rtc
