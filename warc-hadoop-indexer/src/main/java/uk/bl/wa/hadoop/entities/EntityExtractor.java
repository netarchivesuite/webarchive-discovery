package uk.bl.wa.hadoop.entities;

/*
 * #%L
 * warc-hadoop-indexer
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import uk.bl.wa.hadoop.ArchiveFileInputFormat;
import uk.bl.wa.hadoop.mapred.FrequencyCountingReducer;

/**
 * EntityExtractor:
 * 
 * Extracts known entities from web archives.
 * 
 * @author Andrew.Jackson@bl.uk
 */

@SuppressWarnings( { "deprecation" } )
public class EntityExtractor extends Configured implements Tool {
    private static Logger log = Logger.getLogger(EntityExtractor.class.getName());
    
    public static final String REGEX_PATTERN_PARAM = "regex.pattern";

    public int run( String[] args ) throws IOException {
        JobConf conf = new JobConf( getConf(), EntityExtractor.class );
        
        log.info("Loading paths...");
        String line = null;
        List<Path> paths = new ArrayList<Path>();
        BufferedReader br = new BufferedReader( new FileReader( args[ 0 ] ) );
        while( ( line = br.readLine() ) != null ) {
            paths.add( new Path( line ) );
        }
        br.close();
        log.info("Setting paths...");
        FileInputFormat.setInputPaths( conf, paths.toArray(new Path[] {}) );
        log.info("Set "+paths.size()+" InputPaths");

        FileOutputFormat.setOutputPath( conf, new Path( args[ 1 ] ) );
        
        conf.set( REGEX_PATTERN_PARAM, args[ 2 ] );
        log.info("Set regex pattern = "+conf.get(REGEX_PATTERN_PARAM));

        conf.setJobName( args[ 0 ] + "_" + System.currentTimeMillis() );
        conf.setInputFormat( ArchiveFileInputFormat.class );
        conf.setMapperClass( EntityMapper.class );
        conf.setReducerClass( FrequencyCountingReducer.class );
        conf.setOutputFormat( TextOutputFormat.class );

        conf.setOutputKeyClass( Text.class );
        conf.setOutputValueClass( Text.class );
        
        // Override the maxiumum JobConf size so very large lists of files can be processed:
        // Default mapred.user.jobconf.limit=5242880 (5M), bump to 100 megabytes = 104857600 bytes.
        conf.set("mapred.user.jobconf.limit", "104857600");
        
        // Manually set a large number of reducers:
        conf.setNumReduceTasks(50);
        
        // Run it:
        JobClient.runJob( conf );
        
        return 0;

    }

    public static void main( String[] args ) throws Exception {
        if( args.length != 3 ) {
            System.out.println( "Need <input file list>, <output dir> and <regular expression>!" );
            System.exit( 1 );

        }
        int ret = ToolRunner.run( new EntityExtractor(), args );
        System.exit( ret );
    }
}
