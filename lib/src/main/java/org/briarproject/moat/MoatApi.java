package org.briarproject.moat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.briarproject.nullsafety.NotNullByDefault;
import org.briarproject.socks.SocksSocketFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.net.SocketFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.fasterxml.jackson.databind.MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES;
import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;

@NotNullByDefault
public class MoatApi {

	private static final String MOAT_URL = "https://bridges.torproject.org/moat";
	private static final String MOAT_CIRCUMVENTION_SETTINGS = "circumvention/settings";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String PORT_PREFIX = "CMETHOD meek_lite socks5 127.0.0.1:";

	private static final int CONNECT_TO_PROXY_TIMEOUT = (int) SECONDS.toMillis(5);
	private static final int EXTRA_CONNECT_TIMEOUT = (int) SECONDS.toMillis(120);
	private static final int EXTRA_SOCKET_TIMEOUT = (int) SECONDS.toMillis(30);
	private static final String SOCKS_PASSWORD = "\u0000";

	private final File obfs4Executable, obfs4Dir;
	private final String url, front;
	private final JsonMapper mapper = JsonMapper.builder()
			.enable(BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
			.build();

	public MoatApi(File obfs4Executable, File obfs4Dir, String url, String front) {
		if (!obfs4Dir.isDirectory()) throw new IllegalArgumentException();
		this.obfs4Executable = obfs4Executable;
		this.obfs4Dir = obfs4Dir;
		this.url = url;
		this.front = front;
	}

	public List<Bridges> get() throws IOException {
		return getWithCountry("");
	}

	public List<Bridges> getWithCountry(String country) throws IOException {
		Process obfs4Process = startObfs4();
		try {
			int port = getPort(obfs4Process);
			String socksUsername = "url=" + url + ";front=" + front;
			SocketFactory socketFactory = new SocksSocketFactory(
					new InetSocketAddress("localhost", port),
					CONNECT_TO_PROXY_TIMEOUT,
					EXTRA_CONNECT_TIMEOUT,
					EXTRA_SOCKET_TIMEOUT,
					socksUsername,
					SOCKS_PASSWORD
			);
			OkHttpClient client = new OkHttpClient.Builder()
					.socketFactory(socketFactory)
					.dns(new NoDns())
					.build();

			String requestJson = country.isEmpty() ? "" : "{\"country\": \"" + country + "\"}";
			RequestBody requestBody = RequestBody.create(JSON, requestJson);
			Request request = new Request.Builder()
					.url(MOAT_URL + "/" + MOAT_CIRCUMVENTION_SETTINGS)
					.post(requestBody)
					.build();
			Response response = client.newCall(request).execute();
			ResponseBody responseBody = response.body();
			if (!response.isSuccessful() || responseBody == null)
				throw new IOException("request error");
			String responseJson = responseBody.string();
			return parseResponse(responseJson);
		} finally {
			obfs4Process.destroy();
		}
	}

	private List<Bridges> parseResponse(String responseJson) throws IOException {
		JsonNode node = mapper.readTree(responseJson);
		JsonNode settings = node.get("settings");
		if (settings == null) throw new IOException("no settings in response");
		if (!settings.isArray()) throw new IOException("settings not an array");
		List<Bridges> bridges = new ArrayList<>();
		for (JsonNode n : settings) {
			bridges.add(parseBridges(n));
		}
		return bridges;
	}

	private Bridges parseBridges(JsonNode node) throws IOException {
		JsonNode bridgesNode = node.get("bridges");
		if (bridgesNode == null) throw new IOException("no bridges node");
		String type = bridgesNode.get("type").asText();
		String source = bridgesNode.get("source").asText();
		JsonNode bridgeStrings = bridgesNode.get("bridge_strings");
		List<String> bridges;
		if (bridgeStrings instanceof ArrayNode) {
			bridges = new ArrayList<>();
			for (JsonNode b : bridgeStrings) {
				bridges.add(b.asText());
			}
		} else {
			bridges = emptyList();
		}
		return new Bridges(type, source, bridges);
	}

	private Process startObfs4() throws IOException {
		ProcessBuilder pb = new ProcessBuilder(obfs4Executable.getAbsolutePath());
		Map<String, String> env = pb.environment();
		env.put("TOR_PT_MANAGED_TRANSPORT_VER", "1");
		env.put("TOR_PT_STATE_LOCATION", obfs4Dir.getAbsolutePath());
		env.put("TOR_PT_EXIT_ON_STDIN_CLOSE", "1");
		env.put("TOR_PT_CLIENT_TRANSPORTS", "meek_lite");
		pb.redirectErrorStream(true);
		try {
			return pb.start();
		} catch (SecurityException e) {
			throw new IOException(e);
		}
	}

	private int getPort(Process process) throws IOException {
		BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
		Thread t = new Thread(() -> getPort(process, queue));
		t.setDaemon(false);
		t.start();
		try {
			int port = queue.take();
			if (port == -1) throw new IOException("Failed to parse port number from stdout");
			return port;
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	private void getPort(Process process, BlockingQueue<Integer> queue) {
		boolean found = false;
		try (Scanner s = new Scanner(process.getInputStream())) {
			while (s.hasNextLine()) {
				String line = s.nextLine();
				if (!found && line.startsWith(PORT_PREFIX)) {
					found = true;
					try {
						queue.add(parseInt(line.substring(PORT_PREFIX.length())));
					} catch (NumberFormatException e) {
						queue.add(-1);
					}
				}
			}
		}
		if (!found) queue.add(-1);
		// Wait for the process to exit
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
