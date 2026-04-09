package fm.corus.android.ui.navigation;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.local.PreferencesDataStore;
import fm.corus.android.data.repository.SubscriptionRepository;
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
public final class MainTabViewModel_Factory implements Factory<MainTabViewModel> {
  private final Provider<NowPlayingManager> nowPlayingManagerProvider;

  private final Provider<SubscriptionRepository> subscriptionRepositoryProvider;

  private final Provider<PreferencesDataStore> preferencesDataStoreProvider;

  public MainTabViewModel_Factory(Provider<NowPlayingManager> nowPlayingManagerProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider,
      Provider<PreferencesDataStore> preferencesDataStoreProvider) {
    this.nowPlayingManagerProvider = nowPlayingManagerProvider;
    this.subscriptionRepositoryProvider = subscriptionRepositoryProvider;
    this.preferencesDataStoreProvider = preferencesDataStoreProvider;
  }

  @Override
  public MainTabViewModel get() {
    return newInstance(nowPlayingManagerProvider.get(), subscriptionRepositoryProvider.get(), preferencesDataStoreProvider.get());
  }

  public static MainTabViewModel_Factory create(
      Provider<NowPlayingManager> nowPlayingManagerProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider,
      Provider<PreferencesDataStore> preferencesDataStoreProvider) {
    return new MainTabViewModel_Factory(nowPlayingManagerProvider, subscriptionRepositoryProvider, preferencesDataStoreProvider);
  }

  public static MainTabViewModel newInstance(NowPlayingManager nowPlayingManager,
      SubscriptionRepository subscriptionRepository, PreferencesDataStore preferencesDataStore) {
    return new MainTabViewModel(nowPlayingManager, subscriptionRepository, preferencesDataStore);
  }
}
