package com.storemanager.sales.infrastructure.web;

import com.storemanager.catalog.infrastructure.web.ErrorResponse;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import com.storemanager.invoicing.usecase.VoidRejectedException;
import com.storemanager.invoicing.usecase.VoucherNotFoundException;
import com.storemanager.invoicing.usecase.VoucherNotVoidableException;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.usecase.CancelSaleUseCase;
import com.storemanager.sales.usecase.GetSaleUseCase;
import com.storemanager.sales.usecase.InactiveProductException;
import com.storemanager.sales.usecase.InsufficientStockException;
import com.storemanager.sales.usecase.ListSalesUseCase;
import com.storemanager.sales.usecase.ProductNotFoundException;
import com.storemanager.sales.usecase.RecordSaleUseCase;
import com.storemanager.sales.usecase.RetrySaleVoucherUseCase;
import com.storemanager.sales.usecase.SaleLineItemRequest;
import com.storemanager.sales.usecase.SaleNotFoundException;
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
@RequestMapping("/api/sales")
public class SaleController {

    private final RecordSaleUseCase recordSaleUseCase;
    private final ListSalesUseCase listSalesUseCase;
    private final GetSaleUseCase getSaleUseCase;
    private final RetrySaleVoucherUseCase retrySaleVoucherUseCase;
    private final CancelSaleUseCase cancelSaleUseCase;
    private final VoucherRepositoryPort voucherRepository;

    public SaleController(
            RecordSaleUseCase recordSaleUseCase,
            ListSalesUseCase listSalesUseCase,
            GetSaleUseCase getSaleUseCase,
            RetrySaleVoucherUseCase retrySaleVoucherUseCase,
            CancelSaleUseCase cancelSaleUseCase,
            VoucherRepositoryPort voucherRepository) {
        this.recordSaleUseCase = recordSaleUseCase;
        this.listSalesUseCase = listSalesUseCase;
        this.getSaleUseCase = getSaleUseCase;
        this.retrySaleVoucherUseCase = retrySaleVoucherUseCase;
        this.cancelSaleUseCase = cancelSaleUseCase;
        this.voucherRepository = voucherRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse record(@Valid @RequestBody RecordSaleRequest request) {
        List<SaleLineItemRequest> lines =
                request.items().stream().map(item -> new SaleLineItemRequest(item.productId(), item.quantity())).toList();
        Sale sale = recordSaleUseCase.execute(lines, request.buyerDocumentType(), request.buyerDocumentNumber());
        return toResponse(sale);
    }

    @GetMapping
    public List<SaleResponse> list() {
        return listSalesUseCase.execute().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return toResponse(getSaleUseCase.execute(id));
    }

    private SaleResponse toResponse(Sale sale) {
        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElse(null);
        return SaleResponse.from(sale, voucher);
    }

    @PostMapping("/{id}/retry-voucher")
    public VoucherResponse retryVoucher(@PathVariable Long id) {
        return VoucherResponse.from(retrySaleVoucherUseCase.execute(id));
    }

    @PostMapping("/{id}/void")
    public SaleResponse voidSale(@PathVariable Long id, @Valid @RequestBody VoidSaleRequest request) {
        Sale cancelled = cancelSaleUseCase.execute(id, request.reason());
        return toResponse(cancelled);
    }

    @ExceptionHandler(SaleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSaleNotFound(SaleNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(InactiveProductException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInactiveProduct(InactiveProductException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInsufficientStock(InsufficientStockException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidArgument(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(VoucherNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleVoucherNotFound(VoucherNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(VoucherNotVoidableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleVoucherNotVoidable(VoucherNotVoidableException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /** The retry use case rejects a voucher that isn't PENDING/NEEDS_ATTENTION with this — a state conflict, not a malformed request. */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalState(IllegalStateException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /** The OSE (NubeFacT) didn't confirm the void — an upstream dependency failing to complete our request. */
    @ExceptionHandler(VoidRejectedException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleVoidRejected(VoidRejectedException ex) {
        return new ErrorResponse(ex.getMessage());
    }
}
