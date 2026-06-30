package org.app;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.fazecast.jSerialComm.SerialPort;

public class NumberProcessor {
    // Listener interface for callbacks
    public interface DataListener {
        void onDataReceived(String data);
        void onProcessedData(String result);
    }

    private SerialPort[] ports;
    private SerialPort comPort;
    private List<String> input;       // every raw line we received, just as text
    private List<Byte> collectedBytes; // the actual random bytes we parsed out of the lines
    private InputStream in;
    private boolean isReading = false;

    // How many random bytes we want before we stop and hash everything.
    private int maxSamples;
    private static final int DEFAULT_MAX_SAMPLES = 32; // e.g. 32 bytes = 256 bits, good for SHA-256

    // Default constructor: use the default sample count.
    public NumberProcessor(){
        this(DEFAULT_MAX_SAMPLES);
    }

    // Constructor that lets you choose how many bytes to collect before stopping.
    public NumberProcessor(int maxSamples){
        this.maxSamples = maxSamples;
        input = new ArrayList<>();
        collectedBytes = new ArrayList<>();
        processPorts();
    }

    // Simply hands back the list of serial ports we found, so the GUI can show them.
    public SerialPort[] getPorts(){
        return ports;
    }

    // Tells us whether a port is currently open and ready to use.
    public boolean isPortOpen() {
        return comPort != null && comPort.isOpen();
    }

    // Looks up all available serial ports on this computer and prints them out.
    private void processPorts(){
        ports = SerialPort.getCommPorts();

        for (int i = 0; i < ports.length; i++){
            System.out.println("Port " + i + ": " + ports[i].getSystemPortName() + ", " + ports[i].getPortDescription());
        }

        if(ports.length == 0){
            System.out.println("No devices found");
        }
    }

    // Opens the chosen serial port so we can start reading data from the Arduino.
    public boolean openPort(int portIndex) {
        // Close previously opened port
        if (comPort != null && comPort.isOpen()) {
            comPort.closePort();
        }

        if (portIndex < 0 || portIndex >= ports.length) {
            System.out.println("Invalid port index");
            return false;
        }

        comPort = ports[portIndex];
        comPort.setBaudRate(115200);

        if(comPort.openPort()){
            System.out.println("Opened Port: " + comPort.getSystemPortName());
            comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
            in = comPort.getInputStream();
            return true;
        } else {
            System.out.println("Fehler beim Öffnen des Ports.");
            return false;
        }
    }

    // Main loop: reads characters from the serial port one by one, builds up full
    // lines, and stops either when we have enough samples, or when nothing has
    // arrived for a while (timeout), or when stopReading() is called.
    public void startReading(DataListener listener){
        if (comPort == null || !comPort.isOpen()) {
            System.out.println("Port is not open!");
            return;
        }

        isReading = true;
        input.clear();
        collectedBytes.clear();

        try{
            System.out.println("Retrieving Data from Board...");
            listener.onProcessedData("Reading data from board... (collecting " + maxSamples + " bytes)");

            StringBuilder buffer = new StringBuilder();
            int consecutiveEmpty = 0;
            int maxConsecutiveEmpty = 5000; // 5 seconds of no data

            // Keep looping while: we're still supposed to be reading,
            // we haven't timed out, AND we don't have enough bytes yet.
            while (isReading
                    && consecutiveEmpty < maxConsecutiveEmpty
                    && collectedBytes.size() < maxSamples) {

                if (in.available() > 0) {
                    consecutiveEmpty = 0;
                    char c = (char) in.read();

                    if (c == '\n' || c == '\r') {
                        if (buffer.length() > 0) {
                            String line = buffer.toString().trim();
                            System.out.println("Empfangen: " + line);

                            // Send raw data to input stream text area
                            listener.onDataReceived(line);

                            // Try to pull a random byte out of this line
                            processLine(line, listener);

                            input.add(line);
                            buffer.setLength(0);
                        }
                    } else if (c >= 32 && c <= 126) { // Only printable characters
                        buffer.append(c);
                    }
                } else {
                    consecutiveEmpty++;
                    Thread.sleep(1);
                }
            }

            // Figure out *why* the loop stopped, and tell the user.
            if (collectedBytes.size() >= maxSamples) {
                listener.onProcessedData("Collected " + collectedBytes.size() + " bytes. Stopping.");
            } else if (consecutiveEmpty >= maxConsecutiveEmpty) {
                listener.onProcessedData("No more data received. Stopped reading.");
            }

            // Now that we're done collecting, build the final hash and print a
            // summary that shows the input we started with and the hash we got out.
            String hash = getHash();
            StringBuilder summary = new StringBuilder();
            summary.append("Input bytes: ");
            for (Byte b : collectedBytes) {
                summary.append(String.format("%02X ", b));
            }
            summary.append("\n-> SHA-256: ").append(hash);
            listener.onProcessedData(summary.toString());

        } catch (Exception e) {
            e.printStackTrace();
            listener.onProcessedData("Error: " + e.getMessage());
        } finally {
            isReading = false;
            listener.onProcessedData("Reading stopped.");
        }
    }

    // Looks at one line of text from the Arduino and, if it looks like
    // "Random byte: 0xA3", pulls out the actual byte value (0xA3) and
    // remembers it in our collectedBytes list.
    private void processLine(String line, DataListener listener) {
        String prefix = "Random byte: 0x";

        if (line.startsWith(prefix)) {
            String hexPart = line.substring(prefix.length()).trim();
            try {
                int value = Integer.parseInt(hexPart, 16); // turn "A3" into the number 163
                byte byteValue = (byte) value;
                collectedBytes.add(byteValue);
                listener.onProcessedData("Got byte " + (collectedBytes.size()) + "/" + maxSamples
                        + ": 0x" + hexPart.toUpperCase());
            } catch (NumberFormatException e) {
                // The line said "Random byte: 0x..." but wasn't valid hex - just ignore it.
            }
        }
    }

    // Stops the reading loop early if someone wants to cancel manually.
    public void stopReading() {
        isReading = false;
    }

    // Closes the serial port cleanly when we're done with it.
    public void closePort() {
        isReading = false;

        if (comPort != null && comPort.isOpen()) {
            comPort.closePort();
            System.out.println("Port geschlossen.");
        }
    }

    // Gives back a copy of every raw line of text we received.
    public List<String> getInput() {
        return new ArrayList<>(input);
    }

    // Gives back a copy of every random byte we successfully parsed.
    public List<Byte> getCollectedBytes() {
        return new ArrayList<>(collectedBytes);
    }

    // Takes all the random bytes we collected and runs them through SHA-256,
    // turning the raw bytes into one long, fixed-length, hex-formatted "fingerprint".
    public String getHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Convert our List<Byte> into a plain byte[] array, since that's
            // what MessageDigest wants to work with.
            byte[] dataBytes = new byte[collectedBytes.size()];
            for (int i = 0; i < collectedBytes.size(); i++) {
                dataBytes[i] = collectedBytes.get(i);
            }

            byte[] hashBytes = digest.digest(dataBytes);

            // Convert the hash (raw bytes) into a readable hex string like "1a2b3c...".
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            // This basically never happens, SHA-256 is built into every JVM.
            e.printStackTrace();
            return null;
        }
    }
}