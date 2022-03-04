package org.thoughtcrime.securesms.conversation.v2

import kotlinx.coroutines.flow.first
import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anySet
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.BaseViewModelTest
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.repository.ResultOf
import org.mockito.Mockito.`when` as whenever

class ConversationViewModelTest: BaseViewModelTest() {

    private val repository = mock(ConversationRepository::class.java)

    private val threadId = 123L
    private lateinit var recipient: Recipient

    private val viewModel: ConversationViewModel by lazy {
        ConversationViewModel(threadId, repository)
    }

    @Before
    fun setUp() {
        recipient = mock(Recipient::class.java)
        whenever(repository.isOxenHostedOpenGroup(anyLong())).thenReturn(true)
        whenever(repository.getRecipientForThreadId(anyLong())).thenReturn(recipient)
    }

    @Test
    fun `should emit group type on init`() = runBlockingTest {
        assertTrue(viewModel.uiState.first().isOxenHostedOpenGroup)
    }

    @Test
    fun `should save draft message`() {
        val draft = "Hi there"

        viewModel.saveDraft(draft)

        verify(repository).saveDraft(threadId, draft)
    }

    @Test
    fun `should retrieve draft message`() {
        val draft = "Hi there"
        whenever(repository.getDraft(anyLong())).thenReturn(draft)

        val result = viewModel.getDraft()

        verify(repository).getDraft(threadId)
        assertThat(result, equalTo(draft))
    }

    @Test
    fun `should invite contacts`() {
        val contacts = listOf<Recipient>()

        viewModel.inviteContacts(contacts)

        verify(repository).inviteContacts(threadId, contacts)
    }

    @Test
    fun `should unblock contact recipient`() {
        whenever(recipient.isContactRecipient).thenReturn(true)

        viewModel.unblock()

        verify(repository).unblock(recipient)
    }

    @Test
    fun `should delete locally`() {
        val message = mock(MessageRecord::class.java)

        viewModel.deleteLocally(message)

        verify(repository).deleteLocally(recipient, message)
    }

    @Test
    fun `should emit error message on failure to delete a message for everyone`() = runBlockingTest {
        val message = mock(MessageRecord::class.java)
        val error = Throwable()
        whenever(repository.deleteForEveryone(anyLong(), any(), any()))
            .thenReturn(ResultOf.Failure(error))

        viewModel.deleteForEveryone(message)

        assertThat(viewModel.uiState.first().uiMessages.first().message, endsWith("$error"))
    }

    @Test
    fun `should emit error message on failure to delete messages without unsend request`() =
        runBlockingTest {
            val message = mock(MessageRecord::class.java)
            val error = Throwable()
            whenever(repository.deleteMessageWithoutUnsendRequest(anyLong(), anySet()))
                .thenReturn(ResultOf.Failure(error))

            viewModel.deleteMessagesWithoutUnsendRequest(setOf(message))

            assertThat(viewModel.uiState.first().uiMessages.first().message, endsWith("$error"))
        }

    @Test
    fun `should emit error message on ban user failure`() = runBlockingTest {
        val error = Throwable()
        whenever(repository.banUser(anyLong(), any())).thenReturn(ResultOf.Failure(error))

        viewModel.banUser(recipient)

        assertThat(viewModel.uiState.first().uiMessages.first().message, endsWith("$error"))
    }

    @Test
    fun `should emit a message on ban user success`() = runBlockingTest {
        whenever(repository.banUser(anyLong(), any())).thenReturn(ResultOf.Success(Unit))

        viewModel.banUser(recipient)

        assertThat(
            viewModel.uiState.first().uiMessages.first().message,
            equalTo("Successfully banned user")
        )
    }

    @Test
    fun `should emit error message on ban user and delete all failure`() = runBlockingTest {
        val error = Throwable()
        whenever(repository.banAndDeleteAll(anyLong(), any())).thenReturn(ResultOf.Failure(error))

        viewModel.banAndDeleteAll(recipient)

        assertThat(viewModel.uiState.first().uiMessages.first().message, endsWith("$error"))
    }

    @Test
    fun `should emit a message on ban user and delete all success`() = runBlockingTest {
        whenever(repository.banAndDeleteAll(anyLong(), any())).thenReturn(ResultOf.Success(Unit))

        viewModel.banAndDeleteAll(recipient)

        assertThat(
            viewModel.uiState.first().uiMessages.first().message,
            equalTo("Successfully banned user and deleted all their messages")
        )
    }

    @Test
    fun `should accept message request`() = runBlockingTest {
        viewModel.acceptMessageRequest()

        verify(repository).acceptMessageRequest(threadId, recipient)
    }

    @Test
    fun `should decline message request`() {
        viewModel.declineMessageRequest()

        verify(repository).declineMessageRequest(threadId, recipient)
    }

    @Test
    fun `should remove shown message`() = runBlockingTest {
        // Given that a message is generated
        whenever(repository.banUser(anyLong(), any())).thenReturn(ResultOf.Success(Unit))
        viewModel.banUser(recipient)
        assertThat(viewModel.uiState.value.uiMessages.size, equalTo(1))
        // When the message is shown
        viewModel.messageShown(viewModel.uiState.first().uiMessages.first().id)
        // Then it should be removed
        assertThat(viewModel.uiState.value.uiMessages.size, equalTo(0))
    }

}