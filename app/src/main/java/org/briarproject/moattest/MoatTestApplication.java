package org.briarproject.moattest;

import android.app.Application;

import org.conscrypt.Conscrypt;

import java.security.Security;

public class MoatTestApplication extends Application {

	private AppComponent appComponent;

	@Override
	public void onCreate() {
		super.onCreate();
		Security.insertProviderAt(Conscrypt.newProvider(), 1);
		appComponent = DaggerAppComponent.builder().appModule(new AppModule(this)).build();
	}

	public AppComponent getAppComponent() {
		return appComponent;
	}
}
