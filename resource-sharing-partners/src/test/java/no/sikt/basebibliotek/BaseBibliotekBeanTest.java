package no.sikt.basebibliotek;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

public class BaseBibliotekBeanTest {

    public Clock initClock(LocalDate date) {
        return Clock.fixed(date.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    }

    @Test
    public void isOpenShoudBeFalseWhenInTheMiddleOfClosedDates() {
        Clock clock = initClock(LocalDate.of(2016, 5, 15));
        BaseBibliotekBean bean = new BaseBibliotekBean();
        bean.setStengtFra("2016-05-10");
        bean.setStengtTil("2016-05-20");
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));
        assertEquals(false, open);
    }

    @Test
    public void isOpenShoudBeFalseWhenInTheBeginningOfClosedDates() {
        Clock clock = initClock(LocalDate.of(2016, 5, 10));
        BaseBibliotekBean bean = new BaseBibliotekBean();
        bean.setStengtFra("2016-05-10");
        bean.setStengtTil("2016-05-20");
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));
        assertEquals(false, open);
    }

    @Test
    public void isOpenShoudBeFalseWhenAtTheEndOfClosedDates() {
        Clock clock = initClock(LocalDate.of(2016, 5, 20));
        BaseBibliotekBean bean = new BaseBibliotekBean();
        bean.setStengtFra("2016-05-10");
        bean.setStengtTil("2016-05-20");
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));
        assertEquals(false, open);
    }

    @Test
    public void isOpenShoudBeTrueWhenAfterTheEndOfClosedDates() {
        Clock clock = initClock(LocalDate.of(2016, 5, 25));
        BaseBibliotekBean bean = new BaseBibliotekBean();
        bean.setStengtFra("2016-05-10");
        bean.setStengtTil("2016-05-20");
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));
        assertEquals(true, open);
    }

    @Test
    public void isOpenShoudBeTrueWhenBeforeTheBeginningOfClosedDates() {
        Clock clock = initClock(LocalDate.of(2016, 5, 5));
        BaseBibliotekBean bean = new BaseBibliotekBean();
        bean.setStengtFra("2016-05-10");
        bean.setStengtTil("2016-05-20");
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));
        assertEquals(true, open);
    }

    @Test
    public void isOpenShoudBeTrueWhenNoDatesAreAvailable() {
        Clock clock = initClock(LocalDate.of(2016, 5, 5));
        BaseBibliotekBean bean = new BaseBibliotekBean();
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));
        assertEquals(true, open);
    }

    @Test
    public void isOpenShoudBWorkWhenOnlyBeginningDateIsAvailable() {
        Clock clock = initClock(LocalDate.of(2016, 5, 5));
        BaseBibliotekBean bean = new BaseBibliotekBean();
        bean.setStengtFra("2016-05-05");
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));
        assertEquals(false, open);
    }

    @Test
    public void isOpenShoudBWorkWhenOnlyEndDateIsAvailable2() {
        Clock clock = initClock(LocalDate.of(2016, 5, 5));
        BaseBibliotekBean bean = new BaseBibliotekBean();
        bean.setStengtTil("2016-05-06");
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));
        assertEquals(false, open);
    }

    @Test
    public void isOpenShoudBeFalseIfClosedTagHasValue() {

        Clock clock = initClock(LocalDate.of(2016, 5, 15)); //any date
        BaseBibliotekBean bean = new BaseBibliotekBean();
        bean.setStengt("X");
        boolean open = bean.isOpenAtDate(LocalDate.now(clock));

        assertEquals(false, open);
        bean.setStengt("S");

        assertEquals(false, open);

        bean.setStengt("5493885034");
        assertEquals(false, open);
    }


}
