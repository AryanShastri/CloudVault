package com.cloudvault.storage_engine.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${app.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!rateLimitingEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();


        Integer limit = resolveLimit(path, method);

        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }


        String key = resolveKey(request, path);
        Bucket bucket = buckets.computeIfAbsent(key,
                k -> buildBucket(limit));

        if (bucket.tryConsume(1)) {
            response.addHeader("X-RateLimit-Limit",
                    String.valueOf(limit));
            response.addHeader("X-RateLimit-Remaining",
                    String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429," +
                            "\"message\":\"Too many requests. " +
                            "Please slow down.\"}");
        }
    }


    private Integer resolveLimit(String path, String method) {
        // Only limit write operations and auth
        if (path.equals("/api/auth/login"))    return 20;
        if (path.equals("/api/auth/register")) return 10;

        // GET requests are always unlimited — frontend safe
        if (method.equals("GET")) return null;

        if (path.equals("/api/storage/buckets")
                && method.equals("POST"))      return 10;
        if (path.matches("/api/storage/buckets/[^/]+")
                && method.equals("DELETE"))    return 5;
        if (path.startsWith("/api/billing/invoices/generate")) return 5;
        if (path.matches("/api/storage/buckets/[^/]+/objects")
                && method.equals("POST"))      return 30;

        return null;
    }

    private String resolveKey(HttpServletRequest request, String path) {
        // Auth endpoints — key by IP
        if (path.startsWith("/api/auth")) {
            return "auth:" + request.getRemoteAddr();
        }
        // Other endpoints — key by user token
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7,
                    Math.min(27, auth.length()));
            return "user:" + token + ":" + path;
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket buildBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}