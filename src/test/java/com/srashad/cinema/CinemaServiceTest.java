package com.srashad.cinema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CinemaServiceTest {
    private Database db;
    private CinemaService service;
    private long film,room;
    private final LocalDateTime start=LocalDateTime.of(2030,6,2,18,0);
    private final Clock clock=Clock.fixed(Instant.parse("2030-06-01T12:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void setup() throws Exception {
        db=new Database("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");db.initialize();
        service=new CinemaService(db,clock);film=service.saveMovie(null,"Test film","Drama",100,"PG");room=service.auditoriums().get(2).id();
    }
    private long show() throws SQLException{return service.saveScreening(null,film,room,start,new BigDecimal("12.50"));}
    @Test void initializesIdempotently() throws Exception{db.initialize();assertEquals(3,service.auditoriums().size());assertEquals(1,service.movies().size());}
    @Test void movieCrudSupportsQuotesAndUnicode() throws Exception {
        long id=service.saveMovie(null,"L'été d'O'Brien","Drama",94,"PG-13");
        service.saveMovie(id,"Updated film","Comedy",95,"PG");
        assertTrue(service.movies().stream().anyMatch(m->m.id()==id&&m.minutes()==95));service.deleteMovie(id);assertEquals(1,service.movies().size());
    }
    @Test void rejectsInvalidMovieFields(){
        assertThrows(IllegalArgumentException.class,()->service.saveMovie(null," ","Drama",90,"PG"));
        assertThrows(IllegalArgumentException.class,()->service.saveMovie(null,"A"," ",90,"PG"));
        assertThrows(IllegalArgumentException.class,()->service.saveMovie(null,"A","Drama",0,"PG"));
        assertThrows(IllegalArgumentException.class,()->service.saveMovie(null,"A","Drama",601,"PG"));
        assertThrows(IllegalArgumentException.class,()->service.saveMovie(null,"A","Drama",90,"BAD"));
        assertThrows(IllegalArgumentException.class,()->service.saveMovie(null,"A\nB","Drama",90,"PG"));
    }
    @Test void scheduledMovieCannotBeDeletedOrChangeDuration() throws Exception {
        show();assertThrows(IllegalArgumentException.class,()->service.deleteMovie(film));
        assertThrows(IllegalArgumentException.class,()->service.saveMovie(film,"Test film","Drama",110,"PG"));
        service.saveMovie(film,"New title","Drama",100,"PG");assertEquals("New title",service.screenings().get(0).title());
    }
    @Test void rejectsOverlapIncludingTurnaround() throws Exception {
        show();assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,film,room,start.plusMinutes(110),BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,film,room,start.minusMinutes(110),BigDecimal.TEN));assertEquals(1,service.screenings().size());
    }
    @Test void allowsExactTurnaroundAndDifferentRoom() throws Exception {
        show();service.saveScreening(null,film,room,start.plusMinutes(115),BigDecimal.TEN);
        service.saveScreening(null,film,service.auditoriums().get(0).id(),start,BigDecimal.ZERO);assertEquals(3,service.screenings().size());
    }
    @Test void screeningEditAndRemoval() throws Exception {
        long id=show();service.saveScreening(id,film,room,start.plusHours(1),new BigDecimal("15.00"));
        assertEquals(start.plusHours(1),service.screenings().get(0).startsAt());service.deleteScreening(id);assertTrue(service.screenings().isEmpty());
    }
    @Test void rejectsBadDatePriceAndReferences(){
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,film,room,LocalDateTime.of(2029,1,1,1,0),BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,film,room,null,BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,film,room,start,new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,film,room,start,new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,film,room,start,new BigDecimal("10000")));
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,9999,room,start,BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(null,film,9999,start,BigDecimal.TEN));
    }
    @Test void bookingUsesScreeningPriceAndReducesCapacity() throws Exception {
        long id=show();service.book(id,"O'Brien",3);var b=service.bookings().get(0);
        assertEquals(new BigDecimal("37.50"),b.total());assertEquals(21,service.screenings().get(0).available());assertEquals(3,service.dashboard().tickets());
        assertEquals(new BigDecimal("37.50"),service.dashboard().sales());assertEquals(1,service.dashboard().upcoming());
    }
    @Test void preventsOversellingAndRollsBack() throws Exception {
        long id=show();service.book(id,"Guest",20);assertThrows(IllegalArgumentException.class,()->service.book(id,"Guest 2",5));
        assertEquals(4,service.screenings().get(0).available());assertEquals(1,service.bookings().size());
    }
    @Test void validatesBookingInputs() throws Exception {
        long id=show();assertThrows(IllegalArgumentException.class,()->service.book(id," ",1));
        assertThrows(IllegalArgumentException.class,()->service.book(id,"Guest",0));assertThrows(IllegalArgumentException.class,()->service.book(id,"Guest",21));
        assertThrows(IllegalArgumentException.class,()->service.book(99999,"Guest",1));assertTrue(service.bookings().isEmpty());
    }
    @Test void cancellationReleasesSeatsAndPreservesHistory() throws Exception {
        long id=show();long booking=service.book(id,"Guest",4);service.cancel(booking);
        assertEquals(24,service.screenings().get(0).available());assertEquals("CANCELLED",service.bookings().get(0).status());
        assertEquals(0,service.dashboard().tickets());assertEquals(0,service.dashboard().sales().signum());
        assertThrows(IllegalArgumentException.class,()->service.cancel(booking));assertThrows(IllegalArgumentException.class,()->service.cancel(99999));
    }
    @Test void bookingHistoryProtectsScreening() throws Exception {
        long id=show();long booking=service.book(id,"Guest",1);service.cancel(booking);
        assertThrows(IllegalArgumentException.class,()->service.deleteScreening(id));
        assertThrows(IllegalArgumentException.class,()->service.saveScreening(id,film,room,start.plusHours(2),BigDecimal.ONE));
    }
    @Test void cannotBookOrCancelAfterStart() throws Exception {
        long id=show();long booking=service.book(id,"Guest",1);
        CinemaService later=new CinemaService(db,Clock.fixed(start.toInstant(ZoneOffset.UTC),ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class,()->later.book(id,"Late guest",1));assertThrows(IllegalArgumentException.class,()->later.cancel(booking));assertEquals(0,later.dashboard().upcoming());
    }
    @Test void simultaneousBookingsCannotOversell() throws Exception {
        long id=show();ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{
            Callable<Boolean> task=()->{gate.await();try{service.book(id,"Concurrent guest",20);return true;}catch(IllegalArgumentException e){return false;}};
            Future<Boolean> a=pool.submit(task),b=pool.submit(task);gate.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(4,service.screenings().get(0).available());assertEquals(1,service.bookings().size());
        }finally{pool.shutdownNow();}
    }
    @Test void simultaneousSchedulesCannotOverlap() throws Exception {
        long otherFilm=service.saveMovie(null,"Second film","Drama",100,"PG");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{
            Callable<Boolean> one=()->{gate.await();try{service.saveScreening(null,film,room,start,BigDecimal.TEN);return true;}catch(IllegalArgumentException e){return false;}};
            Callable<Boolean> two=()->{gate.await();try{service.saveScreening(null,otherFilm,room,start.plusMinutes(5),BigDecimal.TEN);return true;}catch(IllegalArgumentException e){return false;}};
            Future<Boolean>a=pool.submit(one),b=pool.submit(two);gate.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,service.screenings().size());
        }finally{pool.shutdownNow();}
    }
    @Test void dataSurvivesReopening(@TempDir Path dir) throws Exception {
        Database file=Database.local(dir);file.initialize();CinemaService first=new CinemaService(file,clock);
        long id=first.saveMovie(null,"Persistent film","Drama",90,"G");
        long show=first.saveScreening(null,id,first.auditoriums().get(0).id(),start,BigDecimal.TEN);first.book(show,"Guest",2);
        Database reopened=Database.local(dir);reopened.initialize();CinemaService second=new CinemaService(reopened,clock);
        assertEquals(1,second.movies().size());assertEquals(1,second.bookings().size());assertEquals(78,second.screenings().get(0).available());
    }
    @Test void sqlTextIsStoredAsData() throws Exception {
        long id=service.saveMovie(null,"Robert'); DROP TABLE movies;--","Drama",90,"PG");assertEquals(2,service.movies().size());
        long s=service.saveScreening(null,id,room,start,BigDecimal.TEN);service.book(s,"'); DROP TABLE bookings;--",1);assertEquals(1,service.bookings().size());
    }
}
