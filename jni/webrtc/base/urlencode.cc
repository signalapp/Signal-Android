/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/urlencode.h"

#include "webrtc/base/common.h"
#include "webrtc/base/stringutils.h"

static int HexPairValue(const char * code) {
  int value = 0;
  for (const char * pch = code; pch < code + 2; ++pch) {
    value <<= 4;
    int digit = *pch;
    if (digit >= '0' && digit <= '9') {
      value += digit - '0';
    }
    else if (digit >= 'A' && digit <= 'F') {
      value += digit - 'A' + 10;
    }
    else if (digit >= 'a' && digit <= 'f') {
      value += digit - 'a' + 10;
    }
    else {
      return -1;
    }
  }
  return value;
}

static int InternalUrlDecode(const char *source, char *dest,
                             bool encode_space_as_plus) {
  char * start = dest;

  while (*source) {
    switch (*source) {
    case '+':
      if (encode_space_as_plus) {
        *(dest++) = ' ';
      } else {
        *dest++ = *source;
      }
      break;
    case '%':
      if (source[1] && source[2]) {
        int value = HexPairValue(source + 1);
        if (value >= 0) {
          *(dest++) = static_cast<char>(value);
          source += 2;
        }
        else {
          *dest++ = '?';
        }
      }
      else {
        *dest++ = '?';
      }
      break;
    default:
      *dest++ = *source;
    }
    source++;
  }

  *dest = 0;
  return static_cast<int>(dest - start);
}

static bool IsValidUrlChar(char ch, bool unsafe_only) {
  if (unsafe_only) {
    return !(ch <= ' ' || strchr("\\\"^&`<>[]{}", ch));
  } else {
    return isalnum(ch) || strchr("-_.!~*'()", ch);
  }
}

namespace rtc {

int UrlDecode(const char *source, char *dest) {
  return InternalUrlDecode(source, dest, true);
}

int UrlDecodeWithoutEncodingSpaceAsPlus(const char *source, char *dest) {
  return InternalUrlDecode(source, dest, false);
}

int InternalUrlEncode(const char *source, char *dest, unsigned int max,
                      bool encode_space_as_plus, bool unsafe_only) {
  static const char *digits = "0123456789ABCDEF";
  if (max == 0) {
    return 0;
  }

  char *start = dest;
  while (static_cast<unsigned>(dest - start) < max && *source) {
    unsigned char ch = static_cast<unsigned char>(*source);
    if (*source == ' ' && encode_space_as_plus && !unsafe_only) {
      *dest++ = '+';
    } else if (IsValidUrlChar(ch, unsafe_only)) {
      *dest++ = *source;
    } else {
      if (static_cast<unsigned>(dest - start) + 4 > max) {
        break;
      }
      *dest++ = '%';
      *dest++ = digits[(ch >> 4) & 0x0F];
      *dest++ = digits[       ch & 0x0F];
    }
    source++;
  }
  ASSERT(static_cast<unsigned int>(dest - start) < max);
  *dest = 0;

  return static_cast<int>(dest - start);
}

int UrlEncode(const char *source, char *dest, unsigned max) {
  return InternalUrlEncode(source, dest, max, true, false);
}

int UrlEncodeWithoutEncodingSpaceAsPlus(const char *source, char *dest,
                                        unsigned max) {
  return InternalUrlEncode(source, dest, max, false, false);
}

int UrlEncodeOnlyUnsafeChars(const char *source, char *dest, unsigned max) {
  return InternalUrlEncode(source, dest, max, false, true);
}

std::string
InternalUrlDecodeString(const std::string & encoded,
                        bool encode_space_as_plus) {
  size_t needed_length = encoded.length() + 1;
  char* buf = STACK_ARRAY(char, needed_length);
  InternalUrlDecode(encoded.c_str(), buf, encode_space_as_plus);
  return buf;
}

std::string
UrlDecodeString(const std::string & encoded) {
  return InternalUrlDecodeString(encoded, true);
}

std::string
UrlDecodeStringWithoutEncodingSpaceAsPlus(const std::string & encoded) {
  return InternalUrlDecodeString(encoded, false);
}

std::string
InternalUrlEncodeString(const std::string & decoded,
                        bool encode_space_as_plus,
                        bool unsafe_only) {
  int needed_length = static_cast<int>(decoded.length()) * 3 + 1;
  char* buf = STACK_ARRAY(char, needed_length);
  InternalUrlEncode(decoded.c_str(), buf, needed_length,
                    encode_space_as_plus, unsafe_only);
  return buf;
}

std::string
UrlEncodeString(const std::string & decoded) {
  return InternalUrlEncodeString(decoded, true, false);
}

std::string
UrlEncodeStringWithoutEncodingSpaceAsPlus(const std::string & decoded) {
  return InternalUrlEncodeString(decoded, false, false);
}

std::string
UrlEncodeStringForOnlyUnsafeChars(const std::string & decoded) {
  return InternalUrlEncodeString(decoded, false, true);
}

}  // namespace rtc
