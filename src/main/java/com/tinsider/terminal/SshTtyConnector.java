package com.tinsider.terminal;

import com.jediterm.terminal.TtyConnector;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SshTtyConnector implements TtyConnector {
    private final SshClient client;
    private final ClientSession session;
    private final ChannelShell channel;
    private final InputStreamReader reader;
    private final OutputStream outputStream;
    private final String name;

    public SshTtyConnector(SshClient client, ClientSession session, ChannelShell channel, String name) {
        this.client = client;
        this.session = session;
        this.channel = channel;
        this.name = name;
        this.reader = new InputStreamReader(channel.getInvertedOut(), StandardCharsets.UTF_8);
        this.outputStream = channel.getInvertedIn();
        channel.addCloseFutureListener(f -> System.out.println("CHANNEL CLOSED"));

        session.addCloseFutureListener(f -> System.out.println("SESSION CLOSED"));
    }

    @Override
    public boolean init(com.jediterm.terminal.Questioner questioner) {
        return true;
    }

    @Override
    public void close() {
        // 1. Close channel and session first to instantly tear down TCP sockets and
        // break blocked reads
        try {
            if (channel != null)
                channel.close(true);
        } catch (Exception ignored) {
        }
        try {
            if (session != null)
                session.close(true);
        } catch (Exception ignored) {
        }

        // 2. Safely close stream decoders and stop client inside a separate background
        // thread
        new Thread(() -> {
            try {
                if (reader != null)
                    reader.close();
                if (outputStream != null)
                    outputStream.close();
            } catch (Exception ignored) {
            }
            if (client != null) {
                try {
                    client.stop();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    @Override
    public void resize(com.jediterm.core.util.TermSize termSize) {
        try {
            if (channel != null && channel.isOpen()) {
                channel.sendWindowChange(termSize.getColumns(), termSize.getRows());
            }
        } catch (IOException e) {
            e.printStackTrace();
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
        if (!isConnected()) {
            return;
        }

        try {
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    @Override
    public void write(String string) throws IOException {
        if (string != null) {
            write(string.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isOpen() && channel != null && channel.isOpen();
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (isConnected()) {
            Thread.sleep(100);
        }
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        return isConnected();
    }
}
