package org.thoughtcrime.bouncycastle.asn1;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.bouncycastle.util.io.Streams;

/**
 * a general purpose ASN.1 decoder - note: this class differs from the
 * others in that it returns null after it has read the last object in
 * the stream. If an ASN.1 NULL is encountered a DER/BER Null object is
 * returned.
 */
public class ASN1InputStream
    extends FilterInputStream
    implements DERTags
{
    private final int limit;
    private final boolean lazyEvaluate;

    public ASN1InputStream(
        InputStream is)
    {
        this(is, Integer.MAX_VALUE);
    }

    /**
     * Create an ASN1InputStream based on the input byte array. The length of DER objects in
     * the stream is automatically limited to the length of the input array.
     * 
     * @param input array containing ASN.1 encoded data.
     */
    public ASN1InputStream(
        byte[] input)
    {
        this(new ByteArrayInputStream(input), input.length);
    }

    /**
     * Create an ASN1InputStream based on the input byte array. The length of DER objects in
     * the stream is automatically limited to the length of the input array.
     *
     * @param input array containing ASN.1 encoded data.
     * @param lazyEvaluate true if parsing inside constructed objects can be delayed.
     */
    public ASN1InputStream(
        byte[] input,
        boolean lazyEvaluate)
    {
        this(new ByteArrayInputStream(input), input.length, lazyEvaluate);
    }
    
    /**
     * Create an ASN1InputStream where no DER object will be longer than limit.
     * 
     * @param input stream containing ASN.1 encoded data.
     * @param limit maximum size of a DER encoded object.
     */
    public ASN1InputStream(
        InputStream input,
        int         limit)
    {
        this(input, limit, false);
    }

    /**
     * Create an ASN1InputStream where no DER object will be longer than limit, and constructed
     * objects such as sequences will be parsed lazily.
     *
     * @param input stream containing ASN.1 encoded data.
     * @param limit maximum size of a DER encoded object.
     * @param lazyEvaluate true if parsing inside constructed objects can be delayed.
     */
    public ASN1InputStream(
        InputStream input,
        int         limit,
        boolean     lazyEvaluate)
    {
        super(input);
        this.limit = limit;
        this.lazyEvaluate = lazyEvaluate;
    }

    protected int readLength()
        throws IOException
    {
        return readLength(this, limit);
    }

    protected void readFully(
        byte[]  bytes)
        throws IOException
    {
        if (Streams.readFully(this, bytes) != bytes.length)
        {
            throw new EOFException("EOF encountered in middle of object");
        }
    }

    /**
     * build an object given its tag and the number of bytes to construct it from.
     */
    protected DERObject buildObject(
        int       tag,
        int       tagNo,
        int       length)
        throws IOException
    {
        boolean isConstructed = (tag & CONSTRUCTED) != 0;

        DefiniteLengthInputStream defIn = new DefiniteLengthInputStream(this, length);

        if ((tag & APPLICATION) != 0)
        {
            return new DERApplicationSpecific(isConstructed, tagNo, defIn.toByteArray());
        }

        if ((tag & TAGGED) != 0)
        {
            return new BERTaggedObjectParser(tag, tagNo, defIn).getDERObject();
        }

        if (isConstructed)
        {
            // TODO There are other tags that may be constructed (e.g. BIT_STRING)
            switch (tagNo)
            {
                case OCTET_STRING:
                    //
                    // yes, people actually do this...
                    //
                    return new BERConstructedOctetString(buildDEREncodableVector(defIn).v);
                case SEQUENCE:
                    if (lazyEvaluate)
                    {
                        return new LazyDERSequence(defIn.toByteArray());
                    }
                    else
                    {
                        return DERFactory.createSequence(buildDEREncodableVector(defIn));   
                    }
                case SET:
                    return DERFactory.createSet(buildDEREncodableVector(defIn), false);
                default:
                    return new DERUnknownTag(true, tagNo, defIn.toByteArray());
            }
        }

        return createPrimitiveDERObject(tagNo, defIn.toByteArray());
    }

    ASN1EncodableVector buildEncodableVector()
        throws IOException
    {
        ASN1EncodableVector v = new ASN1EncodableVector();
        DERObject o;

        while ((o = readObject()) != null)
        {
            v.add(o);
        }

        return v;
    }

    ASN1EncodableVector buildDEREncodableVector(
        DefiniteLengthInputStream dIn) throws IOException
    {
        return new ASN1InputStream(dIn).buildEncodableVector();
    }

    public DERObject readObject()
        throws IOException
    {
        int tag = read();
        if (tag <= 0)
        {
            if (tag == 0)
            {
                throw new IOException("unexpected end-of-contents marker");
            }

            return null;
        }

        //
        // calculate tag number
        //
        int tagNo = readTagNumber(this, tag);

        boolean isConstructed = (tag & CONSTRUCTED) != 0;

        //
        // calculate length
        //
        int length = readLength();

        if (length < 0) // indefinite length method
        {
            if (!isConstructed)
            {
                throw new IOException("indefinite length primitive encoding encountered");
            }

            IndefiniteLengthInputStream indIn = new IndefiniteLengthInputStream(this);

            if ((tag & APPLICATION) != 0)
            {
                ASN1StreamParser sp = new ASN1StreamParser(indIn);

                return new BERApplicationSpecificParser(tagNo, sp).getDERObject();
            }
            if ((tag & TAGGED) != 0)
            {
                return new BERTaggedObjectParser(tag, tagNo, indIn).getDERObject();
            }

            ASN1StreamParser sp = new ASN1StreamParser(indIn);

            // TODO There are other tags that may be constructed (e.g. BIT_STRING)
            switch (tagNo)
            {
                case OCTET_STRING:
                    return new BEROctetStringParser(sp).getDERObject();
                case SEQUENCE:
                    return new BERSequenceParser(sp).getDERObject();
                case SET:
                    return new BERSetParser(sp).getDERObject();
                default:
                    throw new IOException("unknown BER object encountered");
            }
        }
        else
        {
            return buildObject(tag, tagNo, length);
        }
    }

    static int readTagNumber(InputStream s, int tag) 
        throws IOException
    {
        int tagNo = tag & 0x1f;

        //
        // with tagged object tag number is bottom 5 bits, or stored at the start of the content
        //
        if (tagNo == 0x1f)
        {
            tagNo = 0;

            int b = s.read();

            // X.690-0207 8.1.2.4.2
            // "c) bits 7 to 1 of the first subsequent octet shall not all be zero."
            if ((b & 0x7f) == 0) // Note: -1 will pass
            {
                throw new IOException("corrupted stream - invalid high tag number found");
            }

            while ((b >= 0) && ((b & 0x80) != 0))
            {
                tagNo |= (b & 0x7f);
                tagNo <<= 7;
                b = s.read();
            }

            if (b < 0)
            {
                throw new EOFException("EOF found inside tag value.");
            }
            
            tagNo |= (b & 0x7f);
        }
        
        return tagNo;
    }

    static int readLength(InputStream s, int limit)
        throws IOException
    {
        int length = s.read();
        if (length < 0)
        {
            throw new EOFException("EOF found when length expected");
        }

        if (length == 0x80)
        {
            return -1;      // indefinite-length encoding
        }

        if (length > 127)
        {
            int size = length & 0x7f;

            if (size > 4)
            {
                throw new IOException("DER length more than 4 bytes");
            }

            length = 0;
            for (int i = 0; i < size; i++)
            {
                int next = s.read();

                if (next < 0)
                {
                    throw new EOFException("EOF found reading length");
                }

                length = (length << 8) + next;
            }

            if (length < 0)
            {
                throw new IOException("corrupted stream - negative length found");
            }

            if (length >= limit)   // after all we must have read at least 1 byte
            {
                throw new IOException("corrupted stream - out of bounds length found");
            }
        }

        return length;
    }

    static DERObject createPrimitiveDERObject(
        int     tagNo,
        byte[]  bytes)
    {
        switch (tagNo)
        {
            case BIT_STRING:
            {
                int padBits = bytes[0];
                byte[] data = new byte[bytes.length - 1];
                System.arraycopy(bytes, 1, data, 0, bytes.length - 1);
                return new DERBitString(data, padBits);
            }
            case BMP_STRING:
                return new DERBMPString(bytes);
            case BOOLEAN:
                return new DERBoolean(bytes);
            case ENUMERATED:
                return new DEREnumerated(bytes);
            case GENERALIZED_TIME:
                return new DERGeneralizedTime(bytes);
            case GENERAL_STRING:
                return new DERGeneralString(bytes);
            case IA5_STRING:
                return new DERIA5String(bytes);
            case INTEGER:
                return new DERInteger(bytes);
            case NULL:
                return DERNull.INSTANCE;   // actual content is ignored (enforce 0 length?)
            case NUMERIC_STRING:
                return new DERNumericString(bytes);
            case OBJECT_IDENTIFIER:
                return new DERObjectIdentifier(bytes);
            case OCTET_STRING:
                return new DEROctetString(bytes);
            case PRINTABLE_STRING:
                return new DERPrintableString(bytes);
            case T61_STRING:
                return new DERT61String(bytes);
            case UNIVERSAL_STRING:
                return new DERUniversalString(bytes);
            case UTC_TIME:
                return new DERUTCTime(bytes);
            case UTF8_STRING:
                return new DERUTF8String(bytes);
            case VISIBLE_STRING:
                return new DERVisibleString(bytes);
            default:
                return new DERUnknownTag(false, tagNo, bytes);
        }
    }
}
