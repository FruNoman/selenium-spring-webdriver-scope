package com.frunoyman.webdriverscope.elements;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import java.util.List;

/** One {@code <tr>} of a {@link Table}. */
public class Row extends BaseElement {

    @FindBy(tagName = "td")
    private List<WebElement> cells;

    public Row(WebElement root) {
        super(root);
    }

    public List<String> getCellTexts() {
        return cells.stream().map(WebElement::getText).toList();
    }

    public String getCellText(int index) {
        return cells.get(index).getText();
    }
}
