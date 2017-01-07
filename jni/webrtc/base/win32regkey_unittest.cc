/*
 *  Copyright 2003 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unittest for registry access API

#include "webrtc/base/arraysize.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/common.h"
#include "webrtc/base/win32regkey.h"

namespace rtc {

#ifndef EXPECT_SUCCEEDED
#define EXPECT_SUCCEEDED(x)  EXPECT_TRUE(SUCCEEDED(x))
#endif

#ifndef EXPECT_FAILED
#define EXPECT_FAILED(x)  EXPECT_TRUE(FAILED(x))
#endif

#define kBaseKey           L"Software\\Google\\__TEST"
#define kSubkeyName        L"subkey_test"

const wchar_t kRkey1[] = kBaseKey;
const wchar_t kRkey1SubkeyName[] = kSubkeyName;
const wchar_t kRkey1Subkey[] = kBaseKey L"\\" kSubkeyName;
const wchar_t kFullRkey1[] = L"HKCU\\" kBaseKey;
const wchar_t kFullRkey1Subkey[] = L"HKCU\\" kBaseKey L"\\" kSubkeyName;

const wchar_t kValNameInt[] = L"Int32 Value";
const DWORD kIntVal = 20;
const DWORD kIntVal2 = 30;

const wchar_t kValNameInt64[] = L"Int64 Value";
const DWORD64 kIntVal64 = 119600064000000000uI64;

const wchar_t kValNameFloat[] = L"Float Value";
const float kFloatVal = 12.3456789f;

const wchar_t kValNameDouble[] = L"Double Value";
const double kDoubleVal = 98.7654321;

const wchar_t kValNameStr[] = L"Str Value";
const wchar_t kStrVal[] = L"Some string data 1";
const wchar_t kStrVal2[] = L"Some string data 2";

const wchar_t kValNameBinary[] = L"Binary Value";
const char kBinaryVal[] = "Some binary data abcdefghi 1";
const char kBinaryVal2[] = "Some binary data abcdefghi 2";

const wchar_t kValNameMultiStr[] = L"MultiStr Value";
const wchar_t kMultiSZ[] = L"abc\0def\0P12345\0";
const wchar_t kEmptyMultiSZ[] = L"";
const wchar_t kInvalidMultiSZ[] = {L'6', L'7', L'8'};

// friend function of RegKey
void RegKeyHelperFunctionsTest() {
  // Try out some dud values
  std::wstring temp_key = L"";
  EXPECT_TRUE(RegKey::GetRootKeyInfo(&temp_key) == NULL);
  EXPECT_STREQ(temp_key.c_str(), L"");

  temp_key = L"a";
  EXPECT_TRUE(RegKey::GetRootKeyInfo(&temp_key) == NULL);
  EXPECT_STREQ(temp_key.c_str(), L"");

  // The basics
  temp_key = L"HKLM\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_LOCAL_MACHINE);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"HKEY_LOCAL_MACHINE\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_LOCAL_MACHINE);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"HKCU\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_CURRENT_USER);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"HKEY_CURRENT_USER\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_CURRENT_USER);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"HKU\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_USERS);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"HKEY_USERS\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_USERS);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"HKCR\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_CLASSES_ROOT);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"HKEY_CLASSES_ROOT\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_CLASSES_ROOT);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  // Make sure it is case insensitive
  temp_key = L"hkcr\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_CLASSES_ROOT);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"hkey_CLASSES_ROOT\\a";
  EXPECT_EQ(RegKey::GetRootKeyInfo(&temp_key), HKEY_CLASSES_ROOT);
  EXPECT_STREQ(temp_key.c_str(), L"a");

  //
  // Test RegKey::GetParentKeyInfo
  //

  // dud cases
  temp_key = L"";
  EXPECT_STREQ(RegKey::GetParentKeyInfo(&temp_key).c_str(), L"");
  EXPECT_STREQ(temp_key.c_str(), L"");

  temp_key = L"a";
  EXPECT_STREQ(RegKey::GetParentKeyInfo(&temp_key).c_str(), L"");
  EXPECT_STREQ(temp_key.c_str(), L"a");

  temp_key = L"a\\b";
  EXPECT_STREQ(RegKey::GetParentKeyInfo(&temp_key).c_str(), L"a");
  EXPECT_STREQ(temp_key.c_str(), L"b");

  temp_key = L"\\b";
  EXPECT_STREQ(RegKey::GetParentKeyInfo(&temp_key).c_str(), L"");
  EXPECT_STREQ(temp_key.c_str(), L"b");

  // Some regular cases
  temp_key = L"HKEY_CLASSES_ROOT\\moon";
  EXPECT_STREQ(RegKey::GetParentKeyInfo(&temp_key).c_str(),
               L"HKEY_CLASSES_ROOT");
  EXPECT_STREQ(temp_key.c_str(), L"moon");

  temp_key = L"HKEY_CLASSES_ROOT\\moon\\doggy";
  EXPECT_STREQ(RegKey::GetParentKeyInfo(&temp_key).c_str(),
               L"HKEY_CLASSES_ROOT\\moon");
  EXPECT_STREQ(temp_key.c_str(), L"doggy");

  //
  // Test MultiSZBytesToStringArray
  //

  std::vector<std::wstring> result;
  EXPECT_SUCCEEDED(RegKey::MultiSZBytesToStringArray(
      reinterpret_cast<const uint8_t*>(kMultiSZ), sizeof(kMultiSZ), &result));
  EXPECT_EQ(result.size(), 3);
  EXPECT_STREQ(result[0].c_str(), L"abc");
  EXPECT_STREQ(result[1].c_str(), L"def");
  EXPECT_STREQ(result[2].c_str(), L"P12345");

  EXPECT_SUCCEEDED(RegKey::MultiSZBytesToStringArray(
      reinterpret_cast<const uint8_t*>(kEmptyMultiSZ), sizeof(kEmptyMultiSZ),
      &result));
  EXPECT_EQ(result.size(), 0);
  EXPECT_FALSE(SUCCEEDED(RegKey::MultiSZBytesToStringArray(
      reinterpret_cast<const uint8_t*>(kInvalidMultiSZ),
      sizeof(kInvalidMultiSZ), &result)));
}

TEST(RegKeyTest, RegKeyHelperFunctionsTest) {
  RegKeyHelperFunctionsTest();
}

void RegKeyNonStaticFunctionsTest() {
  DWORD int_val = 0;
  DWORD64 int64_val = 0;
  wchar_t* str_val = NULL;
  uint8_t* binary_val = NULL;
  DWORD uint8_count = 0;

  // Just in case...
  // make sure the no test key residue is left from previous aborted runs
  RegKey::DeleteKey(kFullRkey1);

  // initial state
  RegKey r_key;
  EXPECT_TRUE(r_key.key() == NULL);

  // create a reg key
  EXPECT_SUCCEEDED(r_key.Create(HKEY_CURRENT_USER, kRkey1));

  // do the create twice - it should return the already created one
  EXPECT_SUCCEEDED(r_key.Create(HKEY_CURRENT_USER, kRkey1));

  // now do an open - should work just fine
  EXPECT_SUCCEEDED(r_key.Open(HKEY_CURRENT_USER, kRkey1));

  // get an in-existent value
  EXPECT_EQ(r_key.GetValue(kValNameInt, &int_val),
            HRESULT_FROM_WIN32(ERROR_FILE_NOT_FOUND));

  // set and get some values

  // set an INT 32
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameInt, kIntVal));

  // check that the value exists
  EXPECT_TRUE(r_key.HasValue(kValNameInt));

  // read it back
  EXPECT_SUCCEEDED(r_key.GetValue(kValNameInt, &int_val));
  EXPECT_EQ(int_val, kIntVal);

  // set it again!
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameInt, kIntVal2));

  // read it again
  EXPECT_SUCCEEDED(r_key.GetValue(kValNameInt, &int_val));
  EXPECT_EQ(int_val, kIntVal2);

  // delete the value
  EXPECT_SUCCEEDED(r_key.DeleteValue(kValNameInt));

  // check that the value is gone
  EXPECT_FALSE(r_key.HasValue(kValNameInt));

  // set an INT 64
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameInt64, kIntVal64));

  // check that the value exists
  EXPECT_TRUE(r_key.HasValue(kValNameInt64));

  // read it back
  EXPECT_SUCCEEDED(r_key.GetValue(kValNameInt64, &int64_val));
  EXPECT_EQ(int64_val, kIntVal64);

  // delete the value
  EXPECT_SUCCEEDED(r_key.DeleteValue(kValNameInt64));

  // check that the value is gone
  EXPECT_FALSE(r_key.HasValue(kValNameInt64));

  // set a string
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameStr, kStrVal));

  // check that the value exists
  EXPECT_TRUE(r_key.HasValue(kValNameStr));

  // read it back
  EXPECT_SUCCEEDED(r_key.GetValue(kValNameStr, &str_val));
  EXPECT_TRUE(lstrcmp(str_val, kStrVal) == 0);
  delete[] str_val;

  // set it again
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameStr, kStrVal2));

  // read it again
  EXPECT_SUCCEEDED(r_key.GetValue(kValNameStr, &str_val));
  EXPECT_TRUE(lstrcmp(str_val, kStrVal2) == 0);
  delete[] str_val;

  // delete the value
  EXPECT_SUCCEEDED(r_key.DeleteValue(kValNameStr));

  // check that the value is gone
  EXPECT_FALSE(r_key.HasValue(kValNameInt));

  // set a binary value
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameBinary,
                                  reinterpret_cast<const uint8_t*>(kBinaryVal),
                                  sizeof(kBinaryVal) - 1));

  // check that the value exists
  EXPECT_TRUE(r_key.HasValue(kValNameBinary));

  // read it back
  EXPECT_SUCCEEDED(r_key.GetValue(kValNameBinary, &binary_val, &uint8_count));
  EXPECT_TRUE(memcmp(binary_val, kBinaryVal, sizeof(kBinaryVal) - 1) == 0);
  delete[] binary_val;

  // set it again
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameBinary,
                                  reinterpret_cast<const uint8_t*>(kBinaryVal2),
                                  sizeof(kBinaryVal) - 1));

  // read it again
  EXPECT_SUCCEEDED(r_key.GetValue(kValNameBinary, &binary_val, &uint8_count));
  EXPECT_TRUE(memcmp(binary_val, kBinaryVal2, sizeof(kBinaryVal2) - 1) == 0);
  delete[] binary_val;

  // delete the value
  EXPECT_SUCCEEDED(r_key.DeleteValue(kValNameBinary));

  // check that the value is gone
  EXPECT_FALSE(r_key.HasValue(kValNameBinary));

  // set some values and check the total count

  // set an INT 32
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameInt, kIntVal));

  // set an INT 64
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameInt64, kIntVal64));

  // set a string
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameStr, kStrVal));

  // set a binary value
  EXPECT_SUCCEEDED(r_key.SetValue(kValNameBinary,
                                  reinterpret_cast<const uint8_t*>(kBinaryVal),
                                  sizeof(kBinaryVal) - 1));

  // get the value count
  uint32_t value_count = r_key.GetValueCount();
  EXPECT_EQ(value_count, 4);

  // check the value names
  std::wstring value_name;
  DWORD type = 0;

  EXPECT_SUCCEEDED(r_key.GetValueNameAt(0, &value_name, &type));
  EXPECT_STREQ(value_name.c_str(), kValNameInt);
  EXPECT_EQ(type, REG_DWORD);

  EXPECT_SUCCEEDED(r_key.GetValueNameAt(1, &value_name, &type));
  EXPECT_STREQ(value_name.c_str(), kValNameInt64);
  EXPECT_EQ(type, REG_QWORD);

  EXPECT_SUCCEEDED(r_key.GetValueNameAt(2, &value_name, &type));
  EXPECT_STREQ(value_name.c_str(), kValNameStr);
  EXPECT_EQ(type, REG_SZ);

  EXPECT_SUCCEEDED(r_key.GetValueNameAt(3, &value_name, &type));
  EXPECT_STREQ(value_name.c_str(), kValNameBinary);
  EXPECT_EQ(type, REG_BINARY);

  // check that there are no more values
  EXPECT_FAILED(r_key.GetValueNameAt(4, &value_name, &type));

  uint32_t subkey_count = r_key.GetSubkeyCount();
  EXPECT_EQ(subkey_count, 0);

  // now create a subkey and make sure we can get the name
  RegKey temp_key;
  EXPECT_SUCCEEDED(temp_key.Create(HKEY_CURRENT_USER, kRkey1Subkey));

  // check the subkey exists
  EXPECT_TRUE(r_key.HasSubkey(kRkey1SubkeyName));

  // check the name
  EXPECT_EQ(r_key.GetSubkeyCount(), 1);

  std::wstring subkey_name;
  EXPECT_SUCCEEDED(r_key.GetSubkeyNameAt(0, &subkey_name));
  EXPECT_STREQ(subkey_name.c_str(), kRkey1SubkeyName);

  // delete the key
  EXPECT_SUCCEEDED(r_key.DeleteSubKey(kRkey1));

  // close this key
  EXPECT_SUCCEEDED(r_key.Close());

  // whack the whole key
  EXPECT_SUCCEEDED(RegKey::DeleteKey(kFullRkey1));
}

void RegKeyStaticFunctionsTest() {
  DWORD int_val = 0;
  DWORD64 int64_val = 0;
  float float_val = 0;
  double double_val = 0;
  wchar_t* str_val = NULL;
  std::wstring wstr_val;
  uint8_t* binary_val = NULL;
  DWORD uint8_count = 0;

  // Just in case...
  // make sure the no test key residue is left from previous aborted runs
  RegKey::DeleteKey(kFullRkey1);

  // get an in-existent value from an un-existent key
  EXPECT_EQ(RegKey::GetValue(kFullRkey1, kValNameInt, &int_val),
            HRESULT_FROM_WIN32(ERROR_FILE_NOT_FOUND));

  // set int32_t
  EXPECT_SUCCEEDED(RegKey::SetValue(kFullRkey1, kValNameInt, kIntVal));

  // check that the value exists
  EXPECT_TRUE(RegKey::HasValue(kFullRkey1, kValNameInt));

  // get an in-existent value from an existent key
  EXPECT_EQ(RegKey::GetValue(kFullRkey1, L"bogus", &int_val),
            HRESULT_FROM_WIN32(ERROR_FILE_NOT_FOUND));

  // read it back
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameInt, &int_val));
  EXPECT_EQ(int_val, kIntVal);

  // delete the value
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1, kValNameInt));

  // check that the value is gone
  EXPECT_FALSE(RegKey::HasValue(kFullRkey1, kValNameInt));

  // set int64_t
  EXPECT_SUCCEEDED(RegKey::SetValue(kFullRkey1, kValNameInt64, kIntVal64));

  // check that the value exists
  EXPECT_TRUE(RegKey::HasValue(kFullRkey1, kValNameInt64));

  // read it back
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameInt64, &int64_val));
  EXPECT_EQ(int64_val, kIntVal64);

  // delete the value
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1, kValNameInt64));

  // check that the value is gone
  EXPECT_FALSE(RegKey::HasValue(kFullRkey1, kValNameInt64));

  // set float
  EXPECT_SUCCEEDED(RegKey::SetValue(kFullRkey1, kValNameFloat, kFloatVal));

  // check that the value exists
  EXPECT_TRUE(RegKey::HasValue(kFullRkey1, kValNameFloat));

  // read it back
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameFloat, &float_val));
  EXPECT_EQ(float_val, kFloatVal);

  // delete the value
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1, kValNameFloat));

  // check that the value is gone
  EXPECT_FALSE(RegKey::HasValue(kFullRkey1, kValNameFloat));
  EXPECT_FAILED(RegKey::GetValue(kFullRkey1, kValNameFloat, &float_val));

  // set double
  EXPECT_SUCCEEDED(RegKey::SetValue(kFullRkey1, kValNameDouble, kDoubleVal));

  // check that the value exists
  EXPECT_TRUE(RegKey::HasValue(kFullRkey1, kValNameDouble));

  // read it back
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameDouble, &double_val));
  EXPECT_EQ(double_val, kDoubleVal);

  // delete the value
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1, kValNameDouble));

  // check that the value is gone
  EXPECT_FALSE(RegKey::HasValue(kFullRkey1, kValNameDouble));
  EXPECT_FAILED(RegKey::GetValue(kFullRkey1, kValNameDouble, &double_val));

  // set string
  EXPECT_SUCCEEDED(RegKey::SetValue(kFullRkey1, kValNameStr, kStrVal));

  // check that the value exists
  EXPECT_TRUE(RegKey::HasValue(kFullRkey1, kValNameStr));

  // read it back
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameStr, &str_val));
  EXPECT_TRUE(lstrcmp(str_val, kStrVal) == 0);
  delete[] str_val;

  // read it back in std::wstring
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameStr, &wstr_val));
  EXPECT_STREQ(wstr_val.c_str(), kStrVal);

  // get an in-existent value from an existent key
  EXPECT_EQ(RegKey::GetValue(kFullRkey1, L"bogus", &str_val),
            HRESULT_FROM_WIN32(ERROR_FILE_NOT_FOUND));

  // delete the value
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1, kValNameStr));

  // check that the value is gone
  EXPECT_FALSE(RegKey::HasValue(kFullRkey1, kValNameStr));

  // set binary
  EXPECT_SUCCEEDED(RegKey::SetValue(
      kFullRkey1, kValNameBinary, reinterpret_cast<const uint8_t*>(kBinaryVal),
      sizeof(kBinaryVal) - 1));

  // check that the value exists
  EXPECT_TRUE(RegKey::HasValue(kFullRkey1, kValNameBinary));

  // read it back
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameBinary,
      &binary_val, &uint8_count));
  EXPECT_TRUE(memcmp(binary_val, kBinaryVal, sizeof(kBinaryVal)-1) == 0);
  delete[] binary_val;

  // delete the value
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1, kValNameBinary));

  // check that the value is gone
  EXPECT_FALSE(RegKey::HasValue(kFullRkey1, kValNameBinary));

  // special case - set a binary value with length 0
  EXPECT_SUCCEEDED(
      RegKey::SetValue(kFullRkey1, kValNameBinary,
                       reinterpret_cast<const uint8_t*>(kBinaryVal), 0));

  // check that the value exists
  EXPECT_TRUE(RegKey::HasValue(kFullRkey1, kValNameBinary));

  // read it back
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameBinary,
      &binary_val, &uint8_count));
  EXPECT_EQ(uint8_count, 0);
  EXPECT_TRUE(binary_val == NULL);
  delete[] binary_val;

  // delete the value
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1, kValNameBinary));

  // check that the value is gone
  EXPECT_FALSE(RegKey::HasValue(kFullRkey1, kValNameBinary));

  // special case - set a NULL binary value
  EXPECT_SUCCEEDED(RegKey::SetValue(kFullRkey1, kValNameBinary, NULL, 100));

  // check that the value exists
  EXPECT_TRUE(RegKey::HasValue(kFullRkey1, kValNameBinary));

  // read it back
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameBinary,
                                    &binary_val, &uint8_count));
  EXPECT_EQ(uint8_count, 0);
  EXPECT_TRUE(binary_val == NULL);
  delete[] binary_val;

  // delete the value
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1, kValNameBinary));

  // check that the value is gone
  EXPECT_FALSE(RegKey::HasValue(kFullRkey1, kValNameBinary));

  // test read/write REG_MULTI_SZ value
  std::vector<std::wstring> result;
  EXPECT_SUCCEEDED(RegKey::SetValueMultiSZ(
      kFullRkey1, kValNameMultiStr, reinterpret_cast<const uint8_t*>(kMultiSZ),
      sizeof(kMultiSZ)));
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameMultiStr, &result));
  EXPECT_EQ(result.size(), 3);
  EXPECT_STREQ(result[0].c_str(), L"abc");
  EXPECT_STREQ(result[1].c_str(), L"def");
  EXPECT_STREQ(result[2].c_str(), L"P12345");
  EXPECT_SUCCEEDED(RegKey::SetValueMultiSZ(
      kFullRkey1, kValNameMultiStr,
      reinterpret_cast<const uint8_t*>(kEmptyMultiSZ), sizeof(kEmptyMultiSZ)));
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameMultiStr, &result));
  EXPECT_EQ(result.size(), 0);
  // writing REG_MULTI_SZ value will automatically add ending null characters
  EXPECT_SUCCEEDED(
      RegKey::SetValueMultiSZ(kFullRkey1, kValNameMultiStr,
                              reinterpret_cast<const uint8_t*>(kInvalidMultiSZ),
                              sizeof(kInvalidMultiSZ)));
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1, kValNameMultiStr, &result));
  EXPECT_EQ(result.size(), 1);
  EXPECT_STREQ(result[0].c_str(), L"678");

  // Run the following test only in dev machine
  // This is because the build machine might not have admin privilege
#ifdef IS_PRIVATE_BUILD
  // get a temp file name
  wchar_t temp_path[MAX_PATH] = {0};
  EXPECT_LT(::GetTempPath(arraysize(temp_path), temp_path),
            static_cast<DWORD>(arraysize(temp_path)));
  wchar_t temp_file[MAX_PATH] = {0};
  EXPECT_NE(::GetTempFileName(temp_path, L"rkut_",
                              ::GetTickCount(), temp_file), 0);

  // test save
  EXPECT_SUCCEEDED(RegKey::SetValue(kFullRkey1Subkey, kValNameInt, kIntVal));
  EXPECT_SUCCEEDED(RegKey::SetValue(kFullRkey1Subkey, kValNameInt64, kIntVal64));
  EXPECT_SUCCEEDED(RegKey::Save(kFullRkey1Subkey, temp_file));
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1Subkey, kValNameInt));
  EXPECT_SUCCEEDED(RegKey::DeleteValue(kFullRkey1Subkey, kValNameInt64));

  // test restore
  EXPECT_SUCCEEDED(RegKey::Restore(kFullRkey1Subkey, temp_file));
  int_val = 0;
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1Subkey, kValNameInt, &int_val));
  EXPECT_EQ(int_val, kIntVal);
  int64_val = 0;
  EXPECT_SUCCEEDED(RegKey::GetValue(kFullRkey1Subkey,
                                    kValNameInt64,
                                    &int64_val));
  EXPECT_EQ(int64_val, kIntVal64);

  // delete the temp file
  EXPECT_EQ(TRUE, ::DeleteFile(temp_file));
#endif

  // whack the whole key
  EXPECT_SUCCEEDED(RegKey::DeleteKey(kFullRkey1));
}

// Run both tests under the same test target. Because they access (read and
// write) the same registry keys they can't run in parallel with eachother.
TEST(RegKeyTest, RegKeyFunctionsTest) {
  RegKeyNonStaticFunctionsTest();
  RegKeyStaticFunctionsTest();
}

}  // namespace rtc
