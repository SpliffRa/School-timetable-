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

import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

/**
 * Единственный HTTP-клиент приложения.
 * Создаётся один раз (object-уровень) и переиспользуется.
 */
val httpClient: HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000L
        connectTimeoutMillis = 15_000L
        socketTimeoutMillis = 20_000L
    }
    defaultRequest {
        header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 SchoolSchedule/3.7")
        header("Accept", "application/json, text/plain, */*")
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

