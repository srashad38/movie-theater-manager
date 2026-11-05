package com.srashad.cinema;

import java.awt.*;
import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.*;

/** Desktop entry point. Demo data is opt-in and uses a separate local database. */
public final class CinemaApp {
    private CinemaApp() { }
    public static void main(String[] args) {
        for(String arg:args) {
            if(!Arrays.asList("--demo","--check","--help").contains(arg)) {
                System.err.println("Unknown option: "+arg+". Use --help for usage.");System.exit(2);
            }
        }
        if(Arrays.asList(args).contains("--help")) {
            System.out.println("Usage: java [-Dcinema.dataDir=/path] -jar movie-theater-manager.jar [--demo] [--check]");
            System.out.println("--demo: separate fictional sample workspace; --check: verify database setup without opening a window.");return;
        }
        boolean demo=Arrays.asList(args).contains("--demo");
        Path directory=Path.of(System.getProperty("cinema.dataDir",demo?"data/demo":"data/live"));
        try {
            Database db=Database.local(directory);db.initialize();
            CinemaService service=new CinemaService(db);
            if(demo)DemoData.seed(service);
            if(Arrays.asList(args).contains("--check")) {
                System.out.println("Database ready: "+service.movies().size()+" movies, "+service.screenings().size()+" screenings, "+service.bookings().size()+" bookings.");return;
            }
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            UIManager.put("control",new Color(246,247,251));
            UIManager.put("nimbusSelectionBackground",new Color(44,76,155));
            UIManager.put("defaultFont",new Font(Font.SANS_SERIF,Font.PLAIN,14));
            SwingUtilities.invokeLater(()->{
                JFrame frame=new JFrame("Movie Theater Manager"+(demo?" · Demo":""));
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.setContentPane(new CinemaPanel(service,demo));
                frame.setMinimumSize(new Dimension(1000,700));frame.pack();frame.setLocationRelativeTo(null);frame.setVisible(true);
            });
        } catch(Exception e) {
            System.err.println("Movie Theater Manager could not start: "+e.getMessage());
            if(!GraphicsEnvironment.isHeadless())JOptionPane.showMessageDialog(null,
                "Could not open the local database.\nClose any other copy of the app and check that the data folder is writable.\n\n"+e.getMessage(),
                "Startup problem",JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
