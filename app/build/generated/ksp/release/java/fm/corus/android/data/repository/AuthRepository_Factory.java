package fm.corus.android.data.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
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
public final class AuthRepository_Factory implements Factory<AuthRepository> {
  private final Provider<FirebaseAuth> authProvider;

  private final Provider<FirestoreDataSource> firestoreDataSourceProvider;

  private final Provider<FirebaseStorageDataSource> storageDataSourceProvider;

  private final Provider<FirebaseMessaging> messagingProvider;

  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  public AuthRepository_Factory(Provider<FirebaseAuth> authProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider,
      Provider<FirebaseStorageDataSource> storageDataSourceProvider,
      Provider<FirebaseMessaging> messagingProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    this.authProvider = authProvider;
    this.firestoreDataSourceProvider = firestoreDataSourceProvider;
    this.storageDataSourceProvider = storageDataSourceProvider;
    this.messagingProvider = messagingProvider;
    this.cloudFunctionsProvider = cloudFunctionsProvider;
  }

  @Override
  public AuthRepository get() {
    return newInstance(authProvider.get(), firestoreDataSourceProvider.get(), storageDataSourceProvider.get(), messagingProvider.get(), cloudFunctionsProvider.get());
  }

  public static AuthRepository_Factory create(Provider<FirebaseAuth> authProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider,
      Provider<FirebaseStorageDataSource> storageDataSourceProvider,
      Provider<FirebaseMessaging> messagingProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    return new AuthRepository_Factory(authProvider, firestoreDataSourceProvider, storageDataSourceProvider, messagingProvider, cloudFunctionsProvider);
  }

  public static AuthRepository newInstance(FirebaseAuth auth,
      FirestoreDataSource firestoreDataSource, FirebaseStorageDataSource storageDataSource,
      FirebaseMessaging messaging, CloudFunctionsDataSource cloudFunctions) {
    return new AuthRepository(auth, firestoreDataSource, storageDataSource, messaging, cloudFunctions);
  }
}
