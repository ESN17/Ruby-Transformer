import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

final class Transformer {
    static byte[] read(InputStream input,int limit) throws IOException {
        try(input) {
            byte[] bytes=input.readNBytes(limit+1);
            if(bytes.length>limit) throw new IOException("文件条目过大");
            return bytes;
        }
    }
    static JsonObject metadata(ZipFile file) throws IOException {
        var entry=file.getEntry("fabric.mod.json");
        if(entry==null) throw new IOException("缺少 fabric.mod.json，请选择 Fabric Mod JAR");
        var obj=JsonParser.parseString(new String(read(file.getInputStream(entry),1024*1024),StandardCharsets.UTF_8)).getAsJsonObject();
        if(!obj.has("id") || !obj.has("version")) throw new IOException("Mod 描述缺少 id 或 version");
        return obj;
    }
    static boolean accepts(JsonElement rule,String value) throws Exception {
        if(rule==null) return true;
        if(rule.isJsonArray()) {
            for(var item:rule.getAsJsonArray()) if(accepts(item,value)) return true;
            return false;
        }
        return VersionPredicate.parse(rule.getAsString()).test(Version.parse(value));
    }
    static void checkSource(JsonObject meta,String version) throws Exception {
        var depends=meta.getAsJsonObject("depends");
        if(depends!=null && !accepts(depends.get("minecraft"),version))
            throw new IOException("所选版本 "+version+" 与 Mod 的 minecraft 声明 "+depends.get("minecraft")+" 不符");
    }
    static String convert(Path input,String from,String to,Path output,boolean online) throws Exception {
        if(!Port.VERSIONS.contains(from)||!Port.VERSIONS.contains(to)||from.equals(to)) throw new IOException("请选择两个不同的受支持版本");
        output=output.toAbsolutePath();
        if(Files.exists(output)) throw new IOException("输出文件已存在，请选择新文件名");
        Path temp=Files.createTempFile(output.getParent(),".ruby-",".tmp");
        try {
            String result;
            try(var zip=new ZipFile(input.toFile())) {
                var meta=metadata(zip);
                checkSource(meta,from);
                if(online) result=Published.fetch(input,meta,to,temp);
                else result=rewrite(zip,meta,from,to,temp);
            }
            Files.move(temp,output);
            return result;
        } finally {Files.deleteIfExists(temp);}
    }
    static String rewrite(ZipFile zip,JsonObject meta,String from,String to,Path temp) throws Exception {
        for(String feature:List.of("mixins","accessWidener","jars","languageAdapters"))
            if(meta.has(feature)) throw Port.unsupported("fabric.mod.json 声明了 "+feature);
        if(meta.has("breaks") || meta.has("conflicts")) throw Port.unsupported("需核对 conflicts / breaks 声明");
        var depends=meta.getAsJsonObject("depends");
        if(depends==null){depends=new JsonObject();meta.add("depends",depends);}
        for(String dep:depends.keySet()) if(!Set.of("minecraft","java","fabricloader").contains(dep))
            throw Port.unsupported("需验证依赖 "+dep);
        if(!accepts(depends.get("java"),to.startsWith("1.")?"21":"25")) throw Port.unsupported("Mod 的 Java 依赖 "+depends.get("java"));
        depends.addProperty("minecraft","="+to);
        Set<String> local=new HashSet<>();
        for(var e:Collections.list(zip.entries())) if(e.getName().endsWith(".class")) local.add(e.getName().substring(0,e.getName().length()-6));
        var port=new Port(from,to,local);
        long total=0;
        try(var out=new ZipOutputStream(Files.newOutputStream(temp))) {
            for(var e:Collections.list(zip.entries())) {
                String name=e.getName();
                if(name.matches("(?i)META-INF/(.*\\.(SF|RSA|DSA|EC)|versions/.*)") || name.startsWith("data/") ||
                   name.endsWith(".jar") || name.endsWith(".mixins.json") || name.endsWith(".accesswidener"))
                    throw Port.unsupported("需单独检查资源 "+name);
                byte[] data=read(zip.getInputStream(e),32*1024*1024);
                total+=data.length;
                if(total>256L*1024*1024) throw new IOException("解压内容超出 256 MiB");
                if(name.equals("fabric.mod.json")) data=new GsonBuilder().setPrettyPrinting().create().toJson(meta).getBytes(StandardCharsets.UTF_8);
                else if(name.endsWith(".class")) data=port.rewrite(data);
                out.putNextEntry(new ZipEntry(name));out.write(data);out.closeEntry();
            }
        }
        return "已转换 "+local.size()+" 个 class，已检查 "+port.references+" 处游戏类型引用。请在目标实例验证 Mod 功能。";
    }
}

