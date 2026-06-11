package oxoo2a;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class aufgabe3 {

    private static void fatal(String comment) {
        System.out.println(comment);
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            fatal("Usage: \"netcat <ip> <port>\"");
        }

        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);

        Client(serverHost, serverPort);
    }

    private static void Client(String serverHost, int serverPort) throws IOException {
        InetAddress serverAddress = InetAddress.getByName(serverHost);

        Socket serverConnect = new Socket(serverAddress, serverPort);

        BufferedReader serverIn = new BufferedReader(
                new InputStreamReader(serverConnect.getInputStream())
        );

        PrintWriter serverOut = new PrintWriter(
                serverConnect.getOutputStream(),
                true
        );

        Thread receiveThread = new Thread(() -> {
            try {
                String message;
                while ((message = serverIn.readLine()) != null) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                System.out.println("Verbindung zum Server wurde beendet.");
            }
        });

        receiveThread.start();

        String line;
        while ((line = readString()) != null) {
            serverOut.println(line);

            if (line.equalsIgnoreCase("stop")) {
                break;
            }
        }

        serverConnect.close();
    }

    private static String readString() {
        try {
            if (br == null) {
                br = new BufferedReader(new InputStreamReader(System.in));
            }
            return br.readLine();
        } catch (IOException e) {
            System.out.printf("Exception: %s\n", e.getMessage());
            return null;
        }
    }

    private static BufferedReader br = null;
}