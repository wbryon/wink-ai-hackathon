package ru.wink.winkaipreviz.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

	@Value("${app.rate-limit.per-minute:30}")
	private int perMinute;

	private final Map<String, Bucket> ipToBucket = new ConcurrentHashMap<>();

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String path = request.getRequestURI();
		boolean isGenerate = path.matches("^/api/scenes/[^/]+/generate$") || path.matches("^/api/frames/[^/]+/regenerate$");
		if (!isGenerate) {
			filterChain.doFilter(request, response);
			return;
		}

		String clientIp = getClientIp(request);
		Bucket bucket = ipToBucket.computeIfAbsent(clientIp, k -> newBucket());
		if (bucket.tryConsume(1)) {
			filterChain.doFilter(request, response);
		} else {
			response.setStatus(429);
			response.setContentType("application/json;charset=UTF-8");
			response.getOutputStream().write(("{\"error\":\"Too Many Requests\",\"message\":\"Превышен лимит запросов. Попробуйте позже.\"}").getBytes(StandardCharsets.UTF_8));
		}
	}

	private Bucket newBucket() {
		Bandwidth limit = Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	private String getClientIp(HttpServletRequest request) {
		String xf = request.getHeader("X-Forwarded-For");
		if (xf != null && !xf.isBlank()) {
			return xf.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}


