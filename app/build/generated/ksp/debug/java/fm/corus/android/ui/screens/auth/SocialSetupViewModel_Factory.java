package fm.corus.android.ui.screens.auth;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
import fm.corus.android.data.remote.FirestoreDataSource;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.PostRepository;
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
public final class SocialSetupViewModel_Factory implements Factory<SocialSetupViewModel> {
  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<FirestoreDataSource> firestoreDataSourceProvider;

  private final Provider<NowPlayingManager> nowPlayingManagerProvider;

  public SocialSetupViewModel_Factory(Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<PostRepository> postRepositoryProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider) {
    this.authRepositoryProvider = authRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.postRepositoryProvider = postRepositoryProvider;
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.firestoreDataSourceProvider = firestoreDataSourceProvider;
    this.nowPlayingManagerProvider = nowPlayingManagerProvider;
  }

  @Override
  public SocialSetupViewModel get() {
    return newInstance(authRepositoryProvider.get(), userRepositoryProvider.get(), postRepositoryProvider.get(), cloudFunctionsProvider.get(), firestoreDataSourceProvider.get(), nowPlayingManagerProvider.get());
  }

  public static SocialSetupViewModel_Factory create(Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<PostRepository> postRepositoryProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<FirestoreDataSource> firestoreDataSourceProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider) {
    return new SocialSetupViewModel_Factory(authRepositoryProvider, userRepositoryProvider, postRepositoryProvider, cloudFunctionsProvider, firestoreDataSourceProvider, nowPlayingManagerProvider);
  }

  public static SocialSetupViewModel newInstance(AuthRepository authRepository,
      UserRepository userRepository, PostRepository postRepository,
      CloudFunctionsDataSource cloudFunctions, FirestoreDataSource firestoreDataSource,
      NowPlayingManager nowPlayingManager) {
    return new SocialSetupViewModel(authRepository, userRepository, postRepository, cloudFunctions, firestoreDataSource, nowPlayingManager);
  }
}
