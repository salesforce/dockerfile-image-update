package com.salesforce.dockerfileimageupdate.search;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

public class GitHubImageSearchTermList {
    /**
     * We need to split out search terms to accomodate how GitHub code search works
     * https://docs.github.com/en/github/searching-for-information-on-github/searching-code#considerations-for-code-search
     *
     * Essentially we'll split out any dashes in the registry domain and split out any url segments with dashes by
     * removing the slashes surrounding them to make them an independent search term.
     *
     * The overarching goal here is to make sure we try and keep our search space down since GitHub Code Search will
     * only return up to about 1000 results.
     * Reference: #169 and https://developer.github.com/v3/search/#about-the-search-api
     *
     * @param image the name of the image including registry
     * @return list of GitHub code search terms
     */
    public static List<String> getSearchTerms(String image) {
        if (image == null || image.trim().isEmpty()) {
            return ImmutableList.of();
        }
        String[] imageParts = image.split("/");
        ProcessingState state = processDomainPartOfImage(imageParts[0]);
        if (imageParts.length > 1) {
            for (int i = 1; i < imageParts.length - 1; i++) {
                if (imageParts[i].contains("-")) {
                    state.finalizeCurrentTerm();
                } else {
                    state.addToCurrentTerm("/");
                }
                state.addToCurrentTerm(imageParts[i]);
            }
            String leftoverTerm = state.getCurrentTerm();
            if (leftoverTerm.contains("-")) {
                state.finalizeCurrentTerm();
            }
            state.addToCurrentTerm("/");
            state.addToCurrentTerm(imageParts[imageParts.length - 1]);
        }
        state.finalizeCurrentTerm();
        return state.terms;
    }

    /**
     * Domains with dashes do not function well with GitHub code search
     * https://docs.github.com/en/github/searching-for-information-on-github/searching-code#considerations-for-code-search
     *
     * Unfortunately, code search is not implemented in any other way such as GraphQL
     *
     * @param domain the domain part of the image (which may or may not be the full registry name)
     * @return processing state for the search terms
     */
    static ProcessingState processDomainPartOfImage(String domain) {
        ProcessingState state = new ProcessingState();
        if (domain == null || domain.trim().isEmpty()) {
            return state;
        } else {
            state.addToCurrentTerm("FROM ");
            String leftoverDomain = domain;
            if (domain.contains("-")) {
                String[] domainParts = domain.split("-");
                for (int i = 0; i < domainParts.length - 1; i++) {
                    state.addToCurrentTerm(domainParts[i]);
                    state.finalizeCurrentTerm();
                }
                leftoverDomain = domainParts[domainParts.length - 1];
            }
            state.addToCurrentTerm(leftoverDomain);
        }
        return state;
    }

    /**
     * Helper class to store processing state of a search term or terms
     */
    static class ProcessingState {
        final List<String> terms = new ArrayList<>();
        private StringBuffer termBuffer = new StringBuffer();

        /**
         * Add to the current search term
         * @param segment segment to add to the current search term
         */
        void addToCurrentTerm(String segment) {
            termBuffer.append(segment);
        }

        /**
         * Get the current term as it stands right now
         * @return current term
         */
        String getCurrentTerm() {
            return termBuffer.toString();
        }

        /**
         * This will finalize the current term by adding it to the list and prepping for a new empty term
         */
        void finalizeCurrentTerm() {
            terms.add(termBuffer.toString());
            termBuffer = new StringBuffer();
        }
    }
}
