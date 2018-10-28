package lithium.openstud.driver.core;


import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;

enum Type {
    DOABLE, RESERVED, LESSON
}
public class Event {
    private String description;
    private String teacher; //May come in handy in the future
    private String where; //for the future
    private LocalDateTime start;
    private LocalDateTime end;
    private LocalDate startReservations;
    private LocalDate endReservations;
    private Type type;



    public Event(String description, LocalDateTime start, LocalDateTime end, Type type){
        this.description = description;
        this.start = start;
        this.end = end;
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime when) {
        this.start = when;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public LocalDate getStartReservations() {
        return startReservations;
    }

    public void setStartReservations(LocalDate startReservations) {
        this.startReservations = startReservations;
    }

    public LocalDate getEndReservations() {
        return endReservations;
    }

    public void setEndReservations(LocalDate endReservations) {
        this.endReservations = endReservations;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public String getWhere() {
        return where;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    @Override
    public String toString() {
        return "Event{" +
                "description='" + description + '\'' +
                ", teacher='" + teacher + '\'' +
                ", where='" + where + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", startReservations=" + startReservations +
                ", endReservations=" + endReservations +
                ", type=" + type +
                '}';
    }
}