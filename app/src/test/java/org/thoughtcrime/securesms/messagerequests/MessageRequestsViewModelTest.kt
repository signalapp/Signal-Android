package org.thoughtcrime.securesms.messagerequests

import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.thoughtcrime.securesms.BaseViewModelTest
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.repository.ConversationRepository

class MessageRequestsViewModelTest : BaseViewModelTest() {

    private val repository = mock(ConversationRepository::class.java)

    private val viewModel: MessageRequestsViewModel by lazy {
        MessageRequestsViewModel(repository)
    }

    @Test
    fun `should delete message request`() = runBlockingTest {
        val thread = mock(ThreadRecord::class.java)

        viewModel.deleteMessageRequest(thread)

        verify(repository).deleteMessageRequest(thread)
    }

    @Test
    fun `should clear all message requests`() = runBlockingTest {
        viewModel.clearAllMessageRequests()

        verify(repository).clearAllMessageRequests()
    }

}