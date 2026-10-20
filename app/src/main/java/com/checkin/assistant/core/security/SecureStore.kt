package com.checkin.assistant.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 里的 AES-256-GCM 密钥加密本地保存的密码。
 *
 * 为什么不用明文 / 不用固定密钥：
 *  - 密钥由系统 TEE 生成且**不可导出**，App 只能「请求它加密/解密」，
 *    即使有人把 DataStore 的 xml 拷走也解不开。
 *  - 密钥与这台设备、这个 App 绑定；卸载重装或换机后旧密文自然失效，
 *    此时 [decrypt] 返回 null，让上层重新要密码，而不是崩溃。
 *
 * 密文格式：base64(iv) + ":" + base64(ciphertext+tag)
 * GCM 的 IV 每次加密都必须不同，所以随密文一起存。
 */
object SecureStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "checkin_cred_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不要求锁屏/指纹，否则后台定时任务没法解密
                .setUserAuthenticationRequired(false)
                .build()
        )
        return gen.generateKey()
    }

    /** 加密；失败返回 null（Keystore 不可用时上层降级为「不保存密码」） */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }.getOrNull()

    /** 解密；密钥失效 / 密文损坏返回 null */
    fun decrypt(blob: String?): String? {
        if (blob.isNullOrBlank()) return null
        return runCatching {
            val idx = blob.indexOf(':')
            if (idx <= 0) return null
            val iv = Base64.decode(blob.substring(0, idx), Base64.NO_WRAP)
            val ct = Base64.decode(blob.substring(idx + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrNull()
    }

    /** 删掉密钥（退出登录时可调，之后旧密文彻底作废） */
    fun wipe() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }
}
