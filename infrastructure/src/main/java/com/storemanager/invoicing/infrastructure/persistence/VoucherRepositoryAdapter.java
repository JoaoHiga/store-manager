package com.storemanager.invoicing.infrastructure.persistence;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class VoucherRepositoryAdapter implements VoucherRepositoryPort {

    private final VoucherJpaRepository jpaRepository;

    public VoucherRepositoryAdapter(VoucherJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Voucher save(Voucher voucher) {
        VoucherJpaEntity entity =
                new VoucherJpaEntity(
                        voucher.getId(),
                        voucher.getSaleId(),
                        voucher.getType().name(),
                        voucher.getStatus().name(),
                        voucher.getBuyerDocumentType(),
                        voucher.getBuyerDocumentNumber(),
                        voucher.getAttemptCount(),
                        voucher.getRejectionReason(),
                        voucher.getExternalReference(),
                        voucher.getPdfLink(),
                        voucher.getXmlLink(),
                        voucher.getCdrLink(),
                        voucher.getHash(),
                        voucher.getCreatedAt(),
                        voucher.getUpdatedAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Voucher> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Voucher> findBySaleId(Long saleId) {
        return jpaRepository.findBySaleId(saleId).map(this::toDomain);
    }

    @Override
    public List<Voucher> findAllPending() {
        return jpaRepository.findByStatus(VoucherStatus.PENDING.name()).stream().map(this::toDomain).toList();
    }

    private Voucher toDomain(VoucherJpaEntity entity) {
        return Voucher.reconstitute(
                entity.getId(),
                entity.getSaleId(),
                VoucherType.valueOf(entity.getType()),
                VoucherStatus.valueOf(entity.getStatus()),
                entity.getBuyerDocumentType(),
                entity.getBuyerDocumentNumber(),
                entity.getAttemptCount(),
                entity.getRejectionReason(),
                entity.getExternalReference(),
                entity.getPdfLink(),
                entity.getXmlLink(),
                entity.getCdrLink(),
                entity.getHash(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
