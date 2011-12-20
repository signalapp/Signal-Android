package org.thoughtcrime.bouncycastle.asn1;

class DERFactory
{
    static final DERSequence EMPTY_SEQUENCE = new DERSequence();
    static final DERSet EMPTY_SET = new DERSet();

    static DERSequence createSequence(ASN1EncodableVector v)
    {
        return v.size() < 1 ? EMPTY_SEQUENCE : new DERSequence(v);
    }

    static DERSet createSet(ASN1EncodableVector v)
    {
        return v.size() < 1 ? EMPTY_SET : new DERSet(v);
    }

    static DERSet createSet(ASN1EncodableVector v, boolean needsSorting)
    {
        return v.size() < 1 ? EMPTY_SET : new DERSet(v, needsSorting);
    }
}
