package com.hugeinc.ideas;

import co.paralleluniverse.capsule.CapsuleUtils;
import co.paralleluniverse.capsule.Jar;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import static java.util.logging.Level.*;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class StaticServer {
    private static final String DEFAULT_FILE_NAME = "index.html";

    private static final Logger log = Logger.getGlobal();

    public StaticServer(final Path docRoot, final int port) {
        final HttpServer httpServer;
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start HTTP server", e);
        }
        httpServer.createContext("/", new HttpHandler() {

            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                String requestPath = httpExchange.getRequestURI().toString().substring(1);
                Path p = docRoot.resolve(requestPath);
                if (Files.isDirectory(p)) {
                    p = docRoot.resolve(requestPath + DEFAULT_FILE_NAME);
                }
                if (Files.isRegularFile(p)) {
                    String contentType = Files.probeContentType(p);
                    if (contentType == null) {
                        contentType = "application/octet-stream";
                    }
                    httpExchange.getResponseHeaders().add("Content-type", contentType);
                    if (httpExchange.getRequestMethod().equalsIgnoreCase("get")) {
                        httpExchange.sendResponseHeaders(200, Files.size(p));
                        Files.copy(p, httpExchange.getResponseBody());
                    } else if (httpExchange.getRequestMethod().equalsIgnoreCase("head")) {
                        httpExchange.sendResponseHeaders(200, 0);
                    } else {
                        httpExchange.sendResponseHeaders(405, 0);
                    }
                } else {
                    httpExchange.sendResponseHeaders(404, 0);
                }
                httpExchange.getResponseBody().close();
            }
        });
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        log.log(INFO, "HTTP server listening on port " + httpServer.getAddress().getPort());
    }

    public static void main(final String[] args) throws Exception {
        // set defaults
        int port = 8080;
        Path docRoot = Paths.get(".");
        // use passed args
        int nextArg = 0;
        if (args.length > 0) {
            if (args[nextArg].equalsIgnoreCase("bundle")) {
                nextArg++;
                Path jarFile = CapsuleUtils.getCapsuleJar();
                log.log(INFO, "Jar file path is " + jarFile);
                docRoot = Paths.get(args[nextArg]);
                if (!Files.isDirectory(docRoot)) {
                    System.err.println("First argument [" + args[0] + "] must be a valid directory.");
                    System.exit(1);
                }
                Jar jar = new Jar(jarFile);
                log.log(INFO, "Adding [" + docRoot + "] to jar [" + jarFile + "]");
                jar.addEntries((Path)null, docRoot);
                System.exit(0);
            } else {
                docRoot = Paths.get(args[0]);
                if (!Files.isDirectory(docRoot)) {
                    System.err.println("First argument [" + args[0] + "] must be a valid directory.");
                    System.exit(1);
                }
                if (args.length > 1) {
                    try {
                        port = new Integer(args[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Second argument [" + args[1] + "] must be an integer.");
                        System.exit(1);
                    }
                }
            }
        }
        log.log(INFO, "Starting server with doc root [" + docRoot.toString() + "] and port [" + port + "]");
        new StaticServer(docRoot, port);
    }
}
