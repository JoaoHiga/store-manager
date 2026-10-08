package com.storemanager.purchasing.infrastructure.web;

import com.storemanager.catalog.infrastructure.web.ErrorResponse;
import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.usecase.CreateSupplierUseCase;
import com.storemanager.purchasing.usecase.DeactivateSupplierUseCase;
import com.storemanager.purchasing.usecase.DuplicateSupplierRucException;
import com.storemanager.purchasing.usecase.ListSuppliersUseCase;
import com.storemanager.purchasing.usecase.ReactivateSupplierUseCase;
import com.storemanager.purchasing.usecase.SupplierNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final CreateSupplierUseCase createSupplierUseCase;
    private final ListSuppliersUseCase listSuppliersUseCase;
    private final DeactivateSupplierUseCase deactivateSupplierUseCase;
    private final ReactivateSupplierUseCase reactivateSupplierUseCase;

    public SupplierController(
            CreateSupplierUseCase createSupplierUseCase,
            ListSuppliersUseCase listSuppliersUseCase,
            DeactivateSupplierUseCase deactivateSupplierUseCase,
            ReactivateSupplierUseCase reactivateSupplierUseCase) {
        this.createSupplierUseCase = createSupplierUseCase;
        this.listSuppliersUseCase = listSuppliersUseCase;
        this.deactivateSupplierUseCase = deactivateSupplierUseCase;
        this.reactivateSupplierUseCase = reactivateSupplierUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request) {
        Supplier supplier =
                createSupplierUseCase.execute(
                        request.name(), request.ruc(), request.phone(), request.email(), request.address());
        return SupplierResponse.from(supplier);
    }

    /** No {@code active} parameter lists every supplier; {@code active=true|false} lists only that set. */
    @GetMapping
    public List<SupplierResponse> list(@RequestParam(required = false) Boolean active) {
        return listSuppliersUseCase.execute(active).stream().map(SupplierResponse::from).toList();
    }

    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        deactivateSupplierUseCase.execute(id);
    }

    @PatchMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable Long id) {
        reactivateSupplierUseCase.execute(id);
    }

    @ExceptionHandler(DuplicateSupplierRucException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateRuc(DuplicateSupplierRucException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSupplierNotFound(SupplierNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidArgument(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }
}
