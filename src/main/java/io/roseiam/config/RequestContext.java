package io.roseiam.config;

import java.util.UUID;
import org.slf4j.MDC;

public final class RequestContext {

    private static final String REQUEST_ID = "requestId";

    private RequestContext() {}

    public static String requestId() {
        String value = MDC.get(REQUEST_ID);
        if (value == null) {
            value = UUID.randomUUID().toString();
            MDC.put(REQUEST_ID, value);
        }
        return value;
    }

    public static void clear() {
        MDC.remove(REQUEST_ID);
    }
}
