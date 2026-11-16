import java.io.*;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

class MakeIcon {
    public static void main(String[] args)throws Exception{
        var data=new ByteArrayOutputStream();
        ImageIO.write(Studio.logo(256),"png",data);
        var header=ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short)0).putShort((short)1).putShort((short)1);
        header.putInt(0).putShort((short)1).putShort((short)32).putInt(data.size()).putInt(22);
        try(var file=Files.newOutputStream(Path.of(args[0]))){file.write(header.array());data.writeTo(file);}
    }
}

