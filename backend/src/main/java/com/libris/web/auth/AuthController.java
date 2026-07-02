package com.libris.web.auth;

import com.libris.domain.user.*;
import com.libris.security.DbUserDetailsService;
import com.libris.security.LoginAttemptService;
import com.libris.security.SecurityUser;
import com.libris.service.account.AccountSecurityService;
import com.libris.web.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String MFA_PENDING_ATTR = "LIBRIS_MFA_PENDING_USER";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptService loginAttempts;
    private final UserRepository userRepository;
    private final ReaderProfileRepository profileRepository;
    private final WebauthnCredentialRepository webauthnCredentials;
    private final PasswordEncoder passwordEncoder;
    private final DbUserDetailsService userDetailsService;
    private final AccountSecurityService accountSecurity;
    private final AuthEventRepository authEvents;

    public record LoginResponse(boolean mfaRequired, MeResponse me) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest body,
                               HttpServletRequest request, HttpServletResponse response) {
        String ip = clientIp(request);
        if (loginAttempts.isBlocked(body.username(), ip)) {
            throw ApiException.tooManyRequests("error.auth.tooManyAttempts");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (BadCredentialsException | LockedException | DisabledException ex) {
            loginAttempts.recordFailure(body.username(), ip);
            event(null, body.username(), "LOGIN_FAILED", request);
            throw ex instanceof BadCredentialsException ? ex : new BadCredentialsException("locked");
        }
        loginAttempts.reset(body.username(), ip);

        SecurityUser principal = (SecurityUser) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();

        if (user.isMfaEnabled()) {
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            session.setAttribute(MFA_PENDING_ATTR, user.getId());
            event(user.getId(), user.getUsername(), "LOGIN_MFA_PENDING", request);
            return new LoginResponse(true, null);
        }

        establishSession(authentication, request, response);
        event(user.getId(), user.getUsername(), "LOGIN_SUCCESS", request);
        return new LoginResponse(false, me(principal.getId()));
    }

    public record MfaVerifyRequest(@NotBlank @Size(max = 16) String code) {}

    @PostMapping("/mfa/verify")
    public LoginResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest body,
                                   HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Object pending = session == null ? null : session.getAttribute(MFA_PENDING_ATTR);
        if (pending == null) {
            throw ApiException.badRequest("error.mfa.noPending");
        }
        Long userId = (Long) pending;
        User user = userRepository.findById(userId).orElseThrow();
        String ip = clientIp(request);
        if (loginAttempts.isBlocked("mfa:" + user.getUsername(), ip)) {
            throw ApiException.tooManyRequests("error.auth.tooManyAttempts");
        }
        if (!accountSecurity.verifyMfa(user, body.code())) {
            loginAttempts.recordFailure("mfa:" + user.getUsername(), ip);
            event(userId, user.getUsername(), "MFA_FAILED", request);
            throw ApiException.badRequest("error.mfa.codeInvalid");
        }
        loginAttempts.reset("mfa:" + user.getUsername(), ip);
        session.removeAttribute(MFA_PENDING_ATTR);

        var details = userDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities());
        request.changeSessionId();
        establishSession(authentication, request, response);
        event(userId, user.getUsername(), "MFA_SUCCESS", request);
        return new LoginResponse(false, me(userId));
    }

    // ---------- password reset & email verification (unauthenticated) ----------

    public record ForgotPasswordRequest(@NotBlank @Size(max = 128) String usernameOrEmail) {}

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body,
                                               HttpServletRequest request) {
        String ip = clientIp(request);
        if (loginAttempts.isBlocked("forgot:" + ip, ip)) {
            throw ApiException.tooManyRequests("error.auth.tooManyAttempts");
        }
        loginAttempts.recordFailure("forgot:" + ip, ip); // rate-limit the endpoint itself
        accountSecurity.requestPasswordReset(body.usernameOrEmail().trim());
        return ResponseEntity.noContent().build();
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank
            @Size(min = 8, max = 128, message = "{validation.password.length}")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "{validation.password.mix}")
            String newPassword) {}

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body,
                                              HttpServletRequest request) {
        accountSecurity.resetPassword(body.token(), body.newPassword());
        event(null, null, "PASSWORD_RESET", request);
        return ResponseEntity.noContent().build();
    }

    public record VerifyEmailRequest(@NotBlank String token) {}

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest body) {
        accountSecurity.verifyEmail(body.token());
        return ResponseEntity.noContent().build();
    }

    // ---------- session info & self-service ----------

    @GetMapping("/me")
    public MeResponse currentUser(@AuthenticationPrincipal SecurityUser principal) {
        return me(principal.getId());
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                               @AuthenticationPrincipal SecurityUser principal,
                                               HttpServletRequest request) {
        User user = userRepository.findById(principal.getId()).orElseThrow();
        if (!passwordEncoder.matches(body.oldPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("error.auth.oldPasswordMismatch");
        }
        user.setPasswordHash(passwordEncoder.encode(body.newPassword()));
        event(user.getId(), user.getUsername(), "PASSWORD_CHANGED", request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/locale")
    @Transactional
    public ResponseEntity<Void> updateLocale(@Valid @RequestBody LocaleRequest body,
                                             @AuthenticationPrincipal SecurityUser principal) {
        User user = userRepository.findById(principal.getId()).orElseThrow();
        user.setPreferredLocale(body.locale());
        return ResponseEntity.noContent().build();
    }

    // ---------- helpers ----------

    private void establishSession(Authentication authentication,
                                  HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private MeResponse me(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        var readerType = profileRepository.findById(user.getId())
                .map(p -> p.getReaderType().name())
                .orElse(null);
        List<String> permissions = userDetailsService.permissionsOf(user).stream()
                .map(Enum::name).sorted().toList();
        int passkeys = webauthnCredentials.findByUserId(user.getId()).size();
        return new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole().name(), user.getStatus().name(), user.getEmail(),
                user.getPreferredLocale(), readerType, permissions,
                user.isEmailVerified(), user.isMfaEnabled(), passkeys);
    }

    private void event(Long userId, String username, String type, HttpServletRequest request) {
        authEvents.save(new AuthEvent(userId, username, type, clientIp(request),
                request.getHeader("User-Agent")));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
