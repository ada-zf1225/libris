package com.libris.web.auth;

import com.libris.TestcontainersConfiguration;
import com.libris.domain.user.*;
import com.libris.security.TotpService;
import com.libris.service.account.AccountSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IAM lifecycle on real PostgreSQL: password reset tokens, TOTP enrollment
 * and challenge login, recovery codes, and fine-grained staff permissions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IamFlowTests {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository users;
    @Autowired AuthTokenRepository tokens;
    @Autowired UserPermissionRepository permissions;
    @Autowired AccountSecurityService accountSecurity;
    @Autowired TotpService totp;
    @Autowired PasswordEncoder encoder;

    // ---------- password reset ----------

    @Test
    void passwordResetRoundtrip() {
        // request never leaks whether the account exists
        Session s = new Session();
        s.fetchCsrf();
        assertThat(post(s, "/api/auth/forgot-password",
                Map.of("usernameOrEmail", "no-such-user")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        User user = users.findByUsername("liyichen").orElseThrow();
        accountSecurity.requestPasswordReset(user.getUsername());

        // the raw token travels by mail; for the test we mint one directly
        String raw = "test-reset-token-1";
        tokens.save(new AuthToken(user.getId(), AuthToken.Purpose.RESET,
                AccountSecurityService.sha256(raw), Instant.now().plusSeconds(600)));

        ResponseEntity<String> reset = post(s, "/api/auth/reset-password",
                Map.of("token", raw, "newPassword", "NewPass#2026a"));
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(encoder.matches("NewPass#2026a",
                users.findByUsername("liyichen").orElseThrow().getPasswordHash())).isTrue();

        // token is single-use
        assertThat(post(s, "/api/auth/reset-password",
                Map.of("token", raw, "newPassword", "Other#2026aa")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- TOTP MFA ----------

    @Test
    void totpEnrollmentAndChallengeLogin() {
        User user = users.findByUsername("wangwaner").orElseThrow();
        var setup = accountSecurity.startTotpEnrollment(user.getId());
        String code = totp.generate(setup.secret(), Instant.now().getEpochSecond() / 30);
        List<String> recovery = accountSecurity.confirmTotpEnrollment(user.getId(), code);
        assertThat(recovery).hasSize(10);
        assertThat(users.findByUsername("wangwaner").orElseThrow().isMfaEnabled()).isTrue();

        // password alone no longer signs in — server demands the second factor
        Session s = new Session();
        ResponseEntity<String> first = login(s, "wangwaner", "LibrisReader#2026");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).contains("\"mfaRequired\":true");
        assertThat(get(s, "/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // wrong code rejected
        assertThat(post(s, "/api/auth/mfa/verify", Map.of("code", "000000")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // fresh TOTP code completes the sign-in
        String liveCode = totp.generate(
                users.findByUsername("wangwaner").orElseThrow().getTotpSecret(),
                Instant.now().getEpochSecond() / 30);
        ResponseEntity<String> second = post(s, "/api/auth/mfa/verify", Map.of("code", liveCode));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).contains("\"mfaEnabled\":true");
        assertThat(get(s, "/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.OK);

        // recovery code also completes a later challenge, once
        Session s2 = new Session();
        assertThat(login(s2, "wangwaner", "LibrisReader#2026").getBody()).contains("\"mfaRequired\":true");
        ResponseEntity<String> viaRecovery = post(s2, "/api/auth/mfa/verify",
                Map.of("code", recovery.get(0)));
        assertThat(viaRecovery.getStatusCode()).isEqualTo(HttpStatus.OK);

        Session s3 = new Session();
        login(s3, "wangwaner", "LibrisReader#2026");
        assertThat(post(s3, "/api/auth/mfa/verify", Map.of("code", recovery.get(0))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- staff tiers & fine-grained permissions ----------

    @Test
    void librarianPermissionsAreEnforcedPerCapability() {
        User librarian = users.findByUsername("libtest").orElseGet(() -> {
            User u = new User("libtest", encoder.encode("Librarian#2026"), "测试馆员",
                    Role.LIBRARIAN, "libtest@libris.local");
            users.save(u);
            return u;
        });
        permissions.deleteAll(permissions.findByUserId(librarian.getId()));
        permissions.save(new UserPermission(librarian.getId(), Permission.CIRCULATION));

        Session s = new Session();
        ResponseEntity<String> login = login(s, "libtest", "Librarian#2026");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("\"role\":\"LIBRARIAN\"").contains("CIRCULATION");

        // granted capability works
        assertThat(get(s, "/api/admin/circulation/readers/zhanghua").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // missing capability → 403 even inside the staff area
        assertThat(get(s, "/api/admin/policies").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // staff management is SUPER_ADMIN territory
        assertThat(get(s, "/api/admin/staff").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // super admin passes everywhere
        Session admin = new Session();
        login(admin, "admin", "LibrisAdmin#2026");
        assertThat(get(admin, "/api/admin/policies").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(admin, "/api/admin/staff").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(admin, "/api/admin/auth-events").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- helpers ----------

    private ResponseEntity<String> login(Session s, String username, String password) {
        s.fetchCsrf();
        return post(s, "/api/auth/login", Map.of("username", username, "password", password));
    }

    private ResponseEntity<String> post(Session s, String url, Map<String, String> body) {
        HttpHeaders headers = s.headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        s.absorb(response);
        return response;
    }

    private ResponseEntity<String> get(Session s, String url) {
        ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(s.headers()), String.class);
        s.absorb(response);
        return response;
    }

    /** Cookie jar for session + CSRF continuity. */
    private final class Session {
        private final Map<String, String> cookies = new LinkedHashMap<>();

        void fetchCsrf() {
            ResponseEntity<String> response = rest.exchange("/api/auth/me", HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class);
            absorb(response);
        }

        void absorb(ResponseEntity<?> response) {
            for (String cookie : response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)) {
                String pair = cookie.split(";", 2)[0];
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String value = pair.substring(eq + 1);
                    if (value.isBlank()) {
                        cookies.remove(pair.substring(0, eq));
                    } else {
                        cookies.put(pair.substring(0, eq), value);
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
