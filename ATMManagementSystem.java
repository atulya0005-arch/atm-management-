
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ATMManagementSystem {

    static final String ACCOUNT = "1001";
    static String pin = "1234";
    static double balance = 5000.00;
    static boolean loggedIn = false;

    static List<String> transactions = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        transactions.add("Account opened: ₹5000");

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0);

        server.createContext("/", ex -> {
            try {
                byte[] page = Files.readAllBytes(
                        Path.of("public", "index.html"));
                ex.getResponseHeaders().set(
                        "Content-Type", "text/html; charset=UTF-8");
                ex.sendResponseHeaders(200, page.length);
                ex.getResponseBody().write(page);
                ex.close();
            } catch (IOException e) {
                send(ex, 500, "HTML file not found");
            }
        });

        server.createContext("/api/login", ex -> {
            String body = read(ex);
            String account = field(body, "account");
            String enteredPin = field(body, "pin");

            if (ACCOUNT.equals(account) && pin.equals(enteredPin)) {
                loggedIn = true;
                sendJson(ex, 200,
                        "{\"ok\":true,\"message\":\"Login successful\"}");
            } else {
                sendJson(ex, 401,
                        "{\"ok\":false,\"message\":\"Invalid account or PIN\"}");
            }
        });

        server.createContext("/api/logout", ex -> {
            loggedIn = false;
            sendJson(ex, 200, "{\"ok\":true}");
        });

        server.createContext("/api/balance", ex -> {
            if (!authorized(ex)) return;
            sendJson(ex, 200,
                    "{\"ok\":true,\"balance\":" + balance + "}");
        });

        server.createContext("/api/deposit", ex -> {
            if (!authorized(ex)) return;
            double amount = amount(read(ex));

            if (!valid(amount)) {
                sendJson(ex, 400,
                        "{\"ok\":false,\"message\":\"Invalid amount\"}");
                return;
            }

            balance += amount;
            transactions.add("Deposit: +₹" + amount + " | " + time());

            sendJson(ex, 200,
                    "{\"ok\":true,\"message\":\"Deposit successful\"}");
        });

        server.createContext("/api/withdraw", ex -> {
            if (!authorized(ex)) return;
            double amount = amount(read(ex));

            if (!valid(amount)) {
                sendJson(ex, 400,
                        "{\"ok\":false,\"message\":\"Invalid amount\"}");
                return;
            }

            if (amount > balance) {
                sendJson(ex, 400,
                        "{\"ok\":false,\"message\":\"Insufficient balance\"}");
                return;
            }

            balance -= amount;
            transactions.add("Withdrawal: -₹" + amount + " | " + time());

            sendJson(ex, 200,
                    "{\"ok\":true,\"message\":\"Withdrawal successful\"}");
        });

        server.createContext("/api/transactions", ex -> {
            if (!authorized(ex)) return;

            StringBuilder json = new StringBuilder(
                    "{\"ok\":true,\"transactions\":[");
            for (int i = transactions.size() - 1; i >= 0; i--) {
                if (i < transactions.size() - 1) json.append(",");
                json.append("\"")
                    .append(transactions.get(i).replace("\"", "\\\""))
                    .append("\"");
            }
            json.append("]}");
            sendJson(ex, 200, json.toString());
        });

        server.createContext("/api/change-pin", ex -> {
            if (!authorized(ex)) return;
            String body = read(ex);
            String oldPin = field(body, "oldPin");
            String newPin = field(body, "newPin");

            if (!pin.equals(oldPin)) {
                sendJson(ex, 400,
                        "{\"ok\":false,\"message\":\"Incorrect old PIN\"}");
            } else if (!newPin.matches("\\d{4}")) {
                sendJson(ex, 400,
                        "{\"ok\":false,\"message\":\"PIN must be 4 digits\"}");
            } else {
                pin = newPin;
                transactions.add("PIN changed | " + time());
                sendJson(ex, 200,
                        "{\"ok\":true,\"message\":\"PIN changed successfully\"}");
            }
        });

        server.start();
        System.out.println("ATM running at http://localhost:8080");
        System.out.println("Account: 1001 | PIN: 1234");
    }

    static boolean authorized(HttpExchange ex) throws IOException {
        if (!loggedIn) {
            sendJson(ex, 401,
                    "{\"ok\":false,\"message\":\"Please log in first\"}");
            return false;
        }
        return true;
    }

    static String read(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    // Reads simple JSON string fields used by this application.
    static String field(String json, String key) {
        String search = "\"" + key + "\"";
        int i = json.indexOf(search);
        if (i < 0) return "";
        i = json.indexOf(':', i + search.length()) + 1;
        while (i < json.length()
                && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return "";
        int end = json.indexOf('"', i + 1);
        return end < 0 ? "" : json.substring(i + 1, end);
    }

    static double amount(String json) {
        try {
            return Double.parseDouble(field(json, "amount"));
        } catch (Exception e) {
            return -1;
        }
    }

    static boolean valid(double amount) {
        return Double.isFinite(amount)
                && amount > 0 && amount <= 100000;
    }

    static String time() {
        return LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
    }

    static void sendJson(HttpExchange ex, int status, String data)
            throws IOException {
        ex.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        send(ex, status, data);
    }

    static void send(HttpExchange ex, int status, String data)
            throws IOException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
