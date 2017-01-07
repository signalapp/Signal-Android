/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/latebindingsymboltable.h"

#if defined(WEBRTC_POSIX)
#include <dlfcn.h>
#endif

#include "webrtc/base/logging.h"

namespace rtc {

#if defined(WEBRTC_POSIX)
static const DllHandle kInvalidDllHandle = NULL;
#else
#error Not implemented
#endif

static const char *GetDllError() {
#if defined(WEBRTC_POSIX)
  const char *err = dlerror();
  if (err) {
    return err;
  } else {
    return "No error";
  }
#else
#error Not implemented
#endif
}

static bool LoadSymbol(DllHandle handle,
                       const char *symbol_name,
                       void **symbol) {
#if defined(WEBRTC_POSIX)
  *symbol = dlsym(handle, symbol_name);
  const char *err = dlerror();
  if (err) {
    LOG(LS_ERROR) << "Error loading symbol " << symbol_name << ": " << err;
    return false;
  } else if (!*symbol) {
    // ELF allows for symbols to be NULL, but that should never happen for our
    // usage.
    LOG(LS_ERROR) << "Symbol " << symbol_name << " is NULL";
    return false;
  }
  return true;
#else
#error Not implemented
#endif
}

LateBindingSymbolTable::LateBindingSymbolTable(const TableInfo *info,
    void **table)
    : info_(info),
      table_(table),
      handle_(kInvalidDllHandle),
      undefined_symbols_(false) {
  ClearSymbols();
}

LateBindingSymbolTable::~LateBindingSymbolTable() {
  Unload();
}

bool LateBindingSymbolTable::IsLoaded() const {
  return handle_ != kInvalidDllHandle;
}

bool LateBindingSymbolTable::Load() {
  ASSERT(info_->dll_name != NULL);
  return LoadFromPath(info_->dll_name);
}

bool LateBindingSymbolTable::LoadFromPath(const char *dll_path) {
  if (IsLoaded()) {
    return true;
  }
  if (undefined_symbols_) {
    // We do not attempt to load again because repeated attempts are not
    // likely to succeed and DLL loading is costly.
    LOG(LS_ERROR) << "We know there are undefined symbols";
    return false;
  }

#if defined(WEBRTC_POSIX)
  handle_ = dlopen(dll_path,
                   // RTLD_NOW front-loads symbol resolution so that errors are
                   // caught early instead of causing a process abort later.
                   // RTLD_LOCAL prevents other modules from automatically
                   // seeing symbol definitions in the newly-loaded tree. This
                   // is necessary for same-named symbols in different ABI
                   // versions of the same library to not explode.
                   RTLD_NOW|RTLD_LOCAL
#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID) && defined(RTLD_DEEPBIND)
                   // RTLD_DEEPBIND makes symbol dependencies in the
                   // newly-loaded tree prefer to resolve to definitions within
                   // that tree (the default on OS X). This is necessary for
                   // same-named symbols in different ABI versions of the same
                   // library to not explode.
                   |RTLD_DEEPBIND
#endif
                   );  // NOLINT
#else
#error Not implemented
#endif

  if (handle_ == kInvalidDllHandle) {
    LOG(LS_WARNING) << "Can't load " << dll_path << ": "
                    << GetDllError();
    return false;
  }
#if defined(WEBRTC_POSIX)
  // Clear any old errors.
  dlerror();
#endif
  for (int i = 0; i < info_->num_symbols; ++i) {
    if (!LoadSymbol(handle_, info_->symbol_names[i], &table_[i])) {
      undefined_symbols_ = true;
      Unload();
      return false;
    }
  }
  return true;
}

void LateBindingSymbolTable::Unload() {
  if (!IsLoaded()) {
    return;
  }

#if defined(WEBRTC_POSIX)
  if (dlclose(handle_) != 0) {
    LOG(LS_ERROR) << GetDllError();
  }
#else
#error Not implemented
#endif

  handle_ = kInvalidDllHandle;
  ClearSymbols();
}

void LateBindingSymbolTable::ClearSymbols() {
  memset(table_, 0, sizeof(void *) * info_->num_symbols);
}

}  // namespace rtc
