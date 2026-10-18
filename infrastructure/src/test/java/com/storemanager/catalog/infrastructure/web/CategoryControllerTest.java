package com.storemanager.catalog.infrastructure.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.usecase.CreateCategoryUseCase;
import com.storemanager.catalog.usecase.DuplicateCategoryNameException;
import com.storemanager.catalog.usecase.ListCategoriesUseCase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CreateCategoryUseCase createCategoryUseCase;
    @MockitoBean private ListCategoriesUseCase listCategoriesUseCase;

    @Test
    void createsACategoryAndReturns201() throws Exception {
        Instant now = Instant.now();
        when(createCategoryUseCase.execute("Celulares", null))
                .thenReturn(Category.reconstitute(1L, "Celulares", null, now, now));

        mockMvc.perform(
                        post("/api/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateCategoryRequest("Celulares", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Celulares"));
    }

    @Test
    void rejectsABlankNameWith400() throws Exception {
        mockMvc.perform(
                        post("/api/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateCategoryRequest("  ", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOverLengthNameWith400() throws Exception {
        String tooLongName = "a".repeat(121);

        mockMvc.perform(
                        post("/api/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateCategoryRequest(tooLongName, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOverLengthDescriptionWith400() throws Exception {
        String tooLongDescription = "a".repeat(501);

        mockMvc.perform(
                        post("/api/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreateCategoryRequest("Celulares", tooLongDescription))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsADuplicateNameWith409() throws Exception {
        when(createCategoryUseCase.execute(anyString(), any()))
                .thenThrow(new DuplicateCategoryNameException("Celulares"));

        mockMvc.perform(
                        post("/api/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateCategoryRequest("Celulares", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void listsCategories() throws Exception {
        Instant now = Instant.now();
        when(listCategoriesUseCase.execute())
                .thenReturn(List.of(Category.reconstitute(1L, "Celulares", null, now, now)));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Celulares"));
    }
}
