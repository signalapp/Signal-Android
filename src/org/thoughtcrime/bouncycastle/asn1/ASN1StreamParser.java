package org.thoughtcrime.bouncycastle.asn1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ASN1StreamParser
{
    private final InputStream _in;
    private final int         _limit;

    public ASN1StreamParser(
        InputStream in)
    {
        this(in, Integer.MAX_VALUE);
    }

    public ASN1StreamParser(
        InputStream in,
        int         limit)
    {
        this._in = in;
        this._limit = limit;
    }

    public ASN1StreamParser(
        byte[] encoding)
    {
        this(new ByteArrayInputStream(encoding), encoding.length);
    }

    public DEREncodable readObject()
        throws IOException
    {
        int tag = _in.read();
        if (tag == -1)
        {
            return null;
        }

        //
        // turn of looking for "00" while we resolve the tag
        //
        set00Check(false);

        //
        // calculate tag number
        //
        int tagNo = ASN1InputStream.readTagNumber(_in, tag);

        boolean isConstructed = (tag & DERTags.CONSTRUCTED) != 0;

        //
        // calculate length
        //
        int length = ASN1InputStream.readLength(_in, _limit);

        if (length < 0) // indefinite length method
        {
            if (!isConstructed)
            {
                throw new IOException("indefinite length primitive encoding encountered");
            }

            IndefiniteLengthInputStream indIn = new IndefiniteLengthInputStream(_in);

            if ((tag & DERTags.APPLICATION) != 0)
            {
                ASN1StreamParser sp = new ASN1StreamParser(indIn);

                return new BERApplicationSpecificParser(tagNo, sp);
            }

            if ((tag & DERTags.TAGGED) != 0)
            {
                return new BERTaggedObjectParser(tag, tagNo, indIn);
            }

            ASN1StreamParser sp = new ASN1StreamParser(indIn);

            // TODO There are other tags that may be constructed (e.g. BIT_STRING)
            switch (tagNo)
            {
                case DERTags.OCTET_STRING:
                    return new BEROctetStringParser(sp);
                case DERTags.SEQUENCE:
                    return new BERSequenceParser(sp);
                case DERTags.SET:
                    return new BERSetParser(sp);
                default:
                    throw new IOException("unknown BER object encountered");
            }
        }
        else
        {
            DefiniteLengthInputStream defIn = new DefiniteLengthInputStream(_in, length);

            if ((tag & DERTags.APPLICATION) != 0)
            {
                return new DERApplicationSpecific(isConstructed, tagNo, defIn.toByteArray());
            }

            if ((tag & DERTags.TAGGED) != 0)
            {
                return new BERTaggedObjectParser(tag, tagNo, defIn);
            }

            if (isConstructed)
            {
                // TODO There are other tags that may be constructed (e.g. BIT_STRING)
                switch (tagNo)
                {
                    case DERTags.OCTET_STRING:
                        //
                        // yes, people actually do this...
                        //
                        return new BEROctetStringParser(new ASN1StreamParser(defIn));
                    case DERTags.SEQUENCE:
                        return new DERSequenceParser(new ASN1StreamParser(defIn));
                    case DERTags.SET:
                        return new DERSetParser(new ASN1StreamParser(defIn));
                    default:
                        // TODO Add DERUnknownTagParser class?
                        return new DERUnknownTag(true, tagNo, defIn.toByteArray());
                }
            }

            // Some primitive encodings can be handled by parsers too...
            switch (tagNo)
            {
                case DERTags.OCTET_STRING:
                    return new DEROctetStringParser(defIn);
            }

            return ASN1InputStream.createPrimitiveDERObject(tagNo, defIn.toByteArray());
        }
    }

    private void set00Check(boolean enabled)
    {
        if (_in instanceof IndefiniteLengthInputStream)
        {
            ((IndefiniteLengthInputStream)_in).setEofOn00(enabled);
        }
    }

    ASN1EncodableVector readVector() throws IOException
    {
        ASN1EncodableVector v = new ASN1EncodableVector();

        DEREncodable obj;
        while ((obj = readObject()) != null)
        {
            v.add(obj.getDERObject());
        }

        return v;
    }
}
