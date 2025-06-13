package com.tradeback.config;

public final class ApplicationConstants {

    // Session constants
    public static final String USER_SESSION_KEY = "currentUser";

    // API constants
    public static final String API_BASE_PATH = "/api";
    public static final String AUTH_BASE_PATH = API_BASE_PATH + "/auth";

    // Cache constants
    public static final String CACHE_MARKET_DATA = "marketData";
    public static final String CACHE_SYMBOLS = "symbols";
    public static final String CACHE_INDICATORS = "indicators";

    // Validation constants
    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 50;
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 100;

    // Business constants
    public static final int DEFAULT_INDICATOR_PERIOD = 14;
    public static final int MAX_INDICATOR_PERIOD = 200;
    public static final int MIN_INDICATOR_PERIOD = 2;

    private ApplicationConstants() {
        // Utility class - prevent instantiation
    }
}