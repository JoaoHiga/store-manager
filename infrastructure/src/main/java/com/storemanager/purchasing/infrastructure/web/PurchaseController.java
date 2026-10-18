package com.storemanager.purchasing.infrastructure.web;

import com.storemanager.catalog.infrastructure.web.ErrorResponse;
import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.usecase.DuplicatePurchaseInvoiceException;
import com.storemanager.purchasing.usecase.GetPurchaseUseCase;
import com.storemanager.purchasing.usecase.InactiveProductException;
import com.storemanager.purchasing.usecase.InactiveSupplierException;
import com.storemanager.purchasing.usecase.ListPurchasesUseCase;
import com.storemanager.purchasing.usecase.ProductNotFoundException;
import com.storemanager.purchasing.usecase.PurchaseLineItemRequest;
import com.storemanager.purchasing.usecase.PurchaseNotFoundException;
import com.storemanager.purchasing.usecase.RecordPurchaseUseCase;
import com.storemanager.purchasing.usecase.SupplierNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final RecordPurchaseUseCase recordPurchaseUseCase;
    private final ListPurchasesUseCase listPurchasesUseCase;
    private final GetPurchaseUseCase getPurchaseUseCase;

    public PurchaseController(
            RecordPurchaseUseCase recordPurchaseUseCase,
            ListPurchasesUseCase listPurchasesUseCase,
            GetPurchaseUseCase getPurchaseUseCase) {
        this.recordPurchaseUseCase = recordPurchaseUseCase;
        this.listPurchasesUseCase = listPurchasesUseCase;
        this.getPurchaseUseCase = getPurchaseUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse record(@Valid @RequestBody RecordPurchaseRequest request) {
        List<PurchaseLineItemRequest> lines =
                request.items().stream()
                        .map(item -> new PurchaseLineItemRequest(item.productId(), item.quantity(), item.unitCost()))
                        .toList();
        Purchase purchase =
                recordPurchaseUseCase.execute(
                        request.supplierId(), request.invoiceNumber(), request.purchaseDate(), lines);
        return PurchaseResponse.from(purchase);
    }

    /** Newest first. */
    @GetMapping
    public List<PurchaseSummaryResponse> list() {
        return listPurchasesUseCase.execute().stream().map(PurchaseSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PurchaseResponse get(@PathVariable Long id) {
        return PurchaseResponse.from(getPurchaseUseCase.execute(id));
    }

    @ExceptionHandler(PurchaseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePurchaseNotFound(PurchaseNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSupplierNotFound(SupplierNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /** Same mapping as an inactive product in a sale: a rejected request, not a state conflict. */
    @ExceptionHandler(InactiveSupplierException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInactiveSupplier(InactiveSupplierException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(InactiveProductException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInactiveProduct(InactiveProductException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(DuplicatePurchaseInvoiceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateInvoice(DuplicatePurchaseInvoiceException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidArgument(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }
}
