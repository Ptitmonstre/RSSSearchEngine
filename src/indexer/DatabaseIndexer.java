package indexer;

// Reproduced from http://www.lucenetutorial.com/code/TextFileIndexer.java
// Lucene V 5.3.1

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONObject;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.*;
import java.util.ArrayList;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentNavigableMap;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This terminal application creates an Apache Lucene index in a folder and adds files into this index
 * based on the input of the user.
 */

public class DatabaseIndexer {
	private static StandardAnalyzer analyzer = new StandardAnalyzer();

	/**
	 * Database
	 */
	private static DB db;
	/**
	 * Map DB
	 */
	private IndexWriter writer;
	private ArrayList<File> queue = new ArrayList<File>();

	private static ConcurrentNavigableMap<String, String> treeMap;

	public static void main(String[] args) throws IOException {
		if(args.length<2){
			System.out.println("Add a parameter the path to the database, and the path to the index which will get created ");
			System.exit(0);
		}

		DatabaseIndexer indexer = null;
		try {
			indexer = new DatabaseIndexer(args[1]);
		} catch (Exception ex) {
			System.out.println("Cannot create index..." + ex.getMessage());
			System.exit(-1);
		}

		//===================================================
		//read input from user until he enters q for quit
		//===================================================


		indexer.indexDatabase(args[0]);

		//===================================================
		//after adding, we always have to call the
		//closeIndex, otherwise the index is not created    
		//===================================================
		indexer.closeIndex();

		//=========================================================
		// Now search
		//=========================================================
		Path path = Paths.get(args[1]);
		Directory directory = FSDirectory.open(path);
		IndexReader indexReader =  DirectoryReader.open(directory);
		IndexSearcher searcher = new IndexSearcher(indexReader);
		TopScoreDocCollector collector;

		String s = "";
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		while (!s.equalsIgnoreCase("q")) {
			try {
				System.out.println("Enter the search query (q=quit):");
				s = br.readLine();
				System.out.println("Q="+s);
				if (s.equalsIgnoreCase("q")) {
					break;
				}
				collector = TopScoreDocCollector.create(20);
				Query q = new MultiFieldQueryParser(new String[]{"content","description","title","date","author","language"}, analyzer).parse(s);
				searcher.search(q, collector);
				ScoreDoc[] hits = collector.topDocs().scoreDocs;

				// 4. display results
				System.out.println("Found " + hits.length + " hits.");
				for(int i=0;i<hits.length;++i) {
					int docId = hits[i].doc;
					Document d = searcher.doc(docId);
					System.out.println((i + 1) + ". " + d.get("hash") + " score=" + hits[i].score);
				}
				s="";

			} catch (Exception e) {
				System.out.println("Error searching " + s + " : " + e.getMessage());
			}
		}

	}


	/**
	 * Constructor
	 * @param indexDir the name of the folder in which the index should be created
	 * @throws java.io.IOException when exception creating index.
	 */
	DatabaseIndexer(String indexDir) throws IOException {
		// the boolean true parameter means to create a new index everytime, 
		// potentially overwriting any existing files there.
		Path path = Paths.get(indexDir);
		Directory directory = FSDirectory.open(path);


		IndexWriterConfig config = new IndexWriterConfig(analyzer);

		this.writer = new IndexWriter(directory, config);
	}

	/**
	 * Indexes a database
	 * @param fileName the name of a text file or a folder we wish to add to the index
	 * @throws java.io.IOException when exception
	 */
	private void indexDatabase(String fileName) throws IOException {
		//===================================================
		//gets the list of files in a folder (if user has submitted
		//the name of a folder) or gets a single file name (is user
		//has submitted only the file name) 
		//===================================================

		int originalNumDocs = writer.numDocs();

		db = DBMaker.newFileDB(new File( fileName)).make();
		treeMap = db.getTreeMap("map");
		
		NavigableSet<String> keys= treeMap.navigableKeySet();
		for (String k : keys) {
			JSONObject obj = new JSONObject(treeMap.get(k));
			try {
				Document doc = new Document();

				//===================================================
				// add contents of file
				//===================================================
				
				for(String item: JSONObject.getNames(obj)){
					
					doc.add(new TextField(item, obj.getString(item), Field.Store.YES));
//					doc.add(new StringField("path", f.getPath(), Field.Store.YES));
//					doc.add(new StringField("filename", f.getName(), Field.Store.YES));					
				}
				

				writer.addDocument(doc);
				System.out.println("Added: " + obj.get("hash"));
			} catch (Exception e) {
				System.out.println("Could not add: " + obj.get("hash"));
			}
		}

		int newNumDocs = writer.numDocs();
		System.out.println("");
		System.out.println("************************");
		System.out.println((newNumDocs - originalNumDocs) + " documents added.");
		System.out.println("************************");

		queue.clear();

	}

	/**
	 * Close the index.
	 * @throws java.io.IOException when exception closing
	 */
	public void closeIndex() throws IOException {
		writer.close();
	}
}

