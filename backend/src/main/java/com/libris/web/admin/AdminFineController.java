package com.libris.web.admin;

import com.libris.security.SecurityUser;
import com.libris.service.circulation.CirculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/fines")
@RequiredArgsConstructor
public class AdminFineController {

    private final CirculationService circulation;

    @PostMapping("/{id}/pay")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pay(@PathVariable Long id, @AuthenticationPrincipal SecurityUser operator) {
        circulation.payFine(id, operator.getId());
    }

    @PostMapping("/{id}/waive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void waive(@PathVariable Long id, @AuthenticationPrincipal SecurityUser operator) {
        circulation.waiveFine(id, operator.getId());
    }
}
