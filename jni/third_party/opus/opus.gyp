# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

{
  'variables': {
    'conditions': [
      ['target_arch=="arm" or target_arch=="arm64"', {
        'use_opus_fixed_point%': 1,
      }, {
        'use_opus_fixed_point%': 0,
      }],
      ['target_arch=="arm"', {
        'use_opus_arm_optimization%': 1,
      }, {
        'use_opus_arm_optimization%': 0,
      }],
      ['target_arch=="arm" and (OS=="win" or OS=="android" or OS=="linux")', {
        # Based on the conditions in celt/arm/armcpu.c:
        # defined(_MSC_VER) || defined(__linux__).
        'use_opus_rtcd%': 1,
      }, {
        'use_opus_rtcd%': 0,
      }],
    ],
  },
  'target_defaults': {
    'target_conditions': [
      ['_type=="executable"', {
        # All of the executable targets depend on 'opus'. Unfortunately the
        # 'dependencies' block cannot be inherited via 'target_defaults'.
        'include_dirs': [
          'src/celt',
          'src/silk',
        ],
        'conditions': [
          ['OS == "win"', {
            'defines': [
              'inline=__inline',
            ],
          }],
          ['OS=="android"', {
            'libraries': [
              '-llog',
            ],
          }],
          ['clang==1', {
            'cflags': [ '-Wno-absolute-value' ],
          }]
        ],
      }],
    ],
  },
  'targets': [
    {
      'target_name': 'opus',
      'type': 'static_library',
      'defines': [
        'OPUS_BUILD',
        'OPUS_EXPORT=',
      ],
      'include_dirs': [
        'src/celt',
        'src/include',
        'src/silk',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'src/include',
        ],
      },
      'includes': [
        'opus_srcs.gypi',
        # Disable LTO due to ELF section name out of range
        # crbug.com/422251
        '../../build/android/disable_gcc_lto.gypi',
      ],
      'sources': ['<@(opus_common_sources)'],
      'conditions': [
        ['OS!="win"', {
          'defines': [
            'HAVE_LRINT',
            'HAVE_LRINTF',
            'VAR_ARRAYS',
          ],
        }, {
          'defines': [
            'USE_ALLOCA',
            'inline=__inline',
          ],
          'msvs_disabled_warnings': [
            4305,  # Disable truncation warning in celt/pitch.c .
            4334,  # Disable 32-bit shift warning in src/opus_encoder.c .
          ],
        }],
        ['os_posix==1 and OS!="android"', {
          # Suppress a warning given by opus_decoder.c that tells us
          # optimizations are turned off.
          'cflags': [
            '-Wno-#pragma-messages',
          ],
          'xcode_settings': {
            'WARNING_CFLAGS': [
              '-Wno-#pragma-messages',
            ],
          },
          'link_settings': {
            # This appears in the OS!="android" section because all Android
            # targets already link libm (in common.gypi), and it's important
            # that it appears after libc++ on the link command line.
            # https://code.google.com/p/android-developer-preview/issues/detail?id=3193
            'libraries': [ '-lm' ],
          },
        }],
        ['os_posix==1 and (target_arch=="arm" or target_arch=="arm64")', {
          'cflags!': ['-Os'],
          'cflags': ['-O3'],
        }],
        ['use_opus_fixed_point==0', {
          'include_dirs': [
            'src/silk/float',
          ],
          'sources': ['<@(opus_float_sources)'],
        }, {
          'defines': [
            'FIXED_POINT',
          ],
          'direct_dependent_settings': {
            'defines': [
              'OPUS_FIXED_POINT',
            ],
          },
          'include_dirs': [
            'src/silk/fixed',
          ],
          'sources': ['<@(opus_fixed_sources)'],
          'conditions': [
            ['use_opus_arm_optimization==1', {
              'defines': [
                'OPUS_ARM_ASM',
                'OPUS_ARM_INLINE_ASM',
                'OPUS_ARM_INLINE_EDSP',
              ],
              'includes': [
                'opus_srcs_arm.gypi',
              ],
              'conditions': [
                ['use_opus_rtcd==1', {
                  'defines': [
                    'OPUS_ARM_MAY_HAVE_EDSP',
                    'OPUS_ARM_MAY_HAVE_MEDIA',
                    'OPUS_ARM_MAY_HAVE_NEON',
                    'OPUS_ARM_MAY_HAVE_NEON_INTR',
                    'OPUS_HAVE_RTCD',
                  ],
                  'include_dirs': [
                    'src',
                  ],
                  'includes': [
                    'opus_srcs_rtcd.gypi',
                  ],
                  'cflags!': [ '-mfpu=vfpv3-d16' ],
                  'cflags': [ '-mfpu=neon' ],
                }],
              ],
            }],
          ],
        }],
      ],
    },  # target opus
    {
      'target_name': 'opus_compare',
      'type': 'executable',
      'dependencies': [
        'opus'
      ],
      'sources': [
        'src/src/opus_compare.c',
      ],
    },  # target opus_compare
    {
      'target_name': 'opus_demo',
      'type': 'executable',
      'dependencies': [
        'opus'
      ],
      'sources': [
        'src/src/opus_demo.c',
      ],
    },  # target opus_demo
    {
      'target_name': 'test_opus_api',
      'type': 'executable',
      'dependencies': [
        'opus'
      ],
      'sources': [
        'src/tests/test_opus_api.c',
      ],
    },  # target test_opus_api
    {
      'target_name': 'test_opus_encode',
      'type': 'executable',
      'dependencies': [
        'opus'
      ],
      'sources': [
        'src/tests/test_opus_encode.c',
      ],
    },  # target test_opus_encode
    {
      'target_name': 'test_opus_decode',
      'type': 'executable',
      'dependencies': [
        'opus'
      ],
      'sources': [
        'src/tests/test_opus_decode.c',
      ],
      # test_opus_decode passes a null pointer to opus_decode() for an argument
      # marked as requiring a non-null value by the nonnull function attribute,
      # and expects opus_decode() to fail. Disable the -Wnonnull option to avoid
      # a compilation error if -Werror is specified.
      'conditions': [
        ['os_posix==1 and OS!="mac" and OS!="ios"', {
          'cflags': ['-Wno-nonnull'],
        }],
        ['OS=="mac" or OS=="ios"', {
          'xcode_settings': {
            'WARNING_CFLAGS': ['-Wno-nonnull'],
          },
        }],
      ],
    },  # target test_opus_decode
    {
      'target_name': 'test_opus_padding',
      'type': 'executable',
      'dependencies': [
        'opus'
      ],
      'sources': [
        'src/tests/test_opus_padding.c',
      ],
    },  # target test_opus_padding
  ]
}
