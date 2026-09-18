package com.schedule.app.data.network

import com.schedule.app.data.model.Schedule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference

const val SERVER_PORT = 8080

/**
 * Встроенный HTTP-сервер приложения.
 * Запускается на устройстве-сервере (папа), отдаёт расписание по GET /schedule.
 *
 * Используется AtomicReference, чтобы безопасно обновлять расписание
 * из любого потока без пересоздания сервера.
 */
class ScheduleServer {

    private var engine: ApplicationEngine? = null
    private val scheduleRef = AtomicReference<Schedule?>(null)

    /** Обновить текущее расписание (потокобезопасно) */
    fun updateSchedule(schedule: Schedule) {
        scheduleRef.set(schedule)
    }

    /** Запустить сервер. Вызывать однократно. */
    fun start() {
        if (engine != null) return
        engine = embeddedServer(CIO, port = SERVER_PORT, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                // Полное расписание
                get("/schedule") {
                    val schedule = scheduleRef.get()
                    if (schedule != null) {
                        call.respond(schedule)
                    } else {
                        call.respondText(
                            text = "Расписание ещё не загружено",
                            status = HttpStatusCode.ServiceUnavailable
                        )
                    }
                }
                // Лёгкий endpoint — только версия, чтобы клиент не тратил трафик на полный JSON
                get("/version") {
                    val v = scheduleRef.get()?.version ?: 0L
                    call.respond(mapOf("version" to v))
                }
            }
        }
        engine!!.start(wait = false)
    }

    /** Остановить сервер при уничтожении сервиса */
    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1000)
        engine = null
    }
}
