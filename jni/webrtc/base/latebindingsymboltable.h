/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_LATEBINDINGSYMBOLTABLE_H_
#define WEBRTC_BASE_LATEBINDINGSYMBOLTABLE_H_

#include <string.h>

#include "webrtc/base/common.h"
#include "webrtc/base/constructormagic.h"

namespace rtc {

#if defined(WEBRTC_POSIX)
typedef void *DllHandle;
#else
#error Not implemented for this platform
#endif

// This is the base class for "symbol table" classes to simplify the dynamic
// loading of symbols from DLLs. Currently the implementation only supports
// Linux and OS X, and pure C symbols (or extern "C" symbols that wrap C++
// functions).  Sub-classes for specific DLLs are generated via the "supermacro"
// files latebindingsymboltable.h.def and latebindingsymboltable.cc.def. See
// talk/sound/pulseaudiosymboltable.(h|cc) for an example.
class LateBindingSymbolTable {
 public:
  struct TableInfo {
    const char *dll_name;
    int num_symbols;
    // Array of size num_symbols.
    const char *const *symbol_names;
  };

  LateBindingSymbolTable(const TableInfo *info, void **table);
  ~LateBindingSymbolTable();

  bool IsLoaded() const;
  // Loads the DLL and the symbol table. Returns true iff the DLL and symbol
  // table loaded successfully.
  bool Load();
  // Like load, but allows overriding the dll path for when the dll path is
  // dynamic.
  bool LoadFromPath(const char *dll_path);
  void Unload();

  // Gets the raw OS handle to the DLL. Be careful what you do with it.
  DllHandle GetDllHandle() const { return handle_; }

 private:
  void ClearSymbols();

  const TableInfo *info_;
  void **table_;
  DllHandle handle_;
  bool undefined_symbols_;

  RTC_DISALLOW_COPY_AND_ASSIGN(LateBindingSymbolTable);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_LATEBINDINGSYMBOLTABLE_H_
