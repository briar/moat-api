package org.briarproject.moat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.briarproject.socks.NoDns;
import org.briarproject.socks.SocksSocketFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.annotation.Nullable;
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
import static java.util.logging.Logger.getLogger;

@NotNullByDefault
public class MoatApi {

	// TODO remove logging
	private static final Logger LOG = getLogger(MoatApi.class.getName());

	private static final String MOAT_URL = "https://bridges.torproject.org/moat";
	private static final String MOAT_CIRCUMVENTION_SETTINGS = "circumvention/settings";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final int CONNECT_TO_PROXY_TIMEOUT = (int) SECONDS.toMillis(5);
	private static final int EXTRA_CONNECT_TIMEOUT = (int) SECONDS.toMillis(120);
	private static final int EXTRA_SOCKET_TIMEOUT = (int) SECONDS.toMillis(30);
	private static final String SOCKS_USERNAME =
			"url=https://moat.torproject.org.global.prod.fastly.net/;front=cdn.sstatic.net";
	private static final String SOCKS_PASSWORD = "\u0000";

	private final File obfs4Executable;
	private final File obfs4Dir;
	private final JsonMapper mapper = JsonMapper.builder()
			.enable(BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
			.build();

	public MoatApi(File obfs4Executable, File obfs4Dir) {
		if (!obfs4Dir.isDirectory()) throw new IllegalArgumentException();
		this.obfs4Executable = obfs4Executable;
		this.obfs4Dir = obfs4Dir;
	}

	public List<Bridges> get() throws IOException {
		return getWithCountry(null);
	}

	public List<Bridges> getWithCountry(@Nullable String country) throws IOException {
		Process obfs4Process = startObfs4();
		try {
			int port = getPort(obfs4Process);
			SocketFactory socketFactory = new SocksSocketFactory(
					new InetSocketAddress("localhost", port),
					CONNECT_TO_PROXY_TIMEOUT,
					EXTRA_CONNECT_TIMEOUT,
					EXTRA_SOCKET_TIMEOUT,
					SOCKS_USERNAME,
					SOCKS_PASSWORD
			);
			OkHttpClient client = new OkHttpClient.Builder()
					.socketFactory(socketFactory)
					.dns(new NoDns())
					.connectTimeout(60, SECONDS)
					.build();

			String requestJson = country == null ? "" : "{\"country\": \"" + country + "\"}";
			RequestBody requestBody = RequestBody.create(JSON, requestJson);
			Request request = new Request.Builder()
					.url(MOAT_URL + "/" + MOAT_CIRCUMVENTION_SETTINGS)
					.post(requestBody)
					.build();
			LOG.info("Sending request '" + requestJson + "' to " + request.url());
			Response response = client.newCall(request).execute();
			ResponseBody responseBody = response.body();
			if (!response.isSuccessful() || responseBody == null)
				throw new IOException("request error");
			String responseJson = responseBody.string();
			LOG.info("Received response '" + responseJson + "'");
			JsonNode node = mapper.readTree(responseJson);
			JsonNode settings = node.get("settings");
			if (settings == null) throw new IOException("no settings in response");
			if (!settings.isArray()) throw new IOException("settings not an array");
			List<Bridges> bridges = new ArrayList<>();
			for (JsonNode n : ((ArrayNode) settings)) {
				bridges.add(parseBridges(n));
			}
			return bridges;
		} finally {
			// TODO remove logging
			File[] files = obfs4Dir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.getName().equals("obfs4proxy.log")) {
						Scanner s = new Scanner(new FileInputStream(file));
						while (s.hasNextLine()) {
							LOG.info("LOG: " + s.nextLine());
						}
						s.close();
					}
					//noinspection ResultOfMethodCallIgnored
					file.delete();
				}
			}
			obfs4Process.destroy();
		}
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
			for (JsonNode b : ((ArrayNode) bridgeStrings)) {
				bridges.add(b.asText());
			}
		} else {
			bridges = emptyList();
		}
		return new Bridges(type, source, bridges);
	}

	private Process startObfs4() throws IOException {
		// TODO remove logging
		ProcessBuilder pb = new ProcessBuilder(obfs4Executable.getAbsolutePath(),
				"-enableLogging",
				"-logLevel=DEBUG"
		);
		Map<String, String> env = pb.environment();
		env.put("TOR_PT_MANAGED_TRANSPORT_VER", "1");
		env.put("TOR_PT_STATE_LOCATION", obfs4Dir.getAbsolutePath());
		env.put("TOR_PT_EXIT_ON_STDIN_CLOSE", "0");
		env.put("TOR_PT_CLIENT_TRANSPORTS", "meek_lite");
		pb.redirectErrorStream(true);
		try {
			return pb.start();
		} catch (SecurityException e) {
			throw new IOException(e);
		}
	}

	private int getPort(Process process) throws IOException {
		try (Scanner s = new Scanner(process.getInputStream())) {
			while (s.hasNextLine()) {
				String line = s.nextLine();
				LOG.info("STDOUT: " + line); // TODO remove logging
				if (line.startsWith("CMETHOD meek_lite socks5 127.0.0.1:")) {
					try {
						// TODO might need to keep consuming stdout on Windows
						//  to stop obfs4proxy process hanging
						return parseInt(line.substring(35));
					} catch (NumberFormatException e) {
						throw new IOException("Invalid port $line");
					}
				}
			}
			throw new IOException("Did not find meek");
		}
	}
}
