package collector;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.cybozu.labs.langdetect.*;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

import de.l3s.boilerpipe.extractors.ArticleSentencesExtractor;
import org.apache.commons.cli.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.QueryBuilder;
import org.apache.tika.Tika;
import org.apache.tika.io.IOUtils;

/**
 * RSS Feed Reader
 *
 * @author Aubert Gwendal & Scolan Alexis
 *
 */
public class Collector implements Runnable{

	private boolean classify=true;
	private static String rssfeeds="./data/RSSList.txt";
	private static String classifiedfeeds="./data/LearningList.txt";
	private static String indexFolder="./data/indexes";

	private StandardAnalyzer analyzer = new StandardAnalyzer();
	private static IndexWriter writer;
	private IndexSearcher searcher;

	private Directory directory;


	public Collector(boolean classify, IndexWriter w) {
		this.classify=classify;
		writer=w;
	}

	public static void main(String[] args) {

		// Proxy configuration
		/*System.setProperty("http.proxySet", "true");
        System.setProperty("http.proxyHost", "squidva.univ-ubs.fr");
        System.setProperty("http.proxyPort", "3128");
        System.setProperty("http.proxyType", "4");*/

		//parsing des options
		OptionBuilder.withArgName("I");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("The folder where the index is stored");
		Option indexFolderOption=OptionBuilder.create("I");
		OptionBuilder.withArgName("rssfeeds");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("The list of feed to collect from");
		Option optionRssFile=OptionBuilder.create("rssfeeds");
		OptionBuilder.withArgName("classifiedfeeds");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("The list of classified feed to collect from");
		Option optionCategoriesRSS=OptionBuilder.create("classifiedfeeds");
		Option classifyOption=new Option("classify", "Collects classified feeds");

		Options options=new Options();

		options.addOption(optionRssFile);
		options.addOption(optionCategoriesRSS);
		options.addOption(indexFolderOption);
		options.addOption(classifyOption);

		CommandLineParser parser = new GnuParser();
		CommandLine commandLine;   


		boolean classify=false;
		try{
			commandLine=parser.parse(options, args);
			if(commandLine.hasOption("rssfeeds")){
				rssfeeds=commandLine.getOptionValue("rssfeed");
			}
			if(commandLine.hasOption("I")){
				indexFolder=commandLine.getOptionValue("I");
			}
			if(commandLine.hasOption("classify")){
				classify=true;
			}
			if(commandLine.hasOption("classifiedfeeds")){
				classifiedfeeds=commandLine.getOptionValue("classifiedfeeds");
			}
		}catch (ParseException exception){
			System.out.print("Parse error: ");
			System.out.println(exception.getMessage());
		}

		new Thread(new Collector(classify,null)).run();
	}
	public void run() {
		// Setting the path for Langdetector
		try {
			DetectorFactory.loadProfile("./data/langdetect/profiles");
		} catch (LangDetectException e1) {
			e1.printStackTrace();
		}

		//Index initialisation
		Path path = Paths.get(indexFolder);
		try {
			directory = FSDirectory.open(path);
		}catch (IndexNotFoundException e) {
			e.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		}

		// URL du feed
		ArrayList<String> feeds=new ArrayList<String>();
		try {
			String line;
			InputStream fis=null;
			if(classify) fis=new FileInputStream(classifiedfeeds);
			else fis=new FileInputStream(rssfeeds);

			InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
			BufferedReader br = new BufferedReader(isr);
			while ((line = br.readLine()) != null) {
				feeds.add(line);
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		indexFeeds(feeds);
		System.out.println("Done collecting.");
	}
	private void indexFeeds(ArrayList<String> feedURLs) {
		for(String line : feedURLs){
			String category=null;
			if(classify){
				String[] splitted=line.split("\\s+");
				category=splitted[0];
				line=splitted[1];
			}
			URLConnection feedUrl=null;
			try {
				feedUrl= new URL(line).openConnection();
				feedUrl.setReadTimeout(1000);
				feedUrl.setConnectTimeout(1000);
				SyndFeedInput input = new SyndFeedInput();
				SyndFeed feed = input.build(new XmlReader(feedUrl));
				@SuppressWarnings("unchecked")
				List<SyndEntry> entries = feed.getEntries();
				for(SyndEntry e:entries){

					String title ="", description="", author="", txtcontent="", date="", url_src = "", txt_src= "", language = "", copyright="";

					//URL Source
					url_src = e.getLink();
					//Titre de l'article
					title = e.getTitle();
					//Date de l'article
					if(e.getPublishedDate()!=null) date = e.getPublishedDate().toString();
					//Description de l'article
					if(e.getDescription()!=null){
						description = e.getDescription().toString();
					}
					if(e.getSource()!=null){
						txt_src = e.getSource().toString();
					}
					if(category==null) category="";
					MRIEntry entry = new MRIEntry(title, description, txtcontent, author, date, url_src, txt_src, language, copyright, category);  
					if(!isPresentIndex(entry.getHash())){
						Tika tika=new Tika();
						try {
							URL url = new URL(e.getLink());
							if(! tika.detect(url).contains("html")){
								txtcontent=tika.parseToString(url);
							}else{

								HttpURLConnection connection = (HttpURLConnection)url.openConnection();
								connection.setReadTimeout(1000);
								connection.setConnectTimeout(1000);
								txtcontent=connection.getResponseMessage();
								StringWriter wr=new StringWriter();
								IOUtils.copy(connection.getInputStream(), wr, "utf-8");
								txtcontent=wr.toString();
								//Contenu de la page
								txtcontent = ArticleSentencesExtractor.INSTANCE.getText(txtcontent).replaceAll("[^\\p{L}^\\d\n ]", " ").replaceAll(" +|\n", " ");                       
							}
						}catch (UnknownHostException e2) {
							System.err.println("Unknown host: couldn't read "+e.getLink());
						}catch (Exception e2) {
							System.err.println("Connection timed out: couldn't read "+e.getLink());
						}
						entry.setContent(txtcontent);

						try {
							Detector detector = DetectorFactory.create();
							detector.append(e.getSource()+" "+e.getTitle()+" "+e.getDescription()+" "+txtcontent);
							//Langage de l'article
							language = detector.detect();
						} catch (LangDetectException e1) {
							System.err.println("Couldnt detect the language");
						}
						entry.setLanguage(language);

						//Ajout � l'index
						addToIndex(entry);
					}
				}
			}catch (UnknownHostException e2) {
				System.err.println("Unknown host: couldn't read "+feedUrl.getURL().toString());
			}catch (Exception e) {
				System.err.println("Invalid RSS or RSS Content:");
				e.printStackTrace();
			}
		}
	}

	private void addToIndex(MRIEntry obj){
		try {

			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			if(writer==null) writer = new IndexWriter(directory, config);
			Document doc = new Document();
			doc.add(new TextField("hash", obj.getHash(), Field.Store.YES));
			doc.add(new TextField("title", obj.getTitle(), Field.Store.YES));
			doc.add(new TextField("description", obj.getDescription(), Field.Store.YES));
			doc.add(new TextField("author", obj.getAuthor(), Field.Store.YES));
			doc.add(new TextField("content", obj.getContent(), Field.Store.YES));
			doc.add(new TextField("date", obj.getDate(), Field.Store.YES));
			doc.add(new TextField("url_src", obj.getUrl_src(), Field.Store.YES));
			doc.add(new TextField("txt_src", obj.getTxt_src(), Field.Store.YES));
			doc.add(new TextField("language", obj.getLanguage(), Field.Store.YES));
			doc.add(new TextField("copyright", obj.getCopyright(), Field.Store.YES));
			doc.add(new TextField("category", obj.getCategory(), Field.Store.YES));
			writer.prepareCommit();
			writer.addDocument(doc);
			if(!obj.getCategory().equals("")){
				System.out.println("Added: " + obj.getHash()+" Category: "+obj.getCategory());
			}else System.out.println("Added: " + obj.getHash());
			writer.commit();           
		} catch (Exception e) {
			System.out.println("Could not add: " + obj.getHash());
			e.printStackTrace();
		}
	}

	private boolean isPresentIndex(String md5){    
		Query q = new QueryBuilder(analyzer).createPhraseQuery("hash", md5);

		try {
			IndexReader reader = DirectoryReader.open(directory);
			searcher= new IndexSearcher(reader);
		} catch (IOException e1) {
			return false;
		}
		try {
			if((searcher.search(q,1)).scoreDocs.length <1){return false;}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}
}