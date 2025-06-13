package com.tradeback.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "signals", indexes = {
        @Index(name = "idx_signal_symbol", columnList = "symbol"),
        @Index(name = "idx_signal_symbol_date", columnList = "symbol, date"),
        @Index(name = "idx_signal_type", columnList = "type"),
        @Index(name = "idx_signal_date", columnList = "date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Signal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 10)
    private String symbol;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @DecimalMin(value = "0.0", inclusive = true)
    @Column(precision = 10, scale = 4)
    private BigDecimal price;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime date;

    // Enum for signal types
    public enum SignalType {
        BUY, SELL, HOLD, STRONG_BUY, STRONG_SELL, UNKNOWN
    }

    // Convenience methods for backward compatibility
    public double getPriceAsDouble() {
        return price != null ? price.doubleValue() : 0.0;
    }

    public void setPrice(double price) {
        this.price = BigDecimal.valueOf(price);
    }

    public String getTypeAsString() {
        return type != null ? type.name() : SignalType.UNKNOWN.name();
    }

    public void setType(String typeStr) {
        try {
            this.type = SignalType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            this.type = SignalType.UNKNOWN;
        }
    }
}