package com.stylenest.stylenest_backend.constant;

public final class OtpConstants {

    private OtpConstants() {
    }

    public static final int OTP_EXPIRY_MINUTES = 5;

    public static final int OTP_COOLDOWN_SECONDS = 60;

    public static final int MAX_OTP_ATTEMPTS = 3;

    public static final int MAX_DAILY_OTP_REQUESTS = 4;

    public static final int OTP_REQUEST_WINDOW_HOURS = 24;

}