package fm.corus.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.TMDBApiService;
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
public final class TMDBRepository_Factory implements Factory<TMDBRepository> {
  private final Provider<TMDBApiService> tmdbApiProvider;

  public TMDBRepository_Factory(Provider<TMDBApiService> tmdbApiProvider) {
    this.tmdbApiProvider = tmdbApiProvider;
  }

  @Override
  public TMDBRepository get() {
    return newInstance(tmdbApiProvider.get());
  }

  public static TMDBRepository_Factory create(Provider<TMDBApiService> tmdbApiProvider) {
    return new TMDBRepository_Factory(tmdbApiProvider);
  }

  public static TMDBRepository newInstance(TMDBApiService tmdbApi) {
    return new TMDBRepository(tmdbApi);
  }
}
