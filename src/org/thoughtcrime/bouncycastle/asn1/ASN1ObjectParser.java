package org.thoughtcrime.bouncycastle.asn1;

import java.io.InputStream;
import org.thoughtcrime.securesms.R;

/**
 * @deprecated will be removed
 */
public class ASN1ObjectParser
{
    ASN1StreamParser _aIn;

    protected ASN1ObjectParser(
        int         baseTag,
        int         tagNumber,
        InputStream contentStream)
    {
        _aIn = new ASN1StreamParser(contentStream);
    }
}
