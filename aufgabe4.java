package oxoo2a;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class aufgabe4 {

    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Map<String, DiceGame> diceInvites = new ConcurrentHashMap<>();
    private static final Random random = new Random();

    private static void fatal(String comment) {
        System.out.println(comment);
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !args[0].equalsIgnoreCase("-l")) {
            fatal("Usage: \"netcat -l <port>\"");
        }

        int port = Integer.parseInt(args[1]);
        Server(port);
    }

    private static void Server(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("TCP Chat-Server läuft auf Port " + port);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(clientSocket);
            new Thread(handler).start();
        }
    }

    private static void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendToClient(message);
        }
    }

    private static class ClientHandler implements Runnable {

        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String name;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("Willkommen beim TCP Chat-Server.");
                out.println("Bitte registrieren mit: register <Name>");

                String line;

                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("stop")) {
                        break;
                    }

                    if (line.startsWith("register ")) {
                        register(line);
                    } else if (line.equalsIgnoreCase("clientlist")) {
                        clientList();
                    } else if (line.startsWith("sendall ")) {
                        sendAll(line);
                    } else if (line.startsWith("send ")) {
                        sendMessage(line);
                    } else if (line.startsWith("dice invite ")) {
                        diceInvite(line);
                    } else if (line.equalsIgnoreCase("dice join")) {
                        diceJoin();
                    } else if (line.equalsIgnoreCase("dice decline")) {
                        diceDecline();
                    } else {
                        printHelp();
                    }
                }

            } catch (IOException e) {
                System.out.println("Client-Verbindung beendet.");
            } finally {
                disconnect();
            }
        }

        private void register(String line) {
            String[] parts = line.split(" ", 2);

            if (parts.length < 2 || parts[1].isBlank()) {
                out.println("Fehler: Bitte Name angeben.");
                return;
            }

            String newName = parts[1].trim();

            if (clients.containsKey(newName)) {
                out.println("Fehler: Name ist bereits vergeben.");
                return;
            }

            if (name != null) {
                clients.remove(name);
            }

            name = newName;
            clients.put(name, this);

            out.println("Registrierung erfolgreich als: " + name);
            broadcast("Info: Neuer Client verbunden: " + name);
            printHelp();
            clientList();
        }

        private void clientList() {
            out.println("Aktive Clients: " + clients.keySet());
        }

        private void sendAll(String line) {
            if (!isRegistered()) return;

            String[] parts = line.split(" ", 2);

            if (parts.length < 2) {
                out.println("Benutzung: sendall <Nachricht>");
                return;
            }

            String message = parts[1];

            for (ClientHandler client : clients.values()) {
                if (client != this) {
                    client.sendToClient("Nachricht von " + name + " an alle: " + message);
                }
            }

            out.println("Nachricht an alle gesendet.");
        }

        private void sendMessage(String line) {
            if (!isRegistered()) return;

            String[] parts = line.split(" ", 3);

            if (parts.length < 3) {
                out.println("Benutzung: send <Empfängername> <Nachricht>");
                return;
            }

            String receiverName = parts[1];
            String message = parts[2];

            ClientHandler receiver = clients.get(receiverName);

            if (receiver == null) {
                out.println("Fehler: Empfänger nicht gefunden.");
                return;
            }

            receiver.sendToClient("Nachricht von " + name + ": " + message);
            out.println("Nachricht an " + receiverName + " gesendet.");
        }

        private void diceInvite(String line) {
            if (!isRegistered()) return;

            String[] parts = line.split(" ", 3);

            if (parts.length < 3) {
                out.println("Benutzung: dice invite <Client>");
                return;
            }

            String invitedName = parts[2];
            ClientHandler invited = clients.get(invitedName);

            if (invited == null) {
                out.println("Fehler: Client nicht gefunden.");
                return;
            }

            if (invited == this) {
                out.println("Fehler: Du kannst dich nicht selbst einladen.");
                return;
            }

            DiceGame game = new DiceGame(name, invitedName);
            diceInvites.put(invitedName, game);

            invited.sendToClient(name + " lädt dich zu einem Würfelspiel ein.");
            invited.sendToClient("Antworte mit: dice join oder dice decline");

            out.println("Einladung an " + invitedName + " gesendet.");
        }

        private void diceJoin() {
            if (!isRegistered()) return;

            DiceGame game = diceInvites.remove(name);

            if (game == null) {
                out.println("Keine offene Würfeleinladung gefunden.");
                return;
            }

            ClientHandler inviter = clients.get(game.inviterName);

            if (inviter == null) {
                out.println("Der einladende Client ist nicht mehr verbunden.");
                return;
            }

            int a1 = roll();
            int a2 = roll();
            int b1 = roll();
            int b2 = roll();

            int inviterSum = a1 + a2;
            int invitedSum = b1 + b2;

            String resultForInviter;
            String resultForInvited;

            if (inviterSum > invitedSum) {
                resultForInviter = "Gewonnen";
                resultForInvited = "Verloren";
            } else if (inviterSum < invitedSum) {
                resultForInviter = "Verloren";
                resultForInvited = "Gewonnen";
            } else {
                resultForInviter = "Unentschieden";
                resultForInvited = "Unentschieden";
            }

            inviter.sendToClient("Würfelspiel gegen " + name);
            inviter.sendToClient(game.inviterName + ": " + a1 + " + " + a2 + " = " + inviterSum);
            inviter.sendToClient(name + ": " + b1 + " + " + b2 + " = " + invitedSum);
            inviter.sendToClient("Ergebnis: " + resultForInviter);

            out.println("Würfelspiel gegen " + game.inviterName);
            out.println(game.inviterName + ": " + a1 + " + " + a2 + " = " + inviterSum);
            out.println(name + ": " + b1 + " + " + b2 + " = " + invitedSum);
            out.println("Ergebnis: " + resultForInvited);
        }

        private void diceDecline() {
            if (!isRegistered()) return;

            DiceGame game = diceInvites.remove(name);

            if (game == null) {
                out.println("Keine offene Würfeleinladung gefunden.");
                return;
            }

            ClientHandler inviter = clients.get(game.inviterName);

            if (inviter != null) {
                inviter.sendToClient(name + " hat die Würfeleinladung abgelehnt.");
            }

            out.println("Du hast die Würfeleinladung abgelehnt.");
        }

        private int roll() {
            return random.nextInt(6) + 1;
        }

        private boolean isRegistered() {
            if (name == null) {
                out.println("Fehler: Bitte zuerst registrieren mit: register <Name>");
                return false;
            }
            return true;
        }

        private void sendToClient(String message) {
            out.println(message);
        }

        private void printHelp() {
            out.println("Befehle:");
            out.println("register <Name>");
            out.println("clientlist");
            out.println("send <Empfängername> <Nachricht>");
            out.println("sendall <Nachricht>");
            out.println("dice invite <Client>");
            out.println("dice join");
            out.println("dice decline");
            out.println("stop");
        }

        private void disconnect() {
            if (name != null) {
                clients.remove(name);
                diceInvites.remove(name);
                broadcast("Info: Client getrennt: " + name);
                System.out.println(name + " wurde getrennt.");
            }

            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Socket konnte nicht geschlossen werden.");
            }
        }
    }

    private static class DiceGame {
        String inviterName;
        String invitedName;

        DiceGame(String inviterName, String invitedName) {
            this.inviterName = inviterName;
            this.invitedName = invitedName;
        }
    }
}