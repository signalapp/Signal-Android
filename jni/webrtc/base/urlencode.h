/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef _URLENCODE_H_
#define _URLENCODE_H_ 

#include <string>

namespace rtc {

// Decode all encoded characters. Also decode + as space.
int UrlDecode(const char *source, char *dest);

// Decode all encoded characters.
int UrlDecodeWithoutEncodingSpaceAsPlus(const char *source, char *dest);

// Encode all characters except alphas, numbers, and -_.!~*'()
// Also encode space as +.
int UrlEncode(const char *source, char *dest, unsigned max);

// Encode all characters except alphas, numbers, and -_.!~*'()
int UrlEncodeWithoutEncodingSpaceAsPlus(const char *source, char *dest,
                                        unsigned max);

// Encode only unsafe chars, including \ "^&`<>[]{}
// Also encode space as %20, instead of +
int UrlEncodeOnlyUnsafeChars(const char *source, char *dest, unsigned max);

std::string UrlDecodeString(const std::string & encoded);
std::string UrlDecodeStringWithoutEncodingSpaceAsPlus(
    const std::string & encoded);
std::string UrlEncodeString(const std::string & decoded);
std::string UrlEncodeStringWithoutEncodingSpaceAsPlus(
    const std::string & decoded);
std::string UrlEncodeStringForOnlyUnsafeChars(const std::string & decoded);

#endif

}  // namespace rtc
