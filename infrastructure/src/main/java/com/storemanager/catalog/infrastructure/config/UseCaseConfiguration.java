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
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import com.storemanager.sales.usecase.GetSaleUseCase;
import com.storemanager.sales.usecase.ListSalesUseCase;
import com.storemanager.sales.usecase.RecordSaleUseCase;
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
            SaleRepositoryPort saleRepositoryPort, ProductRepositoryPort productRepositoryPort) {
        return new RecordSaleUseCase(saleRepositoryPort, productRepositoryPort);
    }

    @Bean
    public ListSalesUseCase listSalesUseCase(SaleRepositoryPort saleRepositoryPort) {
        return new ListSalesUseCase(saleRepositoryPort);
    }

    @Bean
    public GetSaleUseCase getSaleUseCase(SaleRepositoryPort saleRepositoryPort) {
        return new GetSaleUseCase(saleRepositoryPort);
    }
}
