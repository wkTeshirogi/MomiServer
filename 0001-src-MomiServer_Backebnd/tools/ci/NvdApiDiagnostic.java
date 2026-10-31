import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import javax.net.ssl.SSLException;

/** NVD connection diagnostic for CI.
 *
 * Records only controlled fields; never prints credentials, response headers/body,
 * exception messages, or stack traces. This is not a vulnerability assessment.
 */
public final class NvdApiDiagnostic {

    /** Run a single bounded diagnostic against the fixed official endpoint.
     *
     * The API key is read only from the environment, not command-line arguments.
     *
     * @param args one output file path beneath the caller's report directory
     */
    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException | RuntimeException exception) {
            System.err.println("NVD diagnostic helper failed: " + exception.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Specify one diagnostic output file.");
        }
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            Properties result = probe(client,
                    URI.create("https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=1"),
                    System.getenv("NVD_API_KEY"), Duration.ofSeconds(30));
            Path output = Path.of(args[0]);
            Files.createDirectories(output.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                result.store(writer, "NVD connection diagnostic; not a scan result");
            }
            System.out.printf("NVD diagnostic: keyPresent=%s outcome=%s httpStatus=%s elapsedMillis=%s%n",
                    result.getProperty("keyPresent"), result.getProperty("outcome"),
                    result.getProperty("httpStatus"), result.getProperty("elapsedMillis"));
            System.out.println("NVD diagnostic does not certify the subsequent full data update.");
        }
    }

    /** Inspect connectivity without exposing server-controlled text.
     *
     * Failures remain diagnostic data so the real scanner can perform its retries.
     *
     * @param client HTTP client with normal TLS verification and redirects disabled
     * @param endpoint fixed production URI, or a local test server URI
     * @param key API key, omitted when blank
     * @param timeout request deadline
     * @return sanitized connection metadata
     */
    static Properties probe(HttpClient client, URI endpoint, String key, Duration timeout) {
        Properties result = new Properties();
        boolean hasKey = key != null && !key.isBlank();
        result.setProperty("keyPresent", Boolean.toString(hasKey));
        result.setProperty("httpStatus", "none");
        long start = System.nanoTime();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Accept", "application/json").GET();
            if (hasKey) {
                request.header("apiKey", key);
            }
            HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            result.setProperty("httpStatus", Integer.toString(response.statusCode()));
            result.setProperty("outcome", "http_response");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result.setProperty("outcome", "interrupted");
        } catch (IOException exception) {
            result.setProperty("outcome", classify(exception));
        } catch (IllegalArgumentException exception) {
            result.setProperty("outcome", "invalid_request_configuration");
        }
        result.setProperty("elapsedMillis", Long.toString((System.nanoTime() - start) / 1_000_000));
        return result;
    }

    /** Classify known communication failures without including exception messages.
     *
     * @param exception failure returned by the HTTP client
     * @return controlled diagnostic code
     */
    static String classify(Throwable exception) {
        for (int depth = 0; exception != null && depth < 10; depth++, exception = exception.getCause()) {
            if (exception instanceof SSLException) {
                return "tls_error";
            }
            if (exception instanceof UnknownHostException || exception instanceof UnresolvedAddressException) {
                return "dns_error";
            }
            if (exception instanceof HttpTimeoutException) {
                return "timeout";
            }
        }
        return "connection_or_io_error";
    }
}
