# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    # kenny
    {
      'target_name': 'isac_fix_test',
      'type': 'executable',
      'dependencies': [
        'isac_fix',
        '<(webrtc_root)/test/test.gyp:test_support',
      ],
      'include_dirs': [
        './fix/test',
        './fix/include',
        '<(webrtc_root)',
      ],
      'sources': [
        './fix/test/kenny.cc',
      ],
      # Disable warnings to enable Win64 build, issue 1323.
      'msvs_disabled_warnings': [
        4267,  # size_t to int truncation.
      ],
    },
  ],
}

# TODO(kma): Add bit-exact test for iSAC-fix.
