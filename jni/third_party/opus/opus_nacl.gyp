# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

{
  'includes': [
    '../../native_client/build/untrusted.gypi',
  ],
  'targets': [
    {
      'target_name': 'opus_nacl',
      'type': 'none',
      'variables': {
        'nlib_target': 'libopus_nacl.a',
        'build_glibc': 0,
        'build_newlib': 0,
        'build_pnacl_newlib': 1,
      },
      'defines': [
        'OPUS_BUILD',
        'OPUS_EXPORT=',
        'HAVE_LRINT',
        'HAVE_LRINTF',
        'VAR_ARRAYS',
      ],
      'include_dirs': [
        'src/celt',
        'src/include',
        'src/silk',
        'src/silk/float',
      ],
      'includes': ['opus_srcs.gypi', ],
      'sources': [
        '<@(opus_common_sources)',
        '<@(opus_float_sources)',
      ],
      # Suppress a warning given by opus_decoder.c that tells us
      # optimizations are turned off.
      'cflags': [
        '-Wno-#pragma-messages',
      ],
    },  # end of target 'opus_nacl'
  ],
}
