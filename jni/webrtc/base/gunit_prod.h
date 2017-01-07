/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_GUNIT_PROD_H_
#define WEBRTC_BASE_GUNIT_PROD_H_

#if defined(WEBRTC_ANDROID)
// Android doesn't use gtest at all, so anything that relies on gtest should
// check this define first.
#define NO_GTEST
#elif defined (GTEST_RELATIVE_PATH)
#include "gtest/gtest_prod.h"
#else
#include "testing/base/gunit_prod.h"
#endif

#endif  // WEBRTC_BASE_GUNIT_PROD_H_
