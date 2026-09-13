package com.storemanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void createsANewCategoryWithAName() {
        Category category = Category.createNew("Celulares", "Telefonos moviles", Instant.now());

        assertThat(category.getId()).isNull();
        assertThat(category.getName()).isEqualTo("Celulares");
        assertThat(category.getDescription()).isEqualTo("Telefonos moviles");
    }

    @Test
    void allowsAnOptionalNullDescription() {
        Category category = Category.createNew("Accesorios", null, Instant.now());

        assertThat(category.getDescription()).isNull();
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> Category.createNew("   ", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullName() {
        assertThatThrownBy(() -> Category.createNew(null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
