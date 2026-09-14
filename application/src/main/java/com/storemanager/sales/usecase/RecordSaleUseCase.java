package com.storemanager.sales.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records a sale against the existing product catalog. For each requested line, validates the
 * referenced product exists, is active, and has enough stock via the existing
 * {@code ProductRepositoryPort} — {@code product-catalog}'s own code is never modified (see
 * proposal.md, Dependencies), but a completed sale DOES decrement stock through that same port,
 * since a sale is exactly the event that consumes it. Quantities are validated cumulatively per
 * product across every line in the request (not line-by-line against the untouched stock), so two
 * lines referencing the same product can't each pass an individual check while jointly overselling
 * — found and required by review-reliability's R3-stock-not-decremented finding. Then each
 * product's current {@code salePrice}/{@code description} is snapshotted onto its {@link SaleItem}
 * so a later catalog change never affects this sale (see design.md, Decision: Sale line-item
 * pricing). Nothing here calls invoicing: that's wired in a later PR.
 */
public class RecordSaleUseCase {

    private final SaleRepositoryPort saleRepository;
    private final ProductRepositoryPort productRepository;

    public RecordSaleUseCase(SaleRepositoryPort saleRepository, ProductRepositoryPort productRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
    }

    public Sale execute(List<SaleLineItemRequest> lines, String buyerDocumentType, String buyerDocumentNumber) {
        Map<Long, Integer> requestedQuantityByProductId = new LinkedHashMap<>();
        for (SaleLineItemRequest line : lines) {
            requestedQuantityByProductId.merge(line.productId(), line.quantity(), Integer::sum);
        }

        Map<Long, Product> productsById = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> requested : requestedQuantityByProductId.entrySet()) {
            Long productId = requested.getKey();
            int totalRequestedQuantity = requested.getValue();
            Product product =
                    productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
            if (!product.isActive()) {
                throw new InactiveProductException(product.getId());
            }
            if (totalRequestedQuantity > product.getStockQuantity()) {
                throw new InsufficientStockException(
                        product.getId(), totalRequestedQuantity, product.getStockQuantity());
            }
            productsById.put(productId, product);
        }

        List<SaleItem> items =
                lines.stream().map(line -> toSaleItem(line, productsById.get(line.productId()))).toList();
        Sale sale = Sale.createNew(items, buyerDocumentType, buyerDocumentNumber, Instant.now());
        Sale saved = saleRepository.save(sale);

        Instant now = Instant.now();
        for (Map.Entry<Long, Integer> requested : requestedQuantityByProductId.entrySet()) {
            Product product = productsById.get(requested.getKey());
            int remainingStock = product.getStockQuantity() - requested.getValue();
            productRepository.save(
                    product.withUpdatedFields(
                            product.getName(),
                            product.getDescription(),
                            product.getCategoryId(),
                            product.getBrand(),
                            product.getCostPrice(),
                            product.getSalePrice(),
                            remainingStock,
                            now));
        }

        return saved;
    }

    private SaleItem toSaleItem(SaleLineItemRequest line, Product product) {
        return SaleItem.createNew(product.getId(), product.getDescription(), product.getSalePrice(), line.quantity());
    }
}
