package com.fyiplayer.app.source.newpipe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single headless WebView used as a JavaScript runtime for the local sig/n decoder.
 *
 * <p>Ported from PipePipe's SharedWebViewRuntime (GPLv3, same license family as the bundled
 * PipePipeExtractor), trimmed to what {@link WebViewJsDecoder} needs: one blank first-party page
 * kept alive for the process, network loads blocked, and blocking JS evaluation. The SABR/poToken
 * bridge callbacks of the original are not ported -- nothing here uses them.</p>
 */
public final class SharedWebViewRuntime {

    public static final String BRIDGE_NAME = "PipePipeWebViewBridge";

    private static final String TAG = "SharedWebViewRuntime";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    private static final long READY_CALLBACK_ATTEMPT_TIMEOUT_MS = 5_000L;
    private static final int MAX_READY_CALLBACK_ATTEMPTS = 2;
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3";
    private static volatile SharedWebViewRuntime instance;

    private final Context appContext;
    private final Context webViewContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object initLock = new Object();

    @Nullable
    private CountDownLatch initLatch;
    @Nullable
    private AtomicReference<Throwable> initError;
    @Nullable
    private InitializationAttempt activeInitializationAttempt;
    private long nextInitializationAttemptId;
    @Nullable
    private WebView webView;
    private volatile boolean ready;

    private SharedWebViewRuntime(final Context context) {
        appContext = context.getApplicationContext();
        final Configuration configuration =
                new Configuration(appContext.getResources().getConfiguration());
        webViewContext = appContext.createConfigurationContext(configuration);
    }

    @NonNull
    public static SharedWebViewRuntime get(final Context context) {
        SharedWebViewRuntime runtime = instance;
        if (runtime == null) {
            synchronized (SharedWebViewRuntime.class) {
                runtime = instance;
                if (runtime == null) {
                    runtime = new SharedWebViewRuntime(context);
                    instance = runtime;
                }
            }
        }
        return runtime;
    }

    public void ensureReady(final long timeoutMs, @NonNull final String operation)
            throws Exception {
        final CountDownLatch latch;
        final AtomicReference<Throwable> error;
        synchronized (initLock) {
            if (ready) {
                return;
            }
            if (initLatch == null) {
                startInitializationLocked();
            }
            latch = initLatch;
            error = initError;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(operation + " cannot wait on the main thread");
        }
        if (latch == null || error == null) {
            throw new IllegalStateException(operation + " did not start WebView initialization");
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(operation + " timed out waiting for WebView runtime");
        }
        final Throwable failure = error.get();
        if (failure != null) {
            throw new IllegalStateException(operation + " failed to initialize WebView runtime",
                    failure);
        }
    }

    @NonNull
    public String evaluateJavascriptBlocking(@NonNull final String script,
                                             final long timeoutMs,
                                             @NonNull final String operation) throws Exception {
        ensureReady(timeoutMs, operation);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(operation + " cannot wait on the main thread");
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        if (!mainHandler.post(() -> {
            try {
                final WebView view = webView;
                if (view == null) {
                    throw new IllegalStateException("WebView runtime is not initialized");
                }
                view.evaluateJavascript(script, value -> {
                    result.set(value);
                    latch.countDown();
                });
            } catch (final Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        })) {
            throw new IllegalStateException(operation + " could not post JavaScript evaluation");
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(operation + " timed out");
        }
        final Throwable failure = error.get();
        if (failure != null) {
            throw new IllegalStateException(operation + " failed", failure);
        }
        return result.get();
    }

    @NonNull
    public String loadAsset(@NonNull final String path) {
        try (InputStream in = appContext.getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (final Exception e) {
            throw new IllegalStateException("Could not load asset " + path, e);
        }
    }

    private void startInitializationLocked() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final InitializationAttempt attempt = new InitializationAttempt(
                ++nextInitializationAttemptId, 1, latch, error);
        initLatch = latch;
        initError = error;
        activeInitializationAttempt = attempt;
        if (!mainHandler.post(() -> createWebView(attempt))) {
            final IllegalStateException exception =
                    new IllegalStateException("Could not post WebView creation");
            activeInitializationAttempt = null;
            error.set(exception);
            latch.countDown();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView(@NonNull final InitializationAttempt attempt) {
        if (!isActiveInitializationAttempt(attempt)) {
            return;
        }
        try {
            final WebView view = new WebView(webViewContext);
            attempt.view = view;
            if (!isActiveInitializationAttempt(attempt)) {
                destroyWebView(view);
                return;
            }
            final WebSettings settings = view.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setUserAgentString(USER_AGENT);
            settings.setBlockNetworkLoads(true);
            view.addJavascriptInterface(new Bridge(), BRIDGE_NAME);
            view.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(final WebView view, final WebResourceRequest request,
                                            final WebResourceError webError) {
                    super.onReceivedError(view, request, webError);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame()) {
                        retryOrFail(attempt, new IllegalStateException(
                                "WebView runtime main frame error " + webError.getErrorCode()
                                        + ": " + webError.getDescription()));
                    }
                }
            });
            // Readiness is signalled only by the local document's bridge call: WebView 44/83
            // occasionally misses onPageFinished for a headless local page.
            view.loadDataWithBaseURL("https://www.youtube.com/",
                    runtimeDocument(attempt.id),
                    "text/html", "UTF-8", null);
            mainHandler.postDelayed(() -> retryOrFail(attempt, new IllegalStateException(
                    "WebView runtime ready callback timed out after "
                            + READY_CALLBACK_ATTEMPT_TIMEOUT_MS + " ms")),
                    READY_CALLBACK_ATTEMPT_TIMEOUT_MS);
        } catch (final Throwable throwable) {
            retryOrFail(attempt, throwable);
        }
    }

    @NonNull
    private static String runtimeDocument(final long attemptId) {
        return "<!doctype html><html><head><script>"
                + BRIDGE_NAME + ".onRuntimeDocumentReady('" + attemptId + "');"
                + "</script><title></title></head><body></body></html>";
    }

    private void completeInitialization(@NonNull final InitializationAttempt attempt) {
        if (!attempt.completed.compareAndSet(false, true)) {
            return;
        }
        final boolean stale;
        synchronized (initLock) {
            stale = activeInitializationAttempt != attempt || ready;
            if (!stale) {
                webView = attempt.view;
                ready = true;
                activeInitializationAttempt = null;
            }
        }
        if (stale) {
            destroyWebView(attempt.view);
            return;
        }
        attempt.latch.countDown();
    }

    private void retryOrFail(@NonNull final InitializationAttempt attempt,
                             @NonNull final Throwable throwable) {
        if (!attempt.completed.compareAndSet(false, true)) {
            return;
        }
        if (!isActiveInitializationAttempt(attempt)) {
            destroyWebView(attempt.view);
            return;
        }
        destroyWebView(attempt.view);
        if (attempt.number < MAX_READY_CALLBACK_ATTEMPTS) {
            final InitializationAttempt retry;
            synchronized (initLock) {
                if (activeInitializationAttempt != attempt || ready) {
                    return;
                }
                retry = new InitializationAttempt(++nextInitializationAttemptId,
                        attempt.number + 1, attempt.latch, attempt.error);
                activeInitializationAttempt = retry;
            }
            Log.w(TAG, "retrying WebView runtime init after attempt " + attempt.number);
            if (!mainHandler.post(() -> createWebView(retry))) {
                retryOrFail(retry, new IllegalStateException("Could not post WebView retry"));
            }
            return;
        }
        synchronized (initLock) {
            if (activeInitializationAttempt != attempt || ready) {
                return;
            }
            activeInitializationAttempt = null;
            attempt.error.compareAndSet(null, throwable);
        }
        Log.e(TAG, "WebView runtime init failed attempt=" + attempt.number);
        attempt.latch.countDown();
    }

    private boolean isActiveInitializationAttempt(@NonNull final InitializationAttempt attempt) {
        synchronized (initLock) {
            return activeInitializationAttempt == attempt && initLatch == attempt.latch
                    && initError == attempt.error && !ready;
        }
    }

    private static void destroyWebView(@Nullable final WebView view) {
        if (view == null) {
            return;
        }
        try {
            view.stopLoading();
            view.destroy();
        } catch (final Throwable throwable) {
            Log.w(TAG, "Could not destroy failed WebView initialization attempt");
        }
    }

    private static final class InitializationAttempt {
        private final long id;
        private final int number;
        private final CountDownLatch latch;
        private final AtomicReference<Throwable> error;
        private final AtomicBoolean completed = new AtomicBoolean();
        private final long startedAtMs = SystemClock.elapsedRealtime();
        @Nullable
        private WebView view;

        InitializationAttempt(final long id, final int number, @NonNull final CountDownLatch latch,
                              @NonNull final AtomicReference<Throwable> error) {
            this.id = id;
            this.number = number;
            this.latch = latch;
            this.error = error;
        }
    }

    private final class Bridge {
        @android.webkit.JavascriptInterface
        public void onRuntimeDocumentReady(final String attemptId) {
            mainHandler.post(() -> {
                final InitializationAttempt attempt;
                synchronized (initLock) {
                    attempt = activeInitializationAttempt;
                    if (attempt == null || !Long.toString(attempt.id).equals(attemptId)) {
                        return;
                    }
                }
                completeInitialization(attempt);
            });
        }
    }
}
