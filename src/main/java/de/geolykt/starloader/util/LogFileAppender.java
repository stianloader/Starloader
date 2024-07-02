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
    private static final DateTimeFormatter TIMESTAMP_FORMATTER;

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
        TIMESTAMP_FORMATTER = formatter;
    }

    private BufferedWriter bw;
    private boolean shutdownHookShutdown = false;

    public LogFileAppender() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            this.shutdownHookShutdown = true;
            this.stop();
        }));
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        try {
            this.bw.write(TIMESTAMP_FORMATTER.format(LocalDateTime.now(Clock.systemDefaultZone())));
            this.bw.write(eventObject.getThreadName());
            this.bw.write(']');
            this.bw.write(' ');
            this.bw.write('[');
            this.bw.write(eventObject.getLevel().toString());
            this.bw.write(']');
            this.bw.write(':');
            this.bw.write(' ');
            this.bw.write(eventObject.getFormattedMessage());
            this.bw.newLine();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write log entry", e);
        }
    }

    @Override
    public void doAppend(ILoggingEvent eventObject) {
        if (this.shutdownHookShutdown && !super.isStarted()) {
            return; // Avoid polluting the logs when running in logback debug mode
        }
        super.doAppend(eventObject);
    }

    @Override
    public void start() {
        this.shutdownHookShutdown = true;
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
            this.bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logfile), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initalize appender.", e);
        }
        super.start();
    }

    @Override
    public void stop() {
        if (!this.isStarted()) {
            return;
        }
        super.stop();
        try {
            this.bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
