package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.statistics.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.format.DateTimeParseException

@Singleton
class ApiProviderCollectionsRepository @Inject constructor(private val api: ProviderCollectionsApi) : ProviderCollectionsRepository {
    override suspend fun getCollections(query: ActivityQuery): CollectionsOutcome = try {
        val granularity = query.granularity.name.lowercase(java.util.Locale.ROOT)
        val collections = api.getCollections(query.from.toString(), query.to.toString(), granularity, query.comparePrevious).toDomain()
        require(collections.period.from == query.from && collections.period.to == query.to && collections.period.granularity == granularity)
        require((collections.comparison != null) == query.comparePrevious)
        CollectionsOutcome.Success(collections)
    } catch (e: CancellationException) { throw e
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> CollectionsOutcome.Failure.InvalidQuery
            401 -> CollectionsOutcome.Failure.Unauthorized
            403 -> CollectionsOutcome.Failure.Forbidden
            404 -> CollectionsOutcome.Failure.NotFound
            else -> CollectionsOutcome.Failure.Server(e.code())
        }
    } catch (e: SerializationException) { CollectionsOutcome.Failure.Malformed
    } catch (e: DateTimeParseException) { CollectionsOutcome.Failure.Malformed
    } catch (e: IllegalArgumentException) { CollectionsOutcome.Failure.Malformed
    } catch (e: ArithmeticException) { CollectionsOutcome.Failure.Malformed
    } catch (e: IOException) { CollectionsOutcome.Failure.Network
    } catch (e: Exception) { CollectionsOutcome.Failure.Unknown }
}
