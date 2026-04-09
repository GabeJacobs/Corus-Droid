package fm.corus.android.service;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class ReviewPromptManager_Factory implements Factory<ReviewPromptManager> {
  private final Provider<Context> contextProvider;

  private final Provider<RemoteConfigService> remoteConfigServiceProvider;

  public ReviewPromptManager_Factory(Provider<Context> contextProvider,
      Provider<RemoteConfigService> remoteConfigServiceProvider) {
    this.contextProvider = contextProvider;
    this.remoteConfigServiceProvider = remoteConfigServiceProvider;
  }

  @Override
  public ReviewPromptManager get() {
    return newInstance(contextProvider.get(), remoteConfigServiceProvider.get());
  }

  public static ReviewPromptManager_Factory create(Provider<Context> contextProvider,
      Provider<RemoteConfigService> remoteConfigServiceProvider) {
    return new ReviewPromptManager_Factory(contextProvider, remoteConfigServiceProvider);
  }

  public static ReviewPromptManager newInstance(Context context,
      RemoteConfigService remoteConfigService) {
    return new ReviewPromptManager(context, remoteConfigService);
  }
}
