package com.example.search;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.*;

/** Local-first Word document search website. */
public class SearchEngineServer {
    private static final Path DOCS=Path.of("data","documents");
    private static final Path STATIC=Path.of("src","main","resources","static").toAbsolutePath().normalize();
    private static final SearchIndex INDEX=new SearchIndex();
    private static final int PORT=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
    private static final long MAX_FILE=50L*1024*1024;

    public static void main(String[] args)throws Exception{
        Files.createDirectories(DOCS);
        try(var files=Files.list(DOCS)){files.filter(Files::isRegularFile).filter(SearchEngineServer::isWordFile).forEach(p->{try{INDEX.add(p.getFileName().toString(),extract(p));}catch(Exception e){System.err.println("Could not index "+p.getFileName()+": "+e.getMessage());}});}
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",PORT),0);server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/health",x->send(x,200,"{\"status\":\"ok\",\"documents\":"+INDEX.count()+"}","application/json"));server.createContext("/api/search",SearchEngineServer::search);server.createContext("/api/upload",SearchEngineServer::upload);server.createContext("/api/documents",SearchEngineServer::documents);server.createContext("/",SearchEngineServer::staticFiles);server.start();
        System.out.println("WordVault is running at http://localhost:"+PORT);System.out.println("Documents are stored in: "+DOCS.toAbsolutePath());
    }
    private static String extract(Path p)throws Exception{String n=p.getFileName().toString().toLowerCase(Locale.ROOT);try(InputStream in=Files.newInputStream(p)){if(n.endsWith(".docx")){try(XWPFDocument d=new XWPFDocument(in);XWPFWordExtractor x=new XWPFWordExtractor(d)){return x.getText();}}if(n.endsWith(".doc")){try(WordExtractor x=new WordExtractor(in)){return x.getText();}}throw new IOException("Only .doc and .docx files are supported.");}}
    private static boolean isWordFile(Path p){String n=p.getFileName().toString().toLowerCase(Locale.ROOT);return n.endsWith(".doc")||n.endsWith(".docx");}
    private static void search(HttpExchange x)throws IOException{if(!x.getRequestMethod().equals("GET")){send(x,405,"{\"error\":\"GET required\"}","application/json");return;}Map<String,String> q=query(x.getRequestURI().getRawQuery());send(x,200,INDEX.search(q.getOrDefault("q",""),q.getOrDefault("mode","keyword")),"application/json");}
    private static void upload(HttpExchange x)throws IOException{
        if(!x.getRequestMethod().equals("POST")){send(x,405,"{\"error\":\"POST required\"}","application/json");return;}
        Map<String,String> q=query(x.getRequestURI().getRawQuery());String name=Path.of(q.getOrDefault("name","document.docx")).getFileName().toString().replaceAll("[^A-Za-z0-9._ -]","_");String lower=name.toLowerCase(Locale.ROOT);
        if(!(lower.endsWith(".doc")||lower.endsWith(".docx"))){send(x,400,"{\"error\":\"Choose a .doc or .docx Word document.\"}","application/json");return;}
        byte[] bytes=x.getRequestBody().readNBytes((int)MAX_FILE+1);if(bytes.length==0||bytes.length>MAX_FILE){send(x,400,"{\"error\":\"File must be between 1 byte and 50 MB.\"}","application/json");return;}
        String id=UUID.randomUUID().toString();Path dest=DOCS.resolve(id+"_"+name);Files.write(dest,bytes);
        try{String text=extract(dest);INDEX.add(dest.getFileName().toString(),name,text,bytes.length,Files.getLastModifiedTime(dest));send(x,201,"{\"ok\":true,\"id\":"+quote(id)+",\"name\":"+quote(name)+"}","application/json");}
        catch(Exception e){Files.deleteIfExists(dest);send(x,400,"{\"error\":\"Could not read this Word file. Please check that it is a valid, unprotected .doc or .docx.\"}","application/json");}
    }
    private static void documents(HttpExchange x)throws IOException{
        String path=x.getRequestURI().getPath();if(path.equals("/api/documents")||path.equals("/api/documents/")){if(!x.getRequestMethod().equals("GET")){send(x,405,"{\"error\":\"GET required\"}","application/json");return;}send(x,200,INDEX.documentsJson(),"application/json");return;}
        String id=URLDecoder.decode(path.substring("/api/documents/".length()),StandardCharsets.UTF_8);String stored=INDEX.fileForId(id);if(stored==null){send(x,404,"{\"error\":\"Document not found.\"}","application/json");return;}
        if(x.getRequestMethod().equals("GET")){Path f=DOCS.resolve(stored);String name=stored.substring(37);x.getResponseHeaders().set("Content-Type","application/octet-stream");x.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+name.replace("\"","_")+"\"");byte[] bytes=Files.readAllBytes(f);x.sendResponseHeaders(200,bytes.length);try(OutputStream o=x.getResponseBody()){o.write(bytes);}return;}
        if(!x.getRequestMethod().equals("DELETE")){send(x,405,"{\"error\":\"GET or DELETE required\"}","application/json");return;}Files.deleteIfExists(DOCS.resolve(stored));INDEX.remove(stored);send(x,200,"{\"ok\":true}","application/json");
    }
    private static void staticFiles(HttpExchange x)throws IOException{String path=x.getRequestURI().getPath();if(path.equals("/"))path="/index.html";Path f=STATIC.resolve(path.substring(1)).normalize();if(!f.startsWith(STATIC)||!Files.isRegularFile(f)){send(x,404,"Not found","text/plain; charset=utf-8");return;}String type=f.toString().endsWith(".css")?"text/css; charset=utf-8":f.toString().endsWith(".js")?"text/javascript; charset=utf-8":"text/html; charset=utf-8";send(x,200,Files.readString(f),type);}
    private static Map<String,String> query(String raw){Map<String,String> m=new HashMap<>();if(raw!=null)for(String p:raw.split("&")){String[] a=p.split("=",2);m.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),a.length>1?URLDecoder.decode(a[1],StandardCharsets.UTF_8):"");}return m;}
    private static String quote(String s){StringBuilder b=new StringBuilder("\"");for(char c:s.toCharArray()){switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\b'->b.append("\\b");case '\f'->b.append("\\f");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<0x20)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}}}return b.append('"').toString();}
    private static void send(HttpExchange x,int status,String body,String type)throws IOException{x.getResponseHeaders().set("Content-Type",type);byte[] b=body.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(status,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}

    static final class SearchIndex{
        private record Doc(String stored,String name,String text,long size,FileTime modified){}
        private final Map<String,Doc> docs=new LinkedHashMap<>();private final Map<String,Set<String>> inverted=new HashMap<>();private final Map<String,Map<String,Integer>> freq=new HashMap<>();
        synchronized void add(String stored,String text){String name=stored.replaceFirst("^[0-9a-fA-F-]{36}_","");try{add(stored,name,text,Files.size(DOCS.resolve(stored)),Files.getLastModifiedTime(DOCS.resolve(stored)));}catch(IOException ignored){}}
        synchronized void add(String stored,String name,String text,long size,FileTime modified){docs.put(stored,new Doc(stored,name,text,size,modified));Map<String,Integer> f=new HashMap<>();for(String w:tokens(text))f.merge(w,1,Integer::sum);freq.put(stored,f);for(String w:f.keySet())inverted.computeIfAbsent(w,k->new HashSet<>()).add(stored);}
        synchronized String fileForId(String id){for(String s:docs.keySet())if(s.startsWith(id+"_"))return s;return null;}
        synchronized int count(){return docs.size();}
        synchronized void remove(String stored){Doc d=docs.remove(stored);freq.remove(stored);if(d!=null)for(String w:tokens(d.text())){Set<String> names=inverted.get(w);if(names!=null){names.remove(stored);if(names.isEmpty())inverted.remove(w);}}}
        synchronized String documentsJson(){StringBuilder b=new StringBuilder("[");boolean first=true;for(Doc d:docs.values()){if(!first)b.append(',');first=false;b.append("{\"id\":").append(quote(d.stored().substring(0,36))).append(",\"name\":").append(quote(d.name())).append(",\"size\":").append(d.size()).append(",\"modified\":").append(quote(d.modified().toString())).append('}');}return b.append(']').toString();}
        synchronized String search(String raw,String mode){List<String> terms=tokens(raw);Map<String,Double> scores=new HashMap<>();if(!terms.isEmpty()){
            if(mode.equals("phrase")){String phrase=raw.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");for(Doc d:docs.values())if(kmpContains(d.text().toLowerCase(Locale.ROOT).replaceAll("\\s+"," "),phrase))scores.put(d.stored(),1.0);}
            else if(mode.equals("related")){Set<String> seeds=new HashSet<>();for(String t:terms)seeds.addAll(inverted.getOrDefault(t,Set.of()));Map<String,Integer> global=new HashMap<>();for(String key:seeds)for(var e:freq.get(key).entrySet())global.merge(e.getKey(),e.getValue(),Integer::sum);for(Doc d:docs.values()){double dot=0,a=0,b=0;for(var e:global.entrySet()){double x=e.getValue(),y=freq.get(d.stored()).getOrDefault(e.getKey(),0);dot+=x*y;a+=x*x;b+=y*y;}if(a>0&&b>0)scores.put(d.stored(),dot/Math.sqrt(a*b));}}
            else if(mode.equals("spelling")){for(String t:terms){Set<String> files=inverted.get(t);String lookup=t;if(files==null||files.isEmpty()){int best=3;for(String word:inverted.keySet()){int dist=distance(t,word);if(dist<best){best=dist;lookup=word;}}files=inverted.getOrDefault(lookup,Set.of());}for(String file:files)scores.merge(file,1+Math.log1p(freq.get(file).getOrDefault(lookup,0)),Double::sum);}}
            else for(String t:terms)for(String file:inverted.getOrDefault(t,Set.of()))scores.merge(file,1+Math.log1p(freq.get(file).getOrDefault(t,0)),Double::sum);
        }
        List<Map.Entry<String,Double>> hits=scores.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).toList();StringBuilder b=new StringBuilder("{\"results\":[");boolean first=true;for(var h:hits){if(!first)b.append(',');first=false;Doc d=docs.get(h.getKey());String text=d.text().replaceAll("\\s+"," ").trim();if(text.length()>260)text=text.substring(0,260)+"…";b.append("{\"id\":").append(quote(h.getKey().substring(0,36))).append(",\"name\":").append(quote(d.name())).append(",\"score\":").append(String.format(Locale.ROOT,"%.3f",h.getValue())).append(",\"snippet\":").append(quote(text)).append('}');}
        b.append("],\"suggestions\":[");Set<String> suggested=new TreeSet<>();if(!terms.isEmpty()&&(hits.isEmpty()||mode.equals("spelling")))for(String t:terms){String bestWord=null;int best=3;for(String w:inverted.keySet()){int dist=distance(t,w);if(dist<best){best=dist;bestWord=w;}}if(bestWord!=null&&!bestWord.equals(t))suggested.add(bestWord);}first=true;for(String s:suggested){if(!first)b.append(',');first=false;b.append(quote(s));}return b.append("]}").toString();}
        private static List<String> tokens(String s){Matcher m=Pattern.compile("[\\p{L}\\p{N}]+").matcher(s.toLowerCase(Locale.ROOT));List<String> a=new ArrayList<>();while(m.find())a.add(m.group());return a;}
        private static boolean kmpContains(String text,String pattern){if(pattern.isEmpty())return true;int[] lps=new int[pattern.length()];for(int i=1,len=0;i<pattern.length();){if(pattern.charAt(i)==pattern.charAt(len))lps[i++]=++len;else if(len>0)len=lps[len-1];else lps[i++]=0;}for(int i=0,j=0;i<text.length();){if(text.charAt(i)==pattern.charAt(j)){i++;j++;if(j==pattern.length())return true;}else if(j>0)j=lps[j-1];else i++;}return false;}
        private static int distance(String a,String b){int[] prev=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;for(int i=1;i<=a.length();i++){int[] cur=new int[b.length()+1];cur[0]=i;for(int j=1;j<=b.length();j++)cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));prev=cur;}return prev[b.length()];}
    }
}

