package com.storemanager.catalog.infrastructure.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.usecase.CategoryNotFoundException;
import com.storemanager.catalog.usecase.CreateProductUseCase;
import com.storemanager.catalog.usecase.DeactivateProductUseCase;
import com.storemanager.catalog.usecase.ListProductsUseCase;
import com.storemanager.catalog.usecase.ProductNotFoundException;
import com.storemanager.catalog.usecase.UpdateProductUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CreateProductUseCase createProductUseCase;
    @MockBean private ListProductsUseCase listProductsUseCase;
    @MockBean private UpdateProductUseCase updateProductUseCase;
    @MockBean private DeactivateProductUseCase deactivateProductUseCase;

    private static Product sampleProduct(long id, boolean active) {
        Instant now = Instant.now();
        return Product.reconstitute(
                id, "PRD-%06d".formatted(id), "iPhone 12", null, 1L, null, null, new BigDecimal("350"), 3, active,
                now, now);
    }

    @Test
    void createsAProductAndReturns201() throws Exception {
        when(createProductUseCase.execute(
                        eq("iPhone 12"), isNull(), eq(1L), isNull(), isNull(), eq(new BigDecimal("350")), eq(3)))
                .thenReturn(sampleProduct(1L, true));

        mockMvc.perform(
                        post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreateProductRequest(
                                                        "iPhone 12", null, 1L, null, null, new BigDecimal("350"), 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("PRD-000001"));
    }

    @Test
    void rejectsANegativeStockQuantityWith400() throws Exception {
        String badJson =
                "{\"name\":\"iPhone 12\",\"categoryId\":1,\"salePrice\":350,\"stockQuantity\":-1}";

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(badJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANonexistentCategoryWith400() throws Exception {
        when(createProductUseCase.execute(
                        anyString(), any(), anyLong(), any(), any(), any(BigDecimal.class), anyInt()))
                .thenThrow(new CategoryNotFoundException(999L));

        mockMvc.perform(
                        post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreateProductRequest(
                                                        "iPhone 12", null, 999L, null, null, new BigDecimal("350"), 3))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsProducts() throws Exception {
        when(listProductsUseCase.execute(null)).thenReturn(List.of(sampleProduct(1L, true)));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void updatesAProduct() throws Exception {
        when(updateProductUseCase.execute(
                        eq(1L), eq("iPhone 12"), isNull(), eq(1L), isNull(), isNull(), eq(new BigDecimal("320")), eq(5)))
                .thenReturn(sampleProduct(1L, true));

        mockMvc.perform(
                        put("/api/products/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new UpdateProductRequest(
                                                        "iPhone 12", null, 1L, null, null, new BigDecimal("320"), 5))))
                .andExpect(status().isOk());
    }

    @Test
    void returns404WhenUpdatingANonexistentProduct() throws Exception {
        when(updateProductUseCase.execute(
                        eq(999L), anyString(), any(), anyLong(), any(), any(), any(BigDecimal.class), anyInt()))
                .thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(
                        put("/api/products/999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new UpdateProductRequest(
                                                        "iPhone 12", null, 1L, null, null, new BigDecimal("320"), 5))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatesAProductAndReturns204() throws Exception {
        mockMvc.perform(patch("/api/products/1/deactivate")).andExpect(status().isNoContent());
    }

    @Test
    void returns404WhenDeactivatingANonexistentProduct() throws Exception {
        doThrow(new ProductNotFoundException(999L)).when(deactivateProductUseCase).execute(999L);

        mockMvc.perform(patch("/api/products/999/deactivate")).andExpect(status().isNotFound());
    }
}
