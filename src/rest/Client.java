package rest;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
 
@SuppressWarnings("serial")
public class Client extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	String req = request.getQueryString();
    	String[] splitted = req.split("&|=");
    	
    	//Si parametre query
    	if(splitted.length>0 && (splitted[0].equals("query"))){
    		
    		//Si parametre flag
    		if(splitted.length > 2 && (splitted[2].equals("flag"))){
    			this.query(splitted[1], splitted[3], response);
    		}
    		//Si autre parametre que flag, on ne s'en charge pas
    		else{
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
    
    //Lance la query et traite une reponse
    public void query(String q, String flag, HttpServletResponse response) throws ServletException, IOException{
    	response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().println("<p>Requete : "+q+"</p>");
        response.getWriter().println("<p>Categorie : "+flag+"</p>");
        
        //TODO exemple : http://stackoverflow.com/a/5100727
    }
    
}
