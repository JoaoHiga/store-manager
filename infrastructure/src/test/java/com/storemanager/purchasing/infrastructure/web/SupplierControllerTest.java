package com.storemanager.purchasing.infrastructure.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.usecase.CreateSupplierUseCase;
import com.storemanager.purchasing.usecase.DeactivateSupplierUseCase;
import com.storemanager.purchasing.usecase.DuplicateSupplierRucException;
import com.storemanager.purchasing.usecase.ListSuppliersUseCase;
import com.storemanager.purchasing.usecase.ReactivateSupplierUseCase;
import com.storemanager.purchasing.usecase.SupplierNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(SupplierController.class)
class SupplierControllerTest {

    private static final String RUC = "20131312955";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CreateSupplierUseCase createSupplierUseCase;
    @MockitoBean private ListSuppliersUseCase listSuppliersUseCase;
    @MockitoBean private DeactivateSupplierUseCase deactivateSupplierUseCase;
    @MockitoBean private ReactivateSupplierUseCase reactivateSupplierUseCase;

    private static Supplier sampleSupplier(boolean active) {
        Instant now = Instant.now();
        return Supplier.reconstitute(1L, "Distribuidora Lima", RUC, "987654321", null, null, active, now, now);
    }

    private String json(CreateSupplierRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void createsASupplierAndReturns201() throws Exception {
        when(createSupplierUseCase.execute("Distribuidora Lima", RUC, "987654321", null, null))
                .thenReturn(sampleSupplier(true));

        mockMvc.perform(
                        post("/api/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new CreateSupplierRequest("Distribuidora Lima", RUC, "987654321", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ruc").value(RUC))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsABlankNameWith400() throws Exception {
        mockMvc.perform(
                        post("/api/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new CreateSupplierRequest("  ", RUC, null, null, null))))
                .andExpect(status().isBadRequest());
        verify(createSupplierUseCase, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsARucThatIsNotElevenDigitsWith400() throws Exception {
        mockMvc.perform(
                        post("/api/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new CreateSupplierRequest("Distribuidora Lima", "2013131295", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAMalformedEmailWith400() throws Exception {
        mockMvc.perform(
                        post("/api/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new CreateSupplierRequest("Distribuidora Lima", RUC, null, "not-an-email", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsAnInvalidRucCheckDigitTo400() throws Exception {
        when(createSupplierUseCase.execute(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid RUC check digit"));

        mockMvc.perform(
                        post("/api/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new CreateSupplierRequest("Distribuidora Lima", "20131312956", null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid RUC check digit"));
    }

    @Test
    void rejectsADuplicateRucWith409() throws Exception {
        when(createSupplierUseCase.execute(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new DuplicateSupplierRucException(RUC));

        mockMvc.perform(
                        post("/api/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new CreateSupplierRequest("Distribuidora Lima", RUC, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A supplier with RUC " + RUC + " already exists"));
    }

    @Test
    void listsAllSuppliersWhenNoActiveFilterIsGiven() throws Exception {
        when(listSuppliersUseCase.execute(isNull())).thenReturn(List.of(sampleSupplier(true), sampleSupplier(false)));

        mockMvc.perform(get("/api/suppliers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Distribuidora Lima"));
    }

    @Test
    void filtersSuppliersByActiveState() throws Exception {
        when(listSuppliersUseCase.execute(false)).thenReturn(List.of(sampleSupplier(false)));

        mockMvc.perform(get("/api/suppliers").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void deactivatesASupplierWith204() throws Exception {
        mockMvc.perform(patch("/api/suppliers/1/deactivate")).andExpect(status().isNoContent());
        verify(deactivateSupplierUseCase).execute(1L);
    }

    @Test
    void reactivatesASupplierWith204() throws Exception {
        mockMvc.perform(patch("/api/suppliers/1/activate")).andExpect(status().isNoContent());
        verify(reactivateSupplierUseCase).execute(1L);
    }

    @Test
    void returns404ForAnUnknownSupplier() throws Exception {
        doThrow(new SupplierNotFoundException(99L)).when(deactivateSupplierUseCase).execute(99L);

        mockMvc.perform(patch("/api/suppliers/99/deactivate")).andExpect(status().isNotFound());
    }
}
