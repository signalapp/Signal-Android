package org.thoughtcrime.bouncycastle.asn1;

import java.io.InputStream;

abstract class LimitedInputStream
        extends InputStream
{
    protected final InputStream _in;

    LimitedInputStream(
        InputStream in)
    {
        this._in = in;
    }

    protected void setParentEofDetect(boolean on)
    {
        if (_in instanceof IndefiniteLengthInputStream)
        {
            ((IndefiniteLengthInputStream)_in).setEofOn00(on);
        }
    }
}
