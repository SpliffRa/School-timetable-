package com.schedule.app.data.network

import com.schedule.app.data.model.BackpackState
import com.schedule.app.data.model.Schedule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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

/**
 * Загружает состояние рюкзака с указанного хоста/порта.
 */
suspend fun fetchBackpack(host: String, port: Int): BackpackState =
    httpClient.get("http://$host:$port/backpack").body()

/**
 * Отправляет состояние рюкзака на указанный хост/порт.
 */
suspend fun sendBackpack(host: String, port: Int, state: BackpackState): Boolean =
    try {
        val resp = httpClient.post("http://$host:$port/backpack") {
            contentType(ContentType.Application.Json)
            setBody(state)
        }
        resp.status.value in 200..299
    } catch (_: Exception) {
        false
    }

