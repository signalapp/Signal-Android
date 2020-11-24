package org.whispersystems.signalservice.loki.protocol.closedgroups

enum class ClosedGroupRatchetCollectionType { Old, Current  }

interface SharedSenderKeysDatabaseProtocol {

    // region Ratchets & Sender Keys
    fun getClosedGroupRatchet(groupPublicKey: String, senderPublicKey: String, collection: ClosedGroupRatchetCollectionType): ClosedGroupRatchet?
    fun setClosedGroupRatchet(groupPublicKey: String, senderPublicKey: String, ratchet: ClosedGroupRatchet, collection: ClosedGroupRatchetCollectionType)
    fun removeAllClosedGroupRatchets(groupPublicKey: String, collection: ClosedGroupRatchetCollectionType)
    fun getAllClosedGroupRatchets(groupPublicKey: String, collection: ClosedGroupRatchetCollectionType): Set<Pair<String, ClosedGroupRatchet>>
    fun getAllClosedGroupSenderKeys(groupPublicKey: String, collection: ClosedGroupRatchetCollectionType): Set<ClosedGroupSenderKey>
    // endregion

    // region Private & Public Keys
    fun getClosedGroupPrivateKey(groupPublicKey: String): String?
    fun setClosedGroupPrivateKey(groupPublicKey: String, groupPrivateKey: String)
    fun removeClosedGroupPrivateKey(groupPublicKey: String)
    fun getAllClosedGroupPublicKeys(): Set<String>
    // endregion

    // region Convenience
    fun isSSKBasedClosedGroup(groupPublicKey: String): Boolean
    // endregion
}
