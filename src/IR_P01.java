import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;

import java.io.File;
import java.util.List;

public class IR_P01 {

    private String getRanked() {
        /** TODO
         * Print  a  ranked  list  of  relevant  articles  given  a  search  query.   The  output  should
         * contain 10 most relevant documents with their rank, title and summary, relevance
         * score and path.
         */
        return null;
    }

    private Document getDocument(String path) {
        File doc = new File(path);
        /** TODO
         * Consider the English language and use a stemmer for it (e.g. Porter Stemmer)
         */
        return null;
    }

    private List<String> stemText(String text) {
        // TODO

        return null;
    }

	public static void main(String[] args) {
		String doc_location = "";
		String index_location = "";
		boolean use_vs = false;
		String query = "";

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
		System.out.print((use_vs ? "Vector Space Model ranking algorithm" : "Okapi BM25") + " with query" + query + "\n");

		/**
		 * Stemming
		 */
        EnglishAnalyzer analyzer = new EnglishAnalyzer();
        // TODO

	}
}
