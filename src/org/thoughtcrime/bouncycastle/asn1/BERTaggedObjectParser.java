package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;
import java.io.InputStream;

public class BERTaggedObjectParser
    implements ASN1TaggedObjectParser
{
    private int _baseTag;
    private int _tagNumber;
    private InputStream _contentStream;

    private boolean _indefiniteLength;

    protected BERTaggedObjectParser(
        int         baseTag,
        int         tagNumber,
        InputStream contentStream)
    {
        _baseTag = baseTag;
        _tagNumber = tagNumber;
        _contentStream = contentStream;
        _indefiniteLength = contentStream instanceof IndefiniteLengthInputStream;
    }

    public boolean isConstructed()
    {
        return (_baseTag & DERTags.CONSTRUCTED) != 0;
    }

    public int getTagNo()
    {
        return _tagNumber;
    }
    
    public DEREncodable getObjectParser(
        int     tag,
        boolean isExplicit)
        throws IOException
    {
        if (isExplicit)
        {
            return new ASN1StreamParser(_contentStream).readObject();
        }

        switch (tag)
        {
            case DERTags.SET:
                if (_indefiniteLength)
                {
                    return new BERSetParser(new ASN1StreamParser(_contentStream));
                }
                else
                {
                    return new DERSetParser(new ASN1StreamParser(_contentStream));
                }
            case DERTags.SEQUENCE:
                if (_indefiniteLength)
                {
                    return new BERSequenceParser(new ASN1StreamParser(_contentStream));
                }
                else
                {
                    return new DERSequenceParser(new ASN1StreamParser(_contentStream));
                }
            case DERTags.OCTET_STRING:
                // TODO Is the handling of definite length constructed encodings correct?
                if (_indefiniteLength || this.isConstructed())
                {
                    return new BEROctetStringParser(new ASN1StreamParser(_contentStream));
                }
                else
                {
                    return new DEROctetStringParser((DefiniteLengthInputStream)_contentStream);
                }
        }

        throw new RuntimeException("implicit tagging not implemented");
    }

    private ASN1EncodableVector rLoadVector(InputStream in)
    {
        try
        {
            return new ASN1StreamParser(in).readVector();
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
    }

    public DERObject getDERObject()
    {
        if (_indefiniteLength)
        {
            ASN1EncodableVector v = rLoadVector(_contentStream);

            return v.size() == 1
                ?   new BERTaggedObject(true, _tagNumber, v.get(0))
                :   new BERTaggedObject(false, _tagNumber, BERFactory.createSequence(v));
        }

        if (this.isConstructed())
        {
            ASN1EncodableVector v = rLoadVector(_contentStream);

            return v.size() == 1
                ?   new DERTaggedObject(true, _tagNumber, v.get(0))
                :   new DERTaggedObject(false, _tagNumber, DERFactory.createSequence(v));
        }

        try
        {
            DefiniteLengthInputStream defIn = (DefiniteLengthInputStream)_contentStream;
            return new DERTaggedObject(false, _tagNumber, new DEROctetString(defIn.toByteArray()));
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
