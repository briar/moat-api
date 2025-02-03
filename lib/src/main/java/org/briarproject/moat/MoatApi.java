package org.briarproject.moat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.briarproject.nullsafety.NotNullByDefault;
import org.briarproject.socks.SocksSocketFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.fasterxml.jackson.databind.MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES;
import static java.lang.Integer.parseInt;
import static java.lang.System.arraycopy;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Logger.getLogger;
import static javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

@NotNullByDefault
public class MoatApi {

	private static final Logger LOG = getLogger(MoatApi.class.getName());

	private static final String MOAT_URL = "https://bridges.torproject.org/moat";
	private static final String MOAT_CIRCUMVENTION_SETTINGS = "circumvention/settings";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String PORT_PREFIX = "CMETHOD meek_lite socks5 127.0.0.1:";
	private static final String ISRG_RESOURCE_NAME = "isrg-root-x1.der";

	private static final int CONNECT_TO_PROXY_TIMEOUT = (int) SECONDS.toMillis(5);
	private static final int EXTRA_CONNECT_TIMEOUT = (int) SECONDS.toMillis(120);
	private static final int EXTRA_SOCKET_TIMEOUT = (int) SECONDS.toMillis(30);
	private static final String SOCKS_PASSWORD = "\u0000";

	private final File lyrebirdExecutable, lyrebirdDir;
	private final String url, front;
	private final JsonMapper mapper = JsonMapper.builder()
			.enable(BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
			.build();

	public MoatApi(File lyrebirdExecutable, File lyrebirdDir, String url, String front) {
		if (!lyrebirdDir.isDirectory()) throw new IllegalArgumentException();
		this.lyrebirdExecutable = lyrebirdExecutable;
		this.lyrebirdDir = lyrebirdDir;
		this.url = url;
		this.front = front;
	}

	public List<Bridges> get() throws IOException {
		return getWithCountry("");
	}

	public List<Bridges> getWithCountry(String country) throws IOException {
		Process lyrebirdProcess = startLyrebird();
		try {
			int port = getPort(lyrebirdProcess);
			String socksUsername = "url=" + url + ";front=" + front;
			SocketFactory socketFactory = new SocksSocketFactory(
					new InetSocketAddress("localhost", port),
					CONNECT_TO_PROXY_TIMEOUT,
					EXTRA_CONNECT_TIMEOUT,
					EXTRA_SOCKET_TIMEOUT,
					socksUsername,
					SOCKS_PASSWORD
			);
			X509TrustManager trustManager = createTrustManager();
			SSLSocketFactory sslSocketFactory = createSslSocketFactory(trustManager);
			OkHttpClient client = new OkHttpClient.Builder()
					.socketFactory(socketFactory)
					.sslSocketFactory(sslSocketFactory, trustManager)
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
		} catch (CertificateException | NoSuchAlgorithmException | KeyStoreException |
		         KeyManagementException e) {
			throw new IOException(e);
		} finally {
			lyrebirdProcess.destroy();
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

	private Process startLyrebird() throws IOException {
		ProcessBuilder pb = new ProcessBuilder(lyrebirdExecutable.getAbsolutePath());
		Map<String, String> env = pb.environment();
		env.put("TOR_PT_MANAGED_TRANSPORT_VER", "1");
		env.put("TOR_PT_STATE_LOCATION", lyrebirdDir.getAbsolutePath());
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

	private SSLSocketFactory createSslSocketFactory(X509TrustManager trustManager)
			throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext sslContext = SSLContext.getInstance("SSL");
		sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
		return sslContext.getSocketFactory();
	}

	@SuppressWarnings("CustomX509TrustManager")
	private X509TrustManager createTrustManager() throws IOException, CertificateException,
			NoSuchAlgorithmException, KeyStoreException {
		// Find the default X-509 trust manager
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(getDefaultAlgorithm());
		// Using null here initialises the TrustManagerFactory with the default trust store.
		tmf.init((KeyStore) null);
		X509TrustManager x509 = null;
		for (TrustManager tm : tmf.getTrustManagers()) {
			if (tm instanceof X509TrustManager) {
				x509 = (X509TrustManager) tm;
				break;
			}
		}
		if (x509 == null) throw new IOException("Could not find default X-509 trust manager");
		final X509TrustManager delegate = x509;

		// Return a trust manager that includes the root certificate used by Let's Encrypt
		X509Certificate authority = createX509Certificate();
		return new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType)
					throws CertificateException {
				delegate.checkClientTrusted(chain, authType);
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType)
					throws CertificateException {
				LOG.info("Auth type: " + authType);
				try {
					delegate.checkServerTrusted(chain, authType);
					LOG.info("Certificate chain was verified by default trust manager");
				} catch (CertificateException e) {
					LOG.info("Certificate chain was not verified by default trust manager: " + e);
					validateCertificateChain(chain, authority);
				}
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				X509Certificate[] defaultIssuers = delegate.getAcceptedIssuers();
				X509Certificate[] allIssuers = new X509Certificate[defaultIssuers.length + 1];
				arraycopy(defaultIssuers, 0, allIssuers, 0, defaultIssuers.length);
				allIssuers[defaultIssuers.length] = authority;
				return allIssuers;
			}
		};
	}

	private X509Certificate createX509Certificate() throws CertificateException {
		InputStream in = requireNonNull(
				getClass().getClassLoader().getResourceAsStream(ISRG_RESOURCE_NAME));
		CertificateFactory certFactory = CertificateFactory.getInstance("X509");
		X509Certificate cert = (X509Certificate) certFactory.generateCertificate(in);
		LOG.info("Adding certificate authority, issuer: " + cert.getIssuerX500Principal().getName()
				+ ", subject: " + cert.getSubjectX500Principal().getName());
		return cert;
	}

	static void validateCertificateChain(X509Certificate[] chain, X509Certificate authority)
			throws CertificateException {
		if (chain.length == 0) {
			throw new CertificateException("Certificate chain is empty");
		}
		X509Certificate prev = authority;
		for (int i = chain.length - 1; i >= 0; i--) {
			X509Certificate curr = chain[i];
			LOG.info("Checking subject: " + curr.getSubjectX500Principal().getName());
			// Check that the certificate is within its validity period
			curr.checkValidity();
			// Check that the issuer matches the subject of the previous certificate
			if (!Arrays.equals(curr.getIssuerUniqueID(), prev.getSubjectUniqueID())) {
				throw new CertificateException("Certificate issuer does not match");
			}
			// Check that the certificate can be used for digital signatures
			boolean[] keyUsage = curr.getKeyUsage();
			if (keyUsage.length == 0 || !keyUsage[0]) {
				throw new CertificateException(
						"Certificate is not authorised for digital signatures");
			}
			// Check the basic constraints. The number of CA certificates in the chain
			// following the current certificate is (i - 1).
			int constraints = curr.getBasicConstraints();
			int caPathLength = i - 1;
			if (constraints == -1) {
				LOG.info("Non-CA certificate");
				if (i != 0) {
					throw new CertificateException("Non-CA certificate found at invalid position");
				}
			} else if (i == 0) {
				throw new CertificateException("CA certificate found at invalid position");
			} else {
				LOG.info("CA certificate with maximum path length: " + constraints);
				if (constraints < caPathLength) {
					throw new CertificateException("CA certificate has maximum CA path length: "
							+ constraints + ", needed: " + caPathLength);
				}
			}
			// Check that the certificate was signed by the public key of the previous
			// certificate
			try {
				curr.verify(prev.getPublicKey());
			} catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException |
			         NoSuchProviderException e1) {
				throw new CertificateException(e1);
			}
			// All good, move on to the next certificate in the chain
			prev = curr;
		}
		LOG.info("Certificate chain accepted");
	}
}
