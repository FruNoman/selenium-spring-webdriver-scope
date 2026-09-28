package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.pages.SubmittedFormPage;
import com.frunoyman.webdriverscope.pages.WebFormPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** selenium.dev's web form: one short action per test, through {@link WebFormPage}. */
public class WebFormTests extends BaseWebTest {

    // a singleton page, shared by every test and thread — safe because
    // its driver is the per-thread scoped proxy
    @Autowired
    private WebFormPage webFormPage;

    @Test
    public void pageHasTitle() {
        webFormPage.open();

        assertEquals(webFormPage.getTitle(), "Web form");
    }

    @Test
    public void typeIntoTextInput() {
        webFormPage.open().typeText("hello");

        assertEquals(webFormPage.getText(), "hello");
    }

    @Test
    public void selectFromDropdown() {
        webFormPage.open().selectOption("Two");

        assertEquals(webFormPage.getSelectedValue(), "2");
    }

    @Test
    public void toggleCheckbox() {
        webFormPage.open();
        assertFalse(webFormPage.isCheckboxChecked());

        webFormPage.toggleCheckbox();

        assertTrue(webFormPage.isCheckboxChecked());
    }

    @Test
    public void submitForm() {
        SubmittedFormPage submitted = webFormPage.open().submit();

        assertEquals(submitted.getMessage(), "Received!");
    }
}
