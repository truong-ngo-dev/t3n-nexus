/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.lang;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for handling various date types.
 * <p>
 * The following classes are supported:
 * </p>
 * <ul>
 *     <li>{@link Date}</li>
 *     <li>{@link LocalDate}</li>
 *     <li>{@link LocalDateTime}</li>
 *     <li>{@link ZonedDateTime}</li>
 *     <li>{@link OffsetDateTime}</li>
 *     <li>{@link Instant}</li>
 * </ul>
 * <p>
 * Provide utility methods for converting, parsing, and manipulating date objects.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li>Parsing strings to various date types with multiple patterns.</li>
 * <li>Converting between different date types (e.g., Date to Instant).</li>
 * <li>Date manipulation (plus, minus, truncate).</li>
 * <li>Date inspection (getYear, isWeekend, isLeapYear).</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DateUtils {

    private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private DateUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * PATTERNS & FORMATTERS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * A list of common date patterns used for date parsing and formatting.
     * <p>
     * The patterns include various formats with different separators such as dashes (-),
     * slashes (/), dots (.), and commas (,). It also includes patterns with abbreviated
     * month names (MMM).
     * </p>
     */
    public static final List<String> DATE_PATTERNS;

    /**
     * A list of common date-time patterns used for parsing and formatting date-time values.
     * <p>
     * The patterns include different formats with various separators and time zone representations.
     * </p>
     */
    public static final List<String> DATE_TIME_PATTERNS;

    static {
        List<String> datePattern = List.of(
                // With (-) separator
                "d-M-yyyy", "yyyy-M-d", "d-MMM-yyyy", "MMM-d-yyyy", "yyyy-MMM-d", "yyyy-d-MMM",
                // With (/) separator
                "d/M/yyyy", "yyyy/M/d", "d/MMM/yyyy", "MMM/d/yyyy", "yyyy/MMM/d", "yyyy/d/MMM",
                // With (.) separator
                "d.M.yyyy", "yyyy.M.d", "d.MMM.yyyy", "MMM.d.yyyy", "yyyy.MMM.d", "yyyy.d.MMM",
                // With (,) separator
                "d,M,yyyy", "yyyy,M,d", "d,MMM,yyyy", "MMM,d,yyyy", "yyyy,MMM,d", "yyyy,d,MMM",
                // With ( ) separator
                "d M yyyy", "yyyy M d", "d MMM yyyy", "MMM d yyyy", "yyyy MMM d", "yyyy d MMM",
                // Abbreviated month (MMM) with comma - (MMM,)
                "MMM d, yyyy", "d MMM, yyyy"
        );
        DATE_PATTERNS = new ArrayList<>(datePattern);

        List<String> dateTimePattern = List.of(
                // Timezone type [VV]
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX'['VV']'",
                "yyyy-MM-dd'T'HH:mm:ss,SSSXXX'['VV']'",
                "yyyy-MM-dd HH:mm:ss.SSSXXX'['VV']'",
                "yyyy-MM-dd HH:mm:ss,SSSXXX'['VV']'",
                "yyyy-MM-dd'T'HH:mm:ssXXX'['VV']'",
                "yyyy-MM-dd HH:mm:ssXXX'['VV']'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'['VV']'",
                "yyyy-MM-dd'T'HH:mm:ss,SSS'['VV']'",
                "yyyy-MM-dd HH:mm:ss.SSS'['VV']'",
                "yyyy-MM-dd HH:mm:ss,SSS'['VV']'",

                // Timezone type (XXX, X, Z)
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss,SSSXXX",
                "yyyy-MM-dd HH:mm:ss.SSSXXX",
                "yyyy-MM-dd HH:mm:ss,SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss,SSS'Z'",
                "yyyy-MM-dd HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd HH:mm:ss,SSS'Z'",

                // No timezone
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss,SSS",
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss,SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",

                // Custom format
                "d-M-yyyy HH:mm:ss", "yyyy-M-d HH:mm:ss", "d-M-yyyy HH:mm", "yyyy-M-d HH:mm",
                "d/M/yyyy HH:mm:ss", "yyyy/M/d HH:mm:ss", "d/M/yyyy HH:mm", "yyyy/M/d HH:mm",
                "d.M.yyyy HH:mm:ss", "yyyy.M.d HH:mm:ss", "d.M.yyyy HH:mm", "yyyy.M.d HH:mm",
                "d,M,yyyy HH:mm:ss", "yyyy,M,d HH:mm:ss", "d,M,yyyy HH:mm", "yyyy,M,d HH:mm",
                "d M yyyy HH:mm:ss", "yyyy M d HH:mm:ss", "d M yyyy HH:mm", "yyyy M d HH:mm"
        );
        DATE_TIME_PATTERNS = new ArrayList<>(dateTimePattern);
    }

    /**
     * Gets a cached DateTimeFormatter for the given pattern.
     *
     * @param pattern the date pattern (must not be {@code null})
     * @return the DateTimeFormatter
     * @since 1.0.0
     */
    public static DateTimeFormatter getFormatter(String pattern) {
        return FORMATTER_CACHE.computeIfAbsent(pattern, p -> DateTimeFormatter.ofPattern(p).withResolverStyle(ResolverStyle.STRICT));
    }

    /**
     * Registers a new date pattern.
     *
     * @param pattern the pattern to register (must not be {@code null})
     * @since 1.0.0
     */
    public static void registerDatePattern(String pattern) {
        if (!DATE_PATTERNS.contains(pattern)) DATE_PATTERNS.add(pattern);
    }

    /**
     * Registers a new date-time pattern.
     *
     * @param pattern the pattern to register (must not be {@code null})
     * @since 1.0.0
     */
    public static void registerDateTimePattern(String pattern) {
        if (!DATE_TIME_PATTERNS.contains(pattern)) DATE_TIME_PATTERNS.add(pattern);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * PARSING
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Parses a string into a {@link LocalDate}.
     * <p>
     * Attempts to parse using default format, then predefined date patterns, then date-time patterns.
     * </p>
     *
     * <pre>{@code
     * LocalDate date = DateUtils.parseLocalDate("2023-12-31");
     * }</pre>
     *
     * @param s the date string to parse (maybe {@code null})
     * @return the parsed LocalDate, or {@code null} if input is null/empty
     * @throws IllegalArgumentException if parsing fails
     * @since 1.0.0
     */
    public static LocalDate parseLocalDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException | IllegalArgumentException ignored) {}
        for (String pattern : DATE_PATTERNS) {
            try {
                DateTimeFormatter formatter = getFormatter(pattern);
                return LocalDate.parse(s, formatter);
            } catch (DateTimeParseException | IllegalArgumentException ignored) {}
        }
        for (String pattern : DATE_TIME_PATTERNS) {
            try {
                DateTimeFormatter formatter = getFormatter(pattern);
                return LocalDateTime.parse(s, formatter).toLocalDate();
            } catch (DateTimeParseException | IllegalArgumentException ignored) {}
        }
        throw new IllegalArgumentException("Could not parse to java.time.LocalDate: " + s);
    }

    /**
     * Parses a string into a {@link LocalDateTime}.
     * <p>
     * Attempts to parse using default format, then predefined date-time patterns, then date patterns (at start of day).
     * </p>
     *
     * <pre>{@code
     * LocalDateTime dt = DateUtils.parseLocalDateTime("2023-12-31T10:15:30");
     * }</pre>
     *
     * @param s the date-time string to parse (maybe {@code null})
     * @return the parsed LocalDateTime, or {@code null} if input is null/empty
     * @throws IllegalArgumentException if parsing fails
     * @since 1.0.0
     */
    public static LocalDateTime parseLocalDateTime(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException | IllegalArgumentException ignored) {}
        for (String pattern : DATE_TIME_PATTERNS) {
            try {
                DateTimeFormatter formatter = getFormatter(pattern);
                return LocalDateTime.parse(s, formatter);
            } catch (DateTimeParseException | IllegalArgumentException ignored) {}
        }
        for (String pattern : DATE_PATTERNS) {
            try {
                DateTimeFormatter formatter = getFormatter(pattern);
                LocalDate ld = LocalDate.parse(s, formatter);
                return ld.atStartOfDay();
            } catch (DateTimeParseException | IllegalArgumentException ignored) {}
        }
        throw new IllegalArgumentException("Could not parse to java.time.LocalDateTime: " + s);
    }

    /**
     * Parses a string into a {@link Date} using the specified {@link ZoneId}.
     *
     * <pre>{@code
     * Date date = DateUtils.parseDate("2023-12-31T10:15:30", ZoneId.of("UTC"));
     * }</pre>
     *
     * @param s      the date-time string to parse (maybe {@code null})
     * @param zoneId the time zone to apply (must not be {@code null})
     * @return the parsed Date, or {@code null} if input is null/empty
     * @throws IllegalArgumentException if parsing fails
     * @since 1.0.0
     */
    public static Date parseDate(String s, ZoneId zoneId) {
        if (s == null || s.trim().isEmpty()) return null;
        LocalDateTime ldt = parseLocalDateTime(s);
        Instant instant = ldt.atZone(zoneId).toInstant();
        return Date.from(instant);
    }

    /**
     * Parses a string into a {@link ZonedDateTime} using the specified {@link ZoneId}.
     *
     * <pre>{@code
     * ZonedDateTime zdt = DateUtils.parseZonedDateTime("2023-12-31T10:15:30", ZoneId.of("UTC"));
     * }</pre>
     *
     * @param s      the date-time string to parse (maybe {@code null})
     * @param zoneId the time zone to apply (must not be {@code null})
     * @return the parsed ZonedDateTime, or {@code null} if input is null/empty
     * @throws IllegalArgumentException if parsing fails
     * @since 1.0.0
     */
    public static ZonedDateTime parseZonedDateTime(String s, ZoneId zoneId) {
        if (s == null || s.trim().isEmpty()) return null;
        LocalDateTime ldt = parseLocalDateTime(s);
        return ldt.atZone(zoneId);
    }

    /**
     * Parses a string into an {@link OffsetDateTime} using the specified {@link ZoneId}.
     *
     * <pre>{@code
     * OffsetDateTime odt = DateUtils.parseOffsetDateTime("2023-12-31T10:15:30", ZoneId.of("UTC"));
     * }</pre>
     *
     * @param s      the date-time string to parse (maybe {@code null})
     * @param zoneId the time zone to apply (must not be {@code null})
     * @return the parsed OffsetDateTime, or {@code null} if input is null/empty
     * @throws IllegalArgumentException if parsing fails
     * @since 1.0.0
     */
    public static OffsetDateTime parseOffsetDateTime(String s, ZoneId zoneId) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return OffsetDateTime.parse(s); } catch (Exception ignored) {}
        return parseLocalDateTime(s).atZone(zoneId).toOffsetDateTime();
    }

    /**
     * Parses a string into an {@link Instant} using the specified {@link ZoneId}.
     *
     * <pre>{@code
     * Instant instant = DateUtils.parseInstant("2023-12-31T10:15:30", ZoneId.of("UTC"));
     * }</pre>
     *
     * @param s      the date-time string to parse (maybe {@code null})
     * @param zoneId the time zone to apply (must not be {@code null})
     * @return the parsed Instant, or {@code null} if input is null/empty
     * @throws IllegalArgumentException if parsing fails
     * @since 1.0.0
     */
    public static Instant parseInstant(String s, ZoneId zoneId) {
        if (s == null || s.trim().isEmpty()) return null;
        LocalDateTime ldt = parseLocalDateTime(s);
        return ldt.atZone(zoneId).toInstant();
    }

    /**
     * Parses a string into a date object of the specified type {@code T}.
     *
     * <pre>{@code
     * LocalDate date = DateUtils.parse("2023-12-31", LocalDate.class, ZoneId.of("UTC"));
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param s          the string representing the date (maybe {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @param zoneId     the time zone to apply (must not be {@code null})
     * @return the parsed date object, or {@code null} if input is null/empty
     * @throws IllegalArgumentException if parsing fails or target type is unsupported
     * @since 1.0.0
     */
    public static <T> T parse(String s, Class<T> targetType, ZoneId zoneId)  {
        try {
            if (targetType == LocalDate.class) return targetType.cast(parseLocalDate(s));
            if (targetType == LocalDateTime.class) return targetType.cast(parseLocalDateTime(s));
            if (targetType == Instant.class) return targetType.cast(parseInstant(s, zoneId));
            if (targetType == Date.class) return targetType.cast(parseDate(s, zoneId));
            if (targetType == ZonedDateTime.class) return targetType.cast(parseZonedDateTime(s, zoneId));
            if (targetType == OffsetDateTime.class) return targetType.cast(parseOffsetDateTime(s, zoneId));
            throw new IllegalArgumentException("Unsupported target type: " + targetType);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date data: " + s, e);
        }
    }

    /**
     * Parses a string into a date object of the specified type {@code T} using a specific pattern.
     *
     * <pre>{@code
     * Instant instant = DateUtils.parse("2025-01-10T15:30:00", "yyyy-MM-dd'T'HH:mm:ss", Instant.class, ZoneId.of("UTC"));
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param s          the string representing the date (maybe {@code null})
     * @param pattern    the pattern of the date string (must not be {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @param zoneId     the time zone to apply (must not be {@code null})
     * @return the parsed date object
     * @throws IllegalArgumentException if parsing fails or target type is unsupported
     * @since 1.0.0
     */
    public static <T> T parse(String s, String pattern, Class<T> targetType, ZoneId zoneId)  {
        DateTimeFormatter formatter;
        try {
            formatter = getFormatter(pattern);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid pattern: " + pattern, e);
        }
        try {
            if (targetType == LocalDate.class) {
                return targetType.cast(LocalDate.parse(s, formatter));
            } else if (targetType == LocalDateTime.class) {
                return targetType.cast(LocalDateTime.parse(s, formatter));
            } else if (targetType == Instant.class) {
                LocalDateTime dateTime = LocalDateTime.parse(s, formatter);
                return targetType.cast(dateTime.atZone(zoneId).toInstant());
            } else if (targetType == Date.class) {
                LocalDateTime dateTime = LocalDateTime.parse(s, formatter);
                Instant instant = dateTime.atZone(zoneId).toInstant();
                return targetType.cast(Date.from(instant));
            } else if (targetType == ZonedDateTime.class) {
                LocalDateTime dateTime = LocalDateTime.parse(s, formatter);
                return targetType.cast(dateTime.atZone(zoneId));
            } else {
                throw new IllegalArgumentException("Unsupported target type: " + targetType);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + s, e);
        }
    }

    /**
     * Parses a string into a date object using UTC time zone.
     *
     * <pre>{@code
     * LocalDate date = DateUtils.parseWithUTC("2023-12-31", LocalDate.class);
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param s          the string representing the date (maybe {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseWithUTC(String s, Class<T> targetType) {
        ZoneId zoneId = ZoneOffset.UTC;
        return parse(s, targetType, zoneId);
    }

    /**
     * Parses a string into a date object using a specific pattern and UTC time zone.
     *
     * <pre>{@code
     * LocalDate date = DateUtils.parseWithUTC("2023-12-31", "yyyy-MM-dd", LocalDate.class);
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param s          the string representing the date (maybe {@code null})
     * @param pattern    the date pattern (must not be {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseWithUTC(String s, String pattern, Class<T> targetType) {
        ZoneId zoneId = ZoneOffset.UTC;
        return parse(s, pattern, targetType, zoneId);
    }

    /**
     * Parses a string into a date object using the system default time zone.
     *
     * <pre>{@code
     * LocalDate date = DateUtils.parseWithSystemZone("2023-12-31", LocalDate.class);
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param s          the string representing the date (maybe {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseWithSystemZone(String s, Class<T> targetType) {
        ZoneId zoneId = ZoneId.systemDefault();
        return parse(s, targetType, zoneId);
    }

    /**
     * Parses a string into a date object using a specific pattern and the system default time zone.
     *
     * <pre>{@code
     * LocalDate date = DateUtils.parseWithSystemZone("2023-12-31", "yyyy-MM-dd", LocalDate.class);
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param s          the string representing the date (maybe {@code null})
     * @param pattern    the date pattern (must not be {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseWithSystemZone(String s, String pattern, Class<T> targetType) {
        ZoneId zoneId = ZoneId.systemDefault();
        return parse(s, pattern, targetType, zoneId);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * TIMESTAMP PARSING
     * -----------------------------------------------------------------------------------------------------------------
     */

    private static <T> T parse(Instant instant, Class<T> targetType, ZoneId zoneId) {
        if (targetType == Date.class) return targetType.cast(Date.from(instant));
        if (targetType == Instant.class) return targetType.cast(instant);
        if (targetType == LocalDate.class) return targetType.cast(instant.atZone(zoneId).toLocalDate());
        if (targetType == LocalDateTime.class) return targetType.cast(instant.atZone(zoneId).toLocalDateTime());
        if (targetType == ZonedDateTime.class) return targetType.cast(instant.atZone(zoneId));
        if (targetType == OffsetDateTime.class) return targetType.cast(OffsetDateTime.ofInstant(instant, zoneId));
        if (targetType == Long.class) return targetType.cast(instant.toEpochMilli());
        throw new IllegalArgumentException("Unsupported target type: " + targetType);
    }

    /**
     * Parses a millisecond timestamp into a date object.
     *
     * <pre>{@code
     * Instant instant = DateUtils.parseAsMilli(1672531200000L, Instant.class, ZoneId.of("UTC"));
     * }</pre>
     *
     * @param <T>         the type of the desired date object
     * @param millisecond the timestamp in milliseconds (must not be {@code null})
     * @param targetType  the target class type (must not be {@code null})
     * @param zoneId      the time zone to apply (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseAsMilli(Long millisecond, Class<T> targetType, ZoneId zoneId) {
        Objects.requireNonNull(millisecond);
        Assert.isTrue(millisecond >= 0, "millisecond must not be negative");
        Instant instant = Instant.ofEpochMilli(millisecond);
        return parse(instant, targetType, zoneId);
    }

    /**
     * Parses a second timestamp into a date object.
     *
     * <pre>{@code
     * Instant instant = DateUtils.parseAsSecond(1672531200L, Instant.class, ZoneId.of("UTC"));
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param second     the timestamp in seconds (must not be {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @param zoneId     the time zone to apply (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseAsSecond(Long second, Class<T> targetType, ZoneId zoneId) {
        Objects.requireNonNull(second);
        Assert.isTrue(second >= 0, "second must not be negative");
        Instant instant = Instant.ofEpochSecond(second);
        return parse(instant, targetType, zoneId);
    }

    /**
     * Parses a millisecond timestamp into a date object using UTC time zone.
     *
     * <pre>{@code
     * Instant instant = DateUtils.parseAsMilliWithUTC(1672531200000L, Instant.class);
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param timestamp  the timestamp in milliseconds (must not be {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseAsMilliWithUTC(Long timestamp, Class<T> targetType) {
        return parseAsMilli(timestamp, targetType, ZoneOffset.UTC);
    }

    /**
     * Parses a millisecond timestamp into a date object using the system default time zone.
     *
     * <pre>{@code
     * Instant instant = DateUtils.parseAsMilliWithSystemZone(1672531200000L, Instant.class);
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param timestamp  the timestamp in milliseconds (must not be {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseAsMilliWithSystemZone(Long timestamp, Class<T> targetType) {
        return parseAsMilli(timestamp, targetType, ZoneId.systemDefault());
    }

    /**
     * Parses a second timestamp into a date object using UTC time zone.
     *
     * <pre>{@code
     * Instant instant = DateUtils.parseAsSecondWithUTC(1672531200L, Instant.class);
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param timestamp  the timestamp in seconds (must not be {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseAsSecondWithUTC(Long timestamp, Class<T> targetType) {
        return parseAsSecond(timestamp, targetType, ZoneOffset.UTC);
    }

    /**
     * Parses a second timestamp into a date object using the system default time zone.
     *
     * <pre>{@code
     * Instant instant = DateUtils.parseAsSecondWithSystemZone(1672531200L, Instant.class);
     * }</pre>
     *
     * @param <T>        the type of the desired date object
     * @param timestamp  the timestamp in seconds (must not be {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the parsed date object
     * @since 1.0.0
     */
    public static <T> T parseAsSecondWithSystemZone(Long timestamp, Class<T> targetType) {
        return parseAsSecond(timestamp, targetType, ZoneId.systemDefault());
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CONVERSION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Converts a supported date object to {@link Instant}.
     *
     * <pre>{@code
     * Instant instant = DateUtils.toInstant(new Date(), ZoneId.of("UTC"));
     * }</pre>
     *
     * @param o      the date object to be converted (maybe {@code null})
     * @param zoneId the time zone to apply (must not be {@code null})
     * @return the corresponding Instant, or {@code null} if input is null
     * @throws IllegalArgumentException if the object type is unsupported
     * @since 1.0.0
     */
    public static Instant toInstant(Object o, ZoneId zoneId) {
        if (Objects.isNull(o)) return null;
        if (o instanceof Instant) return (Instant) o;
        if (o instanceof Date) return ((Date) o).toInstant();
        if (o instanceof LocalDate) return ((LocalDate) o).atStartOfDay(zoneId).toInstant();
        if (o instanceof LocalDateTime) return ((LocalDateTime) o).atZone(zoneId).toInstant();
        if (o instanceof ZonedDateTime) return ((ZonedDateTime) o).toInstant();
        if (o instanceof OffsetDateTime) return ((OffsetDateTime) o).toInstant();
        throw new IllegalArgumentException("Unsupported target type: " + o.getClass());
    }

    /**
     * Converts a supported date object to {@link Instant} using UTC time zone.
     *
     * <pre>{@code
     * Instant instant = DateUtils.toInstantUTC(new Date());
     * }</pre>
     *
     * @param o the date object to be converted (maybe {@code null})
     * @return the corresponding Instant in UTC
     * @since 1.0.0
     */
    public static Instant toInstantUTC(Object o) {
        return toInstant(o, ZoneOffset.UTC);
    }

    /**
     * Converts a supported date object to {@link Instant} using the system default time zone.
     *
     * <pre>{@code
     * Instant instant = DateUtils.toInstantSystem(new Date());
     * }</pre>
     *
     * @param o the date object to be converted (maybe {@code null})
     * @return the corresponding Instant in system default zone
     * @since 1.0.0
     */
    public static Instant toInstantSystem(Object o) {
        return toInstant(o, ZoneId.systemDefault());
    }

    /**
     * Converts an {@link Instant} to a date object of the specified target type.
     *
     * <pre>{@code
     * Date date = (Date) DateUtils.fromInstant(Instant.now(), ZoneId.of("UTC"), Date.class);
     * }</pre>
     *
     * @param instant    the Instant to be converted (maybe {@code null})
     * @param zoneId     the time zone to apply (must not be {@code null})
     * @param targetType the desired target type (must not be {@code null})
     * @return the corresponding date object
     * @throws IllegalArgumentException if target type is unsupported
     * @since 1.0.0
     */
    public static Object fromInstant(Instant instant, ZoneId zoneId, Class<?> targetType) {
        if (Objects.isNull(instant)) return null;
        if (targetType == Date.class) return Date.from(instant);
        if (targetType == Instant.class) return instant;
        if (targetType == LocalDate.class) return instant.atZone(zoneId).toLocalDate();
        if (targetType == LocalDateTime.class) return instant.atZone(zoneId).toLocalDateTime();
        if (targetType == ZonedDateTime.class) return instant.atZone(zoneId);
        if (targetType == OffsetDateTime.class) return instant.atZone(zoneId).toOffsetDateTime();
        throw new IllegalArgumentException("Unsupported target type: " + targetType);
    }

    /**
     * Converts an {@link Instant} to a date object in UTC time zone.
     *
     * <pre>{@code
     * Date date = (Date) DateUtils.fromInstantUTC(Instant.now(), Date.class);
     * }</pre>
     *
     * @param instant    the Instant to be converted (maybe {@code null})
     * @param targetType the desired target type (must not be {@code null})
     * @return the corresponding date object
     * @since 1.0.0
     */
    public static Object fromInstantUTC(Instant instant, Class<?> targetType) {
        ZoneId zoneId = ZoneOffset.UTC;
        return fromInstant(instant, zoneId, targetType);
    }

    /**
     * Converts an {@link Instant} to a date object in the system default time zone.
     *
     * <pre>{@code
     * Date date = (Date) DateUtils.fromInstantSystemZone(Instant.now(), Date.class);
     * }</pre>
     *
     * @param instant    the Instant to be converted (maybe {@code null})
     * @param targetType the desired target type (must not be {@code null})
     * @return the corresponding date object
     * @since 1.0.0
     */
    public static Object fromInstantSystemZone(Instant instant, Class<?> targetType) {
        ZoneId zoneId = ZoneId.systemDefault();
        return fromInstant(instant, zoneId, targetType);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * MANIPULATION & INSPECTION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Compares two date objects.
     *
     * <pre>{@code
     * int result = DateUtils.compare(date1, date2);
     * }</pre>
     *
     * @param lhs the first date object (must not be {@code null})
     * @param rhs the second date object (must not be {@code null})
     * @return negative if lhs < rhs, zero if equal, positive if lhs > rhs
     * @since 1.0.0
     */
    @SuppressWarnings("all")
    public static int compare(Object lhs, Object rhs) {
        Objects.requireNonNull(lhs);
        Objects.requireNonNull(rhs);
        Instant date1 = toInstant(lhs, ZoneId.systemDefault());
        Instant date2 = toInstant(rhs, ZoneId.systemDefault());
        return date1.compareTo(date2);
    }

    private static ZonedDateTime toZonedDateTimeSystem(Object date) {
        Instant instant = toInstantSystem(date);
        return (instant != null) ? instant.atZone(ZoneId.systemDefault()) : null;
    }

    /**
     * Retrieves the value of a specific time field from a date object.
     *
     * <pre>{@code
     * Long year = DateUtils.getTimeFieldValue(new Date(), ChronoField.YEAR);
     * }</pre>
     *
     * @param date  the date object (maybe {@code null})
     * @param field the field to retrieve (maybe {@code null})
     * @return the value of the field, or {@code null}
     * @since 1.0.0
     */
    public static Long getTimeFieldValue(Object date, ChronoField field) {
        if (date == null || field == null) return null;
        ZonedDateTime zdt = toZonedDateTimeSystem(date);

        if (zdt != null && zdt.isSupported(field)) {
            return zdt.getLong(field);
        }
        return null;
    }

    // Convenience methods

    public static Integer getYear(Object o) {
        Long val = getTimeFieldValue(o, ChronoField.YEAR);
        return (val != null) ? val.intValue() : null;
    }

    public static Integer getMonth(Object o) {
        Long val = getTimeFieldValue(o, ChronoField.MONTH_OF_YEAR);
        return (val != null) ? val.intValue() : null;
    }

    public static Integer getDay(Object o) {
        Long val = getTimeFieldValue(o, ChronoField.DAY_OF_MONTH);
        return (val != null) ? val.intValue() : null;
    }

    public static Integer getHour(Object o) {
        Long val = getTimeFieldValue(o, ChronoField.HOUR_OF_DAY);
        return (val != null) ? val.intValue() : null;
    }

    public static Integer getMinute(Object o) {
        Long val = getTimeFieldValue(o, ChronoField.MINUTE_OF_HOUR);
        return (val != null) ? val.intValue() : null;
    }

    public static DayOfWeek getDayOfWeek(Object o) {
        Long val = getTimeFieldValue(o, ChronoField.DAY_OF_WEEK);
        return (val != null) ? DayOfWeek.of(val.intValue()) : null;
    }

    /**
     * Gets the quarter of the year (1-4).
     *
     * @param o the date object
     * @return the quarter, or {@code null}
     * @since 1.0.0
     */
    public static Integer getQuarter(Object o) {
        Integer month = getMonth(o);
        return (month != null) ? (month - 1) / 3 + 1 : null;
    }

    // Current time getters

    public static Integer getCurrentYear() {
        return getYear(Instant.now());
    }

    public static Integer getCurrentMonth() {
        return getMonth(Instant.now());
    }

    public static Integer getCurrentDay() {
        return getDay(Instant.now());
    }

    public static DayOfWeek getCurrentDayOfWeek() {
        return getDayOfWeek(Instant.now());
    }

    // Start/End of periods

    public static <T> T atStartOfDay(Object date, Class<T> targetType) {
        if (date == null) return null;
        Instant instant = toInstantSystem(date);
        if (instant == null) return null;
        LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay();
        return parse(ldt.atZone(ZoneId.systemDefault()).toInstant(), targetType, ZoneId.systemDefault());
    }

    public static <T> T atEndOfDay(Object date, Class<T> targetType) {
        if (date == null) return null;
        Instant instant = toInstantSystem(date);
        if (instant == null) return null;
        LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDate().atTime(LocalTime.MAX);
        return parse(ldt.atZone(ZoneId.systemDefault()).toInstant(), targetType, ZoneId.systemDefault());
    }

    public static <T> T atStartOfMonth(Object date, Class<T> targetType) {
        if (date == null) return null;
        Instant instant = toInstantSystem(date);
        if (instant == null) return null;
        LocalDate ld = instant.atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1);
        return parse(ld.atStartOfDay(ZoneId.systemDefault()).toInstant(), targetType, ZoneId.systemDefault());
    }

    public static <T> T atEndOfMonth(Object date, Class<T> targetType) {
        if (date == null) return null;
        Instant instant = toInstantSystem(date);
        if (instant == null) return null;
        LocalDate ld = instant.atZone(ZoneId.systemDefault()).toLocalDate().with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        return atEndOfDay(ld, targetType);
    }

    public static <T> T atStartOfYear(Object date, Class<T> targetType) {
        if (date == null) return null;
        Instant instant = toInstantSystem(date);
        if (instant == null) return null;
        LocalDate ld = instant.atZone(ZoneId.systemDefault()).toLocalDate().withDayOfYear(1);
        return parse(ld.atStartOfDay(ZoneId.systemDefault()).toInstant(), targetType, ZoneId.systemDefault());
    }

    public static <T> T atEndOfYear(Object date, Class<T> targetType) {
        if (date == null) return null;
        Instant instant = toInstantSystem(date);
        if (instant == null) return null;
        LocalDate ld = instant.atZone(ZoneId.systemDefault()).toLocalDate().with(java.time.temporal.TemporalAdjusters.lastDayOfYear());
        return atEndOfDay(ld, targetType);
    }

    // Checks

    public static boolean isSameDay(Object d1, Object d2) {
        if (d1 == null || d2 == null) return false;
        Instant i1 = toInstant(d1, ZoneId.systemDefault());
        Instant i2 = toInstant(d2, ZoneId.systemDefault());
        if (i1 == null || i2 == null) return false;
        return i1.atZone(ZoneId.systemDefault()).toLocalDate().equals(i2.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    public static boolean isWeekend(Object date) {
        if (date == null) return false;
        Instant instant = toInstant(date, ZoneId.systemDefault());
        if (instant == null) return false;
        DayOfWeek dw = instant.atZone(ZoneId.systemDefault()).getDayOfWeek();
        return dw == DayOfWeek.SATURDAY || dw == DayOfWeek.SUNDAY;
    }

    public static boolean isLeapYear(Object date) {
        if (date == null) return false;
        Instant instant = toInstant(date, ZoneId.systemDefault());
        if (instant == null) return false;
        return instant.atZone(ZoneId.systemDefault()).toLocalDate().isLeapYear();
    }

    public static boolean isPast(Object date) {
        if (date == null) return false;
        return toInstantSystem(date).isBefore(Instant.now());
    }

    public static boolean isFuture(Object date) {
        if (date == null) return false;
        return toInstantSystem(date).isAfter(Instant.now());
    }

    public static boolean isBetween(Object date, Object start, Object end) {
        if (date == null || start == null || end == null) return false;
        Instant target = toInstantSystem(date);
        Instant s = toInstantSystem(start);
        Instant e = toInstantSystem(end);
        return (target.equals(s) || target.isAfter(s)) && (target.equals(e) || target.isBefore(e));
    }

    // Arithmetic

    public static <T> T plus(Object date, long amount, ChronoUnit unit, Class<T> targetType) {
        if (date == null || unit == null) return null;
        Instant result = toInstantSystem(date).plus(amount, unit);
        return parse(result, targetType, ZoneId.systemDefault());
    }

    public static <T> T minus(Object date, long amount, ChronoUnit unit, Class<T> targetType) {
        return plus(date, -amount, unit, targetType);
    }

    public static Long diff(Object start, Object end, ChronoUnit unit) {
        if (start == null || end == null || unit == null) return null;
        Instant s = toInstantSystem(start);
        Instant e = toInstantSystem(end);
        return unit.between(s, e);
    }

    public static String format(Object date, String pattern) {
        if (date == null || pattern == null) return null;
        ZonedDateTime zdt = toZonedDateTimeSystem(date);
        return zdt != null ? zdt.format(getFormatter(pattern)) : null;
    }

    public static <T> T truncate(Object date, ChronoUnit unit, Class<T> targetType) {
        if (date == null || unit == null) return null;

        Instant instant = toInstantSystem(date);

        if (unit.ordinal() <= ChronoUnit.DAYS.ordinal()) {
            Instant result = instant.truncatedTo(unit);
            return parse(result, targetType, ZoneId.systemDefault());
        }

        if (unit == ChronoUnit.MONTHS) return atStartOfMonth(date, targetType);
        if (unit == ChronoUnit.YEARS) return atStartOfYear(date, targetType);

        throw new IllegalArgumentException("Unsupported truncate unit: " + unit);
    }

}
