package com.srashad.cinema;

import com.srashad.cinema.Models.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;

/** Swing presentation only. Every database operation runs off the event dispatch thread. */
public final class CinemaPanel extends JPanel {
    private static final Color NAVY=new Color(20,30,52),INK=new Color(28,39,58),MUTED=new Color(97,109,130),BLUE=new Color(44,76,155);
    private static final DateTimeFormatter INPUT=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DISPLAY=DateTimeFormatter.ofPattern("MMM d, uuuu · h:mm a",Locale.US);
    private static final NumberFormat MONEY=NumberFormat.getCurrencyInstance(Locale.US);
    private final CinemaService service;
    private final JLabel status=new JLabel("Loading your theater…");
    private final JLabel[] metrics=new JLabel[4];
    private final JTabbedPane tabs=new JTabbedPane();
    private final JTable movies=table("ID","Movie","Genre","Minutes","Rating");
    private final JTable screenings=table("ID","Movie","Auditorium","Starts","Price","Available","Capacity");
    private final JTable bookings=table("ID","Customer","Movie","Auditorium","Starts","Seats","Total","Status");
    private List<Movie> movieRows=List.of();
    private List<Screening> screeningRows=List.of();
    private List<Booking> bookingRows=List.of();
    private List<Auditorium> rooms=List.of();
    private boolean busy;
    private record Snapshot(List<Movie> movies,List<Screening> screenings,List<Booking> bookings,List<Auditorium> rooms,Dashboard dashboard) { }
    @FunctionalInterface private interface Task<T> { T run() throws Exception; }

    public CinemaPanel(CinemaService service,boolean demo) {
        super(new BorderLayout());this.service=service;
        
        setMinimumSize(new Dimension(1000,700));setPreferredSize(new Dimension(1220,800));
        JPanel root=new JPanel(new BorderLayout(0,0));root.setBackground(new Color(246,247,251));add(root,BorderLayout.CENTER);
        root.add(header(demo),BorderLayout.NORTH);
        JPanel workspace=new JPanel(new BorderLayout(0,22));workspace.setOpaque(false);workspace.setBorder(new EmptyBorder(24,28,20,28));
        workspace.add(metrics(),BorderLayout.NORTH);
        tabs.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));
        tabs.addTab("Movies",section(movies,"Movie library","Manage the films your theater can schedule.",
            button("Add movie",this::addMovie),button("Edit selected",this::editMovie),button("Delete selected",this::deleteMovie)));
        tabs.addTab("Screenings",section(screenings,"Screening schedule","Room capacity and a 15-minute turnaround are checked automatically.",
            button("Schedule film",()->screeningForm(null)),button("Edit selected",this::editScreening),button("Remove selected",this::deleteScreening),button("Book seats",this::bookSeats)));
        tabs.addTab("Bookings",section(bookings,"Booking register","Active reservations count toward capacity. Cancelled records remain in history.",
            button("Cancel selected booking",this::cancelBooking)));
        workspace.add(tabs,BorderLayout.CENTER);root.add(workspace,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.setBorder(new EmptyBorder(12,28,14,28));footer.setBackground(Color.WHITE);
        status.setForeground(MUTED);footer.add(status,BorderLayout.CENTER);
        JLabel local=new JLabel("LOCAL DESKTOP  /  USD  /  LOCAL TIME");local.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));local.setForeground(MUTED);footer.add(local,BorderLayout.EAST);
        root.add(footer,BorderLayout.SOUTH);
        registerKeyboardAction(e->refresh(),KeyStroke.getKeyStroke("F5"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        refresh();
    }
    private JPanel header(boolean demo) {
        JPanel p=new JPanel(new BorderLayout(20,0));p.setBackground(NAVY);p.setBorder(new EmptyBorder(24,28,24,28));
        JPanel text=new JPanel(new GridLayout(2,1,0,6));text.setOpaque(false);
        JLabel eyebrow=new JLabel("CINEMA OPERATIONS"+(demo?"   /   DEMO WORKSPACE":""));eyebrow.setForeground(new Color(156,180,232));eyebrow.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));
        JLabel title=new JLabel("Movie Theater Manager");title.setForeground(Color.WHITE);title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,28));
        text.add(eyebrow);text.add(title);p.add(text,BorderLayout.CENTER);
        JButton refresh=button("Refresh  ↻",this::refresh);p.add(refresh,BorderLayout.EAST);return p;
    }
    private JPanel metrics() {
        JPanel p=new JPanel(new GridLayout(1,4,16,0));p.setOpaque(false);
        String[] names={"FILMS IN LIBRARY","UPCOMING SCREENINGS","ACTIVE TICKETS · ALL DATES","RESERVED VALUE · ALL DATES"};
        for(int i=0;i<4;i++){
            JPanel card=new JPanel(new BorderLayout(0,10));card.setBackground(Color.WHITE);card.setBorder(new EmptyBorder(18,20,18,20));
            JLabel label=new JLabel(names[i]);label.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));label.setForeground(MUTED);
            metrics[i]=new JLabel("—");metrics[i].setFont(new Font(Font.SANS_SERIF,Font.BOLD,28));metrics[i].setForeground(INK);
            card.add(label,BorderLayout.NORTH);card.add(metrics[i],BorderLayout.CENTER);p.add(card);
        }return p;
    }
    private JPanel section(JTable table,String title,String description,JButton... actions) {
        JPanel p=new JPanel(new BorderLayout(0,16));p.setBackground(Color.WHITE);p.setBorder(new EmptyBorder(22,20,20,20));
        JPanel top=new JPanel(new BorderLayout(0,16));top.setOpaque(false);
        JPanel headings=new JPanel(new GridLayout(2,1,0,5));headings.setOpaque(false);
        JLabel h=new JLabel(title);h.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));h.setForeground(INK);
        JLabel sub=new JLabel(description);sub.setForeground(MUTED);headings.add(h);headings.add(sub);top.add(headings,BorderLayout.NORTH);
        JPanel tools=new JPanel(new BorderLayout(16,0));tools.setOpaque(false);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));buttons.setOpaque(false);for(var b:actions)buttons.add(b);
        tools.add(buttons,BorderLayout.CENTER);
        JTextField filter=new JTextField(14);filter.putClientProperty("JTextField.placeholderText","Search rows");filter.setToolTipText("Filter this table across all columns");
        JPanel search=new JPanel(new BorderLayout(6,0));search.setOpaque(false);JLabel find=new JLabel("Search");find.setLabelFor(filter);search.add(find,BorderLayout.WEST);search.add(filter,BorderLayout.CENTER);tools.add(search,BorderLayout.EAST);
        filter.getDocument().addDocumentListener(new DocumentListener(){
            private void update(){String q=filter.getText();sorter(table).setRowFilter(q.isBlank()?null:RowFilter.regexFilter("(?i)"+Pattern.quote(q)));}
            public void insertUpdate(DocumentEvent e){update();}public void removeUpdate(DocumentEvent e){update();}public void changedUpdate(DocumentEvent e){update();}
        });
        top.add(tools,BorderLayout.SOUTH);p.add(top,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(table);scroll.setColumnHeaderView(table.getTableHeader());scroll.getViewport().setBackground(Color.WHITE);scroll.setBorder(BorderFactory.createLineBorder(new Color(227,231,239)));p.add(scroll,BorderLayout.CENTER);
        JLabel help=new JLabel("Select a row to act on it · Click a column heading to sort · F5 to refresh");help.setForeground(MUTED);help.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));p.add(help,BorderLayout.SOUTH);return p;
    }
    private static JButton button(String text,Runnable action) {
        JButton b=new JButton(text);b.setFocusPainted(true);b.setMargin(new Insets(8,12,8,12));b.setForeground(BLUE);b.addActionListener(e->action.run());return b;
    }
    private static JTable table(String... columns) {
        DefaultTableModel model=new DefaultTableModel(columns,0){
            @Override public boolean isCellEditable(int row,int col){return false;}
            @Override public Class<?> getColumnClass(int col){
                String n=getColumnName(col);return switch(n){case "ID","Booking"->Long.class;case "Minutes","Available","Capacity","Seats"->Integer.class;case "Starts"->LocalDateTime.class;case "Price","Total"->BigDecimal.class;default->String.class;};
            }
        };
        JTable t=new JTable(model);t.setRowHeight(38);t.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));t.setShowGrid(false);t.setIntercellSpacing(new Dimension(0,0));t.setFillsViewportHeight(true);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);t.setAutoCreateRowSorter(true);
        t.getTableHeader().setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));t.getTableHeader().setPreferredSize(new Dimension(0,40));t.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer renderer=new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable tb,Object value,boolean selected,boolean focus,int row,int col){
                Object display=value instanceof LocalDateTime dt?DISPLAY.format(dt):value instanceof BigDecimal money?MONEY.format(money):value;
                super.getTableCellRendererComponent(tb,display,selected,focus,row,col);
                setBorder(new EmptyBorder(0,10,0,10));setToolTipText(String.valueOf(display));
                if(!selected){setBackground(row%2==0?Color.WHITE:new Color(247,249,253));setForeground("CANCELLED".equals(value)?MUTED:INK);}
                setHorizontalAlignment(value instanceof Number?SwingConstants.RIGHT:SwingConstants.LEFT);return this;
            }
        };
        for(Class<?> type:new Class<?>[]{Object.class,Long.class,Integer.class,BigDecimal.class,LocalDateTime.class})t.setDefaultRenderer(type,renderer);
        for(int i=0;i<columns.length;i++){
            int width=switch(columns[i]){
                case "ID","Booking"->60;case "Movie"->250;case "Customer"->135;
                case "Starts"->230;case "Minutes","Seats","Available","Capacity"->80;
                case "Auditorium","Rating","Status"->100;case "Price","Total"->95;default->150;
            };
            t.getColumnModel().getColumn(i).setPreferredWidth(width);
        }
        t.getColumnModel().getColumn(0).setMaxWidth(85);return t;
    }
    @SuppressWarnings("unchecked") private static TableRowSorter<TableModel> sorter(JTable t){return (TableRowSorter<TableModel>)t.getRowSorter();}
    private static void rows(JTable table,Object[][] data){DefaultTableModel m=(DefaultTableModel)table.getModel();m.setRowCount(0);for(var row:data)m.addRow(row);}
    private Snapshot snapshot() throws Exception{return new Snapshot(service.movies(),service.screenings(),service.bookings(),service.auditoriums(),service.dashboard());}
    private void render(Snapshot s){
        movieRows=s.movies();screeningRows=s.screenings();bookingRows=s.bookings();rooms=s.rooms();
        rows(movies,movieRows.stream().map(m->new Object[]{m.id(),m.title(),m.genre(),m.minutes(),m.rating()}).toArray(Object[][]::new));
        rows(screenings,screeningRows.stream().map(v->new Object[]{v.id(),v.title(),v.room(),v.startsAt(),v.price(),v.available(),v.capacity()}).toArray(Object[][]::new));
        rows(bookings,bookingRows.stream().map(b->new Object[]{b.id(),b.customer(),b.title(),b.room(),b.startsAt(),b.quantity(),b.total(),b.status()}).toArray(Object[][]::new));
        Dashboard d=s.dashboard();metrics[0].setText(""+d.movies());metrics[1].setText(""+d.upcoming());metrics[2].setText(""+d.tickets());metrics[3].setText(MONEY.format(d.sales()));
    }
    private <T> void run(String progress,Task<T> task,Consumer<T> success){
        if(busy)return;busy=true;status.setText(progress);setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T,Void>(){
            @Override protected T doInBackground() throws Exception{return task.run();}
            @Override protected void done(){
                busy=false;setCursor(Cursor.getDefaultCursor());
                try{success.accept(get());}
                catch(InterruptedException e){Thread.currentThread().interrupt();status.setText("Operation interrupted. Refresh to check the latest state.");}
                catch(ExecutionException e){Throwable cause=e.getCause();status.setText("Action could not be completed.");
                    String message=cause instanceof IllegalArgumentException?cause.getMessage():"The database operation could not be completed. Refresh and try again.\nDetails: "+cause.getMessage();
                    JOptionPane.showMessageDialog(CinemaPanel.this,message,"Please check",JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }
    private void refresh(){run("Refreshing…",this::snapshot,s->{render(s);status.setText(movieRows.isEmpty()?"Your library is empty. Add a movie to get started.":"Up to date · "+movieRows.size()+" movies · "+screeningRows.size()+" screenings · "+bookingRows.size()+" bookings");});}
    private void mutate(String message,Task<?> operation){run("Saving…",()->{operation.run();return snapshot();},s->{render(s);status.setText(message);});}
    private Long selected(JTable t){int row=t.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(this,"Select a row first.");return null;}return ((Number)t.getValueAt(row,0)).longValue();}
    private boolean ready(){if(busy){Toolkit.getDefaultToolkit().beep();return false;}return true;}
    private boolean confirm(String message){return JOptionPane.showConfirmDialog(this,message,"Confirm action",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION;}
    private JPanel form(String[] labels,JComponent... controls){
        JPanel p=new JPanel(new GridLayout(0,2,14,14));p.setBorder(new EmptyBorder(12,0,12,0));
        for(int i=0;i<labels.length;i++){JLabel label=new JLabel(labels[i]);label.setLabelFor(controls[i]);p.add(label);p.add(controls[i]);}return p;
    }
    private boolean dialog(String title,JPanel form){return JOptionPane.showConfirmDialog(this,form,title,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION;}
    private void addMovie(){if(ready())movieForm(null);}
    private void editMovie(){if(!ready())return;Long id=selected(movies);if(id!=null)movieRows.stream().filter(m->m.id()==id).findFirst().ifPresent(this::movieForm);}
    private void movieForm(Movie movie){
        JTextField title=new JTextField(movie==null?"":movie.title(),24),genre=new JTextField(movie==null?"":movie.genre());
        JSpinner minutes=new JSpinner(new SpinnerNumberModel(movie==null?120:movie.minutes(),1,600,1));
        JComboBox<String> rating=new JComboBox<>(CinemaService.RATINGS.toArray(String[]::new));if(movie!=null)rating.setSelectedItem(movie.rating());
        while(dialog(movie==null?"Add movie":"Edit movie",form(new String[]{"Title","Genre","Running time (minutes)","Rating"},title,genre,minutes,rating))){
            try{minutes.commitEdit();if(title.getText().isBlank()||genre.getText().isBlank())throw new IllegalArgumentException("Enter a title and genre.");
                mutate("Movie saved.",()->service.saveMovie(movie==null?null:movie.id(),title.getText(),genre.getText(),(Integer)minutes.getValue(),(String)rating.getSelectedItem()));return;
            }catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Check the movie details",JOptionPane.WARNING_MESSAGE);}
        }
    }
    private void deleteMovie(){if(!ready())return;Long id=selected(movies);if(id!=null&&confirm("Delete the selected movie? Movies with screenings are protected."))mutate("Movie deleted.",()->{service.deleteMovie(id);return null;});}
    private void editScreening(){if(!ready())return;Long id=selected(screenings);if(id!=null)screeningRows.stream().filter(s->s.id()==id).findFirst().ifPresent(this::screeningForm);}
    private void screeningForm(Screening old){
        if(!ready())return;if(movieRows.isEmpty()){JOptionPane.showMessageDialog(this,"Add a movie before scheduling a screening.");return;}
        JComboBox<Movie> movie=new JComboBox<>(movieRows.toArray(Movie[]::new));JComboBox<Auditorium> room=new JComboBox<>(rooms.toArray(Auditorium[]::new));
        if(old!=null){movieRows.stream().filter(m->m.id()==old.movieId()).findFirst().ifPresent(movie::setSelectedItem);rooms.stream().filter(a->a.id()==old.auditoriumId()).findFirst().ifPresent(room::setSelectedItem);}
        JTextField starts=new JTextField(INPUT.format(old==null?LocalDateTime.now().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0):old.startsAt()));
        JTextField price=new JTextField(old==null?"14.50":old.price().toPlainString());
        JPanel f=form(new String[]{"Movie","Auditorium","Starts (YYYY-MM-DD HH:MM)","Ticket price (USD)"},movie,room,starts,price);
        while(dialog(old==null?"Schedule film":"Edit screening",f)){
            try{LocalDateTime time=LocalDateTime.parse(starts.getText().strip(),INPUT);BigDecimal cost=new BigDecimal(price.getText().strip());
                if(!time.isAfter(LocalDateTime.now()))throw new IllegalArgumentException("Choose a future date and time.");
                if(cost.signum()<0||cost.compareTo(new BigDecimal("9999.99"))>0||cost.stripTrailingZeros().scale()>2)throw new IllegalArgumentException("Enter a price from 0.00 to 9999.99, with up to two decimal places.");
                Movie m=(Movie)movie.getSelectedItem();Auditorium a=(Auditorium)room.getSelectedItem();
                mutate("Screening saved.",()->service.saveScreening(old==null?null:old.id(),m.id(),a.id(),time,cost));return;
            }catch(Exception e){JOptionPane.showMessageDialog(this,"Check the date and price.\nUse a date such as 2027-05-20 18:30 and a price such as 14.50.\n"+e.getMessage(),"Check screening details",JOptionPane.WARNING_MESSAGE);}
        }
    }
    private void deleteScreening(){if(!ready())return;Long id=selected(screenings);if(id!=null&&confirm("Remove this screening? Screenings with booking history are protected."))mutate("Screening removed.",()->{service.deleteScreening(id);return null;});}
    private void bookSeats(){
        if(!ready())return;Long id=selected(screenings);if(id==null)return;
        Screening show=screeningRows.stream().filter(s->s.id()==id).findFirst().orElseThrow();
        if(show.available()<1||!show.startsAt().isAfter(LocalDateTime.now())){JOptionPane.showMessageDialog(this,"Choose a future screening with available seats.");return;}
        JTextField customer=new JTextField(22);JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,Math.min(20,show.available()),1));
        JLabel total=new JLabel(MONEY.format(show.price()));quantity.addChangeListener(e->total.setText(MONEY.format(show.price().multiply(BigDecimal.valueOf((Integer)quantity.getValue())))));
        JPanel f=form(new String[]{"Customer / booking name","Seats ("+show.available()+" available)","Reservation total (USD)"},customer,quantity,total);
        while(dialog("Book seats · "+show.title(),f)){
            try{quantity.commitEdit();if(customer.getText().isBlank())throw new IllegalArgumentException("Enter a booking name.");int count=(Integer)quantity.getValue();
                mutate("Reservation created. No payment was processed.",()->service.book(show.id(),customer.getText(),count));return;
            }catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Check booking details",JOptionPane.WARNING_MESSAGE);}
        }
    }
    private void cancelBooking(){if(!ready())return;Long id=selected(bookings);if(id!=null&&confirm("Cancel booking #"+id+"? Its seats will become available. No payment or refund is processed."))mutate("Booking cancelled; seats released.",()->{service.cancel(id);return null;});}
}
