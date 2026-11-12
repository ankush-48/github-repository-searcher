package com.ankush.reposearch.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ankush.reposearch.api.Responses.*;
import com.ankush.reposearch.error.*;
import com.ankush.reposearch.service.RepositoryService;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepositoryController.class)
@Import(RepositoryControllerTest.TestConfig.class)
class RepositoryControllerTest {
  @TestConfiguration
  static class TestConfig {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean RepositoryService service;

  @Test
  void acceptsValidSearch() throws Exception {
    when(service.search(any()))
        .thenReturn(
            new SearchResponse(
                "Repositories fetched and saved successfully", List.of(), 0, false, 1, 30));
    mvc.perform(
            post("/api/github/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"spring\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("repositories").isArray())
        .andExpect(jsonPath("page").value(1));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"query\":\" \"}",
        "{\"query\":\"spring\",\"sort\":\"oops\"}",
        "{\"query\":\"spring\",\"page\":0}",
        "{\"query\":\"spring\",\"perPage\":101}",
        "{\"query\":\"spring\",\"language\":\"Java:evil\"}",
        "{\"query\":\"spring\",\"unknown\":true}",
        "{\"query\":\"spring\",\"page\":\"oops\"}",
        "{\"query\":\"spring\",\"perPage\":1.5}",
        "{broken",
        "null",
        "{\"query\":\"repo;drop\"}"
      })
  void rejectsInvalidSearch(String body) throws Exception {
    mvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("code").exists());
    verifyNoInteractions(service);
  }

  @Test
  void returnsStoredResultsAndDefaults() throws Exception {
    when(service.find(null, null, "stars", 0, 20))
        .thenReturn(new StoredResponse(List.of(), 0, 0, 20, 0));
    mvc.perform(get("/api/github/repositories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(0));
    verify(service).find(null, null, "stars", 0, 20);
  }

  @Test
  void passesFiltersToService() throws Exception {
    mvc.perform(get("/api/github/repositories?language=Java&minStars=10&sort=forks&page=2&size=5"))
        .andExpect(status().isOk());
    verify(service).find("Java", 10L, "forks", 2, 5);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sort=oops",
        "minStars=-1",
        "page=-1",
        "size=0",
        "size=101",
        "page=abc",
        "minStars=abc",
        "page=1000001"
      })
  void rejectsInvalidFilters(String query) throws Exception {
    mvc.perform(get("/api/github/repositories?" + query)).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void propagatesSafeRateLimitError() throws Exception {
    when(service.search(any()))
        .thenThrow(
            new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_RATE_LIMITED", "Retry later", 60L));
    mvc.perform(
            post("/api/github/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"spring\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(jsonPath("code").value("GITHUB_RATE_LIMITED"))
        .andExpect(jsonPath("path").value("/api/github/search"));
  }

  @Test
  void databaseErrorsDoNotExposeDetails() throws Exception {
    when(service.find(any(), any(), anyString(), anyInt(), anyInt()))
        .thenThrow(new DataAccessResourceFailureException("secret password"));
    mvc.perform(get("/api/github/repositories"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("code").value("DATABASE_UNAVAILABLE"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  @Test
  void unexpectedErrorsDoNotExposeDetails() throws Exception {
    when(service.find(any(), any(), anyString(), anyInt(), anyInt()))
        .thenThrow(new IllegalStateException("secret"));
    mvc.perform(get("/api/github/repositories"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("code").value("INTERNAL_ERROR"));
  }

  @Test
  void unsupportedMethodReturns405() throws Exception {
    mvc.perform(delete("/api/github/repositories"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"));
  }

  @Test
  void unknownPathReturns404() throws Exception {
    mvc.perform(get("/api/unknown")).andExpect(status().isNotFound());
  }

  @Test
  void unsupportedContentTypeReturns415() throws Exception {
    mvc.perform(post("/api/github/search").contentType(MediaType.TEXT_PLAIN).content("text"))
        .andExpect(status().isUnsupportedMediaType());
  }
}
