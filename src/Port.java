import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

final class Port extends Remapper {
    static final List<String> VERSIONS = List.of("1.21.10","1.21.11","26.1.2","26.2","26.3");
    record Member(String runtime, String stable, int access) {}
    static final class GameClass {
        final String runtime, stable;
        final int access;
        final Map<String,Member> members = new HashMap<>();
        GameClass(String[] fields) {
            runtime=fields[1]; stable=fields[2]; access=Integer.parseInt(fields[3]);
        }
    }
    static Map<String,GameClass> load(String version, boolean stable) throws IOException {
        if (!VERSIONS.contains(version)) throw new IOException("不支持的 Minecraft 版本 "+version);
        var result = new HashMap<String,GameClass>();
        var resource = Port.class.getResourceAsStream("/index/"+version+".gz");
        if (resource==null) throw new IOException("缺少版本索引 "+version);
        try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(resource),StandardCharsets.UTF_8))) {
            GameClass owner=null;
            String line;
            while((line=reader.readLine())!=null) {
                String[] f=line.split("\t");
                if(f[0].equals("C")) {
                    owner=new GameClass(f);
                    result.put(stable?owner.stable:owner.runtime,owner);
                } else {
                    var m=new Member(f[1],f[2],Integer.parseInt(f[4]));
                    owner.members.put(f[0]+(stable?m.stable:m.runtime)+f[3],m);
                }
            }
        }
        return result;
    }
    final Map<String,GameClass> source,target;
    final Set<String> local;
    final int maxClass;
    int references;
    Port(String from,String to,Set<String> local) throws IOException {
        super(Opcodes.ASM9);
        source=load(from,false); target=load(to,true); this.local=local;
        maxClass=to.startsWith("1.")?65:69;
    }
    static IllegalArgumentException unsupported(String detail) {
        return new IllegalArgumentException("需要移植或作者适配包： "+detail);
    }
    GameClass destination(GameClass old) {
        GameClass next=target.get(old.stable);
        if(next==null || (next.access & Opcodes.ACC_PUBLIC)==0 ||
            ((old.access^next.access) & Opcodes.ACC_INTERFACE)!=0)
            throw unsupported("目标版本缺少兼容的公开类 "+old.stable);
        return next;
    }
    @Override public String map(String type) {
        GameClass old=source.get(type);
        if(old!=null) {references++; return destination(old).runtime;}
        if(local.contains(type) || type.startsWith("java/") || type.startsWith("javax/") ||
           type.startsWith("net/fabricmc/api/") || type.startsWith("net/fabricmc/loader/api/")) return type;
        throw unsupported("无法验证外部类型 "+type);
    }
    String member(String kind,String owner,String name,String desc) {
        GameClass old=source.get(owner);
        if(old==null) return name;
        var original=old.members.get(kind+name+desc);
        if(original==null) throw unsupported("源索引未收录 "+owner+"."+name+desc);
        String newDesc=kind.equals("M")?mapMethodDesc(desc):mapDesc(desc);
        var next=destination(old).members.get(kind+original.stable+newDesc);
        int mask=Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_FINAL;
        if(next==null || ((original.access^next.access)&mask)!=0)
            throw unsupported("目标签名不兼容 "+old.stable+"."+original.stable+newDesc);
        return next.runtime;
    }
    @Override public String mapMethodName(String o,String n,String d){return member("M",o,n,d);}
    @Override public String mapFieldName(String o,String n,String d){return member("F",o,n,d);}
    byte[] rewrite(byte[] bytes) {
        var reader=new ClassReader(bytes);
        if(reader.readUnsignedShort(6)>maxClass || reader.readUnsignedShort(4)==65535)
            throw unsupported(reader.getClassName()+" 需要不同的 Java 字节码版本，请用目标 JDK 重新编译源码");
        var node=new ClassNode();
        reader.accept(node,0);
        if(source.containsKey(node.superName) || node.interfaces.stream().anyMatch(source::containsKey))
            throw unsupported(node.name+" 继承游戏类型，需核对重写方法");
        for(var method:node.methods) for(var insn:method.instructions) {
            if(insn instanceof InvokeDynamicInsnNode dyn && source.containsKey(Type.getReturnType(dyn.desc).getInternalName()))
                throw unsupported("游戏类型的动态调用 "+dyn.name);
            if(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String value &&
              (value.contains("net.minecraft.") || value.contains("net/minecraft/") || value.matches(".*(?:method_|field_|class_)\\d+.*")))
                throw unsupported("字符串形式的游戏符号 "+value);
        }
        var writer=new ClassWriter(0);
        node.accept(new ClassRemapper(writer,this));
        return writer.toByteArray();
    }
}

