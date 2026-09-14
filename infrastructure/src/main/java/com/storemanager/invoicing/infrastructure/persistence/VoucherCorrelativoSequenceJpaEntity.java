package com.storemanager.invoicing.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row per NubeFacT series, tracking the next correlativo to hand out. See {@code V5__create_voucher_correlativo_sequences.sql}. */
@Entity
@Table(name = "voucher_correlativo_sequences")
public class VoucherCorrelativoSequenceJpaEntity {

    @Id
    private String series;

    private long nextCorrelativo;

    protected VoucherCorrelativoSequenceJpaEntity() {
        // JPA
    }

    public VoucherCorrelativoSequenceJpaEntity(String series, long nextCorrelativo) {
        this.series = series;
        this.nextCorrelativo = nextCorrelativo;
    }

    public String getSeries() {
        return series;
    }

    public long getNextCorrelativo() {
        return nextCorrelativo;
    }

    public void setNextCorrelativo(long nextCorrelativo) {
        this.nextCorrelativo = nextCorrelativo;
    }
}
