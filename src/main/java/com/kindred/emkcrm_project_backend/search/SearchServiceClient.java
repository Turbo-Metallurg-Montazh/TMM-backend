package com.kindred.emkcrm_project_backend.search;

import com.kindred.emkcrm_project_backend.config.SearchServiceProperties;
import com.kindred.emkcrm_project_backend.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class SearchServiceClient {

    private final RestClient restClient;
    private final SearchServiceTokenProvider tokenProvider;

    public SearchServiceClient(SearchServiceProperties properties,
                               SearchServiceTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                        java.net.http.HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(10))
                                .build()
                ))
                .build();
    }

    public String suggest(String queryText) {
        return postJson("/suggest", queryText);
    }

    public String getIndexStatus() {
        return getJson("/index-status");
    }

    public String listCatalogs() {
        return getJson("/catalogs");
    }

    public String buildIndex(int batchSize) {
        return postJson("/build-index", "{\"batch_size\":" + batchSize + "}");
    }

    private String postJson(String path, String body) {
        try {
            return restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            log.warn("POST search service failed: path={}, status={}", path, e.getStatusCode());
            throw new ServiceUnavailableException("Search service returned " + e.getStatusCode());
        } catch (Exception e) {
            log.error("POST search service failed: path={}", path, e);
            throw new ServiceUnavailableException("Failed to call search service");
        }
    }

    private String getJson(String path) {
        try {
            return restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            log.warn("GET search service failed: path={}, status={}", path, e.getStatusCode());
            throw new ServiceUnavailableException("Search service returned " + e.getStatusCode());
        } catch (Exception e) {
            log.error("GET search service failed: path={}", path, e);
            throw new ServiceUnavailableException("Failed to call search service");
        }
    }
}
