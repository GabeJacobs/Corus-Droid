package fm.corus.android.ui.screens.compose;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.PostRepository;
import fm.corus.android.data.repository.SpotifyRepository;
import fm.corus.android.data.repository.SubscriptionRepository;
import fm.corus.android.data.repository.TMDBRepository;
import fm.corus.android.data.repository.UserRepository;
import fm.corus.android.service.AnalyticsService;
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
public final class ComposeViewModel_Factory implements Factory<ComposeViewModel> {
  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<SpotifyRepository> spotifyRepositoryProvider;

  private final Provider<TMDBRepository> tmdbRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<AnalyticsService> analyticsServiceProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<SubscriptionRepository> subscriptionRepositoryProvider;

  public ComposeViewModel_Factory(Provider<PostRepository> postRepositoryProvider,
      Provider<SpotifyRepository> spotifyRepositoryProvider,
      Provider<TMDBRepository> tmdbRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<AnalyticsService> analyticsServiceProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
    this.spotifyRepositoryProvider = spotifyRepositoryProvider;
    this.tmdbRepositoryProvider = tmdbRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.analyticsServiceProvider = analyticsServiceProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.subscriptionRepositoryProvider = subscriptionRepositoryProvider;
  }

  @Override
  public ComposeViewModel get() {
    return newInstance(postRepositoryProvider.get(), spotifyRepositoryProvider.get(), tmdbRepositoryProvider.get(), authRepositoryProvider.get(), analyticsServiceProvider.get(), userRepositoryProvider.get(), subscriptionRepositoryProvider.get());
  }

  public static ComposeViewModel_Factory create(Provider<PostRepository> postRepositoryProvider,
      Provider<SpotifyRepository> spotifyRepositoryProvider,
      Provider<TMDBRepository> tmdbRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<AnalyticsService> analyticsServiceProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider) {
    return new ComposeViewModel_Factory(postRepositoryProvider, spotifyRepositoryProvider, tmdbRepositoryProvider, authRepositoryProvider, analyticsServiceProvider, userRepositoryProvider, subscriptionRepositoryProvider);
  }

  public static ComposeViewModel newInstance(PostRepository postRepository,
      SpotifyRepository spotifyRepository, TMDBRepository tmdbRepository,
      AuthRepository authRepository, AnalyticsService analyticsService,
      UserRepository userRepository, SubscriptionRepository subscriptionRepository) {
    return new ComposeViewModel(postRepository, spotifyRepository, tmdbRepository, authRepository, analyticsService, userRepository, subscriptionRepository);
  }
}
