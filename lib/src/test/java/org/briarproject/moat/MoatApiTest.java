package org.briarproject.moat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class MoatApiTest {

	private static final String CDN77_URL = "https://1723079976.rsc.cdn77.org/";
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
	public void testCnCdn77() throws Exception {
		testCn(CDN77_URL, CDN77_FRONTS);
	}

	@SuppressWarnings("SameParameterValue")
	private void testCn(String url, String[] fronts) throws Exception {
		for (String front : fronts) {
			for (boolean isrg : new boolean[]{true, false}) {
				MoatApi moatApi = new MoatApi(lyrebirdExecutable, tempFolder, url, front, isrg);
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
	}

	@Test
	public void testUsCdn77() throws Exception {
		testUs(CDN77_URL, CDN77_FRONTS);
	}

	@SuppressWarnings("SameParameterValue")
	private void testUs(String url, String[] fronts) throws Exception {
		for (String front : fronts) {
			for (boolean isrg : new boolean[]{true, false}) {
				MoatApi moatApi = new MoatApi(lyrebirdExecutable, tempFolder, url, front, isrg);
				assertEquals(emptyList(), moatApi.getWithCountry("us"));
			}
		}
	}

	private void extractLyrebirdExecutable() throws IOException {
		OutputStream out = Files.newOutputStream(lyrebirdExecutable.toPath());
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
