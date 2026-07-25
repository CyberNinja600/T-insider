package com.tinsider.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class PtyTtyConnector implements TtyConnector {
    private final PtyProcess process;
    private final InputStreamReader reader;
    private final OutputStream outputStream;
    private final String name;

    public PtyTtyConnector(PtyProcess process, String name) {
        this.process = process;
        this.name = name;
        this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
        this.outputStream = process.getOutputStream();
    }

    @Override
    public boolean init(Questioner questioner) {
        return true;
    }

    @Override
    public void close() {
        try {
            process.destroy();
        } catch (Exception ignored) {}

        new Thread(() -> {
            try {
                if (reader != null) reader.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    public void resize(TermSize termSize) {
        if (process.isAlive()) {
            process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        outputStream.write(bytes);
        outputStream.flush();
    }

    @Override
    public void write(String string) throws IOException {
        if (string != null) {
            write(string.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        // Fix: Do not call reader.ready() as native Linux PTY streams return false/0 available bytes.
        return process.isAlive();
    }
}