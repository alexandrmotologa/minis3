package com.engine.minis3.infrastructure.adapter.in.auth;

import com.engine.minis3.application.dto.ErrorResponse;
import com.engine.minis3.domain.auth.CanonicalRequest;
import com.engine.minis3.domain.auth.SigV4Credentials;
import com.engine.minis3.domain.auth.SigV4Validator;
import com.engine.minis3.domain.exception.InvalidAccessKeyIdException;
import com.engine.minis3.domain.exception.MiniS3Exception;
import com.engine.minis3.domain.model.ApiCredential;
import com.engine.minis3.domain.port.CredentialRepositoryPort;
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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Order(1)
public class AwsSigV4Filter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AwsSigV4Filter.class);

    private final CredentialRepositoryPort credentialRepository;
    private final MiniS3Properties properties;
    private final boolean requireAuth;
    private final XmlMapper xmlMapper;

    public AwsSigV4Filter(CredentialRepositoryPort credentialRepository, MiniS3Properties properties) {
        this.credentialRepository = credentialRepository;
        this.properties = properties;
        this.requireAuth = properties.getAuth().isRequireAuth();
        this.xmlMapper = new XmlMapper();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
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
        String accessKey = extractAccessKeyFromHeader(authHeader);
        ApiCredential credential = resolveCredential(accessKey);

        enforcePolicy(request, credential);

        String requestDate = request.getHeader("x-amz-date");
        if (requestDate == null || requestDate.isBlank()) {
            requestDate = request.getHeader("Date");
        }

        CanonicalRequest canonical = buildCanonicalRequest(request, Collections.emptySet());
        SigV4Validator validator = new SigV4Validator(new SigV4Credentials(
                credential.getAccessKey(),
                credential.getSecretKey(),
                properties.getAuth().getRegion()
        ));
        validator.validateHeader(authHeader, requestDate, canonical);
    }

    private void validatePresignedAuth(HttpServletRequest request, String signature) {
        String credentialParam = request.getParameter("X-Amz-Credential");
        String accessKey = extractAccessKeyFromParam(credentialParam);
        ApiCredential credential = resolveCredential(accessKey);

        enforcePolicy(request, credential);

        String date = request.getParameter("X-Amz-Date");
        String expires = request.getParameter("X-Amz-Expires");

        Set<String> excludedQueryParams = Set.of("X-Amz-Signature", "x-amz-signature");
        CanonicalRequest canonical = buildCanonicalRequest(request, excludedQueryParams);

        SigV4Validator validator = new SigV4Validator(new SigV4Credentials(
                credential.getAccessKey(),
                credential.getSecretKey(),
                properties.getAuth().getRegion()
        ));
        validator.validatePresigned(credentialParam, date, expires, signature, canonical);
    }

    private ApiCredential resolveCredential(String accessKey) {
        // 1. Check custom credentials from SQLite
        Optional<ApiCredential> custom = credentialRepository.findByAccessKey(accessKey);
        if (custom.isPresent()) {
            return custom.get();
        }

        // 2. Check master credentials from configuration
        if (properties.getAuth().getAccessKey().equals(accessKey)) {
            return new ApiCredential(
                    properties.getAuth().getAccessKey(),
                    properties.getAuth().getSecretKey(),
                    ApiCredential.Role.ADMIN,
                    "*",
                    java.time.Instant.EPOCH
            );
        }

        throw new InvalidAccessKeyIdException("The AWS Access Key Id you provided does not exist in our records: " + accessKey);
    }

    private void enforcePolicy(HttpServletRequest request, ApiCredential credential) {
        // Enforce method authorization (e.g. Read-Only check)
        if (!credential.allowsMethod(request.getMethod())) {
            throw new MiniS3Exception("Access denied: role " + credential.getRole() + " is not authorized for " + request.getMethod(), "AccessDenied", 403) {};
        }

        // Enforce bucket scope
        String uri = request.getRequestURI();
        String bucket = extractBucketFromUri(uri);
        if (bucket != null && !credential.allowsBucket(bucket)) {
            throw new MiniS3Exception("Access denied: credentials do not have permission for bucket " + bucket, "AccessDenied", 403) {};
        }
    }

    private String extractBucketFromUri(String uri) {
        if (uri == null || uri.isBlank() || "/".equals(uri)) return null;
        String clean = uri.startsWith("/") ? uri.substring(1) : uri;
        int slashIdx = clean.indexOf('/');
        return (slashIdx != -1) ? clean.substring(0, slashIdx) : clean;
    }

    private String extractAccessKeyFromHeader(String authHeader) {
        int credIdx = authHeader.indexOf("Credential=");
        if (credIdx == -1) {
            throw new InvalidAccessKeyIdException("Malformed Authorization header: missing Credential=");
        }
        int slashIdx = authHeader.indexOf('/', credIdx + 11);
        if (slashIdx == -1) {
            throw new InvalidAccessKeyIdException("Malformed Credential parameter in Authorization header");
        }
        return authHeader.substring(credIdx + 11, slashIdx).trim();
    }

    private String extractAccessKeyFromParam(String credentialParam) {
        if (credentialParam == null) {
            throw new InvalidAccessKeyIdException("Malformed X-Amz-Credential parameter");
        }
        String decoded = URLDecoder.decode(credentialParam, StandardCharsets.UTF_8);
        if (!decoded.contains("/")) {
            throw new InvalidAccessKeyIdException("Malformed X-Amz-Credential parameter");
        }
        return decoded.substring(0, decoded.indexOf('/')).trim();
    }

    private CanonicalRequest buildCanonicalRequest(HttpServletRequest request, Set<String> excludedQueryParams) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

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
                String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
                String decodedVal = URLDecoder.decode(val, StandardCharsets.UTF_8);
                querySb.append(URLEncoder.encode(decodedKey, StandardCharsets.UTF_8).replace("+", "%20"))
                        .append('=')
                        .append(URLEncoder.encode(decodedVal, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }

        Map<String, String> headers = new HashMap<>();
        List<String> signedHeaderNames = new ArrayList<>();

        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement().toLowerCase();
                headers.put(name, request.getHeader(name));
            }
        }

        if (!headers.containsKey("host")) {
            int port = request.getServerPort();
            if (port == 80 || port == 443 || port <= 0) {
                headers.put("host", request.getServerName());
            } else {
                headers.put("host", request.getServerName() + ":" + port);
            }
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
