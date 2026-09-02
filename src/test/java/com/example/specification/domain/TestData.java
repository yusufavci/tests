package com.example.specification.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Shared seed data for the specification tests. */
public final class TestData {

    private TestData() {
    }

    public static void seed(AuthorRepository authorRepository, BookRepository bookRepository) {
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

        // A book without an author, for null checks.
        bookRepository.save(new Book("Anonymous Chronicle", 200,
                new BigDecimal("9.99"), LocalDate.of(2020, 5, 1), Genre.HISTORY));
    }
}
