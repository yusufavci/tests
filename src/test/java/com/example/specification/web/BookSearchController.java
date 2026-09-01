package com.example.specification.web;

import com.example.specification.domain.Book;
import com.example.specification.domain.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo controller showing the intended wiring: the controller only binds the
 * generic {@link QueryRequest} DTO and hands the derived Specification and
 * Pageable to the repository.
 */
@RestController
@RequestMapping("/books")
class BookSearchController {

    private final BookRepository repository;

    BookSearchController(BookRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/search")
    Page<Book> search(@ModelAttribute QueryRequest query) {
        return repository.findAll(query.<Book>toSpecification(), query.toPageable());
    }
}
