package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.statistics.ProviderReputationRepository
import com.loresuelvo.serviceprovider.domain.statistics.ReputationOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.format.DateTimeParseException

@Singleton
class ApiProviderReputationRepository @Inject constructor(private val api: ProviderReputationApi) : ProviderReputationRepository {
    override suspend fun getReputation(): ReputationOutcome = try {
        ReputationOutcome.Success(api.getReputation(20).toDomain())
    } catch (e: CancellationException) { throw e
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> ReputationOutcome.Failure.InvalidQuery
            401 -> ReputationOutcome.Failure.Unauthorized
            403 -> ReputationOutcome.Failure.Forbidden
            404 -> ReputationOutcome.Failure.NotFound
            else -> ReputationOutcome.Failure.Server(e.code())
        }
    } catch (e: SerializationException) { ReputationOutcome.Failure.Malformed
    } catch (e: DateTimeParseException) { ReputationOutcome.Failure.Malformed
    } catch (e: IllegalArgumentException) { ReputationOutcome.Failure.Malformed
    } catch (e: ArithmeticException) { ReputationOutcome.Failure.Malformed
    } catch (e: IOException) { ReputationOutcome.Failure.Network
    } catch (e: Exception) { ReputationOutcome.Failure.Unknown }
}
