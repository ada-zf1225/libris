package com.libris.web.admin;

import com.libris.domain.user.User;
import com.libris.security.SecurityUser;
import com.libris.service.circulation.CirculationQueryService;
import com.libris.service.circulation.CirculationService;
import com.libris.service.patron.PatronService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/circulation")
@RequiredArgsConstructor
public class CirculationController {

    private final CirculationService circulation;
    private final CirculationQueryService query;
    private final PatronService patrons;

    public record CheckoutRequest(@NotBlank String barcode, @NotNull Long readerId) {}

    public record BarcodeRequest(@NotBlank String barcode) {}

    public record RenewRequest(@NotNull Long loanId) {}

    /** Desk lookup: reader id or username → full patron card. */
    @GetMapping("/readers/{key}")
    public CirculationQueryService.ReaderSummary readerSummary(@PathVariable String key) {
        User reader = patrons.getByKey(key);
        return query.readerSummary(reader);
    }

    @PostMapping("/checkout")
    public CirculationService.CheckoutResult checkout(@Valid @RequestBody CheckoutRequest body,
                                                      @AuthenticationPrincipal SecurityUser operator) {
        return circulation.checkout(body.barcode().trim(), body.readerId(), operator.getId());
    }

    @PostMapping("/checkin")
    public CirculationService.CheckinResult checkin(@Valid @RequestBody BarcodeRequest body,
                                                    @AuthenticationPrincipal SecurityUser operator) {
        return circulation.checkin(body.barcode().trim(), operator.getId());
    }

    @PostMapping("/renew")
    public CirculationService.RenewResult renew(@Valid @RequestBody RenewRequest body,
                                                @AuthenticationPrincipal SecurityUser operator) {
        return circulation.renew(body.loanId(), null, operator.getId());
    }

    @PostMapping("/mark-lost")
    public CirculationService.LostResult markLost(@Valid @RequestBody BarcodeRequest body,
                                                  @AuthenticationPrincipal SecurityUser operator) {
        return circulation.markLost(body.barcode().trim(), operator.getId());
    }
}
