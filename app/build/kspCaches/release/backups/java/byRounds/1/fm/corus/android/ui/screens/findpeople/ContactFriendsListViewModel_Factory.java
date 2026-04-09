package fm.corus.android.ui.screens.findpeople;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.FirestoreDataSource;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.UserRepository;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ContactFriendsListViewModel_Factory implements Factory<ContactFriendsListViewModel> {
  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<FirestoreDataSource> firestoreDataSourceProvider;

  public ContactFriendsListViewModel_Factory(Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.firestoreDataSourceProvider = firestoreDataSourceProvider;
  }

  @Override
  public ContactFriendsListViewModel get() {
    return newInstance(userRepositoryProvider.get(), authRepositoryProvider.get(), firestoreDataSourceProvider.get());
  }

  public static ContactFriendsListViewModel_Factory create(
      Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider) {
    return new ContactFriendsListViewModel_Factory(userRepositoryProvider, authRepositoryProvider, firestoreDataSourceProvider);
  }

  public static ContactFriendsListViewModel newInstance(UserRepository userRepository,
      AuthRepository authRepository, FirestoreDataSource firestoreDataSource) {
    return new ContactFriendsListViewModel(userRepository, authRepository, firestoreDataSource);
  }
}
