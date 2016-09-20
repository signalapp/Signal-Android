#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Script for converting celt_pitch_xcorr_arm.s -> celt_pitch_xcorr_arm.S
# using the arm2gnu.pl script.

import os
import sys


USAGE = ('Usage:\n'
         './convert_rtcd_assembler.py arm2gnu_script input_file output_file')


def main(argv):
  if len(argv) != 3:
    print >> sys.stderr, ('Error: You must pass the following arguments:\n'
                          ' * arm2gnu_script_path\n'
                          ' * input_file\n'
                          ' * output_file')
    print USAGE
    return 1

  arm2gnu_script = os.path.abspath(argv[0])
  if not os.path.exists(arm2gnu_script):
    print >> sys.stderr, ('Error: Cannot find arm2gnu.pl script at: %s.' %
                          arm2gnu_script)
    return 2

  input_file = os.path.abspath(argv[1])
  if not os.path.exists(input_file):
    print >> sys.stderr, 'Error: Cannot find input file at: %s.' % input_file
    return 3

  output_file = argv[2]

  # Ensure the output file's directory path exists.
  output_dir = os.path.dirname(output_file)
  if not os.path.exists(output_dir):
    os.makedirs(output_dir)

  cmd = ('perl %s %s | '
         'sed "s/OPUS_ARM_MAY_HAVE_[A-Z]*/1/g" | '
         'sed "/.include/d" '
         '> %s') % (arm2gnu_script, input_file, output_file)
  print cmd
  return os.system(cmd)

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
