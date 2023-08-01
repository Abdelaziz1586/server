import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static Server INSTANCE;

    public Process process = null;
    private BufferedReader br = null;
    private ServerSocket server = null;
    private InputStreamReader isr = null;

    public Server(final int port) {

        INSTANCE = this;

        try {
            server = new ServerSocket(port);

            final ExecutorService threadPool = Executors.newFixedThreadPool(10);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.close();

                    if (process != null) process.destroy();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));

            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (process != null) {

                        if (isr == null) {
                            isr = new InputStreamReader(process.getInputStream());
                            br = new BufferedReader(isr);
                        }

                        final String line;
                        try {
                            line = br.readLine();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        if (line != null) System.out.println(line);
                    }
                }
            }, 0, 1);


            while (true) {
                if (server == null) return;

                Socket clientSocket = server.accept();

                if (!clientSocket.getInetAddress().toString().equals("/127.0.0.1")) {

                    System.out.println("Incoming connection from " + clientSocket.getInetAddress());
                    try {
                        Socket targetSocket = new Socket("localhost", 25566);

                        System.out.println("Connected to target server: " + "127.0.0.1" + ":" + 25566);

                        threadPool.execute(() -> forwardData(clientSocket, targetSocket));
                        threadPool.execute(() -> forwardData(targetSocket, clientSocket));
                    } catch (final Exception ignored) {
                        System.out.println("Closed incoming connection as the server is starting (X220)");
                        clientSocket.close();

                        if (process == null) {
                            process = new ProcessBuilder("java", "-Xms128M", "-XX:MaxRAMPercentage=95.0", "-Dterminal.jline=false", "-Dterminal.ansi=true", "-jar", "paper.jar").start();
                            new Thread(this::checkForInput).start();
                        }
                    }
                }

            }
        } catch (final IOException ignored) {
        }
    }

    private void checkForInput() {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        final PrintWriter pw = new PrintWriter(new OutputStreamWriter(process.getOutputStream()));

        final String line;
        try {
            line = reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // process stdin

        pw.println(line);
        pw.flush();

        checkForInput();
    }


    private void forwardData(final Socket sourceSocket, final Socket destinationSocket) {
        try {
            final OutputStream output = destinationSocket.getOutputStream();

            final byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = sourceSocket.getInputStream().read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
        } catch (final IOException ignored) {
        }
    }
}
