package uk.bl.wa.hadoop.indexer;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.PropertyConfigurator;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.util.SurtPrefixSet;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import uk.bl.wa.annotation.Annotations;
import uk.bl.wa.annotation.Annotator;
import uk.bl.wa.hadoop.WritableArchiveRecord;
import uk.bl.wa.indexer.WARCIndexer;
import uk.bl.wa.solr.SolrRecord;
import uk.bl.wa.solr.SolrRecordFactory;
import uk.bl.wa.solr.SolrWebServer;
import uk.bl.wa.util.Normalisation;

@SuppressWarnings( { "deprecation" } )
public class WARCIndexerMapper extends MapReduceBase implements
		Mapper<Text, WritableArchiveRecord, IntWritable, WritableSolrRecord> {
	private static final Log LOG = LogFactory.getLog( WARCIndexerMapper.class );

	static enum MyCounters {
		NUM_RECORDS, NUM_ERRORS, NUM_NULLS, NUM_EMPTY_HEADERS
	}

	private String mapTaskId;
	private String inputFile;
	private int noRecords = 0;

	private WARCIndexer windex;

	private int numShards = 1;
    private int numReducers = 10;
	private Config config;

	private SolrRecordFactory solrFactory = SolrRecordFactory.createFactory(null); // Overridden by innerConfigure

	public WARCIndexerMapper() {
		try {
			// Re-configure logging:
			Properties props = new Properties();
			props.load(getClass().getResourceAsStream("/log4j-override.properties"));
			PropertyConfigurator.configure(props);
		} catch (IOException e1) {
			LOG.error("Failed to load log4j config from properties file.");
		}
	}

	@Override
	public void configure( JobConf job ) {
		if (this.config == null) {
			innerConfigure(ConfigFactory.parseString(job
				.get(WARCIndexerRunner.CONFIG_PROPERTIES)));
		}

		// Other properties:
		mapTaskId = job.get("mapred.task.id");
		inputFile = job.get("map.input.file");
		LOG.info("Got task.id " + mapTaskId + " and input.file " + inputFile);

	}

	public void innerConfigure(Config jobConfig) {
		try {
			// Get config from job property:
			config = jobConfig;
			// Initialise indexer:
			this.windex = new WARCIndexer( config );
			// Decide whether to try to apply annotations:
			boolean applyAnnotations = false;
			if( config.hasPath(WARCIndexerRunner.CONFIG_APPLY_ANNOTATIONS)) {
				applyAnnotations = config
						.getBoolean(WARCIndexerRunner.CONFIG_APPLY_ANNOTATIONS);
			}
			if (applyAnnotations) {
				LOG.info("Attempting to load annotations from 'annotations.json'...");
				Annotations ann = Annotations.fromJsonFile("annotations.json");
                LOG.info(
                        "Attempting to load OA SURTS from 'openAccessSurts.txt'...");
                SurtPrefixSet oaSurts = Annotator
                        .loadSurtPrefix("openAccessSurts.txt");
                windex.setAnnotations(ann, oaSurts);
			}

			// Set up sharding:
			numShards = config.getInt(SolrWebServer.NUM_SHARDS);

            // Get the number of reducers:
            try {
                numReducers = config.getInt("warc.hadoop.num_reducers");
            } catch (NumberFormatException n) {
                numReducers = 10;
            }
			solrFactory = SolrRecordFactory.createFactory(config);
		} catch( NoSuchAlgorithmException e ) {
			LOG.error("WARCIndexerMapper.configure(): " + e.getMessage());
		} catch (JsonParseException e) {
			LOG.error("WARCIndexerMapper.configure(): " + e.getMessage());
		} catch (JsonMappingException e) {
			LOG.error("WARCIndexerMapper.configure(): " + e.getMessage());
		} catch (IOException e) {
			LOG.error("WARCIndexerMapper.configure(): " + e.getMessage());
		}
	}

	public WritableSolrRecord innerMap(Text key,
			WritableArchiveRecord value,
			Reporter reporter) throws IOException {

		ArchiveRecordHeader header = value.getRecord().getHeader();

		noRecords++;

		ArchiveRecord rec = value.getRecord();
		SolrRecord solr = solrFactory.createRecord(key.toString(), rec.getHeader());
		final String url = Normalisation.sanitiseWARCHeaderValue(header.getUrl());
		try {
			if (!header.getHeaderFields().isEmpty()) {
				// Do the indexing:
				solr = windex.extract(key.toString(),
						value.getRecord());

				// If there is no result, report it
				if (solr == null) {
					LOG.debug("WARCIndexer returned NULL for: " + url);
					reporter.incrCounter(MyCounters.NUM_NULLS, 1);
					return null;
				}

				// Increment record counter:
				reporter.incrCounter(MyCounters.NUM_RECORDS, 1);

			} else {
				// Report headerless records:
				reporter.incrCounter(MyCounters.NUM_EMPTY_HEADERS, 1);

			}

		} catch (Exception e) {
			LOG.error(e.getClass().getName() + ": " + e.getMessage() + "; "
					+ url + "; " + header.getOffset(), e);
			// Increment error counter
			reporter.incrCounter(MyCounters.NUM_ERRORS, 1);
			// Store it:
			solr.addParseException(e);

		} catch (OutOfMemoryError e) {
			// Allow processing to continue if a record causes OOME:
			LOG.error("OOME " + e.getClass().getName() + ": " + e.getMessage()
					+ "; " + url + "; " + header.getOffset());
			// Increment error counter
			reporter.incrCounter(MyCounters.NUM_ERRORS, 1);
			// Store it:
			solr.addParseException(e);
		}

		// Wrap up and collect the result:
		WritableSolrRecord wsolr = new WritableSolrRecord(solr);

		// Occasionally update application-level status
		if ((noRecords % 1000) == 0) {
			reporter.setStatus(noRecords + " processed from " + inputFile);
			// Also assure framework that we are making progress:
			reporter.progress();
		}

		return wsolr;

	}

	@Override
	public void map(Text key, WritableArchiveRecord value,
			OutputCollector<IntWritable, WritableSolrRecord> output,
			Reporter reporter) throws IOException {

		WritableSolrRecord wsolr = this.innerMap(key, value, reporter);

		// Pass to reduce stage if successful:
		if (wsolr != null) {

            // Use a random assignment per shard:
            // int iKey = (int) (Math.round(Math.random() * numShards));
            // Use a random assignment per reducer:
            int iKey = (int) (Math.round(Math.random() * numReducers));

            IntWritable oKey = new IntWritable(iKey);
			output.collect(oKey, wsolr);

		}
	}


}
