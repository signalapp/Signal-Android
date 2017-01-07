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
      'target_name': 'pcm16b',
      'type': 'static_library',
      'dependencies': [
        'audio_encoder_interface',
        'g711',
      ],
      'sources': [
        'audio_decoder_pcm16b.cc',
        'audio_decoder_pcm16b.h',
        'audio_encoder_pcm16b.cc',
        'audio_encoder_pcm16b.h',
        'pcm16b.c',
        'pcm16b.h',
      ],
    },
  ], # targets
}
