package com.storemanager.invoicing.infrastructure.scheduling;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory {@link VoucherRepositoryPort} test double — own copy for this module's test scope; see {@code FakeInvoicingProvider} in this package for why. */
class InMemoryVoucherRepository implements VoucherRepositoryPort {

    private final List<Voucher> vouchers = new ArrayList<>();
    private long nextId = 1;

    @Override
    public Voucher save(Voucher voucher) {
        if (voucher.getId() != null) {
            vouchers.removeIf(v -> v.getId().equals(voucher.getId()));
            vouchers.add(voucher);
            return voucher;
        }
        Voucher persisted =
                Voucher.reconstitute(
                        nextId++,
                        voucher.getSaleId(),
                        voucher.getType(),
                        voucher.getStatus(),
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
        vouchers.add(persisted);
        return persisted;
    }

    @Override
    public Optional<Voucher> findById(Long id) {
        return vouchers.stream().filter(v -> v.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<Voucher> findBySaleId(Long saleId) {
        return vouchers.stream().filter(v -> v.getSaleId().equals(saleId)).findFirst();
    }

    @Override
    public List<Voucher> findAllPending() {
        return vouchers.stream().filter(v -> v.getStatus() == VoucherStatus.PENDING).toList();
    }
}
