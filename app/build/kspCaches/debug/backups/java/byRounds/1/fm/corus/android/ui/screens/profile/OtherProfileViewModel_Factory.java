package fm.corus.android.ui.screens.profile;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class OtherProfileViewModel_Factory implements Factory<OtherProfileViewModel> {
  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<NowPlayingManager> nowPlayingManagerProvider;

  public OtherProfileViewModel_Factory(Provider<UserRepository> userRepositoryProvider,
      Provider<PostRepository> postRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
    this.postRepositoryProvider = postRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.nowPlayingManagerProvider = nowPlayingManagerProvider;
  }

  @Override
  public OtherProfileViewModel get() {
    return newInstance(userRepositoryProvider.get(), postRepositoryProvider.get(), authRepositoryProvider.get(), nowPlayingManagerProvider.get());
  }

  public static OtherProfileViewModel_Factory create(
      Provider<UserRepository> userRepositoryProvider,
      Provider<PostRepository> postRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider) {
    return new OtherProfileViewModel_Factory(userRepositoryProvider, postRepositoryProvider, authRepositoryProvider, nowPlayingManagerProvider);
  }

  public static OtherProfileViewModel newInstance(UserRepository userRepository,
      PostRepository postRepository, AuthRepository authRepository,
      NowPlayingManager nowPlayingManager) {
    return new OtherProfileViewModel(userRepository, postRepository, authRepository, nowPlayingManager);
  }
}
