package fm.corus.android.ui.screens.auth;

import com.google.firebase.auth.FirebaseAuth;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.UserRepository;
import fm.corus.android.service.AnalyticsService;
import fm.corus.android.service.RemoteConfigService;
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
public final class AuthViewModel_Factory implements Factory<AuthViewModel> {
  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<RemoteConfigService> remoteConfigServiceProvider;

  private final Provider<AnalyticsService> analyticsServiceProvider;

  private final Provider<FirebaseAuth> firebaseAuthProvider;

  public AuthViewModel_Factory(Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<RemoteConfigService> remoteConfigServiceProvider,
      Provider<AnalyticsService> analyticsServiceProvider,
      Provider<FirebaseAuth> firebaseAuthProvider) {
    this.authRepositoryProvider = authRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.remoteConfigServiceProvider = remoteConfigServiceProvider;
    this.analyticsServiceProvider = analyticsServiceProvider;
    this.firebaseAuthProvider = firebaseAuthProvider;
  }

  @Override
  public AuthViewModel get() {
    return newInstance(authRepositoryProvider.get(), userRepositoryProvider.get(), remoteConfigServiceProvider.get(), analyticsServiceProvider.get(), firebaseAuthProvider.get());
  }

  public static AuthViewModel_Factory create(Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<RemoteConfigService> remoteConfigServiceProvider,
      Provider<AnalyticsService> analyticsServiceProvider,
      Provider<FirebaseAuth> firebaseAuthProvider) {
    return new AuthViewModel_Factory(authRepositoryProvider, userRepositoryProvider, remoteConfigServiceProvider, analyticsServiceProvider, firebaseAuthProvider);
  }

  public static AuthViewModel newInstance(AuthRepository authRepository,
      UserRepository userRepository, RemoteConfigService remoteConfigService,
      AnalyticsService analyticsService, FirebaseAuth firebaseAuth) {
    return new AuthViewModel(authRepository, userRepository, remoteConfigService, analyticsService, firebaseAuth);
  }
}
