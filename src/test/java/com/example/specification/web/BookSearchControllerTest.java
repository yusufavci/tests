package com.example.specification.web;

import com.example.specification.domain.Author;
import com.example.specification.domain.AuthorRepository;
import com.example.specification.domain.Book;
import com.example.specification.domain.BookRepository;
import com.example.specification.domain.Genre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        bookRepository.deleteAll();
        authorRepository.deleteAll();

        Author knuth = new Author("Donald Knuth", "USA");
        knuth.addBook(new Book("The Art of Computer Programming", 650,
                new BigDecimal("120.00"), LocalDate.of(1968, 1, 1), Genre.SCIENCE));

        Author bloch = new Author("Joshua Bloch", "USA");
        bloch.addBook(new Book("Effective Java", 412,
                new BigDecimal("45.50"), LocalDate.of(2018, 1, 6), Genre.SCIENCE));

        Author tolkien = new Author("J.R.R. Tolkien", "UK");
        tolkien.addBook(new Book("The Hobbit", 310,
                new BigDecimal("15.99"), LocalDate.of(1937, 9, 21), Genre.FICTION));
        tolkien.addBook(new Book("The Lord of the Rings", 1178,
                new BigDecimal("29.99"), LocalDate.of(1954, 7, 29), Genre.FICTION));

        authorRepository.saveAll(List.of(knuth, bloch, tolkien));

        bookRepository.save(new Book("Anonymous Chronicle", 200,
                new BigDecimal("9.99"), LocalDate.of(2020, 5, 1), Genre.HISTORY));
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
    void filtersAcrossAssociationsWithFunctions() throws Exception {
        mockMvc.perform(get("/books/search")
                        .param("filter", "contains(author.name, 'TOLKIEN')")
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
                        .param("filter", "contains(authorName, 'tolkien') and authorCountry eq 'UK'")
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
