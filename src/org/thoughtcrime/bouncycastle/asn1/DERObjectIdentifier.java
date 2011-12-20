package org.thoughtcrime.bouncycastle.asn1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

public class DERObjectIdentifier
    extends ASN1Object
{
    String      identifier;

    /**
     * return an OID from the passed in object
     *
     * @exception IllegalArgumentException if the object cannot be converted.
     */
    public static DERObjectIdentifier getInstance(
        Object  obj)
    {
        if (obj == null || obj instanceof DERObjectIdentifier)
        {
            return (DERObjectIdentifier)obj;
        }

        if (obj instanceof ASN1OctetString)
        {
            return new DERObjectIdentifier(((ASN1OctetString)obj).getOctets());
        }

        if (obj instanceof ASN1TaggedObject)
        {
            return getInstance(((ASN1TaggedObject)obj).getObject());
        }

        throw new IllegalArgumentException("illegal object in getInstance: " + obj.getClass().getName());
    }

    /**
     * return an Object Identifier from a tagged object.
     *
     * @param obj the tagged object holding the object we want
     * @param explicit true if the object is meant to be explicitly
     *              tagged false otherwise.
     * @exception IllegalArgumentException if the tagged object cannot
     *               be converted.
     */
    public static DERObjectIdentifier getInstance(
        ASN1TaggedObject obj,
        boolean          explicit)
    {
        return getInstance(obj.getObject());
    }
    

    DERObjectIdentifier(
        byte[]  bytes)
    {
        StringBuffer    objId = new StringBuffer();
        long            value = 0;
        BigInteger      bigValue = null;
        boolean         first = true;

        for (int i = 0; i != bytes.length; i++)
        {
            int b = bytes[i] & 0xff;

            if (value < 0x80000000000000L) 
            {
                value = value * 128 + (b & 0x7f);
                if ((b & 0x80) == 0)             // end of number reached
                {
                    if (first)
                    {
                        switch ((int)value / 40)
                        {
                        case 0:
                            objId.append('0');
                            break;
                        case 1:
                            objId.append('1');
                            value -= 40;
                            break;
                        default:
                            objId.append('2');
                            value -= 80;
                        }
                        first = false;
                    }

                    objId.append('.');
                    objId.append(value);
                    value = 0;
                }
            } 
            else 
            {
                if (bigValue == null)
                {
                    bigValue = BigInteger.valueOf(value);
                }
                bigValue = bigValue.shiftLeft(7);
                bigValue = bigValue.or(BigInteger.valueOf(b & 0x7f));
                if ((b & 0x80) == 0) 
                {
                    objId.append('.');
                    objId.append(bigValue);
                    bigValue = null;
                    value = 0;
                }
            }
        }

        this.identifier = objId.toString();
    }

    public DERObjectIdentifier(
        String  identifier)
    {
        if (!isValidIdentifier(identifier))
        {
            throw new IllegalArgumentException("string " + identifier + " not an OID");
        }

        this.identifier = identifier;
    }

    public String getId()
    {
        return identifier;
    }

    private void writeField(
        OutputStream    out,
        long            fieldValue)
        throws IOException
    {
        if (fieldValue >= (1L << 7))
        {
            if (fieldValue >= (1L << 14))
            {
                if (fieldValue >= (1L << 21))
                {
                    if (fieldValue >= (1L << 28))
                    {
                        if (fieldValue >= (1L << 35))
                        {
                            if (fieldValue >= (1L << 42))
                            {
                                if (fieldValue >= (1L << 49))
                                {
                                    if (fieldValue >= (1L << 56))
                                    {
                                        out.write((int)(fieldValue >> 56) | 0x80);
                                    }
                                    out.write((int)(fieldValue >> 49) | 0x80);
                                }
                                out.write((int)(fieldValue >> 42) | 0x80);
                            }
                            out.write((int)(fieldValue >> 35) | 0x80);
                        }
                        out.write((int)(fieldValue >> 28) | 0x80);
                    }
                    out.write((int)(fieldValue >> 21) | 0x80);
                }
                out.write((int)(fieldValue >> 14) | 0x80);
            }
            out.write((int)(fieldValue >> 7) | 0x80);
        }
        out.write((int)fieldValue & 0x7f);
    }

    private void writeField(
        OutputStream    out,
        BigInteger      fieldValue)
        throws IOException
    {
        int byteCount = (fieldValue.bitLength()+6)/7;
        if (byteCount == 0) 
        {
            out.write(0);
        }  
        else 
        {
            BigInteger tmpValue = fieldValue;
            byte[] tmp = new byte[byteCount];
            for (int i = byteCount-1; i >= 0; i--) 
            {
                tmp[i] = (byte) ((tmpValue.intValue() & 0x7f) | 0x80);
                tmpValue = tmpValue.shiftRight(7); 
            }
            tmp[byteCount-1] &= 0x7f;
            out.write(tmp);
        }

    }

    void encode(
        DEROutputStream out)
        throws IOException
    {
        OIDTokenizer            tok = new OIDTokenizer(identifier);
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        DEROutputStream         dOut = new DEROutputStream(bOut);

        writeField(bOut, 
                    Integer.parseInt(tok.nextToken()) * 40
                    + Integer.parseInt(tok.nextToken()));

        while (tok.hasMoreTokens())
        {
            String token = tok.nextToken();
            if (token.length() < 18) 
            {
                writeField(bOut, Long.parseLong(token));
            }
            else
            {
                writeField(bOut, new BigInteger(token));
            }
        }

        dOut.close();

        byte[]  bytes = bOut.toByteArray();

        out.writeEncoded(OBJECT_IDENTIFIER, bytes);
    }

    public int hashCode()
    {
        return identifier.hashCode();
    }

    boolean asn1Equals(
        DERObject  o)
    {
        if (!(o instanceof DERObjectIdentifier))
        {
            return false;
        }

        return identifier.equals(((DERObjectIdentifier)o).identifier);
    }

    public String toString()
    {
        return getId();
    }

    private static boolean isValidIdentifier(
        String identifier)
    {
        if (identifier.length() < 3
            || identifier.charAt(1) != '.')
        {
            return false;
        }

        char first = identifier.charAt(0);
        if (first < '0' || first > '2')
        {
            return false;
        }

        boolean periodAllowed = false;
        for (int i = identifier.length() - 1; i >= 2; i--)
        {
            char ch = identifier.charAt(i);

            if ('0' <= ch && ch <= '9')
            {
                periodAllowed = true;
                continue;
            }

            if (ch == '.')
            {
                if (!periodAllowed)
                {
                    return false;
                }

                periodAllowed = false;
                continue;
            }

            return false;
        }

        return periodAllowed;
    }
}
