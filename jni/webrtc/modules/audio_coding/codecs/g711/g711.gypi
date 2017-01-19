# Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'G711',
      'type': 'static_library',
      'include_dirs': [
        'include',
        '<(webrtc_root)',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'include',
          '<(webrtc_root)',
        ],
      },
      'sources': [
        'include/g711_interface.h',
        'g711_interface.c',
        'g711.c',
        'g711.h',
      ],
    },
  ], # targets
  'conditions': [
    ['include_tests==1', {
      'targets': [
        {
          'target_name': 'g711_test',
          'type': 'executable',
          'dependencies': [
            'G711',
          ],
          'sources': [
            'test/testG711.cc',
          ],
        },
      ], # targets
    }], # include_tests
  ], # conditions
}
