package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

/**
 * ASN.1 TaggedObject - in ASN.1 notation this is any object preceded by
 * a [n] where n is some number - these are assumed to follow the construction
 * rules (as with sequences).
 */
public abstract class ASN1TaggedObject
    extends ASN1Object
    implements ASN1TaggedObjectParser
{
    int             tagNo;
    boolean         empty = false;
    boolean         explicit = true;
    DEREncodable    obj = null;

    static public ASN1TaggedObject getInstance(
        ASN1TaggedObject    obj,
        boolean             explicit)
    {
        if (explicit)
        {
            return (ASN1TaggedObject)obj.getObject();
        }

        throw new IllegalArgumentException("implicitly tagged tagged object");
    }

    static public ASN1TaggedObject getInstance(
        Object obj) 
    {
        if (obj == null || obj instanceof ASN1TaggedObject) 
        {
                return (ASN1TaggedObject)obj;
        }

        throw new IllegalArgumentException("unknown object in getInstance: " + obj.getClass().getName());
    }

    /**
     * Create a tagged object in the explicit style.
     * 
     * @param tagNo the tag number for this object.
     * @param obj the tagged object.
     */
    public ASN1TaggedObject(
        int             tagNo,
        DEREncodable    obj)
    {
        this.explicit = true;
        this.tagNo = tagNo;
        this.obj = obj;
    }

    /**
     * Create a tagged object with the style given by the value of explicit.
     * <p>
     * If the object implements ASN1Choice the tag style will always be changed
     * to explicit in accordance with the ASN.1 encoding rules.
     * </p>
     * @param explicit true if the object is explicitly tagged.
     * @param tagNo the tag number for this object.
     * @param obj the tagged object.
     */
    public ASN1TaggedObject(
        boolean         explicit,
        int             tagNo,
        DEREncodable    obj)
    {
        if (obj instanceof ASN1Choice)
        {
            this.explicit = true;
        }
        else
        {
            this.explicit = explicit;
        }
        
        this.tagNo = tagNo;
        this.obj = obj;
    }
    
    boolean asn1Equals(
        DERObject o)
    {
        if (!(o instanceof ASN1TaggedObject))
        {
            return false;
        }
        
        ASN1TaggedObject other = (ASN1TaggedObject)o;
        
        if (tagNo != other.tagNo || empty != other.empty || explicit != other.explicit)
        {
            return false;
        }
        
        if(obj == null)
        {
            if (other.obj != null)
            {
                return false;
            }
        }
        else
        {
            if (!(obj.getDERObject().equals(other.obj.getDERObject())))
            {
                return false;
            }
        }
        
        return true;
    }
    
    public int hashCode()
    {
        int code = tagNo;

        // TODO: actually this is wrong - the problem is that a re-encoded
        // object may end up with a different hashCode due to implicit
        // tagging. As implicit tagging is ambiguous if a sequence is involved
        // it seems the only correct method for both equals and hashCode is to
        // compare the encodings...
        if (obj != null)
        {
            code ^= obj.hashCode();
        }

        return code;
    }

    public int getTagNo()
    {
        return tagNo;
    }

    /**
     * return whether or not the object may be explicitly tagged. 
     * <p>
     * Note: if the object has been read from an input stream, the only
     * time you can be sure if isExplicit is returning the true state of
     * affairs is if it returns false. An implicitly tagged object may appear
     * to be explicitly tagged, so you need to understand the context under
     * which the reading was done as well, see getObject below.
     */
    public boolean isExplicit()
    {
        return explicit;
    }

    public boolean isEmpty()
    {
        return empty;
    }

    /**
     * return whatever was following the tag.
     * <p>
     * Note: tagged objects are generally context dependent if you're
     * trying to extract a tagged object you should be going via the
     * appropriate getInstance method.
     */
    public DERObject getObject()
    {
        if (obj != null)
        {
            return obj.getDERObject();
        }

        return null;
    }

    /**
     * Return the object held in this tagged object as a parser assuming it has
     * the type of the passed in tag. If the object doesn't have a parser
     * associated with it, the base object is returned.
     */
    public DEREncodable getObjectParser(
        int     tag,
        boolean isExplicit)
    {
        switch (tag)
        {
        case DERTags.SET:
            return ASN1Set.getInstance(this, isExplicit).parser();
        case DERTags.SEQUENCE:
            return ASN1Sequence.getInstance(this, isExplicit).parser();
        case DERTags.OCTET_STRING:
            return ASN1OctetString.getInstance(this, isExplicit).parser();
        }

        if (isExplicit)
        {
            return getObject();
        }

        throw new RuntimeException("implicit tagging not implemented for tag: " + tag);
    }

    abstract void encode(DEROutputStream  out)
        throws IOException;

    public String toString()
    {
        return "[" + tagNo + "]" + obj;
    }
}
