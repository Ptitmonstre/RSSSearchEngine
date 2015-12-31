package classifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.QueryBuilder;
import org.tartarus.snowball.ext.FrenchStemmer;
import org.tartarus.snowball.ext.PorterStemmer;

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
		//création des stemmers
		frStemmer=new FrenchStemmer();
		BufferedReader br;
		frStopList=new ArrayList<>();
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
				//				incrémenter et sauvegarder les stamps des categories qui ont été mises à jour
				//				saveStamps();
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
		//créer deux classifiers: en et fr
		clsEn=(weka.classifiers.Classifier) new NaiveBayes();
		clsFr=(weka.classifiers.Classifier) new NaiveBayes();
		try {
			//lancer la classification
			IndexReader reader = DirectoryReader.open(index);
			searcher= new IndexSearcher(reader);

			//pour chaque language construire un classifier
			for(String language : new String[]{"fr","en"}){

				FastVector fvClassVal = new FastVector((categories.size()));
				//créer un dictionnaire contenant tous les mots possibles
				HashMap<String,Integer> dictionnary=new HashMap<String,Integer>();
				//créer un dictionnaire des mots de chaque document, rangé par numéro de catégorie (index tableau)
				ArrayList<String[]>[] elementsInCategory=new ArrayList[categories.size()];

				System.out.print("Lecture et création des dictionnaires... ");
				for(String category : categories){
					//pour tout élément avec un champ predicted_category vide
					Query q=MultiFieldQueryParser.parse(new String[]{language} ,new String[]{"language"}, analyzer);
					double nbRes=searcher.count(q);

					//ajoute la catégorie dans le vecteur des catégories possibles
					if(nbRes>0){
						fvClassVal.addElement(category);

						TopDocs docs=searcher.search(q, (int)Math.ceil(nbRes*2/3));
						Document document;

						for(ScoreDoc hit : docs.scoreDocs){//pour tout élément d'apprentissage
							//stemming
							document=searcher.doc(hit.doc);
							String[] stemd=stemming(document.get("content"), document.get("language"));
							//ajouter au dictionnaire général
							for(String w:stemd){
								if(dictionnary.containsKey(w)){
									dictionnary.replace(w, dictionnary.get(w)+1);
								}else dictionnary.put(w, 1);
							}
							//ajouter au dictionnaire de la catégorie (numérotées telles qu'elles sont lues)

							int categoryNumber=categories.indexOf(category);
							if(elementsInCategory[categoryNumber]==null) elementsInCategory[categoryNumber]=new ArrayList<String[]>();
							elementsInCategory[categoryNumber].add(stemd);
						}
					}
				} 
				Attribute ClassAttribute = new Attribute("theClass", fvClassVal);
				FastVector fvWekaAttributes = new FastVector(dictionnary.size()+1);
				fvWekaAttributes.addElement(ClassAttribute);
				//création des attributs (chaque mot possible de l'apprentissage)
				for(String w:dictionnary.keySet()){
					fvWekaAttributes.addElement(new Attribute(w));
				}
				System.out.println("Fin.");

				System.out.print("Apprentissage en cours... ");
				Instances TrainingSet = new Instances(language+"Set", fvWekaAttributes, dictionnary.size()+1);
				TrainingSet.setClassIndex(0);
				//apprentissage pour chaque categorie: insérer les mots stemmés de tous les éléments d'apprentissage portant cette catégorie
				Instance i;
				int n;
				Attribute a;
				int tf;
				double idf;
				for(String category:categories){
					for(String[] doc:elementsInCategory[categories.indexOf(category)]){
						i = new Instance(dictionnary.size()+1);
						i.setValue((Attribute)fvWekaAttributes.elementAt(0), category);
						for(n=0; n<dictionnary.size();n++){
							a=(Attribute)fvWekaAttributes.elementAt(n+1);
							//tf: fréquence dans le document
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
					//TODO évaluer la qualité du classifier avec le tiers de documents restant
				} catch (Exception e) {
					System.err.println("Could not generate the classifier");
				}
				System.out.println("Fin.");
			}
			File newF=new File(dicFolders+"_new_locked");
			if((newF=new File(dicFolders+"_new")).exists()){
				//supprimer le dictionnaire courant et renommer le nouveau en dictinnaire courant
				try{
					(new File(dicFolders+"_new")).delete();
				}catch(Exception e){
					try {
						Thread.currentThread().wait(300);
						(new File(dicFolders+"_new")).delete();
					} catch (Exception e1) {
						System.err.println("Could not save the instances to _new");
						e1.printStackTrace();
					}
				}
				if(!newF.renameTo(new File(dicFolders+"_new"))) System.err.println("Error swapping the dictionnaries");

			}
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
			for(int i=0; i<nbRes*2/3; i++){//pour tout élément non classifié
				//stemming
				//confrontation au dictionnaire
				//réinsérer avec les champs de prédiction
			}			
		} catch (IOException e) {
			System.err.println("Couldn't read the index");
		}
	}
	private void loadDictionnary(){
		//si il existe un dictionnaire nouveau
		File newF;
		if((newF=new File(dicFolders+"_new")).exists()){
			//supprimer le dictionnaire courant et renommer le nouveau en dictinnaire courant
			try{
				(new File(dicFolders+"_current")).delete();
			}catch(Exception e){}
			if(newF.renameTo(new File(dicFolders+"_current"))) System.err.println("Error swapping the dictionnaries");

		}
		//utiliser le dictionnaire courant
		try {
			clsEn = (weka.classifiers.Classifier) weka.core.SerializationHelper.read(dicFolders+"_current/dictionnaryEn.model");
			clsFr = (weka.classifiers.Classifier) weka.core.SerializationHelper.read(dicFolders+"_current/dictionnaryFr.model");
		} catch (Exception e) {
			System.err.println("Error reading the current dictionnary");
		}
	}

	private String[] stemming(String s, String language){
		ArrayList<String> res=new ArrayList<String>();
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
