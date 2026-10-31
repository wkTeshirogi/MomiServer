package jp.xoki.web.zunda.spike.git;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static jp.xoki.web.zunda.spike.git.constants.SpikeConstants.*;

/**
 * Isolated servlet host.
 *
 * Credentials, permissions, audit and failure injection are test fixtures, not production implementations.
 */
final class SpikeServer implements AutoCloseable {
    private final Git bare;
    private final WebServer server;
    private final Map<String, Credential> credentials = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();
    final List<Map<String, String>> updates = new CopyOnWriteArrayList<>();
    final List<String> requests = new CopyOnWriteArrayList<>();
    final AtomicBoolean dropAcknowledgement = new AtomicBoolean();
    final AtomicBoolean committed = new AtomicBoolean();
    volatile long objectLimit;
    volatile CountDownLatch receiveArrivals;
    volatile CountDownLatch releaseReceive;
    volatile CountDownLatch uploadPaused;
    volatile CountDownLatch releaseUpload;
    volatile long uploadedBytes;
    final CountDownLatch interruptedUploadFinished = new CountDownLatch(1);

    private record Credential(byte[] digest, boolean write, boolean allowedRepository, boolean active, Instant expiresAt) { }

    SpikeServer(Path root, Path keyStore, String password) throws Exception {
        Files.createDirectories(root);
        bare = Git.init().setBare(true).setInitialBranch("main")
                .setDirectory(root.resolve("store/internal-id-1.git").toFile()).call();
        GitServlet servlet = new GitServlet();
        servlet.setAsIsFileService(null);
        servlet.setRepositoryResolver((request, name) -> {
            if (!REPOSITORY_NAME.equals(name)) {
                throw new RepositoryNotFoundException(name);
            }
            repository().incrementOpen();
            return repository();
        });
        servlet.setReceivePackFactory((request, repository) -> {
            ReceivePack receive = new ReceivePack(repository);
            receive.setAllowNonFastForwards(false);
            receive.setMaxObjectSizeLimit(objectLimit);
            receive.setRefLogIdent(new PersonIdent((String) request.getAttribute("actor"), "spike@example.invalid"));
            receive.setPreReceiveHook((pack, commands) -> {
                boolean forbidden = commands.stream().anyMatch(command -> MAIN_REF.equals(command.getRefName())
                        || (command.getRefName().startsWith("refs/tags/")
                        && !command.getOldId().equals(org.eclipse.jgit.lib.ObjectId.zeroId())));
                if (forbidden) {
                    // Reject every command, even when the client did not request atomic push.
                    commands.forEach(command -> command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON,
                            "fixture ref protection"));
                }
                awaitGate(receiveArrivals, releaseReceive);
            });
            receive.setPostReceiveHook((pack, commands) -> {
                for (ReceiveCommand command : commands) {
                    updates.add(Map.of("actor", (String) request.getAttribute("actor"), "ref", command.getRefName(),
                            "old", command.getOldId().name(), "new", command.getNewId().name()));
                }
                committed.set(true);
            });
            return receive;
        });

        // A separate loopback-only TLS listener; no production ports, stores, or trust settings are changed.
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        factory.setAddress(InetAddress.getByName("127.0.0.1"));
        factory.setBaseDirectory(root.resolve("tomcat").toFile());
        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        ssl.setKeyStore(keyStore.toUri().toString());
        ssl.setKeyStorePassword(password);
        ssl.setKeyStoreType("PKCS12");
        ssl.setKeyAlias("spike");
        factory.setSsl(ssl);
        server = factory.getWebServer(context -> {
            context.addServlet("jgit", servlet).addMapping("/git/*");
            context.addFilter("fixture-auth", (Filter) (request, response, chain) ->
                    filter((HttpServletRequest) request, (HttpServletResponse) response, chain))
                    .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/git/*");
        });
        server.start();
    }

    Repository repository() { return bare.getRepository(); }
    String baseUrl() { return "https://127.0.0.1:" + server.getPort() + "/git/"; }
    String url() { return baseUrl() + REPOSITORY_NAME; }

    String credential(String user, boolean write, boolean allowedRepository, boolean active) {
        String token = UUID.randomUUID().toString();
        credentials.put(user, new Credential(digest(token), write, allowedRepository, active, Instant.MAX));
        return user + ":" + token;
    }

    void revoke(String user) { credentials.remove(user); }

    void expire(String user) {
        credentials.computeIfPresent(user, (name, identity) -> new Credential(identity.digest(), identity.write(),
                identity.allowedRepository(), identity.active(), Instant.EPOCH));
    }

    Map<String, String> refs() throws IOException {
        Map<String, String> result = new TreeMap<>();
        repository().getRefDatabase().getRefsByPrefix("refs/")
                .forEach(ref -> result.put(ref.getName(), ref.getObjectId().name()));
        return result;
    }

    private void filter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        requests.add(request.getMethod() + " " + request.getRequestURI());
        String[] pair = decode(request.getHeader("Authorization"));
        Credential identity = pair == null ? null : credentials.get(pair[0]);
        if (identity == null || !identity.active() || !identity.expiresAt().isAfter(clock.instant())
                || !MessageDigest.isEqual(identity.digest(), digest(pair[1]))) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"isolated-spike\"");
            response.setStatus(401);
            return;
        }
        boolean push = request.getRequestURI().endsWith("/git-receive-pack")
                || "git-receive-pack".equals(request.getParameter("service"));
        if (!identity.allowedRepository() || (push && !identity.write())) {
            response.setStatus(403);
            return;
        }
        request.setAttribute("actor", pair[0]);
        boolean receivePost = "POST".equals(request.getMethod())
                && request.getRequestURI().endsWith("/git-receive-pack");
        HttpServletRequest actualRequest = receivePost && uploadPaused != null ? paused(request) : request;
        HttpServletResponse actualResponse = receivePost && dropAcknowledgement.get() ? dropped(response) : response;
        try {
            chain.doFilter(actualRequest, actualResponse);
            if (receivePost && dropAcknowledgement.get() && committed.get()) {
                // A proxy-style response failure after the Git ref update has already committed.
                response.reset();
                response.setStatus(502);
                response.setContentLength(0);
            }
        } finally {
            if (Boolean.TRUE.equals(request.getAttribute(UPLOAD_PAUSED_ATTRIBUTE))) {
                interruptedUploadFinished.countDown();
            }
        }
    }

    private HttpServletRequest paused(HttpServletRequest request) throws IOException {
        ServletInputStream input = request.getInputStream();
        return new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    private boolean paused;
                    @Override public boolean isFinished() { return input.isFinished(); }
                    @Override public boolean isReady() { return input.isReady(); }
                    @Override public void setReadListener(ReadListener listener) { input.setReadListener(listener); }
                    @Override public int read() throws IOException {
                        byte[] buffer = new byte[1];
                        return read(buffer, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(buffer[0]);
                    }
                    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                        int count = input.read(buffer, offset, length);
                        if (count > 0) { uploadedBytes += count; }
                        if (!paused && uploadedBytes >= 32768) {
                            paused = true;
                            request.setAttribute(UPLOAD_PAUSED_ATTRIBUTE, Boolean.TRUE);
                            awaitGate(uploadPaused, releaseUpload);
                        }
                        return count;
                    }
                };
            }
        };
    }

    private HttpServletResponse dropped(HttpServletResponse response) throws IOException {
        return new HttpServletResponseWrapper(response) {
            @Override public void flushBuffer() { }
            @Override public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    @Override public boolean isReady() { return true; }
                    @Override public void setWriteListener(WriteListener listener) { }
                    @Override public void write(int value) { }
                    @Override public void write(byte[] bytes, int offset, int length) { }
                };
            }
        };
    }

    private static void awaitGate(CountDownLatch arrived, CountDownLatch release) {
        if (arrived == null || release == null) { return; }
        arrived.countDown();
        try {
            if (!release.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("fixture gate timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String[] decode(String header) {
        if (header == null || !header.startsWith("Basic ")) { return null; }
        try {
            String value = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
            int separator = value.indexOf(':');
            return separator < 0 ? null : new String[] { value.substring(0, separator), value.substring(separator + 1) };
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void close() {
        if (releaseReceive != null) { releaseReceive.countDown(); }
        if (releaseUpload != null) { releaseUpload.countDown(); }
        server.stop();
        bare.close();
    }
}
