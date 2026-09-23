package com.frunoyman.webdriverscope.pages;

import com.frunoyman.webdriverscope.elements.Table;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Page object for https://the-internet.herokuapp.com/tables — a sortable
 * table, used to exercise {@link Table}. The same {@code <table>} is also
 * declared as a plain {@link WebElement}: both kinds of field work side
 * by side with the same, unmodified {@code @FindBy}.
 */
@Component
public class TablesPage extends BasePage {

    @Value("${the-internet.url}")
    private String theInternetUrl;

    @FindBy(id = "table1")
    private Table table;

    @FindBy(id = "table1")
    private WebElement rawTable;

    @Override
    public TablesPage open() {
        driver.get(theInternetUrl + "tables");
        return this;
    }

    public Table getTable() {
        return table;
    }

    public WebElement getRawTable() {
        return rawTable;
    }
}
