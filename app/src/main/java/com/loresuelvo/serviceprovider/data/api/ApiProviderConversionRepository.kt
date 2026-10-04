package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.statistics.ProviderConversionRepository
import com.loresuelvo.serviceprovider.domain.statistics.ConversionOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter

@Singleton
class ApiProviderConversionRepository @Inject constructor(private val api: ProviderConversionApi) : ProviderConversionRepository {
    override suspend fun getConversion(query: com.loresuelvo.serviceprovider.domain.statistics.ConversionQuery): ConversionOutcome = try {
        ConversionOutcome.Success(api.getConversion(query.from?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), query.to?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).toDomain())
    } catch (e: CancellationException) { throw e
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> ConversionOutcome.Failure.InvalidQuery
            401 -> ConversionOutcome.Failure.Unauthorized
            403 -> ConversionOutcome.Failure.Forbidden
            404 -> ConversionOutcome.Failure.NotFound
            else -> ConversionOutcome.Failure.Server(e.code())
        }
    } catch (e: SerializationException) { ConversionOutcome.Failure.Malformed
    } catch (e: DateTimeParseException) { ConversionOutcome.Failure.Malformed
    } catch (e: IllegalArgumentException) { ConversionOutcome.Failure.Malformed
    } catch (e: ArithmeticException) { ConversionOutcome.Failure.Malformed
    } catch (e: IOException) { ConversionOutcome.Failure.Network
    } catch (e: Exception) { ConversionOutcome.Failure.Unknown }
}
