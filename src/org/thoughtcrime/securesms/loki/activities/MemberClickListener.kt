package org.thoughtcrime.securesms.loki.activities

@FunctionalInterface
interface MemberClickListener {
    fun onMemberClick(member: String)
}