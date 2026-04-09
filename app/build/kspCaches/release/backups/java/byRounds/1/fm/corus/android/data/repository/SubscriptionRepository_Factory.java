package fm.corus.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
import fm.corus.android.service.RemoteConfigService;
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
public final class SubscriptionRepository_Factory implements Factory<SubscriptionRepository> {
  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<RemoteConfigService> remoteConfigProvider;

  public SubscriptionRepository_Factory(Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<RemoteConfigService> remoteConfigProvider) {
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.remoteConfigProvider = remoteConfigProvider;
  }

  @Override
  public SubscriptionRepository get() {
    return newInstance(cloudFunctionsProvider.get(), remoteConfigProvider.get());
  }

  public static SubscriptionRepository_Factory create(
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<RemoteConfigService> remoteConfigProvider) {
    return new SubscriptionRepository_Factory(cloudFunctionsProvider, remoteConfigProvider);
  }

  public static SubscriptionRepository newInstance(CloudFunctionsDataSource cloudFunctions,
      RemoteConfigService remoteConfig) {
    return new SubscriptionRepository(cloudFunctions, remoteConfig);
  }
}
