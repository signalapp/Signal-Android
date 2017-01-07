/*
 *  Copyright 2006 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_DISKCACHEWIN32_H__
#define WEBRTC_BASE_DISKCACHEWIN32_H__

#include "webrtc/base/diskcache.h"

namespace rtc {

class DiskCacheWin32 : public DiskCache {
 protected:
  virtual bool InitializeEntries();
  virtual bool PurgeFiles();

  virtual bool FileExists(const std::string& filename) const;
  virtual bool DeleteFile(const std::string& filename) const;
};

}

#endif  // WEBRTC_BASE_DISKCACHEWIN32_H__
