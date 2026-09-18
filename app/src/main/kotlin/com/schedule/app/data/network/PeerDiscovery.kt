package com.schedule.app.data.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

private const val TAG = "PeerDiscovery"
private const val UDP_PORT = 8888
private const val BROADCAST_INTERVAL_MS = 5_000L   // уменьшено с 10s для быстрого обнаружения
private const val MAGIC = "SCHEDULE_APP_V1"

/**
 * UDP-обнаружение P2P.
 *
 * Сервер периодически рассылает Broadcast с форматом:
 *   "SCHEDULE_APP_V1|<deviceName>"
 *
 * Клиент слушает UDP на порту 8888 и при получении правильного пакета
 * возвращает IP-адрес отправителя через [onServerFound].
 */
object PeerDiscovery {

    // ─────────────────────────────────────────────────────────────────────────
    // Сторона СЕРВЕРА — рассылка UDP Broadcast
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Непрерывно рассылает UDP Broadcast (suspend — запускать в coroutine).
     * Отменяется при отмене родительского Job.
     *
     * @param broadcastAddress Адрес broadcast (лучше subnet, например 192.168.1.255;
     *   fallback — "255.255.255.255").
     */
    suspend fun broadcastLoop(
        deviceName: String,
        broadcastAddress: String = "255.255.255.255"
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            val message = "$MAGIC|$deviceName".toByteArray(Charsets.UTF_8)
            val broadcast = InetAddress.getByName(broadcastAddress)
            val packet = DatagramPacket(message, message.size, broadcast, UDP_PORT)

            while (true) {
                try {
                    socket.send(packet)
                    Log.d(TAG, "Broadcast sent: $deviceName → $broadcastAddress")
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
    // Сторона КЛИЕНТА — прослушивание UDP
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Слушает UDP до первого обнаружения сервера, затем вызывает [onServerFound] с IP
     * и **прекращает** прослушивание (break из цикла).
     * (suspend — запускать в coroutine; отменяется при отмене Job).
     */
    suspend fun listenForServer(onServerFound: (ip: String) -> Unit) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(UDP_PORT))
                soTimeout = 2000  // 2s timeout so the loop can exit promptly on cancellation
            }
            val buf = ByteArray(256)
            val packet = DatagramPacket(buf, buf.size)

            while (true) {
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (text.startsWith(MAGIC)) {
                        val senderIp = packet.address.hostAddress ?: continue
                        Log.d(TAG, "Server found at $senderIp")
                        onServerFound(senderIp)
                        break   // ← выходим — больше слушать не нужно
                    }
                } catch (e: CancellationException) {
                    throw e    // propagate coroutine cancellation
                } catch (_: java.net.SocketTimeoutException) {
                    // Таймаут сокета для проверки отмены корутины
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
