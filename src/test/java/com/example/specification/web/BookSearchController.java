package com.example.specification.web;

import com.example.specification.domain.Book;
import com.example.specification.domain.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Demo controller showing the intended wiring: the controller only binds the
 * generic {@link QueryRequest} DTO, registers its endpoint-specific field
 * mappings, and hands the derived Specification and Pageable to the
 * repository.
 */
@RestController
@RequestMapping("/books")
class BookSearchController {

    /** Client-facing field names translated to entity paths. */
    private static final Map<String, String> FIELD_MAPPINGS = Map.of(
            "authorName", "author.name",
            "authorCountry", "author.country");

    private final BookRepository repository;

    BookSearchController(BookRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/search")
    Page<Book> search(@ModelAttribute QueryRequest query) {
        query.withFieldMappings(FIELD_MAPPINGS);
        return repository.findAll(query.<Book>toSpecification(), query.toPageable());
    }
}
