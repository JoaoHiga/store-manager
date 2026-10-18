package com.storemanager.catalog.infrastructure.config;

import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import com.storemanager.catalog.usecase.CreateCategoryUseCase;
import com.storemanager.catalog.usecase.CreateProductUseCase;
import com.storemanager.catalog.usecase.DeactivateProductUseCase;
import com.storemanager.catalog.usecase.ListCategoriesUseCase;
import com.storemanager.catalog.usecase.ListProductsUseCase;
import com.storemanager.catalog.usecase.ReactivateProductUseCase;
import com.storemanager.catalog.usecase.UpdateProductUseCase;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import com.storemanager.invoicing.usecase.IssueVoucherForSaleUseCase;
import com.storemanager.invoicing.usecase.RetryPendingVoucherUseCase;
import com.storemanager.invoicing.usecase.VoidSaleUseCase;
import com.storemanager.purchasing.domain.port.PurchaseRepositoryPort;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import com.storemanager.purchasing.usecase.CreateSupplierUseCase;
import com.storemanager.purchasing.usecase.DeactivateSupplierUseCase;
import com.storemanager.purchasing.usecase.GetPurchaseUseCase;
import com.storemanager.purchasing.usecase.ListPurchasesUseCase;
import com.storemanager.purchasing.usecase.ListSuppliersUseCase;
import com.storemanager.purchasing.usecase.ReactivateSupplierUseCase;
import com.storemanager.purchasing.usecase.RecordPurchaseUseCase;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import com.storemanager.sales.usecase.CancelSaleUseCase;
import com.storemanager.sales.usecase.GetSaleUseCase;
import com.storemanager.sales.usecase.ListSalesUseCase;
import com.storemanager.sales.usecase.RecordSaleUseCase;
import com.storemanager.sales.usecase.RetrySaleVoucherUseCase;
import com.storemanager.shared.domain.port.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires application-module use cases as Spring beans. This class has to live
 * here, not in {@code application}: use cases carry no Spring annotations
 * themselves (that module has zero Spring dependency), so infrastructure is
 * responsible for constructing and registering them explicitly.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    public CreateCategoryUseCase createCategoryUseCase(CategoryRepositoryPort categoryRepositoryPort) {
        return new CreateCategoryUseCase(categoryRepositoryPort);
    }

    @Bean
    public ListCategoriesUseCase listCategoriesUseCase(CategoryRepositoryPort categoryRepositoryPort) {
        return new ListCategoriesUseCase(categoryRepositoryPort);
    }

    @Bean
    public CreateProductUseCase createProductUseCase(
            ProductRepositoryPort productRepositoryPort, CategoryRepositoryPort categoryRepositoryPort) {
        return new CreateProductUseCase(productRepositoryPort, categoryRepositoryPort);
    }

    @Bean
    public ListProductsUseCase listProductsUseCase(ProductRepositoryPort productRepositoryPort) {
        return new ListProductsUseCase(productRepositoryPort);
    }

    @Bean
    public UpdateProductUseCase updateProductUseCase(
            ProductRepositoryPort productRepositoryPort, CategoryRepositoryPort categoryRepositoryPort) {
        return new UpdateProductUseCase(productRepositoryPort, categoryRepositoryPort);
    }

    @Bean
    public DeactivateProductUseCase deactivateProductUseCase(ProductRepositoryPort productRepositoryPort) {
        return new DeactivateProductUseCase(productRepositoryPort);
    }

    @Bean
    public ReactivateProductUseCase reactivateProductUseCase(ProductRepositoryPort productRepositoryPort) {
        return new ReactivateProductUseCase(productRepositoryPort);
    }

    @Bean
    public RecordSaleUseCase recordSaleUseCase(
            SaleRepositoryPort saleRepositoryPort,
            ProductRepositoryPort productRepositoryPort,
            IssueVoucherForSaleUseCase issueVoucherForSaleUseCase,
            TransactionRunner transactionRunner) {
        return new RecordSaleUseCase(
                saleRepositoryPort, productRepositoryPort, issueVoucherForSaleUseCase, transactionRunner);
    }

    @Bean
    public ListSalesUseCase listSalesUseCase(SaleRepositoryPort saleRepositoryPort) {
        return new ListSalesUseCase(saleRepositoryPort);
    }

    @Bean
    public GetSaleUseCase getSaleUseCase(SaleRepositoryPort saleRepositoryPort) {
        return new GetSaleUseCase(saleRepositoryPort);
    }

    @Bean
    public IssueVoucherForSaleUseCase issueVoucherForSaleUseCase(
            VoucherRepositoryPort voucherRepositoryPort, InvoicingProvider invoicingProvider) {
        return new IssueVoucherForSaleUseCase(voucherRepositoryPort, invoicingProvider);
    }

    @Bean
    public RetryPendingVoucherUseCase retryPendingVoucherUseCase(
            VoucherRepositoryPort voucherRepositoryPort,
            SaleRepositoryPort saleRepositoryPort,
            InvoicingProvider invoicingProvider) {
        return new RetryPendingVoucherUseCase(voucherRepositoryPort, saleRepositoryPort, invoicingProvider);
    }

    @Bean
    public VoidSaleUseCase voidSaleUseCase(
            VoucherRepositoryPort voucherRepositoryPort, InvoicingProvider invoicingProvider) {
        return new VoidSaleUseCase(voucherRepositoryPort, invoicingProvider);
    }

    @Bean
    public RetrySaleVoucherUseCase retrySaleVoucherUseCase(
            VoucherRepositoryPort voucherRepositoryPort, RetryPendingVoucherUseCase retryPendingVoucherUseCase) {
        return new RetrySaleVoucherUseCase(voucherRepositoryPort, retryPendingVoucherUseCase);
    }

    @Bean
    public CancelSaleUseCase cancelSaleUseCase(VoidSaleUseCase voidSaleUseCase, SaleRepositoryPort saleRepositoryPort) {
        return new CancelSaleUseCase(voidSaleUseCase, saleRepositoryPort);
    }

    @Bean
    public CreateSupplierUseCase createSupplierUseCase(SupplierRepositoryPort supplierRepositoryPort) {
        return new CreateSupplierUseCase(supplierRepositoryPort);
    }

    @Bean
    public ListSuppliersUseCase listSuppliersUseCase(SupplierRepositoryPort supplierRepositoryPort) {
        return new ListSuppliersUseCase(supplierRepositoryPort);
    }

    @Bean
    public DeactivateSupplierUseCase deactivateSupplierUseCase(SupplierRepositoryPort supplierRepositoryPort) {
        return new DeactivateSupplierUseCase(supplierRepositoryPort);
    }

    @Bean
    public ReactivateSupplierUseCase reactivateSupplierUseCase(SupplierRepositoryPort supplierRepositoryPort) {
        return new ReactivateSupplierUseCase(supplierRepositoryPort);
    }

    @Bean
    public RecordPurchaseUseCase recordPurchaseUseCase(
            PurchaseRepositoryPort purchaseRepositoryPort,
            SupplierRepositoryPort supplierRepositoryPort,
            ProductRepositoryPort productRepositoryPort,
            TransactionRunner transactionRunner) {
        return new RecordPurchaseUseCase(
                purchaseRepositoryPort, supplierRepositoryPort, productRepositoryPort, transactionRunner);
    }

    @Bean
    public ListPurchasesUseCase listPurchasesUseCase(PurchaseRepositoryPort purchaseRepositoryPort) {
        return new ListPurchasesUseCase(purchaseRepositoryPort);
    }

    @Bean
    public GetPurchaseUseCase getPurchaseUseCase(PurchaseRepositoryPort purchaseRepositoryPort) {
        return new GetPurchaseUseCase(purchaseRepositoryPort);
    }
}
