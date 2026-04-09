package fm.corus.android.domain;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
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
public final class NowPlayingManager_Factory implements Factory<NowPlayingManager> {
  private final Provider<Context> contextProvider;

  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  public NowPlayingManager_Factory(Provider<Context> contextProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    this.contextProvider = contextProvider;
    this.cloudFunctionsProvider = cloudFunctionsProvider;
  }

  @Override
  public NowPlayingManager get() {
    return newInstance(contextProvider.get(), cloudFunctionsProvider.get());
  }

  public static NowPlayingManager_Factory create(Provider<Context> contextProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    return new NowPlayingManager_Factory(contextProvider, cloudFunctionsProvider);
  }

  public static NowPlayingManager newInstance(Context context,
      CloudFunctionsDataSource cloudFunctions) {
    return new NowPlayingManager(context, cloudFunctions);
  }
}
