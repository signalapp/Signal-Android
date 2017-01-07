/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#if defined(WEBRTC_LINUX)
#include "webrtc/base/linux.h"

#include <ctype.h>

#include <errno.h>
#include <sys/utsname.h>
#include <sys/wait.h>

#include <cstdio>
#include <set>

#include "webrtc/base/stringencode.h"

namespace rtc {

static const char kCpuInfoFile[] = "/proc/cpuinfo";
static const char kCpuMaxFreqFile[] =
    "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq";

ProcCpuInfo::ProcCpuInfo() {
}

ProcCpuInfo::~ProcCpuInfo() {
}

bool ProcCpuInfo::LoadFromSystem() {
  ConfigParser procfs;
  if (!procfs.Open(kCpuInfoFile)) {
    return false;
  }
  return procfs.Parse(&sections_);
};

bool ProcCpuInfo::GetSectionCount(size_t* count) {
  if (sections_.empty()) {
    return false;
  }
  if (count) {
    *count = sections_.size();
  }
  return true;
}

bool ProcCpuInfo::GetNumCpus(int* num) {
  if (sections_.empty()) {
    return false;
  }
  int total_cpus = 0;
#if defined(__arm__)
  // Count the number of blocks that have a "processor" key defined. On ARM,
  // there may be extra blocks of information that aren't per-processor.
  size_t section_count = sections_.size();
  for (size_t i = 0; i < section_count; ++i) {
    int processor_id;
    if (GetSectionIntValue(i, "processor", &processor_id)) {
      ++total_cpus;
    }
  }
  // Single core ARM systems don't include "processor" keys at all, so return
  // that we have a single core if we didn't find any explicitly above.
  if (total_cpus == 0) {
    total_cpus = 1;
  }
#else
  // On X86, there is exactly one info section per processor.
  total_cpus = static_cast<int>(sections_.size());
#endif
  if (num) {
    *num = total_cpus;
  }
  return true;
}

bool ProcCpuInfo::GetNumPhysicalCpus(int* num) {
  if (sections_.empty()) {
    return false;
  }
  // TODO: /proc/cpuinfo only reports cores that are currently
  // _online_, so this may underreport the number of physical cores.
#if defined(__arm__)
  // ARM (currently) has no hyperthreading, so just return the same value
  // as GetNumCpus.
  return GetNumCpus(num);
#else
  int total_cores = 0;
  std::set<int> physical_ids;
  size_t section_count = sections_.size();
  for (size_t i = 0; i < section_count; ++i) {
    int physical_id;
    int cores;
    // Count the cores for the physical id only if we have not counted the id.
    if (GetSectionIntValue(i, "physical id", &physical_id) &&
        GetSectionIntValue(i, "cpu cores", &cores) &&
        physical_ids.find(physical_id) == physical_ids.end()) {
      physical_ids.insert(physical_id);
      total_cores += cores;
    }
  }

  if (num) {
    *num = total_cores;
  }
  return true;
#endif
}

bool ProcCpuInfo::GetCpuFamily(int* id) {
  int cpu_family = 0;

#if defined(__arm__)
  // On some ARM platforms, there is no 'cpu family' in '/proc/cpuinfo'. But
  // there is 'CPU Architecture' which can be used as 'cpu family'.
  // See http://en.wikipedia.org/wiki/ARM_architecture for a good list of
  // ARM cpu families, architectures, and their mappings.
  // There may be multiple sessions that aren't per-processor. We need to scan
  // through each session until we find the first 'CPU architecture'.
  size_t section_count = sections_.size();
  for (size_t i = 0; i < section_count; ++i) {
    if (GetSectionIntValue(i, "CPU architecture", &cpu_family)) {
      // We returns the first one (if there are multiple entries).
      break;
    };
  }
#else
  GetSectionIntValue(0, "cpu family", &cpu_family);
#endif
  if (id) {
    *id = cpu_family;
  }
  return true;
}

bool ProcCpuInfo::GetSectionStringValue(size_t section_num,
                                        const std::string& key,
                                        std::string* result) {
  if (section_num >= sections_.size()) {
    return false;
  }
  ConfigParser::SimpleMap::iterator iter = sections_[section_num].find(key);
  if (iter == sections_[section_num].end()) {
    return false;
  }
  *result = iter->second;
  return true;
}

bool ProcCpuInfo::GetSectionIntValue(size_t section_num,
                                     const std::string& key,
                                     int* result) {
  if (section_num >= sections_.size()) {
    return false;
  }
  ConfigParser::SimpleMap::iterator iter = sections_[section_num].find(key);
  if (iter == sections_[section_num].end()) {
    return false;
  }
  return FromString(iter->second, result);
}

ConfigParser::ConfigParser() {}

ConfigParser::~ConfigParser() {}

bool ConfigParser::Open(const std::string& filename) {
  FileStream* fs = new FileStream();
  if (!fs->Open(filename, "r", NULL)) {
    return false;
  }
  instream_.reset(fs);
  return true;
}

void ConfigParser::Attach(StreamInterface* stream) {
  instream_.reset(stream);
}

bool ConfigParser::Parse(MapVector* key_val_pairs) {
  // Parses the file and places the found key-value pairs into key_val_pairs.
  SimpleMap section;
  while (ParseSection(&section)) {
    key_val_pairs->push_back(section);
    section.clear();
  }
  return (!key_val_pairs->empty());
}

bool ConfigParser::ParseSection(SimpleMap* key_val_pair) {
  // Parses the next section in the filestream and places the found key-value
  // pairs into key_val_pair.
  std::string key, value;
  while (ParseLine(&key, &value)) {
    (*key_val_pair)[key] = value;
  }
  return (!key_val_pair->empty());
}

bool ConfigParser::ParseLine(std::string* key, std::string* value) {
  // Parses the next line in the filestream and places the found key-value
  // pair into key and val.
  std::string line;
  if ((instream_->ReadLine(&line)) == SR_EOS) {
    return false;
  }
  std::vector<std::string> tokens;
  if (2 != split(line, ':', &tokens)) {
    return false;
  }
  // Removes whitespace at the end of Key name
  size_t pos = tokens[0].length() - 1;
  while ((pos > 0) && isspace(tokens[0][pos])) {
    pos--;
  }
  tokens[0].erase(pos + 1);
  // Removes whitespace at the start of value
  pos = 0;
  while (pos < tokens[1].length() && isspace(tokens[1][pos])) {
    pos++;
  }
  tokens[1].erase(0, pos);
  *key = tokens[0];
  *value = tokens[1];
  return true;
}

std::string ReadLinuxUname() {
  struct utsname buf;
  if (uname(&buf) < 0) {
    LOG_ERR(LS_ERROR) << "Can't call uname()";
    return std::string();
  }
  std::ostringstream sstr;
  sstr << buf.sysname << " "
       << buf.release << " "
       << buf.version << " "
       << buf.machine;
  return sstr.str();
}

int ReadCpuMaxFreq() {
  FileStream fs;
  std::string str;
  int freq = -1;
  if (!fs.Open(kCpuMaxFreqFile, "r", NULL) ||
      SR_SUCCESS != fs.ReadLine(&str) ||
      !FromString(str, &freq)) {
    return -1;
  }
  return freq;
}

}  // namespace rtc

#endif  // defined(WEBRTC_LINUX)
