package fm.corus.android.ui.screens.explore;

import com.google.firebase.auth.FirebaseAuth;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.ExploreRepository;
import fm.corus.android.data.repository.SpotifyRepository;
import fm.corus.android.data.repository.TMDBRepository;
import fm.corus.android.data.repository.UserRepository;
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
public final class ExploreViewModel_Factory implements Factory<ExploreViewModel> {
  private final Provider<ExploreRepository> exploreRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<SpotifyRepository> spotifyRepositoryProvider;

  private final Provider<TMDBRepository> tmdbRepositoryProvider;

  private final Provider<RemoteConfigService> remoteConfigServiceProvider;

  private final Provider<FirebaseAuth> firebaseAuthProvider;

  public ExploreViewModel_Factory(Provider<ExploreRepository> exploreRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<SpotifyRepository> spotifyRepositoryProvider,
      Provider<TMDBRepository> tmdbRepositoryProvider,
      Provider<RemoteConfigService> remoteConfigServiceProvider,
      Provider<FirebaseAuth> firebaseAuthProvider) {
    this.exploreRepositoryProvider = exploreRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.spotifyRepositoryProvider = spotifyRepositoryProvider;
    this.tmdbRepositoryProvider = tmdbRepositoryProvider;
    this.remoteConfigServiceProvider = remoteConfigServiceProvider;
    this.firebaseAuthProvider = firebaseAuthProvider;
  }

  @Override
  public ExploreViewModel get() {
    return newInstance(exploreRepositoryProvider.get(), userRepositoryProvider.get(), spotifyRepositoryProvider.get(), tmdbRepositoryProvider.get(), remoteConfigServiceProvider.get(), firebaseAuthProvider.get());
  }

  public static ExploreViewModel_Factory create(
      Provider<ExploreRepository> exploreRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<SpotifyRepository> spotifyRepositoryProvider,
      Provider<TMDBRepository> tmdbRepositoryProvider,
      Provider<RemoteConfigService> remoteConfigServiceProvider,
      Provider<FirebaseAuth> firebaseAuthProvider) {
    return new ExploreViewModel_Factory(exploreRepositoryProvider, userRepositoryProvider, spotifyRepositoryProvider, tmdbRepositoryProvider, remoteConfigServiceProvider, firebaseAuthProvider);
  }

  public static ExploreViewModel newInstance(ExploreRepository exploreRepository,
      UserRepository userRepository, SpotifyRepository spotifyRepository,
      TMDBRepository tmdbRepository, RemoteConfigService remoteConfigService,
      FirebaseAuth firebaseAuth) {
    return new ExploreViewModel(exploreRepository, userRepository, spotifyRepository, tmdbRepository, remoteConfigService, firebaseAuth);
  }
}
