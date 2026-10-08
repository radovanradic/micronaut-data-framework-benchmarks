package bench.spring.jdbc;

import bench.common.model.BookView;
import bench.common.model.Inputs.NewBook;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Table("book")
public class Book implements BookView {

    @Id
    private Long id;
    private Long authorId;
    private String title;
    private String isbn;
    private int pages;
    private BigDecimal price;
    private LocalDate publishedOn;

    static Book from(NewBook b) {
        Book book = new Book();
        book.setAuthorId(b.authorId());
        book.setTitle(b.title());
        book.setIsbn(b.isbn());
        book.setPages(b.pages());
        book.setPrice(b.price());
        book.setPublishedOn(b.publishedOn());
        return book;
    }

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    @Override
    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    @Override
    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public void setPublishedOn(LocalDate publishedOn) {
        this.publishedOn = publishedOn;
    }
}
