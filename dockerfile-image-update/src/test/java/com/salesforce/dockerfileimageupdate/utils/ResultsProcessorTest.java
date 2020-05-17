package com.salesforce.dockerfileimageupdate.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.salesforce.dockerfileimageupdate.utils.ResultsProcessor.REPOS_SKIPPED_MESSAGE;
import static org.mockito.Mockito.*;
import static org.testng.Assert.fail;

public class ResultsProcessorTest {

    @Test(expectedExceptions = IOException.class)
    public void testProcessResultsHasExceptionsAndThrows() throws IOException {
        ResultsProcessor.processResults(new ArrayList<>(),
                Collections.singletonList(new IOException("test")),
                LoggerFactory.getLogger(ResultsProcessorTest.class));
    }

    @Test
    public void testProcessResultsHasSkippedReposAndExceptions() {
        Logger mockLogger = mock(Logger.class);
        List<String> skippedRepos = Collections.singletonList("skipped");
        try {
            ResultsProcessor.processResults(skippedRepos,
                    Collections.singletonList(new IOException("test")),
                    mockLogger);
            fail();
        } catch (IOException exception) {
            verify(mockLogger, times(1)).info(REPOS_SKIPPED_MESSAGE, skippedRepos);
        }
    }

    @Test
    public void testProcessResultsHasNothingToReport() throws IOException {
        Logger mockLogger = mock(Logger.class);
        ResultsProcessor.processResults(new ArrayList<>(),
                new ArrayList<>(),
                mockLogger);
        verify(mockLogger, times(0)).info(any(), anyList());
    }
}