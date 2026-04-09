package fm.corus.android.ui.screens.settings;

import com.google.firebase.auth.FirebaseAuth;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class ChangePhoneNumberViewModel_Factory implements Factory<ChangePhoneNumberViewModel> {
  private final Provider<FirebaseAuth> authProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  public ChangePhoneNumberViewModel_Factory(Provider<FirebaseAuth> authProvider,
      Provider<UserRepository> userRepositoryProvider) {
    this.authProvider = authProvider;
    this.userRepositoryProvider = userRepositoryProvider;
  }

  @Override
  public ChangePhoneNumberViewModel get() {
    return newInstance(authProvider.get(), userRepositoryProvider.get());
  }

  public static ChangePhoneNumberViewModel_Factory create(Provider<FirebaseAuth> authProvider,
      Provider<UserRepository> userRepositoryProvider) {
    return new ChangePhoneNumberViewModel_Factory(authProvider, userRepositoryProvider);
  }

  public static ChangePhoneNumberViewModel newInstance(FirebaseAuth auth,
      UserRepository userRepository) {
    return new ChangePhoneNumberViewModel(auth, userRepository);
  }
}
