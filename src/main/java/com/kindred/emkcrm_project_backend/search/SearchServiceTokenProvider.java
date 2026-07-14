package com.kindred.emkcrm_project_backend.search;

import com.kindred.emkcrm_project_backend.config.SearchServiceProperties;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.StringReader;
import java.security.PrivateKey;
import java.util.Date;

@Slf4j
@Component
public class SearchServiceTokenProvider {

    private final SearchServiceProperties properties;
    private PrivateKey privateKey;
    private volatile String cachedToken;
    private volatile long tokenExpiresAt;

    public SearchServiceTokenProvider(SearchServiceProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.privateKey = parsePrivateKey(properties.privateKeyPem());
        log.info("SearchServiceTokenProvider initialized: serviceName={}, tokenTtl={}s, refreshMargin={}s",
                properties.serviceName(), properties.tokenTtlSeconds(), properties.tokenRefreshMarginSeconds());
    }

    public synchronized String getToken() {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long marginSeconds = properties.tokenRefreshMarginSeconds();

        if (cachedToken != null && tokenExpiresAt - nowSeconds > marginSeconds) {
            return cachedToken;
        }

        cachedToken = generateToken();
        log.debug("Generated new search-service JWT token, expiresAt={}", tokenExpiresAt);
        return cachedToken;
    }

    private String generateToken() {
        long nowMillis = System.currentTimeMillis();
        long ttlMillis = properties.tokenTtlSeconds() * 1000;
        Date issuedAt = new Date(nowMillis);
        Date expiration = new Date(nowMillis + ttlMillis);

        tokenExpiresAt = expiration.getTime() / 1000;

        return Jwts.builder()
                .subject(properties.serviceName())
                .claim("scopes", properties.scopes())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(privateKey)
                .compact();
    }

    private PrivateKey parsePrivateKey(String pem) {
        try (PEMParser pemParser = new PEMParser(new StringReader(pem))) {
            Object object = pemParser.readObject();
            if (object instanceof PEMKeyPair keyPair) {
                return new JcaPEMKeyConverter().getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            throw new IllegalStateException("Unsupported PEM format: expected RSA key pair");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA private key from SEARCH_SERVICE_PRIVATE_KEY", e);
        }
    }
}
