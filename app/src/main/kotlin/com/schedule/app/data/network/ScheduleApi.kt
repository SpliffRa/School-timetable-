package com.schedule.app.data.network

import com.schedule.app.data.model.Schedule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Единственный HTTP-клиент приложения.
 * Создаётся один раз (object-уровень) и переиспользуется.
 */
val httpClient: HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
    }
}

/**
 * Загружает расписание с указанного хоста/порта.
 * Вызывать из IO-диспетчера.
 */
suspend fun fetchSchedule(host: String, port: Int): Schedule =
    httpClient.get("http://$host:$port/schedule").body()
