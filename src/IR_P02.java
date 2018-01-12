import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.TokenStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class IR_P02 {
	/**
	 * Variables
	 */
	private static EnglishAnalyzer analyzer;
	private static String index_location;
	private static String outputFileName = "pages.txt";

	/**
	 * Calculates rankings for the documents and given query according to the
	 * selected ranking model.
	 * 
	 * @param query
	 * @param results
	 * @param indexSearcher
	 */
	private static String getRankings(String query, ScoreDoc[] results, IndexSearcher indexSearcher) throws Exception {
		int rank = 1;
		String output = "";

		output += "\nSearch Results for \"" + query + "\":\n\n";

		for (ScoreDoc scoredDoc : results) {
			Document currentDoc = indexSearcher.getIndexReader().document(scoredDoc.doc);

			String title = currentDoc.getField("title").stringValue() + "\n";
			String relScore = "Relevance Score: " + scoredDoc.score + "\n";
			String path = "Url: " + currentDoc.getField("url").stringValue() + "\n";

			output += rank + "." + title + relScore + path + "\n";
			rank++;
		}

		return output;
	}

	/**
	 * Prepares a given document.
	 * 
	 * @param path
	 */
	private static Document getDocument(String url) throws Exception {
		org.jsoup.nodes.Document doc = Jsoup.connect(url).get();

		Document result = new Document();

		// Prepare text body
		String body = tokenStemming(doc.getElementsByTag("body").text()).toString();

		// Fill new document with crucial fields
		result.add(new TextField("title", doc.getElementsByTag("title").text(), Field.Store.YES));
		result.add(new TextField("summary", doc.getElementsByTag("summary").text(), Field.Store.YES));
		result.add(new TextField("body", body, Field.Store.NO));
		result.add(new TextField("url", url, Field.Store.YES));

		return result;
	}

	public static void main(String[] args) throws Exception {
		String url;
		String query;
		int crawlDepth;
		Path path;
		Directory directory;
		DirectoryReader directoryReader;
		IndexSearcher indexSearcher;

		/**
		 * Argument collection
		 */
		try {
			url = args[0];
			crawlDepth = Integer.parseInt(args[1]);
			index_location = args[2];
			query = args[3];

		} catch (NumberFormatException nexception) {
			System.err
					.print("Necessary arguments are invalid.\nPlease run the program again with the following syntax: "
							+ "java -jar IR_P01.jar [seed url] [crawling depth] [path to lucene index] [query]\n");
			return;
		} catch (ArrayIndexOutOfBoundsException exception) {
			System.err.print(
					"Necessary arguments not detected.\n Please run the program again with the following syntax: "
							+ "java -jar IR_P01.jar [seed url] [crawling depth] [path to lucene index] [query]\n");
			return;
		}

		/**
		 * Welcome Screen
		 */
		System.out.println("IR_P02 running.\n");
		System.out.println("Seed URL is " + url);
		System.out.println("Using index directory " + index_location);

		/**
		 * Initialize Analyzer
		 */
		analyzer = new EnglishAnalyzer();

		/**
		 * Initialize index if none is found
		 */
		path = Paths.get(index_location);
		directory = FSDirectory.open(path);

		/**
		 * Check for output file and remove it, a new one will be generated
		 */
		File linksFile = new File(index_location + File.separator + outputFileName);
		if (linksFile.exists()) {
			linksFile.delete();
		}

		/**
		 * Check for search index. If it doesn't exist, create a new one and crawl from
		 */
		if (!DirectoryReader.indexExists(directory)) {

			IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
			IndexWriter indWriter = new IndexWriter(directory, writerConfig);

			/**
			 * Create Document list to feed into our index writer
			 */
			ArrayList<String> documents = new ArrayList<String>();

			addDocumentsFromUrl(url, documents, crawlDepth, 0);

			System.out.println("Begin crawling...");
			for (String s : documents) {
				try {
					indWriter.addDocument(getDocument(s));
				} catch (Exception e) {
					continue;
				}
			}

			indWriter.close();
			System.out.println("Crawling done");
		}

		directoryReader = DirectoryReader.open(directory);
		indexSearcher = new IndexSearcher(directoryReader);

		ScoreDoc[] result = createSearchIndex(query, 10, indexSearcher);

		System.out.println(getRankings(query, result, indexSearcher));
	}

	/**
	 * Creates a new search index if none is available and configures searchfields
	 * as well as weights. Output as scored Document array.
	 * 
	 * @param query
	 * @param numberOfResults
	 * @param indexSearcher
	 */
	public static ScoreDoc[] createSearchIndex(String query, int numberOfResults, IndexSearcher indexSearcher)
			throws Exception {

		/**
		 * Set weights for our tags
		 */
		Map<String, Float> weights = new HashMap<String, Float>();
		weights.put("title", 0.5f);
		weights.put("body", 2.0f);

		/**
		 * Search for title, body and date
		 */
		String[] searchFields = { "title", "body" };
		MultiFieldQueryParser parser = new MultiFieldQueryParser(searchFields, analyzer, weights);

		/**
		 * Parse user query
		 */
		Query parsedQuery = parser.parse(query);

		/**
		 * Create search index
		 */
		return indexSearcher.search(parsedQuery, numberOfResults).scoreDocs;
	}

	/**
	 * Adds every link found in <em>Uri<em> into the <em>documents<em> list
	 * 
	 * @param Uri
	 *            URI to search every link in
	 * @param documents
	 *            List of found links
	 * @param depth
	 *            Maximum crawling level
	 * @param currentDepth
	 *            Current crawling level
	 * @throws Exception
	 */
	private static void addDocumentsFromUrl(String Uri, ArrayList<String> documents, int depth, int currentDepth)
			throws Exception {

		/**
		 * Stop when maximum depth is reached
		 */
		if (currentDepth >= depth) {
			return;
		}

		/**
		 * Parse the links from website
		 */
		ArrayList<String> links = parseLinksFromDocument(Uri.toLowerCase());

		for (String s : links) {
			/**
			 * Normalize and check validity of link
			 */
			if (!documents.contains(s)) {
				documents.add(s);
				writeToOutput(s, currentDepth);
			}

			/**
			 * Recursively repeat for every url found
			 */
			addDocumentsFromUrl(s, documents, depth, currentDepth + 1);
		}

	}

	/**
	 * 
	 * Writes link to output file, Create file if it doesn't exist
	 * 
	 * @param Url
	 *            Current URL to write
	 * @param level
	 *            Current level to write
	 */
	private static void writeToOutput(String Url, int level) {
		FileWriter writer;

		try {
			String fullpath = index_location + File.separator + outputFileName;
			String line = Url + "\t" + level + "\n";

			writer = new FileWriter(fullpath, true);
			writer.write(line);
			writer.close();
		} catch (Exception ex) {

		}

	}

	/**
	 * 
	 * Retrieves a list of every link found in <em>url</em>
	 * 
	 * @param url URL to look for links in
	 * @return List of every <em>&lt;a&gt;</em> element found in webpage
	 * @throws Exception
	 */
	private static ArrayList<String> parseLinksFromDocument(String url) throws Exception {

		ArrayList<String> result = new ArrayList<String>();

		org.jsoup.nodes.Document doc;
		try {
			doc = Jsoup.connect(url).get();
		} catch (Exception e) {
			return result;
		}

		ArrayList<Element> links = doc.select("a");

		for (Element el : links) {
			/**
			 * Add absolute links to our result list
			 */
			String l = el.attr("abs:href");

			/**
			 * remove empty lines (artifact by implementation)
			 */
			if (l == "")
				continue;

			/**
			 * Check for anchor and remove it
			 */
			if (l.contains("#")) {
				l = l.substring(0, l.indexOf("#"));
			}

			// Check for trailing slash
			/**
			 * ToDo
			 */

			/**
			 * Check, if it is in our list already and add it otherwise
			 */
			if (!result.contains(l))
				result.add(l);
		}

		return result;
	}

	/**
	 * Runs a stream of tokens to stem the document texts.
	 * 
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