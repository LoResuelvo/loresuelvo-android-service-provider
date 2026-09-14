package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetConversationsUseCaseTest {

    @Test
    fun returns_the_repository_result_unchanged() = runTest {
        val expected = ConversationsOutcome.Success(emptyList())

        val actual = GetConversationsUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome = expected
            },
        )()

        assertEquals(expected, actual)
    }
}
