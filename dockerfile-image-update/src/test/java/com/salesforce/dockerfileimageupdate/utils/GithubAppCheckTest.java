package com.salesforce.dockerfileimageupdate.utils;

import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class GithubAppCheckTest {

    private Namespace ns;
    private GithubAppCheck githubAppCheck;
    private CloseableHttpClient httpClient;
    private CloseableHttpResponse response;
    private StatusLine statusLine;

    @BeforeMethod
    public void setUp() throws Exception {
        ns = mock(Namespace.class);
        when(ns.get(Constants.SKIP_GITHUB_APP_SERVER_API_ENDPOINT)).thenReturn("http://renovate.api");
        when(ns.get(Constants.SKIP_GITHUB_APP_SERVER_API_TOKEN)).thenReturn("token");
        githubAppCheck = new GithubAppCheck(ns);

        httpClient = mock(CloseableHttpClient.class);
        response = mock(CloseableHttpResponse.class);
        statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithRenovateApi_Success() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(200);

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithRenovateApi("owner/repo", httpClient);

        assertTrue(result);
        ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);
        verify(httpClient).execute(captor.capture());
        HttpGet httpGet = captor.getValue();
        assertEquals(httpGet.getURI().toString(), "http://renovate.api/api/v1/repos/owner/repo");
        assertEquals(httpGet.getFirstHeader("Authorization").getValue(), "token");
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithRenovateApi_NotFound() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(404);

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithRenovateApi("owner/repo", httpClient);

        assertFalse(result);
    }

    @Test(expectedExceptions = UncheckedIOException.class)
    public void testIsGithubAppEnabledOnRepositoryWithRenovateApi_ServerError() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(500);

        githubAppCheck.isGithubAppEnabledOnRepositoryWithRenovateApi("owner/repo", httpClient);
    }

    @Test(expectedExceptions = UncheckedIOException.class)
    public void testIsGithubAppEnabledOnRepositoryWithRenovateApi_IOException() throws Exception {
        when(httpClient.execute(any(HttpGet.class))).thenThrow(new IOException("network error"));

        githubAppCheck.isGithubAppEnabledOnRepositoryWithRenovateApi("owner/repo", httpClient);
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithGitApi_Success() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(200);
        // Set jwt and appId to avoid refreshJwtIfNeeded calling generateJWT
        setInternalField(githubAppCheck, "jwt", "test-jwt");
        setInternalField(githubAppCheck, "appId", "test-app-id");
        setInternalField(githubAppCheck, "jwtExpiry", Instant.now().plusSeconds(600));

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithGitApi("owner/repo", httpClient);

        assertTrue(result);
        ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);
        verify(httpClient).execute(captor.capture());
        HttpGet httpGet = captor.getValue();
        assertEquals(httpGet.getURI().toString(), "https://git.soma.salesforce.com/api/v3/repos/owner/repo/installation");
        assertEquals(httpGet.getFirstHeader("Authorization").getValue(), "test-jwt");
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithGitApi_NotFound() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(404);
        setInternalField(githubAppCheck, "jwt", "test-jwt");
        setInternalField(githubAppCheck, "jwtExpiry", Instant.now().plusSeconds(600));

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithGitApi("owner/repo", httpClient);

        assertFalse(result);
    }

    @Test(expectedExceptions = UncheckedIOException.class)
    public void testIsGithubAppEnabledOnRepositoryWithGitApi_ServerError() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(500);
        setInternalField(githubAppCheck, "jwt", "test-jwt");
        setInternalField(githubAppCheck, "jwtExpiry", Instant.now().plusSeconds(600));

        githubAppCheck.isGithubAppEnabledOnRepositoryWithGitApi("owner/repo", httpClient);
    }

    @Test
    public void testIsGithubAppEnabledOnRepository_FallbackToGitApi() throws Exception {
        GithubAppCheck spyCheck = spy(githubAppCheck);
        
        // Mock Renovate API to fail with 500 (throws UncheckedIOException)
        doThrow(new UncheckedIOException(new IOException())).when(spyCheck).isGithubAppEnabledOnRepositoryWithRenovateApi(anyString());
        
        // Mock Git API to succeed
        doReturn(true).when(spyCheck).isGithubAppEnabledOnRepositoryWithGitApi(anyString());

        boolean result = spyCheck.isGithubAppEnabledOnRepository("owner/repo");

        assertTrue(result);
        verify(spyCheck, times(2)).isGithubAppEnabledOnRepositoryWithRenovateApi("owner/repo");
        verify(spyCheck).isGithubAppEnabledOnRepositoryWithGitApi("owner/repo");
    }

    @Test
    public void testIsGithubAppEnabledOnRepository_RenovateApiSuccess() throws Exception {
        GithubAppCheck spyCheck = spy(githubAppCheck);
        
        doReturn(true).when(spyCheck).isGithubAppEnabledOnRepositoryWithRenovateApi(anyString());

        boolean result = spyCheck.isGithubAppEnabledOnRepository("owner/repo");

        assertTrue(result);
        verify(spyCheck).isGithubAppEnabledOnRepositoryWithRenovateApi("owner/repo");
        verify(spyCheck, never()).isGithubAppEnabledOnRepositoryWithGitApi(anyString());
    }

    @Test
    public void testRefreshJwtIfNeeded_NotNeeded() throws Exception {
        Instant farFuture = Instant.now().plusSeconds(1000);
        setInternalField(githubAppCheck, "jwt", "valid-jwt");
        setInternalField(githubAppCheck, "jwtExpiry", farFuture);

        githubAppCheck.refreshJwtIfNeeded("appId", "path");

        // Verify it didn't change (though we can't easily verify generateJWT wasn't called without PowerMock)
        assertEquals(getInternalField(githubAppCheck, "jwt"), "valid-jwt");
        assertEquals(getInternalField(githubAppCheck, "jwtExpiry"), farFuture);
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithRetry_ExecutesSupplier() {
        Supplier<Boolean> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn(true);

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithRetry("owner/repo", supplier);

        assertTrue(result);
        verify(supplier, times(1)).get();
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithRetry_RetriesOnException() {
        Supplier<Boolean> supplier = mock(Supplier.class);
        when(supplier.get())
                .thenThrow(new UncheckedIOException(new IOException("retryable")))
                .thenReturn(true);

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithRetry("owner/repo", supplier);

        assertTrue(result);
        verify(supplier, times(2)).get();
    }

    @Test
    public void testConstructor_PopulatesFields() throws Exception {
        Namespace mockNs = mock(Namespace.class);
        when(mockNs.get(Constants.SKIP_GITHUB_APP_ID)).thenReturn("appId");
        when(mockNs.get(Constants.SKIP_GITHUB_APP_KEY)).thenReturn("keyPath");
        when(mockNs.get(Constants.SKIP_GITHUB_APP_SERVER_API_TOKEN)).thenReturn("token");
        when(mockNs.get(Constants.SKIP_GITHUB_APP_SERVER_API_ENDPOINT)).thenReturn("endpoint");
        when(mockNs.getString(Constants.GIT_API)).thenReturn("http://git.api");

        GithubAppCheck check = new GithubAppCheck(mockNs);

        assertEquals(getInternalField(check, "appId"), "appId");
        assertEquals(getInternalField(check, "privateKeyPath"), "keyPath");
        assertEquals(getInternalField(check, "appServerApiToken"), "token");
        assertEquals(getInternalField(check, "appServerApiEndpoint"), "endpoint");
    }

    @Test
    public void testRefreshJwtIfNeeded_WhenNull() throws Exception {
        setInternalField(githubAppCheck, "jwt", null);

        githubAppCheck.refreshJwtIfNeeded("appId", "invalid-path");

        assertNull(getInternalField(githubAppCheck, "jwt"));
    }

    @Test
    public void testRefreshJwtIfNeeded_WhenExpired() throws Exception {
        setInternalField(githubAppCheck, "jwt", "old-jwt");
        setInternalField(githubAppCheck, "jwtExpiry", Instant.now().minusSeconds(100));

        githubAppCheck.refreshJwtIfNeeded("appId", "invalid-path");

        // Should have tried to refresh and failed (since path is invalid), keeping it "old-jwt"
        assertEquals(getInternalField(githubAppCheck, "jwt"), "old-jwt");
    }

    private void setInternalField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private Object getInternalField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }
}
