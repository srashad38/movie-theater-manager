package com.srashad.cinema;

import com.srashad.cinema.Models.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/** Business rules and JDBC persistence. SQL values are always bound parameters. */
public final class CinemaService {
    public static final List<String> RATINGS = List.of("G", "PG", "PG-13", "R", "NC-17", "Not rated");
    public static final int TURNAROUND_MINUTES = 15;
    private final Database db;
    private final Clock clock;
    public CinemaService(Database db) { this(db, Clock.systemDefaultZone()); }
    CinemaService(Database db, Clock clock) { this.db = db; this.clock = clock; }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private static String required(String input, String label, int max) {
        String value = input == null ? "" : input.strip();
        if (value.isEmpty() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + " must contain 1–" + max + " printable characters.");
        return value;
    }
    @FunctionalInterface private interface Work<T> { T run(Connection c) throws SQLException; }
    private <T> T transaction(Work<T> work) throws SQLException {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try { T value = work.run(c); c.commit(); return value; }
            catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    private static int execute(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) { bind(p,args); return p.executeUpdate(); }
    }
    private static void bind(PreparedStatement p, Object... args) throws SQLException {
        for (int i=0;i<args.length;i++) p.setObject(i+1,args[i]);
    }
    private static long insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            bind(p,args); p.executeUpdate();
            try (ResultSet r=p.getGeneratedKeys()) { if(!r.next()) throw new SQLException("No identifier returned."); return r.getLong(1); }
        }
    }
    private static int count(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p=c.prepareStatement(sql)) { bind(p,args);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);} }
    }
    private static Movie readMovie(ResultSet r) throws SQLException {
        return new Movie(r.getLong("id"),r.getString("title"),r.getString("genre"),r.getInt("minutes"),r.getString("rating"));
    }
    private static Movie movie(Connection c,long id,boolean lock) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT * FROM movies WHERE id=?"+(lock?" FOR UPDATE":""))){
            p.setLong(1,id);try(ResultSet r=p.executeQuery()){
                if(!r.next()) throw new IllegalArgumentException("That movie no longer exists. Refresh the list.");
                return readMovie(r);
            }
        }
    }
    public List<Movie> movies() throws SQLException {
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement("SELECT * FROM movies ORDER BY LOWER(title),id");ResultSet r=p.executeQuery()){
            List<Movie> rows=new ArrayList<>();while(r.next()) rows.add(readMovie(r));return rows;
        }
    }
    public long saveMovie(Long id,String title,String genre,int minutes,String rating) throws SQLException {
        String name=required(title,"Title",160), category=required(genre,"Genre",60);
        if(minutes<1||minutes>600) throw new IllegalArgumentException("Running time must be between 1 and 600 minutes.");
        if(!RATINGS.contains(rating)) throw new IllegalArgumentException("Choose a supported rating.");
        return transaction(c->{
            if(id==null) return insert(c,"INSERT INTO movies(title,genre,minutes,rating) VALUES(?,?,?,?)",name,category,minutes,rating);
            Movie old=movie(c,id,true);
            if(old.minutes()!=minutes && count(c,"SELECT COUNT(*) FROM screenings WHERE movie_id=?",id)>0)
                throw new IllegalArgumentException("Running time cannot change while screenings exist. Remove unbooked screenings first.");
            execute(c,"UPDATE movies SET title=?,genre=?,minutes=?,rating=? WHERE id=?",name,category,minutes,rating,id);return id;
        });
    }
    public void deleteMovie(long id) throws SQLException {
        transaction(c->{ movie(c,id,true);
            if(count(c,"SELECT COUNT(*) FROM screenings WHERE movie_id=?",id)>0)
                throw new IllegalArgumentException("This movie has screenings. Keep it for booking history, or remove unbooked screenings first.");
            execute(c,"DELETE FROM movies WHERE id=?",id);return null;
        });
    }
    public List<Auditorium> auditoriums() throws SQLException {
        try(Connection c=db.open();Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT * FROM auditoriums ORDER BY id")){
            List<Auditorium> rows=new ArrayList<>();while(r.next()) rows.add(new Auditorium(r.getLong("id"),r.getString("name"),r.getInt("capacity")));return rows;
        }
    }
    private static final String SHOW_SELECT="""
        SELECT s.id,s.movie_id,m.title,s.auditorium_id,a.name,s.starts_at,m.minutes,s.price,a.capacity,
          COALESCE((SELECT SUM(b.quantity) FROM bookings b WHERE b.screening_id=s.id AND b.status='ACTIVE'),0) booked
        FROM screenings s JOIN movies m ON m.id=s.movie_id JOIN auditoriums a ON a.id=s.auditorium_id
        """;
    private static Screening readScreening(ResultSet r) throws SQLException {
        return new Screening(r.getLong(1),r.getLong(2),r.getString(3),r.getLong(4),r.getString(5),
            r.getTimestamp(6).toLocalDateTime(),r.getInt(7),r.getBigDecimal(8),r.getInt(9),r.getInt(10));
    }
    public List<Screening> screenings() throws SQLException {
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(SHOW_SELECT+" ORDER BY s.starts_at,s.id");ResultSet r=p.executeQuery()){
            List<Screening> rows=new ArrayList<>();while(r.next()) rows.add(readScreening(r));return rows;
        }
    }
    private static Screening screening(Connection c,long id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(SHOW_SELECT+" WHERE s.id=?")){
            p.setLong(1,id);try(ResultSet r=p.executeQuery()){
                if(!r.next()) throw new IllegalArgumentException("That screening no longer exists. Refresh the list.");
                return readScreening(r);
            }
        }
    }
    private static void lockScreening(Connection c,long id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT id FROM screenings WHERE id=? FOR UPDATE")){
            p.setLong(1,id);try(ResultSet r=p.executeQuery()){
                if(!r.next()) throw new IllegalArgumentException("That screening no longer exists. Refresh the list.");
            }
        }
    }
    public long saveScreening(Long id,long movieId,long roomId,LocalDateTime startsAt,BigDecimal price) throws SQLException {
        if(startsAt==null||!startsAt.isAfter(now())) throw new IllegalArgumentException("Choose a screening time in the future.");
        if(price==null||price.signum()<0||price.compareTo(new BigDecimal("9999.99"))>0||price.stripTrailingZeros().scale()>2)
            throw new IllegalArgumentException("Ticket price must be between $0.00 and $9,999.99, with at most two decimal places.");
        BigDecimal cost=price.setScale(2,RoundingMode.UNNECESSARY);
        return transaction(c->{
            Movie m=movie(c,movieId,true);
            Screening old=id==null?null:screening(c,id);
            // Lock rooms in stable order to serialize schedule conflict checks.
            TreeSet<Long> rooms=new TreeSet<>();rooms.add(roomId);if(old!=null)rooms.add(old.auditoriumId());
            for(long room:rooms)try(PreparedStatement p=c.prepareStatement("SELECT id FROM auditoriums WHERE id=? FOR UPDATE")){
                p.setLong(1,room);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose an existing auditorium.");}
            }
            if(id!=null){
                lockScreening(c,id);
                if(count(c,"SELECT COUNT(*) FROM bookings WHERE screening_id=?",id)>0)
                    throw new IllegalArgumentException("A screening with booking history cannot be edited. Create a new screening instead.");
            }
            LocalDateTime end=startsAt.plusMinutes(m.minutes()+TURNAROUND_MINUTES);
            try(PreparedStatement p=c.prepareStatement(SHOW_SELECT+" WHERE s.auditorium_id=? AND s.id<>?")){
                bind(p,roomId,id==null?-1:id);try(ResultSet r=p.executeQuery()){
                    while(r.next()){
                        Screening other=readScreening(r);
                        if(startsAt.isBefore(other.startsAt().plusMinutes(other.minutes()+TURNAROUND_MINUTES)) && end.isAfter(other.startsAt()))
                            throw new IllegalArgumentException("This room is occupied at that time. Allow 15 minutes between films.");
                    }
                }
            }
            if(id==null)return insert(c,"INSERT INTO screenings(movie_id,auditorium_id,starts_at,price) VALUES(?,?,?,?)",movieId,roomId,startsAt,cost);
            execute(c,"UPDATE screenings SET movie_id=?,auditorium_id=?,starts_at=?,price=? WHERE id=?",movieId,roomId,startsAt,cost,id);return id;
        });
    }
    public void deleteScreening(long id) throws SQLException {
        transaction(c->{lockScreening(c,id);
            if(count(c,"SELECT COUNT(*) FROM bookings WHERE screening_id=?",id)>0)
                throw new IllegalArgumentException("This screening has booking history and cannot be deleted.");
            execute(c,"DELETE FROM screenings WHERE id=?",id);return null;
        });
    }
    public long book(long screeningId,String customer,int quantity) throws SQLException {
        String name=required(customer,"Customer name",100);
        if(quantity<1||quantity>20)throw new IllegalArgumentException("Book between 1 and 20 seats at a time.");
        return transaction(c->{
            lockScreening(c,screeningId); // Prevent simultaneous requests from overselling a room.
            Screening s=screening(c,screeningId);
            if(!s.startsAt().isAfter(now()))throw new IllegalArgumentException("Bookings close when the screening starts.");
            if(quantity>s.available())throw new IllegalArgumentException("Only "+s.available()+" seats remain. Refresh and try again.");
            return insert(c,"INSERT INTO bookings(screening_id,customer,quantity,total,status) VALUES(?,?,?,?,'ACTIVE')",
                screeningId,name,quantity,s.price().multiply(BigDecimal.valueOf(quantity)));
        });
    }
    public void cancel(long bookingId) throws SQLException {
        transaction(c->{
            long showId;
            try(PreparedStatement p=c.prepareStatement("SELECT screening_id FROM bookings WHERE id=?")){
                p.setLong(1,bookingId);try(ResultSet r=p.executeQuery()){
                    if(!r.next())throw new IllegalArgumentException("That booking no longer exists.");showId=r.getLong(1);
                }
            }
            lockScreening(c,showId);
            if(!screening(c,showId).startsAt().isAfter(now()))throw new IllegalArgumentException("Past or started screenings cannot be cancelled.");
            if(execute(c,"UPDATE bookings SET status='CANCELLED' WHERE id=? AND status='ACTIVE'",bookingId)!=1)
                throw new IllegalArgumentException("This booking has already been cancelled.");
            return null;
        });
    }
    public List<Booking> bookings() throws SQLException {
        String sql="""
          SELECT b.id,b.screening_id,b.customer,m.title,a.name,s.starts_at,b.quantity,b.total,b.status
          FROM bookings b JOIN screenings s ON s.id=b.screening_id JOIN movies m ON m.id=s.movie_id
          JOIN auditoriums a ON a.id=s.auditorium_id ORDER BY b.id DESC
          """;
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){
            List<Booking> rows=new ArrayList<>();while(r.next()) rows.add(new Booking(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),
                r.getString(5),r.getTimestamp(6).toLocalDateTime(),r.getInt(7),r.getBigDecimal(8),r.getString(9)));return rows;
        }
    }
    public Dashboard dashboard() throws SQLException {
        try(Connection c=db.open()){
            int movies=count(c,"SELECT COUNT(*) FROM movies");
            int upcoming=count(c,"SELECT COUNT(*) FROM screenings WHERE starts_at>?",now());
            int tickets=count(c,"SELECT COALESCE(SUM(quantity),0) FROM bookings WHERE status='ACTIVE'");
            try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT COALESCE(SUM(total),0) FROM bookings WHERE status='ACTIVE'")){
                r.next();return new Dashboard(movies,upcoming,tickets,r.getBigDecimal(1));
            }
        }
    }
}
