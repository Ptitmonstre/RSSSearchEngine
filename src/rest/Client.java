package rest;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
 
@SuppressWarnings("serial")
public class Client extends HttpServlet {

	private static String indexFolder="./data/indexes";
	private Directory index;
	private StandardAnalyzer analyzer = new StandardAnalyzer();
	private IndexSearcher searcher;
	
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	String req = request.getQueryString();
    	String[] splitted = req.split("&|=");
    	
    	//Si parametre query
    	if(splitted.length>0 && (splitted[0].equals("query"))){
    		
    		//Si parametre flag
    		if(splitted.length > 2 && (splitted[2].equals("flag"))){
    			splitted[1] = splitted[1].replace('_', ' ');
    			splitted[3] = splitted[3].replace('_', ' ');
    			this.query(splitted[1], splitted[3], response);
    		}
    		//Si autre parametre que flag, on ne s'en charge pas
    		else{
    			splitted[1] = splitted[1].replace('_', ' ');
    			this.query(splitted[1], "", response);
    		}
    	}
    	else{
    		response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("<p>"+splitted[0]+"</p>");
            response.getWriter().println("<p>"+splitted[1]+"</p>");
            response.getWriter().println("<p>"+splitted[2]+"</p>");
            response.getWriter().println("<p>"+splitted[3]+"</p>");
            response.getWriter().println("<p>Usage : /search?query=<your_query> or /search?query=<your_query>&flag=<flag></p>");
    	}

    }
    
    public void init(){
    	Path path = Paths.get(indexFolder);
		try {
			index = FSDirectory.open(path);
	    }catch (IndexNotFoundException e) {
			e.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		}	
    }
    
    //Lance la query et traite une reponse
    public void query(String query, String flag, HttpServletResponse response) throws ServletException, IOException{
    	
		try {
			//lancer la classification
			IndexReader reader = DirectoryReader.open(index);
			searcher= new IndexSearcher(reader);
			Query q = null;
			if(query.equals("language:en") || query.equals("language:fr")){
				String language = query.split(":")[1];
				
				QueryParser qp=new QueryParser("", analyzer);
				if(flag == "")
					q =qp.parse("language:\""+language+"\"");
				else
					q =qp.parse("language:\""+language+"\" AND category:\""+flag+"\"");	
			}
			else{
				q =MultiFieldQueryParser.parse(new String[]{query,query,query,query,query} , new String[] {"title","author","content","description","language"}, analyzer);
			}
			
			double nbRes=searcher.count(q);
			
			//ajoute la cat�gorie dans le vecteur des cat�gories possibles
			if(nbRes>0){
				
				TopDocs docs=searcher.search(q, 1000);
				Document document;

				for(ScoreDoc hit : docs.scoreDocs){//pour tout �l�ment d'apprentissage
					//stemming
					document=searcher.doc(hit.doc);
					//document.get("content")
					//document.get("language")
					response.setContentType("text/html");
			        response.setStatus(HttpServletResponse.SC_OK);
			        
			        response.getWriter().println("<div style='width: 80%; margin: 0 auto; margin-bottom: 30px;'>");
			        response.getWriter().println("<p><a href='"+document.get("url_src")+"'>"+document.get("title")+"</a></p>");
			        response.getWriter().println("<span style='margin-right: 30px;'> Auteur : "+document.get("author")+"</span>"+"<span> Date : "+document.get("date")+"</span>");
			        response.getWriter().println("<p>"+document.get("content")+"</p>");
			        response.getWriter().println("</div>");
				}
			}
			
		}catch (IOException | org.apache.lucene.queryparser.classic.ParseException e) {
			System.err.println("Couldn't read the index");
		}

    	response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().println("<p>Requete : "+query+"</p>");
        response.getWriter().println("<p>Categorie : "+flag+"</p>");
        
        //TODO exemple : http://stackoverflow.com/a/5100727
    }
    
}
