package com.tradeback.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_data", indexes = {
        @Index(name = "idx_market_data_symbol", columnList = "symbol"),
        @Index(name = "idx_market_data_symbol_date", columnList = "symbol, date"),
        @Index(name = "idx_market_data_date", columnList = "date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 10)
    private String symbol;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime date;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal openPrice;

    @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal highPrice;

    @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal lowPrice;

    @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal closePrice;

    @PositiveOrZero
    @Column(nullable = false)
    private Long volume;

    // Convenience methods for backward compatibility
    public double getOpenPriceAsDouble() {
        return openPrice != null ? openPrice.doubleValue() : 0.0;
    }

    public double getHighPriceAsDouble() {
        return highPrice != null ? highPrice.doubleValue() : 0.0;
    }

    public double getLowPriceAsDouble() {
        return lowPrice != null ? lowPrice.doubleValue() : 0.0;
    }

    public double getClosePriceAsDouble() {
        return closePrice != null ? closePrice.doubleValue() : 0.0;
    }

    public void setOpenPrice(double price) {
        this.openPrice = BigDecimal.valueOf(price);
    }

    public void setHighPrice(double price) {
        this.highPrice = BigDecimal.valueOf(price);
    }

    public void setLowPrice(double price) {
        this.lowPrice = BigDecimal.valueOf(price);
    }

    public void setClosePrice(double price) {
        this.closePrice = BigDecimal.valueOf(price);
    }
}