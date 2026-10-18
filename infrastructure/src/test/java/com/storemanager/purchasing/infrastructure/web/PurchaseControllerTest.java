package com.storemanager.purchasing.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.PurchaseItem;
import com.storemanager.purchasing.usecase.DuplicatePurchaseInvoiceException;
import com.storemanager.purchasing.usecase.GetPurchaseUseCase;
import com.storemanager.purchasing.usecase.InactiveProductException;
import com.storemanager.purchasing.usecase.InactiveSupplierException;
import com.storemanager.purchasing.usecase.ListPurchasesUseCase;
import com.storemanager.purchasing.usecase.ProductNotFoundException;
import com.storemanager.purchasing.usecase.PurchaseLineItemRequest;
import com.storemanager.purchasing.usecase.PurchaseNotFoundException;
import com.storemanager.purchasing.usecase.RecordPurchaseUseCase;
import com.storemanager.purchasing.usecase.SupplierNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PurchaseController.class)
class PurchaseControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RecordPurchaseUseCase recordPurchaseUseCase;
    @MockitoBean private ListPurchasesUseCase listPurchasesUseCase;
    @MockitoBean private GetPurchaseUseCase getPurchaseUseCase;

    private static Purchase samplePurchase() {
        return Purchase.reconstitute(
                5L, 1L, "Distribuidora Lima", "F001-123", DATE,
                List.of(
                        PurchaseItem.reconstitute(11L, 7L, "iPhone 12", 2, new BigDecimal("330.00")),
                        PurchaseItem.reconstitute(12L, 8L, "Funda", 10, new BigDecimal("4.50"))),
                Instant.parse("2026-10-08T15:00:00Z"));
    }

    private static final String VALID_BODY =
            """
            {"supplierId": 1, "invoiceNumber": "F001-123", "purchaseDate": "2026-10-08",
             "items": [{"productId": 7, "quantity": 2, "unitCost": 330.00},
                       {"productId": 8, "quantity": 10, "unitCost": 4.5}]}
            """;

    private org.springframework.test.web.servlet.ResultActions postPurchase(String body) throws Exception {
        return mockMvc.perform(post("/api/purchases").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void recordsAPurchaseAndReturns201WithItsDetail() throws Exception {
        when(recordPurchaseUseCase.execute(
                        1L, "F001-123", DATE,
                        List.of(
                                new PurchaseLineItemRequest(7L, 2, new BigDecimal("330.00")),
                                new PurchaseLineItemRequest(8L, 10, new BigDecimal("4.5")))))
                .thenReturn(samplePurchase());

        postPurchase(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.supplierId").value(1))
                .andExpect(jsonPath("$.supplierName").value("Distribuidora Lima"))
                .andExpect(jsonPath("$.invoiceNumber").value("F001-123"))
                .andExpect(jsonPath("$.purchaseDate").value("2026-10-08"))
                .andExpect(jsonPath("$.total").value(705.00))
                .andExpect(jsonPath("$.items[0].productName").value("iPhone 12"))
                .andExpect(jsonPath("$.items[0].unitCost").value(330.00))
                .andExpect(jsonPath("$.items[0].subtotal").value(660.00))
                .andExpect(jsonPath("$.items[1].quantity").value(10));
    }

    @Test
    void rejectsAMalformedRequestWith400BeforeCallingTheUseCase() throws Exception {
        postPurchase("{\"supplierId\": 1, \"invoiceNumber\": \" \", \"purchaseDate\": \"2026-10-08\","
                        + " \"items\": [{\"productId\": 7, \"quantity\": 1, \"unitCost\": 1}]}")
                .andExpect(status().isBadRequest());
        postPurchase("{\"supplierId\": 1, \"invoiceNumber\": \"F001-1\", \"purchaseDate\": \"2026-10-08\", \"items\": []}")
                .andExpect(status().isBadRequest());
        postPurchase("{\"supplierId\": 1, \"invoiceNumber\": \"F001-1\", \"purchaseDate\": \"2026-10-08\","
                        + " \"items\": [{\"productId\": 7, \"quantity\": 0, \"unitCost\": 1}]}")
                .andExpect(status().isBadRequest());
        postPurchase("{\"supplierId\": 1, \"invoiceNumber\": \"F001-1\", \"purchaseDate\": \"2026-10-08\","
                        + " \"items\": [{\"productId\": 7, \"quantity\": 1, \"unitCost\": -1}]}")
                .andExpect(status().isBadRequest());
        postPurchase("{\"supplierId\": 1, \"invoiceNumber\": \"F001-1\","
                        + " \"items\": [{\"productId\": 7, \"quantity\": 1, \"unitCost\": 1}]}")
                .andExpect(status().isBadRequest());
        postPurchase("{\"invoiceNumber\": \"F001-1\", \"purchaseDate\": \"2026-10-08\","
                        + " \"items\": [{\"productId\": 7, \"quantity\": 1, \"unitCost\": 1}]}")
                .andExpect(status().isBadRequest());
        verify(recordPurchaseUseCase, never()).execute(any(), any(), any(), any());
    }

    private static String bodyWithItem(String item) {
        return "{\"supplierId\": 1, \"invoiceNumber\": \"F001-1\", \"purchaseDate\": \"2026-10-08\", \"items\": ["
                + item + "]}";
    }

    @Test
    void rejectsQuantitiesAndUnitCostsBeyondTheirDigitLimitsWith400() throws Exception {
        // unitCost is @Digits(integer = 10, fraction = 2), like the NUMERIC(12, 2) column.
        postPurchase(bodyWithItem("{\"productId\": 7, \"quantity\": 1, \"unitCost\": 1.005}"))
                .andExpect(status().isBadRequest());
        postPurchase(bodyWithItem("{\"productId\": 7, \"quantity\": 1, \"unitCost\": 12345678901}"))
                .andExpect(status().isBadRequest());
        // quantity is a whole number of units that fits an INTEGER column.
        postPurchase(bodyWithItem("{\"productId\": 7, \"quantity\": 1.5, \"unitCost\": 1}"))
                .andExpect(status().isBadRequest());
        postPurchase(bodyWithItem("{\"productId\": 7, \"quantity\": 2147483648, \"unitCost\": 1}"))
                .andExpect(status().isBadRequest());
        verify(recordPurchaseUseCase, never()).execute(any(), any(), any(), any());
    }

    @Test
    void acceptsAUnitCostAtItsDigitLimits() throws Exception {
        when(recordPurchaseUseCase.execute(anyLong(), anyString(), any(), anyList())).thenReturn(samplePurchase());
        postPurchase(bodyWithItem("{\"productId\": 7, \"quantity\": 1, \"unitCost\": 9999999999.99}"))
                .andExpect(status().isCreated());
        verify(recordPurchaseUseCase)
                .execute(1L, "F001-1", DATE, List.of(new PurchaseLineItemRequest(7L, 1, new BigDecimal("9999999999.99"))));
    }

    @Test
    void rejectsANullItemWith400() throws Exception {
        postPurchase(bodyWithItem("null")).andExpect(status().isBadRequest());
        postPurchase(bodyWithItem("{\"productId\": 7, \"quantity\": 1, \"unitCost\": 1}, null"))
                .andExpect(status().isBadRequest());
        verify(recordPurchaseUseCase, never()).execute(any(), any(), any(), any());
    }

    @Test
    void checksTheInvoiceNumberLengthAfterTrimmingIt() throws Exception {
        String thirtyChars = "F001-" + "1".repeat(25);
        when(recordPurchaseUseCase.execute(anyLong(), anyString(), any(), anyList())).thenReturn(samplePurchase());
        postPurchase("{\"supplierId\": 1, \"invoiceNumber\": \"  " + thirtyChars + "  \", \"purchaseDate\": \"2026-10-08\","
                        + " \"items\": [{\"productId\": 7, \"quantity\": 1, \"unitCost\": 1}]}")
                .andExpect(status().isCreated());
        verify(recordPurchaseUseCase)
                .execute(1L, thirtyChars, DATE, List.of(new PurchaseLineItemRequest(7L, 1, new BigDecimal("1"))));

        postPurchase("{\"supplierId\": 1, \"invoiceNumber\": \"" + thirtyChars + "1\", \"purchaseDate\": \"2026-10-08\","
                        + " \"items\": [{\"productId\": 7, \"quantity\": 1, \"unitCost\": 1}]}")
                .andExpect(status().isBadRequest());
        postPurchase("{\"supplierId\": 1, \"invoiceNumber\": \" \\t \", \"purchaseDate\": \"2026-10-08\","
                        + " \"items\": [{\"productId\": 7, \"quantity\": 1, \"unitCost\": 1}]}")
                .andExpect(status().isBadRequest());
        verify(recordPurchaseUseCase, times(1)).execute(any(), any(), any(), any());
    }

    @Test
    void mapsAnUnknownSupplierOrProductTo404() throws Exception {
        when(recordPurchaseUseCase.execute(anyLong(), anyString(), any(), anyList()))
                .thenThrow(new SupplierNotFoundException(1L));
        postPurchase(VALID_BODY).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());

        doThrow(new ProductNotFoundException(7L))
                .when(recordPurchaseUseCase)
                .execute(anyLong(), anyString(), any(), anyList());
        postPurchase(VALID_BODY).andExpect(status().isNotFound());
    }

    @Test
    void mapsAnInactiveSupplierOrProductTo400() throws Exception {
        when(recordPurchaseUseCase.execute(anyLong(), anyString(), any(), anyList()))
                .thenThrow(new InactiveSupplierException(1L));
        postPurchase(VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Supplier with id 1 is not active"));

        doThrow(new InactiveProductException(7L))
                .when(recordPurchaseUseCase)
                .execute(anyLong(), anyString(), any(), anyList());
        postPurchase(VALID_BODY).andExpect(status().isBadRequest());
    }

    @Test
    void mapsADomainRuleViolationTo400() throws Exception {
        when(recordPurchaseUseCase.execute(anyLong(), anyString(), any(), anyList()))
                .thenThrow(new IllegalArgumentException("Purchase date must not be in the future"));

        postPurchase(VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Purchase date must not be in the future"));
    }

    @Test
    void mapsADuplicateSupplierInvoiceTo409() throws Exception {
        when(recordPurchaseUseCase.execute(anyLong(), anyString(), any(), anyList()))
                .thenThrow(new DuplicatePurchaseInvoiceException(1L, "F001-123"));

        postPurchase(VALID_BODY).andExpect(status().isConflict());
    }

    @Test
    void listsPurchaseSummariesWithTheirLineCount() throws Exception {
        when(listPurchasesUseCase.execute()).thenReturn(List.of(samplePurchase()));

        mockMvc.perform(get("/api/purchases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].supplierId").value(1))
                .andExpect(jsonPath("$[0].supplierName").value("Distribuidora Lima"))
                .andExpect(jsonPath("$[0].invoiceNumber").value("F001-123"))
                .andExpect(jsonPath("$[0].purchaseDate").value("2026-10-08"))
                .andExpect(jsonPath("$[0].total").value(705.00))
                .andExpect(jsonPath("$[0].lineCount").value(2))
                .andExpect(jsonPath("$[0].items").doesNotExist());
    }

    @Test
    void getsAPurchaseDetailOr404() throws Exception {
        when(getPurchaseUseCase.execute(5L)).thenReturn(samplePurchase());
        when(getPurchaseUseCase.execute(eq(99L))).thenThrow(new PurchaseNotFoundException(99L));

        mockMvc.perform(get("/api/purchases/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].productName").value("Funda"));
        mockMvc.perform(get("/api/purchases/99")).andExpect(status().isNotFound());
    }
}
