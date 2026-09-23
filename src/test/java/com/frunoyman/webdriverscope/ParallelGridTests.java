package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.pages.WebFormPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Ten methods, run with {@code parallel="methods" thread-count="10"} in
 * {@code testng-parallel.xml} — a genuinely concurrent proof, not just
 * "a fresh driver per sequential test" like FirstFormTests/SecondFormTests.
 *
 * This only works because RemoteWebDriverConfig's beans use
 * {@code @Scope("webdriverscope")} (SimpleThreadScope-based, thread-local
 * under the hood). Spring Boot's own built-in WebDriverScope shares one
 * instance per bean name across every thread — with ten TestNG threads
 * hitting that, they'd all fight over the same browser session.
 */
public class ParallelGridTests extends BaseWebTest {

    @Autowired
    private WebFormPage webFormPage;

    @Test
    public void test01() { assertFreshSession(); }
    @Test
    public void test02() { assertFreshSession(); }
    @Test
    public void test03() { assertFreshSession(); }
    @Test
    public void test04() { assertFreshSession(); }
    @Test
    public void test05() { assertFreshSession(); }
    @Test
    public void test06() { assertFreshSession(); }
    @Test
    public void test07() { assertFreshSession(); }
    @Test
    public void test08() { assertFreshSession(); }
    @Test
    public void test09() { assertFreshSession(); }
    @Test
    public void test10() { assertFreshSession(); }

    private void assertFreshSession() {
        assertEquals(webFormPage.getTextFieldValue(), "",
                "a session shared with another thread would already have text in it");
        System.out.println(Thread.currentThread().getName() + " -> " + driver);
    }
}
