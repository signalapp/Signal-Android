# Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    # simple kenny
    {
      'target_name': 'iSACtest',
      'type': 'executable',
      'dependencies': [
        'iSAC',
      ],
      'include_dirs': [
        './main/test',
        './main/interface',
        './main/util',
        '<(webrtc_root)',
      ],
      'sources': [
        './main/test/simpleKenny.c',
        './main/util/utility.c',
      ],
    },
    # ReleaseTest-API
    {
      'target_name': 'iSACAPITest',
      'type': 'executable',
      'dependencies': [
        'iSAC',
      ],
      'include_dirs': [
        './main/test',
        './main/interface',
        './main/util',
        '<(webrtc_root)',
      ],
      'sources': [
        './main/test/ReleaseTest-API/ReleaseTest-API.cc',
        './main/util/utility.c',
      ],
    },
    # SwitchingSampRate
    {
      'target_name': 'iSACSwitchSampRateTest',
      'type': 'executable',
      'dependencies': [
        'iSAC',
      ],
      'include_dirs': [
        './main/test',
        './main/interface',
        '../../../../common_audio/signal_processing/include',
        './main/util',
        '<(webrtc_root)',
      ],
      'sources': [
        './main/test/SwitchingSampRate/SwitchingSampRate.cc',
        './main/util/utility.c',
      ],    
    },

  ],
}
