/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/checks.h"
#include "webrtc/base/stringutils.h"

namespace rtc {

bool memory_check(const void* memory, int c, size_t count) {
  const char* char_memory = static_cast<const char*>(memory);
  char char_c = static_cast<char>(c);
  for (size_t i = 0; i < count; ++i) {
    if (char_memory[i] != char_c) {
      return false;
    }
  }
  return true;
}

bool string_match(const char* target, const char* pattern) {
  while (*pattern) {
    if (*pattern == '*') {
      if (!*++pattern) {
        return true;
      }
      while (*target) {
        if ((toupper(*pattern) == toupper(*target))
            && string_match(target + 1, pattern + 1)) {
          return true;
        }
        ++target;
      }
      return false;
    } else {
      if (toupper(*pattern) != toupper(*target)) {
        return false;
      }
      ++target;
      ++pattern;
    }
  }
  return !*target;
}

#if defined(WEBRTC_WIN)
int ascii_string_compare(const wchar_t* s1, const char* s2, size_t n,
                         CharacterTransformation transformation) {
  wchar_t c1, c2;
  while (true) {
    if (n-- == 0) return 0;
    c1 = transformation(*s1);
    // Double check that characters are not UTF-8
    RTC_DCHECK_LT(static_cast<unsigned char>(*s2), 128);
    // Note: *s2 gets implicitly promoted to wchar_t
    c2 = transformation(*s2);
    if (c1 != c2) return (c1 < c2) ? -1 : 1;
    if (!c1) return 0;
    ++s1;
    ++s2;
  }
}

size_t asccpyn(wchar_t* buffer, size_t buflen,
               const char* source, size_t srclen) {
  if (buflen <= 0)
    return 0;

  if (srclen == SIZE_UNKNOWN) {
    srclen = strlenn(source, buflen - 1);
  } else if (srclen >= buflen) {
    srclen = buflen - 1;
  }
#if !defined(NDEBUG)
  // Double check that characters are not UTF-8
  for (size_t pos = 0; pos < srclen; ++pos)
    RTC_DCHECK_LT(static_cast<unsigned char>(source[pos]), 128);
#endif
  std::copy(source, source + srclen, buffer);
  buffer[srclen] = 0;
  return srclen;
}

#endif  // WEBRTC_WIN

void replace_substrs(const char *search,
                     size_t search_len,
                     const char *replace,
                     size_t replace_len,
                     std::string *s) {
  size_t pos = 0;
  while ((pos = s->find(search, pos, search_len)) != std::string::npos) {
    s->replace(pos, search_len, replace, replace_len);
    pos += replace_len;
  }
}

bool starts_with(const char *s1, const char *s2) {
  return strncmp(s1, s2, strlen(s2)) == 0;
}

bool ends_with(const char *s1, const char *s2) {
  size_t s1_length = strlen(s1);
  size_t s2_length = strlen(s2);

  if (s2_length > s1_length) {
    return false;
  }

  const char* start = s1 + (s1_length - s2_length);
  return strncmp(start, s2, s2_length) == 0;
}

static const char kWhitespace[] = " \n\r\t";

std::string string_trim(const std::string& s) {
  std::string::size_type first = s.find_first_not_of(kWhitespace);
  std::string::size_type last  = s.find_last_not_of(kWhitespace);

  if (first == std::string::npos || last == std::string::npos) {
    return std::string("");
  }

  return s.substr(first, last - first + 1);
}

}  // namespace rtc
