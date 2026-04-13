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
public final class GifRepository_Factory implements Factory<GifRepository> {
  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  public GifRepository_Factory(Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    this.cloudFunctionsProvider = cloudFunctionsProvider;
  }

  @Override
  public GifRepository get() {
    return newInstance(cloudFunctionsProvider.get());
  }

  public static GifRepository_Factory create(
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    return new GifRepository_Factory(cloudFunctionsProvider);
  }

  public static GifRepository newInstance(CloudFunctionsDataSource cloudFunctions) {
    return new GifRepository(cloudFunctions);
  }
}
