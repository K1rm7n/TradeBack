package com.tradeback.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "listings", indexes = {
        @Index(name = "idx_listing_symbol", columnList = "symbol"),
        @Index(name = "idx_listing_exchange", columnList = "exchange"),
        @Index(name = "idx_listing_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Listing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20) // Увеличено с 10 до 20
    private String symbol;

    @Column(nullable = true, length = 500) // Увеличено с 255 до 500
    private String name;

    @Column(length = 100) // Увеличено с 50 до 100
    private String exchange;

    @Column(length = 50)
    private String assetType;

    private LocalDate ipoDate;

    private LocalDate delistingDate;

    @Column(length = 20)
    private String status;

    // Конструктор для создания из CSV (без id)
    public Listing(String symbol, String name, String exchange, String assetType,
                   String ipoDateStr, String delistingDateStr, String status) {
        this.symbol = symbol;
        // ИСПРАВЛЕНО: проверяем на null/empty и заменяем на symbol если нужно
        this.name = (name == null || name.trim().isEmpty() || "null".equals(name)) ? symbol : name.trim();
        this.exchange = exchange;
        this.assetType = assetType;
        this.ipoDate = parseDate(ipoDateStr);
        this.delistingDate = parseDate(delistingDateStr);
        this.status = status;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "null".equals(dateStr)) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}