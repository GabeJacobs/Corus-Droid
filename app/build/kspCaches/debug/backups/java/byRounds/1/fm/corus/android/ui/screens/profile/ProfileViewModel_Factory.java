package fm.corus.android.ui.screens.profile;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.SubscriptionRepository;
import fm.corus.android.data.repository.UserRepository;
import fm.corus.android.domain.NowPlayingManager;
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
public final class ProfileViewModel_Factory implements Factory<ProfileViewModel> {
  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<SubscriptionRepository> subscriptionRepositoryProvider;

  private final Provider<NowPlayingManager> nowPlayingManagerProvider;

  public ProfileViewModel_Factory(Provider<AuthRepository> authRepositoryProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider) {
    this.authRepositoryProvider = authRepositoryProvider;
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.subscriptionRepositoryProvider = subscriptionRepositoryProvider;
    this.nowPlayingManagerProvider = nowPlayingManagerProvider;
  }

  @Override
  public ProfileViewModel get() {
    return newInstance(authRepositoryProvider.get(), cloudFunctionsProvider.get(), userRepositoryProvider.get(), subscriptionRepositoryProvider.get(), nowPlayingManagerProvider.get());
  }

  public static ProfileViewModel_Factory create(Provider<AuthRepository> authRepositoryProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider) {
    return new ProfileViewModel_Factory(authRepositoryProvider, cloudFunctionsProvider, userRepositoryProvider, subscriptionRepositoryProvider, nowPlayingManagerProvider);
  }

  public static ProfileViewModel newInstance(AuthRepository authRepository,
      CloudFunctionsDataSource cloudFunctions, UserRepository userRepository,
      SubscriptionRepository subscriptionRepository, NowPlayingManager nowPlayingManager) {
    return new ProfileViewModel(authRepository, cloudFunctions, userRepository, subscriptionRepository, nowPlayingManager);
  }
}
