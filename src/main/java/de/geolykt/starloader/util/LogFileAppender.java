package de.geolykt.starloader.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;

import org.jetbrains.annotations.NotNull;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import de.geolykt.starloader.launcher.Utils;

public class LogFileAppender extends AppenderBase<ILoggingEvent> {

    @NotNull
    private static DateTimeFormatter timestampFormatter;

    static {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendLiteral('[').appendValue(ChronoField.HOUR_OF_DAY, 2, 2, SignStyle.NORMAL);
        builder.appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2, 2, SignStyle.NORMAL);
        builder.appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2, 2, SignStyle.NORMAL);
        builder.appendLiteral("] [");
        DateTimeFormatter formatter = builder.toFormatter();
        if (formatter == null) {
            throw new InternalError();
        }
        timestampFormatter = formatter;
    }

    private BufferedWriter bw;

    @Override
    protected void append(ILoggingEvent eventObject) {
        try {
            bw.write(timestampFormatter.format(LocalDateTime.now(Clock.systemDefaultZone())));
            bw.write(eventObject.getThreadName());
            bw.write(']');
            bw.write(' ');
            bw.write('[');
            bw.write(eventObject.getLevel().toString());
            bw.write(']');
            bw.write(':');
            bw.write(' ');
            bw.write(eventObject.getFormattedMessage());
            bw.newLine();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write log entry", e);
        }
    }

    public LogFileAppender() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    @Override
    public void start() {
        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.YEAR, 4, 4, SignStyle.NEVER)
                    .appendLiteral('-')
                    .appendValue(ChronoField.MONTH_OF_YEAR, 2, 2, SignStyle.NEVER)
                    .appendLiteral('-')
                    .appendValue(ChronoField.DAY_OF_MONTH, 2, 2, SignStyle.NEVER)
                    .appendLiteral('-')
                    .appendValue(ChronoField.HOUR_OF_DAY, 2, 2, SignStyle.NEVER)
                    .appendLiteral('-')
                    .appendValue(ChronoField.MINUTE_OF_HOUR, 2, 2, SignStyle.NEVER)
                    .appendLiteral('-')
                    .appendValue(ChronoField.SECOND_OF_MINUTE, 2, 2, SignStyle.NEVER)
                    .appendLiteral(".log")
                    .toFormatter();
            File logsFolder = new File(Utils.getApplicationFolder(), "logs");
            logsFolder.mkdirs();
            File logfile = new File(logsFolder, formatter.format(LocalDateTime.now(Clock.systemDefaultZone())));
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logfile), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initalize appender.", e);
        }
        super.start();
    }

    @Override
    public void stop() {
        if (!isStarted()) {
            return;
        }
        super.stop();
        try {
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
