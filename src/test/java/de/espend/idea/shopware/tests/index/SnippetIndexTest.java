package de.espend.idea.shopware.tests.index;

import de.espend.idea.shopware.index.SnippetIndex;
import de.espend.idea.shopware.tests.ShopwareLightCodeInsightFixtureTestCase;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class SnippetIndexTest extends ShopwareLightCodeInsightFixtureTestCase {
    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyFileToProject("snippets.tpl");
        myFixture.copyFileToProject("widgets.ini", "snippets/frontend/listing/foobar_ini/widgets.ini");
        myFixture.copyFileToProject("javascript.js");
    }

    public String getTestDataPath() {
        return "src/test/java/de/espend/idea/shopware/tests/index/fixtures";
    }

    public void testTemplateUsageSnippetsAreInIndex() {
        assertIndexContains(SnippetIndex.KEY, "frontend/foobar", "frontend/detail/actions");

        assertIndexContainsKeyWithValue(SnippetIndex.KEY, "frontend/foobar", value ->
            value.contains("FO-O/BAR")
        );
    }

    public void testIniSnippetsAreInIndex() {
        assertIndexContains(SnippetIndex.KEY, "frontend/listing/foobar_ini/widgets");

        assertIndexContainsKeyWithValue(SnippetIndex.KEY, "frontend/listing/foobar_ini/widgets", value ->
            value.contains("swag-last-registrations/customer")
        );
    }

    public void testJavascriptCommentNamespaceSnippetsAreInIndex() {
        assertIndexContains(SnippetIndex.KEY, "backend/namespace");
    }
}
