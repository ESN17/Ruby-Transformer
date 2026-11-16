import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import net.fabricmc.mappingio.*;
import net.fabricmc.mappingio.tree.*;
import org.objectweb.asm.*;

// Build a public client ABI index. No game code is embedded in the index.
public class BuildIndex {
    static MemoryMappingTree read(Reader reader) throws IOException {
        var tree = new MemoryMappingTree(true);
        MappingReader.read(reader, tree);
        return tree;
    }
    public static void main(String[] args) throws Exception {
        String version = args[0];
        boolean legacy = version.startsWith("1.");
        MemoryMappingTree names, intermediary;
        if (legacy) {
            try (var r = Files.newBufferedReader(Path.of("build/data/"+version+".txt"));
                 var z = new ZipFile("build/data/"+version+"-intermediary.jar");
                 var t = new InputStreamReader(z.getInputStream(z.getEntry("mappings/mappings.tiny")))) {
                names = read(r);
                intermediary = read(t);
            }
        } else { names = null; intermediary = null; }
        Path dir = Files.createDirectories(Path.of("build/classes/index"));
        try (var jar = new ZipFile(args[1]);
             var out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(dir.resolve(version+".gz")))))) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassReader cr;
                try (var stream = jar.getInputStream(entry)) { cr = new ClassReader(stream); }
                String raw = cr.getClassName();
                String stable = legacy ? names.mapClassName(raw,0,-1) : raw;
                if (!(stable.startsWith("net/minecraft/") || stable.startsWith("com/mojang/") || stable.startsWith("com/microsoft/"))) continue;
                String runtime = legacy ? intermediary.mapClassName(raw,0) : raw;
                StringBuilder lines = new StringBuilder().append("C\t").append(runtime).append('\t').append(stable).append('\t').append(cr.getAccess()).append('\n');
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    void add(boolean method, int flags, String name, String desc) {
                        if ((flags & Opcodes.ACC_PUBLIC)==0) return;
                        String runName=name, semName=name, runDesc=desc;
                        if (legacy) {
                            var m = method ? names.getMethod(raw,name,desc,0) : names.getField(raw,name,desc,0);
                            var i = method ? intermediary.getMethod(raw,name,desc) : intermediary.getField(raw,name,desc);
                            if (m==null && !name.startsWith("<")) return;
                            if (m!=null) semName=m.getSrcName();
                            if (i!=null && i.getDstName(0)!=null) runName=i.getDstName(0);
                            runDesc=intermediary.mapDesc(desc,0);
                        }
                        lines.append(method?"M":"F").append('\t').append(runName).append('\t').append(semName).append('\t').append(runDesc).append('\t').append(flags).append('\n');
                    }
                    public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){add(true,a,n,d);return null;}
                    public FieldVisitor visitField(int a,String n,String d,String s,Object v){add(false,a,n,d);return null;}
                }, ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                out.write(lines.toString());
            }
        }
    }
}

