package fm.corus.android.ui.screens.findpeople;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class SuggestedUsersListViewModel_Factory implements Factory<SuggestedUsersListViewModel> {
  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  public SuggestedUsersListViewModel_Factory(Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
  }

  @Override
  public SuggestedUsersListViewModel get() {
    return newInstance(userRepositoryProvider.get(), authRepositoryProvider.get());
  }

  public static SuggestedUsersListViewModel_Factory create(
      Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    return new SuggestedUsersListViewModel_Factory(userRepositoryProvider, authRepositoryProvider);
  }

  public static SuggestedUsersListViewModel newInstance(UserRepository userRepository,
      AuthRepository authRepository) {
    return new SuggestedUsersListViewModel(userRepository, authRepository);
  }
}
