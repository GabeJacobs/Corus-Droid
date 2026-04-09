package fm.corus.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
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
public final class SpotifyRepository_Factory implements Factory<SpotifyRepository> {
  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  public SpotifyRepository_Factory(Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    this.cloudFunctionsProvider = cloudFunctionsProvider;
  }

  @Override
  public SpotifyRepository get() {
    return newInstance(cloudFunctionsProvider.get());
  }

  public static SpotifyRepository_Factory create(
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    return new SpotifyRepository_Factory(cloudFunctionsProvider);
  }

  public static SpotifyRepository newInstance(CloudFunctionsDataSource cloudFunctions) {
    return new SpotifyRepository(cloudFunctions);
  }
}
