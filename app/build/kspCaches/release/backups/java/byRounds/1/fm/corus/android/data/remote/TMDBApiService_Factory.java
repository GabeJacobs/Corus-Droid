package fm.corus.android.data.remote;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
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
public final class TMDBApiService_Factory implements Factory<TMDBApiService> {
  private final Provider<HttpClient> clientProvider;

  public TMDBApiService_Factory(Provider<HttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public TMDBApiService get() {
    return newInstance(clientProvider.get());
  }

  public static TMDBApiService_Factory create(Provider<HttpClient> clientProvider) {
    return new TMDBApiService_Factory(clientProvider);
  }

  public static TMDBApiService newInstance(HttpClient client) {
    return new TMDBApiService(client);
  }
}
