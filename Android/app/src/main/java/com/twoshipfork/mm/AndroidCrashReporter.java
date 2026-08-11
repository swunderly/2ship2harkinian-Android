package com.twoshipfork.mm;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class AndroidCrashReporter {
    private static final String TAG = "CrashReporter";
    private static final String PREFS_NAME = "com.twoshipfork.mm.crash-reporter";
    private static final String PREF_LAST_EXIT = "lastExitTimestamp";
    private static final String JAVA_REPORT = "2ship-java-crash.txt";
    private static final String EXIT_REPORT = "2ship-last-exit.txt";
    private static final String EXIT_TRACE = "2ship-last-exit-trace.bin";
    private static final int MAX_TRACE_BYTES = 2 * 1024 * 1024;

    private static boolean installed;
    private static File reportDirectory;

    private AndroidCrashReporter() {
    }

    static synchronized void install(Context context, File preferredDirectory) {
        reportDirectory = chooseReportDirectory(context, preferredDirectory);
        capturePreviousExit(context);

        if (installed) {
            return;
        }

        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeJavaCrash(thread, throwable);
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
        installed = true;
    }

    static File getNativeReportFile() {
        return new File(reportDirectory, "2ship-native-crash.txt");
    }

    private static File chooseReportDirectory(Context context, File preferredDirectory) {
        if (ensureDirectory(preferredDirectory)) {
            return preferredDirectory;
        }

        File fallback = context.getExternalFilesDir(null);
        if (ensureDirectory(fallback)) {
            return fallback;
        }

        return context.getFilesDir();
    }

    private static boolean ensureDirectory(File directory) {
        return directory != null && (directory.isDirectory() || directory.mkdirs()) && directory.canWrite();
    }

    private static void writeJavaCrash(Thread thread, Throwable throwable) {
        File report = new File(reportDirectory, JAVA_REPORT);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(report, false), StandardCharsets.UTF_8))) {
            writeHeader(writer, "Java exception");
            writer.println("Thread: " + thread.getName());
            writer.println();
            throwable.printStackTrace(writer);
        } catch (IOException e) {
            Log.e(TAG, "Unable to write Java crash report", e);
        }
    }

    private static void capturePreviousExit(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }

        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return;
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastTimestamp = preferences.getLong(PREF_LAST_EXIT, 0L);
        List<ApplicationExitInfo> exits = activityManager.getHistoricalProcessExitReasons(null, 0, 10);

        for (ApplicationExitInfo exit : exits) {
            if (exit.getTimestamp() <= lastTimestamp || !isUsefulExit(exit)) {
                continue;
            }

            writeExitReport(exit);
            copyExitTrace(exit);
            preferences.edit().putLong(PREF_LAST_EXIT, exit.getTimestamp()).apply();
            return;
        }
    }

    private static boolean isUsefulExit(ApplicationExitInfo exit) {
        int reason = exit.getReason();
        return reason == ApplicationExitInfo.REASON_ANR ||
                reason == ApplicationExitInfo.REASON_CRASH ||
                reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ||
                reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ||
                reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
                reason == ApplicationExitInfo.REASON_SIGNALED ||
                (reason == ApplicationExitInfo.REASON_EXIT_SELF && exit.getStatus() >= 128);
    }

    private static void writeExitReport(ApplicationExitInfo exit) {
        File report = new File(reportDirectory, EXIT_REPORT);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(report, false), StandardCharsets.UTF_8))) {
            writeHeader(writer, "Previous Android process exit");
            writer.println("Exit time: " + formatTimestamp(exit.getTimestamp()));
            writer.println("Reason: " + reasonName(exit.getReason()) + " (" + exit.getReason() + ")");
            writer.println("Status: " + exit.getStatus());
            writer.println("Importance: " + exit.getImportance());
            writer.println("PSS: " + exit.getPss() + " kB");
            writer.println("RSS: " + exit.getRss() + " kB");
            if (exit.getDescription() != null) {
                writer.println("Description: " + exit.getDescription());
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to write previous-exit report", e);
        }
    }

    private static void copyExitTrace(ApplicationExitInfo exit) {
        File traceFile = new File(reportDirectory, EXIT_TRACE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        try (InputStream input = exit.getTraceInputStream()) {
            if (input == null) {
                if (traceFile.exists() && !traceFile.delete()) {
                    Log.w(TAG, "Unable to remove stale exit trace");
                }
                return;
            }

            try (OutputStream output = new FileOutputStream(traceFile, false)) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while (total < MAX_TRACE_BYTES &&
                        (read = input.read(buffer, 0, Math.min(buffer.length, MAX_TRACE_BYTES - total))) != -1) {
                    output.write(buffer, 0, read);
                    total += read;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to copy previous-exit trace", e);
        }
    }

    private static void writeHeader(PrintWriter writer, String reportType) {
        writer.println("2 Ship 2 Harkinian Android crash report");
        writer.println("Report type: " + reportType);
        writer.println("Recorded: " + formatTimestamp(System.currentTimeMillis()));
        writer.println("App version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        writer.println("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        writer.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        writer.println();
    }

    private static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date(timestamp));
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_ANR:
                return "ANR";
            case ApplicationExitInfo.REASON_CRASH:
                return "Java crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "Native crash";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "Excessive resource usage";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                return "Initialization failure";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "Low memory";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "Signal";
            case ApplicationExitInfo.REASON_EXIT_SELF:
                return "Signal exit";
            default:
                return "Unknown";
        }
    }
}
