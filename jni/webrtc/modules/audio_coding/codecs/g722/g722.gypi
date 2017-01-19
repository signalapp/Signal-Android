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
      'target_name': 'G722',
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
        'include/g722_interface.h',
        'g722_interface.c',
        'g722_encode.c',
        'g722_decode.c',
        'g722_enc_dec.h',
      ],
    },
  ], # targets
  'conditions': [
    ['include_tests==1', {
      'targets': [
        {
          'target_name': 'G722Test',
          'type': 'executable',
          'dependencies': [
            'G722',
          ],
          'sources': [
            'test/testG722.cc',
          ],
        },
      ], # targets
    }], # include_tests
  ], # conditions
}
