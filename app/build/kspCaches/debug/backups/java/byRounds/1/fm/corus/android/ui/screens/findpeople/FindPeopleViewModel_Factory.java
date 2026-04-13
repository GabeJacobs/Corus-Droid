package fm.corus.android.ui.screens.findpeople;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.local.PreferencesDataStore;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
import fm.corus.android.data.remote.FirestoreDataSource;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.ExploreRepository;
import fm.corus.android.data.repository.SpotifyRepository;
import fm.corus.android.data.repository.TMDBRepository;
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
public final class FindPeopleViewModel_Factory implements Factory<FindPeopleViewModel> {
  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<ExploreRepository> exploreRepositoryProvider;

  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<SpotifyRepository> spotifyRepositoryProvider;

  private final Provider<TMDBRepository> tmdbRepositoryProvider;

  private final Provider<PreferencesDataStore> preferencesDataStoreProvider;

  private final Provider<FirestoreDataSource> firestoreDataSourceProvider;

  public FindPeopleViewModel_Factory(Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<ExploreRepository> exploreRepositoryProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<SpotifyRepository> spotifyRepositoryProvider,
      Provider<TMDBRepository> tmdbRepositoryProvider,
      Provider<PreferencesDataStore> preferencesDataStoreProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.exploreRepositoryProvider = exploreRepositoryProvider;
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.spotifyRepositoryProvider = spotifyRepositoryProvider;
    this.tmdbRepositoryProvider = tmdbRepositoryProvider;
    this.preferencesDataStoreProvider = preferencesDataStoreProvider;
    this.firestoreDataSourceProvider = firestoreDataSourceProvider;
  }

  @Override
  public FindPeopleViewModel get() {
    return newInstance(userRepositoryProvider.get(), authRepositoryProvider.get(), exploreRepositoryProvider.get(), cloudFunctionsProvider.get(), spotifyRepositoryProvider.get(), tmdbRepositoryProvider.get(), preferencesDataStoreProvider.get(), firestoreDataSourceProvider.get());
  }

  public static FindPeopleViewModel_Factory create(Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<ExploreRepository> exploreRepositoryProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<SpotifyRepository> spotifyRepositoryProvider,
      Provider<TMDBRepository> tmdbRepositoryProvider,
      Provider<PreferencesDataStore> preferencesDataStoreProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider) {
    return new FindPeopleViewModel_Factory(userRepositoryProvider, authRepositoryProvider, exploreRepositoryProvider, cloudFunctionsProvider, spotifyRepositoryProvider, tmdbRepositoryProvider, preferencesDataStoreProvider, firestoreDataSourceProvider);
  }

  public static FindPeopleViewModel newInstance(UserRepository userRepository,
      AuthRepository authRepository, ExploreRepository exploreRepository,
      CloudFunctionsDataSource cloudFunctions, SpotifyRepository spotifyRepository,
      TMDBRepository tmdbRepository, PreferencesDataStore preferencesDataStore,
      FirestoreDataSource firestoreDataSource) {
    return new FindPeopleViewModel(userRepository, authRepository, exploreRepository, cloudFunctions, spotifyRepository, tmdbRepository, preferencesDataStore, firestoreDataSource);
  }
}
