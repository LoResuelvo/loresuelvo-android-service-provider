package com.loresuelvo.serviceprovider.domain.usecase.statistics

import com.loresuelvo.serviceprovider.domain.statistics.CollectionTransactionsQuery
import com.loresuelvo.serviceprovider.domain.statistics.CollectionTransactionsRepository

class GetCollectionTransactionsUseCase(private val repository: CollectionTransactionsRepository) {
    suspend operator fun invoke(query: CollectionTransactionsQuery) = repository.getTransactions(query)
}
