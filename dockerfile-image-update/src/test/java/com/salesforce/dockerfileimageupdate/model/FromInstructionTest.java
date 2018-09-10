package com.salesforce.dockerfileimageupdate.model;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class FromInstructionTest {

    @DataProvider
    public Object[][] inputFormInstructionData() {
        return new Object[][]{
                {"FROM dockerimage:3 # some comment",               "FROM dockerimage:3 # some comment"},
                {"FROM dockerimage:3 AS test",                      "FROM dockerimage:3 AS test"},
                {"FROM dockerimage:3 \tAS \ttest # some comment",   "FROM dockerimage:3 AS test # some comment"},
                {"FROM    dockerimage:3#   some   comment",         "FROM dockerimage:3 #   some   comment"},
                {"        FROM       dockerimage   ",               "FROM dockerimage"},
                {"\t FROM \t dockerimage:4 \t #comment",            "FROM dockerimage:4 #comment"},
                {"FROM dockerimage:4:4:4 #comment",                 "FROM dockerimage:4 #comment"},
                {"FROM dockerimage:4 #comment me # # ",             "FROM dockerimage:4 #comment me # # "}
        };
    }

    @Test(dataProvider = "inputFormInstructionData")
    public void testStringResult(String fromInstruction, String expectedResult) {
        assertEquals(new FromInstruction(fromInstruction).toString(), expectedResult);
    }

    @DataProvider
    public Object[][] isFromInstructionData() {
        return new Object[][]{
                {"FROM dockerimage:3 # some comment",       true},
                {"FROM    dockerimage:3#   some   comment", true},
                {"#FROM dockerimage:3",                     false},
                {"# FROM dockerimage:3",                    false},
                {"        FROM       dockerimage   ",       true},
                {"RUN something",                           false},
                {"",                                        false},
                {"      ",                                  false},
                {null,                                      false},
        };
    }

    @Test(dataProvider = "isFromInstructionData")
    public void testLineToSplit(String input, boolean expectedResult) {
        assertEquals(FromInstruction.isFromInstruction(input), expectedResult);
    }

    @DataProvider
    public Object[][] baseImageNameData() {
        return new Object[][] {
                {"FROM image:tag",                  "image"},
                {"FROM image:tag\t",                "image"},
                {" \t FROM \t image:\t# comment",   "image"},
                {"FROM image:",                     "image"},
                {"FROM image",                      "image"},
                {"FROM",                            null},
                {"FROM image:test # :comment",      "image"}
        };
    }

    @Test(dataProvider = "baseImageNameData")
    public void testBaseImageNameParsedCorrectly(String input, String expectedResult) {
        assertEquals(new FromInstruction(input).getBaseImageName(), expectedResult);
    }

    @DataProvider
    public Object[][] hasBaseImageData() {
        return new Object[][] {
                {"FROM image", "image",                   true},
                {"FROM registry.com/some/image", "image", true},
                {"FROM image", null,                      false},
                {"FROM", "something",                     false},
                {"FROM", null,                            false}
        };
    }

    @Test(dataProvider = "hasBaseImageData")
    public void testHasBaseImage(String fromInstruction, String imageToFind, boolean expectedResult) {
        assertEquals(new FromInstruction(fromInstruction).hasBaseImage(imageToFind), expectedResult);
    }

    @DataProvider
    public Object[][] tagData() {
        return new Object[][] {
                {"FROM image:some-tag",                       "some-tag"},
                {"FROM image:some-tag:with:weird:other:tags", "some-tag"},
                {"FROM image",                                null},
                {"FROM image:",                               null},
                {"FROM image@some-digest",                    null},
                {"FROM image# some comment",                  null},
                {"FROM image:\tsome-tag # comment",           null},
                {"FROM image: some-tag # comment",            null}
        };
    }

    @Test(dataProvider = "tagData")
    public void testTagParsedCorrectly(String fromInstruction, String expectedResult) {
        assertEquals(new FromInstruction(fromInstruction).getTag(), expectedResult);
    }

    @DataProvider
    public Object[][] hasTagData() {
        return new Object[][] {
                {"FROM no tag",                 false},
                {"FROM image",                  false},
                {"FROM image:",                 false},
                {"FROM image:tag#as builder",   true}
        };
    }

    @Test(dataProvider = "hasTagData")
    public void testHasTag(String fromInstructions, boolean expectedResult) {
        assertEquals(new FromInstruction(fromInstructions).hasTag(), expectedResult);
    }

    @DataProvider
    public Object[][] instructionWithNewTagData() {
        return new Object[][] {
                {"FROM image:some-tag",                         "some-tag"},
                {"FROM image:some-tag:with:weird:other:tags",   "some-tag"},
                {"FROM image",                                  null},
                {"FROM image:",                                 null},
                {"FROM image@some-digest",                      null},
                {"FROM image# some comment",                    null},
                {"FROM image:\tsome-tag # comment",             null},
                {"FROM image: some-tag # comment",              null}
        };
    }

    @Test(dataProvider = "instructionWithNewTagData")
    public void testGetInstructionWithNewTag(String fromInstruction, String newTag) {
        FromInstruction oldFromInstruction = new FromInstruction(fromInstruction);
        FromInstruction newFromInstruction = oldFromInstruction.getFromInstructionWithNewTag(newTag);
        assertEquals(newFromInstruction.getBaseImageName(), oldFromInstruction.getBaseImageName());
        assertEquals(newFromInstruction.getComments(), oldFromInstruction.getComments());
        assertEquals(newFromInstruction.getAdditionalParts(), oldFromInstruction.getAdditionalParts());
        assertEquals(newFromInstruction.getTag(), newTag);
    }

    @DataProvider
    public Object[][] hasADifferentTagData() {
        return new Object[][] {
                {"FROM image:tag",          "another",  true},
                {"FROM image:tag",          "tag",      false},
                {"FROM image:tag",          "",         true},
                {"FROM image:tag",          null,       true},
                {"FROM image",              null,       false},
                {"FROM image:",             null,       false},
                {"FROM image: # comment",   null,       false},
                {"FROM image: # comment",   "tag",      true}
        };
    }

    @Test(dataProvider = "hasADifferentTagData")
    public void testHasADifferentTag(String fromInstruction, String tagToCheck, boolean expectedResult) {
        assertEquals(new FromInstruction(fromInstruction).hasADifferentTag(tagToCheck), expectedResult);
    }

    @DataProvider
    public Object[][] additionalPartsData() {
        return new Object[][] {
                {"FROM image:tag as builder",                           ImmutableList.of("as", "builder")},
                {"FROM image:tag as builder of things",                 ImmutableList.of("as", "builder", "of", "things")},
                {"FROM image:tag#as builder",                           ImmutableList.of()},
                {"FROM image:tag\t# comment",                           ImmutableList.of()},
                {"FROM image:tag    \t# comment",                       ImmutableList.of()},
                {"FROM image:tag  some\tother \t thing  \t# comment",   ImmutableList.of("some", "other", "thing")},
                {"FROM image:\t# comment # # # ",                       ImmutableList.of()},
                {"FROM image:",                                         ImmutableList.of()},
                {"FROM",                                                ImmutableList.of()}
        };
    }

    @Test(dataProvider = "additionalPartsData")
    public void testAdditionalPartsParsedCorrectly(String input, ImmutableList expectedResult) {
        assertEquals(new FromInstruction(input).getAdditionalParts(), expectedResult);
    }

    @DataProvider
    public Object[][] commentData() {
        return new Object[][] {
                {"FROM image:tag as builder",       null},
                {"FROM image:tag#as builder",       "#as builder"},
                {"FROM image:tag # comment",        "# comment"},
                {"FROM image:tag\t# comment",       "# comment"},
                {"FROM image:\t# comment # # # ",   "# comment # # # "},
                {"FROM image:",                     null},
                {"FROM image:test # :comment",      "# :comment"}
        };
    }

    @Test(dataProvider = "commentData")
    public void testCommentsParsedCorrectly(String input, String expectedResult) {
        assertEquals(new FromInstruction(input).getComments(), expectedResult);
    }

    @DataProvider
    public Object[][] hasCommentsData() {
        return new Object[][] {
                {"FROM no comment",             false},
                {"FROM image:tag#as builder",   true}
        };
    }

    @Test(dataProvider = "hasCommentsData")
    public void testHasComments(String fromInstructions, boolean expectedResult) {
        assertEquals(new FromInstruction(fromInstructions).hasComments(), expectedResult);
    }

    @DataProvider
    public Object[][] invalidData() {
        return new Object[][]{
                {""},
                {"RUN something"},
                {"# FROM someimage"},
                {":tag # comment"},
                {null}
        };
    }

    @Test(dataProvider = "invalidData", expectedExceptions = IllegalArgumentException.class)
    public void testInvalidFromData(String input) {
        new FromInstruction(input);
    }
}