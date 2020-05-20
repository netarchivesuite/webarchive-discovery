/**
 *
 */
package uk.bl.wa.analyser.payload;

/*
 * #%L
 * warc-indexer
 * %%
 * Copyright (C) 2013 - 2020 The webarchive-discovery project contributors
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import com.typesafe.config.Config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.archive.io.ArchiveRecordHeader;

import uk.bl.wa.indexer.HTTPHeader;
import uk.bl.wa.solr.SolrFields;
import uk.bl.wa.solr.SolrRecord;
import uk.bl.wa.util.Instrument;
import uk.bl.wa.util.JSONExtractor;
import uk.bl.wa.util.Normalisation;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Analyzer to Twitter tweets harvested by API and packed as WARC by https://github.com/netarchivesuite/so-me.
 */
public class TwitterAnalyser extends AbstractPayloadAnalyser implements JSONExtractor.ContentCallback {
    private static Log log = LogFactory.getLog( TwitterAnalyser.class );

    // TODO: These dynamic fields does not have docValues in the current schema. Hopefully fixed when switching to the Solr 7 schema, but might require adjustments
    // TODO: Does the user-information belong in the same document as the Tweet? Shouldn't it be in a separate document (and linked somehow)?
    public static final String MENTIONS =          "user_mentions_ss";
    public static final String SCREEN_NAME =       "user_screen_name_ss";
    public static final String USER_PROFILE_IMAGE ="user_profile_image_ss";
    public static final String USER_URL =          "user_url_ss";
    public static final String USER_ID =           "user_id_tls";
    public static final String FOLLOWERS_COUNT =   "user_followers_count_is";
    public static final String FRIENDS_COUNT =     "user_friends_count_is";
    public static final String FAVOURITES_COUNT =  "user_favourites_count_is";
    public static final String STATUSES_COUNT =    "user_statuses_count_is";
    public static final String VERIFIED =          "user_verified_bs";
    public static final String DESCRIPTION =       "user_description_t";

    public static final String RETWEETED_COUNT =   "retweeted_count_is";
    public static final String IS_RETWEET =        "is_retweet_bs";
    public static final String IS_QUOTETWEET =     "is_quotetweet_bs";
    public static final String TWEET_ID =          "tweet_id_tls";
    public static final String REPLY_TO_TWEET_ID = "reply_to_tweet_id_tls";
    public static final String REPLY_TO_USER_ID =  "reply_to_user_id_tls";

    private boolean normaliseLinks;

    // All encountered* sets are reset between each tweet parsing
    private final Set<String> encounteredHashtags = new HashSet<>();
    private final Set<String> encounteredLinks = new HashSet<>();
    private final Set<String> encounteredImageLinks = new HashSet<>();
    private final Set<String> encounteredMentions = new HashSet<>();

    private final JSONExtractor extractor = new JSONExtractor();
    { // We always do these
        extractor.add(SolrFields.SOLR_AUTHOR,   true, this, ".user.name");
        extractor.add(SCREEN_NAME,              true, this, ".user.screen_name");
        extractor.add(USER_PROFILE_IMAGE,       true, this, ".user.profile_image_url_https");
        extractor.add(USER_URL,                 true, this, ".user.url");
        extractor.add(FOLLOWERS_COUNT,          true, this, ".user.followers_count");
        extractor.add(FRIENDS_COUNT,            true, this, ".user.friends_count");
        extractor.add(FAVOURITES_COUNT,         true, this, ".user.favourites_count");
        extractor.add(STATUSES_COUNT,           true, this, ".user.statuses_count");
        extractor.add(VERIFIED,                 true, this, ".user.verified");
        extractor.add(DESCRIPTION,              true, this, ".user.description");
        extractor.add(USER_ID,                  true, this, ".user.id_str");

        extractor.add(RETWEETED_COUNT,          true, this, ".retweeted_count");
        extractor.add(IS_RETWEET,               true, this, ".retweeted_status.retweet_count");
        extractor.add(IS_QUOTETWEET,            true, this, // Note: Can be both retweeted and quoted
                      ".quoted_status.quote_count", ".retweeted_status.quoted_status.quote_count");
        extractor.add(TWEET_ID,                 true, this, ".id_str");
        extractor.add(REPLY_TO_TWEET_ID,        true, this, ".in_reply_to_status_id_str");
        extractor.add(REPLY_TO_USER_ID,         true, this, ".in_reply_to_user_id_str");
        extractor.add(SolrFields.LAST_MODIFIED, true, this, ".created_at");

        extractor.add(SolrFields.SOLR_EXTRACTED_TEXT, true, this, expandPaths(
                ".extended_tweet.full_text",
                ".text"
        ));
        extractor.add(SolrFields.SOLR_LINKS, false, this, expandPaths(
                ".entities.urls[].expanded_url",
                ".extended_tweet.entities.urls[].expanded_url",
                ".extended_entities.media[].video_info.variants[].url",
                ".extended_tweet.extended_entities.media[].video_info.variants[].url",
                ".entities.media[].video_info.variants[].url",
                ".extended_tweet.entities.media[].video_info.variants[].url"
        ));
        extractor.add(SolrFields.SOLR_KEYWORDS, false, this, expandPaths(
                ".entities.hashtags[].text",
                ".extended_tweet.entities.hashtags[].text"
        ));
        extractor.add(MENTIONS, false, this, expandPaths(
                ".entities.user_mentions[].screen_name",
                ".extended_tweet.entities.user_mentions[].screen_name"
        ));
    }

    // Needed by the Analyser-resolver
    public TwitterAnalyser() {
    }

    public TwitterAnalyser(Config conf ) {
        configure(conf);
    }

    @Override
    public void configure(Config conf) {
        this.normaliseLinks = conf.hasPath(uk.bl.wa.parsers.HtmlFeatureParser.CONF_LINKS_NORMALISE) ?
                conf.getBoolean(uk.bl.wa.parsers.HtmlFeatureParser.CONF_LINKS_NORMALISE) :
                uk.bl.wa.parsers.HtmlFeatureParser.DEFAULT_LINKS_NORMALISE;
        if (conf.getBoolean("warc.index.extract.linked.images")) {
            extractor.add(SolrFields.SOLR_LINKS_IMAGES, false, this, expandPaths(
                    ".user.profile_image_url_https",
                    ".user.profile_banner_url",
                    ".user.profile_background_image_url_https",

                    ".extended_tweet.entities.media[].media_url_https",
                    ".extended_tweet.entities.media[].media_url",
                    ".extended_entities.media[].media_url_https",
                    ".extended_entities.media[].media_url",
                    ".entities.media[].media_url_https",
                    ".entities.media[].media_url"
            ));
        }
    }

    /**
     * Tweet content can be plain, quoted, retweeted or both retweeted & quoted.
     *
     * This helper takes the given paths and permutates them with the prefixes
     * {@code } (empty prefix)
     * {@code .quoted_status}
     * {@code .retweeted_status}
     * {@code .retweeted_status.quoted_status}
     * @param paths a number of JSON paths.
     * @return the paths permutated with the prefixes mentioned.
     */
    private String[] expandPaths(String... paths) {
        final String[] PREFIXES = new String[]{
                "", ".quoted_status", ".retweeted_status", ".retweeted_status.quoted_status"};
        String[] permutations = new String[PREFIXES.length*paths.length];
        for (int i = 0 ; i < paths.length ; i++) {
            for (int j = 0 ; j < PREFIXES.length ; j++) {
                permutations[(i*PREFIXES.length)+j] = PREFIXES[j] + paths[i];
            }
        }
        return permutations;
    }

    @Override
    public boolean shouldProcess(String detectedMimeType, ArchiveRecordHeader warcHeader, HTTPHeader httpHeader) {
        // https://github.com/netarchivesuite/so-me
        return (warcHeader != null && warcHeader.getMimetype().contains("format=twitter_tweet")) ||
               (httpHeader != null && httpHeader.getHeader("Content-Type", "").contains("format=twitter_tweet"));
    }

    // content is guaranteed to be a Twitter tweet in JSON
    // https://developer.twitter.com/en/docs/tweets/data-dictionary/overview/intro-to-tweet-json
    @Override
    public void analyse(String source, ArchiveRecordHeader header, InputStream content, SolrRecord solr) {
        final long start = System.nanoTime();
        encounteredImageLinks.clear();
        encounteredLinks.clear();
        encounteredHashtags.clear();
        encounteredMentions.clear();
        log.debug("Performing Twitter tweet analyzing");

        solr.removeField(SolrFields.SOLR_EXTRACTED_TEXT); // Clear any existing content
        try {
            if (!extractor.applyRules(content, new TwitterConsumer(solr))) {
                log.warn("Twitter analysing finished without output for tweet " + header.getUrl());
            }
        } catch (Exception e) {
            log.error("Error analysing Twitter tweet " + header.getUrl(), e);
            solr.addParseException("Error analysing Twitter tweet" + header.getUrl(), e);
        }
        solr.makeFieldSingleStringValued(SolrFields.SOLR_EXTRACTED_TEXT);
        Instrument.timeRel("WARCPayloadAnalyzers.analyze#total", "TwitterAnalyzer.analyze#total", start);
    }

    static class TwitterConsumer implements BiConsumer<String, String> {
        private final SolrRecord solrRecord;

        public TwitterConsumer(SolrRecord solrRecord) {
            this.solrRecord = solrRecord;
        }

        @Override
        public void accept(String solrField, String content) {
            switch (solrField) {
                case SolrFields.SOLR_AUTHOR: {
                    solrRecord.addField(SolrFields.SOLR_TITLE, "Tweet by " + content);
                    break;
                }
                case SolrFields.LAST_MODIFIED: {
                    solrRecord.setField(SolrFields.LAST_MODIFIED_YEAR, content.substring(0, 4));
                    break;
                }
            }
            solrRecord.addField(solrField, content);
        }
    }

    @Override
    public String adjust(String jsonPath, String solrField, String content) {
        switch (solrField) {
            case SolrFields.LAST_MODIFIED: try {
                Date date = parseTwitterDate(content);
                return getSolrTimeStamp(date);
            } catch (ParseException e) {
                log.warn("Unable to parse Twitter timestamp '" + content + "' for field " + solrField);
                return null;
            }
            case USER_URL: return normaliseLinks ? Normalisation.canonicaliseURL(content) : content;
            case SolrFields.SOLR_LINKS_IMAGES: return normaliseAndCollapse(content, encounteredImageLinks);
            case SolrFields.SOLR_LINKS: return normaliseAndCollapse(content, encounteredLinks);
            case SolrFields.SOLR_KEYWORDS: return lowercaseAndCollapse(content, encounteredHashtags);
            case MENTIONS: return lowercaseAndCollapse(content, encounteredMentions);
            case IS_RETWEET: return "true"; // We ignore the count itself and just flag that it is a retweet
            case IS_QUOTETWEET: return "true"; // We ignore the count itself and just flag that it is a quote
            default: return content;
        }
    }

    /**
     * Normalises the URl if normaliseLinks is true, then returns null if the URL is already present in encoutered.
     * Else it is added to encountered and returned.
     */
    private String normaliseAndCollapse(String url, Set<String> encountered) {
        url = normaliseLinks ? Normalisation.canonicaliseURL(url) : url;
        return encountered.add(url) ? url : null;
    }

    /**
     * Lowercases content and returns null if the content is already present in encountered.
     * Else it is added to encountered and returned.
     */
    private String lowercaseAndCollapse(String content, Set<String> encountered) {
        content = content.toLowerCase(Locale.ENGLISH); // Really need a better generic guess here
        return encountered.add(content) ? content : null;
    }

    // "Thu Mar 27 15:41:37 +0000 2014"
    private final DateFormat DF = new SimpleDateFormat("EEE MMM dd kk:mm:ss Z yyyy", Locale.ENGLISH);
    // SimpleDateformat is not thread-safe so we synchronize
    private synchronized Date parseTwitterDate(String content) throws ParseException {
        return DF.parse(content);
    }

    // All date-related fields are in UTZ
    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private final DateFormat yearFormat = new SimpleDateFormat("yyyy");
    {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        yearFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    // SimpleDateformat is not thread-safe so we synchronize
    private synchronized String getSolrTimeStamp(Date date){
        return dateFormat.format(date)+"Z";
    }
    // SimpleDateformat is not thread-safe so we synchronize
    private synchronized String getYear(Date date){
        return yearFormat.format(date);
    }
}
