/**
 * 
 */
package uk.bl.wa.util;

/*
 * #%L
 * warc-indexer
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

import static org.archive.format.warc.WARCConstants.HEADER_KEY_PAYLOAD_DIGEST;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_TYPE;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.io.IOUtils;
import org.archive.format.warc.WARCConstants;
import org.archive.io.ArchiveRecordHeader;
import org.archive.util.Base32;
import org.jwat.common.RandomAccessFileInputStream;

/**
 * Utility method that takes a given input stream and caches the
 * content in RAM, on disk, based on some size limits.
 * 
 * Also calculates the hash of the whole stream.
 * 
 * @author anj
 *
 */
public class HashedCachedInputStream {
	private static Log log = LogFactory.getLog( HashedCachedInputStream.class );
	
	private MessageDigest digest = null;
	
	private String headerHash = null;
	
	private String hash = null;

	private boolean inMemory;
	
	private File cacheFile;
	
	private byte[] cacheBytes;
	
	private boolean truncated = false;
	
	// Thresholds:
	private long inMemoryThreshold = 1024*1024; // Up to 1MB allowed in RAM.
	private long onDiskThreshold = 1024*1024*100; // Up to 100MB cached on disk. 
	
	/**
	 * 
	 * @param header
	 * @param in
	 * @param length
	 * @param inMemoryThreshold
	 * @param onDiskThreshold
	 */
	public HashedCachedInputStream( ArchiveRecordHeader header, InputStream in, long length, long inMemoryThreshold, long onDiskThreshold ) {
		this.inMemoryThreshold = inMemoryThreshold;
		this.onDiskThreshold = onDiskThreshold;
		init(header,in,length);
	}

	/**
	 * Constructo, processed payload for hash and makes content available.
	 * 
	 * @param header
	 * @param in
	 * @param length
	 */
	public HashedCachedInputStream( ArchiveRecordHeader header, InputStream in, long length ) {
		init(header,in,length);
	}
	
	/**
	 * @param header
	 * @param in
	 * @param length
	 */
	private void init(ArchiveRecordHeader header, InputStream in, long length) {
		try {
			digest =  MessageDigest.getInstance( MessageDigestAlgorithms.SHA_1);
		} catch (NoSuchAlgorithmException e) {
			log.error( "Hashing: " + header.getUrl() + "@" + header.getOffset(), e );
		}
		
		try {
			if( header.getHeaderFieldKeys().contains( HEADER_KEY_PAYLOAD_DIGEST ) ) {
				headerHash = ( String ) header.getHeaderValue( HEADER_KEY_PAYLOAD_DIGEST );
			}
			
			// Create a suitable outputstream for caching the content:
			OutputStream cache = null;
			if( length < inMemoryThreshold) {
				inMemory = true;
				cache = new ByteArrayOutputStream();
			} else {
				inMemory = false;
				cacheFile = File.createTempFile("warc-indexer", ".cache");
				cacheFile.deleteOnExit();
				cache = new FileOutputStream( cacheFile );
			}
				
			DigestInputStream dinput = new DigestInputStream( in, digest );
			
			long toCopy = length;
			if( length > this.onDiskThreshold ) {
				toCopy = this.onDiskThreshold;
			}
			IOUtils.copyLarge( dinput, cache, 0, toCopy);
			cache.flush();
			cache.close();

			// Read the remainder of the stream, to get the hash.
			if( length > this.onDiskThreshold ) {
				truncated = true;
				IOUtils.skip( dinput, length - this.onDiskThreshold);
			}
			
			hash = "sha1:" + Base32.encode( digest.digest() );
			
		    // For response records, check the hash is consistent with any header hash:
			if( (headerHash != null) && (hash.length() == headerHash.length())) {
				if( header.getHeaderFieldKeys().contains( HEADER_KEY_TYPE ) &&
					header.getHeaderValue( HEADER_KEY_TYPE ).equals(WARCConstants.WARCRecordType.response.toString())
						) {
					if( ! headerHash.equals(hash)) {
						log.error("Hashes are not equal for this input! " +
								  "Header hash='" + headerHash + "', calculated hash='" + hash + "'");
						throw new RuntimeException(
								"Hash check failed! " +
								"Header hash='" + headerHash + "', calculated hash='" + hash + "'");
					} else {
						log.debug("Hashes were found to match for "+header.getUrl());
					}
				} else {
					// For revisit records, use the hash of the revisited payload:
					// TODO this should actually only do it for revisit type records.
					this.hash = this.headerHash;
				}
			}
			
			// Now set up the inputStream
			if( inMemory ) {
				this.cacheBytes = ((ByteArrayOutputStream)cache).toByteArray();
				// Encourage GC
				cache = null;
			}
		} catch( Exception i ) {
			log.error( "Hashing: " + header.getUrl() + "@" + header.getOffset(), i );
		}		
	}
	
	/**
	 * 
	 * @return
	 */
	public String getHash() {
		return hash;
	}
	
	/**
	 * 
	 * @return
	 */
	public InputStream getInputStream() {
		if( inMemory ) {
			if( this.cacheBytes != null ) {
				return new ByteArrayInputStream( this.cacheBytes );
			} else {
				log.error("Found a NULL byte array!");
				return new ByteArrayInputStream( new byte[] {} );
			}
		} else {
			RandomAccessFile RAFcache;
			try {
				RAFcache = new RandomAccessFile(cacheFile, "r");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return null;
			}
			return new RandomAccessFileInputStream(RAFcache);
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean isTruncated() {
		return truncated;
	}
	
	/**
	 * 
	 */
	public void cleanup() {
		if( this.cacheFile != null )
			this.cacheFile.delete();
	}

}
