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
      'target_name': 'g722',
      'type': 'static_library',
      'dependencies': [
        'audio_encoder_interface',
      ],
      'sources': [
        'audio_decoder_g722.cc',
        'audio_decoder_g722.h',
        'audio_encoder_g722.cc',
        'audio_encoder_g722.h',
        'g722_interface.c',
        'g722_interface.h',
        'g722_decode.c',
        'g722_enc_dec.h',
        'g722_encode.c',
      ],
    },
  ], # targets
  'conditions': [
    ['include_tests==1', {
      'targets': [
        {
          'target_name': 'g722_test',
          'type': 'executable',
          'dependencies': [
            'g722',
          ],
          'sources': [
            'test/testG722.cc',
          ],
        },
      ], # targets
    }], # include_tests
  ], # conditions
}
