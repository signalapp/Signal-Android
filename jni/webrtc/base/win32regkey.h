/*
 *  Copyright 2003 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Registry configuration wrappers class
//
// Offers static functions for convenient
// fast access for individual values
//
// Also provides a wrapper class for efficient
// batch operations on values of a given registry key.
//

#ifndef WEBRTC_BASE_WIN32REGKEY_H_
#define WEBRTC_BASE_WIN32REGKEY_H_

#include <string>
#include <vector>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/win32.h"

namespace rtc {

// maximum sizes registry key and value names
const int kMaxKeyNameChars = 255 + 1;
const int kMaxValueNameChars = 16383 + 1;

class RegKey {
 public:
  // constructor
  RegKey();

  // destructor
  ~RegKey();

  // create a reg key
  HRESULT Create(HKEY parent_key, const wchar_t* key_name);

  HRESULT Create(HKEY parent_key,
                 const wchar_t* key_name,
                 wchar_t* reg_class,
                 DWORD options,
                 REGSAM sam_desired,
                 LPSECURITY_ATTRIBUTES lp_sec_attr,
                 LPDWORD lp_disposition);

  // open an existing reg key
  HRESULT Open(HKEY parent_key, const wchar_t* key_name);

  HRESULT Open(HKEY parent_key, const wchar_t* key_name, REGSAM sam_desired);

  // close this reg key
  HRESULT Close();

  // check if the key has a specified value
  bool HasValue(const wchar_t* value_name) const;

  // get the number of values for this key
  uint32_t GetValueCount();

  // Called to get the value name for the given value name index
  // Use GetValueCount() to get the total value_name count for this key
  // Returns failure if no key at the specified index
  // If you modify the key while enumerating, the indexes will be out of order.
  // Since the index order is not guaranteed, you need to reset your counting
  // loop.
  // 'type' refers to REG_DWORD, REG_QWORD, etc..
  // 'type' can be NULL if not interested in the value type
  HRESULT GetValueNameAt(int index, std::wstring* value_name, DWORD* type);

  // check if the current key has the specified subkey
  bool HasSubkey(const wchar_t* key_name) const;

  // get the number of subkeys for this key
  uint32_t GetSubkeyCount();

  // Called to get the key name for the given key index
  // Use GetSubkeyCount() to get the total count for this key
  // Returns failure if no key at the specified index
  // If you modify the key while enumerating, the indexes will be out of order.
  // Since the index order is not guaranteed, you need to reset your counting
  // loop.
  HRESULT GetSubkeyNameAt(int index, std::wstring* key_name);

  // SETTERS

  // set an int32_t value - use when reading multiple values from a key
  HRESULT SetValue(const wchar_t* value_name, DWORD value) const;

  // set an int64_t value
  HRESULT SetValue(const wchar_t* value_name, DWORD64 value) const;

  // set a string value
  HRESULT SetValue(const wchar_t* value_name, const wchar_t* value) const;

  // set binary data
  HRESULT SetValue(const wchar_t* value_name,
                   const uint8_t* value,
                   DWORD byte_count) const;

  // set raw data, including type
  HRESULT SetValue(const wchar_t* value_name,
                   const uint8_t* value,
                   DWORD byte_count,
                   DWORD type) const;

  // GETTERS

  // get an int32_t value
  HRESULT GetValue(const wchar_t* value_name, DWORD* value) const;

  // get an int64_t value
  HRESULT GetValue(const wchar_t* value_name, DWORD64* value) const;

  // get a string value - the caller must free the return buffer
  HRESULT GetValue(const wchar_t* value_name, wchar_t** value) const;

  // get a string value
  HRESULT GetValue(const wchar_t* value_name, std::wstring* value) const;

  // get a std::vector<std::wstring> value from REG_MULTI_SZ type
  HRESULT GetValue(const wchar_t* value_name,
                   std::vector<std::wstring>* value) const;

  // get binary data - the caller must free the return buffer
  HRESULT GetValue(const wchar_t* value_name,
                   uint8_t** value,
                   DWORD* byte_count) const;

  // get raw data, including type - the caller must free the return buffer
  HRESULT GetValue(const wchar_t* value_name,
                   uint8_t** value,
                   DWORD* byte_count,
                   DWORD* type) const;

  // STATIC VERSIONS

  // flush
  static HRESULT FlushKey(const wchar_t* full_key_name);

  // check if a key exists
  static bool HasKey(const wchar_t* full_key_name);

  // check if the key has a specified value
  static bool HasValue(const wchar_t* full_key_name, const wchar_t* value_name);

  // SETTERS

  // STATIC int32_t set
  static HRESULT SetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          DWORD value);

  // STATIC int64_t set
  static HRESULT SetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          DWORD64 value);

  // STATIC float set
  static HRESULT SetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          float value);

  // STATIC double set
  static HRESULT SetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          double value);

  // STATIC string set
  static HRESULT SetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          const wchar_t* value);

  // STATIC binary data set
  static HRESULT SetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          const uint8_t* value,
                          DWORD byte_count);

  // STATIC multi-string set
  static HRESULT SetValueMultiSZ(const wchar_t* full_key_name,
                                 const TCHAR* value_name,
                                 const uint8_t* value,
                                 DWORD byte_count);

  // GETTERS

  // STATIC int32_t get
  static HRESULT GetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          DWORD* value);

  // STATIC int64_t get
  //
  // Note: if you are using time64 you should
  // likely use GetLimitedTimeValue (util.h) instead of this method.
  static HRESULT GetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          DWORD64* value);

  // STATIC float get
  static HRESULT GetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          float* value);

  // STATIC double get
  static HRESULT GetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          double* value);

  // STATIC string get
  // Note: the caller must free the return buffer for wchar_t* version
  static HRESULT GetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          wchar_t** value);
  static HRESULT GetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          std::wstring* value);

  // STATIC REG_MULTI_SZ get
  static HRESULT GetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          std::vector<std::wstring>* value);

  // STATIC get binary data - the caller must free the return buffer
  static HRESULT GetValue(const wchar_t* full_key_name,
                          const wchar_t* value_name,
                          uint8_t** value,
                          DWORD* byte_count);

  // Get type of a registry value
  static HRESULT GetValueType(const wchar_t* full_key_name,
                              const wchar_t* value_name,
                              DWORD* value_type);

  // delete a subkey of the current key (with no subkeys)
  HRESULT DeleteSubKey(const wchar_t* key_name);

  // recursively delete a sub key of the current key (and all its subkeys)
  HRESULT RecurseDeleteSubKey(const wchar_t* key_name);

  // STATIC version of delete key - handles nested keys also
  // delete a key and all its sub-keys recursively
  // Returns S_FALSE if key didn't exist, S_OK if deletion was successful,
  // and failure otherwise.
  static HRESULT DeleteKey(const wchar_t* full_key_name);

  // STATIC version of delete key
  // delete a key recursively or non-recursively
  // Returns S_FALSE if key didn't exist, S_OK if deletion was successful,
  // and failure otherwise.
  static HRESULT DeleteKey(const wchar_t* full_key_name, bool recursive);

  // delete the specified value
  HRESULT DeleteValue(const wchar_t* value_name);

  // STATIC version of delete value
  // Returns S_FALSE if key didn't exist, S_OK if deletion was successful,
  // and failure otherwise.
  static HRESULT DeleteValue(const wchar_t* full_key_name,
                             const wchar_t* value_name);

  // Peek inside (use a RegKey as a smart wrapper around a registry handle)
  HKEY key() { return h_key_; }

  // helper function to get the HKEY and the root key from a string
  // modifies the argument in place and returns the key name
  // e.g. HKLM\\Software\\Google\... returns HKLM, "Software\\Google\..."
  // Necessary for the static versions that use the full name of the reg key
  static HKEY GetRootKeyInfo(std::wstring* full_key_name);

  // Returns true if this key name is 'safe' for deletion (doesn't specify a key
  // root)
  static bool SafeKeyNameForDeletion(const wchar_t* key_name);

  // save the key and all of its subkeys and values to a file
  static HRESULT Save(const wchar_t* full_key_name, const wchar_t* file_name);

  // restore the key and all of its subkeys and values which are saved into a
  // file
  static HRESULT Restore(const wchar_t* full_key_name,
                         const wchar_t* file_name);

  // Is the key empty: having no sub-keys and values
  static bool IsKeyEmpty(const wchar_t* full_key_name);

 private:

  // helper function to get any value from the registry
  // used when the size of the data is unknown
  HRESULT GetValueHelper(const wchar_t* value_name,
                         DWORD* type,
                         uint8_t** value,
                         DWORD* byte_count) const;

  // helper function to get the parent key name and the subkey from a string
  // modifies the argument in place and returns the key name
  // Necessary for the static versions that use the full name of the reg key
  static std::wstring GetParentKeyInfo(std::wstring* key_name);

  // common SET Helper for the static case
  static HRESULT SetValueStaticHelper(const wchar_t* full_key_name,
                                      const wchar_t* value_name,
                                      DWORD type,
                                      LPVOID value,
                                      DWORD byte_count = 0);

  // common GET Helper for the static case
  static HRESULT GetValueStaticHelper(const wchar_t* full_key_name,
                                      const wchar_t* value_name,
                                      DWORD type,
                                      LPVOID value,
                                      DWORD* byte_count = NULL);

  // convert REG_MULTI_SZ bytes to string array
  static HRESULT MultiSZBytesToStringArray(const uint8_t* buffer,
                                           DWORD byte_count,
                                           std::vector<std::wstring>* value);

  // the HKEY for the current key
  HKEY h_key_;

  // for unittest
  friend void RegKeyHelperFunctionsTest();

  RTC_DISALLOW_COPY_AND_ASSIGN(RegKey);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_WIN32REGKEY_H_
