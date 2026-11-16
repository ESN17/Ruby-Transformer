import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipFile;

final class Published {
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    static HttpResponse<InputStream> get(URI uri) throws Exception {
        var req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).header("User-Agent","Ruby-Transformer/1.0 (Fabric version switcher)").GET().build();
        return HTTP.send(req,HttpResponse.BodyHandlers.ofInputStream());
    }
    static JsonElement api(String path) throws Exception {
        var response=get(URI.create("https://api.modrinth.com/v2/"+path));
        try(var body=response.body()){
            if(response.statusCode()==404) throw new IOException("Modrinth 未收录此 JAR 的哈希");
            if(response.statusCode()!=200) throw new IOException("Modrinth HTTP "+response.statusCode());
            return JsonParser.parseString(new String(Transformer.read(body,16*1024*1024),StandardCharsets.UTF_8));
        }
    }
    static String hash(Path file) throws Exception {
        var digest=MessageDigest.getInstance("SHA-512");
        try(var stream=new DigestInputStream(Files.newInputStream(file),digest)){stream.transferTo(OutputStream.nullOutputStream());}
        return HexFormat.of().formatHex(digest.digest());
    }
    static String fetch(Path input,JsonObject meta,String target,Path output) throws Exception {
        var known=api("version_file/"+hash(input)+"?algorithm=sha512").getAsJsonObject();
        String project=known.get("project_id").getAsString();
        if(!project.matches("[a-zA-Z0-9]+")) throw new IOException("无效项目 ID");
        var versions=api("project/"+project+"/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"+target+"%22%5D").getAsJsonArray();
        for(var item:versions) {
            var v=item.getAsJsonObject();
            if(!v.get("version_type").getAsString().equals("release") || !v.getAsJsonArray("game_versions").contains(new JsonPrimitive(target)) ||
                !v.getAsJsonArray("loaders").contains(new JsonPrimitive("fabric"))) continue;
            for(var f:v.getAsJsonArray("files")){
                var file=f.getAsJsonObject();
                if(!file.get("primary").getAsBoolean() || !file.get("filename").getAsString().endsWith(".jar")) continue;
                URI url=URI.create(file.get("url").getAsString());
                if(!url.getScheme().equals("https") || !"cdn.modrinth.com".equals(url.getHost())) throw new IOException("无效下载地址");
                var response=get(url);
                try(var body=response.body()){
                    if(response.statusCode()!=200) throw new IOException("下载失败 HTTP "+response.statusCode());
                    long size=file.get("size").getAsLong();
                    if(size<1 || size>256L*1024*1024) throw new IOException("发布包大小超出范围");
                    try(var out=Files.newOutputStream(output)){
                        byte[] buffer=new byte[32768]; long count=0; int n;
                        while((n=body.read(buffer))!=-1){count+=n;if(count>size)throw new IOException("长度错误");out.write(buffer,0,n);}
                        if(count!=size)throw new IOException("下载不完整");
                    }
                }
                if(!hash(output).equalsIgnoreCase(file.getAsJsonObject("hashes").get("sha512").getAsString()))
                    throw new IOException("SHA-512 校验失败");
                try(var zip=new ZipFile(output.toFile())){
                    var actual=Transformer.metadata(zip);
                    if(!actual.get("id").equals(meta.get("id"))) throw new IOException("目标包的 Mod ID 不符");
                    Transformer.checkSource(actual,target);
                    return "已获取作者发布包 "+actual.get("version").getAsString()+"，SHA-512 校验通过。目标包依赖："+actual.get("depends");
                }
            }
        }
        throw new IOException("该项目没有适用于 Fabric "+target+" 的正式发布包");
    }
}

