package com.fyiplayer.app.player.sabr;

// Ported from PipePipeClient (GPL-3.0, PipePipe and NewPipe contributors); package and Media3 changes only.

import java.io.IOException;

public final class SabrLogicException extends IOException {
    SabrLogicException(final String message) {
        super(message);
    }

    SabrLogicException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
