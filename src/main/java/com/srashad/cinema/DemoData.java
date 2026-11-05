package com.srashad.cinema;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Entirely fictional examples, loaded only into the opt-in demo database. */
final class DemoData {
    private DemoData() { }
    static void seed(CinemaService service) throws Exception {
        if(!service.movies().isEmpty())return;
        long a=service.saveMovie(null,"Beyond the Blue","Adventure",112,"PG");
        long b=service.saveMovie(null,"Midnight on Platform Nine","Mystery",104,"PG-13");
        long c=service.saveMovie(null,"A Little More Sunshine","Animation",91,"G");
        var rooms=service.auditoriums();var tomorrow=LocalDate.now().plusDays(1);
        long s=service.saveScreening(null,a,rooms.get(0).id(),tomorrow.atTime(18,0),new BigDecimal("14.50"));
        service.saveScreening(null,b,rooms.get(1).id(),tomorrow.atTime(19,30),new BigDecimal("16.00"));
        service.saveScreening(null,c,rooms.get(2).id(),tomorrow.atTime(14,0),new BigDecimal("10.00"));
        service.book(s,"Demo guest",3);
    }
}
