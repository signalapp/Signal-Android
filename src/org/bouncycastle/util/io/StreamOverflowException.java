package org.bouncycastle.util.io;

import java.io.IOException;
import org.thoughtcrime.securesms.R;

public class StreamOverflowException
    extends IOException
{
    public StreamOverflowException(String msg)
    {
        super(msg);
    }
}
