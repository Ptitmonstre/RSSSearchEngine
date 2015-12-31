package classifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.QueryBuilder;
import org.tartarus.snowball.ext.FrenchStemmer;
import org.tartarus.snowball.ext.PorterStemmer;
import weka.core.converters.ConverterUtils.DataSource;

import weka.classifiers.bayes.NaiveBayes;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;

/**
 * RSS Feed Reader
 * 
 * @author Aubert Gwendal & Scolan Alexis
 *
 */
public class Classifier implements Runnable{

	private boolean classify=false;

	private static String indexFolder="./data/indexes";
	private static String dicFolders="./data/dictionnaries";

	private Directory index;
	private StandardAnalyzer analyzer = new StandardAnalyzer();
	private static IndexWriter writer;
	private IndexSearcher searcher;

	private FrenchStemmer frStemmer;
	private ArrayList<String> frStopList;
	private PorterStemmer enStemmer;
	private ArrayList<String> enStopList;

	private weka.classifiers.Classifier clsEn;
	private weka.classifiers.Classifier clsFr;

	public Classifier(boolean classify, IndexWriter w) {
		writer=w;
		//cr�ation des stemmers
		frStemmer=new FrenchStemmer();
		BufferedReader br;
		frStopList=new ArrayList<>();
		this.classify=classify;
		try {
			br = new BufferedReader(new FileReader(new File("./data/stemming/fr/stop.txt")));
			String word=null;
			while((word=br.readLine())!=null){
				frStopList.add(word);
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		enStemmer=new PorterStemmer();
		enStopList=new ArrayList<>();
		try {
			br = new BufferedReader(new FileReader(new File("./data/stemming/en/stop.txt")));
			String word=null;
			while((word=br.readLine())!=null){
				enStopList.add(word);
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
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
		Option classifyOption=new Option("classify", "Classifies documents if a learning has been executed");

		Options options=new Options();

		options.addOption(indexFolderOption);
		options.addOption(classifyOption);
		CommandLineParser parser = new GnuParser();
		CommandLine commandLine;	

		boolean classify=false;	
		try{
			commandLine=parser.parse(options, args);
			if(commandLine.hasOption("I")){
				indexFolder=commandLine.getOptionValue("I");
			}
			if(commandLine.hasOption("classify")){
				classify=true;
			}
		}catch (ParseException exception){
			System.out.print("Parse error: ");
			System.out.println(exception.getMessage());
		}

		new Thread(new Classifier(classify,null)).run();
	}
	public void run() {
		//Index initialisation
		Path path = Paths.get(indexFolder);
		try {
			index = FSDirectory.open(path);
		}catch (IndexNotFoundException e) {
			e.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		}		

		//charger les dictionnaires
		if(!this.classify){
			try {
				learnFromIndex();
			} catch (IOException e) {}
		}else{
			//charger le dictionnaire
			loadDictionnary();
			//lancer la classification des items
			classifyFromDictionnary();			
		}		
	}

	@SuppressWarnings("unchecked")
	private void learnFromIndex() throws IOException {
		//lire les categories
		List<String> categories = new ArrayList<String>(readCategories());
		//cr�er deux classifiers: en et fr
		clsEn=(weka.classifiers.Classifier) new NaiveBayes();
		clsFr=(weka.classifiers.Classifier) new NaiveBayes();
		try {
			//lancer la classification
			IndexReader reader = DirectoryReader.open(index);
			searcher= new IndexSearcher(reader);

			//pour chaque language construire un classifier
			for(String language : new String[]{"fr","en"}){

				FastVector fvClassVal = new FastVector((categories.size()));
				//cr�er un dictionnaire contenant tous les mots possibles
				HashMap<String,Integer> dictionnary=new HashMap<String,Integer>();
				//cr�er un dictionnaire des mots de chaque document, rang� par num�ro de cat�gorie (index tableau)
				ArrayList<String[]>[] elementsInCategory=new ArrayList[categories.size()];

				System.out.print("Lecture et cr�ation des dictionnaires... ");
				for(String category : categories){
					//pour tout �l�ment avec un champ predicted_category vide
					QueryParser qp=new QueryParser("", analyzer);
					Query q=qp.parse("language:\""+language+"\" AND category:\""+category+"\"");
					double nbRes=searcher.count(q);

					//ajoute la cat�gorie dans le vecteur des cat�gories possibles
					int categoryNumber=categories.indexOf(category);
					if(elementsInCategory[categoryNumber]==null) elementsInCategory[categoryNumber]=new ArrayList<String[]>();
					if(nbRes>0){
						fvClassVal.addElement(category);

						TopDocs docs=searcher.search(q, (int)Math.ceil(nbRes*2/3));
						Document document;

						for(ScoreDoc hit : docs.scoreDocs){//pour tout �l�ment d'apprentissage
							//stemming
							document=searcher.doc(hit.doc);
							String[] stemd=stemming(document.get("content"), document.get("language"));
							//ajouter au dictionnaire g�n�ral
							for(String w:stemd){
								if(dictionnary.containsKey(w)){
									dictionnary.replace(w, dictionnary.get(w)+1);
								}else dictionnary.put(w, 1);
							}
							//ajouter au dictionnaire de la cat�gorie (num�rot�es telles qu'elles sont lues)

							elementsInCategory[categoryNumber].add(stemd);
						}
					}
				} 
				Attribute ClassAttribute = new Attribute("theClass", fvClassVal);
				FastVector fvWekaAttributes = new FastVector(dictionnary.size()+1);
				fvWekaAttributes.addElement(ClassAttribute);
				//cr�ation des attributs (chaque mot possible de l'apprentissage)
				for(String w:dictionnary.keySet()){
					fvWekaAttributes.addElement(new Attribute(w));
				}
				System.out.println("Fin.");

				System.out.print("Apprentissage en cours... ");
				Instances TrainingSet = new Instances(language+"Set", fvWekaAttributes, dictionnary.size()+1);
				TrainingSet.setClassIndex(0);
				//apprentissage pour chaque categorie: ins�rer les mots stemm�s de tous les �l�ments d'apprentissage portant cette cat�gorie
				Instance i;
				int n;
				Attribute a;
				int tf;
				double idf;
				for(String category:categories){
					for(String[] doc:elementsInCategory
							[categories.indexOf(category)]){
						i = new Instance(dictionnary.size()+1);
						i.setValue((Attribute)fvWekaAttributes.elementAt(0), category);
						for(n=0; n<dictionnary.size();n++){
							a=(Attribute)fvWekaAttributes.elementAt(n+1);
							//tf: fr�quence dans le document
							tf=0;
							//idf: logarithme de l'inverse de la proportion de documents du corpus qui contiennent le terme
							idf=(int) Math.log(dictionnary.size()/dictionnary.get(a.name()));
							for(String w:doc){
								if(w.equals(a.name())) tf++;
							}
							if(tf==0) i.setValue(a, 1e-2);
							else i.setValue(a, tf*idf);
						}	 
						TrainingSet.add(i);
					}
				}
				System.out.println("Fin.");

				System.out.print("Sauvegarde des instances... ");
				try {
					//sauvegarder le classifierArffSaver saver = new ArffSaver();
					ArffSaver saver = new ArffSaver();
					saver.setInstances(TrainingSet);
					File newF=new File(dicFolders+"_new_locked/"+language+".arff");
					saver.setFile(newF);
					saver.writeBatch();
					//TODO �valuer la qualit� du classifier avec le tiers de documents restant
				} catch (Exception e) {
					System.err.println("Could not generate the classifier");
				}
				System.out.println("Fin.");
			}
			File newF=new File(dicFolders+"_new");
			//supprimer le dictionnaire courant et renommer le nouveau en dictinnaire courant
			try{
				FileUtils.deleteDirectory(newF);
			}catch(Exception e){
				try{
					FileUtils.deleteDirectory(newF);
				}catch(Exception e1){
					System.err.println("Could not save the instances to _new");
					e1.printStackTrace();
				}
			}
			newF=new File(dicFolders+"_new_locked");
			if(!newF.renameTo(new File(dicFolders+"_new"))) System.err.println("Error swapping the dictionnaries");

			System.out.println("Fin de l'apprentissage");
		}catch (IOException | org.apache.lucene.queryparser.classic.ParseException e) {
			System.err.println("Couldn't read the index");
		}
	}

	private void classifyFromDictionnary() {
		try {
			IndexReader reader = DirectoryReader.open(index);
			searcher= new IndexSearcher(reader);

			Query q=new QueryBuilder(analyzer).createPhraseQuery("predicted_category", "NULL");
			int nbRes=searcher.count(q);
			for(int i=0; i<nbRes; i++){//pour tout �l�ment non classifi�
				//stemming
				//confrontation au dictionnaire
				//r�ins�rer avec les champs de pr�diction
			}			
		} catch (IOException e) {
			System.err.println("Couldn't read the index");
		}
	}
	private void loadDictionnary(){
		//si il existe un dictionnaire nouveau
		if((new File(dicFolders+"_new")).exists()){
			//supprimer le dictionnaire courant et renommer le nouveau en dictionnaire courant
			try{
				FileUtils.deleteDirectory(new File(dicFolders+"_current"));
			}catch(Exception e){}
			if((new File(dicFolders+"_new")).renameTo(new File(dicFolders+"_current"))) System.err.println("Error swapping the dictionnaries");

		}
		clsEn = (weka.classifiers.Classifier)new NaiveBayes();
		clsFr = (weka.classifiers.Classifier)new NaiveBayes();
		//utiliser le dictionnaire courant
		try {
			DataSource source = new DataSource(dicFolders+"_current/en.arff");
			Instances data = source.getDataSet();
			data.setClassIndex(0);
			clsEn.buildClassifier(data);
//			source = new DataSource(dicFolders+"_current/fr.arff");
//			data = source.getDataSet();
//			data.setClassIndex(0);
//			clsFr.buildClassifier(data);
		} catch (Exception e) {
			System.err.println("Error reading the current dictionnary");
			e.printStackTrace();
		}
		System.out.println("Classifiers ready.");
	}

	private String[] stemming(String s, String language){
		ArrayList<String> res=new ArrayList<String>();
		s.replaceAll("[^\\p{L}^\\d\n ]", " ").replaceAll(" +|\n", " ");
		String[] words=s.toLowerCase().split("\\s+");
		for(String w: words){
			if(language.equals("en") && !enStopList.contains(w)){
				enStemmer.setCurrent(w);
				enStemmer.stem();
				res.add(enStemmer.getCurrent());
			}else if(language.equals("fr")&& !frStopList.contains(w)){
				frStemmer.setCurrent(w);
				frStemmer.stem();
				res.add(frStemmer.getCurrent());		
			}else if(!language.equals("fr") && !language.equals("en")){
				System.err.println("Language not supported by stemming");
			}
		}
		for(String w:res){
			w=w.replaceAll("[^\\p{L}^\\d]", "").trim();
		}

		return (String[]) res.toArray(new String[res.size()]);
	}

	private HashSet<String> readCategories() throws IOException{

		try {
			FileInputStream fis=new FileInputStream("./data/LearningList.txt");
			BufferedReader bis=new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));

			String line;
			HashSet<String> lines=new HashSet<String>();
			while ((line = bis.readLine()) != null) {
				lines.add(line.split("\\s+")[0]);
			}
			bis.close();
			return lines;
		} catch (IOException e) {
			System.err.println("Could not find the learning list for the classification");
			throw new IOException(e);
		}
	}
}
