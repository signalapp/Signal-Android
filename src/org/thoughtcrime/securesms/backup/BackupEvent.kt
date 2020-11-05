package org.thoughtcrime.securesms.backup

data class BackupEvent constructor(val type: Type, val count: Int, val exception: Exception?) {

    enum class Type {
        PROGRESS, FINISHED
    }

    companion object {
        @JvmStatic fun createProgress(count: Int) = BackupEvent(Type.PROGRESS, count, null)
        @JvmStatic fun createFinished() = BackupEvent(Type.FINISHED, 0, null)
        @JvmStatic fun createFinished(e: Exception?) = BackupEvent(Type.FINISHED, 0, e)
    }
}