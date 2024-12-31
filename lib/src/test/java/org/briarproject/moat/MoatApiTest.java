package org.briarproject.moat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class MoatApiTest {

	private static final String FASTLY_URL = "https://moat.torproject.org.global.prod.fastly.net/";
	private static final String[] FASTLY_FRONTS = new String[]{"cdn.yelp.com", "www.shazam.com",
			"www.cosmopolitan.com", "www.esquire.com"};
	private static final String AZURE_URL = "https://onion.azureedge.net/";
	private static final String[] AZURE_FRONTS = new String[]{"ajax.aspnetcdn.com"};
	private static final String CDN77_URL = "https://1314488750.rsc.cdn77.org/";
	private static final String[] CDN77_FRONTS = new String[]{"www.phpmyadmin.net"};

	@TempDir
	private File tempFolder;
	private File lyrebirdExecutable;

	@BeforeEach
	public void setup() throws IOException {
		lyrebirdExecutable = new File(tempFolder, "lyrebirdExecutable");
		extractLyrebirdExecutable();
	}

	@Test
	public void testCnFastly() throws Exception {
		testCn(FASTLY_URL, FASTLY_FRONTS);
	}

	@Test
	public void testCnAzure() throws Exception {
		testCn(AZURE_URL, AZURE_FRONTS);
	}

	@Test
	public void testCnCdn77() throws Exception {
		testCn(CDN77_URL, CDN77_FRONTS);
	}

	private void testCn(String url, String[] fronts) throws Exception {
		for (String front : fronts) {
			MoatApi moatApi = new MoatApi(lyrebirdExecutable, tempFolder, url, front);
			List<Bridges> bridges = moatApi.getWithCountry("cn");
			boolean anyObfs4 = false, anySnowflake = false;
			for (Bridges b : bridges) {
				if (b.type.equals("obfs4")) anyObfs4 = true;
				else if (b.type.equals("snowflake")) anySnowflake = true;
			}
			assertTrue(anyObfs4);
			assertTrue(anySnowflake);
		}
	}

	@Test
	public void testUsFastly() throws Exception {
		testUs(FASTLY_URL, FASTLY_FRONTS);
	}

	@Test
	public void testUsAzure() throws Exception {
		testUs(AZURE_URL, AZURE_FRONTS);
	}

	@Test
	public void testUsCdn77() throws Exception {
		testUs(CDN77_URL, CDN77_FRONTS);
	}

	private void testUs(String url, String[] fronts) throws Exception {
		for (String front : fronts) {
			MoatApi moatApi = new MoatApi(lyrebirdExecutable, tempFolder, url, front);
			assertEquals(emptyList(), moatApi.getWithCountry("us"));
		}
	}

	private void extractLyrebirdExecutable() throws IOException {
		OutputStream out = new FileOutputStream(lyrebirdExecutable);
		InputStream in = getResourceInputStream("x86_64/lyrebird");
		byte[] buf = new byte[4096];
		while (true) {
			int read = in.read(buf);
			if (read == -1) break;
			out.write(buf, 0, read);
		}
		in.close();
		out.flush();
		out.close();
		if (!lyrebirdExecutable.setExecutable(true, true)) throw new IOException();
	}

	@SuppressWarnings("SameParameterValue")
	private InputStream getResourceInputStream(String name) {
		InputStream in = getClass().getClassLoader().getResourceAsStream(name);
		if (in == null) fail();
		return in;
	}
}
