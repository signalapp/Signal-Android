/*
Source:
http://msdn.microsoft.com/en-us/cc300389.aspx#P

License:
This license governs use of code marked as “sample” or “example” available on
this web site without a license agreement, as provided under the section above
titled “NOTICE SPECIFIC TO SOFTWARE AVAILABLE ON THIS WEB SITE.” If you use
such code (the “software”), you accept this license. If you do not accept the
license, do not use the software.

1. Definitions

The terms “reproduce,” “reproduction,” “derivative works,” and “distribution”
have the same meaning here as under U.S. copyright law.

A “contribution” is the original software, or any additions or changes to the
software.

A “contributor” is any person that distributes its contribution under this
license.

“Licensed patents” are a contributor’s patent claims that read directly on its
contribution.

2. Grant of Rights

(A) Copyright Grant - Subject to the terms of this license, including the
license conditions and limitations in section 3, each contributor grants you a
non-exclusive, worldwide, royalty-free copyright license to reproduce its
contribution, prepare derivative works of its contribution, and distribute its
contribution or any derivative works that you create.

(B) Patent Grant - Subject to the terms of this license, including the license
conditions and limitations in section 3, each contributor grants you a
non-exclusive, worldwide, royalty-free license under its licensed patents to
make, have made, use, sell, offer for sale, import, and/or otherwise dispose
of its contribution in the software or derivative works of the contribution in
the software.

3. Conditions and Limitations

(A) No Trademark License- This license does not grant you rights to use any
contributors’ name, logo, or trademarks.

(B) If you bring a patent claim against any contributor over patents that you
claim are infringed by the software, your patent license from such contributor
to the software ends automatically.

(C) If you distribute any portion of the software, you must retain all
copyright, patent, trademark, and attribution notices that are present in the
software.

(D) If you distribute any portion of the software in source code form, you may
do so only under this license by including a complete copy of this license
with your distribution. If you distribute any portion of the software in
compiled or object code form, you may only do so under a license that complies
with this license.

(E) The software is licensed “as-is.” You bear the risk of using it. The
contributors give no express warranties, guarantees or conditions. You may
have additional consumer rights under your local laws which this license
cannot change. To the extent permitted under your local laws, the contributors
exclude the implied warranties of merchantability, fitness for a particular
purpose and non-infringement.

(F) Platform Limitation - The licenses granted in sections 2(A) and 2(B)
extend only to the software or derivative works that you create that run on a
Microsoft Windows operating system product.
*/

/*
 *  The original code can be found here:
 *  http://msdn.microsoft.com/en-us/library/xcb2z8hs(VS.71).aspx
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_THREAD_WINDOWS_SET_NAME_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_THREAD_WINDOWS_SET_NAME_H_

namespace webrtc {

struct THREADNAME_INFO
{
   DWORD dwType;     // must be 0x1000
   LPCSTR szName;    // pointer to name (in user addr space)
   DWORD dwThreadID; // thread ID (-1 = caller thread)
   DWORD dwFlags;    // reserved for future use, must be zero
};

void SetThreadName(DWORD dwThreadID, LPCSTR szThreadName)
{
    THREADNAME_INFO info;
    info.dwType = 0x1000;
    info.szName = szThreadName;
    info.dwThreadID = dwThreadID;
    info.dwFlags = 0;

    __try
    {
        RaiseException(0x406D1388, 0, sizeof(info) / sizeof(DWORD),
                       (ULONG_PTR*)&info);
    }
    __except (EXCEPTION_CONTINUE_EXECUTION)
    {
    }
}
}  // namespace webrtc
#endif // WEBRTC_SYSTEM_WRAPPERS_SOURCE_THREAD_WINDOWS_SET_NAME_H_
