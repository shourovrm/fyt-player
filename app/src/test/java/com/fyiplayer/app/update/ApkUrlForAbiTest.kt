package com.fyiplayer.app.update

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkUrlForAbiTest {
    private fun asset(name: String) =
        JsonObject(mapOf("name" to JsonPrimitive(name), "browser_download_url" to JsonPrimitive("https://x/$name")))

    private val assets = listOf(
        asset("fyt-player-0.2.46-arm64-v8a-release.apk"),
        asset("fyt-player-0.2.46-armeabi-v7a-release.apk"),
        asset("fyt-player-0.2.46-x86_64-release.apk"),
    )

    @Test fun eachAbiGetsItsOwnApk() {
        assertEquals("https://x/fyt-player-0.2.46-armeabi-v7a-release.apk", apkUrlForAbi(assets, "armeabi-v7a"))
        assertEquals("https://x/fyt-player-0.2.46-x86_64-release.apk", apkUrlForAbi(assets, "x86_64"))
    }

    @Test fun missingAbiIsNullNotAnotherAbi() {
        assertNull(apkUrlForAbi(assets, "x86"))
    }
}
