package com.storemanager.sales.infrastructure.web;

import com.storemanager.catalog.infrastructure.web.ErrorResponse;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.usecase.GetSaleUseCase;
import com.storemanager.sales.usecase.InactiveProductException;
import com.storemanager.sales.usecase.InsufficientStockException;
import com.storemanager.sales.usecase.ListSalesUseCase;
import com.storemanager.sales.usecase.ProductNotFoundException;
import com.storemanager.sales.usecase.RecordSaleUseCase;
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

    public SaleController(
            RecordSaleUseCase recordSaleUseCase, ListSalesUseCase listSalesUseCase, GetSaleUseCase getSaleUseCase) {
        this.recordSaleUseCase = recordSaleUseCase;
        this.listSalesUseCase = listSalesUseCase;
        this.getSaleUseCase = getSaleUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse record(@Valid @RequestBody RecordSaleRequest request) {
        List<SaleLineItemRequest> lines =
                request.items().stream().map(item -> new SaleLineItemRequest(item.productId(), item.quantity())).toList();
        Sale sale = recordSaleUseCase.execute(lines, request.buyerDocumentType(), request.buyerDocumentNumber());
        return SaleResponse.from(sale);
    }

    @GetMapping
    public List<SaleResponse> list() {
        return listSalesUseCase.execute().stream().map(SaleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return SaleResponse.from(getSaleUseCase.execute(id));
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
}
