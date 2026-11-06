package app.musonic.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("musonic_private", Context.MODE_PRIVATE)
    private val alias = "musonic.profiles.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun read(): List<Profile> {
        val stored = prefs.getString("profiles", null) ?: return emptyList()
        val pieces = stored.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
        val array = JSONArray(String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8))
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            Profile(it.getString("id"), it.getString("name"), it.getString("server"), it.getString("username"), it.getString("secret"), AuthMode.valueOf(it.getString("authMode")), it.optBoolean("allowHttp"))
        } }
    }
    fun write(profiles: List<Profile>) {
        val array = JSONArray().apply { profiles.forEach { profile -> put(JSONObject().apply {
            put("id", profile.id); put("name", profile.name); put("server", profile.server)
            put("username", profile.username); put("secret", profile.secret)
            put("authMode", profile.authMode.name); put("allowHttp", profile.allowHttp)
        }) } }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("profiles", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    var activeId: String?
        get() = prefs.getString("active", null)
        set(value) { prefs.edit().putString("active", value).apply() }
}
