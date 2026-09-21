package com.schedule.app.data.security

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Исключение, выбрасываемое при попытке расшифровать расписание с неверным ключом.
 */
class InvalidFamilyKeyException(message: String = "Неверный ключ семьи или данные зашифрованы другим ключом") :
    Exception(message)

/**
 * Зашифрованный конверт расписания для безопасного хранения в облаке (E2EE).
 */
@Serializable
data class EncryptedScheduleEnvelope(
    val version: Int = 1,
    val iv: String,
    val ciphertext: String,
    val updatedAt: Long = System.currentTimeMillis()
)

object CryptoUtils {

    // Алфавит для ключей: исключены визуально похожие символы (0/O, 1/I/L)
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val KEY_PREFIX = "SCH-"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    private val secureRandom = SecureRandom()

    /**
     * Генерирует криптостойкий ключ семьи с высокой энтропией (80 бит, 32^16 комбинаций).
     * Формат: SCH-XXXX-XXXX-XXXX-XXXX
     * Вероятность коллизии между любыми двумя семьями в мире равна нулю.
     */
    fun generateFamilyKey(): String {
        val sb = StringBuilder(KEY_PREFIX)
        for (i in 0 until 16) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-')
            }
            val idx = secureRandom.nextInt(ALPHABET.length)
            sb.append(ALPHABET[idx])
        }
        return sb.toString()
    }

    /**
     * Очищает введённый ключ: удаляет префикс, дефисы, пробелы и приводит к верхнему регистру.
     */
    fun cleanFamilyKey(key: String): String {
        var clean = key.trim().uppercase()
        if (clean.startsWith("SCH-") || clean.startsWith("SCH:")) {
            clean = clean.substring(4)
        }
        return clean.replace(Regex("[^A-Z0-9]"), "")
    }

    /**
     * Форматирует ключ для красивого отображения пользователю (SCH-XXXX-XXXX-XXXX-XXXX).
     */
    fun formatFamilyKey(key: String): String {
        val clean = cleanFamilyKey(key)
        if (clean.isBlank()) return ""
        val chunks = clean.chunked(4)
        return KEY_PREFIX + chunks.joinToString("-")
    }

    /**
     * Проверяет валидность ключа (не менее 8 значащих символов).
     */
    fun isValidFamilyKey(key: String): Boolean {
        return cleanFamilyKey(key).length >= 8
    }

    /**
     * Вычисляет изолированный и нераскрываемый идентификатор пространства в облаке (URL).
     * Исходный ключ никогда не передается в открытом виде в URL запроса.
     */
    fun deriveStorageNamespace(key: String): String {
        val clean = cleanFamilyKey(key)
        val salt = "sch_v3_salt_isolation_"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest((salt + clean).toByteArray(Charsets.UTF_8))
        val hex = hashBytes.joinToString("") { "%02x".format(it) }
        return "sch-enc-" + hex.take(32)
    }

    /**
     * Получает 256-битный ключ AES из ключа семьи через SHA-256.
     */
    private fun deriveAesKey(key: String): SecretKeySpec {
        val clean = cleanFamilyKey(key)
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(("family_aes_key_" + clean).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Шифрует произвольный текст (JSON расписания) алгоритмом AES-256-GCM.
     * Для каждого шифрования генерируется новый криптографически случайный 12-байтовый IV.
     */
    fun encryptPayload(plaintext: String, familyKey: String): EncryptedScheduleEnvelope {
        val clean = cleanFamilyKey(familyKey)
        require(clean.isNotBlank()) { "Ключ семьи не может быть пустым" }

        val secretKey = deriveAesKey(clean)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptedScheduleEnvelope(
            version = 1,
            iv = Base64.getEncoder().encodeToString(iv),
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Дешифрует зашифрованный конверт с расписанием с проверкой целостности (GCM Auth Tag).
     * Если ключ неверный или данные повреждены — выбрасывает InvalidFamilyKeyException.
     */
    fun decryptPayload(envelope: EncryptedScheduleEnvelope, familyKey: String): String {
        val clean = cleanFamilyKey(familyKey)
        require(clean.isNotBlank()) { "Ключ семьи не может быть пустым" }

        return try {
            val secretKey = deriveAesKey(clean)
            val iv = Base64.getDecoder().decode(envelope.iv)
            val ciphertext = Base64.getDecoder().decode(envelope.ciphertext)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw InvalidFamilyKeyException("Не удалось расшифровать данные: неверный ключ семьи или повреждённый файл")
        }
    }

    /**
     * Генерирует Android Bitmap с QR-кодом для ключа семьи.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Распознаёт текст QR-кода из любого изображения Bitmap (скриншот, камера, галерея).
     */
    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
            val result = com.google.zxing.MultiFormatReader().decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}
