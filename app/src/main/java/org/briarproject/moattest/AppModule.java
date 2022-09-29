package org.briarproject.moattest;

import android.app.Application;

import java.util.concurrent.ExecutorService;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.concurrent.Executors.newCachedThreadPool;

@Module
public class AppModule {

	private final Application app;

	public AppModule(Application app) {
		this.app = app;
	}

	@Provides
	Application provideApplication() {
		return app;
	}

	@Provides
	@Singleton
	ExecutorService provideBackgroundExecutor() {
		return newCachedThreadPool();
	}
}
