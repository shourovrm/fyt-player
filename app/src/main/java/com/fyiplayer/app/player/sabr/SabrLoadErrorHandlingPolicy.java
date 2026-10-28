package com.fyiplayer.app.player.sabr;

// Ported from PipePipeClient (GPL-3.0, PipePipe and NewPipe contributors); package and Media3 changes only.

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.C;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;

import java.io.IOException;

/** Retries locally pending SABR segments without applying network-error backoff. */
@OptIn(markerClass = UnstableApi.class)
final class SabrLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
    private static final long PENDING_RETRY_DELAY_MS = 100;

    @Override
    public long getRetryDelayMsFor(final LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
        if (isPending(loadErrorInfo.exception)) {
            return PENDING_RETRY_DELAY_MS;
        }
        final int normalRetryCount = super.getMinimumLoadableRetryCount(
                loadErrorInfo.mediaLoadData.dataType);
        return loadErrorInfo.errorCount > normalRetryCount
                ? C.TIME_UNSET : super.getRetryDelayMsFor(loadErrorInfo);
    }

    @Override
    public int getMinimumLoadableRetryCount(final int dataType) {
        // Pending-segment retries can exceed the normal network retry budget while a response is
        // streaming. Real errors are still made fatal by getRetryDelayMsFor after that budget.
        return Integer.MAX_VALUE;
    }

    private static boolean isPending(final IOException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SabrSegmentPendingException) return true;
            current = current.getCause();
        }
        return false;
    }
}
