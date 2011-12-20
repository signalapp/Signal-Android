package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

/**
 * DER TaggedObject - in ASN.1 notation this is any object preceded by
 * a [n] where n is some number - these are assumed to follow the construction
 * rules (as with sequences).
 */
public class DERTaggedObject
    extends ASN1TaggedObject
{
    private static final byte[] ZERO_BYTES = new byte[0];

    /**
     * @param tagNo the tag number for this object.
     * @param obj the tagged object.
     */
    public DERTaggedObject(
        int             tagNo,
        DEREncodable    obj)
    {
        super(tagNo, obj);
    }

    /**
     * @param explicit true if an explicitly tagged object.
     * @param tagNo the tag number for this object.
     * @param obj the tagged object.
     */
    public DERTaggedObject(
        boolean         explicit,
        int             tagNo,
        DEREncodable    obj)
    {
        super(explicit, tagNo, obj);
    }

    /**
     * create an implicitly tagged object that contains a zero
     * length sequence.
     */
    public DERTaggedObject(
        int             tagNo)
    {
        super(false, tagNo, new DERSequence());
    }

    void encode(
        DEROutputStream  out)
        throws IOException
    {
        if (!empty)
        {
            byte[] bytes = obj.getDERObject().getEncoded(DER);

            if (explicit)
            {
                out.writeEncoded(CONSTRUCTED | TAGGED, tagNo, bytes);
            }
            else
            {
                //
                // need to mark constructed types...
                //
                int flags;
                if ((bytes[0] & CONSTRUCTED) != 0)
                {
                    flags = CONSTRUCTED | TAGGED;
                }
                else
                {
                    flags = TAGGED;
                }

                out.writeTag(flags, tagNo);
                out.write(bytes, 1, bytes.length - 1);
            }
        }
        else
        {
            out.writeEncoded(CONSTRUCTED | TAGGED, tagNo, ZERO_BYTES);
        }
    }
}
