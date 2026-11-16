import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

public class Matrix {
    static final String CLASS="net/minecraft/util/LinearCongruentialGenerator", OLD="net/minecraft/class_4540";
    static void put(ZipOutputStream z,String name,byte[] bytes) throws IOException {
        z.putNextEntry(new ZipEntry(name));z.write(bytes);z.closeEntry();
    }
    static Path probe(Path dir,String version,String method,int javaVersion) throws Exception {
        boolean legacy=version.startsWith("1.");
        var w=new ClassWriter(0);
        w.visit(javaVersion,Opcodes.ACC_PUBLIC,"Probe",null,"java/lang/Object",new String[]{"net/fabricmc/api/ModInitializer"});
        var ctor=w.visitMethod(1,"<init>","()V",null,null);
        ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(1,1);ctor.visitEnd();
        w.visitField(9,"result","J",null,null).visitEnd();
        var init=w.visitMethod(1,"onInitialize","()V",null,null);init.visitCode();
        init.visitLdcInsn(7L);init.visitLdcInsn(23L);
        init.visitMethodInsn(Opcodes.INVOKESTATIC,legacy?OLD:CLASS,method,"(JJ)J",false);
        init.visitFieldInsn(Opcodes.PUTSTATIC,"Probe","result","J");
        init.visitInsn(Opcodes.RETURN);init.visitMaxs(4,1);init.visitEnd();w.visitEnd();
        Path p=dir.resolve(version+method+javaVersion+".jar");
        try(var z=new ZipOutputStream(Files.newOutputStream(p))){
            put(z,"fabric.mod.json",("{\"schemaVersion\":1,\"id\":\"rubyprobe\",\"version\":\"1\",\"entrypoints\":{\"main\":[\"Probe\"]},\"depends\":{\"minecraft\":\"="+version+"\"}}").getBytes());
            put(z,"Probe.class",w.toByteArray());
        }
        return p;
    }
    // Use the official client implementation as the execution oracle. Pre-26 clients
    // need Fabric's intermediary names, as they would have under Loader.
    static Path runtime(Path dir,String version,Path game) throws Exception {
        if(!version.startsWith("1."))return game;
        var tree=new MemoryMappingTree(true);
        try(var z=new ZipFile("build/data/"+version+"-intermediary.jar");
            var r=new InputStreamReader(z.getInputStream(z.getEntry("mappings/mappings.tiny")))){
            MappingReader.read(r,tree);
        }
        String raw=tree.mapClassName(OLD,0,-1);
        try(var jar=new ZipFile(game.toFile());var stream=jar.getInputStream(jar.getEntry(raw+".class"))){
            var writer=new ClassWriter(0);
            new ClassReader(stream).accept(new ClassRemapper(writer,new Remapper(Opcodes.ASM9){
                public String map(String s){return tree.mapClassName(s,0);}
                public String mapMethodName(String o,String n,String d){var m=tree.getMethod(o,n,d);return m==null?n:m.getDstName(0);}
                public String mapFieldName(String o,String n,String d){var f=tree.getField(o,n,d);return f==null?n:f.getDstName(0);}
            }),0);
            var dest=dir.resolve(version+"-runtime.jar");
            try(var z=new ZipOutputStream(Files.newOutputStream(dest))){put(z,OLD+".class",writer.toByteArray());}
            return dest;
        }
    }
    static void reject(Path input,String from,String to,Path out,String expected) throws Exception {
        try { Transformer.convert(input,from,to,out,false);throw new AssertionError("Unexpected success"); }
        catch(IOException|IllegalArgumentException e){if(!e.getMessage().contains(expected))throw e;}
        if(Files.exists(out))throw new AssertionError("Partial output");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("Supply five client JAR paths in version order");
        var dir=Files.createTempDirectory(Path.of("build"),"matrix-");
        var games=new HashMap<String,Path>();
        for(int i=0;i<5;i++){String v=Port.VERSIONS.get(i);games.put(v,runtime(dir,v,Path.of(args[i])));}
        int passed=0;
        for(String from:Port.VERSIONS){
            Path input=probe(dir,from,from.startsWith("1.")?"method_22372":"next",65);
            for(String to:Port.VERSIONS)if(!from.equals(to)){
                Path out=dir.resolve(from+"-to-"+to+".jar");
                Transformer.convert(input,from,to,out,false);
                try(var zip=new ZipFile(out.toFile())){
                    var meta=Transformer.metadata(zip);
                    if(!meta.getAsJsonObject("depends").get("minecraft").getAsString().equals("="+to))throw new AssertionError("Metadata");
                }
                try(var cl=new URLClassLoader(new URL[]{out.toUri().toURL(),games.get(to).toUri().toURL()},Matrix.class.getClassLoader())){
                    var c=cl.loadClass("Probe");
                    ((ModInitializer)c.getConstructor().newInstance()).onInitialize();
                    if(c.getField("result").getLong(null)!=7L*(7L*6364136223846793005L+1442695040888963407L)+23L)
                        throw new AssertionError("Game method returned wrong value");
                }
                passed++;System.out.println("PASS "+from+" -> "+to);
            }
        }
        var bad=dir.resolve("bad.jar");
        reject(probe(dir,"26.3","next",69),"26.3","1.21.11",bad,"Java");
        reject(probe(dir,"26.3","missingFunction",65),"26.3","26.2",bad,"missingFunction");
        reject(probe(dir,"26.3","next",65),"26.2","26.3",bad,"minecraft");
        System.out.println(passed+" runtime conversions and 3 rejection tests passed.");
    }
}

