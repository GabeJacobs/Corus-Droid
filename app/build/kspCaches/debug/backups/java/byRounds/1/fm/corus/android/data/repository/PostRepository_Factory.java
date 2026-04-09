package fm.corus.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class PostRepository_Factory implements Factory<PostRepository> {
  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<FirestoreDataSource> firestoreDataSourceProvider;

  private final Provider<FirebaseStorageDataSource> storageDataSourceProvider;

  public PostRepository_Factory(Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider,
      Provider<FirebaseStorageDataSource> storageDataSourceProvider) {
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.firestoreDataSourceProvider = firestoreDataSourceProvider;
    this.storageDataSourceProvider = storageDataSourceProvider;
  }

  @Override
  public PostRepository get() {
    return newInstance(cloudFunctionsProvider.get(), firestoreDataSourceProvider.get(), storageDataSourceProvider.get());
  }

  public static PostRepository_Factory create(
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider,
      Provider<FirebaseStorageDataSource> storageDataSourceProvider) {
    return new PostRepository_Factory(cloudFunctionsProvider, firestoreDataSourceProvider, storageDataSourceProvider);
  }

  public static PostRepository newInstance(CloudFunctionsDataSource cloudFunctions,
      FirestoreDataSource firestoreDataSource, FirebaseStorageDataSource storageDataSource) {
    return new PostRepository(cloudFunctions, firestoreDataSource, storageDataSource);
  }
}
