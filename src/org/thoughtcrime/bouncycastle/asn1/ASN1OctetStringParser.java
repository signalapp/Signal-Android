package org.thoughtcrime.bouncycastle.asn1;

import java.io.InputStream;
import org.thoughtcrime.securesms.R;

public interface ASN1OctetStringParser
    extends DEREncodable
{
    public InputStream getOctetStream();
}
