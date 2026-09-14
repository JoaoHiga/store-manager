package com.storemanager.sales.infrastructure.persistence;

import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.SaleStatus;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SaleRepositoryAdapter implements SaleRepositoryPort {

    private final SaleJpaRepository jpaRepository;

    public SaleRepositoryAdapter(SaleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Sale save(Sale sale) {
        List<SaleItemJpaEntity> items =
                sale.getItems().stream()
                        .map(
                                item ->
                                        new SaleItemJpaEntity(
                                                item.getId(),
                                                item.getProductId(),
                                                item.getDescription(),
                                                item.getUnitPrice(),
                                                item.getQuantity(),
                                                item.getSubtotal()))
                        .toList();
        SaleJpaEntity entity =
                new SaleJpaEntity(
                        sale.getId(),
                        sale.getBuyerDocumentType(),
                        sale.getBuyerDocumentNumber(),
                        sale.getStatus().name(),
                        sale.getTotal(),
                        sale.getCreatedAt(),
                        items);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Sale> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Sale> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    private Sale toDomain(SaleJpaEntity entity) {
        List<SaleItem> items =
                entity.getItems().stream()
                        .map(
                                item ->
                                        SaleItem.reconstitute(
                                                item.getId(),
                                                item.getProductId(),
                                                item.getDescription(),
                                                item.getUnitPrice(),
                                                item.getQuantity()))
                        .toList();
        return Sale.reconstitute(
                entity.getId(),
                items,
                entity.getBuyerDocumentType(),
                entity.getBuyerDocumentNumber(),
                SaleStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt());
    }
}
