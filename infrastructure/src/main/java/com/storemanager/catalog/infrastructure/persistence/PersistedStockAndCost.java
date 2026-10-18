package com.storemanager.catalog.infrastructure.persistence;

import java.math.BigDecimal;

/** A product's stock and cost price as stored in its row (see {@link ProductJpaRepository#findPersistedStockAndCost}). */
public record PersistedStockAndCost(int stockQuantity, BigDecimal costPrice) {}
