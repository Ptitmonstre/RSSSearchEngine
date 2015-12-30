package collector;
 
import java.io.Serializable;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
 
import javax.xml.bind.DatatypeConverter;
 
import org.json.JSONWriter;
 
 
public class MRIEntry implements Serializable{
 
    /**
     *
     */
    private static final long serialVersionUID = 2557689469770990245L;
    private String title;
    private String description;
    private String author;
    private String date;
    private String content;
    private String url_src;
    private String txt_src;
    private String language;
    private String copyright;
    private String hash;
    private String category;
    private MessageDigest md;
 
    public MRIEntry(String title, String description, String content, String author, String date, String url_src, String txt_src,
            String language, String copyright, String category) {
        super();
        this.title = title;
        this.description = description;
        this.author = author;
        this.date = date;
        this.url_src = url_src;
        this.txt_src = txt_src;
        this.language = language;
        this.copyright = copyright;
        this.content=content;
        this.category=category;
        try {
            md = MessageDigest.getInstance("MD5");
            //cryptage en md5. Cle choisie titre + url + langue
            byte[] toBytes = md.digest((title+url_src+language).getBytes());
            hash = DatatypeConverter.printHexBinary(toBytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
 
    public MRIEntry(String hash){
 
    }
 
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getAuthor() {
        return author;
    }
    public void setAuthor(String author) {
        this.author = author;
    }
    public String getDate() {
        return date;
    }
    public void setDate(String date) {
        this.date = date;
    }
    public String getUrl_src() {
        return url_src;
    }
    public void setUrl_src(String url_src) {
        this.url_src = url_src;
    }
    public String getTxt_src() {
        return txt_src;
    }
    public void setTxt_src(String txt_src) {
        this.txt_src = txt_src;
    }
    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }
    public String getCopyright() {
        return copyright;
    }
    public void setCopyright(String copyright) {
        this.copyright = copyright;
    }
    public String getHash() {
        return hash;
    }
    public void setHash(String hash) {
        this.hash = hash;
    }  
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
 
    public String toString(){
        String ret = "";
 
        ret += "{Hash : "+this.getHash()+"}";
        ret += "{Title : "+this.getTitle();
        ret += "\tLanguage : "+this.getLanguage();
        ret += "\tLink : "+this.getUrl_src();
        //      ret += "\tDesc : "+this.getDescription();
        ret += "\tAuthor : "+this.getAuthor();
        ret += "\tText : "+this.getContent();
        ret += "\tSources : "+this.getTxt_src();
        ret += "\tDate : "+this.getDate();
        ret += "\tCopyright : "+this.getCopyright()+"}";
 
        return ret;
    }
 
    public String toMapString(){
        StringWriter string=new StringWriter();
        new JSONWriter(string)
        .object()
        .key("hash").value(hash)
        .key("title").value(this.getTitle())
        .key("language").value(this.getLanguage())
        .key("url_src").value(this.url_src)
        .key("description").value(description)
        .key("author").value(author)
        .key("content").value(content)
        .key("txt_src").value(txt_src)
        .key("date").value(date)
        .key("copyright").value(copyright)
        .endObject();
 
        return string.toString();
    }
 
}