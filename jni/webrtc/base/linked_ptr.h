/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * linked_ptr - simple reference linked pointer
 * (like reference counting, just using a linked list of the references
 * instead of their count.)
 *
 * The implementation stores three pointers for every linked_ptr, but
 * does not allocate anything on the free store.
 */

#ifndef WEBRTC_BASE_LINKED_PTR_H__
#define WEBRTC_BASE_LINKED_PTR_H__

namespace rtc {

/* For ANSI-challenged compilers, you may want to #define
 * NO_MEMBER_TEMPLATES, explicit or mutable */
#define NO_MEMBER_TEMPLATES

template <class X> class linked_ptr
{
public:

#ifndef NO_MEMBER_TEMPLATES
#   define TEMPLATE_FUNCTION template <class Y>
    TEMPLATE_FUNCTION friend class linked_ptr<Y>;
#else
#   define TEMPLATE_FUNCTION
    typedef X Y;
#endif

    typedef X element_type;

    explicit linked_ptr(X* p = 0) throw()
        : itsPtr(p) {itsPrev = itsNext = this;}
    ~linked_ptr()
        {release();}
    linked_ptr(const linked_ptr& r) throw()
        {acquire(r);}
    linked_ptr& operator=(const linked_ptr& r)
    {
        if (this != &r) {
            release();
            acquire(r);
        }
        return *this;
    }

#ifndef NO_MEMBER_TEMPLATES
    template <class Y> friend class linked_ptr<Y>;
    template <class Y> linked_ptr(const linked_ptr<Y>& r) throw()
        {acquire(r);}
    template <class Y> linked_ptr& operator=(const linked_ptr<Y>& r)
    {
        if (this != &r) {
            release();
            acquire(r);
        }
        return *this;
    }
#endif // NO_MEMBER_TEMPLATES

    X& operator*()  const throw()   {return *itsPtr;}
    X* operator->() const throw()   {return itsPtr;}
    X* get()        const throw()   {return itsPtr;}
    bool unique()   const throw()   {return itsPrev ? itsPrev==this : true;}

private:
    X*                          itsPtr;
    mutable const linked_ptr*   itsPrev;
    mutable const linked_ptr*   itsNext;

    void acquire(const linked_ptr& r) throw()
    { // insert this to the list
        itsPtr = r.itsPtr;
        itsNext = r.itsNext;
        itsNext->itsPrev = this;
        itsPrev = &r;
#ifndef mutable
        r.itsNext = this;
#else // for ANSI-challenged compilers
        (const_cast<linked_ptr<X>*>(&r))->itsNext = this;
#endif
    }

#ifndef NO_MEMBER_TEMPLATES
    template <class Y> void acquire(const linked_ptr<Y>& r) throw()
    { // insert this to the list
        itsPtr = r.itsPtr;
        itsNext = r.itsNext;
        itsNext->itsPrev = this;
        itsPrev = &r;
#ifndef mutable
        r.itsNext = this;
#else // for ANSI-challenged compilers
        (const_cast<linked_ptr<X>*>(&r))->itsNext = this;
#endif
    }
#endif // NO_MEMBER_TEMPLATES

    void release()
    { // erase this from the list, delete if unique
        if (unique()) delete itsPtr;
        else {
            itsPrev->itsNext = itsNext;
            itsNext->itsPrev = itsPrev;
            itsPrev = itsNext = 0;
        }
        itsPtr = 0;
    }
};

} // namespace rtc

#endif // WEBRTC_BASE_LINKED_PTR_H__

