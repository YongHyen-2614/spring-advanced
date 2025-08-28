// src/main/java/org/example/expert/logging/AdminApiLoggingAspect.java
package org.example.expert.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

@Aspect
@Component
public class AdminApiLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(AdminApiLoggingAspect.class);
    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Around("@annotation(org.example.expert.logging.AdminApiLog)")
    public Object enforceAdminAndLog(ProceedingJoinPoint pjp) throws Throwable {
        // ---- 공통 수집 ----
        Instant startedAt = Instant.now();
        HttpServletRequest req = currentRequest();
        String url = req != null ? req.getRequestURI() : "N/A";
        String method = req != null ? req.getMethod() : "N/A";
        String requesterId = resolveRequesterId();

        // ---- 1) 어드민 권한 확인 (AOP만으로 인가) ----
        boolean isAdmin = SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);

        if (!isAdmin) {
            Map<String, Object> denied = Map.of(
                    "tag", "ADMIN_API",
                    "timestamp", startedAt.toString(),
                    "method", method,
                    "url", url,
                    "requesterId", requesterId,
                    "error", "FORBIDDEN",
                    "message", "관리자 권한이 필요합니다."
            );
            log.warn(toJsonSafe(denied));
            throw new RuntimeException("관리자 권한이 필요합니다."); // 프로젝트의 커스텀 예외로 바꿔도 됨
        }

        // ---- 2) 요청 본문 로깅(JSON) ----
        Object reqBody = extractRequestBody(pjp);
        String reqJson = toJsonSafe(maskSensitive(reqBody));

        Object result = null;
        Throwable thrown = null;

        try {
            // 실행 전 로그(요청)
            Map<String, Object> pre = new LinkedHashMap<>();
            pre.put("tag", "ADMIN_API");
            pre.put("phase", "before");
            pre.put("timestamp", startedAt.toString());
            pre.put("method", method);
            pre.put("url", url);
            pre.put("requesterId", requesterId);
            pre.put("requestBody", tryJsonToMap(reqJson));
            log.info(toJsonSafe(pre));

            // 실제 메서드 실행
            result = pjp.proceed();
            return result;

        } catch (Throwable t) {
            thrown = t;
            throw t;

        } finally {
            // ---- 3) 응답/예외 로깅(JSON) ----
            Object resp = (thrown == null)
                    ? unwrapBody(result)
                    : Map.of("error", thrown.getClass().getSimpleName(),
                    "message", Optional.ofNullable(thrown.getMessage()).orElse("(no message)"));

            String resJson = toJsonSafe(maskSensitive(resp));

            Map<String, Object> post = new LinkedHashMap<>();
            post.put("tag", "ADMIN_API");
            post.put("phase", "after");
            post.put("timestamp", startedAt.toString());
            post.put("method", method);
            post.put("url", url);
            post.put("requesterId", requesterId);
            post.put("responseBody", tryJsonToMap(resJson));

            if (thrown == null) log.info(toJsonSafe(post));
            else log.error(toJsonSafe(post));
        }
    }

    // ---- helpers ----
    private HttpServletRequest currentRequest() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra instanceof ServletRequestAttributes sra) return sra.getRequest();
        return null;
    }

    private String resolveRequesterId() {
        try {
            var ctx = SecurityContextHolder.getContext();
            var auth = ctx != null ? ctx.getAuthentication() : null;
            if (auth != null) {
                Object principal = auth.getPrincipal();
                try {
                    var m = principal.getClass().getMethod("getId");
                    Object id = m.invoke(principal);
                    if (id != null) return String.valueOf(id);
                } catch (NoSuchMethodException ignore) {}
                if (auth.getName() != null && !auth.getName().isBlank()) return auth.getName();
            }
        } catch (Exception ignore) {}
        HttpServletRequest req = currentRequest();
        if (req != null) {
            String headerId = req.getHeader("X-USER-ID");
            if (headerId != null && !headerId.isBlank()) return headerId;
        }
        return "anonymous";
    }

    private Object extractRequestBody(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Annotation[][] anns = method.getParameterAnnotations();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < args.length; i++) {
            for (Annotation a : anns[i]) {
                if (a.annotationType().equals(RequestBody.class)) return args[i];
            }
        }
        return null;
    }

    private Object unwrapBody(Object ret) {
        if (ret instanceof ResponseEntity<?> re) return re.getBody();
        return ret;
    }

    private Object maskSensitive(Object body) {
        if (body == null) return null;
        try {
            Map<String, Object> map = (body instanceof Map)
                    ? new LinkedHashMap<>((Map<String, Object>) body)
                    : om.convertValue(body, new TypeReference<Map<String, Object>>() {});
            for (String k : List.of("password", "oldPassword", "newPassword", "verificationCode", "token")) {
                if (map.containsKey(k)) map.put(k, "***");
            }
            return map;
        } catch (Exception e) {
            return body;
        }
    }

    private String toJsonSafe(Object obj) {
        try { return om.writeValueAsString(obj); }
        catch (Exception e) { return String.valueOf(obj); }
    }

    private Object tryJsonToMap(String json) {
        try { return om.readValue(json, new TypeReference<Map<String,Object>>(){}); }
        catch (Exception e) { return json; }
    }
}
