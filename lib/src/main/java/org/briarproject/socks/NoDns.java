package org.briarproject.socks;

import org.briarproject.moat.NotNullByDefault;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import okhttp3.Dns;

import static java.net.InetAddress.getByAddress;
import static java.util.Collections.singletonList;

@NotNullByDefault
public class NoDns implements Dns {

	private static final byte[] UNSPECIFIED_ADDRESS = new byte[4];

	public NoDns() {
	}

	@Override
	public List<InetAddress> lookup(String hostname) throws UnknownHostException {
		return singletonList(getByAddress(hostname, UNSPECIFIED_ADDRESS));
	}
}
