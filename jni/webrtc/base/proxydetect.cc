/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/proxydetect.h"

#if defined(WEBRTC_WIN)
#include "webrtc/base/win32.h"
#include <shlobj.h>
#endif  // WEBRTC_WIN

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include <SystemConfiguration/SystemConfiguration.h>
#include <CoreFoundation/CoreFoundation.h>
#include <CoreServices/CoreServices.h>
#include <Security/Security.h>
#include "macconversion.h"
#endif

#ifdef WEBRTC_IOS
#include <CFNetwork/CFNetwork.h>
#include "macconversion.h"
#endif

#include <map>
#include <memory>

#include "webrtc/base/arraysize.h"
#include "webrtc/base/fileutils.h"
#include "webrtc/base/httpcommon.h"
#include "webrtc/base/httpcommon-inl.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/stringutils.h"

#if defined(WEBRTC_WIN)
#define _TRY_WINHTTP 1
#define _TRY_JSPROXY 0
#define _TRY_WM_FINDPROXY 0
#define _TRY_IE_LAN_SETTINGS 1
#endif  // WEBRTC_WIN

// For all platforms try Firefox.
#define _TRY_FIREFOX 1

// Use profiles.ini to find the correct profile for this user.
// If not set, we'll just look for the default one.
#define USE_FIREFOX_PROFILES_INI 1

static const size_t kMaxLineLength = 1024;
static const char kFirefoxPattern[] = "Firefox";
static const char kInternetExplorerPattern[] = "MSIE";

struct StringMap {
 public:
  void Add(const char * name, const char * value) { map_[name] = value; }
  const std::string& Get(const char * name, const char * def = "") const {
    std::map<std::string, std::string>::const_iterator it =
        map_.find(name);
    if (it != map_.end())
      return it->second;
    def_ = def;
    return def_;
  }
  bool IsSet(const char * name) const {
    return (map_.find(name) != map_.end());
  }
 private:
  std::map<std::string, std::string> map_;
  mutable std::string def_;
};

enum UserAgent {
  UA_FIREFOX,
  UA_INTERNETEXPLORER,
  UA_OTHER,
  UA_UNKNOWN
};

#if _TRY_WINHTTP
//#include <winhttp.h>
// Note: From winhttp.h

const char WINHTTP[] = "winhttp";

typedef LPVOID HINTERNET;

typedef struct {
  DWORD  dwAccessType;      // see WINHTTP_ACCESS_* types below
  LPWSTR lpszProxy;         // proxy server list
  LPWSTR lpszProxyBypass;   // proxy bypass list
} WINHTTP_PROXY_INFO, * LPWINHTTP_PROXY_INFO;

typedef struct {
  DWORD   dwFlags;
  DWORD   dwAutoDetectFlags;
  LPCWSTR lpszAutoConfigUrl;
  LPVOID  lpvReserved;
  DWORD   dwReserved;
  BOOL    fAutoLogonIfChallenged;
} WINHTTP_AUTOPROXY_OPTIONS;

typedef struct {
  BOOL    fAutoDetect;
  LPWSTR  lpszAutoConfigUrl;
  LPWSTR  lpszProxy;
  LPWSTR  lpszProxyBypass;
} WINHTTP_CURRENT_USER_IE_PROXY_CONFIG;

extern "C" {
  typedef HINTERNET (WINAPI * pfnWinHttpOpen)
      (
          IN LPCWSTR pwszUserAgent,
          IN DWORD   dwAccessType,
          IN LPCWSTR pwszProxyName   OPTIONAL,
          IN LPCWSTR pwszProxyBypass OPTIONAL,
          IN DWORD   dwFlags
          );
  typedef BOOL (STDAPICALLTYPE * pfnWinHttpCloseHandle)
      (
          IN HINTERNET hInternet
          );
  typedef BOOL (STDAPICALLTYPE * pfnWinHttpGetProxyForUrl)
      (
          IN  HINTERNET                   hSession,
          IN  LPCWSTR                     lpcwszUrl,
          IN  WINHTTP_AUTOPROXY_OPTIONS * pAutoProxyOptions,
          OUT WINHTTP_PROXY_INFO *        pProxyInfo
          );
  typedef BOOL (STDAPICALLTYPE * pfnWinHttpGetIEProxyConfig)
      (
          IN OUT WINHTTP_CURRENT_USER_IE_PROXY_CONFIG * pProxyConfig
          );

} // extern "C"

#define WINHTTP_AUTOPROXY_AUTO_DETECT           0x00000001
#define WINHTTP_AUTOPROXY_CONFIG_URL            0x00000002
#define WINHTTP_AUTOPROXY_RUN_INPROCESS         0x00010000
#define WINHTTP_AUTOPROXY_RUN_OUTPROCESS_ONLY   0x00020000
#define WINHTTP_AUTO_DETECT_TYPE_DHCP           0x00000001
#define WINHTTP_AUTO_DETECT_TYPE_DNS_A          0x00000002
#define WINHTTP_ACCESS_TYPE_DEFAULT_PROXY               0
#define WINHTTP_ACCESS_TYPE_NO_PROXY                    1
#define WINHTTP_ACCESS_TYPE_NAMED_PROXY                 3
#define WINHTTP_NO_PROXY_NAME     NULL
#define WINHTTP_NO_PROXY_BYPASS   NULL

#endif // _TRY_WINHTTP

#if _TRY_JSPROXY
extern "C" {
  typedef BOOL (STDAPICALLTYPE * pfnInternetGetProxyInfo)
      (
          LPCSTR lpszUrl,
          DWORD dwUrlLength,
          LPSTR lpszUrlHostName,
          DWORD dwUrlHostNameLength,
          LPSTR * lplpszProxyHostName,
          LPDWORD lpdwProxyHostNameLength
          );
} // extern "C"
#endif // _TRY_JSPROXY

#if _TRY_WM_FINDPROXY
#include <comutil.h>
#include <wmnetsourcecreator.h>
#include <wmsinternaladminnetsource.h>
#endif // _TRY_WM_FINDPROXY

#if _TRY_IE_LAN_SETTINGS
#include <wininet.h>
#include <string>
#endif // _TRY_IE_LAN_SETTINGS

namespace rtc {

//////////////////////////////////////////////////////////////////////
// Utility Functions
//////////////////////////////////////////////////////////////////////

#if defined(WEBRTC_WIN)
#ifdef _UNICODE

typedef std::wstring tstring;
std::string Utf8String(const tstring& str) { return ToUtf8(str); }

#else  // !_UNICODE

typedef std::string tstring;
std::string Utf8String(const tstring& str) { return str; }

#endif  // !_UNICODE
#endif  // WEBRTC_WIN

bool ProxyItemMatch(const Url<char>& url, char * item, size_t len) {
  // hostname:443
  if (char * port = ::strchr(item, ':')) {
    *port++ = '\0';
    if (url.port() != atol(port)) {
      return false;
    }
  }

  // A.B.C.D or A.B.C.D/24
  int a, b, c, d, m;
  int match = sscanf(item, "%d.%d.%d.%d/%d", &a, &b, &c, &d, &m);
  if (match >= 4) {
    uint32_t ip = ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((c & 0xFF) << 8) |
                  (d & 0xFF);
    if ((match < 5) || (m > 32))
      m = 32;
    else if (m < 0)
      m = 0;
    uint32_t mask = (m == 0) ? 0 : (~0UL) << (32 - m);
    SocketAddress addr(url.host(), 0);
    // TODO: Support IPv6 proxyitems. This code block is IPv4 only anyway.
    return !addr.IsUnresolvedIP() &&
        ((addr.ipaddr().v4AddressAsHostOrderInteger() & mask) == (ip & mask));
  }

  // .foo.com
  if (*item == '.') {
    size_t hostlen = url.host().length();
    return (hostlen > len)
        && (stricmp(url.host().c_str() + (hostlen - len), item) == 0);
  }

  // localhost or www.*.com
  if (!string_match(url.host().c_str(), item))
    return false;

  return true;
}

bool ProxyListMatch(const Url<char>& url, const std::string& proxy_list,
                    char sep) {
  const size_t BUFSIZE = 256;
  char buffer[BUFSIZE];
  const char* list = proxy_list.c_str();
  while (*list) {
    // Remove leading space
    if (isspace(*list)) {
      ++list;
      continue;
    }
    // Break on separator
    size_t len;
    const char * start = list;
    if (const char * end = ::strchr(list, sep)) {
      len = (end - list);
      list += len + 1;
    } else {
      len = strlen(list);
      list += len;
    }
    // Remove trailing space
    while ((len > 0) && isspace(start[len-1]))
      --len;
    // Check for oversized entry
    if (len >= BUFSIZE)
      continue;
    memcpy(buffer, start, len);
    buffer[len] = 0;
    if (!ProxyItemMatch(url, buffer, len))
      continue;
    return true;
  }
  return false;
}

bool Better(ProxyType lhs, const ProxyType rhs) {
  // PROXY_NONE, PROXY_HTTPS, PROXY_SOCKS5, PROXY_UNKNOWN
  const int PROXY_VALUE[5] = { 0, 2, 3, 1 };
  return (PROXY_VALUE[lhs] > PROXY_VALUE[rhs]);
}

bool ParseProxy(const std::string& saddress, ProxyInfo* proxy) {
  const size_t kMaxAddressLength = 1024;
  // Allow semicolon, space, or tab as an address separator
  const char* const kAddressSeparator = " ;\t";

  ProxyType ptype;
  std::string host;
  uint16_t port;

  const char* address = saddress.c_str();
  while (*address) {
    size_t len;
    const char * start = address;
    if (const char * sep = strchr(address, kAddressSeparator)) {
      len = (sep - address);
      address += len + 1;
      while (*address != '\0' && ::strchr(kAddressSeparator, *address)) {
        address += 1;
      }
    } else {
      len = strlen(address);
      address += len;
    }

    if (len > kMaxAddressLength - 1) {
      LOG(LS_WARNING) << "Proxy address too long [" << start << "]";
      continue;
    }

    char buffer[kMaxAddressLength];
    memcpy(buffer, start, len);
    buffer[len] = 0;

    char * colon = ::strchr(buffer, ':');
    if (!colon) {
      LOG(LS_WARNING) << "Proxy address without port [" << buffer << "]";
      continue;
    }

    *colon = 0;
    char * endptr;
    port = static_cast<uint16_t>(strtol(colon + 1, &endptr, 0));
    if (*endptr != 0) {
      LOG(LS_WARNING) << "Proxy address with invalid port [" << buffer << "]";
      continue;
    }

    if (char * equals = ::strchr(buffer, '=')) {
      *equals = 0;
      host = equals + 1;
      if (_stricmp(buffer, "socks") == 0) {
        ptype = PROXY_SOCKS5;
      } else if (_stricmp(buffer, "https") == 0) {
        ptype = PROXY_HTTPS;
      } else {
        LOG(LS_WARNING) << "Proxy address with unknown protocol ["
                        << buffer << "]";
        ptype = PROXY_UNKNOWN;
      }
    } else {
      host = buffer;
      ptype = PROXY_UNKNOWN;
    }

    if (Better(ptype, proxy->type)) {
      proxy->type = ptype;
      proxy->address.SetIP(host);
      proxy->address.SetPort(port);
    }
  }

  return proxy->type != PROXY_NONE;
}

UserAgent GetAgent(const char* agent) {
  if (agent) {
    std::string agent_str(agent);
    if (agent_str.find(kFirefoxPattern) != std::string::npos) {
      return UA_FIREFOX;
    } else if (agent_str.find(kInternetExplorerPattern) != std::string::npos) {
      return UA_INTERNETEXPLORER;
    } else if (agent_str.empty()) {
      return UA_UNKNOWN;
    }
  }
  return UA_OTHER;
}

bool EndsWith(const std::string& a, const std::string& b) {
  if (b.size() > a.size()) {
    return false;
  }
  int result = a.compare(a.size() - b.size(), b.size(), b);
  return result == 0;
}

bool GetFirefoxProfilePath(Pathname* path) {
#if defined(WEBRTC_WIN)
  wchar_t w_path[MAX_PATH];
  if (SHGetFolderPath(0, CSIDL_APPDATA, 0, SHGFP_TYPE_CURRENT, w_path) !=
      S_OK) {
    LOG(LS_ERROR) << "SHGetFolderPath failed";
    return false;
  }
  path->SetFolder(ToUtf8(w_path, wcslen(w_path)));
  path->AppendFolder("Mozilla");
  path->AppendFolder("Firefox");
#elif defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
  FSRef fr;
  if (0 != FSFindFolder(kUserDomain, kApplicationSupportFolderType,
                        kCreateFolder, &fr)) {
    LOG(LS_ERROR) << "FSFindFolder failed";
    return false;
  }
  char buffer[NAME_MAX + 1];
  if (0 != FSRefMakePath(&fr, reinterpret_cast<uint8_t*>(buffer),
                         arraysize(buffer))) {
    LOG(LS_ERROR) << "FSRefMakePath failed";
    return false;
  }
  path->SetFolder(std::string(buffer));
  path->AppendFolder("Firefox");
#else
  char* user_home = getenv("HOME");
  if (user_home == NULL) {
    return false;
  }
  path->SetFolder(std::string(user_home));
  path->AppendFolder(".mozilla");
  path->AppendFolder("firefox");
#endif  // WEBRTC_WIN
  return true;
}

bool GetDefaultFirefoxProfile(Pathname* profile_path) {
  ASSERT(NULL != profile_path);
  Pathname path;
  if (!GetFirefoxProfilePath(&path)) {
    return false;
  }

#if USE_FIREFOX_PROFILES_INI
  // [Profile0]
  // Name=default
  // IsRelative=1
  // Path=Profiles/2de53ejb.default
  // Default=1

  // Note: we are looking for the first entry with "Default=1", or the last
  // entry in the file
  path.SetFilename("profiles.ini");
  std::unique_ptr<FileStream> fs(Filesystem::OpenFile(path, "r"));
  if (!fs) {
    return false;
  }
  Pathname candidate;
  bool relative = true;
  std::string line;
  while (fs->ReadLine(&line) == SR_SUCCESS) {
    if (line.length() == 0) {
      continue;
    }
    if (line.at(0) == '[') {
      relative = true;
      candidate.clear();
    } else if (line.find("IsRelative=") == 0 &&
               line.length() >= 12) {
      // TODO: The initial Linux public launch revealed a fairly
      // high number of machines where IsRelative= did not have anything after
      // it. Perhaps that is legal profiles.ini syntax?
      relative = (line.at(11) != '0');
    } else if (line.find("Path=") == 0 &&
               line.length() >= 6) {
      if (relative) {
        candidate = path;
      } else {
        candidate.clear();
      }
      candidate.AppendFolder(line.substr(5));
    } else if (line.find("Default=") == 0 &&
               line.length() >= 9) {
      if ((line.at(8) != '0') && !candidate.empty()) {
        break;
      }
    }
  }
  fs->Close();
  if (candidate.empty()) {
    return false;
  }
  profile_path->SetPathname(candidate.pathname());

#else // !USE_FIREFOX_PROFILES_INI
  path.AppendFolder("Profiles");
  DirectoryIterator* it = Filesystem::IterateDirectory();
  it->Iterate(path);
  std::string extension(".default");
  while (!EndsWith(it->Name(), extension)) {
    if (!it->Next()) {
      return false;
    }
  }

  profile_path->SetPathname(path);
  profile->AppendFolder("Profiles");
  profile->AppendFolder(it->Name());
  delete it;

#endif // !USE_FIREFOX_PROFILES_INI

  return true;
}

bool ReadFirefoxPrefs(const Pathname& filename,
                      const char * prefix,
                      StringMap* settings) {
  std::unique_ptr<FileStream> fs(Filesystem::OpenFile(filename, "r"));
  if (!fs) {
    LOG(LS_ERROR) << "Failed to open file: " << filename.pathname();
    return false;
  }

  std::string line;
  while (fs->ReadLine(&line) == SR_SUCCESS) {
    size_t prefix_len = strlen(prefix);

    // Skip blank lines and too long lines.
    if ((line.length() == 0) || (line.length() > kMaxLineLength)
        || (line.at(0) == '#') || line.compare(0, 2, "/*") == 0
        || line.compare(0, 2, " *") == 0) {
      continue;
    }

    char buffer[kMaxLineLength];
    strcpyn(buffer, sizeof(buffer), line.c_str());
    int nstart = 0, nend = 0, vstart = 0, vend = 0;
    sscanf(buffer, "user_pref(\"%n%*[^\"]%n\", %n%*[^)]%n);",
           &nstart, &nend, &vstart, &vend);
    if (vend > 0) {
      char* name = buffer + nstart;
      name[nend - nstart] = 0;
      if ((vend - vstart >= 2) && (buffer[vstart] == '"')) {
        vstart += 1;
        vend -= 1;
      }
      char* value = buffer + vstart;
      value[vend - vstart] = 0;
      if ((strncmp(name, prefix, prefix_len) == 0) && *value) {
        settings->Add(name + prefix_len, value);
      }
    } else {
      LOG_F(LS_WARNING) << "Unparsed pref [" << buffer << "]";
    }
  }
  fs->Close();
  return true;
}

bool GetFirefoxProxySettings(const char* url, ProxyInfo* proxy) {
  Url<char> purl(url);
  Pathname path;
  bool success = false;
  if (GetDefaultFirefoxProfile(&path)) {
    StringMap settings;
    path.SetFilename("prefs.js");
    if (ReadFirefoxPrefs(path, "network.proxy.", &settings)) {
      success = true;
      proxy->bypass_list =
          settings.Get("no_proxies_on", "localhost, 127.0.0.1");
      if (settings.Get("type") == "1") {
        // User has manually specified a proxy, try to figure out what
        // type it is.
        if (ProxyListMatch(purl, proxy->bypass_list.c_str(), ',')) {
          // Our url is in the list of url's to bypass proxy.
        } else if (settings.Get("share_proxy_settings") == "true") {
          proxy->type = PROXY_UNKNOWN;
          proxy->address.SetIP(settings.Get("http"));
          proxy->address.SetPort(atoi(settings.Get("http_port").c_str()));
        } else if (settings.IsSet("socks")) {
          proxy->type = PROXY_SOCKS5;
          proxy->address.SetIP(settings.Get("socks"));
          proxy->address.SetPort(atoi(settings.Get("socks_port").c_str()));
        } else if (settings.IsSet("ssl")) {
          proxy->type = PROXY_HTTPS;
          proxy->address.SetIP(settings.Get("ssl"));
          proxy->address.SetPort(atoi(settings.Get("ssl_port").c_str()));
        } else if (settings.IsSet("http")) {
          proxy->type = PROXY_HTTPS;
          proxy->address.SetIP(settings.Get("http"));
          proxy->address.SetPort(atoi(settings.Get("http_port").c_str()));
        }
      } else if (settings.Get("type") == "2") {
        // Browser is configured to get proxy settings from a given url.
        proxy->autoconfig_url = settings.Get("autoconfig_url").c_str();
      } else if (settings.Get("type") == "4") {
        // Browser is configured to auto detect proxy config.
        proxy->autodetect = true;
      } else {
        // No proxy set.
      }
    }
  }
  return success;
}

#if defined(WEBRTC_WIN)  // Windows specific implementation for reading Internet
              // Explorer proxy settings.

void LogGetProxyFault() {
  LOG_GLEM(LERROR, WINHTTP) << "WinHttpGetProxyForUrl faulted!!";
}

BOOL MyWinHttpGetProxyForUrl(pfnWinHttpGetProxyForUrl pWHGPFU,
                             HINTERNET hWinHttp, LPCWSTR url,
                             WINHTTP_AUTOPROXY_OPTIONS *options,
                             WINHTTP_PROXY_INFO *info) {
  // WinHttpGetProxyForUrl() can call plugins which can crash.
  // In the case of McAfee scriptproxy.dll, it does crash in
  // older versions. Try to catch crashes here and treat as an
  // error.
  BOOL success = FALSE;

#if (_HAS_EXCEPTIONS == 0)
  __try {
    success = pWHGPFU(hWinHttp, url, options, info);
  } __except(EXCEPTION_EXECUTE_HANDLER) {
    // This is a separate function to avoid
    // Visual C++ error 2712 when compiling with C++ EH
    LogGetProxyFault();
  }
#else
  success = pWHGPFU(hWinHttp, url, options, info);
#endif  // (_HAS_EXCEPTIONS == 0)

  return success;
}

bool IsDefaultBrowserFirefox() {
  HKEY key;
  LONG result = RegOpenKeyEx(HKEY_CLASSES_ROOT, L"http\\shell\\open\\command",
                             0, KEY_READ, &key);
  if (ERROR_SUCCESS != result)
    return false;

  DWORD size, type;
  bool success = false;
  result = RegQueryValueEx(key, L"", 0, &type, NULL, &size);
  if (result == ERROR_SUCCESS && type == REG_SZ) {
    wchar_t* value = new wchar_t[size+1];
    BYTE* buffer = reinterpret_cast<BYTE*>(value);
    result = RegQueryValueEx(key, L"", 0, &type, buffer, &size);
    if (result == ERROR_SUCCESS) {
      // Size returned by RegQueryValueEx is in bytes, convert to number of
      // wchar_t's.
      size /= sizeof(value[0]);
      value[size] = L'\0';
      for (size_t i = 0; i < size; ++i) {
        value[i] = tolowercase(value[i]);
      }
      success = (NULL != strstr(value, L"firefox.exe"));
    }
    delete[] value;
  }

  RegCloseKey(key);
  return success;
}

bool GetWinHttpProxySettings(const char* url, ProxyInfo* proxy) {
  HMODULE winhttp_handle = LoadLibrary(L"winhttp.dll");
  if (winhttp_handle == NULL) {
    LOG(LS_ERROR) << "Failed to load winhttp.dll.";
    return false;
  }
  WINHTTP_CURRENT_USER_IE_PROXY_CONFIG iecfg;
  memset(&iecfg, 0, sizeof(iecfg));
  Url<char> purl(url);
  pfnWinHttpGetIEProxyConfig pWHGIEPC =
      reinterpret_cast<pfnWinHttpGetIEProxyConfig>(
          GetProcAddress(winhttp_handle,
                         "WinHttpGetIEProxyConfigForCurrentUser"));
  bool success = false;
  if (pWHGIEPC && pWHGIEPC(&iecfg)) {
    // We were read proxy config successfully.
    success = true;
    if (iecfg.fAutoDetect) {
      proxy->autodetect = true;
    }
    if (iecfg.lpszAutoConfigUrl) {
      proxy->autoconfig_url = ToUtf8(iecfg.lpszAutoConfigUrl);
      GlobalFree(iecfg.lpszAutoConfigUrl);
    }
    if (iecfg.lpszProxyBypass) {
      proxy->bypass_list = ToUtf8(iecfg.lpszProxyBypass);
      GlobalFree(iecfg.lpszProxyBypass);
    }
    if (iecfg.lpszProxy) {
      if (!ProxyListMatch(purl, proxy->bypass_list, ';')) {
        ParseProxy(ToUtf8(iecfg.lpszProxy), proxy);
      }
      GlobalFree(iecfg.lpszProxy);
    }
  }
  FreeLibrary(winhttp_handle);
  return success;
}

// Uses the WinHTTP API to auto detect proxy for the given url. Firefox and IE
// have slightly different option dialogs for proxy settings. In Firefox,
// either a location of a proxy configuration file can be specified or auto
// detection can be selected. In IE theese two options can be independently
// selected. For the case where both options are selected (only IE) we try to
// fetch the config file first, and if that fails we'll perform an auto
// detection.
//
// Returns true if we successfully performed an auto detection not depending on
// whether we found a proxy or not. Returns false on error.
bool WinHttpAutoDetectProxyForUrl(const char* agent, const char* url,
                                  ProxyInfo* proxy) {
  Url<char> purl(url);
  bool success = true;
  HMODULE winhttp_handle = LoadLibrary(L"winhttp.dll");
  if (winhttp_handle == NULL) {
    LOG(LS_ERROR) << "Failed to load winhttp.dll.";
    return false;
  }
  pfnWinHttpOpen pWHO =
      reinterpret_cast<pfnWinHttpOpen>(GetProcAddress(winhttp_handle,
                                                      "WinHttpOpen"));
  pfnWinHttpCloseHandle pWHCH =
      reinterpret_cast<pfnWinHttpCloseHandle>(
          GetProcAddress(winhttp_handle, "WinHttpCloseHandle"));
  pfnWinHttpGetProxyForUrl pWHGPFU =
      reinterpret_cast<pfnWinHttpGetProxyForUrl>(
          GetProcAddress(winhttp_handle, "WinHttpGetProxyForUrl"));
  if (pWHO && pWHCH && pWHGPFU) {
    if (HINTERNET hWinHttp = pWHO(ToUtf16(agent).c_str(),
                                  WINHTTP_ACCESS_TYPE_NO_PROXY,
                                  WINHTTP_NO_PROXY_NAME,
                                  WINHTTP_NO_PROXY_BYPASS,
                                  0)) {
      BOOL result = FALSE;
      WINHTTP_PROXY_INFO info;
      memset(&info, 0, sizeof(info));
      if (proxy->autodetect) {
        // Use DHCP and DNS to try to find any proxy to use.
        WINHTTP_AUTOPROXY_OPTIONS options;
        memset(&options, 0, sizeof(options));
        options.fAutoLogonIfChallenged = TRUE;

        options.dwFlags |= WINHTTP_AUTOPROXY_AUTO_DETECT;
        options.dwAutoDetectFlags |= WINHTTP_AUTO_DETECT_TYPE_DHCP
            | WINHTTP_AUTO_DETECT_TYPE_DNS_A;
        result = MyWinHttpGetProxyForUrl(
            pWHGPFU, hWinHttp, ToUtf16(url).c_str(), &options, &info);
      }
      if (!result && !proxy->autoconfig_url.empty()) {
        // We have the location of a proxy config file. Download it and
        // execute it to find proxy settings for our url.
        WINHTTP_AUTOPROXY_OPTIONS options;
        memset(&options, 0, sizeof(options));
        memset(&info, 0, sizeof(info));
        options.fAutoLogonIfChallenged = TRUE;

        std::wstring autoconfig_url16((ToUtf16)(proxy->autoconfig_url));
        options.dwFlags |= WINHTTP_AUTOPROXY_CONFIG_URL;
        options.lpszAutoConfigUrl = autoconfig_url16.c_str();

        result = MyWinHttpGetProxyForUrl(
            pWHGPFU, hWinHttp, ToUtf16(url).c_str(), &options, &info);
      }
      if (result) {
        // Either the given auto config url was valid or auto
        // detection found a proxy on this network.
        if (info.lpszProxy) {
          // TODO: Does this bypass list differ from the list
          // retreived from GetWinHttpProxySettings earlier?
          if (info.lpszProxyBypass) {
            proxy->bypass_list = ToUtf8(info.lpszProxyBypass);
            GlobalFree(info.lpszProxyBypass);
          } else {
            proxy->bypass_list.clear();
          }
          if (!ProxyListMatch(purl, proxy->bypass_list, ';')) {
            // Found proxy for this URL. If parsing the address turns
            // out ok then we are successful.
            success = ParseProxy(ToUtf8(info.lpszProxy), proxy);
          }
          GlobalFree(info.lpszProxy);
        }
      } else {
        // We could not find any proxy for this url.
        LOG(LS_INFO) << "No proxy detected for " << url;
      }
      pWHCH(hWinHttp);
    }
  } else {
    LOG(LS_ERROR) << "Failed loading WinHTTP functions.";
    success = false;
  }
  FreeLibrary(winhttp_handle);
  return success;
}

#if 0  // Below functions currently not used.

bool GetJsProxySettings(const char* url, ProxyInfo* proxy) {
  Url<char> purl(url);
  bool success = false;

  if (HMODULE hModJS = LoadLibrary(_T("jsproxy.dll"))) {
    pfnInternetGetProxyInfo pIGPI =
        reinterpret_cast<pfnInternetGetProxyInfo>(
            GetProcAddress(hModJS, "InternetGetProxyInfo"));
    if (pIGPI) {
      char proxy[256], host[256];
      memset(proxy, 0, sizeof(proxy));
      char * ptr = proxy;
      DWORD proxylen = sizeof(proxy);
      std::string surl = Utf8String(url);
      DWORD hostlen = _snprintf(host, sizeof(host), "http%s://%S",
                                purl.secure() ? "s" : "", purl.server());
      if (pIGPI(surl.data(), surl.size(), host, hostlen, &ptr, &proxylen)) {
        LOG(INFO) << "Proxy: " << proxy;
      } else {
        LOG_GLE(INFO) << "InternetGetProxyInfo";
      }
    }
    FreeLibrary(hModJS);
  }
  return success;
}

bool GetWmProxySettings(const char* url, ProxyInfo* proxy) {
  Url<char> purl(url);
  bool success = false;

  INSNetSourceCreator * nsc = 0;
  HRESULT hr = CoCreateInstance(CLSID_ClientNetManager, 0, CLSCTX_ALL,
                                IID_INSNetSourceCreator, (LPVOID *) &nsc);
  if (SUCCEEDED(hr)) {
    if (SUCCEEDED(hr = nsc->Initialize())) {
      VARIANT dispatch;
      VariantInit(&dispatch);
      if (SUCCEEDED(hr = nsc->GetNetSourceAdminInterface(L"http", &dispatch))) {
        IWMSInternalAdminNetSource * ians = 0;
        if (SUCCEEDED(hr = dispatch.pdispVal->QueryInterface(
                IID_IWMSInternalAdminNetSource, (LPVOID *) &ians))) {
          _bstr_t host(purl.server());
          BSTR proxy = 0;
          BOOL bProxyEnabled = FALSE;
          DWORD port, context = 0;
          if (SUCCEEDED(hr = ians->FindProxyForURL(
                  L"http", host, &bProxyEnabled, &proxy, &port, &context))) {
            success = true;
            if (bProxyEnabled) {
              _bstr_t sproxy = proxy;
              proxy->ptype = PT_HTTPS;
              proxy->host = sproxy;
              proxy->port = port;
            }
          }
          SysFreeString(proxy);
          if (FAILED(hr = ians->ShutdownProxyContext(context))) {
            LOG(LS_INFO) << "IWMSInternalAdminNetSource::ShutdownProxyContext"
                         << "failed: " << hr;
          }
          ians->Release();
        }
      }
      VariantClear(&dispatch);
      if (FAILED(hr = nsc->Shutdown())) {
        LOG(LS_INFO) << "INSNetSourceCreator::Shutdown failed: " << hr;
      }
    }
    nsc->Release();
  }
  return success;
}

bool GetIePerConnectionProxySettings(const char* url, ProxyInfo* proxy) {
  Url<char> purl(url);
  bool success = false;

  INTERNET_PER_CONN_OPTION_LIST list;
  INTERNET_PER_CONN_OPTION options[3];
  memset(&list, 0, sizeof(list));
  memset(&options, 0, sizeof(options));

  list.dwSize = sizeof(list);
  list.dwOptionCount = 3;
  list.pOptions = options;
  options[0].dwOption = INTERNET_PER_CONN_FLAGS;
  options[1].dwOption = INTERNET_PER_CONN_PROXY_SERVER;
  options[2].dwOption = INTERNET_PER_CONN_PROXY_BYPASS;
  DWORD dwSize = sizeof(list);

  if (!InternetQueryOption(0, INTERNET_OPTION_PER_CONNECTION_OPTION, &list,
                           &dwSize)) {
    LOG(LS_INFO) << "InternetQueryOption failed: " << GetLastError();
  } else if ((options[0].Value.dwValue & PROXY_TYPE_PROXY) != 0) {
    success = true;
    if (!ProxyListMatch(purl, nonnull(options[2].Value.pszValue), _T(';'))) {
      ParseProxy(nonnull(options[1].Value.pszValue), proxy);
    }
  } else if ((options[0].Value.dwValue & PROXY_TYPE_DIRECT) != 0) {
    success = true;
  } else {
    LOG(LS_INFO) << "unknown internet access type: "
                 << options[0].Value.dwValue;
  }
  if (options[1].Value.pszValue) {
    GlobalFree(options[1].Value.pszValue);
  }
  if (options[2].Value.pszValue) {
    GlobalFree(options[2].Value.pszValue);
  }
  return success;
}

#endif  // 0

// Uses the InternetQueryOption function to retrieve proxy settings
// from the registry. This will only give us the 'static' settings,
// ie, not any information about auto config etc.
bool GetIeLanProxySettings(const char* url, ProxyInfo* proxy) {
  Url<char> purl(url);
  bool success = false;

  wchar_t buffer[1024];
  memset(buffer, 0, sizeof(buffer));
  INTERNET_PROXY_INFO * info = reinterpret_cast<INTERNET_PROXY_INFO *>(buffer);
  DWORD dwSize = sizeof(buffer);

  if (!InternetQueryOption(0, INTERNET_OPTION_PROXY, info, &dwSize)) {
    LOG(LS_INFO) << "InternetQueryOption failed: " << GetLastError();
  } else if (info->dwAccessType == INTERNET_OPEN_TYPE_DIRECT) {
    success = true;
  } else if (info->dwAccessType == INTERNET_OPEN_TYPE_PROXY) {
    success = true;
    if (!ProxyListMatch(purl, nonnull(reinterpret_cast<const char*>(
            info->lpszProxyBypass)), ' ')) {
      ParseProxy(nonnull(reinterpret_cast<const char*>(info->lpszProxy)),
                 proxy);
    }
  } else {
    LOG(LS_INFO) << "unknown internet access type: " << info->dwAccessType;
  }
  return success;
}

bool GetIeProxySettings(const char* agent, const char* url, ProxyInfo* proxy) {
  bool success = GetWinHttpProxySettings(url, proxy);
  if (!success) {
    // TODO: Should always call this if no proxy were detected by
    // GetWinHttpProxySettings?
    // WinHttp failed. Try using the InternetOptionQuery method instead.
    return GetIeLanProxySettings(url, proxy);
  }
  return true;
}

#endif  // WEBRTC_WIN

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)  // WEBRTC_MAC && !defined(WEBRTC_IOS) specific implementation for reading system wide
            // proxy settings.

bool p_getProxyInfoForTypeFromDictWithKeys(ProxyInfo* proxy,
                                           ProxyType type,
                                           const CFDictionaryRef proxyDict,
                                           const CFStringRef enabledKey,
                                           const CFStringRef hostKey,
                                           const CFStringRef portKey) {
  // whether or not we set up the proxy info.
  bool result = false;

  // we use this as a scratch variable for determining if operations
  // succeeded.
  bool converted = false;

  // the data we need to construct the SocketAddress for the proxy.
  std::string hostname;
  int port;

  if ((proxyDict != NULL) &&
      (CFGetTypeID(proxyDict) == CFDictionaryGetTypeID())) {
    // CoreFoundation stuff that we'll have to get from
    // the dictionaries and interpret or convert into more usable formats.
    CFNumberRef enabledCFNum;
    CFNumberRef portCFNum;
    CFStringRef hostCFStr;

    enabledCFNum = (CFNumberRef)CFDictionaryGetValue(proxyDict, enabledKey);

    if (p_isCFNumberTrue(enabledCFNum)) {
      // let's see if we can get the address and port.
      hostCFStr = (CFStringRef)CFDictionaryGetValue(proxyDict, hostKey);
      converted = p_convertHostCFStringRefToCPPString(hostCFStr, hostname);
      if (converted) {
        portCFNum = (CFNumberRef)CFDictionaryGetValue(proxyDict, portKey);
        converted = p_convertCFNumberToInt(portCFNum, &port);
        if (converted) {
          // we have something enabled, with a hostname and a port.
          // That's sufficient to set up the proxy info.
          proxy->type = type;
          proxy->address.SetIP(hostname);
          proxy->address.SetPort(port);
          result = true;
        }
      }
    }
  }

  return result;
}

// Looks for proxy information in the given dictionary,
// return true if it found sufficient information to define one,
// false otherwise.  This is guaranteed to not change the values in proxy
// unless a full-fledged proxy description was discovered in the dictionary.
// However, at the present time this does not support username or password.
// Checks first for a SOCKS proxy, then for HTTPS, then HTTP.
bool GetMacProxySettingsFromDictionary(ProxyInfo* proxy,
                                       const CFDictionaryRef proxyDict) {
  // the function result.
  bool gotProxy = false;


  // first we see if there's a SOCKS proxy in place.
  gotProxy = p_getProxyInfoForTypeFromDictWithKeys(proxy,
                                                   PROXY_SOCKS5,
                                                   proxyDict,
                                                   kSCPropNetProxiesSOCKSEnable,
                                                   kSCPropNetProxiesSOCKSProxy,
                                                   kSCPropNetProxiesSOCKSPort);

  if (!gotProxy) {
    // okay, no SOCKS proxy, let's look for https.
    gotProxy = p_getProxyInfoForTypeFromDictWithKeys(proxy,
                                               PROXY_HTTPS,
                                               proxyDict,
                                               kSCPropNetProxiesHTTPSEnable,
                                               kSCPropNetProxiesHTTPSProxy,
                                               kSCPropNetProxiesHTTPSPort);
    if (!gotProxy) {
      // Finally, try HTTP proxy. Note that flute doesn't
      // differentiate between HTTPS and HTTP, hence we are using the
      // same flute type here, ie. PROXY_HTTPS.
      gotProxy = p_getProxyInfoForTypeFromDictWithKeys(
          proxy, PROXY_HTTPS, proxyDict, kSCPropNetProxiesHTTPEnable,
          kSCPropNetProxiesHTTPProxy, kSCPropNetProxiesHTTPPort);
    }
  }
  return gotProxy;
}

// TODO(hughv) Update keychain functions. They work on 10.8, but are depricated.
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
bool p_putPasswordInProxyInfo(ProxyInfo* proxy) {
  bool result = true;  // by default we assume we're good.
  // for all we know there isn't any password.  We'll set to false
  // if we find a problem.

  // Ask the keychain for an internet password search for the given protocol.
  OSStatus oss = 0;
  SecKeychainAttributeList attrList;
  attrList.count = 3;
  SecKeychainAttribute attributes[3];
  attrList.attr = attributes;

  attributes[0].tag = kSecProtocolItemAttr;
  attributes[0].length = sizeof(SecProtocolType);
  SecProtocolType protocol;
  switch (proxy->type) {
    case PROXY_HTTPS :
      protocol = kSecProtocolTypeHTTPS;
      break;
    case PROXY_SOCKS5 :
      protocol = kSecProtocolTypeSOCKS;
      break;
    default :
      LOG(LS_ERROR) << "asked for proxy password for unknown proxy type.";
      result = false;
      break;
  }
  attributes[0].data = &protocol;

  UInt32 port = proxy->address.port();
  attributes[1].tag = kSecPortItemAttr;
  attributes[1].length = sizeof(UInt32);
  attributes[1].data = &port;

  std::string ip = proxy->address.ipaddr().ToString();
  attributes[2].tag = kSecServerItemAttr;
  attributes[2].length = ip.length();
  attributes[2].data = const_cast<char*>(ip.c_str());

  if (result) {
    LOG(LS_INFO) << "trying to get proxy username/password";
    SecKeychainSearchRef sref;
    oss = SecKeychainSearchCreateFromAttributes(NULL,
                                                kSecInternetPasswordItemClass,
                                                &attrList, &sref);
    if (0 == oss) {
      LOG(LS_INFO) << "SecKeychainSearchCreateFromAttributes was good";
      // Get the first item, if there is one.
      SecKeychainItemRef iref;
      oss = SecKeychainSearchCopyNext(sref, &iref);
      if (0 == oss) {
        LOG(LS_INFO) << "...looks like we have the username/password data";
        // If there is, get the username and the password.

        SecKeychainAttributeInfo attribsToGet;
        attribsToGet.count = 1;
        UInt32 tag = kSecAccountItemAttr;
        UInt32 format = CSSM_DB_ATTRIBUTE_FORMAT_STRING;
        void *data;
        UInt32 length;
        SecKeychainAttributeList *localList;

        attribsToGet.tag = &tag;
        attribsToGet.format = &format;
        OSStatus copyres = SecKeychainItemCopyAttributesAndData(iref,
                                                                &attribsToGet,
                                                                NULL,
                                                                &localList,
                                                                &length,
                                                                &data);
        if (0 == copyres) {
          LOG(LS_INFO) << "...and we can pull it out.";
          // now, we know from experimentation (sadly not from docs)
          // that the username is in the local attribute list,
          // and the password in the data,
          // both without null termination but with info on their length.
          // grab the password from the data.
          std::string password;
          password.append(static_cast<const char*>(data), length);

          // make the password into a CryptString
          // huh, at the time of writing, you can't.
          // so we'll skip that for now and come back to it later.

          // now put the username in the proxy.
          if (1 <= localList->attr->length) {
            proxy->username.append(
                static_cast<const char*>(localList->attr->data),
                localList->attr->length);
            LOG(LS_INFO) << "username is " << proxy->username;
          } else {
            LOG(LS_ERROR) << "got keychain entry with no username";
            result = false;
          }
        } else {
          LOG(LS_ERROR) << "couldn't copy info from keychain.";
          result = false;
        }
        SecKeychainItemFreeAttributesAndData(localList, data);
      } else if (errSecItemNotFound == oss) {
        LOG(LS_INFO) << "...username/password info not found";
      } else {
        // oooh, neither 0 nor itemNotFound.
        LOG(LS_ERROR) << "Couldn't get keychain information, error code" << oss;
        result = false;
      }
    } else if (errSecItemNotFound == oss) {  // noop
    } else {
      // oooh, neither 0 nor itemNotFound.
      LOG(LS_ERROR) << "Couldn't get keychain information, error code" << oss;
      result = false;
    }
  }

  return result;
}

bool GetMacProxySettings(ProxyInfo* proxy) {
  // based on the Apple Technical Q&A QA1234
  // http://developer.apple.com/qa/qa2001/qa1234.html
  CFDictionaryRef proxyDict = SCDynamicStoreCopyProxies(NULL);
  bool result = false;

  if (proxyDict != NULL) {
    // sending it off to another function makes it easier to unit test
    // since we can make our own dictionary to hand to that function.
    result = GetMacProxySettingsFromDictionary(proxy, proxyDict);

    if (result) {
      result = p_putPasswordInProxyInfo(proxy);
    }

    // We created the dictionary with something that had the
    // word 'copy' in it, so we have to release it, according
    // to the Carbon memory management standards.
    CFRelease(proxyDict);
  } else {
    LOG(LS_ERROR) << "SCDynamicStoreCopyProxies failed";
  }

  return result;
}
#endif  // WEBRTC_MAC && !defined(WEBRTC_IOS)

#ifdef WEBRTC_IOS
// iOS has only http proxy
bool GetiOSProxySettings(ProxyInfo* proxy) {

  bool result = false;

  CFDictionaryRef proxy_dict = CFNetworkCopySystemProxySettings();
  if (!proxy_dict) {
    LOG(LS_ERROR) << "CFNetworkCopySystemProxySettings failed";
    return false;
  }

  CFNumberRef proxiesHTTPEnable = (CFNumberRef)CFDictionaryGetValue(
    proxy_dict, kCFNetworkProxiesHTTPEnable);
  if (!p_isCFNumberTrue(proxiesHTTPEnable)) {
    CFRelease(proxy_dict);
    return false;
  }

  CFStringRef proxy_address = (CFStringRef)CFDictionaryGetValue(
    proxy_dict, kCFNetworkProxiesHTTPProxy);
  CFNumberRef proxy_port = (CFNumberRef)CFDictionaryGetValue(
    proxy_dict, kCFNetworkProxiesHTTPPort);

  // the data we need to construct the SocketAddress for the proxy.
  std::string hostname;
  int port;
  if (p_convertHostCFStringRefToCPPString(proxy_address, hostname) &&
      p_convertCFNumberToInt(proxy_port, &port)) {
      // We have something enabled, with a hostname and a port.
      // That's sufficient to set up the proxy info.
      // Finally, try HTTP proxy. Note that flute doesn't
      // differentiate between HTTPS and HTTP, hence we are using the
      // same flute type here, ie. PROXY_HTTPS.
      proxy->type = PROXY_HTTPS;

      proxy->address.SetIP(hostname);
      proxy->address.SetPort(port);
      result = true;
  }

  // We created the dictionary with something that had the
  // word 'copy' in it, so we have to release it, according
  // to the Carbon memory management standards.
  CFRelease(proxy_dict);

  return result;
}
#endif // WEBRTC_IOS

bool AutoDetectProxySettings(const char* agent, const char* url,
                             ProxyInfo* proxy) {
#if defined(WEBRTC_WIN)
  return WinHttpAutoDetectProxyForUrl(agent, url, proxy);
#else
  LOG(LS_WARNING) << "Proxy auto-detection not implemented for this platform";
  return false;
#endif
}

bool GetSystemDefaultProxySettings(const char* agent, const char* url,
                                   ProxyInfo* proxy) {
#if defined(WEBRTC_WIN)
  return GetIeProxySettings(agent, url, proxy);
#elif defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
  return GetMacProxySettings(proxy);
#elif defined(WEBRTC_IOS)
  return GetiOSProxySettings(proxy);
#else
  // TODO: Get System settings if browser is not firefox.
  return GetFirefoxProxySettings(url, proxy);
#endif
}

bool GetProxySettingsForUrl(const char* agent, const char* url,
                            ProxyInfo* proxy, bool long_operation) {
  UserAgent a = GetAgent(agent);
  bool result;
  switch (a) {
    case UA_FIREFOX: {
      result = GetFirefoxProxySettings(url, proxy);
      break;
    }
#if defined(WEBRTC_WIN)
    case UA_INTERNETEXPLORER:
      result = GetIeProxySettings(agent, url, proxy);
      break;
    case UA_UNKNOWN:
      // Agent not defined, check default browser.
      if (IsDefaultBrowserFirefox()) {
        result = GetFirefoxProxySettings(url, proxy);
      } else {
        result = GetIeProxySettings(agent, url, proxy);
      }
      break;
#endif  // WEBRTC_WIN
    default:
      result = GetSystemDefaultProxySettings(agent, url, proxy);
      break;
  }

  // TODO: Consider using the 'long_operation' parameter to
  // decide whether to do the auto detection.
  if (result && (proxy->autodetect ||
                 !proxy->autoconfig_url.empty())) {
    // Use WinHTTP to auto detect proxy for us.
    result = AutoDetectProxySettings(agent, url, proxy);
    if (!result) {
      // Either auto detection is not supported or we simply didn't
      // find any proxy, reset type.
      proxy->type = rtc::PROXY_NONE;
    }
  }
  return result;
}

}  // namespace rtc
