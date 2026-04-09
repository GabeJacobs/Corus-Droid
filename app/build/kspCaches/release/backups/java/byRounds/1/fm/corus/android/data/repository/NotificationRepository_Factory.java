package fm.corus.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
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
public final class NotificationRepository_Factory implements Factory<NotificationRepository> {
  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<FirestoreDataSource> firestoreDataSourceProvider;

  public NotificationRepository_Factory(Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider) {
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.firestoreDataSourceProvider = firestoreDataSourceProvider;
  }

  @Override
  public NotificationRepository get() {
    return newInstance(cloudFunctionsProvider.get(), firestoreDataSourceProvider.get());
  }

  public static NotificationRepository_Factory create(
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider) {
    return new NotificationRepository_Factory(cloudFunctionsProvider, firestoreDataSourceProvider);
  }

  public static NotificationRepository newInstance(CloudFunctionsDataSource cloudFunctions,
      FirestoreDataSource firestoreDataSource) {
    return new NotificationRepository(cloudFunctions, firestoreDataSource);
  }
}
