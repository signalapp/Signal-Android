package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

/**
 * A NULL object.
 */
public class DERNull
    extends ASN1Null
{
    public static final DERNull INSTANCE = new DERNull();

    byte[]  zeroBytes = new byte[0];

    public DERNull()
    {
    }

    void encode(
        DEROutputStream  out)
        throws IOException
    {
        out.writeEncoded(NULL, zeroBytes);
    }
}
