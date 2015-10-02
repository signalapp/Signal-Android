# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'iSACFix',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
        '<(webrtc_root)/system_wrappers/source/system_wrappers.gyp:system_wrappers',
      ],
      'include_dirs': [
        '../interface',
        '<(webrtc_root)'
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          '../interface',
          '<(webrtc_root)',
        ],
      },
      'sources': [
        '../interface/isacfix.h',
        'arith_routines.c',
        'arith_routines_hist.c',
        'arith_routines_logist.c',
        'bandwidth_estimator.c',
        'decode.c',
        'decode_bwe.c',
        'decode_plc.c',
        'encode.c',
        'entropy_coding.c',
        'fft.c',
        'filterbank_tables.c',
        'filterbanks.c',
        'filters.c',
        'initialize.c',
        'isacfix.c',
        'lattice.c',
        'lattice_c.c',
        'lpc_masking_model.c',
        'lpc_tables.c',
        'pitch_estimator.c',
        'pitch_estimator_c.c',
        'pitch_filter.c',
        'pitch_filter_c.c',
        'pitch_gain_tables.c',
        'pitch_lag_tables.c',
        'spectrum_ar_model_tables.c',
        'transform.c',
        'transform_tables.c',
        'arith_routins.h',
        'bandwidth_estimator.h',
        'codec.h',
        'entropy_coding.h',
        'fft.h',
        'filterbank_tables.h',
        'lpc_masking_model.h',
        'lpc_tables.h',
        'pitch_estimator.h',
        'pitch_gain_tables.h',
        'pitch_lag_tables.h',
        'settings.h',
        'spectrum_ar_model_tables.h',
        'structs.h',
      ],
      'conditions': [
        ['OS!="win"', {
          'defines': [
            'WEBRTC_LINUX',
          ],
        }],
        ['(target_arch=="arm" and arm_version==7) or target_arch=="armv7"', {
          'dependencies': [ 'isac_neon', ],
          'sources': [
            'lattice_armv7.S',
            'pitch_filter_armv6.S',
          ],
          'sources!': [
            'lattice_c.c',
            'pitch_filter_c.c',
          ],
        }],
        ['target_arch=="mipsel"', {
          'sources': [
            'entropy_coding_mips.c',
            'filters_mips.c',
            'lattice_mips.c',
            'pitch_estimator_mips.c',
            'transform_mips.c',
          ],
          'sources!': [
            'lattice_c.c',
            'pitch_estimator_c.c',
          ],
          'conditions': [
            ['mips_dsp_rev>0', {
              'sources': [
                'filterbanks_mips.c',
              ],
            }],
            ['mips_dsp_rev>1', {
              'sources': [
                'lpc_masking_model_mips.c',
                'pitch_filter_mips.c',
              ],
              'sources!': [
                'pitch_filter_c.c',
              ],
            }],
          ],
        }],
      ],
    },
  ],
  'conditions': [
    ['(target_arch=="arm" and arm_version==7) or target_arch=="armv7"', {
      'targets': [
        {
          'target_name': 'isac_neon',
          'type': 'static_library',
          'includes': ['../../../../../../build/arm_neon.gypi',],
          'dependencies': [
            '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
          ],
          'include_dirs': [
            '<(webrtc_root)',
          ],
          'sources': [
            'entropy_coding_neon.c',
            'filterbanks_neon.S',
            'filters_neon.S',
            'lattice_neon.S',
            'lpc_masking_model_neon.S',
            'transform_neon.S',
          ],
        },
      ],
    }],
  ],
}
