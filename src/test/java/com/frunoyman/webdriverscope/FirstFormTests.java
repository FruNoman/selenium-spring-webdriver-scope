package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.pages.SubmittedFormPage;
import com.frunoyman.webdriverscope.pages.WebFormPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Same @SpringBootTest signature as SecondFormTests, on purpose — that's
 * what makes Spring cache one ApplicationContext across both classes.
 */
public class FirstFormTests extends BaseWebTest {

    @Autowired
    private WebFormPage webFormPage;

    @Test
    public void textFieldStartsEmptyAndAcceptsInput() {
        assertEquals(webFormPage.getTextFieldValue(), "", "a fresh session should start with an empty field");

        webFormPage.typeIntoTextField("first test");
        assertEquals(webFormPage.getTextFieldValue(), "first test");

        System.out.println("FirstFormTests session: " + driver);
    }

    @Test
    public void secondMethodAlsoGetsAFreshSession() {
        // If the scope handed back the previous method's already-quit
        // driver instead of a fresh one, this line throws instead of
        // asserting anything — the whole point of this repo.
        assertTrue(webFormPage.getTextFieldValue().isEmpty(),
                "leftover text from the previous test method would show up here otherwise");

        System.out.println("FirstFormTests session: " + driver);
    }

    @Test
    public void submittingTheFormLandsOnTheSubmittedPage() {
        SubmittedFormPage submitted = webFormPage
                .typeIntoTextField("submit me")
                .submit();

        assertEquals(submitted.getMessage(), "Received!");
    }
}
