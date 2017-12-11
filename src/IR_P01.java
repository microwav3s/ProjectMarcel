import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.TokenStream;

import org.jsoup.Jsoup;

public class IR_P01 {
	/**
	 * Variables
	 */
	private static EnglishAnalyzer analyzer;

    /**
     * Calculates rankings for the documents and given query according to the selected ranking model.
     * @param query
     * @param results
     * @param indexSearcher
     */
	private static String getRankings(String query, ScoreDoc[] results, IndexSearcher indexSearcher) throws Exception {
        int rank = 1;
		String output = "";

		output += "\nQuery Search Results:\n\n";

		for (ScoreDoc scoredDoc : results) {
			Document currentDoc = indexSearcher.getIndexReader().document(scoredDoc.doc);

			String title = "Title: " + currentDoc.getField("title").stringValue() + "\n";
			String summary = "Summary: " + currentDoc.getField("summary").stringValue() + "\n";
			String relScore = "Relevance Score: " + scoredDoc.score + "\n";
            String path = "Path: " + currentDoc.getField("path").stringValue() + "\n";

			output += rank + ".\n" + title + summary + relScore + path + "\n";
			rank++;
		}

		return output;
	}

    /**
     * Prepares a given document.
     * @param path
     */
	private static Document getDocument(String path) throws Exception {
		File file = new File(path);
		org.jsoup.nodes.Document doc = Jsoup.parse(file, "UTF-8", "");
		Document result = new Document();

        //Prepare text body
        String body = tokenStemming(doc.getElementsByTag("body").text()).toString();

		// Fill new document with crucial fields
		result.add(new TextField("title", doc.getElementsByTag("title").text(), Field.Store.YES));
		result.add(new TextField("summary", doc.getElementsByTag("summary").text(), Field.Store.YES));
		result.add(new TextField("body", body, Field.Store.NO));
		result.add(new TextField("path", path, Field.Store.YES));

		return result;
	}

	public static void main(String[] args) throws Exception {
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
		System.out.println("Taking document data from " + doc_location + ".");
		System.out.println("Using index directory " + index_location + ".");
		System.out.print("Running ");
		System.out.println((use_vs ? "Vector Space Model ranking model" : "Okapi BM25") + " with query '" + query + "'.\n");
		System.out.println("Processing...");

		/**
		 * Initialize Analyzer
		 */
		analyzer = new EnglishAnalyzer();

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

		System.out.println(getRankings(query, result, indexSearcher));
	}

    /**
     * Creates a new search index if none is available and configures searchfields as well as weights. Output as scored Document array.
     * @param query
     * @param numberOfResults
     * @param indexSearcher
     */
	public static ScoreDoc[] createSearchIndex(String query, int numberOfResults, IndexSearcher indexSearcher) throws Exception {

		/**
		 * Set weights for our tags
		 */
		Map<String, Float> weights = new HashMap<String, Float>();
		weights.put("title", 0.5f);
		weights.put("body", 2.0f);

		/**
		 * Search for title, body and date
		 */
		String[] searchFields = { "title", "body"};
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

	/**
	 * Adds all files in a directory to a given list. Supports sub-directories.
	 * @param directoryName
	 * @param files
	 */
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

    /**
     * Runs a stream of tokens to stem the document texts.
     * @param string
     */
	private static List<String> tokenStemming(String string) throws Exception {
		List<String> output = new ArrayList<>();

		// Feed given text into analyzer token stream.
		TokenStream stream = analyzer.tokenStream(null, new StringReader(string));

		stream.reset();

		// Add results to our list as long as there are new entries
		while (stream.incrementToken()) {
			output.add(stream.getAttribute(CharTermAttribute.class).toString());
		}

		stream.end();
		stream.close();
		return output;
	}

}