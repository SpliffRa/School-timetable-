package com.schedule.app.data.network

import android.util.Log
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "PeerDiscovery"
private const val UDP_PORT = 8888
private const val BROADCAST_INTERVAL_MS = 4_000L
private const val MAGIC = "SCHEDULE_APP_V1"

/**
 * UDP- и HTTP-обнаружение P2P.
 *
 * Сервер:
 *   – непрерывно рассылает UDP Broadcast на адрес подсети и 255.255.255.255
 *   – слушает входящие запросы на порту 8080
 *
 * Клиент:
 *   – одновременно слушает UDP Broadcast и быстро сканирует локальную подсеть /24 по HTTP,
 *     что гарантирует нахождение сервера даже при блокировках multicast/broadcast на роутере.
 */
object PeerDiscovery {

    // ─────────────────────────────────────────────────────────────────────────
    // Сторона СЕРВЕРА — рассылка UDP Broadcast
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun broadcastLoop(
        deviceName: String,
        broadcastAddress: String = "255.255.255.255"
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            val message = "$MAGIC|$deviceName".toByteArray(Charsets.UTF_8)
            val broadcastSubnet = InetAddress.getByName(broadcastAddress)
            val broadcastGlobal = InetAddress.getByName("255.255.255.255")
            val packetSubnet = DatagramPacket(message, message.size, broadcastSubnet, UDP_PORT)
            val packetGlobal = DatagramPacket(message, message.size, broadcastGlobal, UDP_PORT)

            while (true) {
                try {
                    socket.send(packetSubnet)
                    if (broadcastAddress != "255.255.255.255") {
                        socket.send(packetGlobal)
                    }
                    Log.d(TAG, "Broadcast sent: $deviceName → $broadcastAddress & 255.255.255.255")
                } catch (e: Exception) {
                    Log.w(TAG, "Broadcast send error: ${e.message}")
                }
                delay(BROADCAST_INTERVAL_MS)
            }
        } finally {
            socket?.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сторона КЛИЕНТА — поиск сервера (UDP + прямое сканирование подсети по HTTP)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Быстро сканирует локальную подсеть (1..254) по порту 8080 для прямого обнаружения сервера,
     * минуя любые ограничения UDP multicast, AP-изоляции и блокировки роутеров.
     */
    suspend fun probeSubnetForServer(localIp: String, onServerFound: (ip: String) -> Unit) = withContext(Dispatchers.IO) {
        val dotIndex = localIp.lastIndexOf('.')
        if (dotIndex <= 0) return@withContext
        val prefix = localIp.substring(0, dotIndex + 1)
        val myLastOctet = localIp.substring(dotIndex + 1).toIntOrNull() ?: -1

        try {
            coroutineScope {
                for (i in 1..254) {
                    if (i == myLastOctet) continue
                    val targetIp = "$prefix$i"
                    launch {
                        try {
                            Socket().use { sock ->
                                sock.connect(InetSocketAddress(targetIp, SERVER_PORT), 400)
                            }
                            // Порт открыт — проверяем endpoint
                            val resp = httpClient.get("http://$targetIp:$SERVER_PORT/version")
                            if (resp.status.value in 200..299) {
                                Log.d(TAG, "Subnet probe successfully found ScheduleServer at $targetIp")
                                onServerFound(targetIp)
                                cancel()
                            }
                        } catch (_: Exception) {
                            // Порт закрыт или хост недоступен
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // Сервер найден, остальные проверки остановлены
        }
    }

    /**
     * Слушает UDP до первого обнаружения сервера, затем вызывает [onServerFound] с IP
     * и **прекращает** прослушивание.
     */
    suspend fun listenForServer(onServerFound: (ip: String) -> Unit) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(UDP_PORT))
                soTimeout = 2000
            }
            val buf = ByteArray(256)
            val packet = DatagramPacket(buf, buf.size)

            while (true) {
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (text.startsWith(MAGIC)) {
                        val senderIp = packet.address.hostAddress ?: continue
                        Log.d(TAG, "Server found via UDP at $senderIp")
                        onServerFound(senderIp)
                        break
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    Log.w(TAG, "Listen error: ${e.message}")
                    delay(1_000)
                }
            }
        } finally {
            socket?.close()
        }
    }
}
