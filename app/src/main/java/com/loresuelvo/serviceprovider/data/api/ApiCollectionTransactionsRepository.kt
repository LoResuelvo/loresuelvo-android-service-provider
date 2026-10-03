package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.statistics.*
import java.io.IOException
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

@Singleton
class ApiCollectionTransactionsRepository @Inject constructor(private val api: CollectionTransactionsApi) : CollectionTransactionsRepository {
    override suspend fun getTransactions(query: CollectionTransactionsQuery): CollectionTransactionsOutcome = try {
        val purpose = query.purpose?.name?.lowercase(java.util.Locale.ROOT)
        val page = api.getTransactions(query.from.toString(), query.to.toString(), purpose, query.limit, query.cursor).toDomain()
        require(page.from == query.from && page.to == query.to && page.transactions.size <= query.limit)
        require(query.purpose == null || page.transactions.all { it.purpose == query.purpose })
        require(page.nextCursor == null || page.nextCursor != query.cursor)
        CollectionTransactionsOutcome.Success(page)
    } catch (e: CancellationException) { throw e
    } catch (e: HttpException) {
        CollectionTransactionsOutcome.Failure(when (e.code()) {
            400 -> CollectionsOutcome.Failure.InvalidQuery
            401 -> CollectionsOutcome.Failure.Unauthorized
            403 -> CollectionsOutcome.Failure.Forbidden
            404 -> CollectionsOutcome.Failure.NotFound
            else -> CollectionsOutcome.Failure.Server(e.code())
        })
    } catch (e: SerializationException) { failure(CollectionsOutcome.Failure.Malformed)
    } catch (e: DateTimeParseException) { failure(CollectionsOutcome.Failure.Malformed)
    } catch (e: IllegalArgumentException) { failure(CollectionsOutcome.Failure.Malformed)
    } catch (e: ArithmeticException) { failure(CollectionsOutcome.Failure.Malformed)
    } catch (e: IOException) { failure(CollectionsOutcome.Failure.Network)
    } catch (e: Exception) { failure(CollectionsOutcome.Failure.Unknown) }

    private fun failure(reason: CollectionsOutcome.Failure) = CollectionTransactionsOutcome.Failure(reason)
}
