package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/**
 * Generalized time object.
 */
public class DERGeneralizedTime
    extends ASN1Object
{
    String      time;

    /**
     * return a generalized time from the passed in object
     *
     * @exception IllegalArgumentException if the object cannot be converted.
     */
    public static DERGeneralizedTime getInstance(
        Object  obj)
    {
        if (obj == null || obj instanceof DERGeneralizedTime)
        {
            return (DERGeneralizedTime)obj;
        }

        if (obj instanceof ASN1OctetString)
        {
            return new DERGeneralizedTime(((ASN1OctetString)obj).getOctets());
        }

        throw new IllegalArgumentException("illegal object in getInstance: " + obj.getClass().getName());
    }

    /**
     * return a Generalized Time object from a tagged object.
     *
     * @param obj the tagged object holding the object we want
     * @param explicit true if the object is meant to be explicitly
     *              tagged false otherwise.
     * @exception IllegalArgumentException if the tagged object cannot
     *               be converted.
     */
    public static DERGeneralizedTime getInstance(
        ASN1TaggedObject obj,
        boolean          explicit)
    {
        return getInstance(obj.getObject());
    }
    
    /**
     * The correct format for this is YYYYMMDDHHMMSS[.f]Z, or without the Z
     * for local time, or Z+-HHMM on the end, for difference between local
     * time and UTC time. The fractional second amount f must consist of at
     * least one number with trailing zeroes removed.
     *
     * @param time the time string.
     * @exception IllegalArgumentException if String is an illegal format.
     */
    public DERGeneralizedTime(
        String  time)
    {
        this.time = time;
        try
        {
            this.getDate();
        }
        catch (ParseException e)
        {
            throw new IllegalArgumentException("invalid date string: " + e.getMessage());
        }
    }

    /**
     * base constructer from a java.util.date object
     */
    public DERGeneralizedTime(
        Date time)
    {
        SimpleDateFormat dateF = new SimpleDateFormat("yyyyMMddHHmmss'Z'");

        dateF.setTimeZone(new SimpleTimeZone(0,"Z"));

        this.time = dateF.format(time);
    }

    DERGeneralizedTime(
        byte[]  bytes)
    {
        //
        // explicitly convert to characters
        //
        char[]  dateC = new char[bytes.length];

        for (int i = 0; i != dateC.length; i++)
        {
            dateC[i] = (char)(bytes[i] & 0xff);
        }

        this.time = new String(dateC);
    }

    /**
     * Return the time.
     * @return The time string as it appeared in the encoded object.
     */
    public String getTimeString()
    {
        return time;
    }
    
    /**
     * return the time - always in the form of 
     *  YYYYMMDDhhmmssGMT(+hh:mm|-hh:mm).
     * <p>
     * Normally in a certificate we would expect "Z" rather than "GMT",
     * however adding the "GMT" means we can just use:
     * <pre>
     *     dateF = new SimpleDateFormat("yyyyMMddHHmmssz");
     * </pre>
     * To read in the time and get a date which is compatible with our local
     * time zone.
     */
    public String getTime()
    {
        //
        // standardise the format.
        //             
        if (time.charAt(time.length() - 1) == 'Z')
        {
            return time.substring(0, time.length() - 1) + "GMT+00:00";
        }
        else
        {
            int signPos = time.length() - 5;
            char sign = time.charAt(signPos);
            if (sign == '-' || sign == '+')
            {
                return time.substring(0, signPos)
                    + "GMT"
                    + time.substring(signPos, signPos + 3)
                    + ":"
                    + time.substring(signPos + 3);
            }
            else
            {
                signPos = time.length() - 3;
                sign = time.charAt(signPos);
                if (sign == '-' || sign == '+')
                {
                    return time.substring(0, signPos)
                        + "GMT"
                        + time.substring(signPos)
                        + ":00";
                }
            }
        }            
        return time + calculateGMTOffset();
    }

    private String calculateGMTOffset()
    {
        String sign = "+";
        TimeZone timeZone = TimeZone.getDefault();
        int offset = timeZone.getRawOffset();
        if (offset < 0)
        {
            sign = "-";
            offset = -offset;
        }
        int hours = offset / (60 * 60 * 1000);
        int minutes = (offset - (hours * 60 * 60 * 1000)) / (60 * 1000);

        try
        {
            if (timeZone.useDaylightTime() && timeZone.inDaylightTime(this.getDate()))
            {
                hours += sign.equals("+") ? 1 : -1;
            }
        }
        catch (ParseException e)
        {
            // we'll do our best and ignore daylight savings
        }

        return "GMT" + sign + convert(hours) + ":" + convert(minutes);
    }

    private String convert(int time)
    {
        if (time < 10)
        {
            return "0" + time;
        }

        return Integer.toString(time);
    }

    public Date getDate()
        throws ParseException
    {
        SimpleDateFormat dateF;
        String d = time;

        if (time.endsWith("Z"))
        {
            if (hasFractionalSeconds())
            {
                dateF = new SimpleDateFormat("yyyyMMddHHmmss.SSS'Z'");
            }
            else
            {
                dateF = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
            }

            dateF.setTimeZone(new SimpleTimeZone(0, "Z"));
        }
        else if (time.indexOf('-') > 0 || time.indexOf('+') > 0)
        {
            d = this.getTime();
            if (hasFractionalSeconds())
            {
                dateF = new SimpleDateFormat("yyyyMMddHHmmss.SSSz");
            }
            else
            {
                dateF = new SimpleDateFormat("yyyyMMddHHmmssz");
            }

            dateF.setTimeZone(new SimpleTimeZone(0, "Z"));
        }
        else
        {
            if (hasFractionalSeconds())
            {
                dateF = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
            }
            else
            {
                dateF = new SimpleDateFormat("yyyyMMddHHmmss");
            }

            dateF.setTimeZone(new SimpleTimeZone(0, TimeZone.getDefault().getID()));
        }

        if (hasFractionalSeconds())
        {
            // java misinterprets extra digits as being milliseconds...
            String frac = d.substring(14);
            int    index;
            for (index = 1; index < frac.length(); index++)
            {
                char ch = frac.charAt(index);
                if (!('0' <= ch && ch <= '9'))
                {
                    break;        
                }
            }
            if (index - 1 > 3)
            {
                frac = frac.substring(0, 4) + frac.substring(index);
                d = d.substring(0, 14) + frac;
            }
        }

        return dateF.parse(d);
    }

    private boolean hasFractionalSeconds()
    {
        return time.indexOf('.') == 14;
    }

    private byte[] getOctets()
    {
        char[]  cs = time.toCharArray();
        byte[]  bs = new byte[cs.length];

        for (int i = 0; i != cs.length; i++)
        {
            bs[i] = (byte)cs[i];
        }

        return bs;
    }


    void encode(
        DEROutputStream  out)
        throws IOException
    {
        out.writeEncoded(GENERALIZED_TIME, this.getOctets());
    }
    
    boolean asn1Equals(
        DERObject  o)
    {
        if (!(o instanceof DERGeneralizedTime))
        {
            return false;
        }

        return time.equals(((DERGeneralizedTime)o).time);
    }
    
    public int hashCode()
    {
        return time.hashCode();
    }
}
