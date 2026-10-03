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
class ApiProviderActivityRepository @Inject constructor(private val api: ProviderActivityApi) : ProviderActivityRepository {
    override suspend fun getActivity(query: ActivityQuery): ActivityOutcome = try {
        val granularity = query.granularity.name.lowercase(java.util.Locale.ROOT)
        val activity = api.getActivity(query.from.toString(), query.to.toString(), granularity, query.comparePrevious).toDomain()
        require(activity.period.from == query.from && activity.period.to == query.to && activity.period.granularity == granularity)
        require((activity.comparison != null) == query.comparePrevious)
        ActivityOutcome.Success(activity)
    } catch (e: CancellationException) { throw e
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> ActivityOutcome.Failure.InvalidQuery
            401 -> ActivityOutcome.Failure.Unauthorized
            403 -> ActivityOutcome.Failure.Forbidden
            404 -> ActivityOutcome.Failure.NotFound
            else -> ActivityOutcome.Failure.Server(e.code())
        }
    } catch (e: SerializationException) { ActivityOutcome.Failure.Malformed
    } catch (e: DateTimeParseException) { ActivityOutcome.Failure.Malformed
    } catch (e: IllegalArgumentException) { ActivityOutcome.Failure.Malformed
    } catch (e: IOException) { ActivityOutcome.Failure.Network
    } catch (e: Exception) { ActivityOutcome.Failure.Unknown }
}
