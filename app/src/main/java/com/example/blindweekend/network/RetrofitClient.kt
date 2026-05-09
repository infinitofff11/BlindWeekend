package com.example.blindweekend.network

import com.example.blindweekend.BuildConfig
import com.example.blindweekend.auth.AuthManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 网络客户端单例
 */
object RetrofitClient {

    private const val TIMEOUT_SECONDS = 30L

    val api: BlindWeekendApi by lazy {
        createApi()
    }

    /**
     * Token拦截器 - 自动在请求头中附加登录Token
     */
    private val authInterceptor = Interceptor { chain ->
        val token = AuthManager.getToken()
        val request = if (token.isNotEmpty()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private fun createApi(): BlindWeekendApi {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BlindWeekendApi::class.java)
    }
}
