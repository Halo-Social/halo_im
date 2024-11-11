package org.xmtp.android.example.utils

import android.accounts.AccountManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64.NO_WRAP
import android.util.Base64.decode
import android.util.Base64.encodeToString
import android.util.Log


class KeyUtil(val context: Context) {
    private val PREFS_NAME = "EncryptionPref"
    fun retrieveKey(address: String): ByteArray? {
        val alias = "xmtp-dev-${address.lowercase()}"

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyString = prefs.getString(alias, null)
        return if (keyString != null) {
            decode(keyString, NO_WRAP)
        } else null
    }
}