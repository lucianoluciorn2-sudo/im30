package br.com.jetinopay.network

import br.com.jetinopay.network.models.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface JetinoPayService {

    @POST("api/pagamento")
    suspend fun criarPagamento(
        @Body request: CreatePaymentRequest
    ): Response<CreatePaymentResponse>

    @GET("api/status/{id}")
    suspend fun buscarStatus(
        @Path("id") id: String
    ): Response<StatusResponse>

    @POST("api/webhook")
    suspend fun enviarWebhook(
        @Body request: WebhookRequest
    ): Response<WebhookResponse>
}

object JetinoPayApi {

    private lateinit var service: JetinoPayService

    fun init(baseUrl: String) {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        service = retrofit.create(JetinoPayService::class.java)
    }

    fun getService(): JetinoPayService = service
}
