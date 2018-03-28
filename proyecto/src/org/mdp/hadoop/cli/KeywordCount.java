package org.mdp.hadoop.cli;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class KeywordCount {

	public static class KeywordCountMapper extends Mapper<Object, Text, Text, IntWritable>{
		private static IntWritable one = new IntWritable(1);
		private static Map<String, List<String>> characters = new HashMap<>();

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			URI[] localPaths = context.getCacheFiles();
			BufferedReader br = null;
			FileSystem fs = FileSystem.get(context.getConfiguration());
			try {
				Path path = new Path(localPaths[0].toString());
				FSDataInputStream fsin = fs.open(path);
				DataInputStream in = new DataInputStream(fsin);
				br = new BufferedReader(new InputStreamReader(in));
				String line;
				while((line=br.readLine())!=null){
					String[] parts = line.split(";");
					String[] names = parts[1].split("-");
					characters.put(parts[0], Arrays.asList(names));
				}
				br.close();
			    in.close();
			    fsin.close(); 
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		 }
		@Override
		public void map(Object key, Text value, Context output)
						throws IOException, InterruptedException {
			String line = value.toString();
			String[] parts = line.split(";;");
			String text = parts[0];
			if (parts.length == 2) {
				long timestamp = Long.parseLong(parts[1].replaceAll(";", ""));
				long minutes = TimeUnit.MILLISECONDS.toMinutes(timestamp);
				String tweet= text.replaceAll("[^a-zA-Z\\s]", "").replaceAll("\\s+", " ").toLowerCase();
				for(String character : characters.keySet()){
					for(String name : characters.get(character)){
						if(tweet.contains(name.trim())){
							output.write(new Text(character+minutes), one);
						}
					}
				}
			}
		}
	}

	public static class KeywordCountReducer extends Reducer<Text, IntWritable, Text, IntWritable> {

		@Override
		public void reduce(Text key, Iterable<IntWritable> values,
				Context output) throws IOException, InterruptedException {
			int sum = 0;
			for(IntWritable value: values) {
				sum += value.get();
			}
			output.write(key, new IntWritable(sum));
		}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 3) {
			System.err.println("Usage: StarCount <in> <out>");
			System.exit(2);
		}
		   
		String inputLocation = otherArgs[0];
		String outputLocation = otherArgs[1];

		String namesFile = otherArgs[2];

		Job job = Job.getInstance(new Configuration());

	    FileInputFormat.setInputPaths(job, new Path(inputLocation));
	    FileOutputFormat.setOutputPath(job, new Path(outputLocation));

	    job.setOutputKeyClass(Text.class);
	    job.setOutputValueClass(IntWritable.class);
	    job.setMapOutputKeyClass(Text.class);
	    job.setMapOutputValueClass(IntWritable.class);

	    job.setMapperClass(KeywordCountMapper.class);
	    job.setCombinerClass(KeywordCountReducer.class);
	    job.setReducerClass(KeywordCountReducer.class);
	    
	    job.addCacheFile(new Path(namesFile).toUri());

	    job.setJarByClass(KeywordCount.class);
	    job.waitForCompletion(true);
	}
}
