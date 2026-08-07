package com.fyiplayer.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.fyiplayer.app.source.newpipe.YoutubeAuth

private const val SIGNIN_URL = "https://www.youtube.com/signin"
// Mobile Chrome UA -- Google's sign-in flow serves a desktop-only variant otherwise.
private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/125.0.0.0 Mobile Safari/537.36"

/**
 * Hosts the real Google sign-in page in a WebView -- same mechanism NewPipe/PipePipe use. The
 * user authenticates directly with Google; this activity only ever reads the resulting session
 * cookie back out of [CookieManager]. RESULT_CANCELED (the default) on back press or any failure.
 */
class YoutubeLoginActivity : ComponentActivity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val wv = WebView(this)
        webView = wv
        // Sign-in happens on accounts.google.com and lands the session on youtube.com, so the
        // third-party opt-in is what makes the cookie readable here at all.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = USER_AGENT
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val cookies = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: return
                val hasSession = cookies.contains("SID=") &&
                    (cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID="))
                if (hasSession) {
                    CookieManager.getInstance().flush() // survive process death before we finish
                    YoutubeAuth.store(cookies)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
        }
        setContentView(wv)
        wv.loadUrl(SIGNIN_URL)
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
