package uk.bl.wa.hadoop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.MultiFileSplit;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.log4j.Logger;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;
import org.archive.io.ArchiveRecord;

@SuppressWarnings("deprecation")
public class ArchiveFileRecordReader<Key extends WritableComparable<?>, Value extends Writable>
		implements RecordReader<Text, WritableArchiveRecord> {
	private static Logger log = Logger.getLogger(ArchiveFileRecordReader.class
			.getName());

	private FSDataInputStream datainputstream;
	private FileStatus status;
	private FileSystem filesystem;
	private Path[] paths;
	int currentPath = -1;
	Long offset = 0L;
	private ArchiveReader arcreader;
	private Iterator<ArchiveRecord> iterator;
	private ArchiveRecord record;
	private String archiveName;

	public ArchiveFileRecordReader(Configuration conf, InputSplit split)
			throws IOException {
		if (split instanceof FileSplit) {
			this.paths = new Path[1];
			this.paths[0] = ((FileSplit) split).getPath();
		} else if (split instanceof MultiFileSplit) {
			this.paths = ((MultiFileSplit) split).getPaths();
		} else {
			throw new IOException(
					"InputSplit is not a file split or a multi-file split - aborting");
		}
		// get correct file system in case there are many (such as in EMR)
		this.filesystem = FileSystem.get(this.paths[0].toUri(), conf);

        // Log the paths and check for empty files:
        List<Path> validPaths = new ArrayList<Path>();
		for (Path p : this.paths) {
			log.info("Processing path: " + p);
            FileStatus s = this.filesystem.getFileStatus(p);
            if (s.getLen() == 0) {
                log.warn("Skipping empty file: " + p);
            } else {
                validPaths.add(p);
            }
		}
        // Use this list instead:
        this.paths = validPaths.toArray(this.paths);

		// Queue up the iterator:
		this.nextFile();
	}

	@Override
	public void close() throws IOException {
		if (datainputstream != null) {
			try {
				datainputstream.close();
			} catch (IOException e) {
				log.error("close(): " + e.getMessage());
			}
		}
	}

	@Override
	public Text createKey() {
		return new Text();
	}

	@Override
	public WritableArchiveRecord createValue() {
		return new WritableArchiveRecord();
	}

	@Override
	public long getPos() throws IOException {
		try {
			datainputstream.available();
		} catch (Exception e) {
			return 0;
		}
		return datainputstream.getPos();
	}

	@Override
	public float getProgress() throws IOException {
		float progress = ( float ) datainputstream.getPos() / ( float ) this.status.getLen();
		return progress;
	}

	@Override
	public boolean next(Text key, WritableArchiveRecord value)
			throws IOException {
		boolean found = false;
		while (!found) {
			boolean hasNext = false;
			try {
				hasNext = iterator.hasNext();
			} catch (Throwable e) {
				log.error("ERROR in hasNext():  " + this.archiveName + ": "
						+ e.toString());
				hasNext = false;
			}
			try {
				if (hasNext) {
					record = (ArchiveRecord) iterator.next();
					found = true;
					key.set(this.archiveName);
					value.setRecord(record);
				} else if (!this.nextFile()) {
					break;
				}
			} catch (Throwable e) {
				found = false;
				log.error("ERROR reading " + this.archiveName + ": "
						+ e.toString());
			}
		}
		return found;
	}

	private boolean nextFile() throws IOException {
		currentPath++;
		if (currentPath >= paths.length) {
			return false;
		}
		// Output the archive filename, to help with debugging:
		log.info("Opening nextFile: " + paths[currentPath]);
		// Set up the ArchiveReader:
		this.status = this.filesystem.getFileStatus(paths[currentPath]);
		datainputstream = this.filesystem.open(paths[currentPath]);
		arcreader = (ArchiveReader) ArchiveReaderFactory.get(
				paths[currentPath].getName(), datainputstream, true);
		// Set to strict reading, in order to cope with malformed archive files
		// which cause an infinite loop otherwise.
		arcreader.setStrict(true);
		// Get the iterator:
		iterator = arcreader.iterator();
		this.archiveName = paths[currentPath].getName();
		return true;
	}

}
