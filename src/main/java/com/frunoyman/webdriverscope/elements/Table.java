package com.frunoyman.webdriverscope.elements;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import java.util.List;

/**
 * A {@code <table>} with a {@code <thead>} header row and {@code <tbody>}
 * data rows. Declared on a page like any other element:
 * {@code @FindBy(id = "table1") private Table table;}
 */
public class Table extends BaseElement {

    @FindBy(css = "thead th")
    private List<WebElement> headers;

    @FindBy(css = "tbody tr")
    private List<Row> rows;

    public Table(WebElement root) {
        super(root);
    }

    public List<String> getHeaders() {
        return headers.stream().map(WebElement::getText).toList();
    }

    public List<Row> getRows() {
        return rows;
    }

    public Row getRow(int index) {
        return rows.get(index);
    }

    public List<String> getColumn(String header) {
        int index = columnIndex(header);
        return rows.stream().map(row -> row.getCellText(index)).toList();
    }

    public Table sortBy(String header) {
        headers.get(columnIndex(header)).click();
        return this;
    }

    private int columnIndex(String header) {
        int index = getHeaders().indexOf(header);
        if (index < 0) {
            throw new IllegalArgumentException("No column '" + header + "' in " + getHeaders());
        }
        return index;
    }
}
