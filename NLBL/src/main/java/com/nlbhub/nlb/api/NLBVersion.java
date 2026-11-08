package com.nlbhub.nlb.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Версия NLB (редактора NLBB) — из сборки Maven. Книга хранит наибольшую версию редактора,
 * который её сохранял (файл nlbversion), и старый редактор советует обновиться (#166): он может
 * не знать того, что записал новый, и потерять это при сохранении
 */
public final class NLBVersion {
    /** Версия неизвестна (запуск не из собранного jar без ресурса) */
    public static final String UNKNOWN = "";
    private static final String CURRENT = readCurrent();

    private NLBVersion() {
    }

    public static String current() {
        return CURRENT;
    }

    private static String readCurrent() {
        try (InputStream in = NLBVersion.class.getResourceAsStream("/com/nlbhub/nlb/nlbversion.properties")) {
            if (in == null) {
                return UNKNOWN;
            }
            final Properties properties = new Properties();
            properties.load(in);
            final String version = properties.getProperty("version", UNKNOWN).trim();
            // не подставленная Maven строка — версия неизвестна
            return version.startsWith("${") ? UNKNOWN : version;
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    /**
     * Сравнить версии вида 1.0.6 или 1.0.6-SNAPSHOT по числам до «-»: SNAPSHOT собирается из
     * того же кода, что и выпуск. Неизвестная или непонятная версия считается самой старой
     */
    public static int compare(final String a, final String b) {
        final int[] x = parse(a);
        final int[] y = parse(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            final int xi = i < x.length ? x[i] : 0;
            final int yi = i < y.length ? y[i] : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    /** Бо́льшая из версий; неизвестная (пустая) уступает любой */
    public static String max(final String a, final String b) {
        if (!isKnown(a)) {
            return isKnown(b) ? b.trim() : UNKNOWN;
        }
        if (!isKnown(b)) {
            return a.trim();
        }
        return compare(a, b) >= 0 ? a.trim() : b.trim();
    }

    public static boolean isKnown(final String version) {
        return version != null && parse(version).length > 0;
    }

    private static int[] parse(final String version) {
        if (version == null) {
            return new int[0];
        }
        String main = version.trim();
        final int dash = main.indexOf('-');
        if (dash >= 0) {
            main = main.substring(0, dash);
        }
        if (main.isEmpty()) {
            return new int[0];
        }
        final String[] parts = main.split("\\.");
        final int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return new int[0];
            }
        }
        return result;
    }
}
