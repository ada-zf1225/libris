package com.libris.web.my;

import com.libris.domain.user.WebauthnCredential;
import com.libris.domain.user.WebauthnCredentialRepository;
import com.libris.security.SecurityUser;
import com.libris.service.account.AccountSecurityService;
import com.libris.web.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Self-service security centre: email verification, TOTP MFA, passkeys. */
@RestController
@RequestMapping("/api/my/security")
@RequiredArgsConstructor
public class SecurityController {

    private final AccountSecurityService accountSecurity;
    private final WebauthnCredentialRepository passkeys;

    // ---------- email ----------

    @PostMapping("/email/request-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestVerification(@AuthenticationPrincipal SecurityUser principal) {
        accountSecurity.requestEmailVerification(principal.getId());
    }

    // ---------- TOTP ----------

    @PostMapping("/totp/setup")
    public AccountSecurityService.TotpSetup totpSetup(@AuthenticationPrincipal SecurityUser principal) {
        return accountSecurity.startTotpEnrollment(principal.getId());
    }

    public record CodeRequest(@NotBlank @Size(max = 16) String code) {}

    public record RecoveryCodesResponse(List<String> recoveryCodes) {}

    @PostMapping("/totp/confirm")
    public RecoveryCodesResponse totpConfirm(@Valid @RequestBody CodeRequest body,
                                             @AuthenticationPrincipal SecurityUser principal) {
        return new RecoveryCodesResponse(accountSecurity.confirmTotpEnrollment(principal.getId(), body.code()));
    }

    public record DisableRequest(@NotBlank String password, @NotBlank @Size(max = 16) String code) {}

    @PostMapping("/totp/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void totpDisable(@Valid @RequestBody DisableRequest body,
                            @AuthenticationPrincipal SecurityUser principal) {
        accountSecurity.disableTotp(principal.getId(), body.password(), body.code());
    }

    @PostMapping("/totp/recovery-codes")
    public RecoveryCodesResponse regenerate(@Valid @RequestBody CodeRequest body,
                                            @AuthenticationPrincipal SecurityUser principal) {
        return new RecoveryCodesResponse(accountSecurity.regenerateRecoveryCodes(principal.getId(), body.code()));
    }

    // ---------- passkeys ----------

    public record PasskeyView(String credentialId, String label, Instant createdAt, Instant lastUsedAt) {}

    @GetMapping("/passkeys")
    public List<PasskeyView> list(@AuthenticationPrincipal SecurityUser principal) {
        return passkeys.findByUserId(principal.getId()).stream()
                .map(c -> new PasskeyView(c.getCredentialId(), c.getLabel(), c.getCreatedAt(), c.getLastUsedAt()))
                .toList();
    }

    public record RenameRequest(@NotBlank @Size(max = 64) String label) {}

    @PutMapping("/passkeys/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void rename(@PathVariable String credentialId, @Valid @RequestBody RenameRequest body,
                       @AuthenticationPrincipal SecurityUser principal) {
        WebauthnCredential credential = owned(credentialId, principal);
        credential.setLabel(body.label());
    }

    @DeleteMapping("/passkeys/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remove(@PathVariable String credentialId, @AuthenticationPrincipal SecurityUser principal) {
        passkeys.delete(owned(credentialId, principal));
    }

    private WebauthnCredential owned(String credentialId, SecurityUser principal) {
        return passkeys.findById(credentialId)
                .filter(c -> c.getUserId().equals(principal.getId()))
                .orElseThrow(() -> ApiException.notFound("error.notFound"));
    }
}
