package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class NhtsaResponse(
    @Json(name = "Count") val count: Int,
    @Json(name = "Message") val message: String,
    @Json(name = "SearchCriteria") val searchCriteria: String,
    @Json(name = "Results") val results: List<NhtsaVinDetails>
)

@JsonClass(generateAdapter = true)
data class NhtsaVinDetails(
    @Json(name = "Make") val make: String? = null,
    @Json(name = "Model") val model: String? = null,
    @Json(name = "ModelYear") val modelYear: String? = null,
    @Json(name = "VehicleType") val vehicleType: String? = null,
    @Json(name = "BodyClass") val bodyClass: String? = null,
    @Json(name = "DisplacementCC") val displacementCC: String? = null,
    @Json(name = "EngineHP") val engineHP: String? = null,
    @Json(name = "DriveType") val driveType: String? = null,
    @Json(name = "ErrorCode") val errorCode: String? = null,
    @Json(name = "ErrorText") val errorText: String? = null
)

interface NhtsaApiService {
    @GET("api/vehicles/DecodeVinValues/{vin}")
    suspend fun decodeVin(
        @Path("vin") vin: String,
        @Query("format") format: String = "json"
    ): NhtsaResponse
}

object NhtsaClient {
    private const val BASE_URL = "https://vpic.nhtsa.dot.gov/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: NhtsaApiService = retrofit.create(NhtsaApiService::class.java)

    suspend fun decodeVinDetails(vin: String): NhtsaVinDetails? {
        return try {
            val response = apiService.decodeVin(vin)
            response.results.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
