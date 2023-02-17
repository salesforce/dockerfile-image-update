package com.salesforce.dockerfileimageupdate.storage;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.testng.annotations.Test;
import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class S3BackedImageTagStoreTest {
    @Test
    public void testUpdateStorePutsObjectIfBucketExists() throws IOException {
        AmazonS3 amazonS3 = mock(AmazonS3.class);
        S3BackedImageTagStore s3BackedImageTagStore = spy(new S3BackedImageTagStore(amazonS3, "store"));
        when(amazonS3.doesBucketExistV2("store")).thenReturn(true);
        s3BackedImageTagStore.updateStore("domain/namespace/image", "tag");
        verify(amazonS3).putObject("store", "domain!namespace!image", "tag");
    }

    @Test
    public void testUpdateStoreThrowsExceptionWhenBucketDoesNotExist() throws IOException {
        AmazonS3 amazonS3 = mock(AmazonS3.class);
        S3BackedImageTagStore s3BackedImageTagStore = spy(new S3BackedImageTagStore(amazonS3, "store"));
        when(amazonS3.doesBucketExistV2("store")).thenReturn(false);

        assertThrows(IOException.class, () -> s3BackedImageTagStore.updateStore("image", "tag"));
        verify(amazonS3, times(0)).putObject("store", "image", "tag");
    }

    @Test
    public void testGetStoreContentReturnsStoreContentWithTruncatedResults() throws InterruptedException {
        AmazonS3 amazonS3 = mock(AmazonS3.class);
        S3BackedImageTagStore s3BackedImageTagStore = spy(new S3BackedImageTagStore(amazonS3, "store"));
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        ListObjectsV2Result listObjectsV2Result1 = mock(ListObjectsV2Result.class);
        ListObjectsV2Result listObjectsV2Result2 = mock(ListObjectsV2Result.class);

        S3ObjectSummary s3ObjectSummary = mock(S3ObjectSummary.class);
        List<S3ObjectSummary> s3ObjectSummaryList = new ArrayList<>();
        s3ObjectSummaryList.add(s3ObjectSummary);

        Date date = mock(Date.class);
        S3Object s3Object = mock(S3Object.class);
        S3Object s3Object2 = mock(S3Object.class);
        String tag = "tag";
        String tag2 = "tag2";
        byte tagBytes[] = tag.getBytes();
        byte tagBytes2[] = tag2.getBytes();
        S3ObjectInputStream objectContent = new S3ObjectInputStream(new ByteArrayInputStream(tagBytes), null);
        S3ObjectInputStream objectContent2 = new S3ObjectInputStream(new ByteArrayInputStream(tagBytes2), null);
        s3Object.setObjectContent(objectContent);
        s3Object2.setObjectContent(objectContent2);

        when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsV2Result1, listObjectsV2Result2);
        when(listObjectsV2Result1.getObjectSummaries()).thenReturn(s3ObjectSummaryList);
        when(listObjectsV2Result1.isTruncated()).thenReturn(true);
        when(listObjectsV2Result2.getObjectSummaries()).thenReturn(s3ObjectSummaryList);
        when(listObjectsV2Result2.isTruncated()).thenReturn(false);
        when(s3ObjectSummary.getLastModified()).thenReturn(date , date);
        when(s3ObjectSummary.getKey()).thenReturn("domain!namespace!image", "domain!namespace!image2");
        when(amazonS3.getObject("store", "domain!namespace!image")).thenReturn(s3Object);
        when(amazonS3.getObject("store", "domain!namespace!image2")).thenReturn(s3Object2);
        when(s3Object.getObjectContent()).thenReturn(objectContent);
        when(s3Object2.getObjectContent()).thenReturn(objectContent2);

        List<ImageTagStoreContent> actualResult = s3BackedImageTagStore.getStoreContent(dockerfileGitHubUtil, "store");

        verify(amazonS3).getObject("store", "domain!namespace!image");
        verify(amazonS3).getObject("store", "domain!namespace!image2");
        assertEquals(actualResult.size(), 2);
        assertEquals(actualResult.get(0).getImageName(), "domain/namespace/image");
        assertEquals(actualResult.get(0).getTag(), "tag");
        assertEquals(actualResult.get(1).getImageName(), "domain/namespace/image2");
        assertEquals(actualResult.get(1).getTag(), "tag2");
    }

    @Test
    public void testGetStoreContentReturnsStoreContent() throws InterruptedException {
        AmazonS3 amazonS3 = mock(AmazonS3.class);
        S3BackedImageTagStore s3BackedImageTagStore = spy(new S3BackedImageTagStore(amazonS3, "store"));
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        ListObjectsV2Result listObjectsV2Result = mock(ListObjectsV2Result.class);

        S3ObjectSummary s3ObjectSummary = mock(S3ObjectSummary.class);
        List<S3ObjectSummary> s3ObjectSummaryListList = Collections.singletonList(s3ObjectSummary);
        Date date = mock(Date.class);
        S3Object s3Object = mock(S3Object.class);
        String tag = "tag";
        byte tagBytes[] = tag.getBytes();
        S3ObjectInputStream objectContent = new S3ObjectInputStream(new ByteArrayInputStream(tagBytes), null);
        s3Object.setObjectContent(objectContent);

        when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsV2Result);
        when(listObjectsV2Result.getObjectSummaries()).thenReturn(s3ObjectSummaryListList);
        when(listObjectsV2Result.isTruncated()).thenReturn(false);
        when(s3ObjectSummary.getLastModified()).thenReturn(date);
        when(s3ObjectSummary.getKey()).thenReturn("domain!namespace!image");
        when(amazonS3.getObject("store", "domain!namespace!image")).thenReturn(s3Object);
        when(s3Object.getObjectContent()).thenReturn(objectContent);

        List<ImageTagStoreContent> actualResult = s3BackedImageTagStore.getStoreContent(dockerfileGitHubUtil, "store");

        verify(amazonS3).getObject("store", "domain!namespace!image");
        assertEquals(actualResult.size(), 1);
        assertEquals(actualResult.get(0).getImageName(), "domain/namespace/image");
        assertEquals(actualResult.get(0).getTag(), "tag");
    }

    @Test
    public void testGetStoreContentReturnsStoreContentSorted() throws InterruptedException {
        AmazonS3 amazonS3 = mock(AmazonS3.class);
        S3BackedImageTagStore s3BackedImageTagStore = spy(new S3BackedImageTagStore(amazonS3, "store"));
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        ListObjectsV2Result listObjectsV2Result = mock(ListObjectsV2Result.class);
        S3ObjectSummary s3ObjectSummary = mock(S3ObjectSummary.class);
        List<S3ObjectSummary> s3ObjectSummaryList = mock(List.class);
        Date date1 = new Date();
        date1.setTime(1332403882588L);
        Date date2 = new Date();
        date2.setTime(1332403882589L);
        S3Object s3Object1 = mock(S3Object.class);
        S3Object s3Object2 = mock(S3Object.class);
        String tag1 = "tag1";
        String tag2 = "tag2";
        byte tagBytes1[] = tag1.getBytes();
        byte tagBytes2[] = tag2.getBytes();
        S3ObjectInputStream objectContent1 = new S3ObjectInputStream(new ByteArrayInputStream(tagBytes1), null);
        S3ObjectInputStream objectContent2 = new S3ObjectInputStream(new ByteArrayInputStream(tagBytes2), null);
        s3Object1.setObjectContent(objectContent1);
        s3Object2.setObjectContent(objectContent2);
        String key1 = "domain!namespace!image1";
        String key2 = "domain!namespace!image2";
        String image1 = "domain/namespace/image1";
        String image2 = "domain/namespace/image2";
        Iterator<S3ObjectSummary> s3ObjectSummaryIterator = mock(Iterator.class);

        when(s3ObjectSummaryIterator.next()).thenReturn(s3ObjectSummary, s3ObjectSummary);
        when(s3ObjectSummaryIterator.hasNext()).thenReturn(true, true, false);
        when(s3ObjectSummaryList.iterator()).thenReturn(s3ObjectSummaryIterator);
        when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsV2Result);
        when(listObjectsV2Result.getObjectSummaries()).thenReturn(s3ObjectSummaryList);
        when(listObjectsV2Result.isTruncated()).thenReturn(false);
        when(s3ObjectSummary.getLastModified()).thenReturn(date1, date2);
        when(s3ObjectSummary.getKey()).thenReturn(key1, key2);
        when(amazonS3.getObject("store", key1)).thenReturn(s3Object1);
        when(amazonS3.getObject("store", key2)).thenReturn(s3Object2);
        when(s3Object1.getObjectContent()).thenReturn(objectContent1);
        when(s3Object2.getObjectContent()).thenReturn(objectContent2);

        List<ImageTagStoreContent> actualResult = s3BackedImageTagStore.getStoreContent(dockerfileGitHubUtil, "store");

        verify(amazonS3, times(1)).getObject("store", key1);
        verify(amazonS3, times(1)).getObject("store", key2);
        assertEquals(actualResult.size(), 2);
        assertEquals(actualResult.get(0).getImageName(), image2);
        assertEquals(actualResult.get(0).getTag(), tag2);
        assertEquals(actualResult.get(1).getImageName(), image1);
        assertEquals(actualResult.get(1).getTag(), tag1);
    }
}
