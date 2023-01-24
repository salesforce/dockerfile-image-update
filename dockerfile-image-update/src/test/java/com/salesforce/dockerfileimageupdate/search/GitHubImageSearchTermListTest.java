package com.salesforce.dockerfileimageupdate.search;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class GitHubImageSearchTermListTest {
    @DataProvider
    public Object[][] imageAndParts() {
        return new Object[][]{
                {null, ImmutableList.of()},
                {"", ImmutableList.of()},
                {"dockerimage", ImmutableList.of("FROM dockerimage")},
                {"gcr.io/i/am/a/container/image", ImmutableList.of("FROM gcr.io/i/am/a/container/image")},
                {"gcr.io/the-dash", ImmutableList.of("FROM gcr.io/the-dash")},
                {"gcr.io/thedash/the-mash", ImmutableList.of("FROM gcr.io/thedash/the-mash")},
                {"someregistry.tld/omundoebao", ImmutableList.of("FROM someregistry.tld/omundoebao")},
                {"gcr.io/some-project/some-folder/some-image", ImmutableList.of("FROM gcr.io", "some-project", "some-folder", "/some-image")},
                {"gcr.io/the-dash/the-mash", ImmutableList.of("FROM gcr.io", "the-dash", "/the-mash")},
                {"gcr.io/the-dash/themash", ImmutableList.of("FROM gcr.io", "the-dash", "/themash")},
                {"some-registry.some.dashy.tld/someimage/with-dashes", ImmutableList.of("FROM some", "registry.some.dashy.tld/someimage/with-dashes")},
                {"this-registry-has-dashes.somecompany.io/rv-python-runtime", ImmutableList.of("FROM this", "registry", "has", "dashes.somecompany.io/rv-python-runtime")},
                {"this-registry-has-dashes.some-company.with-dashes.io/rv-python-runtime", ImmutableList.of("FROM this", "registry", "has", "dashes.some", "company.with", "dashes.io/rv-python-runtime")},
                {"this-registry-has-dashes.some-company.with-dashes.io/some-path/with-more-dashes/rv-python-runtime", ImmutableList.of("FROM this", "registry", "has", "dashes.some", "company.with", "dashes.io", "some-path", "with-more-dashes", "/rv-python-runtime")},
                {"this-registry-has-dashes.some-company.with-dashes.io/somepath/with-more-dashes/rv-python-runtime", ImmutableList.of("FROM this", "registry", "has", "dashes.some", "company.with", "dashes.io/somepath", "with-more-dashes", "/rv-python-runtime")},
                {"this-registry-has-dashes.some-company.with-dashes.io/somepath/with-more-dashes/rv-python-3.9-runtime", ImmutableList.of("FROM this", "registry", "has", "dashes.some", "company.with", "dashes.io/somepath", "with-more-dashes", "/rv-python-3", "9-runtime")},
                {"this-registry-has-dashes.some-company.with-dashes.io/somepath/with-more-dashes/rv.python.3-9.runtime", ImmutableList.of("FROM this", "registry", "has", "dashes.some", "company.with", "dashes.io/somepath", "with-more-dashes", "/rv", "python", "3-9", "runtime")},
        };
    }

    @Test(dataProvider = "imageAndParts")
    public void testGetSearchTermsContent(String image, List<String> expectedResult) {
        List<String> searchTerms = GitHubImageSearchTermList.getSearchTerms(image, "Dockerfile");
        assertEquals(joinListByComma(searchTerms), joinListByComma(expectedResult));
    }

    private String joinListByComma(List<String> list) {
        return String.join(", ", list);
    }

    @Test(dataProvider = "imageAndParts")
    public void testGetSearchTermsSize(String image, List<String> expectedResult) {
        List<String> searchTerms = GitHubImageSearchTermList.getSearchTerms(image, "Dockerfile");
        assertEquals(searchTerms, expectedResult);
    }

    @DataProvider
    public Object[][] domainCurrentTermAndProcessedTerms() {
        return new Object[][]{
                {null, "", ImmutableList.of()},
                {"", "", ImmutableList.of()},
                {"dockerimage", "FROM dockerimage", ImmutableList.of()},
                {"someregistry.tld", "FROM someregistry.tld", ImmutableList.of()},
                {"gcr.io", "FROM gcr.io", ImmutableList.of()},
                {"this-registry-has-dashes.somecompany.io", "dashes.somecompany.io", ImmutableList.of("FROM this", "registry", "has")},
                {"this-registry-has-dashes.some-company.with-dashes.io", "dashes.io", ImmutableList.of("FROM this", "registry", "has", "dashes.some", "company.with")},
        };
    }

    @Test(dataProvider = "domainCurrentTermAndProcessedTerms")
    public void testProcessDomainPartOfImage(String domain, String expectedCurrentTerm, List<String> expectedProcessedTerms) {
        GitHubImageSearchTermList.ProcessingState state = GitHubImageSearchTermList.processDomainPartOfImage(domain, "Dockerfile");
        assertEquals(joinListByComma(state.terms), joinListByComma(expectedProcessedTerms));
        assertEquals(state.getCurrentTerm(), expectedCurrentTerm);
    }
}