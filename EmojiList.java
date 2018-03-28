package org.mdp.hadoop.cli;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiTrie;

public class EmojiList {
	static private Map<String, List<String>> characters = new HashMap<>();

	static{
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("nombres.csv"));
			String line = br.readLine();
			while(line!=null){
				String[] parts = line.split(";");
				String[] names = parts[1].split("-");
				characters.put(parts[0], Arrays.asList(names));
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static class EmojiListMapper extends Mapper<Object, Text, Text, Text>{
		@Override
		public void map(Object key, Text value, Context output)
						throws IOException, InterruptedException {
			String line = value.toString();
			String[] parts = line.split(";;");
			String text = parts[0];
			List<String> emojis = new ArrayList<>();
			if(EmojiManager.isEmoji(text.toCharArray())==EmojiTrie.Matches.POSSIBLY){
				for(Emoji e : EmojiManager.getAll()){
					if(text.contains(e.getUnicode())){
						emojis.add(e.getAliases().get(0));
					}
				}
			} else {
				return;
			}
			String tweet= text.replaceAll("[^\\p{L} ]", " ").toLowerCase();
			for(String character : characters.keySet()){
				for(String name : characters.get(character)){
					if(tweet.contains(name)){
						for(String tag : emojis){
							output.write(new Text(character), new Text(tag));
						}
					}
				}
			}
		}
	}

	public static class EmojiListReducer extends Reducer<Text, Text, Text, Text> {

		@Override
		public void reduce(Text key, Iterable<Text> values,
				Context output) throws IOException, InterruptedException {
			StringBuilder builder = new StringBuilder();
			for(Text value: values){
				builder.append(value.toString()+";");
			}
			output.write(key, new Text(builder.toString()));
		}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 2) {
			System.err.println("Usage: StarCount <in> <out>");
			System.exit(2);
		}
		String inputLocation = otherArgs[0];
		String outputLocation = otherArgs[1];

		Job job = Job.getInstance(new Configuration());

	    FileInputFormat.setInputPaths(job, new Path(inputLocation));
	    FileOutputFormat.setOutputPath(job, new Path(outputLocation));

	    job.setOutputKeyClass(Text.class);
	    job.setOutputValueClass(Text.class);
	    job.setMapOutputKeyClass(Text.class);
	    job.setMapOutputValueClass(Text.class);

	    job.setMapperClass(EmojiListMapper.class);
	    job.setCombinerClass(EmojiListReducer.class);
	    job.setReducerClass(EmojiListReducer.class);

	    job.setJarByClass(KeywordCount.class);
		job.waitForCompletion(true);
	}
}
