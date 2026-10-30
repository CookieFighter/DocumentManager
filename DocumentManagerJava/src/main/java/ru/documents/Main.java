package ru.documents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new Window().setVisible(true);
        });
    }

    abstract static class Doc {
        final String number, date, user;
        Doc(String n, String d, String u) { number=n; date=d; user=u; }
        abstract String type();
        abstract String[] fields();
        abstract String details();
        String title() { return type()+" от "+date+" №"+number; }
    }
    static class Invoice extends Doc {
        final String amount,currency,rate,product,quantity;
        Invoice(String[] f) { super(f[0],f[1],f[2]); amount=f[3]; currency=f[4]; rate=f[5]; product=f[6]; quantity=f[7]; }
        String type(){return "Накладная";}
        String[] fields(){return new String[]{number,date,user,amount,currency,rate,product,quantity};}
        String details(){return "Накладная\n\nНомер: "+number+"\nДата: "+date+"\nПользователь: "+user+"\nСумма: "+amount+"\nВалюта: "+currency+"\nКурс валюты: "+rate+"\nТовар: "+product+"\nКоличество: "+quantity;}
    }
    static class Payment extends Doc {
        final String amount,employee;
        Payment(String[] f){super(f[0],f[1],f[2]);amount=f[3];employee=f[4];}
        String type(){return "Платёжка";}
        String[] fields(){return new String[]{number,date,user,amount,employee};}
        String details(){return "Платёжка\n\nНомер: "+number+"\nДата: "+date+"\nПользователь: "+user+"\nСумма: "+amount+"\nСотрудник: "+employee;}
    }
    static class Request extends Doc {
        final String counterparty,amount,currency,rate,commission;
        Request(String[] f){super(f[0],f[1],f[2]);counterparty=f[3];amount=f[4];currency=f[5];rate=f[6];commission=f[7];}
        String type(){return "Заявка на оплату";}
        String[] fields(){return new String[]{number,date,user,counterparty,amount,currency,rate,commission};}
        String details(){return "Заявка на оплату\n\nНомер: "+number+"\nДата: "+date+"\nПользователь: "+user+"\nКонтрагент: "+counterparty+"\nСумма: "+amount+"\nВалюта: "+currency+"\nКурс валюты: "+rate+"\nКомиссия: "+commission;}
    }

    static class Storage {
        static final String HEADER="DOCUMENTS_TEXT_V1";
        static void save(List<Doc> docs, File file) throws IOException {
            List<String> lines=new ArrayList<>(); lines.add(HEADER);
            for(Doc d:docs){
                StringBuilder s=new StringBuilder(d.type());
                for(String f:d.fields()) s.append('\t').append(Base64.getEncoder().encodeToString(f.getBytes(StandardCharsets.UTF_8)));
                lines.add(s.toString());
            }
            Files.write(file.toPath(),lines,StandardCharsets.UTF_8);
        }
        static List<Doc> load(File file) throws IOException {
            List<String> lines=Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
            if(lines.isEmpty()||!HEADER.equals(lines.get(0))) throw new IOException("Неизвестный формат файла.");
            List<Doc> out=new ArrayList<>();
            for(int i=1;i<lines.size();i++){
                if(lines.get(i).isBlank()) continue;
                String[] p=lines.get(i).split("\\t",-1), f=new String[p.length-1];
                try{
                    for(int j=1;j<p.length;j++) f[j-1]=new String(Base64.getDecoder().decode(p[j]),StandardCharsets.UTF_8);
                    switch(p[0]){
                        case "Накладная" -> {if(f.length!=8)throw new IllegalArgumentException();out.add(new Invoice(f));}
                        case "Платёжка" -> {if(f.length!=5)throw new IllegalArgumentException();out.add(new Payment(f));}
                        case "Заявка на оплату" -> {if(f.length!=8)throw new IllegalArgumentException();out.add(new Request(f));}
                        default -> throw new IllegalArgumentException();
                    }
                }catch(RuntimeException e){throw new IOException("Ошибка в строке "+(i+1)+" файла.",e);}
            }
            return out;
        }
    }

    static class Window extends JFrame {
        final List<Doc> docs=new ArrayList<>();
        final JPanel list=new JPanel();
        final Map<Doc,JCheckBox> checks=new LinkedHashMap<>();
        final JLabel count=new JLabel();
        Window(){
            super("Менеджер документов");
            setDefaultCloseOperation(EXIT_ON_CLOSE);setMinimumSize(new Dimension(760,500));setSize(950,620);setLocationRelativeTo(null);
            JPanel root=new JPanel(new BorderLayout(12,12));root.setBorder(new EmptyBorder(14,14,14,14));setContentPane(root);
            JLabel title=new JLabel("Документы");title.setFont(title.getFont().deriveFont(Font.BOLD,23f));root.add(title,BorderLayout.NORTH);
            list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));list.setBackground(Color.WHITE);
            JScrollPane scroll=new JScrollPane(list);scroll.setBorder(BorderFactory.createTitledBorder("Список документов"));root.add(scroll,BorderLayout.CENTER);
            JPanel side=new JPanel();side.setLayout(new BoxLayout(side,BoxLayout.Y_AXIS));side.setPreferredSize(new Dimension(190,10));
            button(side,"Накладная",()->create("Накладная"));button(side,"Платёжка",()->create("Платёжка"));button(side,"Заявка на оплату",()->create("Заявка на оплату"));
            side.add(Box.createVerticalStrut(15));button(side,"Просмотр",this::view);button(side,"Удалить выбранные",this::delete);
            side.add(Box.createVerticalStrut(15));button(side,"Сохранить в файл",this::save);button(side,"Загрузить из файла",this::load);
            side.add(Box.createVerticalGlue());count.setAlignmentX(Component.CENTER_ALIGNMENT);side.add(count);root.add(side,BorderLayout.EAST);
            JLabel hint=new JLabel("Отметьте документы галочками для просмотра или удаления.");hint.setForeground(Color.GRAY);root.add(hint,BorderLayout.SOUTH);
            refresh();
        }
        void button(JPanel p,String name,Runnable action){JButton b=new JButton(name);b.setAlignmentX(Component.CENTER_ALIGNMENT);b.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));b.setPreferredSize(new Dimension(180,38));b.addActionListener(e->action.run());p.add(b);p.add(Box.createVerticalStrut(8));}
        void create(String type){Doc d=Form.show(this,type);if(d!=null){docs.add(d);refresh();}}
        void refresh(){
            list.removeAll();checks.clear();
            if(docs.isEmpty()){JLabel empty=new JLabel("Список пуст. Создайте первый документ.");empty.setBorder(new EmptyBorder(18,12,18,12));list.add(empty);}
            for(Doc d:docs){JCheckBox c=new JCheckBox(d.title());c.setBackground(Color.WHITE);c.setAlignmentX(Component.LEFT_ALIGNMENT);c.setBorder(new EmptyBorder(7,10,7,10));checks.put(d,c);list.add(c);list.add(new JSeparator());}
            count.setText("Документов: "+docs.size());list.revalidate();list.repaint();
        }
        List<Doc> selected(){List<Doc>s=new ArrayList<>();checks.forEach((d,c)->{if(c.isSelected())s.add(d);});return s;}
        void view(){
            List<Doc>s=selected();if(s.size()!=1){info("Для просмотра отметьте ровно один документ.");return;}
            JTextArea a=new JTextArea(s.get(0).details(),13,38);a.setEditable(false);a.setBorder(new EmptyBorder(10,10,10,10));
            JOptionPane.showMessageDialog(this,new JScrollPane(a),"Просмотр документа",JOptionPane.INFORMATION_MESSAGE);
        }
        void delete(){
            List<Doc>s=selected();if(s.isEmpty()){info("Сначала отметьте документы для удаления.");return;}
            if(JOptionPane.showConfirmDialog(this,"Удалить выбранные документы ("+s.size()+")?","Подтверждение",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){docs.removeAll(s);refresh();}
        }
        void save(){
            JFileChooser ch=new JFileChooser();ch.setDialogTitle("Сохранить документы");ch.setSelectedFile(new File("documents.txt"));
            if(ch.showSaveDialog(this)==JFileChooser.APPROVE_OPTION)try{Storage.save(docs,ch.getSelectedFile());info("Файл сохранён.");}catch(IOException e){error(e.getMessage());}
        }
        void load(){
            JFileChooser ch=new JFileChooser();ch.setDialogTitle("Загрузить документы");
            if(ch.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)try{List<Doc>x=Storage.load(ch.getSelectedFile());docs.addAll(x);refresh();info("Загружено документов: "+x.size());}catch(IOException e){error(e.getMessage());}
        }
        void info(String s){JOptionPane.showMessageDialog(this,s,"Информация",JOptionPane.INFORMATION_MESSAGE);}
        void error(String s){JOptionPane.showMessageDialog(this,s,"Ошибка",JOptionPane.ERROR_MESSAGE);}
    }

    static class Form {
        static Doc show(Component parent,String type){
            String[] labels=switch(type){
                case "Накладная" -> new String[]{"Номер","Дата (ГГГГ-ММ-ДД)","Пользователь","Сумма","Валюта","Курс валюты","Товар","Количество"};
                case "Платёжка" -> new String[]{"Номер","Дата (ГГГГ-ММ-ДД)","Пользователь","Сумма","Сотрудник"};
                default -> new String[]{"Номер","Дата (ГГГГ-ММ-ДД)","Пользователь","Контрагент","Сумма","Валюта","Курс валюты","Комиссия"};
            };
            JPanel panel=new JPanel(new GridBagLayout());panel.setBorder(new EmptyBorder(8,8,8,8));JTextField[] fields=new JTextField[labels.length];
            for(int i=0;i<labels.length;i++){
                GridBagConstraints l=new GridBagConstraints();l.gridx=0;l.gridy=i;l.anchor=GridBagConstraints.WEST;l.insets=new Insets(5,5,5,12);panel.add(new JLabel(labels[i]+":"),l);
                fields[i]=new JTextField(22);if(i==1)fields[i].setText(LocalDate.now().toString());
                GridBagConstraints r=new GridBagConstraints();r.gridx=1;r.gridy=i;r.weightx=1;r.fill=GridBagConstraints.HORIZONTAL;r.insets=new Insets(5,5,5,5);panel.add(fields[i],r);
            }
            while(true){
                if(JOptionPane.showConfirmDialog(parent,panel,"Создание: "+type,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return null;
                String[] v=Arrays.stream(fields).map(f->f.getText().trim()).toArray(String[]::new);
                try{
                    for(int i=0;i<v.length;i++)if(v[i].isEmpty())throw new IllegalArgumentException("Заполните поле «"+labels[i]+"».");
                    LocalDate.parse(v[1]);
                    if(type.equals("Накладная")){decimal(v[3]);decimal(v[5]);decimal(v[7]);return new Invoice(v);}
                    if(type.equals("Платёжка")){decimal(v[3]);return new Payment(v);}
                    decimal(v[4]);decimal(v[6]);decimal(v[7]);return new Request(v);
                }catch(DateTimeParseException e){JOptionPane.showMessageDialog(parent,"Введите дату в формате ГГГГ-ММ-ДД.");}
                catch(NumberFormatException e){JOptionPane.showMessageDialog(parent,"Проверьте числовые поля (например, 1250.50).");}
                catch(IllegalArgumentException e){JOptionPane.showMessageDialog(parent,e.getMessage());}
            }
        }
        static void decimal(String s){if(new BigDecimal(s.replace(',','.')).signum()<0)throw new IllegalArgumentException("Число не может быть отрицательным.");}
    }
}
