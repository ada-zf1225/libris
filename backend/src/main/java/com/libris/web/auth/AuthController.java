package com.libris.web.auth;

import com.libris.domain.user.ReaderProfileRepository;
import com.libris.domain.user.User;
import com.libris.domain.user.UserRepository;
import com.libris.security.LoginAttemptService;
import com.libris.security.SecurityUser;
import com.libris.web.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptService loginAttempts;
    private final UserRepository userRepository;
    private final ReaderProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body,
                            HttpServletRequest request, HttpServletResponse response) {
        String ip = clientIp(request);
        if (loginAttempts.isBlocked(body.username(), ip)) {
            throw ApiException.tooManyRequests("error.auth.tooManyAttempts");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (BadCredentialsException ex) {
            loginAttempts.recordFailure(body.username(), ip);
            throw ex;
        }
        loginAttempts.reset(body.username(), ip);

        // rotate the session id on privilege change, then persist the context
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return me(((SecurityUser) authentication.getPrincipal()));
    }

    @GetMapping("/me")
    public MeResponse currentUser(@AuthenticationPrincipal SecurityUser principal) {
        return me(principal);
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                               @AuthenticationPrincipal SecurityUser principal) {
        User user = userRepository.findById(principal.getId()).orElseThrow();
        if (!passwordEncoder.matches(body.oldPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("error.auth.oldPasswordMismatch");
        }
        user.setPasswordHash(passwordEncoder.encode(body.newPassword()));
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

    private MeResponse me(SecurityUser principal) {
        User user = userRepository.findById(principal.getId()).orElseThrow();
        var readerType = profileRepository.findById(user.getId())
                .map(p -> p.getReaderType().name())
                .orElse(null);
        return new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole().name(), user.getStatus().name(), user.getEmail(),
                user.getPreferredLocale(), readerType);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
