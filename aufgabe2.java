package oxoo2a;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class aufgabe2 {

    private static final int packetSize = 4096;
    private static final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    private static BufferedReader br = null;

    private static void fatal(String comment) {
        System.out.println(comment);
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            fatal("Usage: java aufgabe2 <local-port>");
        }

        int localPort = Integer.parseInt(args[0]);
        chat(localPort);
    }

    private static void chat(int localPort) throws IOException {
        DatagramSocket socket = new DatagramSocket(localPort);
        System.out.println("UDP Chat läuft auf Port " + localPort);

        Thread receiver = new Thread(() -> {
            byte[] buffer = new byte[packetSize];

            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

                    System.out.println(
                            packet.getAddress().getHostAddress()
                                    + ":" + packet.getPort()
                                    + " -> " + message
                    );

                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        System.out.println("Fehler beim Empfangen: " + e.getMessage());
                    }
                    break;
                }
            }
        });

        receiver.start();

        while (true) {
            String line = readString();

            if (line == null || line.equalsIgnoreCase("stop")) {
                socket.close();
                break;
            }

            if (line.startsWith("register ")) {
                registerClient(line);
            } else if (line.equalsIgnoreCase("clientlist")) {
                printClientList();
            } else if (line.startsWith("sendall ")) {
                sendAll(socket, line);
            } else if (line.startsWith("send ")) {
                send(socket, line);
            } else {
                printHelp();
            }
        }
    }

    private static void registerClient(String line) {
        String[] parts = line.split(" ", 4);

        if (parts.length != 4) {
            System.out.println("Benutzung: register <Name> <IP-Adresse> <Port>");
            return;
        }

        String name = parts[1];
        String ip = parts[2];
        int port = Integer.parseInt(parts[3]);

        clients.put(name, new ClientInfo(ip, port));

        System.out.println("Client gespeichert: " + name + " -> " + ip + ":" + port);
    }

    private static void printClientList() {
        if (clients.isEmpty()) {
            System.out.println("Keine Clients gespeichert.");
            return;
        }

        System.out.println("Gespeicherte Clients:");
        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            ClientInfo client = entry.getValue();
            System.out.println(entry.getKey() + " -> " + client.ip + ":" + client.port);
        }
    }

    private static void sendAll(DatagramSocket socket, String line) throws IOException {
        if (clients.isEmpty()) {
            System.out.println("Keine Clients gespeichert.");
            return;
        }

        String[] parts = line.split(" ", 2);

        if (parts.length != 2) {
            System.out.println("Benutzung: sendall <Nachricht>");
            return;
        }

        String message = parts[1];

        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            ClientInfo client = entry.getValue();
            sendPacket(socket, client.ip, client.port, message);
            System.out.println("Nachricht an " + entry.getKey() + " gesendet.");
        }
    }

    private static void send(DatagramSocket socket, String line) throws IOException {
        String[] parts = line.split(" ", 4);

        if (parts.length != 4) {
            System.out.println("Benutzung: send <Ziel-IP-Adresse> <Ziel-Port> <Nachricht>");
            return;
        }

        String targetIp = parts[1];
        int targetPort = Integer.parseInt(parts[2]);
        String message = parts[3];

        sendPacket(socket, targetIp, targetPort, message);
    }

    private static void sendPacket(DatagramSocket socket, String ip, int port, String message) throws IOException {
        byte[] data = message.getBytes("UTF-8");

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(ip),
                port
        );

        socket.send(packet);
    }

    private static void printHelp() {
        System.out.println("Befehle:");
        System.out.println("register <Name> <IP-Adresse> <Port>");
        System.out.println("clientlist");
        System.out.println("sendall <Nachricht>");
        System.out.println("send <Ziel-IP-Adresse> <Ziel-Port> <Nachricht>");
        System.out.println("stop");
    }

    private static String readString() {
        try {
            if (br == null) {
                br = new BufferedReader(new InputStreamReader(System.in));
            }
            return br.readLine();
        } catch (Exception e) {
            System.out.printf("Exception: %s\n", e.getMessage());
            return null;
        }
    }

    private static class ClientInfo {
        String ip;
        int port;

        ClientInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}