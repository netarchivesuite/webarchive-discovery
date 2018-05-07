/**
 * 
 */
package uk.bl.wa.hadoop.mapreduce.warcstats;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import uk.bl.wa.hadoop.ArchiveFileInputFormat;
import uk.bl.wa.hadoop.KeylessTextOutputFormat;
import uk.bl.wa.hadoop.mapred.FrequencyCountingReducer;

/**
 * @author Andrew.Jackson@bl.uk
 *
 */
public class WARCStatsTool extends Configured implements Tool {
	
	private static Log log = LogFactory.getLog(WARCStatsTool.class);	
	
	protected void createJobConf( JobConf conf, String[] args ) throws IOException {

		// Store application properties where the mappers/reducers can access them
		Config index_conf = ConfigFactory.load();
		log.info("Loaded warc config.");

		// Also set mapred speculative execution off:
		conf.set( "mapred.reduce.tasks.speculative.execution", "false" );

		// Reducer count dependent on concurrent HTTP connections to Solr server.
		int numReducers = 1;
		try {
			numReducers = index_conf.getInt( "warc.hadoop.num_reducers" );
		} catch( NumberFormatException n ) {
			numReducers = 10;
		}

		// Add input paths:
		log.info("Reading input files...");
		String line = null;
		BufferedReader br = new BufferedReader( new FileReader( args[ 0 ] ) );
		while( ( line = br.readLine() ) != null ) {
			FileInputFormat.addInputPath( conf, new Path( line ) );
		}
		br.close();
		log.info("Read input files.");

		FileOutputFormat.setOutputPath( conf, new Path( args[ 1 ] ) );

		conf.setJobName( args[ 0 ] + "_" + System.currentTimeMillis() );
        conf.setInputFormat(ArchiveFileInputFormat.class);
		conf.setMapperClass( WARCStatsMapper.class );
		conf.setReducerClass( FrequencyCountingReducer.class );
        conf.setOutputFormat(KeylessTextOutputFormat.class);
		conf.set( "map.output.key.field.separator", "" );

		conf.setOutputKeyClass( Text.class );
		conf.setOutputValueClass( Text.class );
		conf.setMapOutputValueClass( Text.class );
		conf.setNumReduceTasks( numReducers );
	}

	
	@Override
	public int run(String[] args) throws Exception {
		// Set up the base conf:
		JobConf conf = new JobConf( getConf(), WARCStatsTool.class );
		
		// Get the job configuration:
		this.createJobConf(conf, args);
		
		// Submit it:
		JobClient client = new JobClient( conf );
        RunningJob job = client.submitJob(conf);

        // And await completion before exiting...
        job.waitForCompletion();
		
		return 0;
	}

	/**
	 * @param args command line arguments
	 * @throws Exception at runtime
	 */
	public static void main(String[] args) throws Exception {
        if (!(args.length == 2)) {
			System.out.println( "Need input file.list and output dir!" );
			System.exit( 0 );

		}
		int ret = ToolRunner.run( new WARCStatsTool(), args );

		System.exit( ret );
	}

}
