package org.briarproject.moat;

import org.briarproject.nullsafety.NotNullByDefault;

import java.util.List;

@NotNullByDefault
public class Bridges {

	public final String type, source;
	public final List<String> bridgeStrings;

	public Bridges(String type, String source, List<String> bridgeStrings) {
		this.type = type;
		this.source = source;
		this.bridgeStrings = bridgeStrings;
	}
}
