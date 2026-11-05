package com.srashad.cinema;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.TableRowSorter;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real Swing model and search listeners without a display server. */
class CinemaPanelTest {
    private static <T> List<T> find(Container parent,Class<T> type){
        List<T> result=new ArrayList<>();
        for(Component c:parent.getComponents()){
            if(type.isInstance(c))result.add(type.cast(c));
            if(c instanceof Container child)result.addAll(find(child,type));
        }
        return result;
    }
    @Test void asynchronousLoadSupportsLiteralSearchAndTypedSorting() throws Exception {
        Database db=new Database("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");db.initialize();
        CinemaService service=new CinemaService(db);
        service.saveMovie(null,"A [film]","Drama",120,"PG");service.saveMovie(null,"B film","Comedy",90,"G");
        CinemaPanel[] panel=new CinemaPanel[1];
        SwingUtilities.invokeAndWait(()->panel[0]=new CinemaPanel(service,false));
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        boolean[] loaded={false};
        while(!loaded[0] && System.nanoTime()<deadline){
            SwingUtilities.invokeAndWait(()->loaded[0]=find(panel[0],JTable.class).get(0).getRowCount()==2);
            if(!loaded[0])Thread.sleep(20);
        }
        assertTrue(loaded[0],"Background database load should populate the table");
        SwingUtilities.invokeAndWait(()->{
            JTable movies=find(panel[0],JTable.class).get(0);
            assertEquals("Movie",movies.getColumnName(1));
            assertEquals(Integer.class,movies.getColumnClass(3));
            TableRowSorter<?> sorter=(TableRowSorter<?>)movies.getRowSorter();
            sorter.setSortKeys(List.of(new RowSorter.SortKey(3,SortOrder.ASCENDING)));
            assertEquals(90,movies.getValueAt(0,3));
            JTextField search=find(panel[0],JTextField.class).get(0);
            search.setText("[film]");assertEquals(1,movies.getRowCount());assertEquals("A [film]",movies.getValueAt(0,1));
            search.setText("no matches");assertEquals(0,movies.getRowCount());
            search.setText("");assertEquals(2,movies.getRowCount());
            assertEquals(3,find(panel[0],JTable.class).size());
        });
    }
}
