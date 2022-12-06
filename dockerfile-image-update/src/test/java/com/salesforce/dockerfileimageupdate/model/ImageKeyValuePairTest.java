package com.salesforce.dockerfileimageupdate.model;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ImageKeyValuePairTest {

    @DataProvider
    public Object[][] inputImageKeyValuePairData() {
        return new Object[][]{
                {"image: dockerimage:3 # some comment",               "image: dockerimage:3 # some comment"},
                {"image: dockerimage:3 AS test",                      "image: dockerimage:3 AS test"},
                {"image: dockerimage:3 \tAS \ttest # some comment",   "image: dockerimage:3 \tAS \ttest # some comment"},
                {"image:    dockerimage:3#   some   comment",         "image: dockerimage:3 #   some   comment"},
                {"        image:       dockerimage   ",               "image: dockerimage"},
                {"\t image: \t dockerimage:4 \t #comment",            "image: dockerimage:4 #comment"},
                {"image: dockerimage:4:4:4 #comment",                 "image: dockerimage:4 #comment"},
                {"image: dockerimage:4 #comment me # # ",             "image: dockerimage:4 #comment me # # "}
        };
    }

    @Test(dataProvider = "inputImageKeyValuePairData")
    public void testStringResult(String fromInstruction, String expectedResult) {
        assertEquals(new ImageKeyValuePair(fromInstruction).toString(), expectedResult);
    }

    @DataProvider
    public Object[][] isImageKeyValuePairData() {
        return new Object[][]{
                {"image: dockerimage:3 # some comment",       true},
                {"image:    dockerimage:3#   some   comment", true},
                {"#image: dockerimage:3",                     false},
                {"# image: dockerimage:3",                    false},
                {"        image:       dockerimage   ",       true},
                {"RUN something",                           false},
                {"",                                        false},
                {"      ",                                  false},
                {null,                                      false},
        };
    }

    @Test(dataProvider = "isImageKeyValuePairData")
    public void testLineToSplit(String input, boolean expectedResult) {
        assertEquals(ImageKeyValuePair.isImageKeyValuePair(input), expectedResult);
    }

    @DataProvider
    public Object[][] baseImageNameData() {
        return new Object[][] {
                {"image: image:tag",                  "image"},
                {"image: image:tag\t",                "image"},
                {" \t image: \t image:\t# comment",   "image"},
                {"image: image:",                     "image"},
                {"image: image",                      "image"},
                {"image:",                            null},
                {"image: image:test # :comment",      "image"}
        };
    }

    @Test(dataProvider = "baseImageNameData")
    public void testBaseImageNameParsedCorrectly(String input, String expectedResult) {
        assertEquals(new ImageKeyValuePair(input).getBaseImageName(), expectedResult);
    }

    @DataProvider
    public Object[][] hasBaseImageData() {
        return new Object[][] {
                {"image: image", "image",                   true},
                {"image: registry.com/some/image", "image", true},
                {"image: image", null,                      false},
                {"image:", "something",                     false},
                {"image:", null,                            false}
        };
    }

    @Test(dataProvider = "hasBaseImageData")
    public void testHasBaseImage(String fromInstruction, String imageToFind, boolean expectedResult) {
        assertEquals(new ImageKeyValuePair(fromInstruction).hasBaseImage(imageToFind), expectedResult);
    }

    @DataProvider
    public Object[][] tagData() {
        return new Object[][] {
                {"image: image:some-tag",                       "some-tag"},
                {"image: image:some-tag:with:weird:other:tags", "some-tag"},
                {"image: image",                                null},
                {"image: image:",                               null},
                {"image: image@some-digest",                    null},
                {"image: image# some comment",                  null},
                {"image: image:\tsome-tag # comment",           "\tsome-tag"},
                {"image: image: some-tag # comment",            " some-tag"}
        };
    }

    @Test(dataProvider = "tagData")
    public void testTagParsedCorrectly(String fromInstruction, String expectedResult) {
        assertEquals(new ImageKeyValuePair(fromInstruction).getTag(), expectedResult);
    }

    @DataProvider
    public Object[][] hasTagData() {
        return new Object[][] {
                {"image: no tag",                 false},
                {"image: image",                  false},
                {"image: image:",                 false},
                {"image: image:tag#as builder",   true}
        };
    }

    @Test(dataProvider = "hasTagData")
    public void testHasTag(String fromInstructions, boolean expectedResult) {
        assertEquals(new ImageKeyValuePair(fromInstructions).hasTag(), expectedResult);
    }

    @DataProvider
    public Object[][] instructionWithNewTagData() {
        return new Object[][] {
                {"image: image:some-tag",                         "some-tag"},
                {"image: image:some-tag:with:weird:other:tags",   "some-tag"},
                {"image: image",                                  null},
                {"image: image:",                                 null},
                {"image: image@some-digest",                      null},
                {"image: image# some comment",                    null},
                {"image: image:\tsome-tag # comment",             null},
                {"image: image: some-tag # comment",              null}
        };
    }

    @Test(dataProvider = "instructionWithNewTagData")
    public void testGetInstructionWithNewTag(String fromInstruction, String newTag) {
        ImageKeyValuePair oldImageKeyValuePair = new ImageKeyValuePair(fromInstruction);
        ImageKeyValuePair newImageKeyValuePair = oldImageKeyValuePair.getImageKeyValuePairWithNewTag(newTag);
        assertEquals(newImageKeyValuePair.getBaseImageName(), oldImageKeyValuePair.getBaseImageName());
        assertEquals(newImageKeyValuePair.getComments(), oldImageKeyValuePair.getComments());
        assertEquals(newImageKeyValuePair.getAdditionalParts(), oldImageKeyValuePair.getAdditionalParts());
        assertEquals(newImageKeyValuePair.getTag(), newTag);
    }

    @DataProvider
    public Object[][] hasADifferentTagData() {
        return new Object[][] {
                {"image: image:tag",          "another",  true},
                {"image: image:tag",          "tag",      false},
                {"image: image:tag",          "",         true},
                {"image: image:tag",          null,       true},
                {"image: image",              null,       false},
                {"image: image:",             null,       false},
                {"image: image: # comment",   null,       false},
                {"image: image: # comment",   "tag",      true}
        };
    }

    @Test(dataProvider = "hasADifferentTagData")
    public void testHasADifferentTag(String fromInstruction, String tagToCheck, boolean expectedResult) {
        assertEquals(new ImageKeyValuePair(fromInstruction).hasADifferentTag(tagToCheck), expectedResult);
    }

/*    @DataProvider
    public Object[][] additionalPartsData() {
        return new Object[][] {
                {"image: image:tag as builder",                           ImmutableList.of("as", "builder")},
                {"image: image:tag as builder of things",                 ImmutableList.of("as", "builder", "of", "things")},
                {"image: image:tag#as builder",                           ImmutableList.of()},
                {"image: image:tag\t# comment",                           ImmutableList.of()},
                {"image: image:tag    \t# comment",                       ImmutableList.of()},
                {"image: image:tag  some\tother \t thing  \t# comment",   ImmutableList.of("some", "other", "thing")},
                {"image: image:\t# comment # # # ",                       ImmutableList.of()},
                {"image: image:",                                         ImmutableList.of()},
                {"image:",                                                ImmutableList.of()}
        };
    }

    @Test(dataProvider = "additionalPartsData")
    public void testAdditionalPartsParsedCorrectly(String input, ImmutableList expectedResult) {
        assertEquals(new ImageKeyValuePair(input).getAdditionalParts(), expectedResult);
    }*/

    @DataProvider
    public Object[][] commentData() {
        return new Object[][] {
                {"image: image:tag as builder",       null},
                {"image: image:tag#as builder",       "#as builder"},
                {"image: image:tag # comment",        "# comment"},
                {"image: image:tag\t# comment",       "# comment"},
                {"image: image:\t# comment # # # ",   "# comment # # # "},
                {"image: image:",                     null},
                {"image: image:test # :comment",      "# :comment"}
        };
    }

    @Test(dataProvider = "commentData")
    public void testCommentsParsedCorrectly(String input, String expectedResult) {
        assertEquals(new ImageKeyValuePair(input).getComments(), expectedResult);
    }

    @DataProvider
    public Object[][] hasCommentsData() {
        return new Object[][] {
                {"image: no comment",             false},
                {"image: image:tag#as builder",   true}
        };
    }

    @Test(dataProvider = "hasCommentsData")
    public void testHasComments(String fromInstructions, boolean expectedResult) {
        assertEquals(new ImageKeyValuePair(fromInstructions).hasComments(), expectedResult);
    }

    @DataProvider
    public Object[][] invalidData() {
        return new Object[][]{
                {""},
                {"RUN something"},
                {"# image: someimage"},
                {":tag # comment"},
                {null}
        };
    }

    @Test(dataProvider = "invalidData", expectedExceptions = IllegalArgumentException.class)
    public void testInvalidFromData(String input) {
        new ImageKeyValuePair(input);
    }

    @DataProvider
    public Object[][] isImageKeyValuePairWithThisImageAndOlderTagData() {
        return new Object[][]{
                {"image: someimage", "someimage", "sometag", true},
                {"image: someimage:sometag", "someimage", "sometag", false},
                {"not a valid image key-value pair", "someimage", "sometag", false},
                {"image: someimage:oldtag", "someimage", "sometag", true}
        };
    }

    @Test(dataProvider = "isImageKeyValuePairWithThisImageAndOlderTagData")
    public void isImageKeyValuePairWithThisImageAndOlderTag(String line, String imageName, String imageTag, boolean expectedResult) {
        assertEquals(ImageKeyValuePair.isImageKeyValuePairWithThisImageAndOlderTag(line, imageName, imageTag), expectedResult);
    }
}