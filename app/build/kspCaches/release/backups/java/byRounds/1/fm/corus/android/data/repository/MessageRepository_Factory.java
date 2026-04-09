package fm.corus.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
import fm.corus.android.data.remote.FirebaseStorageDataSource;
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
public final class MessageRepository_Factory implements Factory<MessageRepository> {
  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<FirebaseStorageDataSource> storageDataSourceProvider;

  public MessageRepository_Factory(Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<FirebaseStorageDataSource> storageDataSourceProvider) {
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.storageDataSourceProvider = storageDataSourceProvider;
  }

  @Override
  public MessageRepository get() {
    return newInstance(cloudFunctionsProvider.get(), storageDataSourceProvider.get());
  }

  public static MessageRepository_Factory create(
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<FirebaseStorageDataSource> storageDataSourceProvider) {
    return new MessageRepository_Factory(cloudFunctionsProvider, storageDataSourceProvider);
  }

  public static MessageRepository newInstance(CloudFunctionsDataSource cloudFunctions,
      FirebaseStorageDataSource storageDataSource) {
    return new MessageRepository(cloudFunctions, storageDataSource);
  }
}
