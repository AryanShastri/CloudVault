package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.dto.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

@Service
@Slf4j
public class VirusScanService {

    @Value("${clamav.host:localhost}")
    private String host;

    @Value("${clamav.port:3310}")
    private int port;

    @Value("${clamav.timeout:0}")
    private int timeout;

    @Value("${clamav.enabled:true}")
    private boolean enabled;


    private static final int CHUNK_SIZE = 131072;


    public ScanResult scanStream(InputStream inputStream,
                                 String filename) {
        if (!enabled) {
            log.debug("Scanning disabled — skipping: {}", filename);
            return ScanResult.clean();
        }

        log.info("Starting scan: {}", filename);
        long start = System.currentTimeMillis();
        Socket socket = null;

        try {
            socket = new Socket();

            socket.connect(new InetSocketAddress(host, port), 10000);

            socket.setSoTimeout(0);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSendBufferSize(CHUNK_SIZE);
            socket.setReceiveBufferSize(CHUNK_SIZE);

            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(
                            socket.getOutputStream(), CHUNK_SIZE));
            InputStream in = socket.getInputStream();


            out.write("zINSTREAM\0".getBytes());
            out.flush();


            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            long totalBytes = 0;
            long lastLogTime = System.currentTimeMillis();

            while ((bytesRead = inputStream.read(buffer)) > 0) {

                out.writeInt(bytesRead);

                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;


                if (totalBytes % (8L * 1024 * 1024) < CHUNK_SIZE) {
                    out.flush();
                }


                long now = System.currentTimeMillis();
                if (now - lastLogTime > 10000) {
                    log.info("Scanning progress: {} — {}MB sent",
                            filename, totalBytes / (1024 * 1024));
                    lastLogTime = now;
                }
            }


            out.writeInt(0);
            out.flush();
            socket.shutdownOutput();
            socket.setSoTimeout(60000);
            ByteArrayOutputStream responseBytes =
                    new ByteArrayOutputStream();
            byte[] responseBuffer = new byte[4096];
            int responseLen;

            while ((responseLen = in.read(responseBuffer)) > 0) {
                responseBytes.write(responseBuffer, 0, responseLen);
                if (responseBytes.toString().contains("\0")
                        || responseBytes.toString().endsWith("OK")
                        || responseBytes.toString().contains("FOUND")
                        || responseBytes.toString().contains("ERROR")) {
                    break;
                }
            }

            String response = responseBytes.toString().trim()
                    .replace("\0", "");

            long duration = System.currentTimeMillis() - start;
            long sizeMB = totalBytes / (1024 * 1024);

            if (response.isEmpty()) {
                log.error("Empty response from ClamAV for: {}",
                        filename);
                return ScanResult.scannerUnavailable();
            }

            if (response.endsWith("OK")) {
                log.info("CLEAN: {} ({}MB) scanned in {}ms",
                        filename, sizeMB, duration);
                return ScanResult.clean();
            } else if (response.contains("FOUND")) {
                log.warn("INFECTED: {} → {} in {}ms",
                        filename, response, duration);
                return ScanResult.infected(response);
            } else {
                log.error("Unexpected ClamAV response: {}", response);
                return ScanResult.scannerUnavailable();
            }

        } catch (IOException e) {
            log.error("Scan IO error for {}: {}",
                    filename, e.getMessage());
            return ScanResult.scannerUnavailable();
        } finally {
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); }
                catch (Exception ignored) {}
            }
        }
    }

    public boolean isAvailable() {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(
                    new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("zPING\0".getBytes());
            out.flush();

            byte[] response = new byte[10];
            int len = in.read(response);
            if (len <= 0) return false;
            boolean up = new String(response, 0, len)
                    .contains("PONG");
            log.info("ClamAV health check: {}", up ? "UP" : "DOWN");
            return up;

        } catch (Exception e) {
            log.warn("ClamAV not available: {}", e.getMessage());
            return false;
        } finally {
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); }
                catch (Exception ignored) {}
            }
        }
    }
}