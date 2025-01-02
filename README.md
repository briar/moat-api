# Tor Moat Api Wrapper

A tiny wrapper around Tor Project's [Moat Circumvention Settings API](https://gitlab.torproject.org/tpo/anti-censorship/rdsys/-/blob/main/doc/moat.md)

# How to use

Add this to your build.gradle file:

    implementation 'org.briarproject:moat-api:0.2'

You also need to add [OkHttp](https://square.github.io/okhttp/) as a dependency. The current stable version of OkHttp (4.10) is compatible with Android 5 (API 21) or later:

    implementation 'com.squareup.okhttp3:okhttp:4.10.0'

If you need to support Android 4, you can use an obsolete version of OkHttp (3.12) instead:

    implementation 'com.squareup.okhttp3:okhttp:3.12.13'

On Android versions earlier than 7.1 (API 25), you may need to use [Conscrypt](https://github.com/google/conscrypt/) for TLS connections to Fastly:

    implementation 'org.conscrypt:conscrypt-android:2.5.2'

Finally, you must also provide a suitable meek implementation, such as [lyrebird](https://gitweb.torproject.org/pluggable-transports/lyrebird.git/tree/README.md), for your platform.
