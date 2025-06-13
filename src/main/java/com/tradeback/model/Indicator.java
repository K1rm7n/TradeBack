package com.tradeback.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "indicators", indexes = {
        @Index(name = "idx_indicator_symbol", columnList = "symbol"),
        @Index(name = "idx_indicator_type", columnList = "type"),
        @Index(name = "idx_indicator_symbol_type_period", columnList = "symbol, type, period")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Indicator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 10)
    private String symbol;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndicatorType type;

    @NotNull
    @Min(2)
    @Max(200)
    @Column(nullable = false)
    private Integer period;

    @NotBlank
    @Column(name = "time_interval", nullable = false, length = 10)
    private String interval;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    @Column(precision = 10, scale = 4)
    private BigDecimal value;

    // Дополнительные поля для комплексных индикаторов (MACD, Bollinger Bands, Stochastic)
    @Column(precision = 10, scale = 4)
    private BigDecimal secondaryValue; // Для MACD Signal, Bollinger Upper Band, Stochastic %D

    @Column(precision = 10, scale = 4)
    private BigDecimal tertiaryValue; // Для MACD Histogram, Bollinger Lower Band

    // Enum для расширенных типов индикаторов
    public enum IndicatorType {
        // Основные Moving Averages
        SMA("Simple Moving Average"),
        EMA("Exponential Moving Average"),
        WMA("Weighted Moving Average"),
        DEMA("Double Exponential Moving Average"),
        TEMA("Triple Exponential Moving Average"),
        TRIMA("Triangular Moving Average"),
        KAMA("Kaufman Adaptive Moving Average"),
        MAMA("MESA Adaptive Moving Average"),
        T3("T3 Moving Average"),

        // Oscillators
        RSI("Relative Strength Index"),
        STOCH("Stochastic Oscillator"),
        STOCHF("Stochastic Fast"),
        STOCHRSI("Stochastic RSI"),
        WILLR("Williams %R"),
        CCI("Commodity Channel Index"),
        CMO("Chande Momentum Oscillator"),
        ROC("Rate of Change"),
        ROCP("Rate of Change Percentage"),
        ROCR("Rate of Change Ratio"),
        MFI("Money Flow Index"),
        BOP("Balance of Power"),

        // MACD Family
        MACD("Moving Average Convergence Divergence"),
        MACDEXT("MACD with controllable MA type"),
        MACDFIX("MACD Fix 12/26"),
        PPO("Percentage Price Oscillator"),
        APO("Absolute Price Oscillator"),

        // Bollinger Bands and Channels
        BBANDS("Bollinger Bands"),
        KELTNER("Keltner Channel"),
        DONCHIAN("Donchian Channel"),

        // Volume Indicators
        AD("Chaikin A/D Line"),
        ADOSC("Chaikin A/D Oscillator"),
        OBV("On Balance Volume"),
        VWAP("Volume Weighted Average Price"),

        // Volatility Indicators
        ATR("Average True Range"),
        NATR("Normalized Average True Range"),
        TRANGE("True Range"),

        // Trend Indicators
        ADX("Average Directional Movement Index"),
        ADXR("Average Directional Movement Index Rating"),
        AROON("Aroon"),
        AROONOSC("Aroon Oscillator"),
        DX("Directional Movement Index"),
        MINUS_DI("Minus Directional Indicator"),
        PLUS_DI("Plus Directional Indicator"),
        MINUS_DM("Minus Directional Movement"),
        PLUS_DM("Plus Directional Movement"),
        SAR("Parabolic SAR"),
        TRIX("1-day Rate-Of-Change (ROC) of a Triple Smooth EMA"),

        // Pattern Recognition
        CDL2CROWS("Two Crows"),
        CDL3BLACKCROWS("Three Black Crows"),
        CDL3INSIDE("Three Inside Up/Down"),
        CDL3LINESTRIKE("Three-Line Strike"),
        CDL3OUTSIDE("Three Outside Up/Down"),
        CDL3STARSINSOUTH("Three Stars In The South"),
        CDL3WHITESOLDIERS("Three Advancing White Soldiers"),
        CDLABANDONEDBABY("Abandoned Baby"),
        CDLBELTHOLD("Belt-hold"),
        CDLBREAKAWAY("Breakaway"),
        CDLCLOSINGMARUBOZU("Closing Marubozu"),
        CDLCONCEALBABYSWALL("Concealing Baby Swallow"),
        CDLCOUNTERATTACK("Counterattack"),
        CDLDARKCLOUDCOVER("Dark Cloud Cover"),
        CDLDOJI("Doji"),
        CDLDOJISTAR("Doji Star"),
        CDLDRAGONFLYDOJI("Dragonfly Doji"),
        CDLENGULFING("Engulfing Pattern"),
        CDLEVENINGDOJISTAR("Evening Doji Star"),
        CDLEVENINGSTAR("Evening Star"),
        CDLGAPSIDESIDEWHITE("Up/Down-gap side-by-side white lines"),
        CDLGRAVESTONEDOJI("Gravestone Doji"),
        CDLHAMMER("Hammer"),
        CDLHANGINGMAN("Hanging Man"),
        CDLHARAMI("Harami Pattern"),
        CDLHARAMICROSS("Harami Cross Pattern"),
        CDLHIGHWAVE("High-Wave Candle"),
        CDLHIKKAKE("Hikkake Pattern"),
        CDLHIKKAKEMOD("Modified Hikkake Pattern"),
        CDLHOMINGPIGEON("Homing Pigeon"),
        CDLIDENTICAL3CROWS("Identical Three Crows"),
        CDLINNECK("In-Neck Pattern"),
        CDLINVERTEDHAMMER("Inverted Hammer"),
        CDLKICKING("Kicking"),
        CDLKICKINGBYLENGTH("Kicking - bull/bear determined by the longer marubozu"),
        CDLLADDERBOTTOM("Ladder Bottom"),
        CDLLONGLEGGEDDOJI("Long Legged Doji"),
        CDLLONGLINE("Long Line Candle"),
        CDLMARUBOZU("Marubozu"),
        CDLMATCHINGLOW("Matching Low"),
        CDLMATHOLD("Mat Hold"),
        CDLMORNINGDOJISTAR("Morning Doji Star"),
        CDLMORNINGSTAR("Morning Star"),
        CDLONNECK("On-Neck Pattern"),
        CDLPIERCING("Piercing Pattern"),
        CDLRICKSHAWMAN("Rickshaw Man"),
        CDLRISEFALL3METHODS("Rising/Falling Three Methods"),
        CDLSEPARATINGLINES("Separating Lines"),
        CDLSHOOTINGSTAR("Shooting Star"),
        CDLSHORTLINE("Short Line Candle"),
        CDLSPINNINGTOP("Spinning Top"),
        CDLSTALLEDPATTERN("Stalled Pattern"),
        CDLSTICKSANDWICH("Stick Sandwich"),
        CDLTAKURI("Takuri (Dragonfly Doji with very long lower shadow)"),
        CDLTASUKIGAP("Tasuki Gap"),
        CDLTHRUSTING("Thrusting Pattern"),
        CDLTRISTAR("Tristar Pattern"),
        CDLUNIQUE3RIVER("Unique 3 River"),
        CDLUPSIDEGAP2CROWS("Upside Gap Two Crows"),
        CDLXSIDEGAP3METHODS("Upside/Downside Gap Three Methods"),

        // Price Transform
        AVGPRICE("Average Price"),
        MEDPRICE("Median Price"),
        TYPPRICE("Typical Price"),
        WCLPRICE("Weighted Close Price"),

        // Cycle Indicators
        HT_DCPERIOD("Hilbert Transform - Dominant Cycle Period"),
        HT_DCPHASE("Hilbert Transform - Dominant Cycle Phase"),
        HT_PHASOR("Hilbert Transform - Phasor Components"),
        HT_SINE("Hilbert Transform - SineWave"),
        HT_TRENDMODE("Hilbert Transform - Trend vs Cycle Mode"),

        // Math Transform
        ACOS("Vector Trigonometric ACos"),
        ASIN("Vector Trigonometric ASin"),
        ATAN("Vector Trigonometric ATan"),
        CEIL("Vector Ceil"),
        COS("Vector Trigonometric Cos"),
        COSH("Vector Trigonometric Cosh"),
        EXP("Vector Arithmetic Exp"),
        FLOOR("Vector Floor"),
        LN("Vector Log Natural"),
        LOG10("Vector Log10"),
        SIN("Vector Trigonometric Sin"),
        SINH("Vector Trigonometric Sinh"),
        SQRT("Vector Square Root"),
        TAN("Vector Trigonometric Tan"),
        TANH("Vector Trigonometric Tanh");

        private final String description;

        IndicatorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        // Метод для получения индикаторов по категориям
        public static IndicatorType[] getMovingAverages() {
            return new IndicatorType[]{SMA, EMA, WMA, DEMA, TEMA, TRIMA, KAMA, MAMA, T3};
        }

        public static IndicatorType[] getOscillators() {
            return new IndicatorType[]{RSI, STOCH, STOCHF, STOCHRSI, WILLR, CCI, CMO, ROC, ROCP, ROCR, MFI, BOP};
        }

        public static IndicatorType[] getMACDFamily() {
            return new IndicatorType[]{MACD, MACDEXT, MACDFIX, PPO, APO};
        }

        public static IndicatorType[] getBandsAndChannels() {
            return new IndicatorType[]{BBANDS, KELTNER, DONCHIAN};
        }

        public static IndicatorType[] getVolumeIndicators() {
            return new IndicatorType[]{AD, ADOSC, OBV, VWAP};
        }

        public static IndicatorType[] getVolatilityIndicators() {
            return new IndicatorType[]{ATR, NATR, TRANGE};
        }

        public static IndicatorType[] getTrendIndicators() {
            return new IndicatorType[]{ADX, ADXR, AROON, AROONOSC, DX, MINUS_DI, PLUS_DI, MINUS_DM, PLUS_DM, SAR, TRIX};
        }

        // Проверяет, является ли индикатор комплексным (возвращает несколько значений)
        public boolean isComplexIndicator() {
            return this == MACD || this == MACDEXT || this == BBANDS || this == STOCH ||
                    this == STOCHF || this == AROON || this == HT_PHASOR || this == HT_SINE;
        }

        // Проверяет, требует ли индикатор дополнительные параметры
        public boolean requiresAdditionalParams() {
            return getMACDFamily().length > 0 && java.util.Arrays.asList(getMACDFamily()).contains(this) ||
                    this == STOCH || this == STOCHF || this == SAR || this == ADXR;
        }
    }

    // Convenience methods для работы с BigDecimal
    public double getValueAsDouble() {
        return value != null ? value.doubleValue() : 0.0;
    }

    public void setValue(double value) {
        this.value = BigDecimal.valueOf(value);
    }

    public double getSecondaryValueAsDouble() {
        return secondaryValue != null ? secondaryValue.doubleValue() : 0.0;
    }

    public void setSecondaryValue(double value) {
        this.secondaryValue = BigDecimal.valueOf(value);
    }

    public double getTertiaryValueAsDouble() {
        return tertiaryValue != null ? tertiaryValue.doubleValue() : 0.0;
    }

    public void setTertiaryValue(double value) {
        this.tertiaryValue = BigDecimal.valueOf(value);
    }

    // Методы для получения описательных названий значений в зависимости от типа индикатора
    public String getValueName() {
        switch (this.type) {
            case MACD:
                return "MACD Line";
            case BBANDS:
                return "Middle Band (SMA)";
            case STOCH:
            case STOCHF:
                return "%K";
            case RSI:
                return "RSI";
            default:
                return type.getDescription();
        }
    }

    public String getSecondaryValueName() {
        switch (this.type) {
            case MACD:
                return "Signal Line";
            case BBANDS:
                return "Upper Band";
            case STOCH:
            case STOCHF:
                return "%D";
            default:
                return "Secondary Value";
        }
    }

    public String getTertiaryValueName() {
        switch (this.type) {
            case MACD:
                return "Histogram";
            case BBANDS:
                return "Lower Band";
            default:
                return "Tertiary Value";
        }
    }
}