package com.libris.web.admin;

import com.libris.domain.circulation.LoanPolicy;
import com.libris.domain.circulation.LoanPolicyRepository;
import com.libris.web.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
public class AdminPolicyController {

    private final LoanPolicyRepository policies;

    public record PolicyRequest(
            @Min(1) @Max(365) int loanDays,
            @Min(1) @Max(200) int maxLoans,
            @Min(0) @Max(10) int maxRenewals,
            @Min(0) @Max(10_000) int dailyFineCents,
            @Min(1) @Max(100) int blockOverdueCount,
            @Min(1) @Max(1_000_000) int blockFineCents) {}

    public record PolicyView(Long id, String readerType, int loanDays, int maxLoans, int maxRenewals,
                             int dailyFineCents, int blockOverdueCount, int blockFineCents) {
        static PolicyView of(LoanPolicy p) {
            return new PolicyView(p.getId(), p.getReaderType().name(), p.getLoanDays(), p.getMaxLoans(),
                    p.getMaxRenewals(), p.getDailyFineCents(), p.getBlockOverdueCount(), p.getBlockFineCents());
        }
    }

    @GetMapping
    public List<PolicyView> list() {
        return policies.findAll().stream().map(PolicyView::of).toList();
    }

    @PutMapping("/{id}")
    @Transactional
    public PolicyView update(@PathVariable Long id, @Valid @RequestBody PolicyRequest body) {
        LoanPolicy policy = policies.findById(id)
                .orElseThrow(() -> ApiException.notFound("error.notFound"));
        policy.setLoanDays(body.loanDays());
        policy.setMaxLoans(body.maxLoans());
        policy.setMaxRenewals(body.maxRenewals());
        policy.setDailyFineCents(body.dailyFineCents());
        policy.setBlockOverdueCount(body.blockOverdueCount());
        policy.setBlockFineCents(body.blockFineCents());
        return PolicyView.of(policy);
    }
}
