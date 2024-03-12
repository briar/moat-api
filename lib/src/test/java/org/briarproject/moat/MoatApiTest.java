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
import java.util.zip.ZipInputStream;

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

	@TempDir
	private File tempFolder;
	private File obfs4Executable;

	@BeforeEach
	public void setup() throws IOException {
		obfs4Executable = new File(tempFolder, "obfs4Executable");
		extractObfs4Executable();
	}

	@Test
	public void testCnFastly() throws Exception {
		testCn(FASTLY_URL, FASTLY_FRONTS);
	}

	@Test
	public void testCnAzure() throws Exception {
		testCn(AZURE_URL, AZURE_FRONTS);
	}

	private void testCn(String url, String[] fronts) throws Exception {
		for (String front : fronts) {
			MoatApi moatApi = new MoatApi(obfs4Executable, tempFolder, url, front);
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

	private void testUs(String url, String[] fronts) throws Exception {
		for (String front : fronts) {
			MoatApi moatApi = new MoatApi(obfs4Executable, tempFolder, url, front);
			assertEquals(emptyList(), moatApi.getWithCountry("us"));
		}
	}

	private void extractObfs4Executable() throws IOException {
		OutputStream out = new FileOutputStream(obfs4Executable);
		InputStream in = getInputStream();
		byte[] buf = new byte[4096];
		while (true) {
			int read = in.read(buf);
			if (read == -1) break;
			out.write(buf, 0, read);
		}
		in.close();
		out.flush();
		out.close();
		if (!obfs4Executable.setExecutable(true, true)) throw new IOException();
	}

	private InputStream getInputStream() throws IOException {
		InputStream in = getResourceInputStream("obfs4proxy_linux-x86_64.zip");
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	@SuppressWarnings("SameParameterValue")
	private InputStream getResourceInputStream(String name) {
		InputStream in = getClass().getClassLoader().getResourceAsStream(name);
		if (in == null) fail();
		return in;
	}
}
