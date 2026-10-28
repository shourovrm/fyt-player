package com.fyiplayer.app.player.sabr;

// Ported from PipePipeClient (GPL-3.0, PipePipe and NewPipe contributors); package and Media3 changes only.

import java.io.IOException;

/** Signals that another serialized SABR request may shortly deliver the requested segment. */
final class SabrSegmentPendingException extends IOException {
    SabrSegmentPendingException(final String message) {
        super(message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
