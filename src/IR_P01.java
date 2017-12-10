import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.*;
import org.jsoup.select.Elements;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
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

	/**
	 * Fields
	 */
	private static Directory directory;
	private static DirectoryReader directoryReader;
	private static Analyzer analyzer;

	private static IndexSearcher indexSearcher;
	private static ScoreDoc[] resultDocuments;
	private static String[] searchFields = { "title", "body", "date" };
	private static String query;
	private static Map<String, Float> weights;
	private static Float titleWeight = 0.5f;
	private static Float bodyWeight = 2.0f;
	private static Float dateWeight = 0.5f;

	private static String getRanked() throws Exception {
		/** TODO
		 * Print  a  ranked  list  of  relevant  articles  given  a  search  query.   The  output  should
		 * contain 10 most relevant documents with their rank, title and summary, relevance
		 * score and path.
		 */
		String output = "";
		int rank = 1;

		output += "Search results for \"" + query + "\"\n";
		// For all search results, add their String representation to the output
		for (ScoreDoc scoredDocument : resultDocuments) {
			Document currentDocument = getDocument(scoredDocument.doc);

			String docHead = rank + ":\t" + currentDocument.getField("title").stringValue();
			String docPath = "Path:" + currentDocument.getField("path").stringValue();
			String docSummary = "Summary: " + currentDocument.getField("summary").stringValue();

			output += docHead + "\n" + docPath + "\n" + docSummary + "\n";
			rank++;
		}

		return output;
	}

	private static Document getDocument(String path) throws Exception {
		/** TODO
		 * Consider the English language and use a stemmer for it (e.g. Porter Stemmer)
		 */

	File input = new File(path);
	org.jsoup.nodes.Document doc = Jsoup.parse(input, "UTF-8", "");

	// Create body, title and date tags
	Elements body = doc.getElementsByTag("body");
	Elements title = doc.getElementsByTag("title");
	Elements summary = doc.getElementsByTag("summary");
	String date = "";

	// Search for document date
		for (org.jsoup.nodes.Element meta : doc.select("meta")) {
		if (meta.attr("name").equals("date"))
			date = meta.attr("content");
	}

	// Tokenize, stopword eliminate and stem document body
	String processedBody = tokenizeString(body.text()).toString();

	// Create Lucene Document and add required Fields, only store title and date in index
	Document lucDoc = new Document();

		lucDoc.add(new TextField("title", title.text(), Field.Store.YES));
		lucDoc.add(new TextField("date", date, Field.Store.YES));
		lucDoc.add(new TextField("summary", summary.text(), Field.Store.YES));
		lucDoc.add(new TextField("body", processedBody, Field.Store.NO));

		lucDoc.add(new TextField("path", path, Field.Store.YES));

		return lucDoc;
	}

	private static Document getDocument(int Id) throws Exception {
		IndexReader indReader = indexSearcher.getIndexReader();
		return indReader.document(Id);
	}

	private List<String> stemText(String text) {
		// TODO

		return null;
	}

	public static void main(String[] args) throws Exception {
		String doc_location = "";
		String index_location = "";
		boolean use_vs = false;

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
		 * Initialize index if no index is found
		 */
		Path path = Paths.get(index_location);
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

		// Set standard weights
		weights = new HashMap<String, Float>();
		weights.put("title", titleWeight);
		weights.put("body", bodyWeight);
		weights.put("date", dateWeight);


		resultDocuments = createSearchIndex(query, 10);

		System.out.println(getRanked());
	}

	public static ScoreDoc[] createSearchIndex(String query, int numberOfResults) throws Exception {

		// Create new MultiFieldQueryParser with chosen search fields and weights
		MultiFieldQueryParser parser = new MultiFieldQueryParser(searchFields, analyzer, weights);

		// Parse user query
		Query parsedQuery = parser.parse(query);

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

	private static List<String> tokenizeString(String string) throws Exception {
		// Use given analyzer to tokenize, stopword eliminate and stem given String
		List<String> result = new ArrayList<String>();
		TokenStream stream = analyzer.tokenStream(null, new StringReader(string));

		stream.reset();

		// Add results to our list as long as there are new entries
		while (stream.incrementToken()) {
			result.add(stream.getAttribute(CharTermAttribute.class).toString());
		}

		// Close and clean up opened streams
		stream.end();
		stream.close();
		return result;
	}

}