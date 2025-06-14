package com.tradeback.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

import java.util.Arrays;
import java.util.List;

@Data
public class IndicatorRequest {

    // Список индикаторов, которые не используют период
    private static final List<String> NO_PERIOD_INDICATORS = Arrays.asList(
            "MACD", "STOCH", "SAR", "VWAP", "OBV"
    );

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Interval is required")
    private String interval;

    @NotBlank(message = "First indicator type is required")
    private String firstIndicatorType;

    @NotNull(message = "First period is required")
    private Integer firstPeriod;

    @NotBlank(message = "Second indicator type is required")
    private String secondIndicatorType;

    @NotNull(message = "Second period is required")
    private Integer secondPeriod;

    @NotBlank(message = "Third indicator type is required")
    private String thirdIndicatorType;

    @NotNull(message = "Third period is required")
    private Integer thirdPeriod;

    // Пользовательская валидация для первого периода
    @AssertTrue(message = "First period must be between 2 and 200 for indicators that use period")
    public boolean isFirstPeriodValid() {
        if (firstIndicatorType == null || firstPeriod == null) {
            return false;
        }
        if (NO_PERIOD_INDICATORS.contains(firstIndicatorType.toUpperCase())) {
            return true; // Период игнорируется для этих индикаторов
        }
        return firstPeriod >= 2 && firstPeriod <= 200;
    }

    // Пользовательская валидация для второго периода
    @AssertTrue(message = "Second period must be between 2 and 200 for indicators that use period")
    public boolean isSecondPeriodValid() {
        if (secondIndicatorType == null || secondPeriod == null) {
            return false;
        }
        if (NO_PERIOD_INDICATORS.contains(secondIndicatorType.toUpperCase())) {
            return true; // Период игнорируется для этих индикаторов
        }
        return secondPeriod >= 2 && secondPeriod <= 200;
    }

    // Пользовательская валидация для третьего периода
    @AssertTrue(message = "Third period must be between 2 and 200 for indicators that use period")
    public boolean isThirdPeriodValid() {
        if (thirdIndicatorType == null || thirdPeriod == null) {
            return false;
        }
        if (NO_PERIOD_INDICATORS.contains(thirdIndicatorType.toUpperCase())) {
            return true; // Период игнорируется для этих индикаторов
        }
        return thirdPeriod >= 2 && thirdPeriod <= 200;
    }

    // Валидация для VWAP - только с intraday интервалами
    @AssertTrue(message = "VWAP indicator requires intraday interval (1min, 5min, 15min, 30min, 60min)")
    public boolean isVWAPIntervalValid() {
        List<String> vwapCompatibleIntervals = Arrays.asList("1min", "5min", "15min", "30min", "60min");

        // Проверяем, используется ли VWAP
        boolean hasVWAP = "VWAP".equalsIgnoreCase(firstIndicatorType) ||
                "VWAP".equalsIgnoreCase(secondIndicatorType) ||
                "VWAP".equalsIgnoreCase(thirdIndicatorType);

        // Если VWAP используется, проверяем совместимость интервала
        if (hasVWAP && interval != null) {
            return vwapCompatibleIntervals.contains(interval);
        }

        // Если VWAP не используется, валидация пройдена
        return true;
    }
}