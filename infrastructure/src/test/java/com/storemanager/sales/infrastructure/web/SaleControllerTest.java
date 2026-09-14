package com.storemanager.sales.infrastructure.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.usecase.VoidRejectedException;
import com.storemanager.invoicing.usecase.VoucherNotFoundException;
import com.storemanager.invoicing.usecase.VoucherNotVoidableException;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.SaleStatus;
import com.storemanager.sales.usecase.CancelSaleUseCase;
import com.storemanager.sales.usecase.GetSaleUseCase;
import com.storemanager.sales.usecase.InactiveProductException;
import com.storemanager.sales.usecase.InsufficientStockException;
import com.storemanager.sales.usecase.ListSalesUseCase;
import com.storemanager.sales.usecase.ProductNotFoundException;
import com.storemanager.sales.usecase.RecordSaleUseCase;
import com.storemanager.sales.usecase.RetrySaleVoucherUseCase;
import com.storemanager.sales.usecase.SaleNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SaleController.class)
class SaleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RecordSaleUseCase recordSaleUseCase;
    @MockBean private ListSalesUseCase listSalesUseCase;
    @MockBean private GetSaleUseCase getSaleUseCase;
    @MockBean private RetrySaleVoucherUseCase retrySaleVoucherUseCase;
    @MockBean private CancelSaleUseCase cancelSaleUseCase;

    private static Voucher sampleVoucher(long id, Long saleId, VoucherStatus status) {
        return Voucher.reconstitute(
                id, saleId, VoucherType.BOLETA, status, "DNI", "12345678", 1, null, "REF-1", "pdf", "xml", "cdr",
                "hash", Instant.now(), Instant.now());
    }

    private static Sale sampleSale(long id, BigDecimal unitPrice) {
        SaleItem item = SaleItem.reconstitute(1L, 1L, "iPhone 12", unitPrice, 1);
        return Sale.reconstitute(id, List.of(item), "DNI", "12345678", com.storemanager.sales.domain.SaleStatus.COMPLETED, Instant.now());
    }

    @Test
    void recordsAValidSaleAndReturns201() throws Exception {
        when(recordSaleUseCase.execute(anyList(), eq("DNI"), eq("12345678")))
                .thenReturn(sampleSale(1L, new BigDecimal("350")));

        String requestJson =
                "{\"items\":[{\"productId\":1,\"quantity\":1}],\"buyerDocumentType\":\"DNI\",\"buyerDocumentNumber\":\"12345678\"}";

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.total").value(350))
                .andExpect(jsonPath("$.voucherStatus").doesNotExist());
    }

    @Test
    void rejectsASaleWithNoLineItemsWith400() throws Exception {
        String requestJson = "{\"items\":[],\"buyerDocumentType\":\"DNI\",\"buyerDocumentNumber\":\"12345678\"}";

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsASaleWithNoBuyerReferenceWith400() throws Exception {
        String requestJson = "{\"items\":[{\"productId\":1,\"quantity\":1}]}";

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsASaleReferencingANonexistentProductWith400() throws Exception {
        when(recordSaleUseCase.execute(anyList(), anyString(), anyString()))
                .thenThrow(new ProductNotFoundException(999L));

        String requestJson =
                "{\"items\":[{\"productId\":999,\"quantity\":1}],\"buyerDocumentType\":\"DNI\",\"buyerDocumentNumber\":\"12345678\"}";

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsASaleReferencingAnInactiveProductWith400() throws Exception {
        when(recordSaleUseCase.execute(anyList(), anyString(), anyString()))
                .thenThrow(new InactiveProductException(1L));

        String requestJson =
                "{\"items\":[{\"productId\":1,\"quantity\":1}],\"buyerDocumentType\":\"DNI\",\"buyerDocumentNumber\":\"12345678\"}";

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsASaleExceedingAvailableStockWith400() throws Exception {
        when(recordSaleUseCase.execute(anyList(), anyString(), anyString()))
                .thenThrow(new InsufficientStockException(1L, 3, 2));

        String requestJson =
                "{\"items\":[{\"productId\":1,\"quantity\":3}],\"buyerDocumentType\":\"DNI\",\"buyerDocumentNumber\":\"12345678\"}";

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPastSalesLineItemPriceIsUnaffectedByALaterCatalogPriceChange() throws Exception {
        // The Sale returned by the (mocked) use case already carries its snapshot price; the
        // controller/DTO layer must surface exactly that stored value, never re-derive one.
        when(recordSaleUseCase.execute(anyList(), eq("DNI"), eq("12345678")))
                .thenReturn(sampleSale(1L, new BigDecimal("350")));

        String requestJson =
                "{\"items\":[{\"productId\":1,\"quantity\":1}],\"buyerDocumentType\":\"DNI\",\"buyerDocumentNumber\":\"12345678\"}";

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(jsonPath("$.items[0].unitPrice").value(350));

        when(getSaleUseCase.execute(1L)).thenReturn(sampleSale(1L, new BigDecimal("350")));

        // Even though the product's *current* price is now 400 in the catalog, the persisted
        // sale (as returned by the use case) still reports 350 — this test only proves the
        // controller doesn't override it; the real snapshot logic is unit-tested in
        // RecordSaleUseCaseTest.
        mockMvc.perform(get("/api/sales/1")).andExpect(jsonPath("$.items[0].unitPrice").value(350));
    }

    @Test
    void listsAllSales() throws Exception {
        when(listSalesUseCase.execute()).thenReturn(List.of(sampleSale(1L, new BigDecimal("350"))));

        mockMvc.perform(get("/api/sales")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void viewsASalesDetailWithVoucherStatusAbsentForNow() throws Exception {
        when(getSaleUseCase.execute(1L)).thenReturn(sampleSale(1L, new BigDecimal("350")));

        mockMvc.perform(get("/api/sales/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyerDocumentType").value("DNI"))
                .andExpect(jsonPath("$.voucherStatus").doesNotExist());
    }

    @Test
    void returns404WhenViewingANonexistentSale() throws Exception {
        when(getSaleUseCase.execute(999L)).thenThrow(new SaleNotFoundException(999L));

        mockMvc.perform(get("/api/sales/999")).andExpect(status().isNotFound());
    }

    @Test
    void retryingAVoucherReturnsItsUpdatedStatus() throws Exception {
        when(retrySaleVoucherUseCase.execute(1L)).thenReturn(sampleVoucher(10L, 1L, VoucherStatus.ACCEPTED));

        mockMvc.perform(post("/api/sales/1/retry-voucher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.saleId").value(1))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void retryingAVoucherForASaleWithNoVoucherReturns404() throws Exception {
        when(retrySaleVoucherUseCase.execute(999L)).thenThrow(VoucherNotFoundException.forSale(999L));

        mockMvc.perform(post("/api/sales/999/retry-voucher")).andExpect(status().isNotFound());
    }

    @Test
    void voidingASaleReturnsItCancelled() throws Exception {
        SaleItem item = SaleItem.reconstitute(1L, 1L, "iPhone 12", new BigDecimal("350"), 1);
        Sale cancelledSale = Sale.reconstitute(1L, List.of(item), "DNI", "12345678", SaleStatus.CANCELLED, Instant.now());
        when(cancelSaleUseCase.execute(1L, "Cliente solicito anulacion")).thenReturn(cancelledSale);

        String requestJson = "{\"reason\":\"Cliente solicito anulacion\"}";

        mockMvc.perform(post("/api/sales/1/void").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void voidingASaleWithNoReasonIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/sales/1/void").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void voidingASaleWhoseVoucherIsNotAcceptedReturns409() throws Exception {
        when(cancelSaleUseCase.execute(any(), any()))
                .thenThrow(new VoucherNotVoidableException(VoucherStatus.PENDING));

        String requestJson = "{\"reason\":\"reason\"}";

        mockMvc.perform(post("/api/sales/1/void").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isConflict());
    }

    @Test
    void voidingASaleTheOseDoesNotConfirmReturns502() throws Exception {
        when(cancelSaleUseCase.execute(any(), any())).thenThrow(new VoidRejectedException("SUNAT no acepto la baja"));

        String requestJson = "{\"reason\":\"reason\"}";

        mockMvc.perform(post("/api/sales/1/void").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadGateway());
    }
}
