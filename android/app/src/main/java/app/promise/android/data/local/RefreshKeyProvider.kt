package app.promise.android.data.local

import javax.crypto.SecretKey

interface RefreshKeyProvider {
    fun getOrCreateKey(): SecretKey

    fun recreateKey(): SecretKey = getOrCreateKey()
}
