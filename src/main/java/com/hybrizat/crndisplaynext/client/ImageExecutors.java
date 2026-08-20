package com.hybrizat.crndisplaynext.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dedicated worker thread for client-side image work (decoding/scaling),
 * so the render/main thread is never blocked by CPU-heavy image processing.
 */
public final class ImageExecutors {

    /** Single decode thread — image decoding does not need parallelism. */
    public static final ExecutorService CLIENT_DECODE =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "crn-image-decode");
            t.setDaemon(true);
            return t;
        });

    private ImageExecutors() {}
}