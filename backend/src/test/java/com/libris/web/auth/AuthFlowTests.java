package com.libris.web.auth;

import com.libris.TestcontainersConfiguration;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end authentication flow against a real PostgreSQL instance,
 * exercising the CSRF cookie dance exactly like the SPA does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTests {

    @Autowired
    TestRestTemplate rest;

    @Test
    @Order(1)
    void unauthenticatedMeReturns401() {
        ResponseEntity<String> response = rest.getForEntity("/api/auth/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    void wrongPasswordReturns401() {
        ResponseEntity<String> response = login("admin", "definitely-wrong", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(3)
    void adminCanLogInAndFetchProfile() {
        Session session = new Session();
        ResponseEntity<String> login = login("admin", "LibrisAdmin#2026", session);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("\"role\":\"SUPER_ADMIN\"");

        ResponseEntity<String> me = rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(session.headers()), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("\"username\":\"admin\"");
    }

    @Test
    @Order(4)
    void readerCannotAccessAdminApi() {
        Session session = new Session();
        ResponseEntity<String> login = login("zhanghua", "LibrisReader#2026", session);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> denied = rest.exchange("/api/admin/anything", HttpMethod.GET,
                new HttpEntity<>(session.headers()), String.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(5)
    void repeatedFailuresGetRateLimited() {
        ResponseEntity<String> last = null;
        for (int i = 0; i < 6; i++) {
            last = login("lierfei", "wrong-password-" + i, null);
        }
        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ---------- helpers ----------

    private ResponseEntity<String> login(String username, String password, Session session) {
        Session s = session != null ? session : new Session();
        s.fetchCsrf();
        HttpHeaders headers = s.headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", password), headers), String.class);
        s.absorb(response);
        return response;
    }

    /** Minimal cookie jar carrying the session and CSRF cookies between calls. */
    private final class Session {
        private final Map<String, String> cookies = new LinkedHashMap<>();

        void fetchCsrf() {
            ResponseEntity<String> response = rest.exchange("/api/auth/me", HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class);
            absorb(response);
        }

        void absorb(ResponseEntity<?> response) {
            List<String> setCookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
            for (String cookie : setCookies) {
                String pair = cookie.split(";", 2)[0];
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String name = pair.substring(0, eq);
                    String value = pair.substring(eq + 1);
                    if (value.isBlank()) {
                        cookies.remove(name);
                    } else {
                        cookies.put(name, value);
                    }
                }
            }
        }

        HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            if (!cookies.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                cookies.forEach((k, v) -> sb.append(k).append('=').append(v).append("; "));
                headers.add(HttpHeaders.COOKIE, sb.substring(0, sb.length() - 2));
            }
            String xsrf = cookies.get("XSRF-TOKEN");
            if (xsrf != null) {
                headers.add("X-XSRF-TOKEN", xsrf);
            }
            return headers;
        }
    }
}
