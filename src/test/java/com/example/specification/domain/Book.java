package com.example.specification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private Integer pages;

    private BigDecimal price;

    private LocalDate publishedDate;

    @Enumerated(EnumType.STRING)
    private Genre genre;

    @ManyToOne
    private Author author;

    public Book() {
    }

    public Book(String title, Integer pages, BigDecimal price, LocalDate publishedDate, Genre genre) {
        this.title = title;
        this.pages = pages;
        this.price = price;
        this.publishedDate = publishedDate;
        this.genre = genre;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Integer getPages() {
        return pages;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDate getPublishedDate() {
        return publishedDate;
    }

    public Genre getGenre() {
        return genre;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }
}
