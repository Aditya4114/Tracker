package com.tracker.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
        RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.PATCH
})
public class GatewayProxyController {

    @Value("${auth.service.uri:http://127.0.0.1:8081}")
    private String authServiceUri;

    @Value("${ingestion.service.uri:http://127.0.0.1:8082}")
    private String ingestionServiceUri;

    @Value("${intelligence.service.uri:http://127.0.0.1:8083}")
    private String intelligenceServiceUri;

    @Value("${core.service.uri:http://127.0.0.1:8084}")
    private String coreServiceUri;

    private final RestTemplate restTemplate;

    public GatewayProxyController(ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        // Build an HttpClient that does NOT automatically follow redirects
        // so that OAuth 302 redirects are passed back to the user's browser
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(180))
                .setRedirectsEnabled(false)
                .build();

        HttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        this.restTemplate = new RestTemplate(factory);
        ObservationRegistry registry = observationRegistryProvider.getIfAvailable();
        if (registry != null) {
            this.restTemplate.setObservationRegistry(registry);
        }
    }

    @RequestMapping(value = {
            "/api/auth/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/api/sync/**",
            "/api/classification/**",
            "/api/emails/**",
            "/api/applications/**",
            "/api/dashboard/**"
    }, method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH
    })
    public ResponseEntity<byte[]> proxyRequest(@RequestBody(required = false) byte[] body,
                                               HttpMethod method,
                                               HttpServletRequest request) throws IOException {

        String path = request.getRequestURI();
        String targetBaseUri;

        if (path.startsWith("/api/auth") || path.startsWith("/oauth2") || path.startsWith("/login/oauth2")) {
            targetBaseUri = authServiceUri;
        } else if (path.startsWith("/api/sync")) {
            targetBaseUri = ingestionServiceUri;
        } else if (path.startsWith("/api/classification") || path.startsWith("/api/emails")) {
            targetBaseUri = intelligenceServiceUri;
        } else if (path.startsWith("/api/applications") || path.startsWith("/api/dashboard")) {
            targetBaseUri = coreServiceUri;
        } else {
            return ResponseEntity.notFound().build();
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(targetBaseUri + path);
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            uriBuilder.query(request.getQueryString());
        }
        URI targetUri = uriBuilder.build(true).toUri();

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            for (String name : Collections.list(headerNames)) {
                if (!name.equalsIgnoreCase(HttpHeaders.HOST) &&
                    !name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                    Enumeration<String> values = request.getHeaders(name);
                    while (values.hasMoreElements()) {
                        headers.add(name, values.nextElement());
                    }
                }
            }
        }

        HttpEntity<byte[]> httpEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(targetUri, method, httpEntity, byte[].class);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());
            responseHeaders.remove(HttpHeaders.TRANSFER_ENCODING);

            // Rewrite redirect Location from internal auth-service port (8081) to Gateway port (8080)
            List<String> locations = responseHeaders.get(HttpHeaders.LOCATION);
            if (locations != null && !locations.isEmpty()) {
                responseHeaders.remove(HttpHeaders.LOCATION);
                for (String loc : locations) {
                    String rewritten = loc.replace("127.0.0.1:8081", "localhost:8080")
                                          .replace("localhost:8081", "localhost:8080");
                    responseHeaders.add(HttpHeaders.LOCATION, rewritten);
                }
            }

            return new ResponseEntity<>(response.getBody(), responseHeaders, response.getStatusCode());
        } catch (HttpStatusCodeException e) {
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.putAll(e.getResponseHeaders());
            errorHeaders.remove(HttpHeaders.TRANSFER_ENCODING);

            // Rewrite redirect Location for 3xx responses
            List<String> locations = errorHeaders.get(HttpHeaders.LOCATION);
            if (locations != null && !locations.isEmpty()) {
                errorHeaders.remove(HttpHeaders.LOCATION);
                for (String loc : locations) {
                    String rewritten = loc.replace("127.0.0.1:8081", "localhost:8080")
                                          .replace("localhost:8081", "localhost:8080");
                    errorHeaders.add(HttpHeaders.LOCATION, rewritten);
                }
            }

            return new ResponseEntity<>(e.getResponseBodyAsByteArray(), errorHeaders, e.getStatusCode());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(("Gateway Error: " + e.getMessage()).getBytes());
        }
    }
}
