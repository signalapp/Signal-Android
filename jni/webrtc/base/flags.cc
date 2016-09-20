/*
 *  Copyright 2006 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>


#if defined(WEBRTC_WIN)
#include "webrtc/base/win32.h"
#include <shellapi.h>
#endif

#include "webrtc/base/flags.h"

namespace rtc {
// -----------------------------------------------------------------------------
// Implementation of Flag

Flag::Flag(const char* file, const char* name, const char* comment,
           Type type, void* variable, FlagValue default__)
    : file_(file),
      name_(name),
      comment_(comment),
      type_(type),
      variable_(reinterpret_cast<FlagValue*>(variable)),
      default_(default__) {
  FlagList::Register(this);
}


void Flag::SetToDefault() {
  // Note that we cannot simply do '*variable_ = default_;' since
  // flag variables are not really of type FlagValue and thus may
  // be smaller! The FlagValue union is simply 'overlayed' on top
  // of a flag variable for convenient access. Since union members
  // are guarantee to be aligned at the beginning, this works.
  switch (type_) {
    case Flag::BOOL:
      variable_->b = default_.b;
      return;
    case Flag::INT:
      variable_->i = default_.i;
      return;
    case Flag::FLOAT:
      variable_->f = default_.f;
      return;
    case Flag::STRING:
      variable_->s = default_.s;
      return;
  }
  FATAL() << "unreachable code";
}


static const char* Type2String(Flag::Type type) {
  switch (type) {
    case Flag::BOOL: return "bool";
    case Flag::INT: return "int";
    case Flag::FLOAT: return "float";
    case Flag::STRING: return "string";
  }
  FATAL() << "unreachable code";
}


static void PrintFlagValue(Flag::Type type, FlagValue* p) {
  switch (type) {
    case Flag::BOOL:
      printf("%s", (p->b ? "true" : "false"));
      return;
    case Flag::INT:
      printf("%d", p->i);
      return;
    case Flag::FLOAT:
      printf("%f", p->f);
      return;
    case Flag::STRING:
      printf("%s", p->s);
      return;
  }
  FATAL() << "unreachable code";
}


void Flag::Print(bool print_current_value) {
  printf("  --%s (%s)  type: %s  default: ", name_, comment_,
          Type2String(type_));
  PrintFlagValue(type_, &default_);
  if (print_current_value) {
    printf("  current value: ");
    PrintFlagValue(type_, variable_);
  }
  printf("\n");
}


// -----------------------------------------------------------------------------
// Implementation of FlagList

Flag* FlagList::list_ = NULL;


FlagList::FlagList() {
  list_ = NULL;
}

void FlagList::Print(const char* file, bool print_current_value) {
  // Since flag registration is likely by file (= C++ file),
  // we don't need to sort by file and still get grouped output.
  const char* current = NULL;
  for (Flag* f = list_; f != NULL; f = f->next()) {
    if (file == NULL || file == f->file()) {
      if (current != f->file()) {
        printf("Flags from %s:\n", f->file());
        current = f->file();
      }
      f->Print(print_current_value);
    }
  }
}


Flag* FlagList::Lookup(const char* name) {
  Flag* f = list_;
  while (f != NULL && strcmp(name, f->name()) != 0)
    f = f->next();
  return f;
}


void FlagList::SplitArgument(const char* arg,
                             char* buffer, int buffer_size,
                             const char** name, const char** value,
                             bool* is_bool) {
  *name = NULL;
  *value = NULL;
  *is_bool = false;

  if (*arg == '-') {
    // find the begin of the flag name
    arg++;  // remove 1st '-'
    if (*arg == '-')
      arg++;  // remove 2nd '-'
    if (arg[0] == 'n' && arg[1] == 'o') {
      arg += 2;  // remove "no"
      *is_bool = true;
    }
    *name = arg;

    // find the end of the flag name
    while (*arg != '\0' && *arg != '=')
      arg++;

    // get the value if any
    if (*arg == '=') {
      // make a copy so we can NUL-terminate flag name
      int n = static_cast<int>(arg - *name);
      RTC_CHECK_LT(n, buffer_size);
      memcpy(buffer, *name, n * sizeof(char));
      buffer[n] = '\0';
      *name = buffer;
      // get the value
      *value = arg + 1;
    }
  }
}


int FlagList::SetFlagsFromCommandLine(int* argc, const char** argv,
                                      bool remove_flags) {
  // parse arguments
  for (int i = 1; i < *argc; /* see below */) {
    int j = i;  // j > 0
    const char* arg = argv[i++];

    // split arg into flag components
    char buffer[1024];
    const char* name;
    const char* value;
    bool is_bool;
    SplitArgument(arg, buffer, sizeof buffer, &name, &value, &is_bool);

    if (name != NULL) {
      // lookup the flag
      Flag* flag = Lookup(name);
      if (flag == NULL) {
        fprintf(stderr, "Error: unrecognized flag %s\n", arg);
        return j;
      }

      // if we still need a flag value, use the next argument if available
      if (flag->type() != Flag::BOOL && value == NULL) {
        if (i < *argc) {
          value = argv[i++];
        } else {
          fprintf(stderr, "Error: missing value for flag %s of type %s\n",
            arg, Type2String(flag->type()));
          return j;
        }
      }

      // set the flag
      char empty[] = { '\0' };
      char* endp = empty;
      switch (flag->type()) {
        case Flag::BOOL:
          *flag->bool_variable() = !is_bool;
          break;
        case Flag::INT:
          *flag->int_variable() = strtol(value, &endp, 10);
          break;
        case Flag::FLOAT:
          *flag->float_variable() = strtod(value, &endp);
          break;
        case Flag::STRING:
          *flag->string_variable() = value;
          break;
      }

      // handle errors
      if ((flag->type() == Flag::BOOL && value != NULL) ||
          (flag->type() != Flag::BOOL && is_bool) ||
          *endp != '\0') {
        fprintf(stderr, "Error: illegal value for flag %s of type %s\n",
          arg, Type2String(flag->type()));
        return j;
      }

      // remove the flag & value from the command
      if (remove_flags)
        while (j < i)
          argv[j++] = NULL;
    }
  }

  // shrink the argument list
  if (remove_flags) {
    int j = 1;
    for (int i = 1; i < *argc; i++) {
      if (argv[i] != NULL)
        argv[j++] = argv[i];
    }
    *argc = j;
  }

  // parsed all flags successfully
  return 0;
}

void FlagList::Register(Flag* flag) {
  assert(flag != NULL && strlen(flag->name()) > 0);
  RTC_CHECK(!Lookup(flag->name())) << "flag " << flag->name()
                                   << " declared twice";
  flag->next_ = list_;
  list_ = flag;
}

#if defined(WEBRTC_WIN)
WindowsCommandLineArguments::WindowsCommandLineArguments() {
  // start by getting the command line.
  LPTSTR command_line = ::GetCommandLine();
   // now, convert it to a list of wide char strings.
  LPWSTR *wide_argv = ::CommandLineToArgvW(command_line, &argc_);
  // now allocate an array big enough to hold that many string pointers.
  argv_ = new char*[argc_];

  // iterate over the returned wide strings;
  for(int i = 0; i < argc_; ++i) {
    std::string s = rtc::ToUtf8(wide_argv[i], wcslen(wide_argv[i]));
    char *buffer = new char[s.length() + 1];
    rtc::strcpyn(buffer, s.length() + 1, s.c_str());

    // make sure the argv array has the right string at this point.
    argv_[i] = buffer;
  }
  LocalFree(wide_argv);
}

WindowsCommandLineArguments::~WindowsCommandLineArguments() {
  // need to free each string in the array, and then the array.
  for(int i = 0; i < argc_; i++) {
    delete[] argv_[i];
  }

  delete[] argv_;
}
#endif  // WEBRTC_WIN

}  // namespace rtc
