package com.srashad.cinema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable snapshots passed from the database layer to the interface. */
public final class Models {
    private Models() { }
    public record Movie(long id, String title, String genre, int minutes, String rating) {
        @Override public String toString() { return title + " (" + minutes + " min)"; }
    }
    public record Auditorium(long id, String name, int capacity) {
        @Override public String toString() { return name + " · " + capacity + " seats"; }
    }
    public record Screening(long id, long movieId, String title, long auditoriumId,
                            String room, LocalDateTime startsAt, int minutes,
                            BigDecimal price, int capacity, int booked) {
        public int available() { return capacity - booked; }
    }
    public record Booking(long id, long screeningId, String customer, String title,
                          String room, LocalDateTime startsAt, int quantity,
                          BigDecimal total, String status) { }
    public record Dashboard(int movies, int upcoming, int tickets, BigDecimal sales) { }
}
