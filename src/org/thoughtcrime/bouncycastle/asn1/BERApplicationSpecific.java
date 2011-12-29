package org.thoughtcrime.bouncycastle.asn1;

import org.thoughtcrime.securesms.R;

public class BERApplicationSpecific
    extends DERApplicationSpecific
{
    public BERApplicationSpecific(int tagNo, ASN1EncodableVector vec)
    {
        super(tagNo, vec);
    }
}
