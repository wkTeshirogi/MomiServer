package jp.xoki.web.zunda.spike.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static jp.xoki.web.zunda.spike.git.constants.SpikeConstants.COMMAND_TIMEOUT_SECONDS;

/** Native Git processes are confined to synthetic worktrees and process-local configuration. */
final class NativeGit {
    private final Path root;
    private final Path certificate;
    private final Path emptyConfig;

    NativeGit(Path root, Path certificate) throws IOException {
        this.root = root.toRealPath();
        this.certificate = certificate;
        this.emptyConfig = Files.createFile(root.resolve("empty-gitconfig"));
    }

    record Result(int code, String output) { }
    record Running(Process process, Path output) { }

    Result run(Path directory, String credentials, String... arguments) throws Exception {
        return finish(start(directory, credentials, true, arguments));
    }

    Result untrusted(Path directory, String credentials, String... arguments) throws Exception {
        return finish(start(directory, credentials, false, arguments));
    }

    Running start(Path directory, String credentials, boolean trust, String... arguments) throws IOException {
        if (!directory.toRealPath().startsWith(root)) {
            throw new IllegalArgumentException("Git cwd must be a synthetic directory");
        }

        // No user config, credentials helper, TLS bypass, inherited trace, or proxy is used.
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(key -> key.toUpperCase(java.util.Locale.ROOT).startsWith("GIT_"));
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("GIT_CONFIG_GLOBAL", emptyConfig.toString());
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.put("GIT_AUTHOR_NAME", "Spike fixture");
        environment.put("GIT_AUTHOR_EMAIL", "spike@example.invalid");
        environment.put("GIT_COMMITTER_NAME", "Spike fixture");
        environment.put("GIT_COMMITTER_EMAIL", "spike@example.invalid");
        List<String[]> config = new ArrayList<>();
        config.add(new String[] { "credential.helper", "" });
        config.add(new String[] { "http.proxy", "" });
        config.add(new String[] { "http.sslVerify", "true" });
        if (System.getProperty("os.name").startsWith("Windows")) {
            config.add(new String[] { "http.sslBackend", "openssl" });
        }
        if (trust) {
            config.add(new String[] { "http.sslCAInfo", certificate.toString() });
        }
        if (credentials != null) {
            config.add(new String[] { "http.extraHeader", "Authorization: Basic "
                    + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)) });
        }
        environment.put("GIT_CONFIG_COUNT", Integer.toString(config.size()));
        for (int index = 0; index < config.size(); index++) {
            environment.put("GIT_CONFIG_KEY_" + index, config.get(index)[0]);
            environment.put("GIT_CONFIG_VALUE_" + index, config.get(index)[1]);
        }
        Path output = root.resolve("git-" + UUID.randomUUID() + ".log");
        return new Running(builder.redirectErrorStream(true).redirectOutput(output.toFile()).start(), output);
    }

    Result finish(Running running) throws Exception {
        if (!running.process().waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            terminate(running);
            throw new IOException("Synthetic Git command timed out");
        }
        return new Result(running.process().exitValue(), Files.readString(running.output()));
    }

    void terminate(Running running) throws Exception {
        // Git spawns remote-https/pack children; interrupt only this owned process tree, not unrelated clients.
        List<ProcessHandle> descendants = running.process().descendants().toList();
        descendants.reversed().forEach(ProcessHandle::destroyForcibly);
        running.process().destroyForcibly();
        running.process().waitFor(5, TimeUnit.SECONDS);
        for (ProcessHandle child : descendants) {
            child.onExit().get(5, TimeUnit.SECONDS);
        }
    }
}
