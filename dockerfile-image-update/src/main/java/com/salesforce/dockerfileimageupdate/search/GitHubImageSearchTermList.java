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
                String imagePart = imageParts[i];
                processIntermediateUrlSegment(state, imagePart);
            }
            String finalImageSegment = imageParts[imageParts.length - 1];
            processFinalUrlSegment(state, finalImageSegment);
        }
        state.finalizeCurrentTerm();
        return state.terms;
    }

    /**
     * Intermediate URL segments will finalize the current search term if they have a dash and prep for
     * a new search term. Otherwise, we'll add a slash and this {@code imagePart}.
     *
     * @param state processing state
     * @param imagePart the current image segment
     */
    private static void processIntermediateUrlSegment(ProcessingState state, String imagePart) {
        if (imagePart.contains("-")) {
            state.finalizeCurrentTerm();
        } else {
            state.addToCurrentTerm("/");
        }
        state.addToCurrentTerm(imagePart);
    }

    /**
     * The final URL segment will conclude the current search term if what is currently in the buffer has dashes.
     * Regardless, the final search term should have a slash separator whether it's part of the current search
     * term or at the beginning of a new term.
     *
     * @param state processing state
     * @param finalImageSegment the final URL segment
     */
    private static void processFinalUrlSegment(ProcessingState state, String finalImageSegment) {
        String leftoverTerm = state.getCurrentTerm();
        if (leftoverTerm.contains("-")) {
            state.finalizeCurrentTerm();
        }
        state.addToCurrentTerm("/");
        state.addToCurrentTerm(finalImageSegment);
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
            if (domain.contains("-")) {
                processDashedDomainParts(state, domain);
            } else {
                state.addToCurrentTerm(domain);
            }
        }
        return state;
    }

    /**
     * Dashed domain parts should simply by split by dashes and we'll leave the final domain part in the current search
     * term buffer
     *
     * @param state processing state
     * @param domain the full dashed domain
     */
    private static void processDashedDomainParts(ProcessingState state, String domain) {
        String[] domainParts = domain.split("-");
        for (int i = 0; i < domainParts.length - 1; i++) {
            state.addToCurrentTerm(domainParts[i]);
            state.finalizeCurrentTerm();
        }
        state.addToCurrentTerm(domainParts[domainParts.length - 1]);
    }

    /**
     * Helper class to store processing state of a search term or terms
     */
    static class ProcessingState {
        final List<String> terms = new ArrayList<>();
        private final StringBuffer termBuffer = new StringBuffer();

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
            termBuffer.setLength(0);
        }
    }
}
