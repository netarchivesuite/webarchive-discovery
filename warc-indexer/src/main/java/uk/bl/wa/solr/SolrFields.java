package uk.bl.wa.solr;

/*
 * #%L
 * warc-indexer
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2013 - 2014 The UK Web Archive
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

/**
 * Holds mappings to Solr document fields.
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public interface SolrFields {
	public static final String ID = "id";
	public static final String ID_LONG = "id_long";
	public static final String SOLR_URL = "url";
    // Intended for building links-graphs. Normalised the same way as SOLR_LINKS
    public static final String SOLR_URL_NORMALISED = "url_norm";
	public static final String SOURCE_FILE = "source_file_s";
	// public static final String SOURCE_FILE_OFFSET = "source_file_offset";
	
	public static final String SOLR_URL_TYPE = "url_type";
	public static final String SOLR_URL_TYPE_SLASHPAGE = "slashpage";
	public static final String SOLR_URL_TYPE_EMBED = "embed";
	public static final String SOLR_URL_TYPE_ROBOTS_TXT = "robots.txt";
	public static final String SOLR_URL_TYPE_NORMAL = "normal";
	public static final String SOLR_URL_TYPE_UNKNOWN = "unknown";
	
	public static final String SOLR_HOST = "host";
	public static final String DOMAIN = "domain";
	public static final String PUBLIC_SUFFIX = "public_suffix";

	public static final String HASH = "hash";
	public static final String SOLR_TITLE = "title";
	public static final String SOLR_SUBJECT = "subject";
	public static final String SOLR_DESCRIPTION = "description";
	public static final String SOLR_COMMENTS = "comments";
	public static final String SOLR_AUTHOR = "author";
	public static final String SOLR_KEYWORDS = "keywords";
	public static final String SOLR_CATEGORY = "category";
	public static final String SOLR_COLLECTION = "collection"; // Top-level collection
	public static final String SOLR_COLLECTIONS = "collections"; // All collections.
	
	public static final String SOLR_LINKS = "links";
	public static final String SOLR_LINKS_HOSTS = "links_hosts";
	public static final String SOLR_LINKS_DOMAINS = "links_domains";
	public static final String SOLR_LINKS_PUBLIC_SUFFIXES = "links_public_suffixes";
	
	public static final String CONTENT_LANGUAGE = "content_language";
	public static final String SOLR_CONTENT_TYPE = "content_type";
	public static final String CONTENT_ENCODING = "content_encoding";
	public static final String CONTENT_VERSION = "content_type_version";
	public static final String FULL_CONTENT_TYPE = "content_type_full";
	public static final String CONTENT_TYPE_TIKA = "content_type_tika";
	public static final String CONTENT_TYPE_DROID = "content_type_droid";
	public static final String CONTENT_TYPE_DROID_B = "content_type_droid_b";
	public static final String CONTENT_TYPE_SERVED = "content_type_served";
	public static final String CONTENT_TYPE_EXT = "content_type_ext";
	public static final String SOLR_NORMALISED_CONTENT_TYPE = "content_type_norm";
	public static final String CONTENT_FFB = "content_ffb"; /* The first four bytes */
	public static final String CONTENT_FIRST_BYTES = "content_first_bytes";
	public static final String GENERATOR = "generator";
	public static final String SERVER = "server";
	public static final String PARSE_ERROR = "parse_error";
	public static final String CONTENT_WARNING = "content_warning";
	public static final String XML_ROOT_NS = "xml_root_ns";
	public static final String PDFA_IS_VALID = "pdf_pdfa_is_valid";
	public static final String PDFA_ERRORS = "pdf_pdfa_errors";
	
	public static final String SOLR_RECORD_TYPE = "record_type";
	
	public static final String CONTENT_LENGTH = "content_length";
	public static final String SOLR_TIMESTAMP = "timestamp";
	public static final String SOLR_REFERRER_URI = "referrer_url";
	public static final String SOLR_EXTRACTED_TEXT = "content";
	public static final String SOLR_EXTRACTED_TEXT_NOT_STORED = "text";
	public static final String SOLR_EXTRACTED_TEXT_LENGTH = "content_text_length";
	public static final String SOLR_TIKA_METADATA = "content_metadata_ss";
	public static final String WAYBACK_DATE = "wayback_date";
	public static final String CRAWL_DATE = "crawl_date";
	public static final String CRAWL_DATES = "crawl_dates";
	public static final String CRAWL_YEAR = "crawl_year";
	public static final String CRAWL_YEARS = "crawl_years";
	public static final String PUBLICATION_DATE = "publication_date";
	public static final String PUBLICATION_YEAR = "publication_year";
	public static final String LAST_MODIFIED = "last_modified";
	public static final String LAST_MODIFIED_YEAR = "last_modified_year";
	
	public static final String POSTCODE = "postcode";
	public static final String POSTCODE_DISTRICT = "postcode_district";
	public static final String LOCATIONS = "locations";
	
	public static final String SENTIMENT = "sentiment";
	public static final String[] SENTIMENTS = new String[] {"Very Negative", "Negative", "Mildly Negative" ,"Neutral", "Mildly Positive", "Positive", "Very Positive"};
	public static final String SENTIMENT_SCORE = "sentiment_score";
	
	public static final String LICENSE_URL = "license_url";
	
	public static final String SSDEEP_PREFIX = "ssdeep_hash_bs_";
	public static final String SSDEEP_NGRAM_PREFIX = "ssdeep_hash_ngram_bs_";
	
	public static final String ELEMENTS_USED = "elements_used";
	
	public static final String IMAGE_WIDTH = "image_width";
	public static final String IMAGE_HEIGHT = "image_height";
	public static final String IMAGE_SIZE = "image_size";
	public static final String IMAGE_FACES = "image_faces";
	public static final String IMAGE_FACES_COUNT = "image_faces_count";
	public static final String IMAGE_COLOURS = "image_colours";
	public static final String IMAGE_DOMINANT_COLOUR = "image_dominant_colour";

}