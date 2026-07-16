package com.kindred.emkcrm_project_backend.search;

import com.kindred.emkcrm_project_backend.config.SearchServiceProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SearchServiceTokenProviderTest {

    private static final String SERVICE_NAME = "emk-backend";
    private static final List<String> SCOPES = List.of("catalogs:read", "catalogs:write");
    private static final long TOKEN_TTL_SECONDS = 600;
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 60;

    private RSAPublicKey publicKey;
    private String rsaPrivateKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        publicKey = (RSAPublicKey) keyPair.getPublic();
        rsaPrivateKeyPem = convertToTraditionalPem(keyPair);
    }

    private SearchServiceTokenProvider createProvider() {
        var props = new SearchServiceProperties(
                "http://localhost:8001",
                SERVICE_NAME,
                rsaPrivateKeyPem,
                SCOPES,
                TOKEN_TTL_SECONDS,
                TOKEN_REFRESH_MARGIN_SECONDS
        );
        var provider = new SearchServiceTokenProvider(props);
        provider.init();
        return provider;
    }

    @Test
    void getTokenReturnsValidRs256Jwt() {
        SearchServiceTokenProvider provider = createProvider();

        String token = provider.getToken();

        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(SERVICE_NAME);
        assertThat(claims.get("scopes", List.class)).isEqualTo(SCOPES);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void getTokenReturnsCachedTokenOnRepeatCalls() {
        SearchServiceTokenProvider provider = createProvider();

        String first = provider.getToken();
        String second = provider.getToken();
        String third = provider.getToken();

        assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    void getTokenRefreshesWhenWithinMarginOfExpiry() throws Exception {
        SearchServiceTokenProvider provider = createProvider();
        String first = provider.getToken();

        Claims firstClaims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(first)
                .getPayload();
        long firstExp = firstClaims.getExpiration().getTime();

        long nowSeconds = System.currentTimeMillis() / 1000;
        setField(provider, "tokenExpiresAt", nowSeconds + TOKEN_REFRESH_MARGIN_SECONDS - 1);

        Thread.sleep(1100);
        String refreshed = provider.getToken();

        Claims refreshedClaims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(refreshed)
                .getPayload();

        assertThat(refreshedClaims.getExpiration().getTime()).isGreaterThan(firstExp);
        assertThat(refreshedClaims.getSubject()).isEqualTo(SERVICE_NAME);
    }

    @Test
    void initThrowsOnInvalidPem() {
        var props = new SearchServiceProperties(
                "http://localhost:8001",
                SERVICE_NAME,
                "not-a-valid-pem",
                SCOPES,
                TOKEN_TTL_SECONDS,
                TOKEN_REFRESH_MARGIN_SECONDS
        );
        var provider = new SearchServiceTokenProvider(props);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported PEM format");
    }

    @Test
    void tokenHasCorrectExpirationBasedOnTtl() {
        SearchServiceTokenProvider provider = createProvider();
        long beforeSeconds = System.currentTimeMillis() / 1000;

        String token = provider.getToken();

        long afterSeconds = System.currentTimeMillis() / 1000;
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long expectedMinExp = beforeSeconds + TOKEN_TTL_SECONDS - 2;
        long expectedMaxExp = afterSeconds + TOKEN_TTL_SECONDS + 2;

        assertThat(claims.getExpiration().getTime() / 1000)
                .isBetween(expectedMinExp, expectedMaxExp);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String convertToTraditionalPem(KeyPair keyPair) throws Exception {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(keyPair);
        }
        return stringWriter.toString();
    }
}
