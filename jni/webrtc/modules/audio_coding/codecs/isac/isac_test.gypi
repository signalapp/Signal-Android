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
      'target_name': 'isac_test',
      'type': 'executable',
      'dependencies': [
        'isac',
      ],
      'include_dirs': [
        './main/test',
        './main/include',
        './main/util',
        '<(webrtc_root)',
      ],
      'sources': [
        './main/test/simpleKenny.c',
        './main/util/utility.c',
      ],
      'conditions': [
        ['OS=="win" and clang==1', {
          'msvs_settings': {
            'VCCLCompilerTool': {
              'AdditionalOptions': [
                # Disable warnings failing when compiling with Clang on Windows.
                # https://bugs.chromium.org/p/webrtc/issues/detail?id=5366
                '-Wno-format',
              ],
            },
          },
        }],
      ],  # conditions.
    },
    # ReleaseTest-API
    {
      'target_name': 'isac_api_test',
      'type': 'executable',
      'dependencies': [
        'isac',
      ],
      'include_dirs': [
        './main/test',
        './main/include',
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
      'target_name': 'isac_switch_samprate_test',
      'type': 'executable',
      'dependencies': [
        'isac',
      ],
      'include_dirs': [
        './main/test',
        './main/include',
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
