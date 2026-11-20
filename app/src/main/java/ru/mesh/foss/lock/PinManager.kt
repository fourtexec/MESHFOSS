package ru.mesh.foss.lock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Необязательный локальный PIN.
 *
 * Хранится только соль и PBKDF2-хеш, сам PIN нигде не сохраняется. Настройки
 * лежат в приватном `mesh_settings`, поэтому PIN переживает выход из МЭШ, но
 * стирается вместе с данными приложения. Это локальная защита от случайного
 * доступа, а не замена системному шифрованию.
 */
object PinManager {
    private const val PREFS = "mesh_settings"
    private const val KEY_HASH = "pin_hash_v1"
    private const val KEY_SALT = "pin_salt_v1"
    private const val KEY_BIOMETRIC = "pin_biometric_v1"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun isEnabled(context: Context): Boolean =
        prefs(context).getString(KEY_HASH, null)?.isNotBlank() == true

    fun isBiometricEnabled(context: Context): Boolean =
        isEnabled(context) && prefs(context).getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC, enabled && isEnabled(context)).apply()
    }

    fun setPin(context: Context, pin: String): Boolean {
        if (pin.length !in MIN_LENGTH..MAX_LENGTH || pin.any { !it.isDigit() }) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs(context).edit()
            .putString(KEY_HASH, hash)
            .putString(KEY_SALT, salt.toHex())
            .putBoolean(KEY_BIOMETRIC, false)
            .apply()
        return true
    }

    fun verify(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_HASH, null) ?: return false
        val salt = prefs(context).getString(KEY_SALT, null)?.hexToBytes() ?: return false
        if (pin.length !in MIN_LENGTH..MAX_LENGTH || pin.any { !it.isDigit() }) return false
        return MessageDigest.isEqual(stored.toByteArray(), hash(pin, salt).toByteArray())
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .remove(KEY_BIOMETRIC)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec)
                .encoded
                .toHex()
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
