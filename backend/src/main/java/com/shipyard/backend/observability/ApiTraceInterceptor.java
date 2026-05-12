package com.shipyard.backend.observability;

import com.shipyard.backend.auth.AuthContext;
import com.shipyard.backend.auth.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class ApiTraceInterceptor implements HandlerInterceptor {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String RECORD_ID_HEADER = "X-Record-Id";
    public static final String FILE_ID_HEADER = "X-File-Id";
    public static final String TRACE_ID_ATTR = "shipyardTraceId";
    private static final String START_TIME_ATTR = "shipyardRequestStartTime";

    private static final Logger log = LoggerFactory.getLogger(ApiTraceInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = firstNonBlank(request.getHeader(TRACE_ID_HEADER), UUID.randomUUID().toString());
        request.setAttribute(TRACE_ID_ATTR, traceId);
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        response.setHeader(TRACE_ID_HEADER, traceId);
        MDC.put("traceId", traceId);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {
        try {
            Map<String, String> uriVariables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            AuthContext authContext = (AuthContext) request.getAttribute(AuthInterceptor.AUTH_CONTEXT_ATTR);

            String traceId = stringAttr(request, TRACE_ID_ATTR);
            String userId = authContext == null ? "-" : authContext.user().getId();
            String recordId = firstNonBlank(
                request.getHeader(RECORD_ID_HEADER),
                uriVariables == null ? null : uriVariables.get("recordId"),
                "-"
            );
            String fileId = firstNonBlank(
                request.getHeader(FILE_ID_HEADER),
                uriVariables == null ? null : uriVariables.get("fileId"),
                "-"
            );
            long startedAt = request.getAttribute(START_TIME_ATTR) instanceof Long value ? value : System.currentTimeMillis();
            long durationMs = Math.max(0L, System.currentTimeMillis() - startedAt);

            if (ex == null) {
                log.info(
                    "api request completed method={} path={} status={} traceId={} userId={} recordId={} fileId={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    traceId,
                    userId,
                    recordId,
                    fileId,
                    durationMs
                );
            } else {
                log.error(
                    "api request failed method={} path={} status={} traceId={} userId={} recordId={} fileId={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    traceId,
                    userId,
                    recordId,
                    fileId,
                    durationMs,
                    ex
                );
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    private String stringAttr(HttpServletRequest request, String attrName) {
        Object value = request.getAttribute(attrName);
        return value == null ? "-" : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
