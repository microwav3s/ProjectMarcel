import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.jsoup.*;
import org.jsoup.select.Elements;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.*;

public class IR_P01 {

	private static Analyzer analyzer;

	private static String getRanked(String query, ScoreDoc[] results, IndexSearcher indexSearcher) throws Throwable {
		String output = "";
		int rank = 1;

		output += "Search results for \"" + query + "\"\n";
		// For all search results, add their String representation to the output
		for (ScoreDoc scoredDocument : results) {

			Document currentDocument = indexSearcher.getIndexReader().document(scoredDocument.doc);

			String docHead = rank + ":\t" + currentDocument.getField("title").stringValue();
			String docPath = "Path:" + currentDocument.getField("path").stringValue();
			String docSummary = "Summary: " + currentDocument.getField("summary").stringValue();

			output += docHead + "\n" + docPath + "\n" + docSummary + "\n";
			rank++;
		}

		return output;
	}

	private static Document getDocument(String path) throws Throwable {

		File input = new File(path);
		org.jsoup.nodes.Document doc = Jsoup.parse(input, "UTF-8", "");

		// Create body, title and date tags
		Elements body = doc.getElementsByTag("body");
		String date = "";

		// Search for document date
		for (org.jsoup.nodes.Element meta : doc.select("meta")) {
			if (meta.attr("name").equals("date"))
				date = meta.attr("content");
		}

		// Tokenize, stopword eliminate and stem document body
		String processedBody = tokenStemming(body.text()).toString();

		// Create Lucene Document and add required Fields, only store title and date in index
		Document result = new Document();

		result.add(new TextField("title", doc.getElementsByTag("title").text(), Field.Store.YES));
		result.add(new TextField("date", date, Field.Store.YES));
		result.add(new TextField("summary", doc.getElementsByTag("summary").text(), Field.Store.YES));
		result.add(new TextField("body", processedBody, Field.Store.NO));
		result.add(new TextField("path", path, Field.Store.YES));

		return result;
	}


	private List<String> stemText(String text) {
		// TODO

		return null;
	}

	public static void main(String[] args) throws Throwable {
		String doc_location = "";
		String index_location = "";
		String query = "";
		boolean use_vs = false;

		Path path;
		Directory directory;
		DirectoryReader directoryReader;
		IndexSearcher indexSearcher;

		/**
		 * Argument collection
		 */
		try {
			doc_location = args[0];
			if(args[1].toLowerCase().equals("vs") || args[1].toLowerCase().equals("ok")) {
				use_vs = (args[1].toLowerCase().equals("vs"));
				query = args[2];
			} else {
				index_location = args[1];
				use_vs = (args[2].toLowerCase().equals("vs"));
				query = args[3];
			}
		}
		catch(ArrayIndexOutOfBoundsException exception) {
			System.err.print("Necessary arguments not detected.\n Please run the program again with the following syntax: " +
					"java -jar IR_P01.jar [path_to_document_folder] [path_to_index_folder] [VS/OK] [query]\n");
		}

		/**
		 * Welcome Screen
		 */
		System.out.println("IR_P01 running.\n");
		System.out.println("Taking document data from " + doc_location);
		System.out.println("Using index directory " + index_location);
		System.out.print("Running ");
		System.out.print((use_vs ? "Vector Space Model ranking algorithm" : "Okapi BM25") + " with query " + query + "\n");

		/**
		 * Stemming
		 */

		analyzer = new EnglishAnalyzer();
		// TODO

		/**
		 * Initialize index if none is found
		 */
		path = Paths.get(index_location);
		directory = FSDirectory.open(path);

		if (!DirectoryReader.indexExists(directory)) {

			IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);

			if (!use_vs) {
				BM25Similarity similarity = new BM25Similarity();
				writerConfig.setSimilarity(similarity);
			} else {
				// VM is default?
			}

			IndexWriter indWriter = new IndexWriter(directory, writerConfig);

			// Create Document list to feed into our index writer
			ArrayList<File> documents = new ArrayList<File>();
			addFileToList(doc_location, documents);

			System.out.println("Indexing files...");
			for (File file : documents) {
				indWriter.addDocument(getDocument(file.getAbsolutePath()));
			}

			indWriter.close();
		}

		directoryReader = DirectoryReader.open(directory);
		indexSearcher = new IndexSearcher(directoryReader);

		ScoreDoc[] result = createSearchIndex(query, 10, indexSearcher);

		System.out.println(getRanked(query, result, indexSearcher));
	}

	public static ScoreDoc[] createSearchIndex(String query, int numberOfResults, IndexSearcher indexSearcher) throws Throwable {

		/**
		 * Set weights for our tags
		 */
		Map<String, Float> weights = new HashMap<String, Float>();
		weights.put("title", 0.5f);
		weights.put("body", 2.0f);
		weights.put("date", 0.5f);

		/**
		 * Search for title, body and date
		 */
		String[] searchFields = { "title", "body", "date" };
		MultiFieldQueryParser parser = new MultiFieldQueryParser(searchFields, analyzer, weights);

		/**
		 *  Parse user query
		 */
		Query parsedQuery = parser.parse(query);

		/**
		 * Create search index
		 */
		return indexSearcher.search(parsedQuery, numberOfResults).scoreDocs;
	}

	private static void addFileToList(String directoryName, ArrayList<File> files) {
		File directory = new File(directoryName);

		// List all files and folders in directory
		File[] fileList = directory.listFiles();

		// Recursively add all Html files to our fileList and print it out
		for (File file : fileList) {
			if (file.isFile() && file.getName().endsWith(".html")) {

				System.out.println("Get " + file.getName());
				files.add(file);

			} else if (file.isDirectory()) {
				addFileToList(file.getAbsolutePath(), files);
			}
		}
	}

	private static List<String> tokenStemming(String string) throws Throwable {
		// Use given analyzer to tokenize, stopword eliminate and stem given String
		List<String> output = new ArrayList<String>();
		TokenStream stream = analyzer.tokenStream(null, new StringReader(string));
		CharTermAttribute ctermattr = stream.addAttribute(CharTermAttribute.class);

		stream.reset();

		// Add output to our list as long as there are new entries
		while (stream.incrementToken()) {
			output.add(ctermattr.toString());
		}

		// Close and clean up opened streams
		stream.end();
		stream.close();
		return output;
	}

}