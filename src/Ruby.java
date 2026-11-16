import java.nio.file.*;
import javax.swing.*;

public class Ruby {
    static final String TITLE="Ruby Transformer";
    public static void main(String[] args) {
        if(args.length>0) {
            try {
                if(args.length!=5 || !(args[0].equals("convert")||args[0].equals("release")))
                    throw new IllegalArgumentException("用法： convert|release <输入.jar> <源版本> <目标版本> <输出.jar>");
                System.out.println(Transformer.convert(Path.of(args[1]),args[2],args[3],Path.of(args[4]),args[0].equals("release")));
            } catch(Exception e){System.err.println(e.getMessage());System.exit(1);}
        } else SwingUtilities.invokeLater(Studio::show);
    }
}
