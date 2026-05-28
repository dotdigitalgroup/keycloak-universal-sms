package com.github.keycloak.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * A zero-dependency, configurable SMS sender that talks to any REST/HTTP-based
 * SMS gateway.
 *
 * <p>The URL, HTTP method, auth header, and payload are all driven by Keycloak
 * Admin Console configuration, so no code changes are needed when switching
 * providers.
 *
 * <h3>Payload template placeholders</h3>
 * <ul>
 *   <li>{@code {phoneNumber}} — replaced with the E.164 phone number</li>
 *   <li>{@code {code}}        — replaced with the OTP</li>
 * </ul>
 *
 * <h3>Example payload templates</h3>
 * <pre>
 * Twilio (POST):
 *   To={phoneNumber}&amp;From=%2B15005550006&amp;Body=Your+OTP+is+{code}
 *
 * Generic JSON (POST):
 *   {"to":"{phoneNumber}","message":"Your OTP is {code}"}
 *
 * GET-based gateway:
 *   Leave payload empty; put params in the URL itself:
 *   https://sms.example.com/send?to={phoneNumber}&amp;msg={code}&amp;key=SECRET
 * </pre>
 */
public class GenericHttpSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(GenericHttpSmsSender.class);

    /** Connection / read timeout in milliseconds. */
    private static final int TIMEOUT_MS = 10_000;

    private final String apiUrl;
    private final String httpMethod;
    private final String authHeader;
    private final String payloadTemplate;

    /**
     * @param apiUrl          full gateway URL (may contain {@code {phoneNumber}}
     *                        and {@code {code}} for GET gateways)
     * @param httpMethod      {@code "GET"} or {@code "POST"}
     * @param authHeader      value of the {@code Authorization} header, or
     *                        {@code null} / empty to omit the header
     * @param payloadTemplate request body template; ignored for GET requests
     */
    public GenericHttpSmsSender(String apiUrl,
                                String httpMethod,
                                String authHeader,
                                String payloadTemplate) {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("SMS API URL must not be blank");
        }
        this.apiUrl          = apiUrl.trim();
        this.httpMethod      = (httpMethod == null || httpMethod.trim().isEmpty())
                               ? "POST" : httpMethod.trim().toUpperCase();
        this.authHeader      = authHeader;
        this.payloadTemplate = payloadTemplate;
    }

    @Override
    public void send(String phoneNumber, String code) throws SmsSendException {
        // Expand placeholders in URL (useful for GET-based gateways)
        String resolvedUrl = expand(apiUrl, phoneNumber, code);

        log.info("Sending SMS to {} via {} {}", mask(phoneNumber), httpMethod, resolvedUrl);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(resolvedUrl).openConnection();
            conn.setRequestMethod(httpMethod);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(!"GET".equals(httpMethod));

            // Auth header
            if (authHeader != null && !authHeader.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", authHeader.trim());
            }

            // Payload for POST / PUT
            if (!"GET".equals(httpMethod) && payloadTemplate != null && !payloadTemplate.trim().isEmpty()) {
                String body = expand(payloadTemplate, phoneNumber, code);

                // Detect content-type from payload shape
                String contentType = body.trim().startsWith("{") || body.trim().startsWith("[")
                                     ? "application/json"
                                     : "application/x-www-form-urlencoded";
                conn.setRequestProperty("Content-Type", contentType + "; charset=UTF-8");

                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }
            }

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                log.info("SMS dispatched successfully to {} (HTTP {})", mask(phoneNumber), status);
            } else {
                String responseBody = readErrorStream(conn);
                log.error("Gateway returned HTTP {} for {}: {}", status, mask(phoneNumber), responseBody);
                throw new SmsSendException(
                        "SMS gateway returned non-success status " + status + " for " + mask(phoneNumber),
                        status);
            }

        } catch (SocketTimeoutException e) {
            log.error("Timeout contacting SMS gateway for {}", mask(phoneNumber), e);
            throw new SmsSendException(
                    "SMS gateway timed out after " + TIMEOUT_MS + " ms for " + mask(phoneNumber), e);
        } catch (IOException e) {
            log.error("I/O error contacting SMS gateway for {}", mask(phoneNumber), e);
            throw new SmsSendException(
                    "I/O error contacting SMS gateway: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Substitutes {@code {phoneNumber}} and {@code {code}} in {@code template}. */
    static String expand(String template, String phoneNumber, String code) {
        if (template == null) return "";
        return template
                .replace("{phoneNumber}", phoneNumber)
                .replace("{code}",        code);
    }

    /**
     * Masks the middle digits of a phone number for safe log output.
     * E.g. {@code +8801712345678} → {@code +880171****678}
     */
    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) return "****";
        int len = phoneNumber.length();
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(len - 3);
    }

    /** Reads the error stream safely, returning an empty string on failure. */
    private String readErrorStream(HttpURLConnection conn) {
        try {
            if (conn.getErrorStream() == null) return "(no body)";
            return new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(could not read error body)";
        }
    }
}
