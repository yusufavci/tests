package com.example.specification;

import com.example.specification.domain.Author;
import com.example.specification.domain.AuthorRepository;
import com.example.specification.domain.Book;
import com.example.specification.domain.BookRepository;
import com.example.specification.domain.Genre;
import com.example.specification.domain.TestData;
import com.example.specification.query.FilterExpressionParser;
import com.example.specification.web.QueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class GenericSpecificationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @BeforeEach
    void seed() {
        TestData.seed(authorRepository, bookRepository);
    }

    @Test
    void equalsOnEnumFromStringValue() {
        Specification<Book> spec = GenericSpecification.of(
                FilterCriteria.of("genre", SearchOperator.EQUALS, "FICTION"));

        assertThat(bookRepository.findAll(spec))
                .extracting(Book::getTitle)
                .containsExactlyInAnyOrder("The Hobbit", "The Lord of the Rings");
    }

    @Test
    void nestedAndOrGroups() {
        // genre = SCIENCE AND (title LIKE 'java' OR pages > 600)
        Specification<Book> spec = SpecificationBuilder.<Book>and()
                .eq("genre", Genre.SCIENCE)
                .or(g -> g.like("title", "java").gt("pages", 600))
                .build();

        assertThat(bookRepository.findAll(spec))
                .extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "The Art of Computer Programming");
    }

    @Test
    void deeplyNestedGroups() {
        // FICTION OR (SCIENCE AND (price < 50 OR pages > 600))
        Specification<Book> spec = SpecificationBuilder.<Book>or()
                .eq("genre", Genre.FICTION)
                .and(g -> g.eq("genre", Genre.SCIENCE)
                        .or(inner -> inner.lt("price", 50).gt("pages", 600)))
                .build();

        assertThat(bookRepository.findAll(spec))
                .extracting(Book::getTitle)
                .containsExactlyInAnyOrder("The Hobbit", "The Lord of the Rings",
                        "Effective Java", "The Art of Computer Programming");
    }

    @Test
    void nestedPathJoinsAssociation() {
        Specification<Book> spec = SpecificationBuilder.<Book>and()
                .eq("author.country", "UK")
                .build();

        assertThat(bookRepository.findAll(spec))
                .extracting(Book::getTitle)
                .containsExactlyInAnyOrder("The Hobbit", "The Lord of the Rings");
    }

    @Test
    void collectionJoinReturnsDistinctRoots() {
        // Tolkien has two FICTION books; without distinct he would appear twice.
        Specification<Author> spec = SpecificationBuilder.<Author>and()
                .eq("books.genre", Genre.FICTION)
                .build();

        assertThat(authorRepository.findAll(spec))
                .extracting(Author::getName)
                .containsExactly("J.R.R. Tolkien");
    }

    @Test
    void likeIsCaseInsensitive() {
        Specification<Book> spec = SpecificationBuilder.<Book>and()
                .like("title", "hOBBit")
                .build();

        assertThat(bookRepository.findAll(spec))
                .extracting(Book::getTitle)
                .containsExactly("The Hobbit");
    }

    @Test
    void textEqualityAndMembershipAreCaseInsensitive() {
        Specification<Book> eq = SpecificationBuilder.<Book>and()
                .eq("title", "the HOBBIT")
                .build();
        assertThat(bookRepository.findAll(eq))
                .extracting(Book::getTitle)
                .containsExactly("The Hobbit");

        Specification<Book> notEq = SpecificationBuilder.<Book>and()
                .eq("genre", Genre.FICTION)
                .notEq("title", "the hobbit")
                .build();
        assertThat(bookRepository.findAll(notEq))
                .extracting(Book::getTitle)
                .containsExactly("The Lord of the Rings");

        Specification<Book> in = SpecificationBuilder.<Book>and()
                .in("author.name", "DONALD KNUTH", "joshua bloch")
                .build();
        assertThat(bookRepository.findAll(in))
                .extracting(Book::getTitle)
                .containsExactlyInAnyOrder("The Art of Computer Programming", "Effective Java");
    }

    @Test
    void inAndBetweenAndNullChecks() {
        Specification<Book> in = SpecificationBuilder.<Book>and()
                .in("genre", "SCIENCE", "HISTORY")
                .build();
        assertThat(bookRepository.findAll(in)).hasSize(3);

        Specification<Book> between = SpecificationBuilder.<Book>and()
                .between("publishedDate", "1930-01-01", "1960-12-31")
                .build();
        assertThat(bookRepository.findAll(between))
                .extracting(Book::getTitle)
                .containsExactlyInAnyOrder("The Hobbit", "The Lord of the Rings");

        Specification<Book> noAuthor = SpecificationBuilder.<Book>and()
                .isNull("author")
                .build();
        assertThat(bookRepository.findAll(noAuthor))
                .extracting(Book::getTitle)
                .containsExactly("Anonymous Chronicle");
    }

    @Test
    void stringValuesConvertToNumbersAndDates() {
        Specification<Book> spec = SpecificationBuilder.<Book>and()
                .gte("price", "40")
                .lt("publishedDate", "2000-01-01")
                .build();

        assertThat(bookRepository.findAll(spec))
                .extracting(Book::getTitle)
                .containsExactly("The Art of Computer Programming");
    }

    @Test
    void specificationIsSerializable() throws Exception {
        Specification<Book> spec = new GenericSpecification<>(FilterExpressionParser.parse(
                "title eq 'x' and (pages gt 1 or author isnot null)"));

        var bytes = new java.io.ByteArrayOutputStream();
        new java.io.ObjectOutputStream(bytes).writeObject(spec);
        Object restored = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bytes.toByteArray())).readObject();

        assertThat(restored).isInstanceOf(GenericSpecification.class);
    }

    @Test
    void emptyFilterMatchesEverything() {
        assertThat(bookRepository.findAll(new GenericSpecification<Book>(new FilterGroup())))
                .hasSize(5);
        assertThat(bookRepository.findAll(new GenericSpecification<Book>(null)))
                .hasSize(5);
    }

    @Test
    void queryRequestAppliesPagingAndOrdering() {
        QueryRequest request = new QueryRequest();
        request.setFilter("author isnot null");
        request.setOrderBy("pages desc");
        request.setPage(0);
        request.setSize(2);

        Page<Book> page = bookRepository.findAll(request.<Book>toSpecification(), request.toPageable());

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Book::getTitle)
                .containsExactly("The Lord of the Rings", "The Art of Computer Programming");

        request.setPage(1);
        Page<Book> second = bookRepository.findAll(request.<Book>toSpecification(), request.toPageable());
        assertThat(second.getContent())
                .extracting(Book::getTitle)
                .containsExactly("Effective Java", "The Hobbit");
    }

    @Test
    void caseSensitiveConditionsMatchExactCase() {
        Specification<Book> wrongCase = GenericSpecification.of(
                FilterCriteria.of("title", SearchOperator.EQUALS, "the hobbit").exactCase());
        assertThat(bookRepository.findAll(wrongCase)).isEmpty();

        Specification<Book> exactCase = GenericSpecification.of(
                FilterCriteria.of("title", SearchOperator.EQUALS, "The Hobbit").exactCase());
        assertThat(bookRepository.findAll(exactCase))
                .extracting(Book::getTitle)
                .containsExactly("The Hobbit");

        Specification<Book> likeWrongCase = GenericSpecification.of(
                FilterCriteria.of("title", SearchOperator.LIKE, "hobbit").exactCase());
        assertThat(bookRepository.findAll(likeWrongCase)).isEmpty();

        Specification<Book> inExactCase = GenericSpecification.of(
                FilterCriteria.of("author.name", SearchOperator.IN,
                        List.of((Object) "Donald Knuth", "joshua bloch")).exactCase());
        assertThat(bookRepository.findAll(inExactCase))
                .extracting(Book::getTitle)
                .containsExactly("The Art of Computer Programming");
    }

    @Test
    void queryRequestAppliesFieldMappings() {
        QueryRequest request = new QueryRequest()
                .withFieldMappings(java.util.Map.of("country", "author.country"));
        request.setFilter("country eq 'UK'");
        request.setOrderBy("country asc, pages asc");

        Page<Book> page = bookRepository.findAll(request.<Book>toSpecification(), request.toPageable());

        assertThat(page.getContent())
                .extracting(Book::getTitle)
                .containsExactly("The Hobbit", "The Lord of the Rings");
    }

    @Test
    void sortingOnNestedFieldWorks() {
        QueryRequest request = new QueryRequest();
        request.setFilter("genre eq SCIENCE");
        request.setOrderBy("author.name asc");

        Page<Book> page = bookRepository.findAll(request.<Book>toSpecification(), request.toPageable());

        assertThat(page.getContent())
                .extracting(b -> b.getAuthor().getName())
                .containsExactly("Donald Knuth", "Joshua Bloch");
    }

    @Test
    void invalidValuesFailFast() {
        // Spring translates our IllegalArgumentException at the repository boundary.
        Specification<Book> badEnum = GenericSpecification.of(
                FilterCriteria.of("genre", SearchOperator.EQUALS, "POETRY"));
        assertThatThrownBy(() -> bookRepository.findAll(badEnum))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("genre");

        Specification<Book> badBetween = GenericSpecification.of(
                FilterCriteria.of("pages", SearchOperator.BETWEEN, List.of((Object) 1)));
        assertThatThrownBy(() -> bookRepository.findAll(badBetween))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("BETWEEN");
    }
}
