package fm.corus.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.local.PreferencesDataStore;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
import fm.corus.android.data.remote.FirebaseStorageDataSource;
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
public final class UserRepository_Factory implements Factory<UserRepository> {
  private final Provider<FirestoreDataSource> firestoreDataSourceProvider;

  private final Provider<FirebaseStorageDataSource> storageDataSourceProvider;

  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<PreferencesDataStore> preferencesDataStoreProvider;

  public UserRepository_Factory(Provider<FirestoreDataSource> firestoreDataSourceProvider,
      Provider<FirebaseStorageDataSource> storageDataSourceProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<PreferencesDataStore> preferencesDataStoreProvider) {
    this.firestoreDataSourceProvider = firestoreDataSourceProvider;
    this.storageDataSourceProvider = storageDataSourceProvider;
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.preferencesDataStoreProvider = preferencesDataStoreProvider;
  }

  @Override
  public UserRepository get() {
    return newInstance(firestoreDataSourceProvider.get(), storageDataSourceProvider.get(), cloudFunctionsProvider.get(), preferencesDataStoreProvider.get());
  }

  public static UserRepository_Factory create(
      Provider<FirestoreDataSource> firestoreDataSourceProvider,
      Provider<FirebaseStorageDataSource> storageDataSourceProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<PreferencesDataStore> preferencesDataStoreProvider) {
    return new UserRepository_Factory(firestoreDataSourceProvider, storageDataSourceProvider, cloudFunctionsProvider, preferencesDataStoreProvider);
  }

  public static UserRepository newInstance(FirestoreDataSource firestoreDataSource,
      FirebaseStorageDataSource storageDataSource, CloudFunctionsDataSource cloudFunctions,
      PreferencesDataStore preferencesDataStore) {
    return new UserRepository(firestoreDataSource, storageDataSource, cloudFunctions, preferencesDataStore);
  }
}
