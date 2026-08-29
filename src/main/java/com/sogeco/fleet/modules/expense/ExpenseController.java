package com.sogeco.fleet.modules.expense;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.expense.dto.ExpenseRequest;
import com.sogeco.fleet.modules.expense.dto.ExpenseResponse;
import com.sogeco.fleet.modules.expense.dto.ExpenseStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Depenses", description = "Salaires, peages, amendes et frais divers")
public class ExpenseController {

    private final ExpenseService service;

    @GetMapping
    @Operation(summary = "Lister les depenses")
    public PageResponse<ExpenseResponse> list(
            @PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/stats")
    @Operation(summary = "Repartition par poste de depense")
    public ExpenseStatsResponse stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return service.stats(start, end);
    }

    @PostMapping
    @Operation(summary = "Saisir une depense")
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une depense")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
