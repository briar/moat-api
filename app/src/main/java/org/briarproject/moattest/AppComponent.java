package org.briarproject.moattest;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		AppModule.class,
		MainActivityModule.class
})
public interface AppComponent {

	public void inject(MainActivity activity);
}
