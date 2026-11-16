import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;

final class Studio {
    static final Color PAPER=new Color(0xF6F4F2), INK=new Color(0x29222A), MUTED=new Color(0x695F68),
        LINE=new Color(0xDED8DB), RUBY=new Color(0xA32C50), BLUSH=new Color(0xF9EAF0);
    static Font face;
    final JFrame window=new JFrame(Ruby.TITLE);
    final JButton file=button("选择 Mod 文件…",Color.WHITE,INK,LINE);
    final JLabel filename=label("拖入 Fabric Mod 的 .jar 文件",15,true);
    final JLabel location=label("也可以从本机选择文件",12,false);
    final JComboBox<String> from=versions(),to=versions();
    final JButton local=button("本地字节码转换",BLUSH,RUBY,RUBY);
    final JButton release=button("获取作者发布包",Color.WHITE,INK,LINE);
    final JButton run=button("选择输出并转换   →",RUBY,Color.WHITE,RUBY);
    final JLabel hint=label("",12,false);
    final JTextArea status=new JTextArea(2,32);
    Path input;
    boolean online;
    static void setup() throws Exception {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        try(var data=Studio.class.getResourceAsStream("/fonts/SourceHanSansCN-Regular.otf")){
            face=Font.createFont(Font.TRUETYPE_FONT,data);
        }
        var keys=java.util.Collections.list(UIManager.getDefaults().keys());
        for(Object k:keys) if(UIManager.get(k) instanceof Font) UIManager.put(k,new FontUIResource(face.deriveFont(14f)));
        UIManager.put("ComboBox.selectionBackground",BLUSH);UIManager.put("ComboBox.selectionForeground",RUBY);
        UIManager.put("FileChooser.useSystemExtensionHiding",false);
    }
    static void show(){
        try{setup();new Studio().open();}
        catch(Exception ex){JOptionPane.showMessageDialog(null,ex.toString(),Ruby.TITLE,JOptionPane.ERROR_MESSAGE);}
    }
    static JLabel label(String text,int size,boolean bold){
        var l=new JLabel(text);l.setForeground(bold?INK:MUTED);l.setFont(face.deriveFont(bold?Font.BOLD:Font.PLAIN,(float)size));return l;
    }
    static Graphics2D smooth(Graphics g){
        var a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);return a;
    }
    static void gem(Graphics2D g,int x,int y,int size){
        g.translate(x,y);g.scale(size/64.0,size/64.0);
        int[][] facets={{2,23,17,7,26,23},{17,7,46,7,26,23},{46,7,62,23,26,23},{2,23,26,23,32,59},{26,23,62,23,32,59}};
        int[] colors={0xFF779B,0xFFD6E1,0xFDA2BC,0xCC426D,0xF1628A};
        for(int i=0;i<facets.length;i++){var p=new Path2D.Double();int[] v=facets[i];p.moveTo(v[0],v[1]);p.lineTo(v[2],v[3]);p.lineTo(v[4],v[5]);p.closePath();g.setColor(new Color(colors[i]));g.fill(p);}
    }
    static BufferedImage logo(int size){
        var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        var g=smooth(image.createGraphics());g.setColor(new Color(0x492037));g.fillRoundRect(0,0,size,size,size/4,size/4);
        gem(g,size/8,size/8,size*3/4);g.dispose();return image;
    }
    static JPanel clear(LayoutManager layout){var p=new JPanel(layout);p.setOpaque(false);return p;}
    static JPanel card(LayoutManager layout){
        var p=new JPanel(layout){
            protected void paintComponent(Graphics graphics){var g=smooth(graphics);g.setColor(Color.WHITE);g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);g.setColor(LINE);g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);g.dispose();}
        };
        p.setOpaque(false);p.setBorder(new EmptyBorder(16,20,16,20));return p;
    }
    static JButton button(String text,Color bg,Color fg,Color border){
        var b=new JButton(text){
            protected void paintComponent(Graphics graphics){
                var g=smooth(graphics);Color base=getBackground();
                if(!isEnabled())base=new Color(0xEAE5E7);
                else if(getModel().isPressed())base=base.darker();
                else if(getModel().isRollover())base=new Color((base.getRed()*15+140)/16,(base.getGreen()*15+38)/16,(base.getBlue()*15+74)/16);
                g.setColor(base);g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g.setColor(!isEnabled()?LINE:hasFocus()?RUBY:(Color)getClientProperty("stroke"));
                g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);g.dispose();super.paintComponent(graphics);
            }
        };
        b.putClientProperty("stroke",border);b.setForeground(fg);b.setBackground(bg);b.setOpaque(false);b.setContentAreaFilled(false);b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(9,16,9,16));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
    static JComboBox<String> versions(){
        var combo=new JComboBox<>(Port.VERSIONS.toArray(String[]::new));
        combo.setUI(new BasicComboBoxUI(){
            protected JButton createArrowButton(){
                var b=new JButton(){
                    protected void paintComponent(Graphics graphics){
                        var g=smooth(graphics);int x=getWidth()/2,y=getHeight()/2;
                        g.setColor(MUTED);g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                        g.drawPolyline(new int[]{x-4,x,x+4},new int[]{y-2,y+2,y-2},3);g.dispose();
                    }
                };
                b.setBorder(null);b.setOpaque(false);b.setContentAreaFilled(false);return b;
            }
        });
        combo.setBackground(Color.WHITE);combo.setForeground(INK);
        combo.setFont(face.deriveFont(Font.BOLD,17f));combo.setPreferredSize(new Dimension(180,40));
        combo.setBorder(new CompoundBorder(new LineBorder(LINE),new EmptyBorder(0,8,0,2)));return combo;
    }
    static JPanel section(String name,JComponent control){
        var panel=clear(new BorderLayout(0,7));panel.add(label(name,13,true),BorderLayout.NORTH);panel.add(control);return panel;
    }
    static JFileChooser chooser(){var f=new JFileChooser();f.setFileFilter(new FileNameExtensionFilter("Fabric Mod (*.jar)","jar"));return f;}
    void open(){
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);window.setIconImage(logo(96));
        var head=new JPanel(new BorderLayout(16,0)){
            protected void paintComponent(Graphics graphics){
                var g=smooth(graphics);g.setPaint(new GradientPaint(0,0,new Color(0x331E30),getWidth(),0,new Color(0x73243F)));g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(new Color(255,255,255,19));int w=getWidth(),h=getHeight();
                for(int i=0;i<3;i++) {int x=w-125+42*i;g.drawLine(x,-8,x+60,h);g.drawLine(x+100,-8,x+60,h);}
                g.dispose();
            }
        };
        head.setOpaque(false);head.setBorder(new EmptyBorder(17,26,17,26));head.add(new JLabel(new ImageIcon(logo(52))),BorderLayout.WEST);
        var titles=clear(new GridLayout(2,1));var brand=label(Ruby.TITLE,24,true);brand.setForeground(Color.WHITE);
        var subtitle=label("Minecraft Fabric 模组版本转换",13,false);subtitle.setForeground(new Color(0xF2D9E1));
        titles.add(brand);titles.add(subtitle);head.add(titles);
        var body=clear(new GridBagLayout());body.setBorder(new EmptyBorder(16,24,12,24));
        var c=new GridBagConstraints();c.gridx=0;c.gridy=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(0,0,10,0);
        var inputCard=card(new BorderLayout(14,0));var text=clear(new GridLayout(2,1,0,2));text.add(filename);text.add(location);
        inputCard.add(text);inputCard.add(file,BorderLayout.EAST);body.add(inputCard,c);c.gridy++;
        var direction=card(new GridLayout(1,2,24,0));to.setSelectedIndex(1);
        direction.add(section("源 Minecraft 版本",from));direction.add(section("目标 Minecraft 版本",to));body.add(direction,c);c.gridy++;
        var method=card(new BorderLayout(0,8));var choices=clear(new GridLayout(1,2,10,0));choices.add(local);choices.add(release);
        method.add(section("切换方式",choices));method.add(hint,BorderLayout.SOUTH);body.add(method,c);c.gridy++;
        var footer=clear(new BorderLayout(12,0));status.setFont(face.deriveFont(13f));status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setOpaque(false);
        var scroll=new JScrollPane(status);scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(300,52));footer.add(scroll);footer.add(run,BorderLayout.EAST);body.add(footer,c);
        status.setForeground(MUTED);status.setText("等待选择 Mod 文件");setMode(false);run.setEnabled(false);
        file.addActionListener(e->{var f=chooser();if(f.showOpenDialog(window)==JFileChooser.APPROVE_OPTION)select(f.getSelectedFile().toPath());});
        local.addActionListener(e->setMode(false));release.addActionListener(e->setMode(true));run.addActionListener(e->convert());
        var drop=new TransferHandler(){
            public boolean canImport(TransferSupport support){return file.isEnabled() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
            public boolean importData(TransferSupport support){
                if(!canImport(support))return false;
                try{var files=(List<?>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);if(files.size()!=1)return false;select(((File)files.get(0)).toPath());return true;}catch(Exception ex){message(ex.toString(),true);return false;}
            }
        };
        inputCard.setTransferHandler(drop);
        var root=new JPanel(new BorderLayout());root.setBackground(PAPER);root.add(head,BorderLayout.NORTH);root.add(body,BorderLayout.CENTER);window.setContentPane(root);
        window.pack();var size=window.getSize();window.setMinimumSize(new Dimension(Math.max(640,size.width),size.height));
        window.setSize(Math.max(690,size.width),size.height);window.setLocationRelativeTo(null);window.setVisible(true);
    }
    void setMode(boolean useOnline){
        online=useOnline;
        for(var b:List.of(local,release)){boolean selected=(b==release)==online;b.setBackground(selected?BLUSH:Color.WHITE);b.setForeground(selected?RUBY:MUTED);b.putClientProperty("stroke",selected?RUBY:LINE);b.repaint();}
        hint.setText(online?"通过 Modrinth 匹配同一项目，并校验目标发布包的 SHA-512。":"离线重映射游戏类和方法，检查目标版本的公共签名。");
    }
    void message(String s,boolean error){status.setForeground(error?RUBY:MUTED);status.setText(s);status.setCaretPosition(0);}
    void select(Path p){
        try(var z=new ZipFile(p.toFile())){
            var meta=Transformer.metadata(z);
            var matches=new java.util.ArrayList<String>();
            for(String v:Port.VERSIONS){try{Transformer.checkSource(meta,v);matches.add(v);}catch(IOException ignored){}}
            if(matches.isEmpty())throw new IOException("此 Mod 的版本声明与五个受支持版本均不匹配");
            if(!matches.contains(from.getSelectedItem()))from.setSelectedItem(matches.get(0));
            input=p.toAbsolutePath();filename.setText(input.getFileName().toString());filename.setToolTipText(input.toString());
            location.setText(meta.get("id").getAsString()+"   /   "+meta.get("version").getAsString());file.setText("更换文件…");run.setEnabled(true);
            message(matches.size()==1?"已识别源版本 "+matches.get(0):"此 Mod 声明支持多个版本，请确认源版本。",false);
        }catch(Exception ex){message("文件读取失败："+ex.getMessage(),true);}
    }
    void convert(){
        String src=(String)from.getSelectedItem(),dst=(String)to.getSelectedItem();boolean web=online;Path jar=input;
        if(src.equals(dst)){message("请选择不同的源版本和目标版本。",true);return;}
        var f=chooser();f.setSelectedFile(jar.resolveSibling(jar.getFileName().toString().replaceFirst("(?i)\\.jar$","")+"-mc"+dst+".jar").toFile());
        if(f.showSaveDialog(window)!=JFileChooser.APPROVE_OPTION)return;
        var out=f.getSelectedFile().toPath();
        for(var component:List.of(file,from,to,local,release,run))component.setEnabled(false);
        message(web?"正在查询并校验发布包…":"正在检查并转换字节码…",false);
        new SwingWorker<String,Void>(){
            protected String doInBackground()throws Exception{return Transformer.convert(jar,src,dst,out,web);}
            protected void done(){try{message(get()+"\n"+out,false);}catch(ExecutionException ex){message("转换失败："+ex.getCause().getMessage(),true);}catch(Exception ex){message(ex.toString(),true);}finally{for(var component:List.of(file,from,to,local,release,run))component.setEnabled(true);}}
        }.execute();
    }
}

