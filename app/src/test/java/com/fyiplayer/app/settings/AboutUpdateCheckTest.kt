package com.fyiplayer.app.settings

import com.fyiplayer.app.update.UpdateInfo
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutUpdateCheckTest {
    private val newerUpdate = UpdateInfo(version = "0.2.29", apkUrl = "https://example.invalid/app.apk")

    @Test
    fun noNewerReleaseIsUpToDate() {
        assertEquals(CheckState.UpToDate, checkStateOf(Result.success(null)))
    }

    @Test
    fun newerReleaseIsFoundWithItsInfo() {
        assertEquals(CheckState.Found(newerUpdate), checkStateOf(Result.success(newerUpdate)))
    }

    @Test
    fun fetchFailureIsFailed() {
        assertEquals(CheckState.Failed, checkStateOf(Result.failure(IOException("offline"))))
    }
}
