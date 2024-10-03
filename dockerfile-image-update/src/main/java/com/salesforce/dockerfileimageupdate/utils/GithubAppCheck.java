package com.salesforce.dockerfileimageupdate.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.salesforce.dockerfileimageupdate.CommandLine;
import net.sourceforge.argparse4j.inf.Namespace;
import org.bouncycastle.util.io.pem.PemReader;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.MaxRetriesExceeded;
import java.time.Duration;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.util.concurrent.TimeoutException;
import java.io.UncheckedIOException;


public class GithubAppCheck {
    private static final Logger log = LoggerFactory.getLogger(GithubAppCheck.class);

    private final String appId;
    private final String privateKeyPath;
    private final String appServerApiToken;
    private final String appServerApiEndpoint;
    private String jwt;
    private Instant jwtExpiry;
    private GitHub gitHub;
    private CloseableHttpClient httpClient = HttpClients.createDefault();

    public GithubAppCheck(final Namespace ns){
        this.appId = ns.get(Constants.SKIP_GITHUB_APP_ID);
        this.privateKeyPath = ns.get(Constants.SKIP_GITHUB_APP_KEY);
        this.appServerApiToken = ns.get(Constants.SKIP_GITHUB_APP_SERVER_API_TOKEN);
        this.appServerApiEndpoint = ns.get(Constants.SKIP_GITHUB_APP_SERVER_API_ENDPOINT);
        this.jwt = null;
        this.jwtExpiry = null;
        this.gitHub = null;
        if (this.appId != null && this.privateKeyPath != null) {
            try {
                generateJWT(this.appId, this.privateKeyPath);
            } catch (GeneralSecurityException | IOException exception) {
                log.warn("Could not initialise JWT due to exception: {}", exception.getMessage());
            }
            try {
                this.gitHub = new GitHubBuilder()
                        .withEndpoint(CommandLine.gitApiUrl(ns))
                        .withJwtToken(jwt)
                        .build();
            } catch (IOException exception) {
                log.warn("Could not initialise github due to exception: {}", exception.getMessage());
            }
        }
        else {
            log.warn("Could not find any Github app ID and Github app Key in the declared list. Hence assuming this class is no longer needed");
        }
    }

    /**
     * Method to verify whether the github app is installed on a repository or not. 
     * @param fullRepoName = The repository full name, i.e, of the format "owner/repoName". Eg: "Salesforce/dockerfile-image-update"
     * @return True if github app is installed, false otherwise. 
     */
    protected boolean isGithubAppEnabledOnRepository(String fullRepoName) {
        try {
            return isGithubAppEnabledOnRepositoryWithRetry(fullRepoName, () -> isGithubAppEnabledOnRepositoryWithRenovateApi(fullRepoName));  
        } catch (MaxRetriesExceeded | UncheckedIOException exception) {
            return isGithubAppEnabledOnRepositoryWithRetry(fullRepoName, () -> isGithubAppEnabledOnRepositoryWithGitApi(fullRepoName));
        }
    }

    protected boolean isGithubAppEnabledOnRepositoryWithRetry(String fullRepoName, Supplier<Boolean> supplier) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2) // The maximum number of attempts (including the initial call as the first attempt); Source: https://resilience4j.readme.io/docs/retry
                .waitDuration(Duration.ofMillis(1000))
                .retryExceptions(TimeoutException.class, UncheckedIOException.class)
                .build();
        Retry retry = Retry.of("id", config);

        return Retry.decorateSupplier(retry, supplier).get();
    }


    /**
     * Method to verify whether the github app is installed on a repository or not, using Git API
     * @param fullRepoName = The repository full name, i.e, of the format "owner/repoName". Eg: "Salesforce/dockerfile-image-update"
     * @return True if github app is installed, false otherwise. 
     * Reference: https://docs.github.com/en/rest/apps/apps?apiVersion=2022-11-28#get-a-repository-installation-for-the-authenticated-app
     */

    protected boolean isGithubAppEnabledOnRepositoryWithGitApi(String fullRepoName) {
        return isGithubAppEnabledOnRepositoryWithGitApi(fullRepoName, httpClient);
    }

    protected boolean isGithubAppEnabledOnRepositoryWithGitApi(String fullRepoName, CloseableHttpClient httpClient) {
        refreshJwtIfNeeded(appId, privateKeyPath);
        String apiEndpoint = "https://git.soma.salesforce.com/api/v3/repos/" + fullRepoName + "/installation";
        HttpGet httpGet = new HttpGet(apiEndpoint);
        httpGet.setHeader("Authorization", jwt);
        httpGet.setHeader("Accept", "application/vnd.github+json");
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            log.warn("[isGithubAppEnabledOnRepositoryWithGitApi] -- Response code `{}` while trying to get app installation using Git API", statusCode);
            if (statusCode >= 500) {
                throw new UncheckedIOException(new IOException());
            }
            return statusCode == 200;
        } catch (IOException exception) {
            log.warn("[isGithubAppEnabledOnRepositoryWithGitApi] -- Exception while trying to get app installation using Git API: {}", exception);
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Method to verify whether the github app is installed on a repository or not, using Renovate API
     * @param fullRepoName = The repository full name, i.e, of the format "owner/repoName". Eg: "Salesforce/dockerfile-image-update"
     * @return True if github app is installed, false otherwise. 
     * Reference: https://github.com/mend/renovate-ce-ee/blob/main/docs/reporting-apis.md#repo-info
     */

    protected boolean isGithubAppEnabledOnRepositoryWithRenovateApi(String fullRepoName) {
        return isGithubAppEnabledOnRepositoryWithRenovateApi(fullRepoName, httpClient);
    }

    protected boolean isGithubAppEnabledOnRepositoryWithRenovateApi(String fullRepoName, CloseableHttpClient httpClient) {
        String apiEndpoint = appServerApiEndpoint + "/api/repos/" + fullRepoName;
        HttpGet httpGet = new HttpGet(apiEndpoint);
        httpGet.setHeader("Authorization", appServerApiToken);
        httpGet.setHeader("Accept", "application/json");
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            log.warn("[isGithubAppEnabledOnRepositoryWithRenovateApi] -- Response code `{}` while trying to get app installation by Renovate API", statusCode);
            if (statusCode >= 500) {
                throw new UncheckedIOException(new IOException());
            }
            return statusCode == 200;
        } catch (IOException exception) {
            log.warn("[isGithubAppEnabledOnRepositoryWithRenovateApi] -- Exception while trying to get app installation using Renovate API: {}", exception);
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Method to refresh the JWT token if needed. Checks the JWT expiry time, and if it is 60s away from expiring, refreshes it. 
     * @param appId = The id of the Github App to generate the JWT for
     * @param privateKeyPath = The path to the private key of the Github App to generate the JWT for
     */
    protected void refreshJwtIfNeeded(String appId, String privateKeyPath) {
        if (jwt == null || jwtExpiry.isBefore(Instant.now().minusSeconds(60))) {  // Adding a buffer to ensure token validity
            try {
                generateJWT(appId, privateKeyPath);
            } catch (IOException | GeneralSecurityException exception) {
                log.warn("Could not refresh the JWT due to exception: {}", exception.getMessage());
            }
        }
    }

    /**
     * Method to generate the JWT used to access the Github App APIs. We generate the JWT to be valid for 600 seconds. 
     * Along with the JWT value, the jwtExpiry value is set to the time of 600 sec from now. 
     * @param appId = The id of the Github App to generate the JWT for
     * @param privateKeyPath = The path to the private key of the Github App to generate the JWT for
     * @throws IOException
     * @throws GeneralSecurityException
     */
    private void generateJWT(String appId, String privateKeyPath) throws IOException, GeneralSecurityException {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        RSAPrivateKey privateKey = getRSAPrivateKey(privateKeyPath);

        Algorithm algorithm = Algorithm.RSA256(null, privateKey);
        Instant now = Instant.now();
        jwt = JWT.create()
                .withIssuer(appId)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(600))) // 10 minutes expiration
                .sign(algorithm);
        jwtExpiry = now.plusSeconds(600);
    }

    /**
     * The method to get the private key in an RSA Encoded format. Makes use of org.bouncycastle.util
     * @param privateKeyPath
     * @return
     * @throws IOException
     * @throws GeneralSecurityException
     */
    private RSAPrivateKey getRSAPrivateKey(String privateKeyPath) throws IOException, GeneralSecurityException {
        try (PemReader pemReader = new PemReader(new FileReader(new File(privateKeyPath)))) {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pemReader.readPemObject().getContent());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(spec);
        }
    }
}