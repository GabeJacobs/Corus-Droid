package fm.corus.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.FirestoreDataSource;
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
public final class ExploreRepository_Factory implements Factory<ExploreRepository> {
  private final Provider<FirestoreDataSource> firestoreDataSourceProvider;

  public ExploreRepository_Factory(Provider<FirestoreDataSource> firestoreDataSourceProvider) {
    this.firestoreDataSourceProvider = firestoreDataSourceProvider;
  }

  @Override
  public ExploreRepository get() {
    return newInstance(firestoreDataSourceProvider.get());
  }

  public static ExploreRepository_Factory create(
      Provider<FirestoreDataSource> firestoreDataSourceProvider) {
    return new ExploreRepository_Factory(firestoreDataSourceProvider);
  }

  public static ExploreRepository newInstance(FirestoreDataSource firestoreDataSource) {
    return new ExploreRepository(firestoreDataSource);
  }
}
