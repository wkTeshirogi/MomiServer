package jp.xoki.web.zunda.spike.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootVersion;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static jp.xoki.web.zunda.spike.git.constants.SpikeConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** End-to-end experiments use real HTTPS, native Git, JGit packs, and temporary filesystem repositories. */
class GitSmartHttpSpikeTest {
    @TempDir
    static Path suite;
    private static Path keyStore;
    private static Path certificate;
    private static String keyPassword;
    private static SSLSocketFactory tls;
    private Path root;
    private SpikeServer server;
    private NativeGit git;
    private String writer;
    private String reader;
    private String baseCommit;
    private final Map<String, Object> details = new LinkedHashMap<>();

    @BeforeAll
    static void createIsolatedTlsIdentity() throws Exception {
        keyStore = suite.resolve("server.p12");
        certificate = suite.resolve("server.pem");
        keyPassword = UUID.randomUUID().toString();
        Path executable = Path.of(System.getProperty("java.home"), "bin", "keytool");
        if (System.getProperty("os.name").startsWith("Windows")) {
            executable = executable.resolveSibling("keytool.exe");
        }
        runTool(executable, "-genkeypair", "-alias", "spike", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1,dns:localhost",
                "-ext", "BC=ca:true", "-ext", "EKU=serverAuth", "-storetype", "PKCS12", "-keystore",
                keyStore.toString(), "-storepass", keyPassword, "-noprompt");
        runTool(executable, "-exportcert", "-rfc", "-alias", "spike", "-keystore", keyStore.toString(),
                "-storepass", keyPassword, "-file", certificate.toString());
        KeyStore trust = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStore)) {
            trust.load(input, keyPassword.toCharArray());
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        tls = context.getSocketFactory();
    }

    private static void runTool(Path executable, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(executable.toString()));
        command.addAll(List.of(arguments));
        Path log = suite.resolve("keytool-" + UUID.randomUUID() + ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
            throw new IllegalStateException("Test certificate generation timed out");
        }
        assertThat(process.exitValue()).as(new String(Files.readAllBytes(log), StandardCharsets.UTF_8)).isZero();
    }

    @BeforeEach
    void startSyntheticServer() throws Exception {
        root = Files.createTempDirectory(suite, "case-");
        git = new NativeGit(root, certificate);
        server = new SpikeServer(root, keyStore, keyPassword);
        writer = server.credential("writer", true, true, true);
        reader = server.credential("reader", false, true, true);
        Path seed = root.resolve("seed");
        try (Git initial = Git.init().setInitialBranch("main").setDirectory(seed.toFile()).call()) {
            Files.writeString(seed.resolve("readme.txt"), "synthetic repository\n");
            initial.add().addFilepattern("readme.txt").call();
            baseCommit = initial.commit().setMessage("synthetic initial commit")
                    .setAuthor("Spike fixture", "spike@example.invalid")
                    .setCommitter("Spike fixture", "spike@example.invalid").call().name();
            initial.push().setRemote(server.repository().getDirectory().toURI().toString())
                    .setRefSpecs(new RefSpec(MAIN_REF + ":" + MAIN_REF)).call();
        }
        assertThat(server.repository().exactRef(Constants.HEAD).getTarget().getName()).isEqualTo(MAIN_REF);
    }

    @AfterEach
    void stopAndRecord(TestInfo info) throws Exception {
        if (server == null) { return; }
        details.put("refs", server.refs());
        details.put("requests", List.copyOf(server.requests));
        details.put("updates", List.copyOf(server.updates));
        Path barePath = server.repository().getDirectory().toPath();
        server.close();
        NativeGit.Result fsck = git.run(barePath, null, "fsck", "--full");
        details.put("fsckExit", fsck.code());
        details.put("java", System.getProperty("java.version"));
        details.put("springBoot", SpringBootVersion.getVersion());
        details.put("tomcat", org.apache.catalina.util.ServerInfo.getServerNumber());
        details.put("jgit", Git.class.getPackage().getImplementationVersion());
        details.put("nativeGit", git.run(root, null, "--version").output().trim());
        Path output = Path.of(System.getProperty("spike.output"));
        Files.createDirectories(output);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                output.resolve(info.getTestMethod().orElseThrow().getName() + ".json").toFile(), details);
        assertThat(fsck.code()).as(fsck.output()).isZero();
    }

    @Test
    void httpsCloneFetchPullPushAndAudit() throws Exception {
        Path first = cloneAs("first", writer);
        Path second = cloneAs("second", reader);
        String commit = commit(first, "feature.txt", "over real HTTPS\n");
        success(git.run(first, writer, "push", "origin", "HEAD:" + TOPIC_REF));
        success(git.run(second, reader, "fetch", "origin"));
        success(git.run(second, reader, "pull", "--ff-only", "origin", "topic"));
        assertThat(head(second)).isEqualTo(commit);
        assertThat(server.refs()).containsEntry(TOPIC_REF, commit);
        assertThat(server.updates).singleElement().satisfies(update -> {
            assertThat(update.get("actor")).isEqualTo("writer");
            assertThat(update.get("new")).isEqualTo(commit);
        });
        assertThat(server.requests).anyMatch(request -> request.endsWith("/git-upload-pack"));
        assertThat(server.requests).anyMatch(request -> request.endsWith("/git-receive-pack"));
    }

    @Test
    void tlsRejectsUntrustedCertificate() throws Exception {
        NativeGit.Result result = git.untrusted(root, reader, "ls-remote", server.url());
        assertThat(result.code()).isNotZero();
        assertThat(result.output()).containsIgnoringCase("certificate");
        success(git.run(root, reader, "ls-remote", server.url()));
    }

    @Test
    void missingInvalidExpiredAndRevokedTokensAreUnauthorized() throws Exception {
        String expired = server.credential("expired", false, true, true);
        server.expire("expired");
        String inactive = server.credential("inactive", false, true, false);
        String revoked = server.credential("revoked", false, true, true);
        server.revoke("revoked");
        for (String credential : new String[] { null, "writer:invalid", inactive, expired, revoked }) {
            HttpResult response = request("GET", REPOSITORY_NAME + "/info/refs?service=git-upload-pack", credential, null);
            assertThat(response.status()).isEqualTo(401);
        }
        assertThat(git.run(root, "writer:invalid", "ls-remote", server.url()).code()).isNotZero();
    }

    @Test
    void readOnlyAndRepositoryDenialsCannotChangeRefs() throws Exception {
        Path client = cloneAs("reader", reader);
        commit(client, "denied.txt", "read only\n");
        Map<String, String> before = server.refs();
        assertThat(git.run(client, reader, "push", "origin", "HEAD:" + TOPIC_REF).code()).isNotZero();
        String outsider = server.credential("outsider", true, false, true);
        assertThat(request("GET", REPOSITORY_NAME + "/info/refs?service=git-upload-pack", outsider, null).status())
                .isEqualTo(403);
        assertThat(request("POST", REPOSITORY_NAME + "/git-receive-pack", reader, "0000").status()).isEqualTo(403);
        assertThat(server.refs()).isEqualTo(before);
    }

    @Test
    void postIsAuthenticatedAgainAfterDiscovery() throws Exception {
        String credential = server.credential("transient", true, true, true);
        assertThat(request("GET", REPOSITORY_NAME + "/info/refs?service=git-receive-pack", credential, null).status())
                .isEqualTo(200);
        server.revoke("transient");
        assertThat(request("POST", REPOSITORY_NAME + "/git-receive-pack", credential, "0000").status()).isEqualTo(401);
        assertThat(request("POST", REPOSITORY_NAME + "/git-receive-pack", null, "0000").status()).isEqualTo(401);
    }

    @Test
    void directForceAndDeletePushToMainAreRejected() throws Exception {
        Path client = cloneAs("writer", writer);
        commit(client, "protected.txt", "not permitted\n");
        for (String refspec : List.of("HEAD:" + MAIN_REF, "+HEAD:" + MAIN_REF, ":" + MAIN_REF)) {
            assertThat(git.run(client, writer, "push", "origin", refspec).code()).isNotZero();
            assertThat(server.refs()).containsEntry(MAIN_REF, baseCommit);
        }
        assertThat(server.updates).isEmpty();
    }

    @Test
    void nonFastForwardAndTagOverwriteAreRejected() throws Exception {
        Path client = cloneAs("writer", writer);
        String newCommit = commit(client, "new.txt", "new commit\n");
        success(git.run(client, writer, "push", "origin", "HEAD:" + TOPIC_REF, "HEAD:refs/tags/release"));
        assertThat(git.run(client, writer, "push", "origin", "+" + baseCommit + ":" + TOPIC_REF).code()).isNotZero();
        assertThat(git.run(client, writer, "push", "origin", "+" + baseCommit + ":refs/tags/release").code()).isNotZero();
        assertThat(server.refs()).containsEntry(TOPIC_REF, newCommit).containsEntry("refs/tags/release", newCommit);
    }

    @Test
    void mixedProtectedPushDoesNotPartiallyUpdateRefs() throws Exception {
        Path client = cloneAs("writer", writer);
        commit(client, "mixed.txt", "mixed update\n");
        Map<String, String> before = server.refs();
        assertThat(git.run(client, writer, "push", "origin", "HEAD:" + TOPIC_REF, "HEAD:" + MAIN_REF).code())
                .isNotZero();
        assertThat(server.refs()).isEqualTo(before);
    }

    @Test
    void nativeAtomicPushCapabilityIsMeasured() throws Exception {
        Path client = cloneAs("writer", writer);
        String commit = commit(client, "atomic.txt", "atomic update\n");
        NativeGit.Result result = git.run(client, writer, "push", "--atomic", "origin",
                "HEAD:refs/heads/atomic-a", "HEAD:refs/heads/atomic-b");
        details.put("atomicPushSupported", result.code() == 0);
        details.put("atomicClientResult", result.output());
        if (result.code() == 0) {
            assertThat(server.refs()).containsEntry("refs/heads/atomic-a", commit)
                    .containsEntry("refs/heads/atomic-b", commit);
            commit(client, "atomic.txt", "rejected atomic update\n");
            Map<String, String> before = server.refs();
            assertThat(git.run(client, writer, "push", "--atomic", "origin", "HEAD:refs/heads/atomic-a",
                    "HEAD:" + MAIN_REF).code()).isNotZero();
            assertThat(server.refs()).isEqualTo(before);
        } else {
            assertThat(result.output()).containsIgnoringCase("atomic");
            assertThat(server.refs()).doesNotContainKeys("refs/heads/atomic-a", "refs/heads/atomic-b");
        }
    }

    @Test
    void concurrentPushUsesActualRefCompareAndSwap() throws Exception {
        Path first = cloneAs("first", writer);
        success(git.run(first, writer, "push", "origin", "HEAD:" + TOPIC_REF));
        Path second = cloneAs("second", writer);
        String firstCommit = commit(first, "first.txt", "first competing update\n");
        String secondCommit = commit(second, "second.txt", "second competing update\n");
        server.receiveArrivals = new CountDownLatch(2);
        server.releaseReceive = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<NativeGit.Result> one = executor.submit(() -> git.run(first, writer, "push", "origin", "HEAD:" + TOPIC_REF));
            Future<NativeGit.Result> two = executor.submit(() -> git.run(second, writer, "push", "origin", "HEAD:" + TOPIC_REF));
            try {
                assertThat(server.receiveArrivals.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            } finally {
                server.releaseReceive.countDown();
            }
            NativeGit.Result oneResult = one.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            NativeGit.Result twoResult = two.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(List.of(oneResult.code(), twoResult.code()).stream().filter(code -> code == 0).count()).isEqualTo(1);
            assertThat(server.refs().get(TOPIC_REF)).isIn(firstCommit, secondCommit);
            details.put("concurrentExitCodes", List.of(oneResult.code(), twoResult.code()));
        }
    }

    @Test
    void clientDisconnectDuringPackUploadLeavesRefsUnchanged() throws Exception {
        Path client = cloneAs("writer", writer);
        randomCommit(client, 2 * 1024 * 1024);
        Map<String, String> before = server.refs();
        server.uploadPaused = new CountDownLatch(1);
        server.releaseUpload = new CountDownLatch(1);
        NativeGit.Running process = git.start(client, writer, true, "-c", "http.postBuffer=1024", "push",
                "origin", "HEAD:" + TOPIC_REF);
        try {
            assertThat(server.uploadPaused.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } finally {
            git.terminate(process);
            server.releaseUpload.countDown();
        }
        assertThat(server.interruptedUploadFinished.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(server.uploadedBytes).isGreaterThanOrEqualTo(32768);
        assertThat(server.refs()).isEqualTo(before);
        details.put("receivedBytesBeforeDisconnect", server.uploadedBytes);
    }

    @Test
    void postCommitResponseFailureDoesNotRollBackRef() throws Exception {
        Path client = cloneAs("writer", writer);
        String commit = commit(client, "ack.txt", "response loss\n");
        server.dropAcknowledgement.set(true);
        server.committed.set(false);
        NativeGit.Result result = git.run(client, writer, "push", "origin", "HEAD:" + TOPIC_REF);
        assertThat(result.code()).isNotZero();
        assertThat(result.output()).contains("502");
        assertThat(server.refs()).containsEntry(TOPIC_REF, commit);
        server.dropAcknowledgement.set(false);
        success(git.run(client, writer, "push", "origin", "HEAD:" + TOPIC_REF));
        assertThat(server.updates).hasSize(1);
    }

    @Test
    void objectSizeLimitRejectsBeforeUpdatingRefs() throws Exception {
        Path client = cloneAs("writer", writer);
        randomCommit(client, 512 * 1024);
        server.objectLimit = 128 * 1024;
        Map<String, String> before = server.refs();
        assertThat(git.run(client, writer, "push", "origin", "HEAD:" + TOPIC_REF).code()).isNotZero();
        assertThat(server.refs()).isEqualTo(before);
    }

    @Test
    void malformedPackDoesNotChangeRefs() throws Exception {
        Map<String, String> before = server.refs();
        String command = "0".repeat(40) + " " + "f".repeat(40) + " refs/heads/bad\0report-status\n";
        String packet = String.format(java.util.Locale.ROOT, "%04x", command.length() + 4) + command;
        HttpResult result = request("POST", REPOSITORY_NAME + "/git-receive-pack", writer,
                packet + "0000PACKbadpack!");
        details.put("malformedPackHttpStatus", result.status());
        details.put("malformedPackGitResponse", result.body());
        assertThat(result.body()).containsIgnoringCase("unpack");
        assertThat(server.refs()).isEqualTo(before);
        assertThat(server.updates).isEmpty();
    }

    @Test
    void unknownTraversalAndDumbHttpPathsAreNotExported() throws Exception {
        for (String path : List.of("unknown.git/info/refs?service=git-upload-pack",
                "%2e%2e/private.git/info/refs?service=git-upload-pack",
                REPOSITORY_NAME + "/HEAD", REPOSITORY_NAME + "/config")) {
            HttpResult result = request("GET", path, reader, null);
            assertThat(result.status()).isBetween(400, 499);
            assertThat(result.body()).doesNotContain(root.toString());
        }
    }

    @Test
    void symlinkAliasOutsideRepositoryMappingIsRejected() throws Exception {
        Path alias = root.resolve("store/alias.git");
        Path outside = root.resolve("outside.git");
        try (Git external = Git.init().setBare(true).setDirectory(outside.toFile()).call()) {
            assertThat(external.getRepository().isBare()).isTrue();
        }
        try {
            Files.createSymbolicLink(alias, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            assumeTrue(false, "OS does not allow creating the synthetic symbolic link");
        }
        assertThat(request("GET", "alias.git/info/refs?service=git-upload-pack", reader, null).status()).isEqualTo(404);
    }

    @Test
    void offlineMirrorRestorePreservesAllRefsAndHead() throws Exception {
        Path client = cloneAs("writer", writer);
        commit(client, "backup.txt", "restorable fixture\n");
        success(git.run(client, writer, "push", "origin", "HEAD:" + TOPIC_REF, "HEAD:refs/tags/backup"));
        Map<String, String> expected = server.refs();
        Path backup = root.resolve("backup.git");
        Path restored = root.resolve("restored.git");
        // No writers run during this snapshot. GitLab, the application DB and real repositories are not involved.
        try (Git mirror = Git.cloneRepository().setURI(server.repository().getDirectory().toURI().toString())
                .setDirectory(backup.toFile()).setMirror(true).call()) {
            assertThat(mirror.getRepository().isBare()).isTrue();
        }
        try (Git copy = Git.cloneRepository().setURI(backup.toUri().toString())
                .setDirectory(restored.toFile()).setMirror(true).call()) {
            Map<String, String> actual = new TreeMap<>();
            for (Ref ref : copy.getRepository().getRefDatabase().getRefsByPrefix("refs/")) {
                actual.put(ref.getName(), ref.getObjectId().name());
            }
            assertThat(actual).isEqualTo(expected);
            assertThat(copy.getRepository().exactRef(Constants.HEAD).getTarget().getName()).isEqualTo(MAIN_REF);
            details.put("restoredRefs", actual);
        }
        success(git.run(restored, null, "fsck", "--full"));
    }

    private Path cloneAs(String name, String credential) throws Exception {
        success(git.run(root, credential, "clone", server.url(), name));
        return root.resolve(name);
    }

    private String commit(Path directory, String name, String value) throws Exception {
        Files.writeString(directory.resolve(name), value);
        return commitFile(directory, name);
    }

    private String randomCommit(Path directory, int size) throws Exception {
        byte[] content = new byte[size];
        new SecureRandom().nextBytes(content);
        Files.write(directory.resolve("payload.bin"), content);
        return commitFile(directory, "payload.bin");
    }

    private String commitFile(Path directory, String name) throws Exception {
        try (Git local = Git.open(directory.toFile())) {
            local.add().addFilepattern(name).call();
            return local.commit().setMessage("synthetic change")
                    .setAuthor("Spike fixture", "spike@example.invalid")
                    .setCommitter("Spike fixture", "spike@example.invalid").call().name();
        }
    }

    private String head(Path directory) throws Exception {
        try (Git local = Git.open(directory.toFile())) {
            return local.getRepository().resolve(Constants.HEAD).name();
        }
    }

    private void success(NativeGit.Result result) { assertThat(result.code()).as(result.output()).isZero(); }
    private record HttpResult(int status, String body) { }

    private HttpResult request(String method, String suffix, String credential, String body) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(server.baseUrl() + suffix)
                .toURL().openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(tls);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod(method);
        if (credential != null) {
            connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-git-receive-pack-request");
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            try (InputStream input = stream) {
                return new HttpResult(status, input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }
}
