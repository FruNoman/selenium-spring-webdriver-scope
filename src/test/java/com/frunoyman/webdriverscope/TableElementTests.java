package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.elements.Row;
import com.frunoyman.webdriverscope.elements.Table;
import com.frunoyman.webdriverscope.pages.BasePage;
import com.frunoyman.webdriverscope.pages.TablesPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Custom elements via plain {@code @FindBy}: a {@link Table} field and a
 * {@code WebElement} field with the same locator on {@link TablesPage}.
 */
public class TableElementTests extends BaseWebTest {

    @Autowired
    private TablesPage tablesPage;

    @Override
    protected Class<? extends BasePage> entryPage() {
        return TablesPage.class;
    }

    @Test
    public void customElementAndPlainWebElementPointAtTheSameNode() {
        Table table = tablesPage.getTable();

        assertEquals(table.getTagName(), "table");
        assertEquals(table.getText(), tablesPage.getRawTable().getText());
        // it is a WebElement, so Selenium's own API takes it as-is
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.visibilityOf(table));
    }

    @Test
    public void readsHeadersAndRows() {
        Table table = tablesPage.getTable();

        assertEquals(table.getHeaders(), List.of("Last Name", "First Name", "Email", "Due", "Web Site", "Action"));
        assertEquals(table.getRows().size(), 4);

        Row first = table.getRow(0);
        assertEquals(first.getCellText(0), "Smith");
        assertEquals(first.getCellText(2), "jsmith@gmail.com");
        assertEquals(table.getColumn("Last Name"), List.of("Smith", "Bach", "Doe", "Conway"));
    }

    @Test
    public void rowsAreLookedUpAgainAfterTheDomChanges() {
        Table table = tablesPage.getTable();

        table.sortBy("Last Name");

        assertEquals(table.getColumn("Last Name"), List.of("Bach", "Conway", "Doe", "Smith"));
    }

    @Test
    public void elementsGetSpringDependenciesWithoutBeingBeans() {
        assertNotNull(tablesPage.getTable().getRow(0).getWrappedDriver(),
                "autowireBean should have injected the WebDriver into a nested Row");
    }
}
