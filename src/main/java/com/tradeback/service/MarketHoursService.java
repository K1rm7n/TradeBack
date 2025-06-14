package com.tradeback.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
public class MarketHoursService {

    private static final ZoneId EASTERN_TIME = ZoneId.of("America/New_York");

    // US Stock Market Holidays 2025
    private static final Set<LocalDate> HOLIDAYS_2025 = Set.of(
            LocalDate.of(2025, 1, 1),   // New Year's Day
            LocalDate.of(2025, 1, 9),   // National Day of Mourning (Jimmy Carter)
            LocalDate.of(2025, 1, 20),  // Martin Luther King Jr. Day
            LocalDate.of(2025, 2, 17),  // Presidents' Day
            LocalDate.of(2025, 4, 18),  // Good Friday
            LocalDate.of(2025, 5, 26),  // Memorial Day
            LocalDate.of(2025, 6, 19),  // Juneteenth
            LocalDate.of(2025, 7, 4),   // Independence Day
            LocalDate.of(2025, 9, 1),   // Labor Day
            LocalDate.of(2025, 11, 27), // Thanksgiving
            LocalDate.of(2025, 12, 25)  // Christmas
    );

    // Early close days (1:00 PM ET)
    private static final Set<LocalDate> EARLY_CLOSE_DAYS_2025 = Set.of(
            LocalDate.of(2025, 7, 3),   // July 3 (day before Independence Day)
            LocalDate.of(2025, 11, 28), // Black Friday
            LocalDate.of(2025, 12, 24)  // Christmas Eve
    );

    /**
     * Проверяет, открыт ли рынок сейчас
     */
    public boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(EASTERN_TIME);
        return isMarketOpen(now);
    }

    /**
     * Проверяет, был ли рынок открыт в указанное время
     */
    public boolean isMarketOpen(ZonedDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        LocalTime time = dateTime.toLocalTime();
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        // Проверяем выходные
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        // Проверяем праздники
        if (HOLIDAYS_2025.contains(date)) {
            return false;
        }

        // Проверяем время работы
        LocalTime marketOpen = LocalTime.of(9, 30);  // 9:30 AM
        LocalTime marketClose = EARLY_CLOSE_DAYS_2025.contains(date)
                ? LocalTime.of(13, 0)  // 1:00 PM (early close)
                : LocalTime.of(16, 0); // 4:00 PM (regular close)

        return time.isAfter(marketOpen) && time.isBefore(marketClose);
    }

    /**
     * Возвращает статус рынка с подробной информацией
     */
    public MarketStatus getMarketStatus() {
        ZonedDateTime now = ZonedDateTime.now(EASTERN_TIME);
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();
        DayOfWeek dayOfWeek = today.getDayOfWeek();

        MarketStatus status = new MarketStatus();
        status.setCurrentTime(now);
        status.setIsOpen(false);

        // Проверяем выходные
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            status.setStatus("WEEKEND");
            status.setMessage("US Stock Markets are closed on weekends");
            status.setNextOpenTime(getNextTradingDay(today).atTime(9, 30).atZone(EASTERN_TIME));
            return status;
        }

        // Проверяем праздники
        if (HOLIDAYS_2025.contains(today)) {
            status.setStatus("HOLIDAY");
            status.setMessage("US Stock Markets are closed for holiday: " + getHolidayName(today));
            status.setNextOpenTime(getNextTradingDay(today).atTime(9, 30).atZone(EASTERN_TIME));
            return status;
        }

        // Проверяем время работы
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime marketClose = EARLY_CLOSE_DAYS_2025.contains(today)
                ? LocalTime.of(13, 0)
                : LocalTime.of(16, 0);

        if (currentTime.isBefore(marketOpen)) {
            status.setStatus("PRE_MARKET");
            status.setMessage("Market opens at " + marketOpen.format(DateTimeFormatter.ofPattern("h:mm a")) + " ET");
            status.setNextOpenTime(today.atTime(marketOpen).atZone(EASTERN_TIME));
        } else if (currentTime.isAfter(marketClose)) {
            status.setStatus("AFTER_HOURS");
            status.setMessage("Market closed at " + marketClose.format(DateTimeFormatter.ofPattern("h:mm a")) + " ET");
            status.setNextOpenTime(getNextTradingDay(today).atTime(9, 30).atZone(EASTERN_TIME));
        } else {
            status.setStatus("OPEN");
            status.setMessage("Market is currently open");
            status.setIsOpen(true);
            status.setNextCloseTime(today.atTime(marketClose).atZone(EASTERN_TIME));
        }

        return status;
    }

    /**
     * Возвращает последний торговый день
     */
    public LocalDate getLastTradingDay() {
        LocalDate date = LocalDate.now(EASTERN_TIME);

        // Если сегодня торговый день и рынок еще открыт или недавно закрылся, возвращаем сегодня
        ZonedDateTime now = ZonedDateTime.now(EASTERN_TIME);
        if (isTradingDay(date) && now.toLocalTime().isAfter(LocalTime.of(9, 30))) {
            return date;
        }

        // Иначе ищем предыдущий торговый день
        date = date.minusDays(1);
        while (!isTradingDay(date)) {
            date = date.minusDays(1);
        }
        return date;
    }

    /**
     * Возвращает следующий торговый день
     */
    public LocalDate getNextTradingDay() {
        return getNextTradingDay(LocalDate.now(EASTERN_TIME));
    }

    private LocalDate getNextTradingDay(LocalDate fromDate) {
        LocalDate date = fromDate.plusDays(1);
        while (!isTradingDay(date)) {
            date = date.plusDays(1);
        }
        return date;
    }

    /**
     * Проверяет, является ли день торговым
     */
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY
                && dayOfWeek != DayOfWeek.SUNDAY
                && !HOLIDAYS_2025.contains(date);
    }

    /**
     * Возвращает рекомендацию по использованию API
     */
    public String getApiRecommendation() {
        MarketStatus status = getMarketStatus();

        switch (status.getStatus()) {
            case "OPEN":
                return "Market is open - real-time data available";
            case "AFTER_HOURS":
                return "Market closed - using last trading day data";
            case "PRE_MARKET":
                return "Pre-market - using previous trading day data";
            case "WEEKEND":
                return "Weekend - using data from " + getLastTradingDay().format(DateTimeFormatter.ofPattern("EEEE, MMM d"));
            case "HOLIDAY":
                return "Holiday - using data from " + getLastTradingDay().format(DateTimeFormatter.ofPattern("EEEE, MMM d"));
            default:
                return "Using historical data";
        }
    }

    private String getHolidayName(LocalDate date) {
        if (date.equals(LocalDate.of(2025, 1, 1))) return "New Year's Day";
        if (date.equals(LocalDate.of(2025, 1, 9))) return "National Day of Mourning";
        if (date.equals(LocalDate.of(2025, 1, 20))) return "Martin Luther King Jr. Day";
        if (date.equals(LocalDate.of(2025, 2, 17))) return "Presidents' Day";
        if (date.equals(LocalDate.of(2025, 4, 18))) return "Good Friday";
        if (date.equals(LocalDate.of(2025, 5, 26))) return "Memorial Day";
        if (date.equals(LocalDate.of(2025, 6, 19))) return "Juneteenth";
        if (date.equals(LocalDate.of(2025, 7, 4))) return "Independence Day";
        if (date.equals(LocalDate.of(2025, 9, 1))) return "Labor Day";
        if (date.equals(LocalDate.of(2025, 11, 27))) return "Thanksgiving";
        if (date.equals(LocalDate.of(2025, 12, 25))) return "Christmas";
        return "Holiday";
    }

    // Внутренний класс для статуса рынка
    public static class MarketStatus {
        private ZonedDateTime currentTime;
        private String status;
        private String message;
        private boolean isOpen;
        private ZonedDateTime nextOpenTime;
        private ZonedDateTime nextCloseTime;

        // Getters and Setters
        public ZonedDateTime getCurrentTime() { return currentTime; }
        public void setCurrentTime(ZonedDateTime currentTime) { this.currentTime = currentTime; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public boolean getIsOpen() { return isOpen; }
        public void setIsOpen(boolean isOpen) { this.isOpen = isOpen; }

        public ZonedDateTime getNextOpenTime() { return nextOpenTime; }
        public void setNextOpenTime(ZonedDateTime nextOpenTime) { this.nextOpenTime = nextOpenTime; }

        public ZonedDateTime getNextCloseTime() { return nextCloseTime; }
        public void setNextCloseTime(ZonedDateTime nextCloseTime) { this.nextCloseTime = nextCloseTime; }
    }
}