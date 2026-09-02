package com.example.specification.web;

import com.example.specification.domain.AuthorRepository;
import com.example.specification.domain.BookRepository;
import com.example.specification.domain.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @BeforeEach
    void seed() {
        TestData.seed(authorRepository, bookRepository);
    }

    @Test
    void filtersWithEqAndIsnotNull() throws Exception {
        mockMvc.perform(get("/books/search")
                        .param("filter", "title eq 'The Hobbit' and author isnot null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("The Hobbit"))
                .andExpect(jsonPath("$.content[0].author.name").value("J.R.R. Tolkien"));
    }

    @Test
    void csSuffixSwitchesToExactCaseMatching() throws Exception {
        mockMvc.perform(get("/books/search")
                        .param("filter", "title eqcs 'the hobbit'"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/books/search")
                        .param("filter", "title eqcs 'The Hobbit'"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("The Hobbit"));
    }

    @Test
    void textEqualityIsCaseInsensitive() throws Exception {
        mockMvc.perform(get("/books/search")
                        .param("filter", "title eq 'the hobbit' or authorName eq 'JOSHUA BLOCH'")
                        .param("orderBy", "title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Effective Java"))
                .andExpect(jsonPath("$.content[1].title").value("The Hobbit"));
    }

    @Test
    void appliesPrecedencePagingAndOrdering() throws Exception {
        // FICTION (Hobbit 310, LOTR 1178) or (SCIENCE and pages > 600) (TAOCP 650)
        mockMvc.perform(get("/books/search")
                        .param("filter", "genre eq FICTION or genre eq SCIENCE and pages gt 600")
                        .param("orderBy", "pages desc")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].title").value("The Lord of the Rings"))
                .andExpect(jsonPath("$.content[1].title").value("The Art of Computer Programming"));

        mockMvc.perform(get("/books/search")
                        .param("filter", "genre eq FICTION or genre eq SCIENCE and pages gt 600")
                        .param("orderBy", "pages desc")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("The Hobbit"));
    }

    @Test
    void filtersAcrossAssociations() throws Exception {
        mockMvc.perform(get("/books/search")
                        .param("filter", "author.name like 'TOLKIEN'")
                        .param("orderBy", "publishedDate asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("The Hobbit"))
                .andExpect(jsonPath("$.content[1].title").value("The Lord of the Rings"));
    }

    @Test
    void supportsBetweenInAndNegatedOperators() throws Exception {
        mockMvc.perform(get("/books/search")
                        .param("filter", "publishedDate between '1930-01-01' and '1960-12-31'"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/books/search")
                        .param("filter", "genre in (FICTION, HISTORY)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        // Negation via the negated operators: neither FICTION nor over 100 in price
        mockMvc.perform(get("/books/search")
                        .param("filter", "genre ne FICTION and price le 100")
                        .param("orderBy", "title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Anonymous Chronicle"))
                .andExpect(jsonPath("$.content[1].title").value("Effective Java"));
    }

    @Test
    void mapsClientFieldNamesToEntityPaths() throws Exception {
        // authorName -> author.name, authorCountry -> author.country
        mockMvc.perform(get("/books/search")
                        .param("filter", "authorName like 'tolkien' and authorCountry eq 'UK'")
                        .param("orderBy", "authorName asc, pages desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("The Lord of the Rings"))
                .andExpect(jsonPath("$.content[1].title").value("The Hobbit"));

        // Unmapped fields (title, and even direct entity paths) pass through unchanged.
        mockMvc.perform(get("/books/search")
                        .param("filter", "authorCountry eq 'USA' and title like 'java'"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Effective Java"));
    }

    @Test
    void noFilterReturnsEverythingPaged() throws Exception {
        mockMvc.perform(get("/books/search").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void syntaxErrorReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/books/search")
                        .param("filter", "title eq"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid query expression"))
                .andExpect(jsonPath("$.detail", containsString("end of expression")));
    }

    @Test
    void semanticErrorReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/books/search")
                        .param("filter", "genre eq POETRY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid query"))
                .andExpect(jsonPath("$.detail", containsString("genre")));
    }
}
