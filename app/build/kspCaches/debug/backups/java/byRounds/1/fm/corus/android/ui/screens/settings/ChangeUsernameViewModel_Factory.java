package fm.corus.android.ui.screens.settings;

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
public final class ChangeUsernameViewModel_Factory implements Factory<ChangeUsernameViewModel> {
  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  public ChangeUsernameViewModel_Factory(Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    this.authRepositoryProvider = authRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
  }

  @Override
  public ChangeUsernameViewModel get() {
    return newInstance(authRepositoryProvider.get(), userRepositoryProvider.get());
  }

  public static ChangeUsernameViewModel_Factory create(
      Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    return new ChangeUsernameViewModel_Factory(authRepositoryProvider, userRepositoryProvider);
  }

  public static ChangeUsernameViewModel newInstance(AuthRepository authRepository,
      UserRepository userRepository) {
    return new ChangeUsernameViewModel(authRepository, userRepository);
  }
}
