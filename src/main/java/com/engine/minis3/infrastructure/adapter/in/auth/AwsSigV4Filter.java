package com.engine.minis3.infrastructure.adapter.in.auth;

import com.engine.minis3.application.dto.ErrorResponse;
import com.engine.minis3.domain.auth.CanonicalRequest;
import com.engine.minis3.domain.auth.SigV4Credentials;
import com.engine.minis3.domain.auth.SigV4Validator;
import com.engine.minis3.domain.exception.MiniS3Exception;
import com.engine.minis3.infrastructure.config.MiniS3Properties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Order(1)
public class AwsSigV4Filter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AwsSigV4Filter.class);

    private final SigV4Validator validator;
    private final boolean requireAuth;
    private final XmlMapper xmlMapper;

    public AwsSigV4Filter(MiniS3Properties properties) {
        SigV4Credentials creds = new SigV4Credentials(
                properties.getAuth().getAccessKey(),
                properties.getAuth().getSecretKey(),
                properties.getAuth().getRegion()
        );
        this.validator = new SigV4Validator(creds);
        this.requireAuth = properties.getAuth().isRequireAuth();
        this.xmlMapper = new XmlMapper();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Allow admin API, static UI assets, and favicon without SigV4
        return uri.startsWith("/api/admin") ||
                uri.equals("/") ||
                uri.equals("/index.html") ||
                uri.equals("/styles.css") ||
                uri.equals("/app.js") ||
                uri.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String presignedSig = request.getParameter("X-Amz-Signature");

        try {
            if (authHeader != null && authHeader.startsWith("AWS4-HMAC-SHA256")) {
                validateHeaderAuth(request, authHeader);
            } else if (presignedSig != null) {
                validatePresignedAuth(request, presignedSig);
            } else if (requireAuth) {
                throw new MiniS3Exception("Authorization header or presigned query parameter is required.", "AccessDenied", 403) {};
            }

            filterChain.doFilter(request, response);
        } catch (MiniS3Exception ex) {
            log.warn("Auth validation failed for {}: {}", request.getRequestURI(), ex.getMessage());
            writeXmlError(response, ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), ex.getHttpStatusCode());
        } catch (Exception ex) {
            log.error("Unexpected error in auth filter: {}", ex.getMessage(), ex);
            writeXmlError(response, "InternalError", "Authentication error: " + ex.getMessage(), request.getRequestURI(), 500);
        }
    }

    private void validateHeaderAuth(HttpServletRequest request, String authHeader) {
        String requestDate = request.getHeader("x-amz-date");
        if (requestDate == null || requestDate.isBlank()) {
            requestDate = request.getHeader("Date");
        }

        CanonicalRequest canonical = buildCanonicalRequest(request, Collections.emptySet());
        validator.validateHeader(authHeader, requestDate, canonical);
    }

    private void validatePresignedAuth(HttpServletRequest request, String signature) {
        String credential = request.getParameter("X-Amz-Credential");
        String date = request.getParameter("X-Amz-Date");
        String expires = request.getParameter("X-Amz-Expires");

        // When building canonical query string for presigned URLs, X-Amz-Signature is excluded
        Set<String> excludedQueryParams = Set.of("X-Amz-Signature", "x-amz-signature");
        CanonicalRequest canonical = buildCanonicalRequest(request, excludedQueryParams);

        validator.validatePresigned(credential, date, expires, signature, canonical);
    }

    private CanonicalRequest buildCanonicalRequest(HttpServletRequest request, Set<String> excludedQueryParams) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Build canonical query string: sorted parameter names
        Map<String, String[]> paramMap = request.getParameterMap();
        List<String> sortedKeys = new ArrayList<>(paramMap.keySet());
        Collections.sort(sortedKeys);

        StringBuilder querySb = new StringBuilder();
        for (String key : sortedKeys) {
            if (excludedQueryParams.contains(key)) {
                continue;
            }
            String[] values = paramMap.get(key);
            for (String val : values) {
                if (querySb.length() > 0) {
                    querySb.append('&');
                }
                querySb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(val, StandardCharsets.UTF_8));
            }
        }

        // Build headers map & signed headers list
        Map<String, String> headers = new HashMap<>();
        List<String> signedHeaderNames = new ArrayList<>();

        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement().toLowerCase();
                headers.put(name, request.getHeader(name));
            }
        }

        // Host header is mandatory in canonical request
        if (!headers.containsKey("host")) {
            headers.put("host", request.getServerName() + ":" + request.getServerPort());
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.contains("SignedHeaders=")) {
            int start = authHeader.indexOf("SignedHeaders=") + 14;
            int end = authHeader.indexOf(',', start);
            String signedList = (end == -1) ? authHeader.substring(start) : authHeader.substring(start, end);
            for (String h : signedList.split(";")) {
                String trimmed = h.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    signedHeaderNames.add(trimmed);
                }
            }
        } else {
            String presignedSignedHeaders = request.getParameter("X-Amz-SignedHeaders");
            if (presignedSignedHeaders != null) {
                for (String h : presignedSignedHeaders.split(";")) {
                    signedHeaderNames.add(h.trim().toLowerCase());
                }
            } else {
                signedHeaderNames.add("host");
            }
        }
        Collections.sort(signedHeaderNames);

        String payloadHash = request.getHeader("x-amz-content-sha256");
        if (payloadHash == null || payloadHash.isBlank()) {
            payloadHash = "UNSIGNED-PAYLOAD";
        }

        return new CanonicalRequest(method, uri, querySb.toString(), headers, signedHeaderNames, payloadHash);
    }

    private void writeXmlError(HttpServletResponse response, String code, String message, String resource, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/xml");
        response.setCharacterEncoding("UTF-8");
        ErrorResponse errorResponse = new ErrorResponse(code, message, resource);
        xmlMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
