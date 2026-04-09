package fm.corus.android.service;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class RemoteConfigService_Factory implements Factory<RemoteConfigService> {
  private final Provider<FirebaseRemoteConfig> remoteConfigProvider;

  public RemoteConfigService_Factory(Provider<FirebaseRemoteConfig> remoteConfigProvider) {
    this.remoteConfigProvider = remoteConfigProvider;
  }

  @Override
  public RemoteConfigService get() {
    return newInstance(remoteConfigProvider.get());
  }

  public static RemoteConfigService_Factory create(
      Provider<FirebaseRemoteConfig> remoteConfigProvider) {
    return new RemoteConfigService_Factory(remoteConfigProvider);
  }

  public static RemoteConfigService newInstance(FirebaseRemoteConfig remoteConfig) {
    return new RemoteConfigService(remoteConfig);
  }
}
