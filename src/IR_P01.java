import java.io.File;
import java.io.IOException;
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

	/*
	 * Fields
	 */
	private Directory directory;
	private DirectoryReader directoryReader;
	private Analyzer analyzer;

	private IndexSearcher indexSearcher;
	private String[] searchFields = { "title", "body", "date" };
	private Map<String, Float> weights;
	private Float titleWeight = 0.5f;
	private Float bodyWeight = 2.0f;
	private Float dateWeight = 0.5f;

	private String rankingMode = "OK";

	public static void main(String[] args) {
		/*
		 * Welcome Screen
		 */
		System.out.println("IR_P01 running.");
		System.out.println("");

	}

	public IR_P01(String pathToDocuments) throws Throwable {
		Path path = Paths.get(pathToDocuments);
		directory = FSDirectory.open(path);

		if (!DirectoryReader.indexExists(directory)) {
			// Open IndexWriter with standard config
			IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);

			// Set similarities based on Command Line
			if (rankingMode == "OK") {
				BM25Similarity similarity = new BM25Similarity();
				writerConfig.setSimilarity(similarity);
			} else {
				// VM is default?
			}

			IndexWriter indWriter = new IndexWriter(directory, writerConfig);

			// Create Document list to feed into our index writer
			ArrayList<File> documents = new ArrayList<File>();
			addFileToList(pathToDocuments, documents);

			System.out.println("Indexing files...");
			for (File file : documents) {
				indWriter.addDocument(getDocumentFromPath(file.getAbsolutePath()));
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

	}

	private static void addFileToList(String directoryName, ArrayList<File> files) {
		File directory = new File(directoryName);

		// List all files and folders in directory
		File[] fileList = directory.listFiles();

		// Reursively add all Html files to our fileList and print it out
		for (File file : fileList) {
			if (file.isFile() && file.getName().endsWith(".html")) {
				
				System.out.println("Get " + file.getName());
				files.add(file);
				
			} else if (file.isDirectory()) {
				addFileToList(file.getAbsolutePath(), files);
			}
		}
	}

	private Document getDocumentFromPath(String path) throws Throwable {
		// Read and get raw text from .html file
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
		String processedBody = this.tokenizeString(body.text()).toString();

		// Create Lucene Document and add required Fields, only store title and date in index
		Document lucDoc = new Document();

		lucDoc.add(new TextField("title", title.text(), Field.Store.YES));
		lucDoc.add(new TextField("date", date, Field.Store.YES));
		lucDoc.add(new TextField("summary", summary.text(), Field.Store.YES));
		lucDoc.add(new TextField("body", processedBody, Field.Store.NO));

		lucDoc.add(new TextField("path", path, Field.Store.YES));

		return lucDoc;
	}
	
	private Document getDocumentById(int Id) throws Throwable {
		IndexReader indReader = indexSearcher.getIndexReader();
		return indReader.document(Id);
	}

	private List<String> tokenizeString(String string) throws Throwable {
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

	public ScoreDoc[] createSearchIndex(String query, int numberOfResults) throws Throwable {

		// Create new MultiFieldQueryParser with chosen search fields and weights
		MultiFieldQueryParser parser = new MultiFieldQueryParser(searchFields, analyzer, weights);

		// Parse user query
		Query parsedQuery = parser.parse(query);

		return indexSearcher.search(parsedQuery, numberOfResults).scoreDocs;
	}

	private void printResults(String query, ScoreDoc[] resultDocuments) throws Throwable {
		
		// Create Output string
		String output = "";
		int rank = 1;
		
		output += "Search results for \"" + query + "\"\n";
		// For all search results, add their String representation to the output
		for (ScoreDoc scoredDocument : resultDocuments) {
			Document currentDocument = getDocumentById(scoredDocument.doc);
			
			String docHead = rank + ":\t" + currentDocument.getField("title").stringValue();
			String docPath = "Path:" + currentDocument.getField("path").stringValue();
			String docSummary = "Summary: " + currentDocument.getField("summary").stringValue();
			
			output += docHead + "\n" + docPath + "\n" + docSummary + "\n";
			rank++;
		}
		
		System.out.println(output);
	}
}