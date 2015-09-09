#!/bin/bash
#
# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# Android testssl.sh driver script for openssl's testssl
#
# based on openssl's test/testss script and test/Makefile's test_ssl target
#

set -e
trap "echo Exiting on unexpected error." ERR

device=/sdcard/android.testssl

digest='-sha1'
reqcmd="adb shell /system/bin/openssl req"
x509cmd="adb shell /system/bin/openssl x509 $digest"

CAkey="$device/keyCA.ss"
CAcert="$device/certCA.ss"
CAreq="$device/reqCA.ss"
CAconf="$device/CAss.cnf"

Uconf="$device/Uss.cnf"
Ureq="$device/reqU.ss"
Ukey="$device/keyU.ss"
Ucert="$device/certU.ss"

echo
echo "setting up"
adb remount
adb shell rm -r $device
adb shell mkdir $device

echo
echo "pushing test files to device"
adb push . $device

echo
echo "make a certificate request using 'req'"
adb shell "echo \"string to make the random number generator think it has entropy\" >> $device/.rnd"
req_new='-new'
$reqcmd -config $CAconf -out $CAreq -keyout $CAkey $req_new

echo
echo "convert the certificate request into a self signed certificate using 'x509'"
$x509cmd -CAcreateserial -in $CAreq -days 30 -req -out $CAcert -signkey $CAkey -extfile $CAconf -extensions v3_ca

echo
echo "make a user certificate request using 'req'"
$reqcmd -config $Uconf -out $Ureq -keyout $Ukey $req_new

echo
echo "sign user certificate request with the just created CA via 'x509'"
$x509cmd -CAcreateserial -in $Ureq -days 30 -req -out $Ucert -CA $CAcert -CAkey $CAkey -extfile $Uconf -extensions v3_ee

echo
echo "running testssl"
./testssl $Ukey $Ucert $CAcert

echo
echo "cleaning up"
adb shell rm -r $device
