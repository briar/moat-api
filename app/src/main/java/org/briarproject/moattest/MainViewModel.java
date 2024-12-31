package org.briarproject.moattest;

import android.app.Application;

import org.briarproject.moat.Bridges;
import org.briarproject.moat.MoatApi;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import dagger.hilt.android.lifecycle.HiltViewModel;

import static android.content.Context.MODE_PRIVATE;
import static java.util.Locale.ROOT;

@HiltViewModel
class MainViewModel extends AndroidViewModel {

	private static final String LYREBIRD_LIB_NAME = "liblyrebird.so";
	private static final String STATE_DIR_NAME = "state";

	private static final String FASTLY_URL = "https://moat.torproject.org.global.prod.fastly.net/";
	private static final String FASTLY_FRONT = "cdn.yelp.com";
	private static final String AZURE_URL = "https://onion.azureedge.net/";
	private static final String AZURE_FRONT = "ajax.aspnetcdn.com";

	private final ExecutorService backgroundExecutor;
	private final MutableLiveData<String> response = new MutableLiveData<>();

	@Inject
	public MainViewModel(@NonNull Application application, ExecutorService backgroundExecutor) {
		super(application);
		this.backgroundExecutor = backgroundExecutor;
	}

	@UiThread
	LiveData<String> getResponse() {
		return response;
	}

	@UiThread
	void sendRequest(String countryCode, boolean azure) {
		backgroundExecutor.execute(() -> sendRequestInBackground(countryCode, azure));
	}

	@WorkerThread
	private void sendRequestInBackground(String countryCode, boolean azure) {
		countryCode = countryCode.toLowerCase(ROOT);
		Application app = getApplication();
		String nativeLibDir = app.getApplicationInfo().nativeLibraryDir;
		File lyrebirdLib = new File(nativeLibDir, LYREBIRD_LIB_NAME);
		File stateDir = app.getDir(STATE_DIR_NAME, MODE_PRIVATE);
		String url = azure ? AZURE_URL : FASTLY_URL;
		String front = azure ? AZURE_FRONT : FASTLY_FRONT;
		MoatApi moat = new MoatApi(lyrebirdLib, stateDir, url, front);
		try {
			List<Bridges> bridges = moat.getWithCountry(countryCode);
			StringBuilder sb = new StringBuilder();
			for (Bridges b : bridges) {
				if (sb.length() > 0) sb.append('\n');
				sb.append(app.getString(R.string.bridge_source, b.source)).append('\n');
				sb.append(app.getString(R.string.bridge_type, b.type)).append('\n');
				for (String s : b.bridgeStrings) {
					sb.append(app.getString(R.string.bridge_line, s)).append('\n');
				}
			}
			response.postValue(sb.toString());
		} catch (IOException e) {
			StringBuilder sb = new StringBuilder();
			appendThrowable(sb, e);
			response.postValue(sb.toString());
		}
	}

	private void appendThrowable(StringBuilder sb, Throwable t) {
		sb.append(t);
		for (StackTraceElement e : t.getStackTrace())
			sb.append("\n        at ").append(e);
		Throwable cause = t.getCause();
		if (cause != null) {
			sb.append("\n     Caused by: ");
			appendThrowable(sb, cause);
		}
	}
}
